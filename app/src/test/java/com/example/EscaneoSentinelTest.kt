package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.ImagenSentinelRemota
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.ui.screens.TarjetaEscaneoSentinel
import com.example.ui.screens.capaSentinelJson
import com.example.ui.theme.GRANEXTheme
import com.example.ui.viewmodel.Carga
import com.example.ui.viewmodel.EscaneoViewModel
import com.example.ui.viewmodel.VistaEscaneo
import com.example.utils.RelieveLote
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1600dp", sdk = [36])
class EscaneoSentinelTest {

    @get:Rule val compose = createComposeRule()

    /**
     * Con un indicador de carga animado en pantalla, waitUntil no procesa la cola del hilo principal
     * (donde vuelve el cálculo en segundo plano): acá se la procesa a mano mientras se espera.
     */
    private fun esperar(vm: EscaneoViewModel, condicion: (com.example.ui.viewmodel.EstadoEscaneo) -> Boolean) {
        repeat(250) {
            if (vm.estado.value?.let(condicion) == true) return
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        throw AssertionError("Estado no alcanzado: ${vm.estado.value}")
    }

    private val lote = LoteSatelital(
        id = "db_1", nombre = "fondo", coordenadas = listOf(-31.58 to -64.28, -31.58 to -64.279, -31.581 to -64.279),
        geojson = "{}", areaHectareas = 1.0, provincia = "Córdoba"
    )

    /** Imágenes inventadas para la prueba: barbecho el 5/10 y suelo más verde el 25/9. */
    private class FuenteFalsa(
        private val lista: List<EscenaSentinel> = listOf(
            EscenaSentinel("2026-10-05", 0.4, 0.15, 0.1, 0.2, 100),
            EscenaSentinel("2026-09-25", 0.0, 0.30, 0.2, 0.35, 100),
            EscenaSentinel("2026-09-20", 87.0, 0.2, 0.1, 0.2, 10)
        )
    ) : FuenteSentinel {
        val pedidos = mutableListOf<String>()
        val fotos = mutableListOf<String>()
        var relieves = 0
        override fun configurada() = true
        override suspend fun escenas(lote: LoteSatelital, dias: Int) = Result.success(lista)

        override suspend fun colorReal(lote: LoteSatelital, fecha: String): Result<ImagenSentinelRemota> {
            fotos += fecha
            return Result.success(ImagenSentinelRemota(fecha, -64.2805, -31.5815, -64.2785, -31.5795, 2, 2, byteArrayOf(1, 2, 3)))
        }

        /** Sube 20 cm cada 10 m hacia el este. */
        override suspend fun relieve(lote: LoteSatelital): Result<RelieveLote> {
            relieves++
            val grilla = GrillaGeografica(-64.2805, -31.5795, 10 / 94_870.0, 10 / 110_950.0, 25, 25)
            return Result.success(RelieveLote(grilla, FloatArray(grilla.total) { 100f + 0.2f * (it % 25) }))
        }

        override suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices> {
            pedidos += fecha
            val grilla = GrillaGeografica(-64.28, -31.58, 10 / 94_870.0, 10 / 110_950.0, 10, 10)
            val base = if (fecha == "2026-10-05") 0.12f else 0.30f
            val ndvi = FloatArray(100) { base + 0.017f * ((it / 10 + it % 10) % 10) }
            return Result.success(ImagenIndices(grilla, fecha, ndvi, FloatArray(100) { ndvi[it] * 0.6f }, ndvi.copyOf()))
        }
    }

    @Test
    fun abreLaFechaMasRecienteYComparaConOtra() {
        val fuente = FuenteFalsa()
        val vm = EscaneoViewModel(fuente)
        vm.abrir(lote)
        compose.setContent {
            GRANEXTheme {
                val estado by vm.estado.collectAsStateWithLifecycle()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TarjetaEscaneoSentinel(estado, vm, onIrAAjustes = {})
                }
            }
        }
        compose.waitUntil(5_000) { vm.estado.value?.resumen != null }

        // La fecha nublada (87 %) no se ofrece; se abre la más reciente despejada
        assertEquals(listOf("2026-10-05"), fuente.pedidos)
        compose.onNodeWithText("5 oct · 0 % nubes").assertExists()
        compose.onNodeWithText("20 sep · 87 % nubes").assertDoesNotExist()
        compose.onNodeWithTag("diagnostico_escaneo").performScrollTo()
        compose.onNodeWithText("Sin cultivo activo").assertExists()
        assertTrue(capaSentinelJson(vm.estado.value)!!.contains("\"rampa\":\"indice\""))

        // Comparar con el 25/9: mapa de cambio y hectáreas que subieron o bajaron
        compose.onNode(hasText("25 sep") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("se tocó la opción de comparar", "2026-09-25", vm.estado.value?.fechaComparar)
        compose.waitUntil(5_000) { vm.estado.value?.imagenComparar is com.example.ui.viewmodel.Carga.Lista }
        compose.waitUntil(5_000) { vm.estado.value?.comparacion != null }
        compose.onNodeWithTag("comparacion_escaneo").performScrollTo()
        val capa = capaSentinelJson(vm.estado.value)!!
        assertTrue(capa.contains("\"rampa\":\"cambio\""))
        assertTrue(vm.estado.value!!.comparacion!!.cambioMedio < 0)

        // Volver a una fecha ya bajada no gasta otra consulta
        vm.elegirFecha("2026-09-25")
        vm.elegirFecha("2026-10-05")
        compose.waitForIdle()
        assertEquals(listOf("2026-10-05", "2026-09-25"), fuente.pedidos)
    }

    @Test
    fun fotoDelDiaYRelieveSinGastarConsultasDeMas() {
        val fuente = FuenteFalsa()
        val vm = EscaneoViewModel(fuente)
        vm.abrir(lote)
        compose.setContent {
            GRANEXTheme {
                val estado by vm.estado.collectAsStateWithLifecycle()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TarjetaEscaneoSentinel(estado, vm, onIrAAjustes = {})
                }
            }
        }
        compose.waitUntil(5_000) { vm.estado.value?.resumen != null }

        // Foto del día: PNG directo con sus límites, sin valor por píxel
        compose.onNodeWithText("Foto del día").performClick()
        compose.waitUntil(5_000) { vm.estado.value?.foto is Carga.Lista }
        compose.onNodeWithTag("foto_escaneo").performScrollTo()
        val foto = capaSentinelJson(vm.estado.value)!!
        assertTrue(foto, foto.contains("\"imagen\":\"data:image/png;base64,AQID\""))
        assertTrue(foto, foto.contains("\"limites\":{\"oeste\":-64.2805"))

        // En la vista de foto, cambiar de fecha baja la foto pero no el índice
        vm.elegirFecha("2026-09-25")
        compose.waitUntil(5_000) { vm.estado.value?.foto is Carga.Lista && vm.estado.value?.fecha == "2026-09-25" }
        assertEquals(listOf("2026-10-05", "2026-09-25"), fuente.fotos)
        assertEquals(listOf("2026-10-05"), fuente.pedidos)

        // Relieve: altura respecto de la típica, en metros, con bajos y lomas
        compose.onNodeWithText("Relieve").performClick()
        esperar(vm) { it.resumenRelieve != null }
        compose.onNodeWithTag("diagnostico_relieve").performScrollTo()
        val r = vm.estado.value!!.resumenRelieve!!
        assertTrue("desnivel ${r.desnivel}", r.desnivel > 1.0)
        val relieve = capaSentinelJson(vm.estado.value)!!
        assertTrue(relieve.contains("\"rampa\":\"relieve\""))
        assertTrue(relieve.contains("\"unidad\":\"m\""))

        // Volver al índice y al relieve no vuelve a pedir nada
        vm.elegirVista(VistaEscaneo.INDICE)
        esperar(vm) { it.resumen != null }
        vm.elegirVista(VistaEscaneo.RELIEVE)
        compose.waitForIdle()
        assertEquals(1, fuente.relieves)
        assertEquals(listOf("2026-10-05", "2026-09-25"), fuente.pedidos)
    }

