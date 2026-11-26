package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.ui.components.CalculadoraMargen
import com.example.ui.theme.GRANEXTheme
import com.example.utils.DerechosExportacion
import com.example.utils.EntradaMargen
import com.example.utils.ImpuestosProvinciales
import com.example.utils.MargenAgricola
import com.example.utils.PrecioBase
import com.example.utils.RubroCosto
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2600dp-xhdpi", sdk = [36])
class MargenAgricolaTest {

    @get:Rule val compose = createComposeRule()

    private val d = 1e-6

    /** Caso calculado a mano (ver comentarios): precio FOB con todos los rubros cargados. */
    @Test
    fun casoCompletoConPrecioFob() {
        val r = MargenAgricola.calcular(
            EntradaMargen(
                superficieHa = 100.0, rindeTnHa = 3.5,
                precioBase = PrecioBase.FOB, precioUsdTn = 400.0, dexPct = 24.0, fobbingUsdTn = 14.0,
                comisionPct = 2.0, secadoUsdTn = 1.0, zarandaUsdTn = 1.0, fumigacionUsdTn = 0.5, almacenajeUsdTn = 0.5,
                fleteCortoUsdTn = 10.0, fleteLargoUsdTn = 20.0,
                costosDirectosUsdHa = 300.0, cosechaPct = 7.0,
                arrendamientoUsdHa = 200.0, estructuraUsdHa = 20.0,
                iibbPct = 0.75, sellosPct = 1.2, sellosAcargoPct = 50.0, tasaVialArsHa = 15_000.0,
                tipoCambio = 1_500.0
            )
        )
        assertEquals(96.0, r.dexUsdTn, d)                 // 400 × 24 %
        assertEquals(290.0, r.precioFasUsdTn, d)          // 400 − 96 − 14
        assertEquals(5.8, r.comisionUsdTn, d)             // 2 % de 290
        assertEquals(251.2, r.precioChacraUsdTn, d)       // 290 − 5,8 − 3 − 30
        assertEquals(1400.0, r.ingresoBrutoUsdHa, d)      // 3,5 × 400
        assertEquals(371.05, r.costosProduccionUsdHa, d)  // 300 + 7 % × 3,5 × 290
        assertEquals(508.15, r.margenBrutoUsdHa, d)       // 3,5 × 251,2 − 371,05
        assertEquals(18.8125, r.impuestosLocalesUsdHa, d) // IIBB 7,6125 + sellos 1,2 + tasa vial 10
        assertEquals(269.3375, r.margenNetoUsdHa, d)      // 508,15 − 18,8125 − 200 − 20
        assertEquals(269.3375 * 1500, r.margenNetoArsHa, 1e-3)
        assertEquals(26_933.75, r.margenNetoTotalUsd, 1e-6)
        // Rinde de indiferencia: fijos 531,2 / aporte 228,725 (y 300 / 230,9 solo con costos directos)
        assertEquals(531.2 / 228.725, r.rindeIndiferenciaTotalTnHa!!, d)
        assertEquals(300.0 / 230.9, r.rindeIndiferenciaDirectosTnHa!!, d)
        // En el rinde de indiferencia el margen neto es cero
        val enQuiebre = MargenAgricola.calcular(
            EntradaMargen(100.0, r.rindeIndiferenciaTotalTnHa!!, PrecioBase.FOB, 400.0, 24.0, 14.0, 2.0, 1.0, 1.0, 0.5, 0.5,
                10.0, 20.0, 300.0, 7.0, 200.0, 20.0, 0.75, 1.2, 50.0, 15_000.0, 1_500.0)
        )
        assertEquals(0.0, enQuiebre.margenNetoUsdHa, 1e-6)
        // La composición reparte exactamente el ingreso bruto
        assertEquals(1400.0, r.composicion.sumOf { it.usdHa }, 1e-6)
        assertEquals(100.0, r.composicion.sumOf { it.pct }, 1e-6)
        assertEquals(3.5 * 96 + 18.8125, r.composicion.first { it.rubro == RubroCosto.IMPUESTOS }.usdHa, d)
    }

    @Test
    fun conPizarraElDexNoSeRestaDosVeces() {
        val r = MargenAgricola.calcular(
            EntradaMargen(10.0, 3.0, PrecioBase.PIZARRA, 300.0, 24.0, comisionPct = 0.0, tipoCambio = 1500.0)
        )
        assertEquals(0.0, r.dexUsdTn, d)
        assertEquals(300.0, r.precioChacraUsdTn, d)
        assertEquals(300.0 * 24 / 76, r.dexImplicitoUsdTn, d) // informativo
        assertEquals(900.0, r.margenNetoUsdHa, d)
    }

