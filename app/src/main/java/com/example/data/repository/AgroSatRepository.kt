package com.example.data.repository

import com.example.data.local.dao.AgroDao
import com.example.data.local.entity.LoteEntity
import com.example.utils.GeometriaLote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LoteSatelital(
    val id: String,
    val nombre: String,
    val coordenadas: List<Pair<Double, Double>>, // (lat, lon), anillo abierto: el último vértice no repite el primero
    val geojson: String,
    val areaHectareas: Double,
    val provincia: String,
    /** Si se puede editar y eliminar. Hoy todos los lotes son del usuario (no hay lotes de ejemplo). */
    val editable: Boolean = true
)

/**
 * @param sincronizaConNube con Supabase configurado los borrados son lógicos (tienen que llegar a la
 * nube) y cada cambio llama a [alCambiar] para programar la sincronización. Sin backend, se borra
 * directo como antes.
 */
class AgroSatRepository(
    private val dao: AgroDao,
    private val sincronizaConNube: Boolean = false,
    private val alCambiar: () -> Unit = {},
    /** Al borrar un lote (id de la app, uuid): sus datos de suelo, rinde y dosis. */
    private val alEliminar: (String, String) -> Unit = { _, _ -> }
) {

    /**
     * Lotes del usuario, los más recientes primero. No hay lotes de ejemplo ni siembra inicial: la
     * primera vez la lista está vacía hasta que el usuario dibuja su primer lote.
     */
    val lotes: Flow<List<LoteSatelital>> = dao.observarLotes()
        .map { entidades -> entidades.mapNotNull { it.aLote() } }
        .flowOn(Dispatchers.Default)

    /** Guarda un lote nuevo o, si [idExistente] es de un lote del usuario, lo actualiza. */
    suspend fun guardarLote(
        idExistente: String?,
        nombre: String,
        provincia: String,
        coordenadas: List<Pair<Double, Double>>
    ): LoteSatelital = withContext(Dispatchers.IO) {
        val anillo = GeometriaLote.anilloAbierto(coordenadas)
        val area = GeometriaLote.areaHectareas(anillo)
        val geojson = generarGeoJson(anillo)
        val ahora = System.currentTimeMillis()
        val existente = idExistente?.removePrefix(PREFIJO_DB)?.toIntOrNull()?.let { dao.lotePorId(it) }
        val id = if (existente != null) {
            // Conserva uuid y fecha de creación; la edición queda pendiente de subir
            dao.actualizarLote(
                existente.copy(
                    nombre = nombre, geoJson = geojson, superficieHa = area, provincia = provincia,
                    modificadoEn = ahora, pendienteSync = true
                )
            )
            existente.id
        } else {
            dao.insertarLote(
                LoteEntity(
                    nombre = nombre, geoJson = geojson, superficieHa = area, provincia = provincia,
                    fechaCreacion = ahora, modificadoEn = ahora, pendienteSync = true
                )
            ).toInt()
        }
        alCambiar()
        LoteSatelital(PREFIJO_DB + id, nombre, anillo, geojson, area, provincia, editable = true)
    }

    suspend fun eliminarLote(lote: LoteSatelital) {
        val idDb = lote.id.removePrefix(PREFIJO_DB).toIntOrNull() ?: return
        withContext(Dispatchers.IO) {
            val uuid = dao.lotePorId(idDb)?.uuid
            if (sincronizaConNube) dao.marcarLoteEliminado(idDb, System.currentTimeMillis()) else dao.eliminarLote(idDb)
            uuid?.let { alEliminar(lote.id, it) }
        }
        if (sincronizaConNube) alCambiar()
    }

    private fun LoteEntity.aLote(): LoteSatelital? {
        val coordenadas = leerCoordenadas(geoJson) ?: return null
        return LoteSatelital(
            id = PREFIJO_DB + id,
            nombre = nombre,
            coordenadas = coordenadas,
            geojson = geoJson,
            areaHectareas = GeometriaLote.areaHectareas(coordenadas),
            provincia = provincia,
            editable = true
        )
    }

    /**
     * Geocodifica una ciudad o dirección con Nominatim (OpenStreetMap) usando el cliente HTTP común.
     * El mensaje de la excepción en caso de fallo está pensado para mostrarse al usuario.
     */
    suspend fun geocodificar(query: String): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("countrycodes", "ar")
            .build()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", GeocodificadorLocalidades.USER_AGENT)
            .build()

        try {
            com.example.data.remote.api.RetrofitNetworkClient.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Error del servidor"))
                }
                val jsonArray = try {
                    JSONArray(body)
                } catch (e: JSONException) {
                    return@withContext Result.failure(Exception("Error al leer respuesta"))
                }
                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("Ubicación no encontrada"))
                }
                val first = jsonArray.getJSONObject(0)
                Result.success(first.getString("lat").toDouble() to first.getString("lon").toDouble())
            }
        } catch (e: java.io.IOException) {
            Result.failure(Exception("Error de red: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(Exception("Error al leer respuesta"))
        }
    }

    // Provincia por punto, redondeado a ~1 km: reabrir el diálogo no repite la consulta
    private val provincias = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Provincia (address.state) de un punto con la geocodificación inversa de Nominatim: una
     * consulta por lote guardado, iniciada por el usuario (dentro de la política de uso de OSM).
     * Falla con un mensaje para mostrar si no hay conexión o el punto no tiene provincia.
     */
    suspend fun provinciaDe(lat: Double, lon: Double): Result<String> = withContext(Dispatchers.IO) {
        val clave = String.format(java.util.Locale.ROOT, "%.2f,%.2f", lat, lon)
        provincias[clave]?.let { return@withContext Result.success(it) }
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("reverse")
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("zoom", "5") // nivel provincia: no hace falta la dirección
            .addQueryParameter("accept-language", "es")
            .build()
        val request = okhttp3.Request.Builder().url(url).header("User-Agent", GeocodificadorLocalidades.USER_AGENT).build()
        try {
            com.example.data.remote.api.RetrofitNetworkClient.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) return@withContext Result.failure(Exception("El servicio de mapas no respondió"))
                val provincia = provinciaDeRespuesta(body)
                    ?: return@withContext Result.failure(Exception("No hay una provincia para esa ubicación"))
                provincias[clave] = provincia
                Result.success(provincia)
            }
        } catch (e: java.io.IOException) {
            Result.failure(Exception("Sin conexión para detectar la provincia"))
        } catch (e: JSONException) {
            Result.failure(Exception("Respuesta ilegible del servicio de mapas"))
        }
    }

    /** Sugerencias del buscador del mapa (Photon, ver [GeocodificadorLocalidades]). */
    suspend fun sugerirLocalidades(texto: String, cerca: Pair<Double, Double>?): Result<List<SugerenciaLocalidad>> =
        GeocodificadorLocalidades.sugerir(texto, cerca)

    companion object {
        const val PREFIJO_DB = "db_"

        /** address.state de la respuesta de Nominatim reverse (jsonv2); null si no viene. */
        internal fun provinciaDeRespuesta(json: String): String? =
            JSONObject(json).optJSONObject("address")?.optString("state")?.trim()?.takeIf { it.isNotEmpty() }

        /** Feature GeoJSON con el anillo cerrado y coordenadas [lon, lat], como pide la especificación. */
        fun generarGeoJson(coordenadas: List<Pair<Double, Double>>): String {
            val anillo = GeometriaLote.anilloAbierto(coordenadas).let { if (it.isEmpty()) it else it + it.first() }
            val ring = JSONArray().apply { anillo.forEach { (lat, lon) -> put(JSONArray().put(lon).put(lat)) } }
            return JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject())
                .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring)))
                .toString(2)
        }

        private fun leerCoordenadas(geojson: String): List<Pair<Double, Double>>? = try {
            val raiz = JSONObject(geojson)
            val geometria = raiz.optJSONObject("geometry") ?: raiz
            val ring = geometria.getJSONArray("coordinates").getJSONArray(0)
            val puntos = (0 until ring.length()).map { i ->
                val c = ring.getJSONArray(i)
                c.getDouble(1) to c.getDouble(0)
            }
            GeometriaLote.anilloAbierto(puntos).takeIf { it.size >= 3 }
        } catch (e: JSONException) {
            null
        }
    }
}
