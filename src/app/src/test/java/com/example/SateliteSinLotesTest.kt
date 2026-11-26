package com.example

import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.AgroSatRepository
import com.example.ui.screens.SateliteContent
import com.example.ui.theme.GRANEXTheme
import com.example.ui.viewmodel.BusquedaUbicacionState
import com.example.ui.viewmodel.MapaBaseState
import com.example.ui.viewmodel.ProveedorMapa
import com.example.ui.viewmodel.SateliteUiState
import com.example.ui.viewmodel.SateliteViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.data.repository.SentinelHubCdse
import com.example.ui.viewmodel.EscaneoViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Primera apertura de Satélite: sin lotes precargados, estado vacío y alta del primer lote. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1000dp-xhdpi", sdk = [36])
class SateliteSinLotesTest {

    @get:Rule val compose = createComposeRule()

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Espera (procesando el hilo principal) a que el estado cumpla [condicion]. */
    private fun esperar(vm: SateliteViewModel, condicion: (SateliteUiState) -> Boolean): SateliteUiState {
        repeat(250) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.uiState.value.takeIf(condicion)?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("Estado no alcanzado: ${vm.uiState.value}")
    }

    @Test
    fun laBaseNuevaNoTraeLotesYElPrimeroQuedaSeleccionado() = runBlocking {
        // La base real, con su siembra inicial (precios, usuario, favoritas): no debe insertar lotes
        val repo = AgroSatRepository(AppDatabase.getDatabase(contexto).agroDao())
        assertEquals(emptyList<Any>(), repo.lotes.first())

        val vm = SateliteViewModel(repo, maptilerDisponible = false)
        val vacio = esperar(vm) { it is SateliteUiState.Success } as SateliteUiState.Success
        assertNull(vacio.loteSeleccionado)
        assertTrue(vacio.lotes.isEmpty())

        val lote = repo.guardarLote(null, "Lote 1", "Santa Fe", listOf(-33.0 to -61.0, -33.0 to -60.99, -33.01 to -60.99))
        val conLote = esperar(vm) { (it as? SateliteUiState.Success)?.loteSeleccionado != null } as SateliteUiState.Success
        assertEquals(lote.id, conLote.loteSeleccionado?.id)
        assertNotNull(repo.lotes.first().singleOrNull())
    }

    @Test
    fun sinLotesSeGuiaACrearloYNoSePuedeEditarNiPrescribir() {
        val repo = AgroSatRepository(AppDatabase.getDatabase(contexto).agroDao())
        val vm = SateliteViewModel(repo, maptilerDisponible = false)
        val estado = esperar(vm) { it is SateliteUiState.Success } as SateliteUiState.Success

        compose.setContent {
            GRANEXTheme {
                // Mismo fondo que la pantalla real (el Scaffold de Satélite)
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                SateliteContent(
                    state = estado,
                    viewModel = vm,
                    gisMap = null,
                    edicion = null,
                    areaEnVivo = null,
                    mapaBase = MapaBaseState(ProveedorMapa.ESRI, maptilerDisponible = false),
                    avisoMapa = null,
                    isFullscreen = false,
                    onFullscreenChange = {},
                    busqueda = BusquedaUbicacionState(),
                    destinoMapa = null,
                    onGuardarClick = {},
                    onCancelarEdicionClick = {},
                    onEliminarLoteClick = {},
                    onAbrirPrescripcion = {},
                    escaneo = null,
                    escaneoViewModel = EscaneoViewModel(SentinelHubCdse(contexto)),
                    onIrAAjustes = {}
                )
                }
            }
        }
        compose.onNodeWithTag("satelite_sin_lotes").assertExists()
        compose.onNodeWithText("Crear lote").assertExists()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithTag("btn_prescripcion").assertIsNotEnabled()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/satelite_sin_lotes.png")
    }
}
