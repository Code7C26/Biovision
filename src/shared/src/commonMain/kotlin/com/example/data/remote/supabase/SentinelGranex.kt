package com.example.data.remote.supabase

import com.example.utils.vra.EscenaSentinel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Error con un mensaje listo para mostrar al usuario. */
class ErrorSentinelGranex(mensaje: String) : Exception(mensaje)

/** Imagen de índices de un día, recortada al lote (ver supabase/functions/sentinel). */
class ImagenSentinelRemota(
    val fecha: String,
    val oeste: Double,
    val sur: Double,
    val este: Double,
    val norte: Double,
    val ancho: Int,
    val alto: Int,
    /** PNG de 8 bits: 0 = sin dato, 1..255 = índice de -1 a 1 (R = NDVI, G = NDRE, B = GNDVI). */
    val png: ByteArray
)

/**
 * Imágenes Sentinel-2 a través de la Edge Function `sentinel` de GRANEX: la cuenta de Copernicus
 * queda en el servidor y cada pedido va con el token del usuario (hace falta sesión).
 */
class SentinelGranex(private val supabase: SupabaseClient) {

    @Serializable
    private class RespuestaEscenas(val escenas: List<Escena>)

    @Serializable
    private class Escena(
        val fecha: String,
        val nubesPct: Double,
        val ndvi: Double? = null,
        val ndre: Double? = null,
        val gndvi: Double? = null,
        val pixelesValidos: Int
    )

    @Serializable
    private class RespuestaImagen(
        val fecha: String,
        val oeste: Double,
        val sur: Double,
        val este: Double,
        val norte: Double,
        val ancho: Int,
        val alto: Int,
        val png: String
    )

    /** Fechas con imagen en los últimos [dias] días, la más reciente primero. [anillo] en (lat, lon). */
    suspend fun escenas(anillo: List<Pair<Double, Double>>, dias: Int): List<EscenaSentinel> {
        val respuesta = JsonSupabase.decodeFromString(
            RespuestaEscenas.serializer(),
            invocar(anillo) {
                put("accion", "escenas")
                put("dias", dias)
            }
        )
        return respuesta.escenas.map { EscenaSentinel(it.fecha, it.nubesPct, it.ndvi, it.ndre, it.gndvi, it.pixelesValidos) }
    }

    suspend fun imagen(anillo: List<Pair<Double, Double>>, fecha: String): ImagenSentinelRemota {
        val r = JsonSupabase.decodeFromString(
            RespuestaImagen.serializer(),
            invocar(anillo) {
                put("accion", "imagen")
                put("fecha", fecha)
            }
        )
        return ImagenSentinelRemota(r.fecha, r.oeste, r.sur, r.este, r.norte, r.ancho, r.alto, Base64.decode(r.png))
    }

    /**
     * Pico de vigor de cada píxel en la campaña [desde]–[hasta] (yyyy-MM-dd): R = NDVI (percentil 90
     * de las pasadas sin nubes), G = cantidad de pasadas sin nubes. Procesa meses de imágenes: tarda
     * bastante más que una sola fecha.
     */
    suspend fun campana(anillo: List<Pair<Double, Double>>, desde: String, hasta: String): ImagenSentinelRemota {
        val r = JsonSupabase.decodeFromString(
            RespuestaImagen.serializer(),
            invocar(anillo, tiempoMaximoMs = TIEMPO_CAMPANA_MS) {
                put("accion", "campana")
                put("desde", desde)
                put("hasta", hasta)
            }
        )
        return ImagenSentinelRemota(r.fecha, r.oeste, r.sur, r.este, r.norte, r.ancho, r.alto, Base64.decode(r.png))
    }

    /** Foto en color real (RGBA) de la [fecha], de la caja del lote con un margen alrededor. */
    suspend fun colorReal(anillo: List<Pair<Double, Double>>, fecha: String): ImagenSentinelRemota = imagenDe(
        invocar(anillo) {
            put("accion", "color")
            put("fecha", fecha)
        }
    )

    /** Altura del terreno (Copernicus DEM): altura = (R·256 + G) / 10 − 500 m; B = 255 con dato. */
    suspend fun relieve(anillo: List<Pair<Double, Double>>): ImagenSentinelRemota = imagenDe(
        invocar(anillo) { put("accion", "relieve") }
    )

    private fun imagenDe(json: String): ImagenSentinelRemota {
        val r = JsonSupabase.decodeFromString(RespuestaImagen.serializer(), json)
        return ImagenSentinelRemota(r.fecha, r.oeste, r.sur, r.este, r.norte, r.ancho, r.alto, Base64.decode(r.png))
    }

    private suspend fun invocar(
        anillo: List<Pair<Double, Double>>,
        tiempoMaximoMs: Long? = null,
        campos: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
    ): String {
        val cuerpo = buildJsonObject {
            campos()
            // GeoJSON: [lon, lat]
            put("anillo", JsonArray(anillo.map { (lat, lon) -> JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat))) }))
        }
        return try {
            if (tiempoMaximoMs == null) supabase.functions.invoke(FUNCION, body = cuerpo).bodyAsText()
            else supabase.functions.invoke(FUNCION) {
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
                timeout { requestTimeoutMillis = tiempoMaximoMs }
            }.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            throw ErrorSentinelGranex(mensajeDelServidor(e.error) ?: "El servicio de imágenes respondió con un error (${e.statusCode}).")
        } catch (e: HttpRequestTimeoutException) {
            throw ErrorSentinelGranex("El servidor de imágenes tardó demasiado en responder. Probá de nuevo en un momento.")
        } catch (e: HttpRequestException) {
            throw ErrorSentinelGranex("Sin conexión con el servidor de imágenes. Revisá tu conexión e intentá de nuevo.")
        }
    }

    companion object {
        const val FUNCION = "sentinel"
        /** Una campaña son ~7 meses de pasadas procesadas en el servidor. */
        const val TIEMPO_CAMPANA_MS = 120_000L

        /** La función responde `{"error": "…"}` con un mensaje en español para el usuario. */
        internal fun mensajeDelServidor(cuerpo: String): String? = runCatching {
            JsonSupabase.parseToJsonElement(cuerpo).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
