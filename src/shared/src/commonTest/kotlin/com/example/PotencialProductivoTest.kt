package com.example

import com.example.utils.vra.CalculoPotencial
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.Campanas
import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.ErrorPotencial
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Temporada
import com.example.utils.vra.Zonificador
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PotencialProductivoTest {

    /** Grilla de 40 × 40 píxeles de 10 m (400 × 400 m, 16 ha) y el lote que la cubre. */
    private val grilla = GrillaGeografica(-61.0, -33.0, 10 / 93_370.0, 10 / 110_950.0, 40, 40)
    private val lote = listOf(
        grilla.norte to grilla.oeste, grilla.norte to grilla.este, grilla.sur to grilla.este, grilla.sur to grilla.oeste
    )

    private fun campana(etiqueta: String, ndvi: (fila: Int, col: Int) -> Float) = CampanaNdvi(
        Campana(etiqueta, "x", "y"), grilla, FloatArray(grilla.total) { ndvi(it / 40, it % 40) }, IntArray(grilla.total) { 12 }
    )

    private fun cerca(esperado: Double, real: Double, tol: Double, mensaje: String) =
        assertTrue(abs(esperado - real) <= tol, "$mensaje: esperado $esperado, real $real")

    @Test
    fun campanasTerminadas() {
        val verano = Campanas.terminadas("2026-10-29", Temporada.VERANO)
        assertEquals("2019/20", verano.first().etiqueta)
        assertEquals(Campana("2025/26", "2025-10-01", "2026-04-30"), verano.last())
        assertEquals(listOf("2022/23", "2023/24", "2024/25", "2025/26"), Campanas.porDefecto("2026-10-29", Temporada.VERANO).map { it.etiqueta })
        // La fina de 2026 termina el 30 de noviembre: todavía no cuenta
        assertEquals("2025", Campanas.terminadas("2026-10-29", Temporada.INVIERNO).last().etiqueta)
        assertEquals("2026", Campanas.terminadas("2026-12-15", Temporada.INVIERNO).last().etiqueta)
    }

    @Test
    fun combinaCampanasRelativasYDescartaLaQueNoTuvoCultivo() {
        // La mitad oeste rinde 20 % más todos los años; en 2023/24 el lote no tuvo cultivo
        val a = campana("2022/23") { _, c -> if (c < 20) 0.84f else 0.70f }
        val b = campana("2023/24") { _, _ -> 0.25f }
        val d = campana("2024/25") { _, c -> if (c < 20) 0.72f else 0.60f }
        val p = CalculoPotencial.combinar(listOf(a, b, d), lote)

        assertEquals(listOf(true, false, true), p.campanas.map { it.usada })
        assertTrue(p.campanas[1].motivo!!.startsWith("Sin cultivo"), p.campanas[1].motivo!!)
        assertEquals("NDVI pico de 2 campañas (2022/23 a 2024/25)", p.descripcion)
        // Relativo a la mediana de cada campaña (la mitad este con la mediana más baja de la lista)
        val oeste = p.indice[5 * 40 + 2].toDouble()
        val este = p.indice[5 * 40 + 35].toDouble()
        cerca(1.2, oeste / este, 0.001, "el oeste queda 20 % arriba del este")
        cerca(0.0, p.variacionEntreCampanas[5 * 40 + 2].toDouble(), 1e-6, "mismo patrón los dos años")
        assertEquals(0.0, p.inestablePct())
    }

    @Test
    fun sinCampanasUtilesAvisa() {
        val e = assertFailsWith<ErrorPotencial> { CalculoPotencial.combinar(listOf(campana("2023/24") { _, _ -> 0.2f }), lote) }
        assertTrue(e.message!!.startsWith("Ninguna campaña"))
    }

    private fun imagen(valor: (fila: Int, col: Int) -> Float): ImagenIndices {
        val v = FloatArray(grilla.total) { valor(it / 40, it % 40) }
        return ImagenIndices(grilla, "2026-01-15", v, v, v)
    }

    @Test
    fun recomiendaTantasZonasComoNivelesTieneElLote() {
        val dos = Zonificador.recomendarZonas(imagen { _, c -> if (c < 20) 0.55f else 0.85f }, IndiceVegetacion.NDVI, lote, 20.0)
        assertEquals(2, dos.zonas)
        cerca(1.0, dos.explicada.getValue(2), 0.01, "dos niveles: 2 zonas explican todo")

        val tres = Zonificador.recomendarZonas(
            imagen { _, c -> when { c < 13 -> 0.45f; c < 27 -> 0.65f; else -> 0.85f } }, IndiceVegetacion.NDVI, lote, 20.0
        )
        assertEquals(3, tres.zonas)
        assertTrue(tres.explicada.getValue(2) < 0.85, "con 2 zonas no alcanza: ${tres.explicada}")

        val parejo = Zonificador.recomendarZonas(imagen { f, c -> 0.80f + 0.002f * ((f + c) % 5) }, IndiceVegetacion.NDVI, lote, 20.0)
        assertTrue(parejo.homogeneo)
        assertEquals(2, parejo.zonas)
        assertFalse(dos.homogeneo)
    }

    @Test
    fun zonificaConSieteZonasYConElPotencial() {
        // Gradiente de oeste a este: alcanza para 7 zonas distintas
        val img = imagen { _, c -> 0.40f + 0.012f * c }
        val z = Zonificador.zonificar(img, IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 7, celdaM = 10.0, areaMinimaHa = 0.1))
        assertEquals(7, z.zonas.size)
        assertTrue(z.zonas.zipWithNext().all { (a, b) -> a.indicePromedio < b.indicePromedio }, "zonas ordenadas de menor a mayor")

        // El potencial entra al mismo Zonificador como una imagen
        val p = CalculoPotencial.combinar(listOf(campana("2024/25") { _, c -> if (c < 20) 0.84f else 0.70f }), lote)
        val zp = Zonificador.zonificar(p.comoImagen(), IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 2, celdaM = 20.0))
        assertEquals(2, zp.zonas.size)
        cerca(1.2, zp.zonas.last().indicePromedio / zp.zonas.first().indicePromedio, 0.01, "la zona alta rinde 20 % más que la baja")
    }

    @Test
    fun sugiereNdreCuandoElNdviEstaSaturado() {
        fun conNdre(etiqueta: String, ndvi: Float) = CampanaNdvi(
            Campana(etiqueta, "x", "y"), grilla,
            FloatArray(grilla.total) { ndvi }, IntArray(grilla.total) { 12 },
            ndre = FloatArray(grilla.total) { i -> if (i % 40 < 20) 0.55f else 0.45f }
        )
        val saturadas = listOf(conNdre("2023/24", 0.91f), conNdre("2024/25", 0.90f))
        assertEquals(IndiceVegetacion.NDRE, CalculoPotencial.indiceSugerido(saturadas, lote))
        assertEquals(IndiceVegetacion.NDVI, CalculoPotencial.indiceSugerido(listOf(conNdre("2023/24", 0.75f)), lote))

        // Con NDRE aparecen las diferencias que el NDVI saturado (todo 0,91) escondía
        val p = CalculoPotencial.combinar(saturadas, lote, IndiceVegetacion.NDRE)
        assertEquals("NDRE pico de 2 campañas (2023/24 a 2024/25)", p.descripcion)
        cerca(0.55 / 0.45, p.indice[3].toDouble() / p.indice[30].toDouble(), 0.001, "oeste / este con NDRE")
    }

    @Test
    fun unBajoConAguaNoDefineLasZonas() {
        // Lote parejo con dos niveles suaves (0,98 y 1,02) y un bajo con agua de 3 × 3 celdas (0,40)
        val img = imagen { f, c -> if (f in 5..7 && c in 5..7) 0.40f else if (c < 20) 0.98f else 1.02f }
        val rec = Zonificador.recomendarZonas(img, IndiceVegetacion.NDVI, lote, 10.0)
        assertTrue(rec.atipicosPct > 0.4, "el bajo es atípico: ${rec.atipicosPct}")
        cerca(1.0, rec.explicada.getValue(2), 0.01, "sin el bajo, 2 zonas explican los dos niveles")

        val z = Zonificador.zonificar(img, IndiceVegetacion.NDVI, lote, ConfigZonificacion(zonas = 2, celdaM = 10.0, areaMinimaHa = 0.01))
        // El corte queda entre 0,98 y 1,02, no entre el agua y el resto: el bajo cae en la zona baja
        val baja = z.zonas.first()
        cerca(0.98 * 0.99 + 0.40 * 0.01, baja.indicePromedio, 0.02, "zona baja = oeste + bajo")
        assertTrue(z.zonas.last().indiceMin > 1.0, "la zona alta es el este: ${z.zonas.last()}")
        assertFalse(z.areaSubioPorLimite)
    }
}