    @Test
    fun sinMargenPorToneladaNoHayRindeDeIndiferencia() {
        val r = MargenAgricola.calcular(
            EntradaMargen(10.0, 3.0, PrecioBase.PIZARRA, 20.0, 0.0, comisionPct = 0.0, fleteLargoUsdTn = 25.0,
                costosDirectosUsdHa = 100.0, tipoCambio = 1500.0)
        )
        assertNull(r.rindeIndiferenciaTotalTnHa)
        assertNull(r.rindeIndiferenciaDirectosTnHa)
    }

    @Test
    fun alicuotasVigentes() {
        assertEquals(24.0, DerechosExportacion.alicuota("Soja"), d)
        assertEquals(8.5, DerechosExportacion.alicuota("Maíz"), d)
        assertEquals(8.5, DerechosExportacion.alicuota("Sorgo"), d)
        assertEquals(5.5, DerechosExportacion.alicuota("Trigo"), d)
        assertEquals(4.5, DerechosExportacion.alicuota("Girasol"), d)
        assertEquals(0.75, ImpuestosProvinciales.de("Córdoba").sellosArrendamientoPct, d)
        assertEquals(0.0, ImpuestosProvinciales.de("Córdoba").iibbPct, d)
    }

    @Test
    fun laPantallaCalculaEnVivoYSoloRestaElDexConPrecioFob() {
        compose.setContent {
            GRANEXTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    CalculadoraMargen(cultivo = "Soja", precioInicialUsd = 300.0, tipoCambio = 1500.0, onCerrar = {})
                }
            }
        }
        compose.onNodeWithTag("input_rendimiento").performTextReplacement("1")
        // Pizarra: 300 − comisión 1,5 % (4,5) = 295,5; el 24 % de DEX ya está en el precio
        compose.onNodeWithText("295,5").performScrollTo().assertExists()

        compose.onNodeWithTag("base_fob").performScrollTo().performClick()
        // FOB: 300 − DEX 72 = 228 de FAS; − 1,5 % (3,42) = 224,58
        compose.onNodeWithText("228,0").performScrollTo().assertExists()
        compose.onNodeWithText("224,6").performScrollTo().assertExists()

        compose.onNodeWithTag("base_pizarra").performScrollTo().performClick()
        compose.onNodeWithTag("input_hectareas").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("input_rendimiento").performScrollTo().performTextReplacement("3,2")
        compose.onNodeWithTag("input_cosecha").performScrollTo().performTextReplacement("7")
        compose.onNodeWithTag("btn_detalle_composicion").performScrollTo().performClick()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/calculadora_margen.png")
    }

    @Test
    fun cerrarYReabrirConservaLoCargadoYLimpiarLoBorra() {
        // Hace de MercadoViewModel: guarda lo que avisa la hoja
        val guardado = mutableMapOf<String, String>()
        var abierta by mutableStateOf(true)
        compose.setContent {
            GRANEXTheme {
                if (abierta) {
                    CalculadoraMargen(
                        cultivo = "Sorgo", precioInicialUsd = 190.1, tipoCambio = 1495.0, onCerrar = { abierta = false },
                        valoresGuardados = guardado.toMap(),
                        onValorCambiado = { k, v -> guardado[k] = v },
                        onLimpiar = { guardado.clear() }
                    )
                }
            }
        }
        compose.onNodeWithTag("input_hectareas").performTextReplacement("100")
        compose.onNodeWithTag("input_rendimiento").performTextReplacement("6,5")
        compose.onNodeWithTag("base_fob").performClick()

        // Se cierra (atrás, tocar afuera o "Listo" dejan de componer la hoja) y se vuelve a abrir
        compose.runOnIdle { abierta = false }
        compose.waitForIdle()
        compose.runOnIdle { abierta = true }
        compose.onNodeWithTag("input_hectareas").assertTextContains("100")
        compose.onNodeWithTag("input_rendimiento").assertTextContains("6,5")
        compose.onNodeWithTag("base_fob").assertIsSelected()
        assertEquals("FOB", guardado["precioBaseTxt"])

        compose.onNodeWithTag("btn_limpiar_calculadora").performClick()
        assertEquals(emptyMap<String, String>(), guardado.toMap())
        compose.onNodeWithTag("input_rendimiento").assertTextContains("", substring = false)
    }
}
