package com.example

import com.example.utils.AnalisisRelieve
import com.example.utils.CapaMapaEscaneo
import com.example.utils.EscalaColor
import com.example.utils.ModoEscala
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Capas del escaneo para la página del mapa (web), en el mismo formato que Android. */
class CapaMapaEscaneoTest {

    // 2 × 2 píxeles de 0,001° (≈ 100 m) a partir de (-60.002, -33.000)
    private val oeste = -60.002
    private val norte = -33.000
    private val este = -60.000
    private val sur = -33.002

    // Lote que contiene solo los centros de la columna izquierda
    private val lote = listOf(-33.0000 to -60.0020, -33.0000 to -60.0012, -33.0020 to -60.0012, -33.0020 to -60.0020)

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun grillaDelIndiceConNullFueraDelLoteYSinDato() {
        // R = NDVI: 255 → 1.0, 128 ≈ 0.004, 0 = sin dato
        val pixeles = intArrayOf(argb(255, 1, 1), argb(255, 1, 1), argb(0, 1, 1), argb(128, 1, 1))
        val imagen = ImagenIndices.desdePixeles(pixeles, 2, 2, oeste, sur, este, norte, "2026-10-12")
        val json = CapaMapaEscaneo.indice(imagen, IndiceVegetacion.NDVI, EscalaColor(0.2, 0.9), ModoEscala.ABSOLUTA, lote)
        val o = Json.parseToJsonElement(json).jsonObject
        assertEquals("s2", o["capa"]!!.jsonPrimitive.content)
        assertEquals("indice", o["rampa"]!!.jsonPrimitive.content)
        assertEquals("Sentinel-2 · 12/10/2026", o["fecha"]!!.jsonPrimitive.content)
        assertEquals(0.9, o["escala"]!!.jsonObject["max"]!!.jsonPrimitive.double)
        assertEquals(2, o["geo"]!!.jsonObject["columnas"]!!.jsonPrimitive.int)
        val v = o["valores"]!!.jsonArray
        assertEquals(1.0, v[0].jsonPrimitive.double, "adentro y con dato")
        assertEquals(JsonNull, v[1], "centro fuera del lote")
        assertEquals(JsonNull, v[2], "sin dato (nube)")
        assertEquals(JsonNull, v[3], "fuera del lote")
    }

    @Test
    fun relieveDecodificaAlturasYQuedaRelativoALaMediana() {
        // altura = (R·256 + G) / 10 − 500: 5050 → 5,0 m; 5060 → 6,0 m; B = 0 → sin dato
        val pixeles = intArrayOf(argb(5050 / 256, 5050 % 256, 255), argb(0, 0, 0), argb(5060 / 256, 5060 % 256, 255), argb(0, 0, 0))
        val relieve = CapaMapaEscaneo.relieveDesdePixeles(pixeles, 2, 2, oeste, sur, este, norte)
        assertEquals(5.0f, relieve.altura[0])
        assertTrue(relieve.altura[1].isNaN())
        assertEquals(6.0f, relieve.altura[2])
        val resumen = AnalisisRelieve.resumir(relieve, lote)!!
        val o = Json.parseToJsonElement(CapaMapaEscaneo.relieve(relieve, resumen, lote)).jsonObject
        assertEquals("relieve", o["rampa"]!!.jsonPrimitive.content)
        assertEquals("m", o["unidad"]!!.jsonPrimitive.content)
        assertTrue(o["escala"]!!.jsonObject["max"]!!.jsonPrimitive.double >= 0.5, "nunca menos de medio metro")
    }

    @Test
    fun fotoEnBase64ConLimites() {
        val o = Json.parseToJsonElement(CapaMapaEscaneo.foto("2026-10-12", byteArrayOf(1, 2, 3), oeste, sur, este, norte)) as JsonObject
        assertEquals("s2foto", o["capa"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,AQID", o["imagen"]!!.jsonPrimitive.content)
        assertEquals(norte, o["limites"]!!.jsonObject["norte"]!!.jsonPrimitive.double)
    }
}
