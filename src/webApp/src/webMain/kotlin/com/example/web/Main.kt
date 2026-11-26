package com.example.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Punto de entrada (Wasm y JS). Compose dibuja en un canvas dentro de <div id="granex"> y no en <body>:
 * así la pantalla de carga y el aviso de versión nueva de index.html siguen siendo HTML visible.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val contenedor = ContenedorWeb()
    ComposeViewport(viewportContainerId = "granex") { GranexWeb(contenedor) }
}
