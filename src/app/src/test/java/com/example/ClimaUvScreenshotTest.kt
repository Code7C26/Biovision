package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.remote.model.NivelUv
import com.example.ui.screens.AgroMetricCard
import com.example.ui.screens.TarjetaRadiacionUv
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Grilla de variables agrometeorológicas (mismo layout que la pantalla) con la tarjeta UV a todo el ancho. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1400dp-xhdpi", sdk = [36])
class ClimaUvScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Grilla(ancho: Dp, uv: Double?, uvMax: Double?) {
        Column(
            modifier = Modifier.width(ancho).background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                listOf(Triple(Icons.Default.WaterDrop, "Humedad Relativa", "62%" to "Óptima aplicación"),
                    Triple(Icons.Default.Air, "Viento", "14.2 km/h" to "SE (135°) • Ráf. 31")),
                listOf(Triple(Icons.Default.Thermostat, "Sensación Térmica", "21.4°C" to "Nubosidad 40%"),
                    Triple(Icons.Default.WaterDrop, "Lluvia Hoy", "0.0 mm" to "Probabilidad 10%")),
                listOf(Triple(Icons.Default.Compress, "Presión Atmosférica", "1016 hPa" to "Normal"),
                    Triple(Icons.Default.Warning, "Riesgo Incendio", "Moderado" to "Monitorear rastrojo"))
            ).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    fila.forEach { (icono, titulo, valores) ->
                        AgroMetricCard(icono, titulo, valores.first, valores.second, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            TarjetaRadiacionUv(uvActual = uv, uvMaxHoy = uvMax)
        }
    }

    private fun capturar(nombre: String, ancho: Dp, uv: Double?, uvMax: Double?) {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        compose.setContent { GRANEXTheme { Grilla(ancho, uv, uvMax) } }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/clima_uv_$nombre.png")
    }

    @Test fun uvAltoEn360dp() = capturar("alto_360", 360.dp, 6.5, 8.2)
    @Test fun uvAltoEn412dp() = capturar("alto_412", 412.dp, 6.5, 8.2)
    @Test fun nocheMuestraMaximoDelDia() = capturar("noche_412", 412.dp, 0.0, 9.1)

    @Test
    fun categoriasDeLaOms() {
        assertEquals(NivelUv.BAJO, NivelUv.de(2.9))
        assertEquals(NivelUv.MODERADO, NivelUv.de(3.0))
        assertEquals(NivelUv.ALTO, NivelUv.de(6.5))
        assertEquals(NivelUv.MUY_ALTO, NivelUv.de(10.9))
        assertEquals(NivelUv.EXTREMO, NivelUv.de(11.0))
    }
}
