package com.example

import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.ui.viewmodel.EstadoPrescripcion
import com.example.ui.viewmodel.FuenteZonas
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.ui.viewmodel.SentidoSiembra
import com.example.utils.AnalisisRelieve
import com.example.utils.RelieveLote
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.MarcoMaquina
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class PrescripcionCampanasTest {

    /** 40 × 40 píxeles de 10 m (16 ha) y el lote que los cubre. */
    private val grilla = GrillaGeografica(-61.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 40, 40)
    private val lote = LoteSatelital(
        id = "db_1", nombre = "Lote prueba",
        coordenadas = listOf(grilla.norte to grilla.oeste, grilla.norte to grilla.este, grilla.sur to grilla.este, grilla.sur to grilla.oeste),
        geojson = "{}", areaHectareas = 16.0, provincia = "Buenos Aires"
    )

    /** Imágenes inventadas para la prueba: el oeste rinde 20 % más todos los años; 2022/23 fue sequía. */
    private inner class FuenteFalsa : FuenteSentinel {
        val campanasPedidas: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val imagenesPedidas: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun configurada() = true
        override suspend fun escenas(lote: LoteSatelital, dias: Int) =
            Result.success(listOf(EscenaSentinel("2026-10-25", 0.0, 0.17, 0.1, 0.2, 400)))

        override suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices> {
            imagenesPedidas += fecha
            val v = FloatArray(grilla.total) { 0.16f } // barbecho
            return Result.success(ImagenIndices(grilla, fecha, v, v, v))
        }

        override suspend fun campana(lote: LoteSatelital, campana: Campana): Result<CampanaNdvi> {
            campanasPedidas += campana.etiqueta
            val sequia = campana.etiqueta == "2022/23"
            val ndvi = FloatArray(grilla.total) { i -> if (sequia) 0.30f else if (i % 40 < 20) 0.84f else 0.70f }
            return Result.success(CampanaNdvi(campana, grilla, ndvi, IntArray(grilla.total) { 15 }))
        }

        /** Bajo al oeste: sube 10 cm cada 10 m hacia el este (3,9 m de desnivel). */
        override suspend fun relieve(lote: LoteSatelital): Result<RelieveLote> =
            Result.success(RelieveLote(grilla, FloatArray(grilla.total) { 30f + 0.1f * (it % 40) }))
    }

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun esperar(vm: PrescripcionViewModel, condicion: (EstadoPrescripcion) -> Boolean): EstadoPrescripcion {
        repeat(300) {
            vm.estado.value?.takeIf(condicion)?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("Estado no alcanzado: ${vm.estado.value}")
    }

    @Test
    fun zonasDeVariasCampanasConLaCantidadRecomendada() {
        val fuente = FuenteFalsa()
        val vm = PrescripcionViewModel(fuente, hoy = { "2026-10-29" })
        vm.abrir(lote)
        val e = esperar(vm) { it.zonas != null }

        // Las últimas 4 campañas de gruesa, de a una; la de sequía no se usa
        assertEquals(listOf("2022/23", "2023/24", "2024/25", "2025/26"), fuente.campanasPedidas)
        val potencial = e.potencialListo!!
        assertEquals(3, potencial.usadas.size)
        assertTrue(potencial.campanas.first { it.campana.etiqueta == "2022/23" }.motivo!!.startsWith("Sin cultivo"))
        // Dos niveles de potencial: se recomiendan y se aplican 2 zonas
        assertEquals(2, e.recomendacion!!.zonas)
        assertEquals(2, e.config.zonas)
        assertEquals("Potencial productivo: NDVI pico de 3 campañas (2023/24 a 2025/26) (1,00 = lo típico del lote)", e.prescripcion()!!.fuente)
        assertEquals("2023-2026", e.fechaImagen)
        // Con varias campañas no se baja la imagen de una fecha (no hace falta y gasta cuota)
        assertTrue(fuente.imagenesPedidas.isEmpty())

        // Elegir a mano la cantidad de zonas: se respeta aunque la recomendada sea otra
        vm.cambiarConfig { it.copy(zonas = 4) }
        val manual = esperar(vm) { it.zonas != null && it.zonasElegidasPorUsuario }
        assertEquals(4, manual.config.zonas)
    }

    @Test
    fun unaFechaDeBarbechoAvisaQueNoSirveParaPrescribir() {
        val fuente = FuenteFalsa()
        val vm = PrescripcionViewModel(fuente, hoy = { "2026-10-29" })
        vm.abrir(lote)
        esperar(vm) { it.zonas != null }
        vm.elegirFuenteZonas(FuenteZonas.FECHA)
        val e = esperar(vm) { it.imagenSinCultivo }
        assertEquals(listOf("2026-10-25"), fuente.imagenesPedidas)
        assertTrue(e.imagenSinCultivo)

        // Volver a campañas no vuelve a pedir las que ya estaban
        vm.elegirFuenteZonas(FuenteZonas.CAMPANAS)
        esperar(vm) { it.fuenteZonas == FuenteZonas.CAMPANAS && it.zonas != null }
        assertEquals(4, fuente.campanasPedidas.size)
        assertFalse(vm.estado.value!!.zonasElegidasPorUsuario)
    }

    @Test
    fun grillaDeMaquinaConAnchoDeLaborYSentido() {
        val vm = PrescripcionViewModel(FuenteFalsa(), hoy = { "2026-10-29" })
        vm.abrir(lote)
        esperar(vm) { it.zonas != null }

        vm.cambiarAnchoLabor(9.0)
        val conMaquina = esperar(vm) { it.zonas?.anchoLaborM == 9.0 }
        assertTrue(conMaquina.zonas!!.marco is MarcoMaquina)
        assertEquals(SentidoSiembra.AUTOMATICO.rumbo(lote.coordenadas), conMaquina.zonas!!.rumboGrados!!, 1e-9)

        vm.cambiarSentido(SentidoSiembra.ESTE_OESTE)
        val esteOeste = esperar(vm) { it.zonas?.rumboGrados == 90.0 }
        assertEquals(90.0, esteOeste.config.rumboGrados!!, 0.0)
        // 8 km/h con 3 s de respuesta: 6,7 m de transición; celdas de 20 m alcanzan
        assertEquals(6.67, esteOeste.metrosTransicion, 0.01)

        vm.cambiarAnchoLabor(null)
        val sinMaquina = esperar(vm) { it.zonas != null && it.zonas!!.anchoLaborM == null }
        assertEquals(null, sinMaquina.config.rumboGrados)
    }

    @Test
    fun zonasPorRelieveDelBajoALaLoma() {
        val fuente = FuenteFalsa()
        val vm = PrescripcionViewModel(fuente, hoy = { "2026-10-29" })
        vm.abrir(lote)
        vm.elegirFuenteZonas(FuenteZonas.RELIEVE)
        val e = esperar(vm) { it.fuenteZonas == FuenteZonas.RELIEVE && it.zonas != null && it.recomendacion != null }

        assertEquals("Altura (m)", e.nombreValor)
        val p = e.prescripcion()!!
        assertTrue(p.fuente, p.fuente.startsWith("Relieve (Copernicus DEM"))
        // La licencia del GLO-30 pide el aviso en lo que se distribuye (LEEME y GeoJSON)
        assertTrue(p.fuente, p.fuente.contains(AnalisisRelieve.AVISO_DEM))
        assertEquals("relieve", p.fechaImagen)
        assertFalse("un lote con 3,9 m de desnivel no es parejo", e.recomendacion!!.homogeneo)
        // Zona 1 = la más baja; la altura va desde el punto más bajo del lote
        val zonas = e.zonas!!.zonas
        assertTrue("${zonas.map { it.indicePromedio }}", zonas.zipWithNext().all { (a, b) -> a.indicePromedio < b.indicePromedio })
        assertTrue(zonas.first().indicePromedio < 1.3 && zonas.last().indicePromedio > 2.5)
        // La base es el relieve: no hizo falta bajar ninguna imagen de una fecha
        assertTrue(fuente.imagenesPedidas.isEmpty())
    }
}
