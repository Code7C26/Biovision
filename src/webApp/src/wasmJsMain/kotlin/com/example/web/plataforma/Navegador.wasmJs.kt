package com.example.web.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import com.example.utils.esUrlExternaSegura
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.Int32Array
import org.khronos.webgl.get
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.MessageEvent

actual object Navegador {
    private val oyentesRuta = mutableListOf<(String) -> Unit>()
    private val oyentesMensaje = mutableListOf<(MessageEvent) -> Unit>()
    private val oyentesConexion = mutableListOf<(Boolean) -> Unit>()
    private val oyentesRegreso = mutableListOf<() -> Unit>()

    // Un solo oyente por tipo de evento durante toda la vida de la página: las lambdas de Kotlin se
    // envuelven en funciones JS nuevas en cada llamada, así que removeEventListener no las encontraría.
    init {
        window.addEventListener("hashchange", { _ -> oyentesRuta.toList().forEach { it(rutaActual()) } })
        window.addEventListener("message", { e -> (e as? MessageEvent)?.let { m -> oyentesMensaje.toList().forEach { it(m) } } })
        window.addEventListener("online", { _ -> oyentesConexion.toList().forEach { it(true) } })
        window.addEventListener("offline", { _ -> oyentesConexion.toList().forEach { it(false) } })
        document.addEventListener("visibilitychange", { _ ->
            if (paginaVisible()) oyentesRegreso.toList().forEach { it() }
        })
    }

    actual fun rutaActual(): String = window.location.hash.removePrefix("#").removePrefix("/")

    actual fun irARuta(ruta: String) {
        window.location.hash = "/$ruta"
    }

    actual fun escucharRuta(alCambiar: (String) -> Unit) {
        oyentesRuta += alCambiar
    }

    actual fun abrirEnPestana(url: String) {
        if (esUrlExternaSegura(url)) window.open(url, "_blank", "noopener,noreferrer")
    }

    actual fun abrirPaginaPropia(pagina: PaginaPropia) {
        window.open(pagina.ruta, "_blank", "noopener")
    }

    actual fun ocultarPantallaDeCarga() {
        document.getElementById("cargando")?.classList?.add("oculto")
    }

    actual fun ponerTitulo(titulo: String) {
        document.title = titulo
    }

    actual fun ponerTemaDocumento(oscuro: Boolean, colorHex: String) {
        document.documentElement?.setAttribute("data-tema", if (oscuro) "oscuro" else "claro")
        val metas = document.querySelectorAll("meta[name=theme-color]")
        for (i in 0 until metas.length) {
            (metas.item(i) as? Element)?.let {
                // Sin "media": el color sigue al tema elegido en la app, no al del sistema
                it.removeAttribute("media")
                it.setAttribute("content", colorHex)
            }
        }
    }

    actual fun prefiereMenosMovimiento(): Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches

    actual fun conectado(): Boolean = window.navigator.onLine

    actual fun recargar() {
        window.location.reload()
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual fun descargarArchivo(nombre: String, bytes: ByteArray, tipo: String) {
        descargarJs(Base64.encode(bytes), nombre, tipo)
    }

    actual fun escucharConexion(alCambiar: (Boolean) -> Unit) {
        oyentesConexion += alCambiar
    }

    actual fun escucharRegreso(alVolver: () -> Unit) {
        oyentesRegreso += alVolver
    }

    internal fun escucharMensajes(oyente: (MessageEvent) -> Unit): () -> Unit {
        oyentesMensaje += oyente
        return { oyentesMensaje -= oyente }
    }
}

/** true si el mensaje lo mandó la página de [marco] (=== en JS: el == de Kotlin/Wasm no compara referencias externas). */
@OptIn(ExperimentalWasmJsInterop::class)
private fun vieneDe(evento: MessageEvent, marco: HTMLIFrameElement): Boolean = js("evento.source === marco.contentWindow")

@OptIn(ExperimentalWasmJsInterop::class)
private fun paginaVisible(): Boolean = js("document.visibilityState === 'visible'")

actual fun crearAjustesNavegador(): Settings? =
    try {
        StorageSettings()
    } catch (e: Throwable) {
        null
    }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
@Composable
actual fun MarcoWeb(
    url: String,
    titulo: String,
    modifier: Modifier,
    externo: Boolean,
    alMensaje: ((String) -> Unit)?,
    alCanal: (CanalMarco?) -> Unit
) {
    val receptor by rememberUpdatedState(alMensaje)
    val entregarCanal by rememberUpdatedState(alCanal)
    var marco by remember { mutableStateOf<HTMLIFrameElement?>(null) }

    WebElementView(
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                title = titulo
                if (externo) {
                    setAttribute("sandbox", SANDBOX_EXTERNO)
                    setAttribute("referrerpolicy", "strict-origin-when-cross-origin")
                }
                src = url
                style.cssText = "border:0;width:100%;height:100%;display:block"
                marco = this
            }
        },
        modifier = modifier,
        update = {
            if (it.getAttribute("src") != url) it.src = url
            if (it.title != titulo) it.title = titulo
        },
        onRelease = { marco = null }
    )

    val oculto = CapasSobreMarcos.abiertas > 0
    // Se oculta el contenedor que Compose posiciona sobre el canvas (no solo el iframe): si no, ese
    // div seguiría tapando la zona del mapa y se quedaría con los clics dirigidos al diálogo
    LaunchedEffect(marco, oculto) {
        val contenedor = marco?.parentElement as? HTMLElement ?: return@LaunchedEffect
        if (oculto) contenedor.style.setProperty("visibility", "hidden") else contenedor.style.removeProperty("visibility")
    }

    DisposableEffect(marco) {
        val iframe = marco
        if (iframe == null) {
            onDispose { }
        } else {
            val origen = window.location.origin
            entregarCanal(CanalMarco { json -> iframe.contentWindow?.postMessage(json.toJsString(), origen) })
            val dejar = Navegador.escucharMensajes { m ->
                if (m.origin == origen && vieneDe(m, iframe)) {
                    (m.data as? JsString)?.toString()?.let { receptor?.invoke(it) }
                }
            }
            onDispose {
                dejar()
                entregarCanal(null)
            }
        }
    }
}

