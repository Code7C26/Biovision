package com.example

import com.example.data.repository.AlmacenAgronomia
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.ui.viewmodel.EstadoPrescripcion
import com.example.ui.viewmodel.FuenteZonas
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.MapaRinde
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.TablaCampo
import com.example.utils.vra.UnidadRinde
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Etapa 4 de la prescripción: mapa de rinde (validación y base de zonas), análisis de suelo y dosis
 * sugerida, guardados por lote. Robolectric: el almacén usa org.json.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrescripcionAgronomiaTest {

    @get:Rule val carpeta = TemporaryFolder()

    /** 40 × 40 píxeles de 10 m (16 ha). */
    private val grilla = GrillaGeografica(-61.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 40, 40)
    private val lote = LoteSatelital(
        id = "db_7", nombre = "Lote suelo",
        coordenadas = listOf(grilla.norte to grilla.oeste, grilla.norte to grilla.este, grilla.sur to grilla.este, grilla.sur to grilla.oeste),
        geojson = "{}", areaHectareas = 16.0, provincia = "Córdoba"
    )

    /** El oeste tiene más vigor todos los años. */
    private inner class FuenteFalsa : FuenteSentinel {
        override fun configurada() = true
        override suspend fun escenas(lote: LoteSatelital, dias: Int) = Result.success(emptyList<EscenaSentinel>())
        override suspend fun imagen(lote: LoteSatelital, fecha: String) = Result.failure<ImagenIndices>(IllegalStateException("sin imagen"))
        override suspend fun campana(lote: LoteSatelital, campana: Campana): Result<CampanaNdvi> {
            val ndvi = FloatArray(grilla.total) { i -> if (i % 40 < 20) 0.84f else 0.70f }
            return Result.success(CampanaNdvi(campana, grilla, ndvi, IntArray(grilla.total) { 15 }))
        }
    }

    /** Monitor de rinde en kg/ha, un punto por píxel: 10 000 al oeste, 8 000 al este. */
    private fun mapaDeRinde(): TablaCampo {
        val lat = DoubleArray(grilla.total) { grilla.centro(it).first }
        val lon = DoubleArray(grilla.total) { grilla.centro(it).second }
        val rinde = DoubleArray(grilla.total) { if (it % 40 < 20) 10_000.0 else 8_000.0 }
        return TablaCampo(lat, lon, mapOf("Yld_Mass_D" to rinde, "Velocidad" to DoubleArray(grilla.total) { 6.5 }))
    }

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun esperar(vm: PrescripcionViewModel, condicion: (EstadoPrescripcion) -> Boolean): EstadoPrescripcion {
        repeat(400) {
            vm.estado.value?.takeIf(condicion)?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("Estado no alcanzado: ${vm.estado.value}")
    }

    @Test
    fun rindeValidaYArmaZonasYElSueloSugiereLaDosis() {
        val almacen = AlmacenAgronomia(carpeta.newFolder("agronomia"))
        val vm = PrescripcionViewModel(FuenteFalsa(), hoy = { "2026-10-29" }, almacen = almacen)
        vm.abrir(lote)
        esperar(vm) { it.zonas != null }

        // Importar: se propone la columna de rinde y la unidad (kg/ha por la mediana)
        vm.importarRinde("rinde_2025.zip", mapaDeRinde())
        val imp = vm.estado.value!!.importacionRinde!!
        assertEquals("Yld_Mass_D", imp.columna)
        assertEquals(UnidadRinde.KG_HA, imp.unidad)
        vm.cambiarImportacionRinde { it.copy(campana = "2024/25") }
        vm.confirmarImportacionRinde()

        // Con varias campañas, el mapa se suma como una campaña más y valida las zonas
        val conRinde = esperar(vm) { e ->
            e.rindes.size == 1 && e.potencialListo?.rindes == listOf("Maíz 2024/25") && e.zonas != null && e.rindePorZona.isNotEmpty()
        }
        val porZona = conRinde.rindePorZona.values.single()
        assertEquals(listOf(8.0, 10.0), porZona.map { it.rindeTHa })
        assertTrue("el rinde sigue a las zonas", MapaRinde.zonasOrdenadas(porZona))

        // Base "Rinde": las zonas salen solo del monitor
        vm.elegirFuenteZonas(FuenteZonas.RINDE)
        val rinde = esperar(vm) { it.fuenteZonas == FuenteZonas.RINDE && it.zonas != null && it.rindePorZona.isNotEmpty() }
        assertTrue(rinde.prescripcion()!!.fuente, rinde.prescripcion()!!.fuente.startsWith("Rinde relativo: mapa de rinde Maíz 2024/25"))
        assertEquals(2, rinde.zonas!!.zonas.size)

        // Suelo a mano por zona y dosis sugerida de urea (maíz, 9 t/ha de objetivo del lote)
        vm.cambiarSueloManual(1, MuestraSuelo(pBrayPpm = 10.0, nNitratosKgHa = 50.0))
        vm.cambiarSueloManual(2, MuestraSuelo(nNitratosKgHa = 70.0))
        val conSuelo = esperar(vm) { it.sueloManual.size == 2 }
        val sug = conSuelo.sugerencias
        // Zona 1 (este) rinde 0,89 del típico: objetivo 8 t/ha → 8 × 17 − 50 = 86 kg N = 187 kg de urea
        assertTrue("objetivo ${sug[0].rindeObjetivoTHa}", abs(sug[0].rindeObjetivoTHa - 8.0) < 0.05)
        assertTrue("urea ${sug[0].productoKgHa}", abs(sug[0].productoKgHa!! - 187) <= 2)
        assertTrue("urea ${sug[1].productoKgHa}", abs(sug[1].productoKgHa!! - 217) <= 2)
        assertEquals(2, vm.aplicarSugerencia())
        val aplicada = vm.estado.value!!
        assertEquals(sug[0].productoKgHa, aplicada.dosis[1]!!.fertilizanteKgHa)
        assertEquals("Urea (46-0-0)", aplicada.producto)

        // El LEEME lleva el análisis, el rinde por zona y el criterio de la dosis
        val leeme = ExportacionPrescripcion.leeme(aplicada.prescripcion()!!, PerfilMonitor.GENERICO)
        assertTrue(leeme, leeme.contains("SUELO Y RINDE"))
        assertTrue(leeme, leeme.contains("Zona 1: 10.0 · 50 · - · -"))
        assertTrue(leeme, leeme.contains("Rinde Maíz 2024/25 por zona: Zona 1 8.00 t/ha · Zona 2 10.00 t/ha"))
        assertTrue(leeme, leeme.contains("Dosis sugerida (a verificar con el agrónomo): Maíz"))

        // Puntos de muestreo georreferenciados: se ubican en su zona
        val lat = doubleArrayOf(grilla.centro(5).first, grilla.centro(35).first)
        val lon = doubleArrayOf(grilla.centro(5).second, grilla.centro(35).second)
        vm.importarSuelo("laboratorio.csv", TablaCampo(lat, lon, mapOf("P Bray" to doubleArrayOf(18.0, 9.0), "pH" to doubleArrayOf(6.1, 5.8))))
        assertEquals("P Bray", vm.estado.value!!.importacionSuelo!!.columnas.pBray)
        vm.confirmarImportacionSuelo()
        val puntos = esperar(vm) { it.sueloPorZona.size == 2 }
        assertEquals(18.0, puntos.sueloPorZona.getValue(2).muestra.pBrayPpm!!, 1e-9)
        // A mano gana, valor por valor: la zona 1 conserva su Bray 10 y suma el pH del punto
        assertEquals(10.0, puntos.sueloDeZona(1)!!.pBrayPpm!!, 1e-9)
        assertEquals(5.8, puntos.sueloDeZona(1)!!.ph!!, 1e-9)

        // Todo queda guardado en el teléfono para el lote (la escritura va en segundo plano)
        repeat(200) { if (almacen.suelo(lote.id).puntos.size == 2) return@repeat; Thread.sleep(25) }
        val otra = PrescripcionViewModel(FuenteFalsa(), hoy = { "2026-10-29" }, almacen = almacen)
        otra.abrir(lote)
        val guardado = esperar(otra) { it.rindes.size == 1 && it.suelo.puntos.size == 2 }
        assertEquals(1600, guardado.rindes.single().puntos.size)
        assertEquals(2, guardado.suelo.manual.size)
    }
}
