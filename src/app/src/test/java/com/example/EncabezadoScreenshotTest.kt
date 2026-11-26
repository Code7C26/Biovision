package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.GRANEXTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Antes / después del encabezado de cada pantalla. Robolectric no dibuja la barra de estado, así que
 * se simula con una franja de 24 dp: antes quedaba como franja vacía (en Satélite, dos veces); ahora
 * el fondo del encabezado se extiende detrás de ella.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class EncabezadoScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val barraEstado = 24.dp

    @Composable
    private fun BarraEstado(color: Color) {
        Box(Modifier.fillMaxWidth().height(barraEstado).background(color), contentAlignment = Alignment.CenterStart) {
            Text("  14:32", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    /** Copia del encabezado anterior, para comparar. */
    @Composable
    private fun EncabezadoAnterior(subtitulo: String, extra: (@Composable () -> Unit)? = null) {
        Surface(tonalElevation = 2.dp, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.logo_granex), null,
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("GRANEX", style = MaterialTheme.typography.titleLarge, color = AgroGreenPrimary, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(subtitulo, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (extra != null) extra() else IconButton(onClick = {}) { Icon(Icons.Default.Refresh, null, tint = AgroGreenPrimary) }
                IconButton(onClick = {}) { Icon(Icons.Default.AccountCircle, null, tint = AgroGreenPrimary, modifier = Modifier.size(30.dp)) }
            }
        }
    }

    @Composable
    private fun TarjetaEjemplo(titulo: String, icono: ImageVector) {
        Card(Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, null, tint = AgroGreenPrimary)
                Spacer(Modifier.width(8.dp))
                Text(titulo, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun Etiqueta(texto: String) {
        Text(texto, Modifier.fillMaxWidth().background(Color(0xFF263238)).padding(6.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    @Composable
    private fun Comparacion(
        antes: @Composable ColumnScope.() -> Unit,
        despues: @Composable ColumnScope.() -> Unit
    ) {
        GRANEXTheme {
            Column(Modifier.width(412.dp).background(MaterialTheme.colorScheme.background)) {
                Etiqueta("ANTES")
                Column(Modifier.height(260.dp).fillMaxWidth().background(MaterialTheme.colorScheme.background), content = antes)
                Etiqueta("DESPUÉS")
                Column(Modifier.height(260.dp).fillMaxWidth().background(MaterialTheme.colorScheme.background), content = despues)
            }
        }
    }

    @Composable
    private fun ColumnScope.Contenido(espacioArriba: Dp, titulo: String, icono: ImageVector, filaExtra: (@Composable () -> Unit)? = null) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = espacioArriba)) {
            filaExtra?.invoke()
            TarjetaEjemplo(titulo, icono)
            Spacer(Modifier.height(12.dp))
            TarjetaEjemplo("Segunda tarjeta", icono)
        }
    }

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        composeTestRule.setContent(contenido)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/encabezado_$nombre.png")
    }

    @Test
    fun finanzas() = capturar("finanzas") {
        Comparacion(
            antes = {
                BarraEstado(MaterialTheme.colorScheme.background)
                EncabezadoAnterior("Financiero & Futuros ROFEX")
                Contenido(16.dp, "Cotizaciones del dólar", Icons.Default.AttachMoney) {
                    // Fila de caché/mercado que antes ocupaba su propio renglón
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFDCFCE7)).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("En Tiempo Real (API Conectada)", style = MaterialTheme.typography.labelSmall)
                        Text("Mercado Abierto", style = MaterialTheme.typography.labelSmall, color = AgroGreenPrimary)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            },
            despues = {
                Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) { BarraEstado(Color.Transparent) }
                GranexHeader(
                    titulo = "Finanzas",
                    subtitle = "Dólar y futuros ROFEX",
                    chips = listOf(ChipEncabezado("Mercado abierto", punto = AgroGreenPrimary), ChipEncabezado("En vivo · 14:32", icono = Icons.Default.Sync)),
                    onRefreshClick = {}
                )
                Contenido(10.dp, "Cotizaciones del dólar", Icons.Default.AttachMoney)
            }
        )
    }

    @Test
    fun clima() = capturar("clima") {
        Comparacion(
            antes = {
                BarraEstado(MaterialTheme.colorScheme.background)
                EncabezadoAnterior("Clima & Mapa Windy", extra = { IconButton(onClick = {}) { Icon(Icons.Default.MyLocation, null, tint = AgroGreenPrimary) } })
                Contenido(16.dp, "Rosario · 18 °C", Icons.Default.WbSunny)
            },
            despues = {
                Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) { BarraEstado(Color.Transparent) }
                GranexHeader(
                    titulo = "Clima",
                    subtitle = "Pronóstico agroclimático",
                    chips = listOf(ChipEncabezado("Rosario", icono = Icons.Default.LocationOn), ChipEncabezado("Act. 14:30", icono = Icons.Default.Schedule)),
                    onRefreshClick = {},
                    rightContent = { IconButton(onClick = {}, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MyLocation, null, tint = AgroGreenPrimary) } }
                )
                Contenido(10.dp, "Rosario · 18 °C", Icons.Default.WbSunny)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun satelite() = capturar("satelite") {
        Comparacion(
            antes = {
                // Barra de estado reservada dos veces: por el Scaffold principal y por el TopAppBar
                BarraEstado(MaterialTheme.colorScheme.background)
                Box(Modifier.fillMaxWidth().height(barraEstado).background(MaterialTheme.colorScheme.surfaceContainerLow))
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SatelliteAlt, null, tint = AgroGreenPrimary, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("Análisis Satelital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Monitoreo de Lotes e Índices", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) }
                        IconButton(onClick = {}) { Icon(Icons.Default.Settings, null) }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
                Contenido(16.dp, "Gestión de Lote Agrícola", Icons.Default.GridOn)
            },
            despues = {
                Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) { BarraEstado(Color.Transparent) }
                GranexHeader(
                    titulo = "Satélite",
                    subtitle = "Monitoreo de lotes",
                    chips = listOf(
                        ChipEncabezado("El Trébol", icono = Icons.Default.Landscape),
                        ChipEncabezado("52,15 ha", icono = Icons.Default.CropFree),
                        ChipEncabezado("Sentinel-2 14 sep", icono = Icons.Default.SatelliteAlt)
                    ),
                    rightContent = { IconButton(onClick = {}, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = AgroGreenPrimary) } }
                )
                Contenido(10.dp, "Gestión de Lote Agrícola", Icons.Default.GridOn)
            }
        )
    }

    @Test
    fun mercado() = capturar("mercado") {
        val selector: @Composable () -> Unit = {
            OutlinedButton(onClick = {}, contentPadding = PaddingValues(start = 10.dp, end = 4.dp), modifier = Modifier.height(34.dp)) {
                Text("Soja", fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                Icon(Icons.Default.ArrowDropDown, null, tint = AgroGreenPrimary)
            }
        }
        Comparacion(
            antes = {
                BarraEstado(MaterialTheme.colorScheme.background)
                EncabezadoAnterior("Cotizaciones oficiales de granos.ar", extra = {
                    OutlinedButton(onClick = {}) { Text("Soja", fontWeight = FontWeight.Bold, color = AgroGreenPrimary); Icon(Icons.Default.ArrowDropDown, null, tint = AgroGreenPrimary) }
                })
                Contenido(16.dp, "Soja · US$ 342/t", Icons.Default.ShowChart)
            },
            despues = {
                Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) { BarraEstado(Color.Transparent) }
                GranexHeader(
                    titulo = "Mercado",
                    subtitle = "Pizarra Rosario",
                    chips = listOf(ChipEncabezado("Pizarra 28/07", icono = Icons.Default.Schedule), ChipEncabezado("US$ 342/t", punto = AgroGreenPrimary)),
                    onRefreshClick = {},
                    rightContent = selector
                )
                Contenido(10.dp, "Soja · US$ 342/t", Icons.Default.ShowChart)
            }
        )
    }

    @Test
    fun novedades() = capturar("novedades") {
        val buscador: @Composable (Dp, Dp, String) -> Unit = { arriba, abajo, ayuda ->
            OutlinedTextField(
                value = "", onValueChange = {}, placeholder = { Text(ayuda) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = arriba, bottom = abajo)
            )
        }
        Comparacion(
            antes = {
                BarraEstado(MaterialTheme.colorScheme.background)
                EncabezadoAnterior("Noticias & Novedades del Sector")
                buscador(8.dp, 8.dp, "Buscar por palabra clave, fuente o categoría...")
                Contenido(16.dp, "Primera noticia", Icons.Default.Newspaper)
            },
            despues = {
                Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) { BarraEstado(Color.Transparent) }
                GranexHeader(
                    titulo = "Novedades",
                    subtitle = "Noticias del agro",
                    chips = listOf(ChipEncabezado("24 noticias", icono = Icons.Default.Inbox), ChipEncabezado("3 fuentes", icono = Icons.Default.Share)),
                    onRefreshClick = {}
                )
                buscador(8.dp, 2.dp, "Buscar noticias, fuentes o temas")
                Contenido(10.dp, "Primera noticia", Icons.Default.Newspaper)
            }
        )
    }
}
