package com.example.data.sync

import com.example.data.remote.supabase.RemotoLotes
import com.example.data.remote.supabase.ServicioCuenta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Resultado de una pasada de sincronización. */
sealed interface ResultadoSincronizacion {
    data object SinSesion : ResultadoSincronizacion
    /** Los lotes del dispositivo son de otra cuenta: no se mezclan (cerrar sesión los vacía). */
    data object OtraCuenta : ResultadoSincronizacion
    data class Lista(val subidos: Int, val recibidos: Int, val borrados: Int) : ResultadoSincronizacion
    /** Falló (sin red, servidor caído): los cambios siguen pendientes y se reintenta después. */
    data class Error(val mensaje: String, val pendientes: Int) : ResultadoSincronizacion
}

/** Resultado de [SincronizadorLotes.cerrarSesion]. */
sealed interface ResultadoCierreSesion {
    data object Cerrada : ResultadoCierreSesion
    /** Quedaron cambios sin subir: la sesión sigue abierta y no se borró nada. [causa]: por qué falló la subida. */
    data class Bloqueada(val pendientes: Int, val causa: String?) : ResultadoCierreSesion
}

/**
 * Sincronización offline-first de lotes entre el almacenamiento local y Supabase.
 *
 * 1. Sube los cambios pendientes (altas, ediciones y borrados lógicos). En el servidor un trigger se
 *    queda con la versión de mayor `modificado_en` ("gana el último que editó").
 * 2. Baja lo que cambió en la nube desde el último cursor (`actualizado_en`, reloj del servidor), con
 *    un margen de [MARGEN_CURSOR] para no perder filas de transacciones que confirmaron tarde.
 *    Aplicar dos veces la misma fila no cambia nada.
 * 3. Un cambio local pendiente más nuevo que el remoto se conserva y sube en la próxima pasada.
 *
 * Los lotes creados sin cuenta (nunca sincronizados) se suben a la primera cuenta que inicia sesión.
 * Si el dispositivo tiene lotes de otra cuenta (su sesión venció sin cerrarla): los ya sincronizados se
 * quitan (siguen en la nube de esa cuenta); si alguno quedó sin subir, no se mezcla nada
 * ([ResultadoSincronizacion.OtraCuenta]).
 */
class SincronizadorLotes(
    private val local: AlmacenLotesLocal,
    private val remoto: RemotoLotes,
    private val marcas: MarcasSincronizacion,
    /** Usuario con sesión; suspende hasta que la sesión guardada termine de cargarse. */
    private val usuarioActual: suspend () -> String?
) {
    private val exclusion = Mutex()

    suspend fun sincronizar(): ResultadoSincronizacion = exclusion.withLock {
        val usuario = usuarioActual() ?: return@withLock ResultadoSincronizacion.SinSesion
        val dueno = marcas.usuario
        if (dueno != null && dueno != usuario) {
            if (local.pendientes().isNotEmpty()) return@withLock ResultadoSincronizacion.OtraCuenta
            local.vaciar()
            marcas.reiniciar()
        }
        try {
            // 1. Subida
            val pendientes = local.pendientes()
            if (pendientes.isNotEmpty()) {
                remoto.subir(pendientes.map { it.aRemoto(usuario) })
                pendientes.forEach { local.marcarSincronizado(it.id, it.modificadoEn) }
            }
            marcas.usuario = usuario

            // 2. Bajada
            var recibidos = 0
            var borrados = 0
            var cursor = marcas.cursor
            for (fila in remoto.cambiosDesde(conMargen(cursor))) {
                val actual = local.porId(fila.id)
                val localMasNuevo = actual != null && actual.pendiente && actual.modificadoEn > fila.modificadoEn
                when {
                    localMasNuevo -> Unit
                    fila.eliminado -> if (actual != null) {
                        local.borrar(fila.id)
                        borrados++
                    }
                    actual == null || actual.modificadoEn != fila.modificadoEn || actual.pendiente -> {
                        local.guardarDesdeRemoto(fila.aRegistro())
                        recibidos++
                    }
                }
                fila.actualizadoEn?.let { if (cursor == null || esPosterior(it, cursor!!)) cursor = it }
            }
            marcas.cursor = cursor
            local.purgarEliminadosSincronizados()
            ResultadoSincronizacion.Lista(pendientes.size, recibidos, borrados)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoSincronizacion.Error(ServicioCuenta.mensajeDeError(e), local.pendientes().size)
        }
    }

    /**
     * Cierre de sesión seguro: primero sube los cambios pendientes. Si se subió todo, vacía los lotes del
     * dispositivo y cierra la sesión, así la próxima cuenta que ingrese no ve ni sube lotes ajenos. Si
     * quedó algo sin subir (sin red, error del servidor) no borra nada, no cierra la sesión y devuelve
     * [ResultadoCierreSesion.Bloqueada].
     *
     * Con lotes de otra cuenta en el dispositivo ([ResultadoSincronizacion.OtraCuenta]) cierra la sesión
     * sin tocarlos: son de esa cuenta y se suben cuando vuelva a ingresar.
     */
    suspend fun cerrarSesion(salir: suspend () -> Unit): ResultadoCierreSesion {
        val resultado = sincronizar()
        exclusion.withLock {
            if (resultado != ResultadoSincronizacion.OtraCuenta) {
                val pendientes = local.pendientes().size
                if (pendientes > 0) {
                    return ResultadoCierreSesion.Bloqueada(pendientes, (resultado as? ResultadoSincronizacion.Error)?.mensaje)
                }
                local.vaciar()
                marcas.reiniciar()
            }
        }
        salir()
        return ResultadoCierreSesion.Cerrada
    }

    /**
     * Después de eliminar la cuenta en el servidor: los lotes del dispositivo eran de esa cuenta (los
     * pendientes también, porque se pidió borrar todo) y se borran sin intentar subirlos.
     */
    suspend fun olvidarCuenta() {
        exclusion.withLock {
            local.vaciar()
            marcas.reiniciar()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun conMargen(cursor: String?): String? = cursor?.let {
        runCatching { (Instant.parse(it) - MARGEN_CURSOR).toString() }.getOrDefault(it)
    }

    @OptIn(ExperimentalTime::class)
    private fun esPosterior(a: String, b: String): Boolean =
        runCatching { Instant.parse(a) > Instant.parse(b) }.getOrElse { a > b }

    companion object {
        val MARGEN_CURSOR = 2.minutes
    }
}
