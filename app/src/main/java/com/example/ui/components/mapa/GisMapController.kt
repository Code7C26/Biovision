package com.example.ui.components.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.ui.components.release
import org.json.JSONException
import org.json.JSONObject

// Origen con el que la WebView carga el mapa. Las páginas y recursos se sirven desde los assets de
// la app bajo este dominio (nunca se consulta la red para él) y las teselas de MapTiler se piden con
// este Referer, así que la clave debe restringirse a este origen en el panel de MapTiler Cloud.
const val MAP_WEBVIEW_ORIGIN = "https://granex-agtech.app"
private const val MAP_HOST = "granex-agtech.app"
private const val MAPA_URL = "$MAP_WEBVIEW_ORIGIN/assets/mapa/mapa_gis.html"

/**
 * Convierte un valor a un literal de string JavaScript seguro para insertarlo en los scripts que se
 * envían a la WebView (escapa comillas, saltos de línea, "</script>" y los separadores U+2028/U+2029).
 */
fun jsString(value: String): String =
    JSONObject.quote(value)
        .replace(" ", "\\u2028")
        .replace(" ", "\\u2029")

/** Eventos que la página del mapa envía a la app. */
sealed interface EventoMapa {
    data class Toque(val lat: Double, val lon: Double) : EventoMapa
    data class VerticeTocado(val indice: Int) : EventoMapa
    data class Arrastrando(val indice: Int, val lat: Double, val lon: Double, val insercion: Boolean) : EventoMapa
    data class VerticeSoltado(val indice: Int, val lat: Double, val lon: Double, val insercion: Boolean) : EventoMapa
    data class LoteTocado(val id: String) : EventoMapa
    /** Toque con la detección automática activa: buscar el lote que hay en ese punto. */
    data class DetectarEn(val lat: Double, val lon: Double) : EventoMapa
    data class ProveedorFallido(val proveedor: String) : EventoMapa
}

/**
 * Dueño de la WebView del mapa GIS. Vive en [GisMapHolder] (alcance de la Activity), así que el mapa
 * no se destruye al cambiar de pestaña ni al pasar la pantalla por Loading/Error: la página se carga
 * una sola vez y todo cambio de estado se aplica por JavaScript. Los scripts enviados antes de que la
 * página avise que está lista se encolan.
 */
@Stable
class GisMapController internal constructor(context: Context) {
    private var paginaLista = false
    private val pendientes = ArrayDeque<String>()
    private val hiloPrincipal = Handler(Looper.getMainLooper())

    /** true hasta que la página del mapa terminó de inicializar Leaflet. */
    var isLoading by mutableStateOf(true)
        private set

    /** Centro del mapa (lat, lon), informado por la página cada vez que el mapa deja de moverse. */
    var centro: Pair<Double, Double>? by mutableStateOf(null)
        private set

