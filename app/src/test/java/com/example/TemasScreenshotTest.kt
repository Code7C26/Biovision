package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroGoldLight
import com.example.ui.theme.AgroGreenDark
import com.example.ui.theme.AgroGreenLight
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.AgroSkyBlue
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Muestra de componentes con cada paleta: verifica contraste y sirve de catálogo visual. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class TemasScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun Muestra() {
        Column(Modifier.width(412.dp).background(MaterialTheme.colorScheme.background)) {
            GranexHeader(
                titulo = "Finanzas",
                subtitle = "Dólar y futuros ROFEX",
                chips = listOf(ChipEncabezado("Mercado abierto", punto = AgroGreenPrimary), ChipEncabezado("En vivo · 14:32", icono = Icons.Default.Sync)),
                onRefreshClick = {}
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tarjeta degradada como la del clima: texto blanco e ícono con acento claro
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(AgroGreenPrimary, AgroGreenDark))).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, null, tint = AgroGoldLight)
                        Spacer(Modifier.width(8.dp))
                        Text("Rosario", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("18 °C", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("Parcialmente nublado", color = Color.White.copy(alpha = 0.85f))
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(AgroGreenPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AttachMoney, null, tint = AgroGreenPrimary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Dólar oficial", fontWeight = FontWeight.Bold)
                            Text("Compra $ 1.010 · Venta $ 1.050", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("+0,8 %", color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)) { Text("Nuevo lote") }
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AgroGoldAccent, contentColor = Color.Black)) { Text("Guardar") }
                    OutlinedButton(onClick = {}) { Text("Cancelar") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = true, onClick = {}, label = { Text("NDVI") })
                    FilterChip(selected = false, onClick = {}, label = { Text("NDRE") })
                    Icon(Icons.Default.WaterDrop, null, tint = AgroSkyBlue)
                    Icon(Icons.Default.Landscape, null, tint = AgroGreenLight)
                    Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedTrackColor = AgroGreenPrimary))
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Icons.Default.AttachMoney to "Finanzas", Icons.Default.Cloud to "Clima", Icons.Default.Satellite to "Satélite", Icons.Default.ShowChart to "Mercado")
                    .forEachIndexed { i, (icono, texto) ->
                        val sel = i == 0
                        NavigationBarItem(
                            selected = sel, onClick = {},
                            icon = { Icon(icono, texto, tint = if (sel) AgroGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            label = { Text(texto, color = if (sel) AgroGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = AgroGreenPrimary.copy(alpha = 0.15f))
                        )
                    }
            }
        }
    }

    private fun capturarTema(tema: TemaGranex, oscuro: Boolean = false) {
        AppThemeState.tema = tema
        AppThemeState.isDark = oscuro
        composeTestRule.setContent { GRANEXTheme { Muestra() } }
        val sufijo = if (oscuro && tema.usaOscuro(false).not()) "_oscuro" else ""
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/tema_${tema.name.lowercase()}$sufijo.png")
    }

    @Test fun verdeCampo() = capturarTema(TemaGranex.VERDE_CAMPO)
    @Test fun verdeCampoOscuro() = capturarTema(TemaGranex.VERDE_CAMPO, oscuro = true)
    @Test fun tierra() = capturarTema(TemaGranex.TIERRA)
    @Test fun tierraOscuro() = capturarTema(TemaGranex.TIERRA, oscuro = true)
    @Test fun azulTecnico() = capturarTema(TemaGranex.AZUL_TECNICO)
    @Test fun solRadiante() = capturarTema(TemaGranex.ALTO_CONTRASTE)

    @Test
    fun selectorEnAjustes() {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        composeTestRule.setContent { GRANEXTheme { SettingsScreen(onBack = {}) } }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/tema_selector_ajustes.png")
    }

    /** Los colores de marca siguen al tema activo (base del cambio en vivo sin reiniciar). */
    @Test
    fun coloresDeMarcaSiguenAlTema() {
        AppThemeState.isDark = false
        AppThemeState.tema = TemaGranex.TIERRA
        assertEquals(TemaGranex.TIERRA.marca(false).primario, AgroGreenPrimary)
        AppThemeState.tema = TemaGranex.AZUL_TECNICO
        assertEquals(TemaGranex.AZUL_TECNICO.marca(true).primario, AgroGreenPrimary)
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
    }
}
