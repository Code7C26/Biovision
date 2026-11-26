package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.model.CotizacionHistorica
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Historial de pizarra de un grano, con el origen de los datos para mostrarlo en pantalla. */
data class SerieHistorica(
    val cotizaciones: List<CotizacionHistorica>,
    val obtenida: Long,
    /** true si no se pudo actualizar y se muestra la última serie guardada. */
    val desactualizada: Boolean
)

/**
 * Precios de pizarra de Rosario de los últimos 90 días.
 *
 * - Precios en $/tn: consulta histórica de la Cámara Arbitral de Cereales de la BCR
 *   (cac.bcr.com.ar, "Precios Pizarra y Estimativos", diaria, paginada de a 10 filas).
 * - Conversión a US$/tn: dólar mayorista comprador de cada día (api.argentinadatos.com). La BCR
 *   convierte con el BNA divisa comprador, que difiere menos de 0,5 %.
 *
 * El endpoint histórico de granos.ar solo tiene 3 registros de abril de 2026, por eso no se usa.
 * La serie se guarda en disco (6 h de vigencia) y sin conexión se muestra la última guardada.
 */
class RosarioMarketRepository(context: Context) {

    // Cliente sin el interceptor de logging: registraría cada página HTML completa en debug
    private val http: OkHttpClient = RetrofitNetworkClient.okHttpClient.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .build()
    private val directorio = File(context.filesDir, "pizarra_historica")
    private val memoria = ConcurrentHashMap<String, SerieHistorica>()

    suspend fun obtenerHistorial(cultivo: String, forzar: Boolean = false): Result<SerieHistorica> = withContext(Dispatchers.IO) {
        val clave = claveCultivo(cultivo)
        val guardada = memoria[clave] ?: leerDisco(clave)?.also { memoria[clave] = it }
        if (!forzar && guardada != null && System.currentTimeMillis() - guardada.obtenida < VIGENCIA_MS) {
            return@withContext Result.success(guardada)
        }
        try {
            val serie = descargar(cultivo)
            Log.d(TAG, "$cultivo: ${serie.size} ruedas (${serie.firstOrNull()?.fecha} → ${serie.lastOrNull()?.fecha})")
            if (serie.isEmpty()) throw IOException("La Cámara Arbitral no devolvió cotizaciones de $cultivo para los últimos 90 días")
            val nueva = SerieHistorica(serie, System.currentTimeMillis(), desactualizada = false)
            memoria[clave] = nueva
            guardarDisco(clave, nueva)
            Result.success(nueva)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar el historial de $cultivo", e)
            if (guardada != null) Result.success(guardada.copy(desactualizada = true))
            else Result.failure(Exception(
                if (e is IOException) "Sin conexión con la Bolsa de Comercio de Rosario. Revisá tu conexión y reintentá."
                else "No se pudo leer el historial de precios de la Cámara Arbitral."
            ))
        }
    }

    private suspend fun descargar(cultivo: String): List<CotizacionHistorica> = coroutineScope {
        val producto = codigoProducto(cultivo) ?: throw IllegalArgumentException("Cultivo sin código en la Cámara Arbitral: $cultivo")
        val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires") }
        val hasta = formato.format(Date())
        val desde = formato.format(Date(System.currentTimeMillis() - DIAS * 86_400_000L))

        val dolar = async { tipoDeCambio() }
        // La primera página dice cuántas hay; el resto se pide en paralelo (de a 3)
        val primera = pagina(producto, desde, hasta, 0)
        val limite = Semaphore(3)
        val resto = (1..primera.ultimaPagina).map { n -> async { limite.withPermit { pagina(producto, desde, hasta, n).filas } } }.awaitAll()
        val filas = (primera.filas + resto.flatten()).distinctBy { it.first }.sortedBy { it.first }

        val cambio = dolar.await()
        filas.mapNotNull { (fecha, ars, estimativo) ->
            // Dólar del día o, si ese día no hay dato (feriado cambiario), el último anterior
            val tc = cambio.floorEntry(fecha)?.value
            if (tc == null) {
                Log.e(TAG, "Sin tipo de cambio para $fecha: la rueda se descarta")
                return@mapNotNull null
            }
            CotizacionHistorica(
                fecha = fecha,
                precioUsd = ars / tc,
                precioArs = ars,
                puerto = if (estimativo) "Cámara Arbitral Rosario (precio estimativo)" else "Cámara Arbitral Rosario (pizarra)"
            )
        }
    }

    private class Pagina(val filas: List<Triple<String, Double, Boolean>>, val ultimaPagina: Int)

