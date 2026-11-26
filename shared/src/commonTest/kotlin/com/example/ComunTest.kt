package com.example

import com.example.data.repository.LectorLocalidades
import com.example.data.repository.PreferenciasUsuario
import com.example.utils.FechasIso
import com.example.utils.formatoDecimal
import com.example.utils.grados
import com.example.utils.radianes
import com.russhwolf.settings.MapSettings
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reemplazos de APIs de la JVM: deben dar lo mismo en Android (JVM), JS y Wasm. */
class ComunTest {

    @Test
    fun formatoDecimalComoStringFormatEnEsAr() {
        assertEquals("0,13", formatoDecimal(0.125, 2))
        assertEquals("13", formatoDecimal(12.5, 0))
        assertEquals("-1,5", formatoDecimal(-1.5, 1))
        assertEquals("0,00", formatoDecimal(-0.004, 2))
        assertEquals("1234,50", formatoDecimal(1234.5, 2))
    }

    @Test
    fun fechasIso() {
        assertEquals("2027-01-01", FechasIso.sumarDias("2026-12-31", 1))
        assertEquals("2028-02-29", FechasIso.sumarDias("2028-02-28", 1))
        assertEquals(2, FechasIso.diasEntre("2026-02-27", "2026-03-01"))
        assertEquals(5, FechasIso.diaDeSemana("2026-05-01")) // viernes
        assertEquals(7, FechasIso.diaDeSemana("2026-10-25")) // domingo
        assertEquals("2026-09-19", FechasIso.desdeDiaDelAnio(2026, 262))
        assertEquals("2027-01-01", FechasIso.desdeDiaDelAnio(2026, 366)) // como Calendar (leniente)
    }

    @Test
    fun angulos() {
        assertEquals(PI, radianes(180.0), 1e-15)
        assertEquals(90.0, grados(PI / 2), 1e-12)
    }

    @Test
    fun normalizaNombresSinTildes() {
        assertEquals("canada de gomez", LectorLocalidades.normalizar(" Cañada de Gómez "))
        assertEquals("guemes", LectorLocalidades.normalizar("Güemes"))
    }

    @Test
    fun preferenciasConLasClavesDeAndroid() {
        val settings = MapSettings()
        val preferencias = PreferenciasUsuario(settings)
        assertNull(preferencias.ultimaCiudad())
        preferencias.guardarUltimaCiudad("Pergamino", -33.89, -60.57)
        preferencias.guardarZoomLocalidad(" Pergamino ", 12)
        assertEquals("Pergamino", settings.getStringOrNull("last_city_name"))
        assertEquals(-33.89, preferencias.ultimaCiudad()!!.lat, 1e-5)
        assertEquals(12, preferencias.zoomLocalidad("pergamino"))
    }
}
