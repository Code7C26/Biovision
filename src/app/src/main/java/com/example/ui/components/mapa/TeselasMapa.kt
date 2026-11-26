package com.example.ui.components.mapa

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Descarga las teselas del mapa con OkHttp en lugar de la red de la WebView. shouldInterceptRequest
 * corre en hilos de fondo de la WebView, así que la descarga nunca bloquea la interfaz.
 *
 * - Caché en disco de 150 MB que respeta los encabezados de caché de cada servidor; sin conexión
 *   se usa la tesela guardada aunque esté vencida, para seguir viendo el lote en el campo.
 * - Solo se permiten los servidores de teselas conocidos: cualquier otro pedido de la página se bloquea.
 * - Cliente propio sin el interceptor de logging (en debug registraría el binario de cada imagen).
 */
internal object TeselasMapa {
    private val SERVIDORES = setOf("api.maptiler.com", "server.arcgisonline.com", "tile.openstreetmap.org")
    // La política de uso de OpenStreetMap exige un User-Agent que identifique a la app
    private const val USER_AGENT = "GRANEX-Android/1.0 (com.aistudio.granex.agtech)"
    private const val TAMANO_CACHE = 150L * 1024 * 1024

    @Volatile
    private var cliente: OkHttpClient? = null

    /** Se llama (desde un hilo de fondo) cuando MapTiler rechaza la clave o se agotó la cuota. */
    @Volatile
    var alRechazarMapTiler: (() -> Unit)? = null

    private fun cliente(context: Context): OkHttpClient =
        cliente ?: synchronized(this) {
            cliente ?: OkHttpClient.Builder()
                .cache(Cache(File(context.applicationContext.cacheDir, "teselas_mapa"), TAMANO_CACHE))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
                .also { cliente = it }
        }

    fun responder(context: Context, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val host = url.host
        if (request.method != "GET" || url.scheme != "https" || host !in SERVIDORES) {
            return respuestaVacia(403, "Bloqueado")
        }
        val peticion = Request.Builder()
            .url(url.toString())
            .header("User-Agent", USER_AGENT)
            .apply {
                // La clave de MapTiler está restringida a este origen en el panel de MapTiler Cloud
                if (host == "api.maptiler.com") header("Referer", "$MAP_WEBVIEW_ORIGIN/")
            }
            .build()
        val http = cliente(context)
        return try {
            val respuesta = try {
                http.newCall(peticion).execute()
            } catch (e: IOException) {
                http.newCall(peticion.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()).execute()
            }
            // Distingue una clave inválida de la falta de señal (que llega como IOException o 504)
            if (host == "api.maptiler.com" && respuesta.code in setOf(401, 403, 429)) alRechazarMapTiler?.invoke()
            aRespuestaWeb(respuesta)
        } catch (e: IOException) {
            respuestaVacia(504, "Sin conexion")
        }
    }

    private fun aRespuestaWeb(respuesta: Response): WebResourceResponse {
        val cuerpo = respuesta.body
        // WebResourceResponse no acepta códigos 3xx (OkHttp ya siguió las redirecciones)
        val codigo = respuesta.code.takeIf { it in 100..299 || it in 400..599 } ?: 502
        val tipo = cuerpo?.contentType()
        val frase = respuesta.message.ifBlank { if (codigo < 400) "OK" else "Error" }
        val cabeceras = buildMap {
            respuesta.header("Cache-Control")?.let { put("Cache-Control", it) }
        }
        return WebResourceResponse(
            tipo?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
            null,
            codigo,
            frase,
            cabeceras,
            cuerpo?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
        )
    }

    fun respuestaVacia(codigo: Int, frase: String) =
        WebResourceResponse("text/plain", "utf-8", codigo, frase, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
