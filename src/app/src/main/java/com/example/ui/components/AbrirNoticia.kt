package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.net.URI

/**
 * URL de una nota apta para abrir: https y con host. Devuelve null para enlaces vacíos, relativos,
 * de otros esquemas (javascript:, intent:, file:) o mal formados. Pura: testeada en NoticiasTest.
 */
fun urlNavegable(url: String?): String? {
    val limpia = url?.trim().orEmpty()
    if (limpia.isEmpty()) return null
    val uri = try { URI(limpia.replace(" ", "%20")) } catch (e: Exception) { return null }
    return limpia.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() }
}

/**
 * Abre la nota en una Custom Tab (Chrome u otro navegador compatible): rápida, con el color de la
 * app y volviendo a GRANEX con "atrás". Sin navegador con Custom Tabs usa ACTION_VIEW; si tampoco
 * hay, o la URL no es válida, avisa con un Toast en lugar de cerrar la app.
 */
fun abrirNoticia(context: Context, url: String?, colorBarra: Color) {
    val segura = urlNavegable(url)
    if (segura == null) {
        Toast.makeText(context, "El enlace de esta noticia no es válido", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = Uri.parse(segura)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(colorBarra.toArgb()).build())
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            Log.w("GranexNoticias", "Sin navegador para $segura", e2)
            Toast.makeText(context, "No hay un navegador para abrir la noticia", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Log.w("GranexNoticias", "No se pudo abrir $segura", e)
        Toast.makeText(context, "No se pudo abrir la noticia", Toast.LENGTH_SHORT).show()
    }
}
