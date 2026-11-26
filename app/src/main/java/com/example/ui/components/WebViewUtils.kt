package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebView

/**
 * Las WebViews que viven dentro de un LazyColumn se crean fuera del item (con remember en el
 * composable padre) para no recrearlas y recargarlas en cada scroll. Al volver el item a la
 * composición, el AndroidView nuevo recibe la misma instancia, que debe quitarse primero del
 * contenedor anterior.
 */
fun WebView.detachFromParent(): WebView {
    (parent as? ViewGroup)?.removeView(this)
    return this
}

/** Libera la WebView: detiene cargas y timers de JavaScript y devuelve su memoria nativa. */
fun WebView.release() {
    detachFromParent()
    stopLoading()
    removeAllViews()
    destroy()
}