    private fun pagina(producto: Int, desde: String, hasta: String, numero: Int): Pagina {
        val url = "https://www.cac.bcr.com.ar/es/precios-de-pizarra/consultas".toHttpUrl().newBuilder()
            .addQueryParameter("product", producto.toString())
            .addQueryParameter("type", "any")
            .addQueryParameter("date_start", desde)
            .addQueryParameter("date_end", hasta)
            .addQueryParameter("period", "day")
            .apply { if (numero > 0) addQueryParameter("page", numero.toString()) }
            .build()
        val html = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("La Cámara Arbitral respondió ${r.code} (página $numero)")
            r.body?.string().orEmpty()
        }
        val doc = Jsoup.parse(html)
        val filas = doc.select("table tr").mapNotNull { tr ->
            val celdas = tr.select("td")
            if (celdas.size < 2) return@mapNotNull null
            val fecha = celdas.first()!!.text().trim()
            val precioTexto = celdas.last()!!.text().trim()
            val partes = fecha.split("/")
            if (partes.size != 3) return@mapNotNull null
            val precio = precioTexto.replace("$", "").replace(".", "").replace(",", ".").trim().toDoubleOrNull()
            if (precio == null || precio <= 0.0) {
                if (!precioTexto.contains("S/C", ignoreCase = true)) Log.e(TAG, "Precio ilegible '$precioTexto' el $fecha (página $numero)")
                return@mapNotNull null
            }
            // La columna del medio marca los precios estimativos
            val estimativo = celdas.size >= 3 && celdas[1].text().isNotBlank()
            Triple("${partes[2]}-${partes[1]}-${partes[0]}", precio, estimativo)
        }
        val ultima = doc.select("a[href*=page=]").mapNotNull { Regex("page=(\\d+)").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        if (filas.isEmpty() && numero == 0) Log.e(TAG, "La página de la Cámara Arbitral no trajo filas: ¿cambió el formato de la tabla?")
        return Pagina(filas, ultima)
    }

    /** Dólar mayorista comprador por fecha (yyyy-MM-dd). */
    private fun tipoDeCambio(): java.util.TreeMap<String, Double> {
        val json = http.newCall(
            Request.Builder().url("https://api.argentinadatos.com/v1/cotizaciones/dolares/mayorista").header("User-Agent", USER_AGENT).build()
        ).execute().use { r ->
            if (!r.isSuccessful) throw IOException("argentinadatos respondió ${r.code}")
            r.body?.string().orEmpty()
        }
        val lista = JSONArray(json)
        val mapa = java.util.TreeMap<String, Double>()
        for (i in 0 until lista.length()) {
            val o = lista.getJSONObject(i)
            val compra = o.optDouble("compra")
            if (compra.isFinite() && compra > 0) mapa[o.getString("fecha").take(10)] = compra
        }
        if (mapa.isEmpty()) throw IOException("La serie del dólar mayorista llegó vacía")
        return mapa
    }

    // --- Caché en disco ---

    private fun claveCultivo(cultivo: String) = cultivo.lowercase(Locale.ROOT).replace("í", "i")

    private fun archivo(clave: String) = File(directorio, "$clave.json")

    private fun guardarDisco(clave: String, serie: SerieHistorica) {
        try {
            directorio.mkdirs()
            val datos = JSONArray()
            serie.cotizaciones.forEach { c ->
                datos.put(JSONObject().put("fecha", c.fecha).put("usd", c.precioUsd).put("ars", c.precioArs).put("puerto", c.puerto))
            }
            archivo(clave).writeText(JSONObject().put("obtenida", serie.obtenida).put("datos", datos).toString())
        } catch (e: IOException) {
            Log.e(TAG, "No se pudo guardar la caché de $clave", e)
        }
    }

    private fun leerDisco(clave: String): SerieHistorica? = try {
        val f = archivo(clave)
        if (!f.exists()) null else {
            val json = JSONObject(f.readText())
            val datos = json.getJSONArray("datos")
            SerieHistorica(
                cotizaciones = (0 until datos.length()).map {
                    val o = datos.getJSONObject(it)
                    CotizacionHistorica(o.getString("fecha"), o.getDouble("usd"), o.getDouble("ars"), puerto = o.optString("puerto"))
                },
                obtenida = json.getLong("obtenida"),
                desactualizada = false
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Caché ilegible de $clave", e)
        null
    }

    companion object {
        private const val TAG = "GranexPizarra"
        private const val DIAS = 92
        private const val VIGENCIA_MS = 6 * 60 * 60 * 1000L
        private const val USER_AGENT = "GRANEX-Android/1.0 (com.aistudio.granex.agtech)"
        // Códigos de producto del formulario de consultas de la Cámara Arbitral
        private val PRODUCTOS = mapOf("trigo" to 8, "maiz" to 3, "girasol" to 9, "soja" to 13, "sorgo" to 6)

        /** Código de la Cámara Arbitral para el cultivo, o null si no tiene pizarra. */
        fun codigoProducto(cultivo: String): Int? = PRODUCTOS[cultivo.lowercase(Locale.ROOT).replace("í", "i")]
    }
}
