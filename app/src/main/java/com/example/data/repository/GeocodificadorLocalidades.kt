package com.example.data.repository

import com.example.data.remote.api.RetrofitNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Sugerencias de localidades argentinas mientras se escribe, con la API de geocodificación de
 * Open-Meteo (GeoNames, sin clave, mismo proveedor y condiciones que el pronóstico).
 * Responde en ~0,3–1 s; Photon (komoot) tardaba 6–13 s y Nominatim prohíbe el autocompletado.
 * La API no tiene sesgo de cercanía, así que el orden por proximidad se calcula acá.
 * Lo usan los buscadores de Clima y Satélite.
 */
object GeocodificadorLocalidades {
    const val USER_AGENT = "GRANEX-Android/1.0 (com.aistudio.granex.agtech)"
    private const val MAX_CACHE = 64

    // Volver a escribir o borrar letras no repite la consulta
    private val cache = object : LinkedHashMap<String, List<SugerenciaLocalidad>>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SugerenciaLocalidad>>?) = size > MAX_CACHE
    }

    /** [cerca] (lat, lon) ordena primero las localidades próximas a ese punto (dispositivo o mapa). */
    suspend fun sugerir(texto: String, cerca: Pair<Double, Double>?): Result<List<SugerenciaLocalidad>> =
        withContext(Dispatchers.IO) {
            val clave = texto.trim().lowercase() + "|" + cerca?.let { (la, lo) -> "${(la * 10).roundToInt()},${(lo * 10).roundToInt()}" }
            synchronized(cache) { cache[clave] }?.let { return@withContext Result.success(it) }
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("geocoding-api.open-meteo.com")
                .addPathSegments("v1/search")
                .addQueryParameter("name", texto.trim())
                .addQueryParameter("count", LectorLocalidades.CANDIDATOS.toString())
                .addQueryParameter("language", "es")
                .addQueryParameter("countryCode", "AR")
                .addQueryParameter("format", "json")
                .build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            try {
                RetrofitNetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body.isNullOrEmpty()) {
                        return@withContext Result.failure(Exception("Error del servidor"))
                    }
                    val lista = parsear(body, texto, cerca)
                    synchronized(cache) { cache[clave] = lista }
                    Result.success(lista)
                }
            } catch (e: java.io.IOException) {
                Result.failure(Exception("Sin conexión para sugerir localidades"))
            } catch (e: IllegalArgumentException) { // JSON inválido (SerializationException)
                Result.failure(Exception("Error al leer respuesta"))
            }
        }

    /** Lectura y orden de la respuesta: código común con la web ([LectorLocalidades]). */
    internal fun parsear(json: String, texto: String, cerca: Pair<Double, Double>?): List<SugerenciaLocalidad> =
        LectorLocalidades.parsear(json, texto, cerca)

    internal fun distanciaKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double = LectorLocalidades.distanciaKm(a, b)
}
