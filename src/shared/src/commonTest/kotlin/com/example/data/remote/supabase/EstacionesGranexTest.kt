package com.example.data.remote.supabase

import com.example.utils.MarcaEstacion
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EstacionesGranexTest {

    @Test
    fun alAgregarSoloSeMandanLasClavesDeLaMarca() {
        val cuerpo = EstacionesGranex.cuerpoAgregar(
            MarcaEstacion.ECOWITT, " Campo norte ",
            mapOf("applicationKey" to " a ", "apiKey" to "b", "apiSecret" to "no"), "AA:BB"
        )
        assertEquals("ecowitt", cuerpo["marca"]!!.jsonPrimitive.content)
        assertEquals("Campo norte", cuerpo["nombre"]!!.jsonPrimitive.content)
        val credenciales = cuerpo["credenciales"]!!.jsonObject
        assertEquals(setOf("applicationKey", "apiKey"), credenciales.keys)
        assertEquals("a", credenciales["applicationKey"]!!.jsonPrimitive.content)
        assertEquals("AA:BB", cuerpo["identificador"]!!.jsonPrimitive.content)

        val envio = EstacionesGranex.cuerpoAgregar(MarcaEstacion.ENVIO, "Galpón", emptyMap(), null)
        assertFalse("credenciales" in envio)
        assertFalse("identificador" in envio)
    }

    @Test
    fun leeLaRespuestaDeDatos() {
        val json = """{"actual":{"momento":"2026-11-16T13:00:00.000Z","temperaturaC":21.4,"humedadPct":null,"lluviaHoyMm":3.2},
            "serie":[{"momento":"2026-11-16T12:30:00.000Z","temperaturaC":21,"lluviaMm":null}],
            "diario":[{"dia":"2026-11-15","lluviaMm":12.5}],"avisos":["x"],"otro":1}"""
        val d = JsonSupabase.decodeFromString(DatosEstacion.serializer(), json)
        assertEquals(21.4, d.actual!!.temperaturaC)
        assertNull(d.actual!!.humedadPct)
        assertEquals(1, d.serie.size)
        assertEquals(12.5, d.diario.single().lluviaMm)
        assertEquals(listOf("x"), d.avisos)
    }
}
