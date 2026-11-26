package com.example.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.web.plataforma.Navegador
import com.example.web.plataforma.PaginaPropia
import com.example.web.plataforma.TaparMarcos

/** Versión que se muestra al usuario (subirla junto con los cambios visibles). */
const val VERSION_WEB = "1.0 beta"

/** Servicio externo, para qué se usa y dónde ver sus condiciones. */
private data class Servicio(val nombre: String, val uso: String, val url: String)

private val SERVICIOS = listOf(
    Servicio("dolarapi.com", "Cotizaciones del dólar", "https://dolarapi.com/"),
    Servicio("Open-Meteo", "Pronóstico y búsqueda de localidades (CC BY 4.0)", "https://open-meteo.com/"),
    Servicio("Windy", "Mapa meteorológico interactivo", "https://www.windy.com/"),
    Servicio("granos.ar", "Pizarra de la Cámara Arbitral de Rosario", "https://granos.ar/"),
    Servicio("argentinadatos", "Dólar mayorista para el historial de la pizarra", "https://argentinadatos.com/"),
    Servicio("A3 Mercados", "Precios de ajuste de futuros, por el servidor de GRANEX", "https://a3mercados.com.ar/"),
    Servicio("Medios del agro", "Noticias de sus RSS oficiales, por el servidor de GRANEX", "https://www.clarin.com/rural/"),
    Servicio("Esri World Imagery", "Imagen satelital de base", "https://www.esri.com/"),
    Servicio("OpenStreetMap", "Mapa de calles y provincia de cada lote (Nominatim, ODbL)", "https://www.openstreetmap.org/copyright"),
    Servicio("Copernicus", "Imágenes Sentinel-2 y relieve (Copernicus DEM), por el servidor de GRANEX", "https://dataspace.copernicus.eu/"),
    Servicio("Supabase", "Servidor de cuentas y lotes de GRANEX", "https://supabase.com/")
)

/**
 * "Acerca de": qué es la versión web, de dónde salen los datos y qué se guarda en el navegador.
 * Lo que dice tiene que seguir siendo cierto: sin cookies ni analítica, y sin credenciales en la web.
 */
@Composable
fun DialogoAcercaDe(alCerrar: () -> Unit) {
    TaparMarcos()
    AlertDialog(
        onDismissRequest = alCerrar,
        icon = { LogoGranex(48) },
        title = { Text("GRANEX web") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Versión $VERSION_WEB. Dólar y futuros de granos, clima agro, mapa satelital, pizarra de " +
                        "Rosario y margen agrícola, noticias del agro y lotes con imágenes Sentinel-2 desde el " +
                        "navegador. Las prescripciones siguen por ahora en la app Android.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Titulo("Privacidad")
                Text(
                    "Sin cookies, analítica ni publicidad. Con tu cuenta se guardan tu correo, tu perfil " +
                        "y tus lotes para sincronizarlos con la app Android. En este navegador quedan el tema, la " +
                        "última localidad, tus lotes y la sesión. Nunca se piden contraseñas de servicios externos.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Titulo("Fuentes de datos")
                SERVICIOS.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(s.uso, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BotonIcono(IconosGranex.AbrirAfuera, "Abrir ${s.nombre}") { Navegador.abrirEnPestana(s.url) }
                    }
                }
                HorizontalDivider()
                Text(
                    "Tipografía Inter (SIL Open Font License 1.1; licencia en licencias/inter-OFL.txt).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaginaPropia.entries.forEach { pagina ->
                        TextButton(onClick = { Navegador.abrirPaginaPropia(pagina) }, modifier = Modifier.mano()) { Text(pagina.titulo) }
                    }
                }
                Text(
                    "Los precios y pronósticos son informativos: confirmalos con tu corredor o fuente " +
                        "oficial antes de operar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = alCerrar, modifier = Modifier.mano()) { Text("Cerrar") } }
    )
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LocalMarca.current.primario)
}