    /** Receptor de eventos de la pantalla que está mostrando el mapa. */
    var onEvento: (EventoMapa) -> Unit = {}

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportZoom(false)
        settings.mediaPlaybackRequiresUserGesture = true
        setBackgroundColor(android.graphics.Color.rgb(11, 15, 12))
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        webViewClient = MapaWebViewClient(context.applicationContext)
        // El mapa está dentro de un LazyColumn: sin esto, los arrastres verticales desplazan la lista
        setOnTouchListener { v, evento ->
            if (evento.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        registrarPuente(this)
        loadUrl(MAPA_URL)
    }

    init {
        TeselasMapa.alRechazarMapTiler = {
            hiloPrincipal.post { onEvento(EventoMapa.ProveedorFallido("maptiler")) }
        }
    }

    private fun registrarPuente(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // Solo la página servida desde nuestro origen puede enviar mensajes; llegan en el hilo principal
            WebViewCompat.addWebMessageListener(webView, "GranexPuente", setOf(MAP_WEBVIEW_ORIGIN)) { _, mensaje, _, _, _ ->
                mensaje.data?.let(::recibirMensaje)
            }
        } else {
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun postMessage(mensaje: String) {
                    hiloPrincipal.post { recibirMensaje(mensaje) }
                }
            }, "GranexPuente")
        }
    }

    fun runScript(script: String) {
        if (paginaLista) ejecutar(script) else pendientes.addLast(script)
    }

    private fun ejecutar(script: String) {
        webView.evaluateJavascript(script) { forzarRedibujo() }
    }

    /**
     * Dentro de un AndroidView (y con la tarjeta recortando sus bordes) la WebView puede quedarse
     * mostrando el cuadro anterior hasta el próximo toque: se invalida la vista y su contenedor
     * para que Compose la vuelva a dibujar en cuanto la página terminó de pintar el cambio.
     */
    private fun forzarRedibujo() {
        webView.postInvalidateOnAnimation()
        (webView.parent as? View)?.postInvalidateOnAnimation()
    }

    private fun recibirMensaje(json: String) {
        val o = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return
        }
        when (o.optString("t")) {
            "listo" -> {
                paginaLista = true
                isLoading = false
                while (pendientes.isNotEmpty()) ejecutar(pendientes.removeFirst())
            }
            "pintado" -> forzarRedibujo()
            "centro" -> centro = o.optDouble("lat") to o.optDouble("lon")
            else -> parsearEvento(o)?.let { onEvento(it) }
        }
    }

    private fun parsearEvento(o: JSONObject): EventoMapa? {
        val lat = o.optDouble("lat")
        val lon = o.optDouble("lon")
        val indice = o.optInt("i", -1)
        val insercion = o.optBoolean("ins")
        return when (o.optString("t")) {
            "toque" -> EventoMapa.Toque(lat, lon)
            "deteccion" -> EventoMapa.DetectarEn(lat, lon)
            "verticeTocado" -> EventoMapa.VerticeTocado(indice)
            "arrastrando" -> EventoMapa.Arrastrando(indice, lat, lon, insercion)
            "verticeSoltado" -> EventoMapa.VerticeSoltado(indice, lat, lon, insercion)
            "loteTocado" -> o.optString("id").takeIf { it.isNotEmpty() }?.let { EventoMapa.LoteTocado(it) }
            else -> null
        }
    }

    /** Detiene timers, animaciones y descargas de esta WebView (no afecta a otras). */
    fun pausar() = webView.onPause()

    fun reanudar() = webView.onResume()

    internal fun liberar() {
        onEvento = {}
        TeselasMapa.alRechazarMapTiler = null
        hiloPrincipal.removeCallbacksAndMessages(null)
        webView.release()
    }
}

private class MapaWebViewClient(private val context: Context) : WebViewClient() {
    private val assets = WebViewAssetLoader.Builder()
        .setDomain(MAP_HOST)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        // blob: y data: (la capa espectral) los resuelve la WebView
        if (url.scheme != "https" && url.scheme != "http") return null
        if (url.host == MAP_HOST) {
            return assets.shouldInterceptRequest(url) ?: TeselasMapa.respuestaVacia(404, "No encontrado")
        }
        return TeselasMapa.responder(context, request)
    }

    // La WebView del mapa nunca navega a otra página
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
}

/**
 * Mantiene una única WebView del mapa por Activity. Se crea en GRANEXMainApp y la WebView recién se
 * construye la primera vez que se abre Satélite ([obtener]).
 */
@Stable
class GisMapHolder {
    var controller by mutableStateOf<GisMapController?>(null)
        private set

    fun obtener(context: Context): GisMapController =
        controller ?: GisMapController(context).also { controller = it }

    internal fun liberar() {
        controller?.liberar()
        controller = null
    }
}

@Composable
fun rememberGisMapHolder(): GisMapHolder {
    val holder = remember { GisMapHolder() }
    DisposableEffect(holder) {
        onDispose { holder.liberar() }
    }
    return holder
}