    @Test
    fun marcaLaFechaConNeblinaYLaDejaFueraDeLaLinea() {
        val escenas = listOf(
            EscenaSentinel("2026-10-05", 0.0, 0.80, 0.5, 0.7, 100),
            EscenaSentinel("2026-09-30", 0.0, 0.81, 0.5, 0.7, 100),
            EscenaSentinel("2026-09-25", 2.0, 0.52, 0.3, 0.5, 100),
            EscenaSentinel("2026-09-20", 0.0, 0.79, 0.5, 0.7, 100),
            EscenaSentinel("2026-09-15", 0.0, 0.78, 0.5, 0.7, 100)
        )
        val vm = EscaneoViewModel(FuenteFalsa(escenas))
        vm.abrir(lote)
        compose.setContent {
            GRANEXTheme {
                val estado by vm.estado.collectAsStateWithLifecycle()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TarjetaEscaneoSentinel(estado, vm, onIrAAjustes = {})
                }
            }
        }
        compose.waitUntil(5_000) { vm.estado.value?.resumen != null }
        assertEquals(setOf("2026-09-25"), vm.estado.value!!.fechasDudosas)
        assertEquals("se abre la más reciente", "2026-10-05", vm.estado.value!!.fecha)
        compose.onNodeWithTag("leyenda_neblina").performScrollTo()
        compose.onNode(hasText("25 sep · posible neblina") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.estado.value?.fecha == "2026-09-25" }
        compose.onNodeWithTag("aviso_neblina").assertExists()
    }
}
