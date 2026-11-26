package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Archivos de la carpeta del usuario en Storage (interfaz: las pruebas usan una en memoria). */
interface ArchivosRemotos {
    suspend fun listar(prefijo: String): List<String>
    suspend fun subir(nombre: String, contenido: ByteArray)
    suspend fun bajar(nombre: String): ByteArray
    suspend fun borrar(nombre: String)
}

/** Qué hacer con los datos agronómicos de cada lote en una pasada. */
sealed interface AccionAgronomia {
    data class Subir(val loteId: String, val uuid: String) : AccionAgronomia
    data class Bajar(val loteId: String, val uuid: String, val nombre: String, val version: Long) : AccionAgronomia
    data class BorrarRemoto(val nombre: String) : AccionAgronomia
}

/**
 * Plan de la sincronización (puro, probado en SincronizacionAgronomiaTest). Cada lote es un ZIP
 * `agro_{uuid}_{version}.zip` en la carpeta del usuario; la versión (milisegundos de la subida) va
 * en el nombre para saber con un solo listado qué cambió en otro teléfono.
 * - Un lote con cambios locales se sube (el último cambio gana, como en los lotes).
 * - Si en la nube hay una versión más nueva que la que bajó este teléfono, se baja.
 * - Las versiones viejas que quedaron y los datos de lotes borrados se borran de la nube.
 * Los ZIP de lotes que este teléfono todavía no tiene se dejan: llegan con la próxima sincronización.
 */
object PlanAgronomia {
    const val PREFIJO = "agro_"

    fun nombre(uuid: String, version: Long) = "$PREFIJO${uuid}_$version.zip"

    /** (uuid, versión) de un nombre de la nube; null si no es un ZIP agronómico. */
    fun leer(nombre: String): Pair<String, Long>? {
        if (!nombre.startsWith(PREFIJO) || !nombre.endsWith(".zip")) return null
        val cuerpo = nombre.removePrefix(PREFIJO).removeSuffix(".zip")
        val corte = cuerpo.lastIndexOf('_')
        if (corte <= 0) return null
        val version = cuerpo.substring(corte + 1).toLongOrNull() ?: return null
        return cuerpo.substring(0, corte) to version
    }

    /**
     * @param lotes id local → uuid de los lotes activos del teléfono.
     * @param versionLocal versión de la nube que tiene el teléfono por uuid (0 = ninguna).
     * @param conDatos si el lote tiene datos en el teléfono: los cargados antes de que existiera la
     *   sincronización (sin versión y sin nada en la nube) se suben aunque no estén pendientes.
     */
    fun planificar(
        lotes: Map<String, String>,
        pendientes: Set<String>,
        borrados: Set<String>,
        versionLocal: (String) -> Long,
        remotos: List<String>,
        conDatos: (String) -> Boolean = { false }
    ): List<AccionAgronomia> {
        val porLote = remotos.mapNotNull { n -> leer(n)?.let { (uuid, v) -> Triple(uuid, v, n) } }.groupBy { it.first }
        val acciones = mutableListOf<AccionAgronomia>()
        for ((loteId, uuid) in lotes) {
            val enNube = porLote[uuid].orEmpty().sortedByDescending { it.second }
            val nuncaSubido = enNube.isEmpty() && versionLocal(uuid) == 0L && conDatos(loteId)
            if (loteId in pendientes || nuncaSubido) {
                // La subida reemplaza todo lo que había de ese lote
                acciones += AccionAgronomia.Subir(loteId, uuid)
                enNube.forEach { acciones += AccionAgronomia.BorrarRemoto(it.third) }
                continue
            }
            val ultima = enNube.firstOrNull()
            if (ultima != null && ultima.second > versionLocal(uuid)) {
                acciones += AccionAgronomia.Bajar(loteId, uuid, ultima.third, ultima.second)
            }
            enNube.drop(1).forEach { acciones += AccionAgronomia.BorrarRemoto(it.third) }
        }
        for (uuid in borrados) porLote[uuid].orEmpty().forEach { acciones += AccionAgronomia.BorrarRemoto(it.third) }
        return acciones
    }
}

/**
 * Sube y baja los mapas de rinde, el análisis de suelo y los parámetros de dosis de cada lote
 * (AlmacenAgronomia) a la carpeta del usuario en Storage. Corre después de sincronizar los lotes.
 *
 * @param lotes id local → uuid de los lotes activos.
 * @param ahora reloj (en las pruebas, fijo).
 */
class SincronizadorAgronomia(
    private val almacen: AlmacenAgronomia,
    private val remoto: ArchivosRemotos,
    private val lotes: suspend () -> Map<String, String>,
    private val ahora: () -> Long = System::currentTimeMillis
) {
    /** null si salió bien; si no, el primer error (lo demás se intenta igual). */
    suspend fun sincronizar(): String? = withContext(Dispatchers.IO) {
        val acciones = PlanAgronomia.planificar(
            lotes(), almacen.pendientes(), almacen.borrados(), almacen::version, remoto.listar(PlanAgronomia.PREFIJO),
            conDatos = almacen::tieneDatos
        )
        var error: String? = null
        // Si la subida de un lote falla, su versión anterior de la nube no se borra
        val fallidos = mutableSetOf<String>()
        // Primero lo que se sube o se baja; las versiones viejas se borran al final
        for (accion in acciones.sortedBy { it is AccionAgronomia.BorrarRemoto }) {
            try {
                when (accion) {
                    is AccionAgronomia.Subir -> subir(accion)
                    is AccionAgronomia.Bajar -> {
                        almacen.desempaquetar(accion.loteId, remoto.bajar(accion.nombre))
                        almacen.guardarVersion(accion.uuid, accion.version)
                    }
                    is AccionAgronomia.BorrarRemoto ->
                        if (PlanAgronomia.leer(accion.nombre)?.first !in fallidos) remoto.borrar(accion.nombre)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Agronomía: no se pudo $accion", e)
                if (accion is AccionAgronomia.Subir) fallidos += accion.uuid
                if (error == null) error = e.message ?: "No se pudieron sincronizar los datos de suelo y rinde."
            }
        }
        // Los datos de un lote borrado ya no están en la nube (o nunca estuvieron)
        val quedan = remoto.listar(PlanAgronomia.PREFIJO).mapNotNull { PlanAgronomia.leer(it)?.first }.toSet()
        almacen.borrados().filter { it !in quedan }.forEach(almacen::olvidarBorrado)
        error
    }

    private suspend fun subir(accion: AccionAgronomia.Subir) {
        val zip = almacen.empaquetar(accion.loteId)
        if (zip != null) {
            if (zip.size > MAX_BYTES) {
                // Queda pendiente y local: no entra en el límite del bucket
                throw IllegalStateException("Los datos de suelo y rinde de un lote superan los 10 MB y no se suben a la nube.")
            }
            val version = ahora()
            remoto.subir(PlanAgronomia.nombre(accion.uuid, version), zip)
            almacen.guardarVersion(accion.uuid, version)
        }
        // Sin datos (se borró todo): alcanza con borrar las versiones de la nube, que ya están en el plan
        almacen.marcarSubido(accion.loteId)
    }

    private companion object {
        const val TAG = "GranexAgronomia"
        /** Límite por archivo del bucket `lotes` (migración 20261026120000). */
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}
