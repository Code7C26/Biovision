package com.example.data.agenda

import kotlin.math.max

/** Archivos de la carpeta del usuario en Storage (interfaz: las pruebas usan una en memoria). */
interface ArchivosAgenda {
    suspend fun listar(prefijo: String): List<String>
    suspend fun subir(nombre: String, contenido: ByteArray)
    suspend fun bajar(nombre: String): ByteArray
    suspend fun borrar(nombre: String)
}

/**
 * Copia de la Agenda en la carpeta privada del usuario en Storage (el mismo bucket `lotes` que los
 * datos agronómicos, con sus políticas por usuario): un documento `agenda_{version}.json`, donde la
 * versión son los milisegundos de la subida. Así un solo listado dice si otro dispositivo subió algo.
 *
 * - Si en la nube hay una versión más nueva que la incorporada, se baja; si además hay cambios sin
 *   subir, se juntan ([fusionar]) en vez de pisarse.
 * - Si hay cambios sin subir, se sube el resultado y se borran las versiones viejas.
 *
 * Probado en AgendaSincronizacionTest.
 */
class SincronizadorAgenda(
    private val almacen: AlmacenAgenda,
    private val remoto: ArchivosAgenda,
    private val ahora: () -> Long
) {
    suspend fun sincronizar() {
        val versiones = remoto.listar(PREFIJO).mapNotNull { n -> version(n)?.let { it to n } }.sortedByDescending { it.first }
        val ultima = versiones.firstOrNull()
        if (ultima != null && ultima.first > almacen.foto().base) {
            val nube = JsonAgenda.decodeFromString(DatosAgenda.serializer(), remoto.bajar(ultima.second).decodeToString())
            almacen.incorporarNube(nube, ultima.first)
        }
        val foto = almacen.foto()
        if (foto.pendiente) {
            val nueva = max(ahora(), (ultima?.first ?: 0L) + 1)
            remoto.subir(nombre(nueva), JsonAgenda.encodeToString(DatosAgenda.serializer(), foto.datos).encodeToByteArray())
            almacen.marcarSubido(nueva, foto.revision)
            versiones.forEach { remoto.borrar(it.second) }
        } else {
            versiones.drop(1).forEach { remoto.borrar(it.second) }
        }
    }

    companion object {
        const val PREFIJO = "agenda_"

        fun nombre(version: Long) = "$PREFIJO$version.json"

        /** Versión de un nombre de la nube; null si no es un documento de la Agenda. */
        fun version(nombre: String): Long? =
            if (nombre.startsWith(PREFIJO) && nombre.endsWith(".json")) nombre.removePrefix(PREFIJO).removeSuffix(".json").toLongOrNull() else null

        /**
         * Junta dos versiones de la Agenda (puro): de cada gasto o aplicación queda la edición más nueva
         * (a igual hora, la de [local]), y lo borrado en cualquiera de las dos no vuelve.
         */
        fun fusionar(local: DatosAgenda, nube: DatosAgenda): DatosAgenda {
            val borrados = local.borrados + nube.borrados
            return DatosAgenda(
                gastos = (local.gastos + nube.gastos).groupBy { it.id }.values
                    .map { versiones -> versiones.maxBy { it.modificadoEn } }
                    .filter { it.id !in borrados },
                aplicaciones = (local.aplicaciones + nube.aplicaciones).groupBy { it.id }.values
                    .map { versiones -> versiones.maxBy { it.modificadoEn } }
                    .filter { it.id !in borrados },
                borrados = borrados
            )
        }
    }
}
