package com.example.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PronosticoAgroTest {

    private val desfase = -3 * 3600
    /** Medianoche local (UTC−3) del día 20000 desde 1970: viernes 4/10/2024. */
    private val medianoche = 20_000L * 86_400L + 3 * 3600

    private fun hora(
        h: Int,
        t: Double = 20.0,
        hum: Double = 60.0,
        viento: Double = 10.0,
        lluviaPrevia: Double = 0.0,
        dia: Boolean = h % 24 in 7..19
    ) = HoraPronosticoAgro(medianoche + h * 3600L, t, hum, viento, viento * 1.3, lluviaPrevia, dia)

    @Test
    fun ventanaDeHorasBuenasYCortePorLluvia() {
        // De 08 a 14 h buenas; la lluvia de 14 a 15 h viene en el dato de las 15 h (hora anterior)
        val horas = (8..20).map { h -> hora(h, lluviaPrevia = if (h == 15) 3.0 else 0.0) }
        val p = PronosticoAgro.pulverizacion(horas, ahoraS = medianoche + 8 * 3600L + 600, desfaseS = desfase)
        val h14 = p.horas.first { it.hora == 14 }
        assertEquals(EstacionMeteo.Aptitud.NO_RECOMENDADA, h14.aptitud, "llueve de 14 a 15")
        assertTrue(h14.motivos.first().startsWith("Lluvia pronosticada"), "motivo principal: lluvia")
        // 12 y 13: la lluvia de las 14 cae en las 2 horas siguientes
        assertEquals(EstacionMeteo.Aptitud.PRECAUCION, p.horas.first { it.hora == 12 }.aptitud, "lavado")
        val primera = p.ventanas.first()
        assertEquals(8, primera.horaDesde, "desde")
        assertEquals(12, primera.horaHasta, "hasta (exclusivo)")
        assertEquals(4, primera.horas, "horas")
        assertEquals(0, primera.dia, "hoy")
    }

    @Test
    fun deNocheConPocoVientoEsPrecaucion() {
        val horas = listOf(hora(2, viento = 4.0, dia = false), hora(3, viento = 4.0, dia = false), hora(4))
        val p = PronosticoAgro.pulverizacion(horas, medianoche + 2 * 3600L, desfase)
        assertEquals(EstacionMeteo.Aptitud.PRECAUCION, p.horas[0].aptitud, "inversión")
        assertTrue(p.horas[0].motivos.any { "inversión" in it }, "motivo")
        assertTrue(p.ventanas.isEmpty(), "sin ventana")
    }

    @Test
    fun deltaTAltoNoEsBueno() {
        val horas = (10..13).map { hora(it, t = 34.0, hum = 15.0) }
        val p = PronosticoAgro.pulverizacion(horas, medianoche + 10 * 3600L, desfase)
        assertTrue(p.horas.all { it.aptitud == EstacionMeteo.Aptitud.NO_RECOMENDADA }, "aire muy seco")
        assertTrue(p.ventanas.isEmpty(), "sin ventanas")
    }

    @Test
    fun ignoraLasHorasYaPasadas() {
        val horas = (0..10).map { hora(it) }
        val p = PronosticoAgro.pulverizacion(horas, medianoche + 5 * 3600L + 1800, desfase)
        assertEquals(5, p.horas.first().hora, "la hora en curso es la primera")
    }

    @Test
    fun heladaConHoraYDuracion() {
        // Mañana (día 1): baja de 3 °C de 02 a 07 h, mínima −1,5 a las 05
        val temps = mapOf(24 + 2 to 2.5, 24 + 3 to 1.0, 24 + 4 to -0.5, 24 + 5 to -1.5, 24 + 6 to 0.5, 24 + 7 to 2.8)
        val horas = (12..60).map { h -> hora(h, t = temps[h] ?: 8.0) }
        val r = assertNotNull(PronosticoAgro.helada(horas, medianoche + 12 * 3600L, desfase))
        assertEquals(NivelHelada.HELADA, r.nivel, "nivel")
        assertEquals(-1.5, r.minimaC, "mínima")
        assertEquals(1, r.diaMinima, "mañana")
        assertEquals(5, r.horaMinima, "hora")
        assertEquals(6, r.horasRiesgo, "horas ≤ 3 °C")
        assertEquals(2, r.horasBajoCero, "horas ≤ 0 °C")
        assertEquals(2, r.desdeHora, "desde")
        assertEquals(8, r.hastaHora, "hasta (exclusivo)")
        assertEquals(1, r.nochesConRiesgo, "una noche")
    }

    @Test
    fun sinRiesgoDeHelada() {
        val horas = (0..71).map { hora(it, t = 10.0 + (it % 24) / 3.0) }
        val r = assertNotNull(PronosticoAgro.helada(horas, medianoche, desfase))
        assertEquals(NivelHelada.SIN_RIESGO, r.nivel, "nivel")
        assertEquals(10.0, r.minimaC, "mínima")
        assertEquals(0, r.horasRiesgo, "sin horas de riesgo")
        assertNull(r.desdeHora, "sin desde")
    }

    @Test
    fun balanceDeLosUltimos30Dias() {
        val hoy = 20_000L
        val dias = (-30..6).map { d ->
            DiaBalance((hoy + d) * 86_400L + 3 * 3600, if (d < 0) 2.0 else 5.0, 4.0)
        }
        val b = assertNotNull(PronosticoAgro.balance(dias, ahoraS = medianoche + 10 * 3600L, desfaseS = desfase))
        assertEquals(30, b.diasPasados, "días pasados")
        assertEquals(60.0, b.lluviaPasadaMm, "lluvia")
        assertEquals(120.0, b.et0PasadaMm, "ET0")
        assertEquals(-60.0, b.balancePasadoMm, "balance")
        assertEquals(14.0, b.lluviaUltimos7Mm, "últimos 7")
        assertEquals(EstadoHidrico.DEFICIT_MODERADO, b.estado, "estado")
        assertEquals(7, b.diasProximos, "próximos")
        assertEquals(35.0, b.lluviaProximaMm, "lluvia próxima")
        assertEquals(30, b.indiceHoy, "índice de hoy")
        assertEquals(37, b.dias.size, "días visibles")
    }

    @Test
    fun balanceSinPasadoEsNull() {
        val dias = (0..6).map { DiaBalance((20_000L + it) * 86_400L + 3 * 3600, 1.0, 4.0) }
        assertNull(PronosticoAgro.balance(dias, medianoche + 3600, desfase), "sin días pasados")
    }

    @Test
    fun nombresDeDias() {
        assertEquals("Hoy", PronosticoAgro.nombreDia(0, 20_000), "hoy")
        assertEquals("Mañana", PronosticoAgro.nombreDia(1, 20_001), "mañana")
        assertEquals("Viernes", PronosticoAgro.nombreDia(2, 20_000), "4/10/2024 fue viernes")
        assertEquals("05:00", PronosticoAgro.hora(5), "hora")
        assertEquals("04/10", PronosticoAgro.fechaDiaMes(20_000), "fecha")
        assertEquals("01/03", PronosticoAgro.fechaDiaMes(20_513), "1/3/2026, después de febrero")
    }

    @Test
    fun informaElDiaDeHoy() {
        val p = PronosticoAgro.pulverizacion(listOf(hora(10), hora(11)), medianoche + 10 * 3600L, desfase)
        assertEquals(20_000L, p.hoy, "hoy")
    }
}
