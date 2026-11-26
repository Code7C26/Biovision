package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.data.repository.AgroSatRepository
import com.example.ui.screens.DialogoGuardarLote
import com.example.ui.theme.GRANEXTheme
import com.example.ui.viewmodel.DeteccionProvincia
import com.example.utils.GeometriaLote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoteProvinciaTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun centroideDelPoligono() {
        // Triángulo: el centroide es el promedio de los vértices
        val triangulo = listOf(-33.0 to -61.0, -33.0 to -60.97, -33.03 to -61.0)
        val c = GeometriaLote.centroide(triangulo)!!
        assertEquals(-33.01, c.first, 1e-5)
        assertEquals(-60.99, c.second, 1e-5)
        // Lote en "L": el centroide del área no es el promedio de los vértices (que cae fuera del lote)
        val ele = listOf(-33.0 to -61.0, -33.0 to -60.98, -33.005 to -60.98, -33.005 to -60.995, -33.02 to -60.995, -33.02 to -61.0)
        val ce = GeometriaLote.centroide(ele)!!
        val promedioLon = ele.sumOf { it.second } / ele.size
        assertTrue(ce.first > -33.02 && ce.first < -33.0)
        assertTrue(kotlin.math.abs(ce.second - promedioLon) > 1e-4)
        // Anillo cerrado de GeoJSON (último = primero) da lo mismo
        assertEquals(c, GeometriaLote.centroide(triangulo + triangulo.first()))
        assertNull(GeometriaLote.centroide(emptyList()))
    }

    @Test
    fun leeLaProvinciaDeNominatim() {
        val json = """{"place_id":1,"addresstype":"state","name":"Santa Fe","address":{"state":"Santa Fe","ISO3166-2-lvl4":"AR-S","country":"Argentina","country_code":"ar"}}"""
        assertEquals("Santa Fe", AgroSatRepository.provinciaDeRespuesta(json))
        assertEquals("Ciudad Autónoma de Buenos Aires",
            AgroSatRepository.provinciaDeRespuesta("""{"address":{"state":"Ciudad Autónoma de Buenos Aires"}}"""))
        // Punto en el mar o sin provincia
        assertNull(AgroSatRepository.provinciaDeRespuesta("""{"error":"Unable to geocode"}"""))
        assertNull(AgroSatRepository.provinciaDeRespuesta("""{"address":{"country":"Argentina"}}"""))
    }

    @Test
    fun elDialogoMuestraLaBusquedaYNuncaBloqueaElGuardado() {
        var deteccion by mutableStateOf<DeteccionProvincia>(DeteccionProvincia.Buscando)
        var provincia by mutableStateOf("")
        var guardados = 0
        compose.setContent {
            GRANEXTheme {
                DialogoGuardarLote(
                    editando = false, nombre = "Lote 1", onNombre = {},
                    provincia = provincia, onProvincia = { provincia = it },
                    deteccion = deteccion, superficieHa = 42.0, vertices = 5,
                    onGuardar = { guardados++ }, onCancelar = {}
                )
            }
        }
        // Buscando: indicador en el campo, y se puede guardar igual
        compose.onNodeWithTag("cargando_provincia").assertExists()
        compose.onNodeWithTag("btn_guardar_lote").assertIsEnabled()

        // Detectada: la pantalla completa el campo (lo hace SateliteScreen) y se informa el origen
        compose.runOnIdle {
            deteccion = DeteccionProvincia.Detectada("Santa Fe")
            provincia = "Santa Fe"
        }
        compose.onNodeWithTag("cargando_provincia").assertDoesNotExist()
        compose.onNodeWithTag("input_provincia_lote").assertTextContains("Santa Fe")
        compose.onNodeWithTag("ayuda_provincia", useUnmergedTree = true).assertTextContains("Detectada por la ubicación", substring = true)

        // Editable a mano
        compose.onNodeWithTag("input_provincia_lote").performTextReplacement("Córdoba")
        assertEquals("Córdoba", provincia)
        compose.onNodeWithTag("ayuda_provincia", useUnmergedTree = true).assertTextContains("Se usa lo que escribiste", substring = true)

        // Sin conexión: campo vacío y editable, mensaje, y el guardado sigue habilitado
        compose.runOnIdle {
            deteccion = DeteccionProvincia.NoDisponible("Sin conexión para detectar la provincia")
            provincia = ""
        }
        compose.onNodeWithTag("ayuda_provincia", useUnmergedTree = true).assertTextContains("Escribila a mano", substring = true)
        compose.onNodeWithTag("btn_guardar_lote").assertIsEnabled().performClick()
        assertEquals(1, guardados)
    }
}
