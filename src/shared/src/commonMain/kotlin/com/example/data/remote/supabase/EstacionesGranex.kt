package com.example.data.remote.supabase

import com.example.utils.MarcaEstacion
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Error con un mensaje listo para mostrar al usuario. */
class ErrorEstaciones(mensaje: String) : Exception(mensaje)

/** Estación conectada por el usuario (sin credenciales: nunca vuelven del servidor). */
@Serializable
data class EstacionMeteoRemota(
    val id: String,
    val nombre: String,
    val marca: String,
    val identificador: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    /** Último dato recibido (solo envío directo), ISO 8601. */
    val ultimoEnvio: String? = null
) {
    val marcaEstacion: MarcaEstacion? get() = MarcaEstacion.deClave(marca)
}

/** Lectura en unidades métricas: °C, %, km/h, grados, hPa, mm, mm/h, W/m². */
@Serializable
data class LecturaEstacion(
    val momento: String,
    val temperaturaC: Double? = null,
    val humedadPct: Double? = null,
    val puntoRocioC: Double? = null,
    val vientoKmh: Double? = null,
    val rafagaKmh: Double? = null,
    val direccionGrados: Double? = null,
    val presionHpa: Double? = null,
    val lluviaHoyMm: Double? = null,
    val lluviaTasaMmH: Double? = null,
    val lluviaSemanaMm: Double? = null,
    val lluviaMesMm: Double? = null,
    val lluviaAnioMm: Double? = null,
    val radiacionWm2: Double? = null,
    val uv: Double? = null,
    val sueloTemperaturaC: Double? = null,
    val sueloHumedadPct: Double? = null
)

/** Un punto de las últimas 24 h; [lluviaMm] es lo que llovió desde el punto anterior. */
@Serializable
data class PuntoEstacion(
    val momento: String,
    val temperaturaC: Double? = null,
    val humedadPct: Double? = null,
    val vientoKmh: Double? = null,
    val lluviaMm: Double? = null
)

@Serializable
data class DiaEstacion(
    val dia: String,
    val lluviaMm: Double? = null,
    val tempMinC: Double? = null,
    val tempMaxC: Double? = null
)

@Serializable
data class DatosEstacion(
    val actual: LecturaEstacion? = null,
    val serie: List<PuntoEstacion> = emptyList(),
    val diario: List<DiaEstacion> = emptyList(),
    val avisos: List<String> = emptyList()
)

/** Estación de la cuenta de la marca, para elegir cuando hay varias. */
@Serializable
data class EstacionDeCuenta(
    val identificador: String,
    val nombre: String,
    val latitud: Double? = null,
    val longitud: Double? = null
)

sealed interface ResultadoAgregarEstacion {
    /** [token]: solo en el envío directo, y solo esta vez. */
    data class Agregada(val estacion: EstacionMeteoRemota, val token: String?) : ResultadoAgregarEstacion
    /** La cuenta tiene varias estaciones: hay que volver a llamar con el identificador elegido. */
    data class Elegir(val opciones: List<EstacionDeCuenta>) : ResultadoAgregarEstacion
}

/** Operaciones con las estaciones del usuario. Separado del cliente para poder probar la UI. */
interface ServicioEstaciones {
    suspend fun listar(): List<EstacionMeteoRemota>
    suspend fun agregar(
        marca: MarcaEstacion,
        nombre: String,
        credenciales: Map<String, String>,
        identificador: String? = null
    ): ResultadoAgregarEstacion
    suspend fun datos(id: String): DatosEstacion
    suspend fun eliminar(id: String)
}

/** Las estaciones a través de la Edge Function `estaciones` (hace falta sesión). */
class EstacionesGranex(private val supabase: SupabaseClient) : ServicioEstaciones {

    @Serializable
    private class RespuestaLista(val estaciones: List<EstacionMeteoRemota>)

    @Serializable
    private class RespuestaAgregar(
        val estacion: EstacionMeteoRemota? = null,
        val token: String? = null,
        val elegir: List<EstacionDeCuenta>? = null
    )

    override suspend fun listar(): List<EstacionMeteoRemota> =
        JsonSupabase.decodeFromString(RespuestaLista.serializer(), invocar(buildJsonObject { put("accion", "listar") })).estaciones

    override suspend fun agregar(
        marca: MarcaEstacion,
        nombre: String,
        credenciales: Map<String, String>,
        identificador: String?
    ): ResultadoAgregarEstacion {
        val r = JsonSupabase.decodeFromString(
            RespuestaAgregar.serializer(),
            invocar(cuerpoAgregar(marca, nombre, credenciales, identificador))
        )
        return when {
            r.elegir != null -> ResultadoAgregarEstacion.Elegir(r.elegir)
            r.estacion != null -> ResultadoAgregarEstacion.Agregada(r.estacion, r.token)
            else -> throw ErrorEstaciones("El servidor respondió algo inesperado.")
        }
    }

    override suspend fun datos(id: String): DatosEstacion = JsonSupabase.decodeFromString(
        DatosEstacion.serializer(),
        invocar(buildJsonObject {
            put("accion", "datos")
            put("id", id)
        })
    )

    override suspend fun eliminar(id: String) {
        invocar(buildJsonObject {
            put("accion", "eliminar")
            put("id", id)
        })
    }

    private suspend fun invocar(cuerpo: JsonObject): String = try {
        supabase.functions.invoke(FUNCION, body = cuerpo).bodyAsText()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        throw ErrorEstaciones(
            SentinelGranex.mensajeDelServidor(e.error) ?: "El servidor de estaciones respondió con un error (${e.statusCode})."
        )
    } catch (e: HttpRequestTimeoutException) {
        throw ErrorEstaciones("La estación tardó demasiado en responder. Probá de nuevo en un momento.")
    } catch (e: HttpRequestException) {
        throw ErrorEstaciones("Sin conexión. Revisá tu conexión e intentá de nuevo.")
    }

    companion object {
        const val FUNCION = "estaciones"

        internal fun cuerpoAgregar(
            marca: MarcaEstacion,
            nombre: String,
            credenciales: Map<String, String>,
            identificador: String?
        ): JsonObject = buildJsonObject {
            put("accion", "agregar")
            put("marca", marca.clave)
            put("nombre", nombre.trim())
            if (marca != MarcaEstacion.ENVIO) {
                // Solo los campos que pide la marca
                putJsonObject("credenciales") {
                    marca.campos.forEach { campo -> put(campo.clave, credenciales[campo.clave].orEmpty().trim()) }
                }
            }
            if (!identificador.isNullOrBlank()) put("identificador", identificador)
        }
    }
}
