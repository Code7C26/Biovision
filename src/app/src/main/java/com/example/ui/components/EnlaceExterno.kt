package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AgroGreenPrimary

/** Páginas oficiales de registro y claves de cada servicio (verificadas: responden 200). */
object EnlacesRegistro {
    /** Portada de Copernicus Data Space: el botón "Register" abre el alta (el enlace directo al formulario cambia). */
    const val COPERNICUS_REGISTRO = "https://dataspace.copernicus.eu/"
    /** Panel de Sentinel Hub: User settings → OAuth clients, para crear el client ID y el secret. */
    const val COPERNICUS_CLIENTES = "https://shapps.dataspace.copernicus.eu/dashboard/#/account/settings"
    const val MAPTILER_REGISTRO = "https://cloud.maptiler.com/auth/widget?mode=signup"
    const val OPEN_METEO_PLANES = "https://open-meteo.com/en/pricing"
}

/** Correo de soporte de GRANEX (también en la web, las páginas legales y la ficha de Play). */
const val EMAIL_SOPORTE = "supportgranex@gmail.com"

/** Abre la app de correo con un mensaje nuevo a soporte; sin app de correo, avisa con un Toast. */
@Composable
fun EnlaceSoporte(asunto: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClickLabel = "Escribir un correo", role = Role.Button) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(EMAIL_SOPORTE))
                    putExtra(android.content.Intent.EXTRA_SUBJECT, asunto)
                }
                try {
                    context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No hay una app de correo. Escribinos a $EMAIL_SOPORTE", Toast.LENGTH_LONG).show()
                }
            }
            .padding(vertical = 6.dp)
            .testTag("link_soporte")
    ) {
        Icon(Icons.Default.Email, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = EMAIL_SOPORTE,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AgroGreenPrimary,
            textDecoration = TextDecoration.Underline
        )
    }
}

/**
 * Enlace a una página externa: texto subrayado con el color de acento e ícono de "abrir afuera".
 * Abre el navegador del teléfono sin cerrar la app. Solo admite https (las URLs son constantes del
 * código, nunca texto ingresado por el usuario).
 */
@Composable
fun EnlaceExterno(
    texto: String,
    url: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    require(url.startsWith("https://")) { "Solo se abren enlaces https" }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val abrir = {
        try {
            uriHandler.openUri(url)
        } catch (e: Exception) {
            // Sin navegador instalado (o bloqueado por el perfil de trabajo): se avisa en lugar de cerrar la app
            Toast.makeText(context, "No hay un navegador para abrir $url", Toast.LENGTH_LONG).show()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClickLabel = "Abrir en el navegador", role = Role.Button, onClick = abrir)
            .padding(vertical = 6.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AgroGreenPrimary,
            textDecoration = TextDecoration.Underline
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Abre una página externa",
            tint = AgroGreenPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
}
