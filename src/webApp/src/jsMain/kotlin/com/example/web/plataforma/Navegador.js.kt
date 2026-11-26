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

// Misma lógica que Navegador.wasmJs.kt con los tipos DOM de Kotlin/JS (respaldo para navegadores sin WasmGC).

actual object Navegador {
    private val oyentesRuta = mutableListOf<(String) -> Unit>()
    private val oyentesMensaje = mutableListOf<(MessageEvent) -> Unit>()
    private val oyentesConexion = mutableListOf<(Boolean) -> Unit>()
    private val oyentesRegreso = mutableListOf<() -> Unit>()

    init {
        window.addEventListener("hashchange", { _ -> oyentesRuta.toList().forEach { it(rutaActual()) } })
        window.addEventListener("message", { e -> (e as? MessageEvent)?.let { m -> oyentesMensaje.toList().forEach { it(m) } } })
        window.addEventListener("online", { _ -> oyentesConexion.toList().forEach { it(true) } })
        window.addEventListener("offline", { _ -> oyentesConexion.toList().forEach { it(false) } })
        document.addEventListener("visibilitychange", { _ ->
            if (document.asDynamic().visibilityState == "visible") oyentesRegreso.toList().forEach { it() }
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
        val base64 = Base64.encode(bytes)
        // js() de Kotlin/JS solo acepta ES5: sin const ni flechas
        js(
            """(function () {
                var bin = atob(base64);
                var datos = new Uint8Array(bin.length);
                for (var i = 0; i < bin.length; i++) datos[i] = bin.charCodeAt(i);
                var url = URL.createObjectURL(new Blob([datos], { type: tipo }));
                var a = document.createElement('a');
                a.href = url;
                a.download = nombre;
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.parentNode.removeChild(a);
                setTimeout(function () { URL.revokeObjectURL(url); }, 30000);
            })()"""
        )
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

actual fun crearAjustesNavegador(): Settings? =
    try {
        StorageSettings()
    } catch (e: Throwable) {
        null
    }

@OptIn(ExperimentalComposeUiApi::class)
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
            entregarCanal(CanalMarco { json -> iframe.contentWindow?.postMessage(json, origen) })
            val dejar = Navegador.escucharMensajes { m ->
                // Solo mensajes de ESTE iframe (=== en JS) y del mismo origen
                if (m.origin == origen && m.source == iframe.contentWindow) (m.data as? String)?.let { receptor?.invoke(it) }
            }
            onDispose {
                dejar()
                entregarCanal(null)
            }
        }
    }
}

/** Misma decodificación que en Wasm (Navegador.wasmJs.kt), con los tipos de Kotlin/JS. */
@OptIn(ExperimentalEncodingApi::class)
actual suspend fun decodificarPng(png: ByteArray): PixelesPng? = try {
    val base64 = Base64.encode(png)
    // js() de Kotlin/JS solo acepta ES5: sin async, const ni flechas (por eso .then)
    val promesa: Promise<Int32Array> = js(
        """(function () {
            var bin = atob(base64);
            var bytes = new Uint8Array(bin.length);
            for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
            return createImageBitmap(new Blob([bytes], { type: 'image/png' }),
                { premultiplyAlpha: 'none', colorSpaceConversion: 'none' }).then(function (bmp) {
                var lienzo = new OffscreenCanvas(bmp.width, bmp.height);
                var ctx = lienzo.getContext('2d', { willReadFrequently: true });
                ctx.drawImage(bmp, 0, 0);
                var d = ctx.getImageData(0, 0, bmp.width, bmp.height).data;
                var n = bmp.width * bmp.height;
                var salida = new Int32Array(n + 2);
                salida[0] = bmp.width;
                salida[1] = bmp.height;
                for (var k = 0; k < n; k++) salida[k + 2] = (d[4 * k + 3] << 24) | (d[4 * k] << 16) | (d[4 * k + 1] << 8) | d[4 * k + 2];
                bmp.close();
                return salida;
            });
        })()"""
    ).unsafeCast<Promise<Int32Array>>()
    val datos = promesa.await()
    val ancho = datos[0]
    val alto = datos[1]
    PixelesPng(ancho, alto, IntArray(ancho * alto) { datos[it + 2] })
} catch (e: Throwable) {
    null
}
