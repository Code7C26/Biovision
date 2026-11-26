package com.example

import com.example.data.remote.ktor.DolarDelDia
import com.example.data.remote.ktor.RuedaPizarra
import com.example.utils.HistorialPizarra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistorialPizarraTest {

    private val dolar = listOf(
        DolarDelDia("2026-11-03", compra = 1500.0),
        DolarDelDia("2026-11-05", compra = 1600.0),
        DolarDelDia("2026-11-04", compra = null)
    )

    @Test
    fun convierteConElDolarDelDiaOElUltimoAnterior() {
        val ruedas = listOf(
            RuedaPizarra("2026-11-05", soja = 480_000.0),
            RuedaPizarra("2026-11-03", soja = 450_000.0),
            RuedaPizarra("2026-11-04", soja = 465_000.0)
        )
        val serie = HistorialPizarra.serie(ruedas, "soja", dolar)
        assertEquals(listOf("2026-11-03", "2026-11-04", "2026-11-05"), serie.map { it.fecha }, "ordenada por fecha")
        assertEquals(300.0, serie[0].precioUsd, 1e-9)
        // El 04/11 no hay dólar: se usa el del 03/11
        assertEquals(310.0, serie[1].precioUsd, 1e-9)
        assertEquals(300.0, serie[2].precioUsd, 1e-9)
        assertEquals(465_000.0, serie[1].precioArs, 1e-9)
    }

    @Test
    fun descartaRuedasSinPrecioOSinDolarPrevio() {
        val ruedas = listOf(
            RuedaPizarra("2026-11-02", maiz = 300_000.0), // antes del primer dólar
            RuedaPizarra("2026-11-03", maiz = null),
            RuedaPizarra("2026-11-05", maiz = 320_000.0, soja = 480_000.0)
        )
        val serie = HistorialPizarra.serie(ruedas, "maiz", dolar)
        assertEquals(listOf("2026-11-05"), serie.map { it.fecha })
        assertEquals(200.0, serie.single().precioUsd, 1e-9)
    }

    @Test
    fun sinDolarNoHaySerie() {
        val serie = HistorialPizarra.serie(listOf(RuedaPizarra("2026-11-05", trigo = 1.0)), "trigo", emptyList())
        assertTrue(serie.isEmpty(), "sin tipo de cambio no se inventan precios en US$")
    }
}
