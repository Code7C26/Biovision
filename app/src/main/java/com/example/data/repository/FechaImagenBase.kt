package com.example.data.repository

import com.example.data.remote.api.RetrofitNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Fecha de la foto de Esri World Imagery en un punto, según los metadatos del propio servicio
 * (capa "Citations"). El mosaico mezcla fotos de distintos años: en un lote de Córdoba la foto de
 * alta resolución era de más de un año antes que las fechas del escaneo, y no coincidía con lo que
 * mostraba el índice. Un pedido por zona (~2 km), con caché en memoria.
 */
object FechaImagenBase {
    private const val MAX_CACHE = 64
    private val cache = object : LinkedHashMap<String, String>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_CACHE
    }

    /** Fecha yyyy-MM-dd de la foto de alta resolución en (lat, lon), o null si el servicio no la da. */
    suspend fun esri(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val clave = "${(lat * 50).roundToInt()},${(lon * 50).roundToInt()}"
        synchronized(cache) { cache[clave] }?.let { return@withContext it.ifEmpty { null } }
        val d = 0.01
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("services.arcgisonline.com")
            .addPathSegments("arcgis/rest/services/World_Imagery/MapServer/identify")
            .addQueryParameter("geometry", "$lon,$lat")
            .addQueryParameter("geometryType", "esriGeometryPoint")
            .addQueryParameter("sr", "4326")
            .addQueryParameter("layers", "all:4")
            .addQueryParameter("tolerance", "1")
            // Escala de detalle (nivel ~15): a menos zoom el servicio responde con el mosaico de 15 m, sin fecha
            .addQueryParameter("mapExtent", "${lon - d},${lat - d},${lon + d},${lat + d}")
            .addQueryParameter("imageDisplay", "800,800,96")
            .addQueryParameter("returnGeometry", "false")
            .addQueryParameter("f", "json")
            .build()
        val fecha = runCatching {
            RetrofitNetworkClient.okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                fechaDeRespuesta(r.body?.string().orEmpty())
            }
        }.getOrNull()
        // Solo se guarda una respuesta válida (con o sin fecha); un error de red se vuelve a intentar
        if (fecha != null) synchronized(cache) { cache[clave] = fecha }
        fecha?.ifEmpty { null }
    }

    /** "" si respondió sin fecha (mosaico de baja resolución); null si la respuesta no se entiende. */
    internal fun fechaDeRespuesta(json: String): String? {
        val resultados = runCatching { JSONObject(json).getJSONArray("results") }.getOrNull() ?: return null
        val fechas = (0 until resultados.length()).mapNotNull { i ->
            resultados.getJSONObject(i).optJSONObject("attributes")?.optString("DATE (YYYYMMDD)")
                ?.takeIf { it.length == 8 && it.all(Char::isDigit) }
        }
        // Con varias fotos superpuestas se dibuja la de más arriba, que es la primera
        val f = fechas.firstOrNull() ?: return ""
        return "${f.substring(0, 4)}-${f.substring(4, 6)}-${f.substring(6, 8)}"
    }
}
