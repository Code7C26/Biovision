package com.example.data.sync

import com.example.data.remote.supabase.LoteRemoto
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Lote tal como se guarda en el dispositivo (Room en Android, localStorage en la web).
 *
 * - [id]: UUID generado en el dispositivo; es también el id de la fila en Supabase.
 * - [modificadoEn]: reloj del dispositivo (ms) en la última edición: en un conflicto gana el mayor.
 * - [eliminado]: borrado lógico, para que el borrado también viaje a la nube (después se purga).
 * - [pendiente]: hay un cambio local que todavía no se subió.
 */
@Serializable
data class RegistroLote(
    val id: String,
    val nombre: String,
    val provincia: String,
    val superficieHa: Double,
    val geojson: String,
    val creadoEn: Long,
    val modificadoEn: Long,
    val eliminado: Boolean = false,
    val pendiente: Boolean = true
)

fun RegistroLote.aRemoto(usuarioId: String) = LoteRemoto(
    id = id,
    usuarioId = usuarioId,
    nombre = nombre,
    provincia = provincia,
    superficieHa = superficieHa,
    geojson = Json.parseToJsonElement(geojson).jsonObject,
    modificadoEn = modificadoEn,
    creadoEnMs = creadoEn,
    eliminado = eliminado
)

fun LoteRemoto.aRegistro() = RegistroLote(
    id = id,
    nombre = nombre,
    provincia = provincia,
    superficieHa = superficieHa,
    geojson = (geojson as? JsonObject ?: JsonObject(emptyMap())).toString(),
    creadoEn = creadoEnMs ?: modificadoEn,
    modificadoEn = modificadoEn,
    eliminado = eliminado,
    pendiente = false
)

/** Lo que la sincronización necesita del almacenamiento local de cada plataforma. */
interface AlmacenLotesLocal {
    /** Cambios locales sin subir (incluye borrados lógicos). */
    suspend fun pendientes(): List<RegistroLote>
    suspend fun porId(id: String): RegistroLote?
    /** Guarda una versión que vino de la nube (sin marca de pendiente). */
    suspend fun guardarDesdeRemoto(registro: RegistroLote)
    suspend fun borrar(id: String)
    /** Quita la marca de pendiente solo si el lote no cambió desde que se subió esa versión. */
    suspend fun marcarSincronizado(id: String, modificadoEn: Long)
    /** Borra del dispositivo los lotes eliminados cuyo borrado ya llegó a la nube. */
    suspend fun purgarEliminadosSincronizados()
    /** Borra todos los lotes locales (al cerrar sesión). */
    suspend fun vaciar()
}

/**
 * Almacén local sobre [Settings]: localStorage en la web (unos pocos MB, sobran para lotes) y
 * MapSettings en los tests. Guarda la lista entera como JSON en una sola clave.
 */
class AlmacenLotesAjustes(private val ajustes: Settings) : AlmacenLotesLocal {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializador = ListSerializer(RegistroLote.serializer())

    private fun leer(): List<RegistroLote> = ajustes.getStringOrNull(CLAVE)?.let {
        runCatching { json.decodeFromString(serializador, it) }.getOrNull()
    }.orEmpty()

    private fun escribir(lotes: List<RegistroLote>) = ajustes.putString(CLAVE, json.encodeToString(serializador, lotes))

    /** Lotes visibles (no eliminados), los más nuevos primero. */
    fun activos(): List<RegistroLote> = leer().filterNot { it.eliminado }.sortedByDescending { it.creadoEn }

    /** Alta o edición hecha por el usuario: queda pendiente de subir. */
    fun guardarCambio(registro: RegistroLote) =
        escribir(leer().filterNot { it.id == registro.id } + registro.copy(pendiente = true))

    /** Borrado hecho por el usuario: lógico, para que también se borre en la nube. */
    fun marcarEliminado(id: String, ahora: Long) =
        escribir(leer().map { if (it.id == id) it.copy(eliminado = true, pendiente = true, modificadoEn = ahora) else it })

    /** Cambios sin subir, incluidos los borrados (para mostrar "N cambios sin subir"). */
    fun cantidadPendientes(): Int = leer().count { it.pendiente }

    override suspend fun pendientes() = leer().filter { it.pendiente }
    override suspend fun porId(id: String) = leer().firstOrNull { it.id == id }
    override suspend fun guardarDesdeRemoto(registro: RegistroLote) =
        escribir(leer().filterNot { it.id == registro.id } + registro.copy(pendiente = false))
    override suspend fun borrar(id: String) = escribir(leer().filterNot { it.id == id })
    override suspend fun marcarSincronizado(id: String, modificadoEn: Long) =
        escribir(leer().map { if (it.id == id && it.modificadoEn == modificadoEn) it.copy(pendiente = false) else it })
    override suspend fun purgarEliminadosSincronizados() = escribir(leer().filterNot { it.eliminado && !it.pendiente })
    override suspend fun vaciar() = ajustes.remove(CLAVE)

    companion object {
        const val CLAVE = "lotes_cache_v1"
    }
}

/** Cursor de la última sincronización y dueño de los datos locales (Settings de cada plataforma). */
class MarcasSincronizacion(private val ajustes: Settings) {
    /** `actualizado_en` (reloj del servidor) del último cambio recibido. */
    var cursor: String?
        get() = ajustes.getStringOrNull(CLAVE_CURSOR)
        set(valor) = if (valor == null) ajustes.remove(CLAVE_CURSOR) else ajustes.putString(CLAVE_CURSOR, valor)

    /** Cuenta con la que se sincronizaron los lotes de este dispositivo; null si nunca se sincronizaron. */
    var usuario: String?
        get() = ajustes.getStringOrNull(CLAVE_USUARIO)
        set(valor) = if (valor == null) ajustes.remove(CLAVE_USUARIO) else ajustes.putString(CLAVE_USUARIO, valor)

    fun reiniciar() {
        cursor = null
        usuario = null
    }

    private companion object {
        const val CLAVE_CURSOR = "sync_lotes_cursor"
        const val CLAVE_USUARIO = "sync_lotes_usuario"
    }
}
