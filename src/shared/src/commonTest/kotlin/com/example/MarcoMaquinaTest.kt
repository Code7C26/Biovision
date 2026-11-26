package com.example

import com.example.utils.GeometriaLote
import com.example.utils.grados
import com.example.utils.radianes
import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.MarcoMaquina
import com.example.utils.vra.Zonificador
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarcoMaquinaTest {

    private val mLat = 110_950.0
    private val mLon = 111_412.84 * cos(radianes(-33.0))

    /** Lote rectangular de [anchoM] (este-oeste) × [altoM] (norte-sur) con la esquina noroeste en (-33, -61). */
    private fun rectangulo(anchoM: Double, altoM: Double) = listOf(
        -33.0 to -61.0, -33.0 to -61.0 + anchoM / mLon, -33.0 - altoM / mLat to -61.0 + anchoM / mLon, -33.0 - altoM / mLat to -61.0
    )

    private fun cerca(esperado: Double, real: Double, tol: Double, mensaje: String) =
        assertTrue(abs(esperado - real) <= tol, "$mensaje: esperado $esperado, real $real")

    @Test
    fun cadaCentroCaeEnSuCelda() {
        val m = MarcoMaquina.para(rectangulo(600.0, 400.0), anchoM = 9.0, largoM = 20.0, rumboGrados = 30.0)
        for (i in 0 until m.total step 7) assertEquals(i, m.celdaDe(m.centro(i).first, m.centro(i).second))
        cerca(0.018, m.areaCeldaHa, 1e-9, "celda de 9 × 20 m")
    }

    @Test
    fun rumboDelLadoMasLargo() {
        cerca(90.0, GeometriaLote.rumboLadoMasLargo(rectangulo(1000.0, 300.0)), 0.5, "largo este-oeste")
        val r = GeometriaLote.rumboLadoMasLargo(rectangulo(300.0, 1000.0))
        assertTrue(r < 0.5 || r > 179.5, "largo norte-sur: $r")
    }

    /** NDVI que crece de oeste a este sobre una imagen de 10 m que cubre el lote. */
    private fun imagen(lote: List<Pair<Double, Double>>): ImagenIndices {
        val oeste = lote.minOf { it.second } - 0.0005
        val norte = lote.maxOf { it.first } + 0.0005
        val g = GrillaGeografica(oeste, norte, 10 / mLon, 10 / mLat, 90, 70)
        val v = FloatArray(g.total) { 0.3f + 0.5f * (it % 90) / 90f }
        return ImagenIndices(g, "2026-01-15", v, v, v)
    }

    @Test
    fun lasZonasSiguenLasPasadas() {
        val lote = rectangulo(700.0, 500.0)
        val config = ConfigZonificacion(zonas = 3, celdaM = 20.0, anchoLaborM = 9.0, rumboGrados = 30.0, areaMinimaHa = 0.2)
        val z = Zonificador.zonificar(imagen(lote), IndiceVegetacion.NDVI, lote, config)

        assertEquals(3, z.zonas.size)
        assertEquals(9.0, z.anchoLaborM)
        assertEquals(30.0, z.rumboGrados)
        // Todos los bordes de las zonas van a lo largo (30°) o a lo ancho (120°) de las pasadas
        z.poligonos.flatMap { it.anillos }.forEach { anillo ->
            anillo.zipWithNext().forEach { (a, b) ->
                val de = (b.second - a.second) * mLon
                val dn = (b.first - a.first) * mLat
                if (abs(de) + abs(dn) > 0.5) {
                    val rumbo = ((grados(atan2(de, dn)) % 180) + 180) % 180
                    assertTrue(abs(rumbo - 30) < 1 || abs(rumbo - 120) < 1, "borde a $rumbo°")
                }
            }
        }
        // Exterior horario, como la grilla geográfica (x = lon, y = lat: horario < 0)
        z.poligonos.forEach { p ->
            val e = p.anillos.first()
            assertTrue(e.zipWithNext().sumOf { (a, b) -> a.second * b.first - b.second * a.first } < 0, "exterior horario")
        }
        // Las zonas cubren el lote (35 ha) salvo el borde dentado de las celdas rotadas
        cerca(35.0, z.zonas.sumOf { it.areaHa }, 1.5, "superficie de las zonas")
        // ISO-XML: la grilla norte-sur fina lleva las mismas zonas
        val g = z.grilla
        val haIso = z.etiquetas.count { it != 0 } * (g.pasoLat * mLat) * (g.pasoLon * mLon) / 10_000
        cerca(35.0, haIso, 1.0, "superficie en la grilla ISO")
        assertEquals(setOf(0, 1, 2, 3), z.etiquetas.toSet())
    }

    @Test
    fun sinAnchoDeLaborQuedaLaGrillaDeSiempre() {
        val lote = rectangulo(700.0, 500.0)
        val z = Zonificador.zonificar(imagen(lote), IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 3, celdaM = 20.0))
        assertTrue(z.marco is GrillaGeografica)
        assertEquals(null, z.anchoLaborM)
        assertTrue(z.marco === z.grilla)
    }
}
