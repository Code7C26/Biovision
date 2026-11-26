package com.example

import com.example.utils.ClaseVigor
import com.example.utils.CoberturaLote
import com.example.utils.EscaneoLote
import com.example.utils.ModoEscala
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EscaneoLoteTest {

    /** Lote de 10 × 10 píxeles de unos 10 m cerca de Córdoba (-31,6°): ~1 ha. */
    private fun imagen(fecha: String = "2026-10-05", ndvi: (fila: Int, col: Int) -> Float): ImagenIndices {
        val grilla = GrillaGeografica(oeste = -64.28, norte = -31.58, pasoLon = 10 / 94_870.0, pasoLat = 10 / 110_950.0, columnas = 10, filas = 10)
        val valores = FloatArray(100) { ndvi(it / 10, it % 10) }
        return ImagenIndices(grilla, fecha, valores, FloatArray(100) { valores[it] * 0.6f }, valores.copyOf())
    }

    private fun cerca(esperado: Double, real: Double, tolerancia: Double, mensaje: String = "") =
        assertTrue(abs(esperado - real) <= tolerancia, "$mensaje: esperado $esperado, real $real")

    @Test
    fun barbechoNoSeMarcaComoProblema() {
        // Suelo desnudo con algo de textura (como el lote "fondo" el 5 de octubre: NDVI 0,12 a 0,29)
        val r = assertNotNull(EscaneoLote.resumir(imagen { f, c -> 0.12f + 0.017f * ((f + c) % 10) }, IndiceVegetacion.NDVI))
        assertEquals(CoberturaLote.SUELO_DESNUDO, r.cobertura)
        assertTrue("sin cultivo activo" in r.diagnostico, r.diagnostico)
        // Escala absoluta: todo el lote cae en el primer quinto de la paleta (por eso "no varía")
        assertEquals(0.0, r.escala(ModoEscala.ABSOLUTA).minimo)
        // Escala relativa: se estira a lo que hay en el lote
        val rel = r.escala(ModoEscala.RELATIVA)
        assertTrue(rel.minimo > 0.1 && rel.maximo < 0.3, "relativa $rel")
    }

    @Test
    fun manchaBajaEnCultivoActivo() {
        // Soja a 0,80 con una mancha de 3 × 3 píxeles a 0,55 (9 % del lote)
        val r = assertNotNull(EscaneoLote.resumir(imagen { f, c -> if (f < 3 && c < 3) 0.55f else 0.80f }, IndiceVegetacion.NDVI))
        assertEquals(CoberturaLote.CULTIVO_ACTIVO, r.cobertura)
        val bajo = r.zonas.first { it.clase == ClaseVigor.BAJO }
        cerca(9.0, bajo.porcentaje, 0.01, "% bajo")
        cerca(0.09, bajo.hectareas, 0.005, "ha bajo (9 píxeles de 100 m²)")
        cerca(1.0, r.hectareasConDato, 0.02, "ha con dato")
        assertEquals(0.0, r.zonas.first { it.clase == ClaseVigor.ALTO }.porcentaje)
    }

    @Test
    fun loteParejoYNubes() {
        // Parejo, con la mitad del lote tapada por nubes (NaN)
        val r = assertNotNull(EscaneoLote.resumir(imagen { f, _ -> if (f < 5) Float.NaN else 0.70f + 0.01f * f }, IndiceVegetacion.NDVI))
        assertTrue(r.diagnostico.startsWith("Lote parejo"), r.diagnostico)
        cerca(0.5, r.hectareasConDato, 0.01, "solo cuenta la mitad sin nubes")
        // Sin variación la escala relativa tiene un rango mínimo (no se exagera el ruido)
        val rel = r.escala(ModoEscala.RELATIVA)
        assertTrue(rel.maximo - rel.minimo >= EscaneoLote.RANGO_MINIMO - 1e-9)
        // Todo nublado: no hay resumen
        assertNull(EscaneoLote.resumir(imagen { _, _ -> Float.NaN }, IndiceVegetacion.NDVI))
    }

    @Test
    fun comparaFechasPixelAPixel() {
        val antes = imagen("2026-09-25") { _, _ -> 0.30f }
        // Crece todo menos una franja que se seca y una celda nublada
        val despues = imagen("2026-10-25") { f, c -> if (f == 0 && c == 0) Float.NaN else if (f == 9) 0.20f else 0.50f }
        val cmp = assertNotNull(EscaneoLote.comparar(antes, despues, IndiceVegetacion.NDVI))
        val haPixel = EscaneoLote.hectareasPorPixel(despues)
        cerca(89 * haPixel, cmp.hectareasSubio, 1e-9, "ha que subieron")
        cerca(10 * haPixel, cmp.hectareasBajo, 1e-9, "ha que bajaron")
        assertTrue(cmp.diferencia[0].isNaN(), "sin dato en una fecha = sin cambio calculado")
        // Imágenes de otro lote (otra grilla): no se comparan
        val otro = ImagenIndices(antes.grilla.copy(columnas = 9), "2026-10-25", FloatArray(90), FloatArray(90), FloatArray(90))
        assertNull(EscaneoLote.comparar(antes, otro, IndiceVegetacion.NDVI))
    }

    @Test
    fun escenasUtilesDescartaNubladas() {
        val escenas = listOf(
            EscenaSentinel("2026-10-25", 45.0, 0.2, 0.1, 0.3, 500),
            EscenaSentinel("2026-10-05", 0.4, 0.15, 0.1, 0.2, 1780),
            EscenaSentinel("2026-09-25", 12.0, null, null, null, 0)
        )
        assertEquals(listOf("2026-10-05"), EscaneoLote.escenasUtiles(escenas).map { it.fecha })
    }

    @Test
    fun escalaRelativaCentradaEnLoTipico() {
        // Barbecho a 0,17 con un desagüe verde (0,35) en una fila: lo típico tiene que quedar en el medio
        val r = assertNotNull(EscaneoLote.resumir(imagen { f, c -> if (f == 9) 0.35f else 0.16f + 0.002f * c }, IndiceVegetacion.NDVI))
        val e = r.escala(ModoEscala.RELATIVA)
        cerca(r.mediana, (e.minimo + e.maximo) / 2, 1e-9, "centro de la escala")
        assertTrue(e.maximo >= 0.35 - 1e-6, "el desagüe entra en la escala: $e")
    }

    @Test
    fun soloCuentanLosPixelesConElCentroEnElLote() {
        // Contorno que cubre las 5 columnas del oeste (centros de columna 0 a 4) de la grilla de 10 × 10
        val g = imagen { _, _ -> 0.5f }.grilla
        val oesteMitad = g.oeste + 5 * g.pasoLon
        val contorno = listOf(g.norte to g.oeste, g.norte to oesteMitad, g.sur to oesteMitad, g.sur to g.oeste)
        val img = imagen { _, _ -> 0.5f }
        val todo = assertNotNull(EscaneoLote.resumir(img, IndiceVegetacion.NDVI))
        val mitad = assertNotNull(EscaneoLote.resumir(img, IndiceVegetacion.NDVI, contorno))
        cerca(todo.hectareasConDato / 2, mitad.hectareasConDato, 1e-9, "la mitad oeste")
        assertEquals(50, EscaneoLote.dentroDelLote(img, contorno).count { it })
    }
}
