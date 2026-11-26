package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.repository.RosarioMarketRepository
import com.example.ui.components.Grano
import com.example.ui.components.IconoGrano
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.TemaGranex
import com.example.ui.viewmodel.MercadoViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cultivos de Mercado: cada uno con código de la Cámara Arbitral e ícono propio. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h400dp-xhdpi", sdk = [36])
class GranosMercadoTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun todosLosCultivosTienenPizarraEIcono() {
        assertTrue("Sorgo" in MercadoViewModel.CULTIVOS)
        assertEquals(6, RosarioMarketRepository.codigoProducto("Sorgo"))
        assertEquals(3, RosarioMarketRepository.codigoProducto("Maíz"))
        MercadoViewModel.CULTIVOS.forEach { cultivo ->
            assertNotNull("Sin código CAC: $cultivo", RosarioMarketRepository.codigoProducto(cultivo))
            assertNotNull("Sin ícono: $cultivo", Grano.de(cultivo))
        }
        assertEquals(Grano.MAIZ, Grano.de("maiz"))
        // Íconos y colores distintos para cada grano
        assertEquals(Grano.entries.size, Grano.entries.map { it.icono }.toSet().size)
    }

    @Composable
    private fun Lista() {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            MercadoViewModel.CULTIVOS.forEach { cultivo ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    IconoGrano(Grano.de(cultivo), tamano = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(cultivo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(24.dp))
                    IconoGrano(Grano.de(cultivo), tamano = 22.dp)
                }
            }
        }
    }

    private fun capturar(oscuro: Boolean) {
        AppThemeState.tema = TemaGranex.VERDE_CAMPO
        AppThemeState.isDark = oscuro
        compose.setContent { GRANEXTheme { Lista() } }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/granos_iconos_${if (oscuro) "oscuro" else "claro"}.png")
    }

    @Test fun iconosTemaClaro() = capturar(false)
    @Test fun iconosTemaOscuro() = capturar(true)
}
