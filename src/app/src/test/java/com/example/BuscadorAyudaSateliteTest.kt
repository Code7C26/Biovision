package com.example

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.repository.GeocodificadorLocalidades
import com.example.data.repository.SugerenciaLocalidad
import com.example.ui.screens.BarraBusqueda
import com.example.ui.screens.DialogoAyudaSatelite
import com.example.ui.theme.GRANEXTheme
import com.example.ui.viewmodel.BusquedaUbicacionState
import com.example.ui.viewmodel.SugerenciasState
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xhdpi", sdk = [36])
class BuscadorAyudaSateliteTest {

    @get:Rule val compose = createComposeRule()

    private val pergamino = SugerenciaLocalidad("Pergamino", "Partido de Pergamino, Buenos Aires, Argentina", -33.89, -60.57, 3.2, true)
    private val pergaminoAlto = SugerenciaLocalidad("Pergamino Alto", "Córdoba, Argentina", -31.4, -64.2, 410.0, true)

    @Test
    fun escribirPideSugerenciasYElegirUnaDevuelveEsaLocalidad() {
        val escritos = mutableListOf<String>()
        var elegida: SugerenciaLocalidad? = null
        compose.setContent {
            GRANEXTheme {
                var texto by remember { mutableStateOf("") }
                BarraBusqueda(
                    query = texto,
                    onQueryChange = { texto = it; escritos += it },
                    busqueda = BusquedaUbicacionState(),
                    sugerencias = if (texto.length >= 3) SugerenciasState(items = listOf(pergamino, pergaminoAlto)) else SugerenciasState(),
                    onBuscar = {},
                    onSugerencia = { elegida = it },
                    onCerrarSugerencias = {}
                )
            }
        }
        compose.onNodeWithTag("buscador_localidad").performTextInput("Perg")
        assertEquals("Perg", escritos.last())
        compose.onNodeWithTag("sugerencias_localidad").assertExists()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/satelite_sugerencias.png")

        compose.onNodeWithText("Pergamino Alto").performClick()
        assertEquals(pergaminoAlto, elegida)
    }

    @Test
    fun laXCierraLaAyuda() {
        abrirAyuda()
        compose.onNodeWithTag("dialogo_ayuda").captureRoboImage(filePath = "src/test/screenshots/satelite_ayuda.png")
        compose.onNodeWithTag("cerrar_ayuda").performClick()
        compose.onNodeWithTag("dialogo_ayuda").assertDoesNotExist()
    }

    @Test
    fun atrasCierraLaAyuda() {
        abrirAyuda()
        (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithTag("dialogo_ayuda").assertDoesNotExist()
    }

    @Test
    fun tocarFueraCierraLaAyuda() {
        abrirAyuda()
        val dialogo = ShadowDialog.getLatestDialog()
        // La ventana del diálogo mide lo mismo que la tarjeta: un toque fuera llega con coordenadas
        // fuera de sus límites, que es lo que Window.shouldCloseOnTouch reconoce como "afuera"
        val t = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { accion ->
            val ev = MotionEvent.obtain(t, t, accion, -200f, -200f, 0)
            compose.runOnUiThread { dialogo.dispatchTouchEvent(ev) }
            ev.recycle()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("dialogo_ayuda").assertDoesNotExist()
    }

    private fun abrirAyuda() {
        compose.setContent {
            GRANEXTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) DialogoAyudaSatelite(onDismiss = { visible = false })
            }
        }
        compose.onNodeWithTag("dialogo_ayuda").assertExists()
    }

    @Test
    fun ordenaPorNombreYCercaniaSinDuplicadosNiLugaresNoPoblados() {
        // Respuesta real de Open-Meteo para "rosario" (recortada), buscando desde Córdoba
        val json = """
            {"results":[
             {"name":"Rosario","latitude":-32.94682,"longitude":-60.63932,"feature_code":"PPLA2","country_code":"AR","admin1":"Provincia de Santa Fe","admin2":"Departamento de Rosario","population":948312},
             {"name":"Rosario","latitude":-32.94682,"longitude":-60.63932,"feature_code":"PPLA2","country_code":"AR","admin1":"Provincia de Santa Fe","admin2":"Departamento de Rosario","population":948312},
             {"name":"Cañada Martel","latitude":-30.9,"longitude":-63.9,"feature_code":"PPL","country_code":"AR","admin1":"Provincia de Córdoba","admin2":"Departamento de Totoral"},
             {"name":"Rosario","latitude":-31.1,"longitude":-63.9,"feature_code":"PPL","country_code":"AR","admin1":"Provincia de Córdoba","admin2":"Departamento de Colón"},
             {"name":"Arroyo Rosario","latitude":-31.4,"longitude":-64.1,"feature_code":"STM","country_code":"AR","admin1":"Provincia de Córdoba"},
             {"name":"Rota","latitude":null,"longitude":-60.0,"feature_code":"PPL"}
            ]}
        """.trimIndent()
        val s = GeocodificadorLocalidades.parsear(json, "rosario", -31.42 to -64.19)
        // El arroyo (STM) y la fila sin latitud se descartan; el nombre alternativo va al final
        assertEquals(listOf("Rosario", "Rosario", "Cañada Martel"), s.map { it.nombre })
        assertEquals("Departamento de Colón, Córdoba", s[0].detalle)
        assertEquals("Departamento de Rosario, Santa Fe", s[1].detalle)
        assertTrue(s[0].distanciaKm!! < s[1].distanciaKm!!)
        // Rosario–Buenos Aires ≈ 280 km
        assertEquals(280.0, GeocodificadorLocalidades.distanciaKm(-32.95 to -60.65, -34.60 to -58.38), 15.0)
    }

    @Test
    fun unaCiudadGrandeLejanaLeGanaAUnParajeApenasMasCercano() {
        val json = """
            {"results":[
             {"name":"San José","latitude":-33.3,"longitude":-60.9,"feature_code":"PPL","country_code":"AR","admin1":"Provincia de Santa Fe"},
             {"name":"San José","latitude":-34.0,"longitude":-60.6,"feature_code":"PPLA2","country_code":"AR","admin1":"Buenos Aires","population":120000}
            ]}
        """.trimIndent()
        // Desde Rosario: el paraje está a ~50 km y la ciudad a ~115 km, pero pesa su población
        val s = GeocodificadorLocalidades.parsear(json, "san jose", -32.95 to -60.64)
        assertEquals(120000, s[0].poblacion)
    }
}
