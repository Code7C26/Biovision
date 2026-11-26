package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.remote.model.DolarRateDto
import com.example.data.remote.model.EstadoRofex
import com.example.data.remote.model.FinancialDashboardData
import com.example.data.remote.model.FuturoRofexContract
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.screens.FinancialContent
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

/**
 * Finanzas en teléfonos angostos, con la letra o la pantalla agrandadas en Ajustes de Android, y
 * apaisado. Con EscalaPorPantalla todos deben verse como en el Pixel (411 dp), en proporción: dos
 * cuadros por fila y los precios enteros. Apaisado, solo hay más ancho (cinco cuadros por fila).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class FinanzasMaquetacionScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val datos = FinancialDashboardData(
        divisas = listOf(
            DolarRateDto(casa = "oficial", nombre = "Dólar Oficial", compra = 1510.0, venta = 1560.0, fechaActualizacion = "2026-10-29T17:00:00.000Z"),
            DolarRateDto(casa = "blue", nombre = "Dólar Blue", compra = 1545.0, venta = 1565.0, fechaActualizacion = "2026-10-29T20:00:00.000Z"),
            DolarRateDto(casa = "bolsa", nombre = "Dólar MEP", compra = 1557.8, venta = 1568.8, fechaActualizacion = "2026-10-29T20:00:00.000Z", variacion = -0.27),
            DolarRateDto(casa = "contadoconliqui", nombre = "Dólar CCL", compra = 1649.8, venta = 1650.3, fechaActualizacion = "2026-10-29T20:00:00.000Z", variacion = -0.91),
            DolarRateDto(casa = "tarjeta", nombre = "Dólar Tarjeta", compra = 1963.0, venta = 2028.0, fechaActualizacion = "2026-10-29T17:00:00.000Z"),
        ),
        rofex = EstadoRofex.Ok(
            listOf(
                FuturoRofexContract("SOJ.ROS/NOV26", "Soja", "Noviembre 2026", 356.0, 1.92, 200, fechaAjuste = "2026-11-11"),
                FuturoRofexContract("SOJ.ROS/ENE27", "Soja", "Enero 2027", 367.2, null, 0),
                FuturoRofexContract("MAI.ROS/DIC26", "Maíz", "Diciembre 2026", 184.0, 0.55, 230),
            ),
            actualizado = 0L
        )
    )

    private fun capturar(nombre: String) {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = false
        compose.setContent {
            GRANEXTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        // Encabezado como el de Mercado: el selector de grano le quita lugar al título
                        GranexHeader(
                            titulo = "Mercado",
                            subtitle = "",
                            chips = listOf(ChipEncabezado("Pizarra 29/10"), ChipEncabezado("Mercado cerrado")),
                            onRefreshClick = {},
                            rightContent = { AssistChip(onClick = {}, label = { Text("Soja") }) }
                        )
                        FinancialContent(data = datos, onRefresh = {}, onNavigateToSettings = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/finanzas_$nombre.png")
    }

    /** "Tamaño de pantalla" al máximo en un teléfono de 393 dp lo deja en unos 320 dp. */
    @Test @Config(qualifiers = "w320dp-h1200dp-xhdpi", fontScale = 1.15f)
    fun pantallaAgrandada() = capturar("320dp_letra115")

    @Test @Config(qualifiers = "w360dp-h1000dp-xhdpi")
    fun angosto() = capturar("360dp")

    @Test @Config(qualifiers = "w360dp-h1300dp-xhdpi", fontScale = 1.3f)
    fun angostoLetraGrande() = capturar("360dp_letra130")

    @Test @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    fun comoElPixel() = capturar("411dp")

    @Test @Config(qualifiers = "w411dp-h1300dp-xxhdpi", fontScale = 1.3f)
    fun letraGrande() = capturar("411dp_letra130")

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun apaisado() = capturar("apaisado")
}
