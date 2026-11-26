package com.example

import com.example.utils.AnalisisRelieve
import com.example.utils.EscaneoLote
import com.example.utils.PosicionRelieve
import com.example.utils.RelieveLote
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.IndiceVegetacion
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RelieveTest {

    /** 40 × 40 píxeles de 10 m (16 ha) y el lote que los cubre. */
    private val grilla = GrillaGeografica(-61.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 40, 40)
    private val lote = listOf(grilla.norte to grilla.oeste, grilla.norte to grilla.este, grilla.sur to grilla.este, grilla.sur to grilla.oeste)

    private fun cerca(esperado: Double, real: Double, tol: Double, mensaje: String) =
        assertTrue(abs(esperado - real) <= tol, "$mensaje: esperado $esperado, real $real")

    @Test
    fun lomaAlEsteYBajoAlOeste() {
        // Sube 10 cm cada 10 m hacia el este: 4 m de desnivel y 1 % de pendiente
        val r = RelieveLote(grilla, FloatArray(grilla.total) { 500f + 0.1f * (it % 40) })
        val res = assertNotNull(AnalisisRelieve.resumir(r, lote))
        assertFalse(res.plano)
        cerca(3.9, res.desnivel, 0.01, "desnivel (con la altura pareja las puntas son las columnas extremas)")
        cerca(1.0, res.pendienteMediaPct, 0.05, "pendiente")
        val bajo = res.clases.first { it.posicion == PosicionRelieve.BAJO }
        val loma = res.clases.first { it.posicion == PosicionRelieve.LOMA }
        assertTrue(bajo.porcentaje in 25.0..40.0 && loma.porcentaje in 25.0..40.0, "${res.clases}")
        cerca(16.0, res.clases.sumOf { it.hectareas }, 0.1, "superficie")
        assertTrue(res.diagnostico.startsWith("Desnivel de 3,"), res.diagnostico)
        // Para zonificar: altura sobre el punto más bajo del lote
        assertEquals(0f, r.comoImagen(lote).ndvi.min())
    }

    @Test
    fun lotePlanoNoInventaRelieve() {
        val r = RelieveLote(grilla, FloatArray(grilla.total) { 500f + 0.05f * ((it * 7) % 9) })
        val res = assertNotNull(AnalisisRelieve.resumir(r, lote))
        assertTrue(res.plano)
        assertEquals(AnalisisRelieve.UMBRAL_MINIMO_M, res.umbral)
        assertTrue(res.diagnostico.startsWith("Lote plano"), res.diagnostico)
        // Las diferencias de pocos centímetros quedan todas en media loma
        assertEquals(100.0, res.clases.first { it.posicion == PosicionRelieve.MEDIA_LOMA }.porcentaje)
    }

    private fun escena(fecha: String, ndvi: Double) = EscenaSentinel(fecha, 0.0, ndvi, ndvi * 0.6, ndvi, 500)

    @Test
    fun marcaLasCaidasDeUnDiaPeroNoLaCosecha() {
        val neblina = listOf(
            escena("2026-01-05", 0.80), escena("2026-01-10", 0.81), escena("2026-01-15", 0.55),
            escena("2026-01-20", 0.82), escena("2026-01-25", 0.83), escena("2026-01-30", 0.82)
        )
        assertEquals(setOf("2026-01-15"), EscaneoLote.fechasDudosas(neblina, IndiceVegetacion.NDVI))

        // Cosecha: el vigor cae y no vuelve; no es neblina
        val cosecha = listOf(
            escena("2026-03-01", 0.80), escena("2026-03-06", 0.78), escena("2026-03-11", 0.76),
            escena("2026-03-16", 0.30), escena("2026-03-21", 0.25), escena("2026-03-26", 0.22)
        )
        assertTrue(EscaneoLote.fechasDudosas(cosecha, IndiceVegetacion.NDVI).isEmpty())
        // La última fecha no se puede juzgar hasta que llegue la siguiente
        assertTrue(EscaneoLote.fechasDudosas(neblina.take(2) + escena("2026-01-15", 0.55), IndiceVegetacion.NDVI).isEmpty())
    }
}
