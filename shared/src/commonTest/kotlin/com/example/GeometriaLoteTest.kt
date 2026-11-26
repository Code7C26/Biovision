package com.example

import com.example.utils.GeometriaLote
import com.example.utils.radianes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class GeometriaLoteTest {

    // En el ecuador: 1° de latitud = 110 574,3 m y 1° de longitud = 111 319,5 m (WGS84)
    @Test
    fun cuadradoEnElEcuador() {
        val cuadrado = listOf(0.0 to 0.0, 0.01 to 0.0, 0.01 to 0.01, 0.0 to 0.01)
        val esperado = (0.01 * 110_574.3) * (0.01 * 111_319.5) / 10_000.0
        assertEquals(esperado, GeometriaLote.areaHectareas(cuadrado), 0.05)
    }

    @Test
    fun anilloCerradoYSentidoNoCambianLaSuperficie() {
        val abierto = listOf(-33.8912 to -60.5732, -33.8850 to -60.5732, -33.8850 to -60.5650, -33.8912 to -60.5650)
        val area = GeometriaLote.areaHectareas(abierto)
        assertEquals(area, GeometriaLote.areaHectareas(abierto + abierto.first()), 1e-9)
        assertEquals(area, GeometriaLote.areaHectareas(abierto.reversed()), 1e-9)
    }

    // Comparación independiente: área esférica (Chamberlain–Duquette) con el radio auténtico de la Tierra
    @Test
    fun coincideConElAreaEsfericaEnPergamino() {
        val lote = listOf(-33.8912 to -60.5732, -33.8850 to -60.5732, -33.8850 to -60.5650, -33.8912 to -60.5650)
        val r = 6_371_007.2
        var suma = 0.0
        for (i in lote.indices) {
            val (lat1, lon1) = lote[i]
            val (lat2, lon2) = lote[(i + 1) % lote.size]
            suma += radianes(lon2 - lon1) * (2 + sin(lat1 * PI / 180) + sin(lat2 * PI / 180))
        }
        val esferica = abs(suma * r * r / 2) / 10_000.0
        val area = GeometriaLote.areaHectareas(lote)
        assertTrue(abs(area - esferica) / esferica < 0.005, "$area vs $esferica")
    }

    @Test
    fun menosDeTresVerticesNoTienenSuperficie() {
        assertEquals(0.0, GeometriaLote.areaHectareas(listOf(-33.0 to -60.0, -33.1 to -60.0)), 0.0)
    }

    @Test
    fun detectaLadosCruzados() {
        val mono = listOf(0.0 to 0.0, 0.01 to 0.01, 0.0 to 0.01, 0.01 to 0.0)
        val rectangulo = listOf(0.0 to 0.0, 0.01 to 0.0, 0.01 to 0.01, 0.0 to 0.01)
        assertTrue(GeometriaLote.tieneAutointerseccion(mono))
        assertFalse(GeometriaLote.tieneAutointerseccion(rectangulo))
        assertFalse(GeometriaLote.tieneAutointerseccion(rectangulo + rectangulo.first()))
    }

    @Test
    fun perimetroDeUnCuadradoEnElEcuador() {
        val cuadrado = listOf(0.0 to 0.0, 0.01 to 0.0, 0.01 to 0.01, 0.0 to 0.01)
        assertEquals(2 * (1105.743 + 1113.195), GeometriaLote.perimetroMetros(cuadrado), 0.5)
    }

    @Test
    fun puntoDentroDelLote() {
        // Lote en forma de L: el hueco de la L queda afuera
        val ele = listOf(-33.0 to -61.0, -33.0 to -60.98, -33.01 to -60.98, -33.01 to -60.99, -33.02 to -60.99, -33.02 to -61.0)
        assertTrue(GeometriaLote.dentroDePoligono(-33.005, -60.995, ele))
        assertTrue(GeometriaLote.dentroDePoligono(-33.015, -60.995, ele))
        assertFalse(GeometriaLote.dentroDePoligono(-33.015, -60.985, ele), "hueco de la L")
        assertFalse(GeometriaLote.dentroDePoligono(-32.99, -60.995, ele), "al norte del lote")
    }
}
