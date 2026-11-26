package com.example

import com.example.data.remote.supabase.ConfiguracionSupabase
import com.example.data.remote.supabase.ErrorSentinelGranex
import com.example.data.remote.supabase.JsonSupabase
import com.example.data.remote.supabase.SentinelGranex
import com.example.data.remote.supabase.SesionEnAjustes
import com.example.data.remote.supabase.crearClienteSupabase
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SentinelGranexTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun cliente(motor: MockEngine) = crearClienteSupabase(
        assertNotNull(ConfiguracionSupabase.desde("https://proyecto.supabase.co", "sb_publishable_PruebaDeGranex123")),
        SesionEnAjustes(MapSettings()),
        motor,
        cicloDeVida = false
    )

    @Test
    fun pideConElLoteEnLonLatYDecodificaLaImagen() = runTest {
        val cuerpos = mutableListOf<String>()
        val png = byteArrayOf(-119, 80, 78, 71, 1, 2, 3)
        val motor = MockEngine { pedido ->
            cuerpos += ((pedido.body as OutgoingContent.ByteArrayContent).bytes()).decodeToString()
            assertEquals("/functions/v1/sentinel", pedido.url.encodedPath)
            val accion = JsonSupabase.parseToJsonElement(cuerpos.last()).jsonObject["accion"]!!.jsonPrimitive.content
            respond(
                if (accion == "escenas") {
                    """{"escenas":[{"fecha":"2026-10-05","nubesPct":0.4,"ndvi":0.15,"ndre":0.1,"gndvi":0.2,"pixelesValidos":1780}]}"""
                } else {
                    """{"fecha":"2026-10-05","oeste":-64.28,"sur":-31.59,"este":-64.27,"norte":-31.58,"ancho":98,"alto":81,"png":"${Base64.encode(png)}"}"""
                },
                HttpStatusCode.OK, json
            )
        }
        val c = cliente(motor)
        try {
            val sentinel = SentinelGranex(c)
            val anillo = listOf(-31.59 to -64.28, -31.59 to -64.27, -31.58 to -64.27)

            val escenas = sentinel.escenas(anillo, dias = 60)
            assertEquals("2026-10-05", escenas.single().fecha)
            assertEquals(0.15, escenas.single().ndvi)
            val pedido = JsonSupabase.parseToJsonElement(cuerpos.first()).jsonObject
            assertEquals(60, pedido["dias"]!!.jsonPrimitive.content.toInt())
            val primero = pedido["anillo"]!!.jsonArray.first().jsonArray
            assertEquals(-64.28, primero[0].jsonPrimitive.content.toDouble(), "primero la longitud (GeoJSON)")
            assertEquals(-31.59, primero[1].jsonPrimitive.content.toDouble())

            val imagen = sentinel.imagen(anillo, "2026-10-05")
            assertEquals(98, imagen.ancho)
            assertContentEquals(png, imagen.png)
        } finally {
            c.close()
        }
    }

    @Test
    fun elMensajeDelServidorLlegaAlUsuario() = runTest {
        val motor = MockEngine {
            respond("""{"error":"Llegaste al límite de 200 consultas satelitales por día. Mañana se renueva."}""", HttpStatusCode.TooManyRequests, json)
        }
        val c = cliente(motor)
        try {
            val e = assertFailsWith<ErrorSentinelGranex> {
                SentinelGranex(c).escenas(listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0), dias = 30)
            }
            assertEquals("Llegaste al límite de 200 consultas satelitales por día. Mañana se renueva.", e.message)
        } finally {
            c.close()
        }
    }
}
