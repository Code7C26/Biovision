package com.example.data.agenda

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Dónde guarda cada plataforma el documento de la Agenda (Android: un archivo; web: localStorage). */
interface PersistenciaAgenda {
    suspend fun leer(): String?
    suspend fun escribir(texto: String)
    suspend fun borrar()
}

/** Lo que se guarda en el dispositivo: los datos y cómo están respecto de la nube. */
@Serializable
internal data class EstadoAgendaLocal(
    val formato: Int = 1,
    val datos: DatosAgenda = DatosAgenda(),
    /** Versión de la nube que ya está incorporada (0 = ninguna). */
    val base: Long = 0,
    /** Hay cambios hechos acá que todavía no se subieron. */
    val pendiente: Boolean = false
)

/** Foto del almacén para la sincronización; [revision] cambia con cada escritura local. */
internal data class FotoAgenda(val datos: DatosAgenda, val base: Long, val pendiente: Boolean, val revision: Long)

internal val JsonAgenda = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * La Agenda del dispositivo: datos observables, cambios pendientes de subir y el punto de la nube ya
 * incorporado. Cada cambio del usuario sella [Gasto.modificadoEn] y llama a [alCambiar] (que programa
 * la sincronización). Un documento ilegible no impide abrir la app: se empieza vacío.
 */
class AlmacenAgenda(
    private val persistencia: PersistenciaAgenda,
    private val ahora: () -> Long,
    private val alCambiar: () -> Unit = {}
) {
    private val mutex = Mutex()
    private var cargado = false
    private var base = 0L
    private var revision = 0L

    private val _datos = MutableStateFlow(DatosAgenda())
    val datos: StateFlow<DatosAgenda> = _datos.asStateFlow()

    private val _pendiente = MutableStateFlow(false)
    /** Cambios hechos acá que todavía no se subieron a la nube. */
    val pendiente: StateFlow<Boolean> = _pendiente.asStateFlow()

    suspend fun cargar() = mutex.withLock { cargarSinBloqueo() }

    private suspend fun cargarSinBloqueo() {
        if (cargado) return
        val estado = runCatching { persistencia.leer()?.let { JsonAgenda.decodeFromString(EstadoAgendaLocal.serializer(), it) } }
            .getOrNull() ?: EstadoAgendaLocal()
        _datos.value = estado.datos
        _pendiente.value = estado.pendiente
        base = estado.base
        cargado = true
    }

    suspend fun guardarGasto(gasto: Gasto) = cambiar { d ->
        val sellado = gasto.copy(producto = gasto.producto.trim(), modificadoEn = ahora())
        d.copy(gastos = d.gastos.filterNot { it.id == gasto.id } + sellado)
    }

    suspend fun borrarGasto(id: String) = cambiar { d ->
        d.copy(gastos = d.gastos.filterNot { it.id == id }, borrados = d.borrados + id)
    }

    suspend fun guardarAplicacion(a: Aplicacion) = cambiar { d ->
        d.copy(aplicaciones = d.aplicaciones.filterNot { it.id == a.id } + a.copy(modificadoEn = ahora()))
    }

    suspend fun borrarAplicacion(id: String) = cambiar { d ->
        d.copy(aplicaciones = d.aplicaciones.filterNot { it.id == id }, borrados = d.borrados + id)
    }

    /** Al cerrar sesión o eliminar la cuenta: no queda nada de la Agenda en el dispositivo. */
    suspend fun vaciar() = mutex.withLock {
        persistencia.borrar()
        _datos.value = DatosAgenda()
        _pendiente.value = false
        base = 0
        revision++
        cargado = true
    }

    private suspend fun cambiar(f: (DatosAgenda) -> DatosAgenda) {
        mutex.withLock {
            cargarSinBloqueo()
            val nuevos = f(_datos.value)
            guardar(nuevos, base, pendiente = true)
            revision++
        }
        alCambiar()
    }

    private suspend fun guardar(datos: DatosAgenda, base: Long, pendiente: Boolean) {
        persistencia.escribir(JsonAgenda.encodeToString(EstadoAgendaLocal.serializer(), EstadoAgendaLocal(datos = datos, base = base, pendiente = pendiente)))
        _datos.value = datos
        _pendiente.value = pendiente
        this.base = base
    }

    // ---------- Para SincronizadorAgenda ----------

    internal suspend fun foto(): FotoAgenda = mutex.withLock {
        cargarSinBloqueo()
        FotoAgenda(_datos.value, base, _pendiente.value, revision)
    }

    /** Incorpora la versión [version] de la nube: la reemplaza o, con cambios sin subir, se junta con ellos. */
    internal suspend fun incorporarNube(nube: DatosAgenda, version: Long) = mutex.withLock {
        cargarSinBloqueo()
        val pendiente = _pendiente.value
        val datos = if (pendiente) SincronizadorAgenda.fusionar(_datos.value, nube) else nube
        guardar(datos, version, pendiente)
        revision++
    }

    /** Se subió la versión [version] con lo que había en [revisionSubida]; si después hubo cambios, siguen pendientes. */
    internal suspend fun marcarSubido(version: Long, revisionSubida: Long) = mutex.withLock {
        guardar(_datos.value, version, pendiente = revision != revisionSubida)
    }
}
