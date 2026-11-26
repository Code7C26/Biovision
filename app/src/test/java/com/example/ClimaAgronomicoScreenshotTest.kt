package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.screens.TarjetaBalanceHidrico
import com.example.ui.screens.TarjetaHeladaPronostico
import com.example.ui.screens.TarjetaVentanaPulverizacion
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.example.utils.DiaBalance
import com.example.utils.HoraPronosticoAgro
import com.example.utils.PronosticoAgro
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos

/** Tarjetas de pulverización, helada y balance hídrico de Clima con un pronóstico de invierno. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2000dp-xhdpi", sdk = [36])
class ClimaAgronomicoScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val desfase = -3 * 3600
    /** Medianoche local del viernes 4/10/2024; "ahora" son las 10:20. */
    private val medianoche = 20_000L * 86_400L + 3 * 3600
    private val ahora = medianoche + 10 * 3600 + 1200

    /** Ciclo diario: mínima a las 6, máxima a las 15; la segunda noche hiela. Llueve el sábado a la tarde. */
    private fun horas(): List<HoraPronosticoAgro> = (0 until 96).map { i ->
        val h = i % 24
        val dia = i / 24
        val minima = if (dia == 1) -1.5 else 6.0
        val ciclo = (1 - cos((h - 6).mod(24) / 24.0 * 2 * PI)) / 2
        val t = minima + 14 * ciclo
        HoraPronosticoAgro(
            epochS = medianoche + i * 3600L,
            temperaturaC = t,
            humedadPct = 95 - 55 * ciclo,
            vientoKmh = if (h in 12..18) 19.0 else 4.0 + 8 * ciclo,
            rafagaKmh = if (h in 12..18) 30.0 else 8.0 + 10 * ciclo,
            lluviaHoraPreviaMm = if (dia == 1 && h in 17..19) 2.5 else 0.0,
            esDeDia = h in 7..19
        )
    }

    private fun dias(): List<DiaBalance> = (-30..6).map { d ->
        DiaBalance(
            (20_000L + d) * 86_400L + 3 * 3600,
            when (d) { -22 -> 18.0; -9 -> 6.5; -3 -> 12.0; 1 -> 7.5; else -> 0.0 },
            3.2 + (d + 30) * 0.03
        )
    }

    private fun capturar(nombre: String, ancho: Dp, oscuro: Boolean = false) {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = oscuro
        val horas = horas()
        val pulverizacion = PronosticoAgro.pulverizacion(horas, ahora, desfase)
        val helada = PronosticoAgro.helada(horas, ahora, desfase)!!
        val balance = PronosticoAgro.balance(dias(), ahora, desfase)!!
        compose.setContent {
            GRANEXTheme {
                Column(
                    Modifier.width(ancho).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TarjetaVentanaPulverizacion(pulverizacion)
                    TarjetaHeladaPronostico(helada)
                    TarjetaBalanceHidrico(balance)
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/clima_agro_$nombre.png")
    }

    @Test fun en412dp() = capturar("412", 412.dp)
    @Test fun en360dpOscuro() = capturar("360_oscuro", 360.dp, oscuro = true)

    @Test
    fun tocarUnaHoraMuestraSuDetalle() {
        capturar("toque", 412.dp)
        compose.onNodeWithTag("detalle_hora_pulverizacion").assert(hasAnyDescendant(hasText("Hoy 10:00", substring = true)))
        compose.onNodeWithContentDescription("Mañana 18:00", substring = true).performClick()
        compose.onNodeWithTag("detalle_hora_pulverizacion").assert(hasAnyDescendant(hasText("Mañana 18:00 · No conviene", substring = true)))
        compose.onNodeWithText("Lluvia pronosticada", substring = true).assertIsDisplayed()
    }
}
