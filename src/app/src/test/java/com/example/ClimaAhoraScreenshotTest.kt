package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.remote.model.AgroClimateDashboard
import com.example.ui.screens.MetricasAhora
import com.example.ui.screens.TarjetaAhora
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El clima de ahora de Android con el diseño de la web: tarjeta destacada y los seis datos con su lectura. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1400dp-xhdpi", sdk = [36])
class ClimaAhoraScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val altaGracia = AgroClimateDashboard(
        ciudad = "Alta Gracia", lat = -31.66, lon = -64.43,
        tempCelsius = 19.7, tempMin = 16.0, tempMax = 24.0,
        humedadPorcentaje = 68, presionHpa = 1018,
        vientoKmH = 4.7, direccionVientoGrados = 360,
        condicionTexto = "Despejado", evapotranspiracionET0 = 4.2, riesgoFuego = "Bajo",
        codigoClima = 0, esDeDia = true, sensacionTermica = 20.1, rafagasKmH = 13.0,
        nubosidadPorcentaje = 17, lluviaHoyMm = 1.4, probLluviaHoy = 51
    )

    private fun capturar(d: AgroClimateDashboard, oscuro: Boolean, archivo: String, textos: List<String>) {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = oscuro
        compose.setContent {
            GRANEXTheme {
                Column(
                    Modifier.width(412.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TarjetaAhora(d, favorita = true, onFavorita = {}, onAbrir = {})
                    MetricasAhora(d)
                }
            }
        }
        (listOf("Humedad relativa", "N (360°) · Ráf. 13", "Nubosidad 17 %", "Probabilidad 51 %", "1.018 hPa") + textos)
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/$archivo")
    }

    @Test
    fun deDiaOscuro() = capturar(altaGracia, oscuro = true, "clima_ahora_oscuro.png", listOf("Óptima aplicación", "Baja alerta"))

    @Test
    fun deNocheClaro() = capturar(
        altaGracia.copy(esDeDia = false, riesgoFuego = "Alto", humedadPorcentaje = 35),
        oscuro = false, "clima_ahora_noche_claro.png", listOf("Extrema para aplicar", "Monitorear rastrojo")
    )
}