/** [base64] → Int32Array con [ancho, alto, argb…] (el bucle por píxel corre en JS, no por interop). */
@OptIn(ExperimentalWasmJsInterop::class)
private fun decodificarPngJs(base64: String): Promise<JsAny?> = js(
    """(async () => {
        const bin = atob(base64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const bmp = await createImageBitmap(new Blob([bytes], { type: 'image/png' }),
            { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
        const lienzo = new OffscreenCanvas(bmp.width, bmp.height);
        const ctx = lienzo.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(bmp, 0, 0);
        const d = ctx.getImageData(0, 0, bmp.width, bmp.height).data;
        const n = bmp.width * bmp.height;
        const salida = new Int32Array(n + 2);
        salida[0] = bmp.width;
        salida[1] = bmp.height;
        for (let i = 0; i < n; i++) salida[i + 2] = (d[4 * i + 3] << 24) | (d[4 * i] << 16) | (d[4 * i + 1] << 8) | d[4 * i + 2];
        bmp.close();
        return salida;
    })()"""
)

@OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)
actual suspend fun decodificarPng(png: ByteArray): PixelesPng? = try {
    val datos = decodificarPngJs(Base64.encode(png)).await<JsAny?>() as Int32Array
    val ancho = datos[0]
    val alto = datos[1]
    PixelesPng(ancho, alto, IntArray(ancho * alto) { datos[it + 2] })
} catch (e: Throwable) {
    null
}

/** Blob con los bytes y un enlace con "download": el navegador lo guarda sin abrir otra página. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun descargarJs(base64: String, nombre: String, tipo: String): Unit = js(
    """{
        const bin = atob(base64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const url = URL.createObjectURL(new Blob([bytes], { type: tipo }));
        const a = document.createElement('a');
        a.href = url;
        a.download = nombre;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 30000);
    }"""
)
