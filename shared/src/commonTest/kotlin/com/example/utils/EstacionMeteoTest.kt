package com.example.utils

import com.example.utils.EstacionMeteo.Aptitud
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstacionMeteoTest {

    private fun cerca(esperado: Double, real: Double, tolerancia: Double = 0.15) =
        assertTrue(abs(esperado - real) <= tolerancia, "esperado $esperado, fue $real")

    @Test
    fun bulboHumedoComoElEjemploDeStull() {
        // Stull (2011): 20 °C y 50 % → 13,7 °C
        cerca(13.7, EstacionMeteo.bulboHumedo(20.0, 50.0))
        cerca(6.3, EstacionMeteo.deltaT(20.0, 50.0))
        // Aire saturado: Delta T casi cero
        assertTrue(EstacionMeteo.deltaT(15.0, 99.0) < 0.5)
    }

    @Test
    fun puntoDeRocio() {
        cerca(9.3, EstacionMeteo.puntoRocio(20.0, 50.0), 0.2)
    }

    @Test
    fun puntosCardinales() {
        assertEquals("N", EstacionMeteo.puntoCardinal(0.0))
        assertEquals("N", EstacionMeteo.puntoCardinal(350.0))
        assertEquals("NE", EstacionMeteo.puntoCardinal(40.0))
        assertEquals("SO", EstacionMeteo.puntoCardinal(225.0))
        assertEquals("O", EstacionMeteo.puntoCardinal(-90.0))
    }

    @Test
    fun pulverizacionBuena() {
        val c = EstacionMeteo.pulverizacion(20.0, 60.0, 8.0)!!
        assertEquals(Aptitud.BUENA, c.aptitud)
        assertTrue(c.motivos.isEmpty())
    }

    @Test
    fun pulverizacionConAireSecoOVientoFuerte() {
        // 34 °C y 20 %: Delta T > 10
        val seco = EstacionMeteo.pulverizacion(34.0, 20.0, 8.0)!!
        assertEquals(Aptitud.NO_RECOMENDADA, seco.aptitud)
        assertTrue(seco.motivos.first().startsWith("Delta T"))

        assertEquals(Aptitud.NO_RECOMENDADA, EstacionMeteo.pulverizacion(20.0, 60.0, 25.0)!!.aptitud)
        assertEquals(Aptitud.PRECAUCION, EstacionMeteo.pulverizacion(20.0, 60.0, 17.0)!!.aptitud)
        // Ráfagas fuertes con viento medio moderado
        assertEquals(Aptitud.PRECAUCION, EstacionMeteo.pulverizacion(20.0, 60.0, 10.0, rafagaKmh = 24.0)!!.aptitud)
        // Calma: inversión térmica
        assertEquals(Aptitud.PRECAUCION, EstacionMeteo.pulverizacion(20.0, 60.0, 1.0)!!.aptitud)
        // Lloviendo
        assertEquals(Aptitud.NO_RECOMENDADA, EstacionMeteo.pulverizacion(20.0, 60.0, 8.0, lluviaTasaMmH = 2.0)!!.aptitud)
        // Sin humedad no se puede calcular
        assertNull(EstacionMeteo.pulverizacion(20.0, null, 8.0))
    }

    @Test
    fun fechasIsoDelServidor() {
        assertEquals(0L, EstacionMeteo.epochMs("1970-01-01T00:00:00Z"))
        assertEquals(1_763_298_300_000L, EstacionMeteo.epochMs("2025-11-16T13:05:00.000Z"))
        assertEquals(1_763_298_300_000L, EstacionMeteo.epochMs("2025-11-16T13:05:00+00:00"))
        assertEquals(1_763_298_300_000L, EstacionMeteo.epochMs("2025-11-16T10:05:00-03:00"))
        assertEquals(1_763_298_300_123L, EstacionMeteo.epochMs("2025-11-16 13:05:00.123456+00"))
        assertEquals(951_782_400_000L, EstacionMeteo.epochMs("2000-02-29T00:00:00Z"))
        assertNull(EstacionMeteo.epochMs("ayer"))
        assertEquals("10:05", EstacionMeteo.horaArgentina(1_763_298_300_000L))
        assertEquals("21:00", EstacionMeteo.horaArgentina(0L))
    }

    @Test
    fun avisoDeHelada() {
        assertNull(EstacionMeteo.avisoHelada(5.0))
        assertTrue(EstacionMeteo.avisoHelada(2.0)!!.startsWith("Riesgo"))
        assertTrue(EstacionMeteo.avisoHelada(-1.5)!!.startsWith("Helada"))
    }

    @Test
    fun configuracionDelEnvioDirecto() {
        val c = EstacionMeteo.configuracionEnvio("https://abc.supabase.co", "tok_123")
        assertEquals("abc.supabase.co", c.servidor)
        assertEquals("/functions/v1/estaciones/subir/tok_123", c.ruta)
        assertEquals(443, c.puerto)
    }

    @Test
    fun antiguedadDeLaLectura() {
        val ahora = 1_000_000_000L
        assertEquals("recién", EstacionMeteo.antiguedad(ahora - 10_000, ahora))
        assertEquals("hace 5 min", EstacionMeteo.antiguedad(ahora - 5 * 60_000, ahora))
        assertEquals("hace 3 h", EstacionMeteo.antiguedad(ahora - 3 * 3_600_000, ahora))
        assertEquals("hace 3 días", EstacionMeteo.antiguedad(ahora - 72 * 3_600_000, ahora))
    }
}
