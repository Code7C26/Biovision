package com.example

import com.example.data.remote.model.CotizacionHistorica
import com.example.utils.AgregacionPrecios
import com.example.utils.FechasIso
import com.example.utils.Granularidad
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class AgregacionPreciosTest {

    /** 90 días de pizarra solo en días hábiles (como la Cámara Arbitral), precio subiendo 1 US$ por rueda. */
    private val serie: List<CotizacionHistorica> = run {
        var fecha = "2026-05-01" // viernes
        var precio = 300.0
        buildList {
            repeat(90) {
                // 6 = sábado, 7 = domingo (ISO)
                if (FechasIso.diaDeSemana(fecha) < 6) {
                    add(CotizacionHistorica(fecha, precio, precio * 1000))
                    precio += 1.0
                }
                fecha = AgregacionPrecios.sumarDias(fecha, 1)
            }
        }
    }

    @Test
    fun sieteDiasMuestraCadaRuedaConVariacionDiaria() {
        val a = AgregacionPrecios.agregar(serie, Granularidad.DIARIA)
        // Ventana de 7 días de calendario: 5 ruedas
        assertEquals(5, a.puntos.size)
        assertEquals(5, a.filas.size)
        assertEquals(serie.last().fecha, a.puntos.last().fecha)
        // Más reciente primero, con variación contra la rueda anterior
        assertEquals(serie.last().precioUsd, a.filas.first().cierreUsd, 0.0)
        assertEquals(1.0 / (serie.last().precioUsd - 1) * 100, a.filas.first().variacionPct!!, 1e-9)
    }

    @Test
    fun treintaDiasAgrupaCadaTresDiasYResumePorSemana() {
        val a = AgregacionPrecios.agregar(serie, Granularidad.TRES_DIAS)
        val ruedas = serie.count { it.fecha >= AgregacionPrecios.sumarDias(serie.last().fecha, -29) }
        assertTrue(a.puntos.size in 8..11 && a.puntos.size < ruedas, "menos puntos que ruedas: ${a.puntos.size} de $ruedas")
        // Cada punto es el cierre de su tramo
        assertEquals(serie.last().precioUsd, a.puntos.last().precioUsd, 0.0)
        assertTrue(a.filas.size in 5..6)
        assertEquals(ruedas, a.filas.sumOf { it.ruedas })
        a.filas.forEach { assertTrue(it.minUsd <= it.promedioUsd && it.promedioUsd <= it.maxUsd) }
    }

    @Test
    fun tresMesesUsaCierresSemanalesYResumenMensual() {
        val a = AgregacionPrecios.agregar(serie, Granularidad.SEMANAL)
        assertTrue(a.puntos.size in 13..14, "puntos semanales: ${a.puntos.size}")
        assertTrue(a.filas.size in 3..4)
        assertEquals(serie.size, a.filas.sumOf { it.ruedas })
        // El primer mes de la serie no tiene mes anterior con qué comparar
        assertNull(a.filas.last().variacionPct)
        assertTrue(a.filas.first().titulo.matches(Regex("[A-Z][a-z]+ 2026")))
    }

    @Test
    fun anualTomaTodaLaSerieConCierresSemanales() {
        val a = AgregacionPrecios.agregar(serie, Granularidad.ANUAL)
        // Los 90 días entran enteros en la ventana de un año: un punto por semana y una fila por mes
        assertEquals(serie.last(), a.puntos.last().copy(puerto = serie.last().puerto))
        assertTrue(a.puntos.size in 13..14, "semanas en 90 días: ${a.puntos.size}")
        assertEquals(listOf("Julio 2026", "Junio 2026", "Mayo 2026"), a.filas.map { it.titulo })
    }

    @Test
    fun serieVaciaNoFalla() {
        val a = AgregacionPrecios.agregar(emptyList(), Granularidad.SEMANAL)
        assertTrue(a.puntos.isEmpty() && a.filas.isEmpty())
    }
}
