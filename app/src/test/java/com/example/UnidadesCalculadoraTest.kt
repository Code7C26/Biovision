package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.ui.components.CalculadoraMargen
import com.example.ui.components.convertirCampos
import com.example.ui.components.textoCampo
import com.example.ui.theme.GRANEXTheme
import com.example.utils.EntradaMargen
import com.example.utils.MargenAgricola
import com.example.utils.PrecioBase
import com.example.utils.UnidadPeso
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Toneladas ↔ quintales: 1 t = 1.000 kg = 10 qq (1 qq = 100 kg). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2600dp-xhdpi", sdk = [36])
class UnidadesCalculadoraTest {

    @get:Rule val compose = createComposeRule()

    private val qq = UnidadPeso.QUINTAL
    private val t = UnidadPeso.TONELADA

    @Test
    fun formulasDeConversion() {
        assertEquals(65.0, qq.cantidadDesdeTn(6.5), 1e-9)      // 6,5 t/ha = 65 qq/ha
        assertEquals(6.5, qq.cantidadATn(65.0), 1e-9)
        assertEquals(19.01, qq.precioDesdeTn(190.1), 1e-9)    // 190,1 US$/t = 19,01 US$/qq
        assertEquals(190.1, qq.precioATn(19.01), 1e-9)
        assertEquals(1000.0 / 100.0, qq.porTonelada, 0.0)      // kg por t ÷ kg por qq
        assertEquals(10.0, UnidadPeso.factorCantidad(t, qq), 0.0)
        assertEquals(0.1, UnidadPeso.factorCantidad(qq, t), 0.0)
        // El ingreso no depende de la unidad: cantidad × precio es invariante
        assertEquals(6.5 * 190.1, qq.cantidadDesdeTn(6.5) * qq.precioDesdeTn(190.1), 1e-9)
        // Tonelada es la identidad
        assertEquals(6.5, t.cantidadDesdeTn(6.5), 0.0)
        assertEquals(190.1, t.precioDesdeTn(190.1), 0.0)
    }

    @Test
    fun cambiarDeUnidadNoPierdeDatosYVuelveExacto() {
        val enToneladas = mapOf(
            "rinde" to "6,5", "precio" to "190,13", "secado" to "8", "fleteLargoUsd" to "22,5", "tarifaArsTn" to "31500",
            "superficie" to "100", "semilla" to "40", "comision" to "1,5", "fobbing" to ""
        )
        val enQuintales = convertirCampos(enToneladas, t, qq)
        assertEquals("65", enQuintales["rinde"])
        assertEquals("19,013", enQuintales["precio"])
        assertEquals("0,8", enQuintales["secado"])
        assertEquals("2,25", enQuintales["fleteLargoUsd"])
        assertEquals("3150", enQuintales["tarifaArsTn"])
        // Por hectárea, porcentajes y vacíos no cambian
        assertEquals("100", enQuintales["superficie"])
        assertEquals("40", enQuintales["semilla"])
        assertEquals("1,5", enQuintales["comision"])
        assertEquals("", enQuintales["fobbing"])
        // Ida y vuelta: exactamente lo que se había cargado
        assertEquals(enToneladas, convertirCampos(enQuintales, qq, t))
        assertEquals("19,01", textoCampo(19.01))
        assertEquals("65", textoCampo(65.0))
    }

    @Test
    fun elMargenEsElMismoEnToneladasQueEnQuintales() {
        fun entrada(unidad: UnidadPeso, rinde: Double, precio: Double, flete: Double) = EntradaMargen(
            superficieHa = 100.0, rindeTnHa = unidad.cantidadATn(rinde), precioBase = PrecioBase.PIZARRA,
            precioUsdTn = unidad.precioATn(precio), dexPct = 8.5, comisionPct = 1.5,
            fleteLargoUsdTn = unidad.precioATn(flete), costosDirectosUsdHa = 235.0, cosechaPct = 8.0, tipoCambio = 1495.0
        )
        val enT = MargenAgricola.calcular(entrada(t, 6.5, 190.1, 20.0))
        val enQq = MargenAgricola.calcular(entrada(qq, 65.0, 19.01, 2.0))
        assertEquals(enT.margenBrutoUsdHa, enQq.margenBrutoUsdHa, 1e-9)
        assertEquals(enT.margenNetoUsdHa, enQq.margenNetoUsdHa, 1e-9)
        assertEquals(enT.rindeIndiferenciaTotalTnHa!!, enQq.rindeIndiferenciaTotalTnHa!!, 1e-9)
    }

    @Test
    fun laPantallaMuestraYConvierteEnQuintales() {
        compose.setContent {
            GRANEXTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    CalculadoraMargen(cultivo = "Sorgo", precioInicialUsd = 190.1, tipoCambio = 1495.0, onCerrar = {})
                }
            }
        }
        compose.onNodeWithTag("input_hectareas").performTextReplacement("100")
        compose.onNodeWithTag("input_rendimiento").performTextReplacement("6,5")
        compose.onNodeWithText("Semilla").performScrollTo().performTextReplacement("235")
        compose.onNodeWithTag("input_cosecha").performScrollTo().performTextReplacement("8")

        compose.onNodeWithTag("unidad_quintal").performScrollTo().performClick()
        compose.onNodeWithTag("unidad_quintal").assertIsSelected()
        // Lo cargado se convirtió: 6,5 t/ha → 65 qq/ha; la pizarra 190,1 US$/t → 19,01 US$/qq
        compose.onNodeWithTag("input_rendimiento").assertTextContains("65")
        compose.onNodeWithTag("input_rendimiento").assertTextContains("qq/ha")
        compose.onNodeWithTag("input_precio_usd").assertTextContains("19,01")
        compose.onNodeWithTag("input_precio_usd").assertTextContains("US$/qq")
        // Resultado: neto en chacra 187,2485 US$/t = 18,72 US$/qq = $ 27.994/qq (dólar 1.495)
        compose.onNodeWithText("Del precio al neto en chacra (US$/qq)").performScrollTo().assertExists()
        compose.onNodeWithText("18,72").performScrollTo().assertExists()
        compose.onNodeWithText("En pesos: \$ 27.994/qq").performScrollTo().assertExists()
        // Rinde de indiferencia 1,366 t/ha = 13,66 qq/ha; el margen por ha no cambia (US$ 883)
        // Sin arrendamiento ni impuestos los dos rindes de indiferencia coinciden
        compose.onAllNodesWithText("13,66 qq/ha ✓").assertCountEquals(2)
        compose.onAllNodesWithText("US$ 883/ha").assertCountEquals(2) // bruto y neto (sin arrendamiento)
        compose.onNodeWithText("producción 6.500 qq", substring = true).performScrollTo().assertExists()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/calculadora_quintales.png")

        // Volver a toneladas restaura los valores originales
        compose.onNodeWithTag("unidad_tonelada").performScrollTo().performClick()
        compose.onNodeWithTag("input_rendimiento").assertTextContains("6,5")
        compose.onNodeWithTag("input_precio_usd").assertTextContains("190,1")
        compose.onNodeWithText("13,66 qq/ha ✓").assertDoesNotExist()
        compose.onAllNodesWithText("1,37 t/ha ✓").assertCountEquals(2)
    }
}
