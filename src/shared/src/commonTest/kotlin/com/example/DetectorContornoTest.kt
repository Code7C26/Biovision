package com.example

import com.example.utils.vra.DetectorContorno
import com.example.utils.vra.ErrorDeteccion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectorContornoTest {

    /** 120 × 120 píxeles de 10 m (1,2 km de lado). */
    private val grilla = GrillaGeografica(-63.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 120, 120)

    /** Ruido determinístico de ±0,03, como la variabilidad dentro de un cultivo. */
    private fun ruido(i: Int, semilla: Int = 0): Float {
        var v = (i * 1_103_515_245 + 12_345 + semilla * 7_919) and 0x7fffffff
        v = (v xor (v ushr 13)) * 1_274_126_177 and 0x7fffffff
        return ((v % 1000) / 1000f - 0.5f) * 0.06f
    }

    private fun imagen(valor: (col: Int, fila: Int) -> Float, semilla: Int = 0): ImagenIndices {
        val v = FloatArray(grilla.total) { i -> valor(i % 120, i / 120).let { if (it.isNaN()) it else it + ruido(i, semilla) } }
        return ImagenIndices(grilla, "x", v, v.copyOf(), v.copyOf())
    }

    private fun centro(col: Int, fila: Int) = grilla.centro(fila * 120 + col)

    /** Lote A (columnas 10–59, filas 10–89) con soja, camino en la columna 60, lote B con maíz más ralo. */
    private fun dosLotes(col: Int, fila: Int): Float = when {
        col == 60 && fila in 5..100 -> 0.20f
        col in 10..59 && fila in 10..89 -> 0.80f
        col in 61..109 && fila in 10..89 -> 0.55f
        else -> 0.30f
    }

    @Test
    fun detectaElLoteTocadoConPocosVertices() {
        val (lat, lon) = centro(35, 50)
        val c = DetectorContorno.detectar(listOf(imagen(::dosLotes)), lat, lon)
        // 50 × 80 píxeles de 1 ha / 100 = 40 ha
        assertTrue(abs(c.areaHa - 40.0) < 4.0, "superficie ${c.areaHa}")
        assertTrue(c.vertices.size in 4..10, "vértices: ${c.vertices.size}")
        assertFalse(c.cortadoPorBorde, "no toca el borde del recuadro")
    }

    @Test
    fun conDosFechasSeparaLotesIgualesHoy() {
        // Hoy los dos lotes tienen el mismo verde y no hay camino; hace un mes uno estaba recién sembrado
        val hoy = imagen({ col, fila -> if (col in 10..109 && fila in 10..89) 0.8f else 0.3f })
        val antes = imagen({ col, fila ->
            when {
                col in 10..59 && fila in 10..89 -> 0.7f
                col in 60..109 && fila in 10..89 -> 0.25f
                else -> 0.3f
            }
        }, semilla = 1)
        val (lat, lon) = centro(30, 50)
        val solaHoy = DetectorContorno.detectar(listOf(hoy), lat, lon)
        assertTrue(solaHoy.areaHa > 70, "con una sola fecha se juntan: ${solaHoy.areaHa}")
        val conDos = DetectorContorno.detectar(listOf(hoy, antes), lat, lon)
        assertTrue(abs(conDos.areaHa - 40.0) < 5.0, "con dos fechas, solo el lote tocado: ${conDos.areaHa}")
    }

    @Test
    fun rellenaLosManchonesDelLote() {
        // Un monte de 5 × 5 píxeles en el medio del lote A
        val conMonte = imagen({ col, fila -> if (col in 30..34 && fila in 40..44) 0.35f else dosLotes(col, fila) })
        val (lat, lon) = centro(20, 20)
        val c = DetectorContorno.detectar(listOf(conMonte), lat, lon)
        assertTrue(abs(c.areaHa - 40.0) < 4.0, "el monte queda adentro: ${c.areaHa}")
    }

    @Test
    fun avisaSiElToqueCaeEnUnaNubeOEnUnaZonaPareja() {
        val nube = imagen({ col, fila -> if (col in 30..40 && fila in 40..50) Float.NaN else dosLotes(col, fila) })
        val (lat, lon) = centro(35, 45)
        val e = assertFailsWith<ErrorDeteccion> { DetectorContorno.detectar(listOf(nube), lat, lon) }
        assertTrue(e.message!!.contains("sin dato"), e.message)

        val pareja = imagen({ _, _ -> 0.6f })
        assertFailsWith<ErrorDeteccion> { DetectorContorno.detectar(listOf(pareja), lat, lon) }
    }

    @Test
    fun marcaCuandoElLoteSigueFueraDelRecuadro() {
        val grande = imagen({ col, fila -> if (col >= 40 && fila in 10..89) 0.8f else 0.3f })
        val (lat, lon) = centro(80, 50)
        val c = DetectorContorno.detectar(listOf(grande), lat, lon)
        assertTrue(c.cortadoPorBorde, "llega al borde este")
    }

    /** Lote con lados en diagonal (como los de la foto real): pocos vértices, sin amontonarse. */
    @Test
    fun ladosEnDiagonalDanPocosVertices() {
        val paralelogramo = imagen({ col, fila ->
            // Borde irregular como el real: píxeles mezclados que corren el límite ±1–2 píxeles
            val diente = ((fila * 7 + col * 3) % 5 - 2) * 0.6f
            val corrido = col - fila * 0.35f + diente
            if (fila in 20..80 && corrido in 15f..75f) 0.8f else 0.35f
        })
        val (lat, lon) = centro(60, 50)
        val c = DetectorContorno.detectar(listOf(paralelogramo), lat, lon)
        assertTrue(c.vertices.size <= 8, "vértices: ${c.vertices.size}")
        // Ningún par de vértices a menos de 25 m (2,5 píxeles)
        val cercanos = c.vertices.indices.count { i ->
            val (a, b) = c.vertices[i]
            val (x, y) = c.vertices[(i + 1) % c.vertices.size]
            kotlin.math.hypot((a - x) * 110_950, (b - y) * 93_370) < 25
        }
        assertEquals(0, cercanos, "vértices amontonados: ${c.vertices}")
    }

    /** Lote alambrado (columnas 10–59, filas 10–89) rodeado de pastizal; [adentro] da el valor de cada píxel del lote. */
    private fun alambrado(adentro: (col: Int, fila: Int) -> Float) = { col: Int, fila: Int ->
        if (col in 10..59 && fila in 10..89) adentro(col, fila) else 0.30f
    }

    @Test
    fun elBajoDelLoteEntraHastaElAlambrado() {
        // Franja de bajo al sur con borde ondulado (sigue el terreno, no es recta): el cultivo no la junta
        val conBajo = imagen(alambrado { col, fila ->
            if (fila > 76 + 3 * kotlin.math.sin(col / 4.0)) 0.50f else 0.80f
        })
        val (lat, lon) = centro(35, 40)
        val c = DetectorContorno.detectar(listOf(conBajo), lat, lon)
        assertTrue(abs(c.areaHa - 40.0) < 3.0, "el bajo queda adentro: ${c.areaHa}")
        assertTrue(c.vertices.size in 4..6, "cierra con los alambrados: ${c.vertices.size}")
        assertFalse(c.cortadoPorBorde)
    }

    @Test
    fun unaFranjaDeOtraLabranzaEntraHastaElAlambrado() {
        // La mitad oeste del lote (borde ondulado, no es un alambrado) quedó con otra labranza: la región
        // solo junta la mitad tocada, pero los cuatro alambrados son nítidos (caso real de 97 ha)
        val conFranja = imagen(alambrado { col, fila ->
            if (col < 35 + 3 * kotlin.math.sin(fila / 4.0)) 0.62f else 0.80f
        })
        val (lat, lon) = centro(48, 50)
        val c = DetectorContorno.detectar(listOf(conFranja), lat, lon)
        assertTrue(abs(c.areaHa - 40.0) < 3.0, "el lote entero: ${c.areaHa}")
        assertTrue(c.vertices.size in 4..6, "cierra con los alambrados: ${c.vertices.size}")
    }

    @Test
    fun unPuebloEnElRecuadroNoTapaLosAlambradosDelLote() {
        // Alambrados suaves (diferencia ~0,14) y, en el tercio este del recuadro, un pueblo con bordes de
        // 0,8 que subían el umbral de todo el recuadro: el lote se quedaba sin líneas y sin el bajo
        val escena = imagen({ col, fila ->
            when {
                col >= 85 -> if ((col / 2 + fila / 2) % 2 == 0) 0.10f else 0.90f
                col in 10..59 && fila in 10..89 -> if (fila > 76 + 3 * kotlin.math.sin(col / 4.0)) 0.68f else 0.56f
                else -> 0.42f
            }
        })
        val (lat, lon) = centro(35, 40)
        val c = DetectorContorno.detectar(listOf(escena), lat, lon)
        assertTrue(abs(c.areaHa - 40.0) < 3.0, "el bajo queda adentro: ${c.areaHa}")
        assertTrue(c.vertices.size in 4..6, "cierra con los alambrados: ${c.vertices.size}")
    }

    @Test
    fun unAlambradoInternoNoJuntaDosLotes() {
        // Marco alambrado (columnas 20–49) partido por un camino en la columna 33. Los dos lotes tienen
        // el mismo verde, y el tocado tiene una franja de otra labranza junto al camino
        val dosEnUnMarco = imagen({ col, fila ->
            when {
                fila !in 10..89 || col !in 20..49 -> 0.30f
                col == 33 -> 0.30f
                col < 33 -> 0.80f
                col < 36 + 1.5 * kotlin.math.sin(fila / 3.0) -> 0.62f
                else -> 0.80f
            }
        })
        val (lat, lon) = centro(44, 50)
        val c = DetectorContorno.detectar(listOf(dosEnUnMarco), lat, lon)
        // El marco entero serían 24 ha; el lote tocado, 12,8 ha
        assertTrue(c.areaHa < 15.0, "no cruza el camino: ${c.areaHa}")
    }

    @Test
    fun noSeEscapaAlCascoPorLaTranquera() {
        // Casco con monte al norte, del mismo verde, unido al lote por una tranquera de 80 m
        val conCasco = imagen({ col, fila ->
            when {
                col in 10..59 && fila in 30..89 -> 0.80f
                fila == 29 && col in 30..37 -> 0.80f
                col in 25..42 && fila in 12..28 -> 0.78f
                else -> 0.30f
            }
        })
        val (lat, lon) = centro(35, 60)
        val sinAlambrados = DetectorContorno.detectar(listOf(conCasco), lat, lon)
        // 50 × 60 píxeles = 30 ha
        assertTrue(abs(sinAlambrados.areaHa - 30.0) < 2.5, "el casco queda afuera: ${sinAlambrados.areaHa}")
        val norte = sinAlambrados.vertices.maxOf { it.first }
        val (latAlambrado, _) = centro(35, 30)
        assertTrue((norte - latAlambrado) * 110_950 < 20, "el borde norte es el alambrado")
    }

    @Test
    fun unaCanaladaAdentroNoPartElLote() {
        // Canal recto de 360 m que entra desde el oeste hasta la mitad del lote
        val conCanal = imagen(alambrado { col, fila -> if (fila == 50 && col <= 45) 0.40f else 0.80f })
        val (lat, lon) = centro(35, 30)
        val c = DetectorContorno.detectar(listOf(conCanal), lat, lon)
        assertTrue(abs(c.areaHa - 40.0) < 3.0, "el lote entero: ${c.areaHa}")
    }

    @Test
    fun simplificaUnRectanguloEscalonadoACuatroEsquinas() {
        val m = BooleanArray(100) { i -> i % 10 in 2..7 && i / 10 in 3..8 }
        val anillo = DetectorContorno.trazarContorno(m, 10, 10)
        assertEquals(4, anillo.size, "esquinas: $anillo")
        assertEquals(setOf(2.0 to 3.0, 8.0 to 3.0, 8.0 to 9.0, 2.0 to 9.0), anillo.toSet())
    }
}
