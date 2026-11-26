package com.example

import com.example.data.repository.GeocodificadorLocalidades
import com.example.data.repository.SugerenciaLocalidad
import com.example.data.repository.TipoLocalidad
import com.example.ui.components.WindyEmbed
import com.example.ui.viewmodel.VistaMapaClima
import com.example.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Zoom según el tamaño de la localidad y vuelo del mapa Windy del buscador de Clima. */
// Robolectric: org.json es parte de Android y en la JVM pura no está implementado
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class BuscadorClimaTest {

    private val localeOriginal = Locale.getDefault()

    @Before
    fun preparar() {
        // Sin avanzar el dispatcher no corre ninguna carga de red del ViewModel
        Dispatchers.setMain(StandardTestDispatcher())
        // En es-AR "%.5f" usaría coma decimal: el script y la URL deben salir igual con punto
        Locale.setDefault(Locale.forLanguageTag("es-AR"))
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
        Locale.setDefault(localeOriginal)
    }

    @Test
    fun elTipoSaleDelCodigoGeoNamesYLaPoblacion() {
        assertEquals(TipoLocalidad.CIUDAD_GRANDE, TipoLocalidad.de("PPLC", 3_000_000)) // CABA
        assertEquals(TipoLocalidad.CIUDAD_GRANDE, TipoLocalidad.de("PPLA2", 948_312)) // Rosario
        assertEquals(TipoLocalidad.CIUDAD, TipoLocalidad.de("PPLA", 30_000)) // capital provincial chica
        assertEquals(TipoLocalidad.CIUDAD, TipoLocalidad.de("PPL", 72_340)) // Venado Tuerto
        assertEquals(TipoLocalidad.PUEBLO, TipoLocalidad.de("PPLA2", 27_000)) // Marcos Juárez
        assertEquals(TipoLocalidad.LOCALIDAD, TipoLocalidad.de("PPL", null)) // Chabás
    }

    @Test
    fun cuantoMasChicaLaLocalidadMasCercaElZoom() {
        val max = WeatherViewModel.ZOOM_MAX_WINDY_EMBED
        assertEquals(9, TipoLocalidad.CIUDAD_GRANDE.zoom(max))
        assertEquals(10, TipoLocalidad.CIUDAD.zoom(max))
        // El embed de Windy no pasa de 11: pueblos y parajes van al máximo
        assertEquals(11, TipoLocalidad.PUEBLO.zoom(max))
        assertEquals(11, TipoLocalidad.LOCALIDAD.zoom(max))
        assertEquals(13, TipoLocalidad.LOCALIDAD.zoom())
        assertTrue(TipoLocalidad.entries.zipWithNext().all { (a, b) -> a.zoom() < b.zoom() })
    }

    @Test
    fun elScriptVuelaConAnimacionYNumerosConPunto() {
        val vista = VistaMapaClima(-33.2462, -61.3587, 11, "Chabás")
        val script = WindyEmbed.scriptVolar(vista)
        assertTrue(script, script.contains("m.flyTo([-33.24620,-61.35870],11,{duration:1.6,easeLinearity:0.25})"))
        assertTrue(script.contains("'rqstOpen','picker',{lat:-33.24620,lon:-61.35870}"))
        assertTrue(script.contains("return 'sin_api'"))
        // El nombre (texto del usuario) nunca entra al script
        assertTrue(!script.contains("Chabás"))
        val url = WindyEmbed.urlEmbed(vista, "rain")
        assertTrue(url, url.contains("lat=-33.24620&lon=-61.35870&zoom=11&") && url.contains("overlay=rain"))
        assertTrue(WindyEmbed.urlCompleta(vista, "rain").endsWith("rain,-33.24620,-61.35870,12"))
    }

    @Test
    fun elegirUnaLocalidadMueveElMapaAlInstanteYLosRefrescosNoLoAlejan() {
        val vm = WeatherViewModel()
        val chabas = SugerenciaLocalidad("Chabás", "Santa Fe, Argentina", -33.2462, -61.3587, 70.0, true, TipoLocalidad.LOCALIDAD)

        vm.seleccionarCiudad(chabas)
        val vista = vm.vistaMapa.value
        assertEquals(-33.2462, vista.lat, 0.0)
        assertEquals(-61.3587, vista.lon, 0.0)
        assertEquals(11, vista.zoom)
        assertEquals("Chabás", vista.nombre)

        // Refresco periódico o manual de la misma ubicación: no vuelve a volar ni cambia el zoom
        vm.cargarClimaActual(chabas.lat, chabas.lon, chabas.nombre, forceRefresh = true)
        assertEquals(vista, vm.vistaMapa.value)

        // Elegirla de nuevo sí vuela (el usuario pudo haber movido el mapa)
        vm.seleccionarCiudad(chabas)
        assertNotEquals(vista.id, vm.vistaMapa.value.id)

        // Una carga sin zoom ni tipo conocido: vista regional
        vm.cargarClimaActual(-32.9468, -60.6393, "Rosario")
        assertEquals(WeatherViewModel.ZOOM_REGIONAL, vm.vistaMapa.value.zoom)

        // Ubicación actual (GPS): se acerca como a una ciudad, y vuela aunque ya esté ahí
        vm.usarUbicacionActual(-31.66, -64.43)
        val gps = vm.vistaMapa.value
        assertEquals(10, gps.zoom)
        assertEquals(-31.66, gps.lat, 0.0)
        vm.usarUbicacionActual(-31.66, -64.43)
        assertNotEquals(gps.id, vm.vistaMapa.value.id)
        assertEquals(10, vm.vistaMapa.value.zoom)
    }
}
