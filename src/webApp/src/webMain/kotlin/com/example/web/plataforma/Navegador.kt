package com.example.web.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.russhwolf.settings.Settings

/*
 * Lo que depende del DOM. Wasm (kotlinx-browser) y JS (stdlib) exponen el DOM con tipos distintos,
 * así que cada target tiene su implementación: wasmJsMain/.../Navegador.wasmJs.kt y jsMain/.../Navegador.js.kt.
 */

/** Navegación por el fragmento de la URL (#/clima), así funcionan el botón Atrás y los enlaces directos. */
expect object Navegador {
    /** Sección del fragmento actual ("#/clima" → "clima"). */
    fun rutaActual(): String

    fun irARuta(ruta: String)

    /** [alCambiar] recibe la ruta nueva cada vez que cambia el fragmento (Atrás, Adelante, enlace). */
    fun escucharRuta(alCambiar: (String) -> Unit)

    /**
     * Abre un sitio externo en otra pestaña, sin darle acceso a esta (noopener, sin referrer).
     * Solo acepta direcciones https con dominio (esUrlExternaSegura, en :shared); el resto se ignora.
     */
    fun abrirEnPestana(url: String)

    /** Abre una página propia del sitio (las legales) en otra pestaña. */
    fun abrirPaginaPropia(pagina: PaginaPropia)

    /** Desvanece la pantalla de carga de index.html (se llama cuando Compose ya dibujó). */
    fun ocultarPantallaDeCarga()

    /** Título de la pestaña y del historial ("Clima · GRANEX"). */
    fun ponerTitulo(titulo: String)

    /**
     * Refleja el tema de la app en el documento: color de la barra del sistema / de la ventana
     * instalada (meta theme-color, "#RRGGBB") y data-tema en <html>, que usan los avisos HTML.
     */
    fun ponerTemaDocumento(oscuro: Boolean, colorHex: String)

    /** prefers-reduced-motion: el usuario pidió menos animaciones en su sistema. */
    fun prefiereMenosMovimiento(): Boolean

    /** navigator.onLine: false cuando el navegador sabe que no hay red. */
    fun conectado(): Boolean

    /** [alCambiar] recibe el estado nuevo con cada evento online/offline. */
    fun escucharConexion(alCambiar: (Boolean) -> Unit)

    /** [alVolver] se llama cada vez que la pestaña vuelve a verse (desbloquear el teléfono, volver de otra app). */
    fun escucharRegreso(alVolver: () -> Unit)

    /** Vuelve a cargar la página desde cero (después de cerrar sesión no queda nada de la cuenta en memoria). */
    fun recargar()

    /** Descarga [bytes] como un archivo [nombre] (la carpeta de Descargas del navegador). */
    fun descargarArchivo(nombre: String, bytes: ByteArray, tipo: String)
}

/** Páginas estáticas que se publican junto a la app (webMain/resources/legal). Solo estas se abren con abrirPaginaPropia. */
enum class PaginaPropia(val ruta: String, val titulo: String) {
    PRIVACIDAD("legal/privacidad.html", "Política de privacidad"),
    TERMINOS("legal/terminos.html", "Términos de servicio")
}

/** Píxeles de un PNG en ARGB y sin premultiplicar, como Bitmap.getPixels en Android. */
class PixelesPng(val ancho: Int, val alto: Int, val argb: IntArray)

/**
 * Decodifica un PNG con el decodificador del navegador (createImageBitmap sin premultiplicar ni
 * convertir el color, así los valores de cada canal llegan exactos). null si no es una imagen válida.
 */
expect suspend fun decodificarPng(png: ByteArray): PixelesPng?

/** Preferencias en localStorage; null si el navegador no lo permite (modo privado estricto, cookies bloqueadas). */
expect fun crearAjustesNavegador(): Settings?

/** Canal hacia una página del mismo origen incrustada en un iframe (el mapa GIS). */
fun interface CanalMarco {
    /** Envía un texto JSON a la página por postMessage (destino restringido al mismo origen). */
    fun enviar(mensajeJson: String)
}

/**
 * Permisos de un iframe de otro sitio (Windy): puede ejecutar sus scripts, usar su propio origen
 * (cookies y almacenamiento de su dominio) y abrir enlaces en pestañas nuevas, pero no navegar ni
 * redirigir la página de GRANEX, ni abrir formularios o diálogos sobre ella.
 */
internal const val SANDBOX_EXTERNO = "allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox"

/**
 * Diálogos y menús de Compose abiertos en este momento. Compose dibuja en un canvas y los iframes
 * (mapa, Windy) son HTML encima del canvas: un diálogo que pasara sobre el mapa quedaría tapado.
 * Mientras haya alguno, [MarcoWeb] oculta su iframe.
 */
object CapasSobreMarcos {
    var abiertas by mutableStateOf(0)
        private set

    internal fun sumar() {
        abiertas++
    }

    internal fun restar() {
        abiertas = (abiertas - 1).coerceAtLeast(0)
    }
}

/** Llamarlo dentro de cada diálogo o menú desplegable: oculta los iframes mientras está abierto. */
@Composable
fun TaparMarcos() {
    DisposableEffect(Unit) {
        CapasSobreMarcos.sumar()
        onDispose { CapasSobreMarcos.restar() }
    }
}

/**
 * Página web (iframe) dentro de la UI de Compose. Compose dibuja en un canvas: el iframe se superpone
 * en el lugar y tamaño del composable (WebElementView).
 * - [externo] = sitio de otro dominio: se carga con sandbox ([SANDBOX_EXTERNO]), sin enviar la
 *   dirección de GRANEX como referrer completo y sin permisos de cámara, ubicación, etc.
 * - [alMensaje] recibe lo que la página publica con `parent.postMessage`, solo si viene de ESTE
 *   iframe y del mismo origen: ni Windy ni otra ventana pueden enviarle mensajes a la app.
 * - [alCanal] entrega el canal hacia la página al crearse el iframe y null al quitarlo.
 * - Se oculta (visibility: hidden en su contenedor, sin perder su estado ni tapar clics) mientras haya diálogos ([TaparMarcos]).
 */
@Composable
expect fun MarcoWeb(
    url: String,
    titulo: String,
    modifier: Modifier = Modifier,
    externo: Boolean = false,
    alMensaje: ((String) -> Unit)? = null,
    alCanal: (CanalMarco?) -> Unit = {}
)
