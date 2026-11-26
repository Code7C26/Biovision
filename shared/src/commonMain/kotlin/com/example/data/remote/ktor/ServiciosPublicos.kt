package com.example.data.remote.ktor

import com.example.data.repository.LectorLocalidades
import com.example.data.repository.SugerenciaLocalidad
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

// --- Dólar (dolarapi.com, sin clave, con CORS) ---

@Serializable
data class CotizacionDolar(
    val casa: String,
    val nombre: String,
    val compra: Double? = null,
    val venta: Double? = null,
    val fechaActualizacion: String = ""
)

class ServicioCotizaciones(private val http: HttpClient) {
    suspend fun dolares(): List<CotizacionDolar> = http.get("https://dolarapi.com/v1/dolares").body()
}

// --- Pronóstico (Open-Meteo, sin clave, con CORS; uso no comercial: mostrar "Datos: Open-Meteo.com") ---

@Serializable
data class PronosticoOpenMeteo(
    val current: ActualOpenMeteo? = null,
    val hourly: HorarioOpenMeteo? = null,
    val daily: DiarioOpenMeteo? = null
)

/**
 * Valores por hora (hora local "yyyy-MM-ddTHH:mm"). La lluvia de cada hora es la acumulada en la
 * hora anterior (convención de Open-Meteo).
 */
@Serializable
data class HorarioOpenMeteo(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperatura: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val viento: List<Double?> = emptyList()
)

/** Índice UV ahora y máximo de hoy (pedido aparte: el modelo ECMWF del pronóstico no publica UV). */
data class UvHoy(val actual: Double?, val maximo: Double?)

@Serializable
private data class RespuestaUv(val current: ActualUv? = null, val daily: DiarioUv? = null)

@Serializable
private data class ActualUv(@SerialName("uv_index") val uv: Double? = null)

@Serializable
private data class DiarioUv(@SerialName("uv_index_max") val maximo: List<Double?> = emptyList())

@Serializable
data class ActualOpenMeteo(
    val time: String = "",
    @SerialName("temperature_2m") val temperatura: Double? = null,
    @SerialName("apparent_temperature") val sensacion: Double? = null,
    @SerialName("relative_humidity_2m") val humedad: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val codigo: Int? = null,
    @SerialName("cloud_cover") val nubosidad: Double? = null,
    /** 1 de día, 0 de noche (en el lugar). */
    @SerialName("is_day") val esDeDia: Int? = null,
    @SerialName("wind_speed_10m") val viento: Double? = null,
    @SerialName("wind_direction_10m") val direccionViento: Double? = null,
    @SerialName("wind_gusts_10m") val rafagas: Double? = null,
    @SerialName("pressure_msl") val presion: Double? = null
)

@Serializable
data class DiarioOpenMeteo(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val codigo: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val maxima: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minima: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val lluvia: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val probabilidadLluvia: List<Double?> = emptyList(),
    @SerialName("et0_fao_evapotranspiration") val evapotranspiracion: List<Double?> = emptyList()
)

/**
 * Horas y días con instante Unix (timeformat=unixtime), para las herramientas agro de
 * [com.example.utils.PronosticoAgro]: pulverización, helada y balance hídrico (como WeatherRepository
 * en Android). [desfase] es el de la localidad, en segundos.
 */
@Serializable
data class RespuestaAgroOpenMeteo(
    @SerialName("utc_offset_seconds") val desfase: Int = 0,
    val hourly: HorarioAgroOpenMeteo? = null,
    val daily: DiarioBalanceOpenMeteo? = null
)

@Serializable
data class HorarioAgroOpenMeteo(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperatura: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humedad: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val viento: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val rafagas: List<Double?> = emptyList(),
    @SerialName("is_day") val esDeDia: List<Int?> = emptyList()
)

@Serializable
data class DiarioBalanceOpenMeteo(
    val time: List<Long> = emptyList(),
    @SerialName("precipitation_sum") val lluvia: List<Double?> = emptyList(),
    @SerialName("et0_fao_evapotranspiration") val et0: List<Double?> = emptyList()
)

class ServicioPronostico(private val http: HttpClient) {
    suspend fun pronostico(lat: Double, lon: Double): PronosticoOpenMeteo =
        http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter(
                "current",
                "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,is_day,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
            )
            parameter("hourly", "temperature_2m,precipitation,wind_speed_10m")
            parameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,et0_fao_evapotranspiration"
            )
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
            // ECMWF IFS HRES de 9 km, como la app Android y el mapa de Windy: el ecmwf_ifs025 de antes
            // (grilla de 0,25°) tomaba un punto a ~10 km y el viento y la lluvia no coincidían
            parameter("models", "ecmwf_ifs")
        }.body()

    /** Horas de los próximos días con humedad, ráfagas e is_day: ventana de pulverización y helada. */
    suspend fun horasAgro(lat: Double, lon: Double): RespuestaAgroOpenMeteo =
        http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("hourly", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,wind_gusts_10m,is_day")
            parameter("timezone", "auto")
            parameter("timeformat", "unixtime")
            parameter("forecast_days", 4)
            parameter("models", "ecmwf_ifs")
        }.body()

    /**
     * Lluvia y ET0 de los últimos 30 días y los próximos 7 (balance hídrico). Aparte, como en Android,
     * para no correr los índices del pronóstico diario. Lo pasado también es del modelo, no medido.
     */
    suspend fun balanceHidrico(lat: Double, lon: Double): RespuestaAgroOpenMeteo =
        http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("daily", "precipitation_sum,et0_fao_evapotranspiration")
            parameter("timezone", "auto")
            parameter("timeformat", "unixtime")
            parameter("past_days", 30)
            parameter("forecast_days", 7)
            parameter("models", "ecmwf_ifs")
        }.body()

    /** UV sin "models": el modelo por defecto lo publica (como OpenMeteoApiService.getUv en Android). */
    suspend fun uv(lat: Double, lon: Double): UvHoy {
        val r: RespuestaUv = http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("current", "uv_index")
            parameter("daily", "uv_index_max")
            parameter("timezone", "auto")
            parameter("forecast_days", 1)
        }.body()
        return UvHoy(r.current?.uv, r.daily?.maximo?.firstOrNull())
    }
}

// --- Pizarra del día (granos.ar, con CORS; pide atribución: "Fuente: granos.ar") ---

@Serializable
data class RespuestaPizarra(val data: PizarraHoy)

@Serializable
data class PizarraHoy(
    val fecha: String,
    @SerialName("tipo_cambio_bna_divisas") val tipoCambio: Double? = null,
    val granos: Map<String, PrecioGrano> = emptyMap(),
    @SerialName("fuente_precios") val fuentePrecios: String? = null
)

@Serializable
data class PrecioGrano(
    @SerialName("ars_tn") val arsTn: Double? = null,
    @SerialName("usd_tn") val usdTn: Double? = null,
    @SerialName("variacion_pct_vs_rueda_anterior") val variacionPct: Double? = null
)

@Serializable
data class RespuestaHistoricoPizarra(val data: HistoricoPizarra)

@Serializable
data class HistoricoPizarra(val serie: List<RuedaPizarra> = emptyList())

/** Una rueda de la pizarra de Rosario en ARS/t (null = sin cotización ese día). */
@Serializable
data class RuedaPizarra(
    val fecha: String,
    val soja: Double? = null,
    val maiz: Double? = null,
    val trigo: Double? = null,
    val girasol: Double? = null,
    val sorgo: Double? = null
) {
    /** Precio del grano por su clave de granos.ar ("soja", "maiz", …). */
    fun precio(clave: String): Double? = when (clave) {
        "soja" -> soja
        "maiz" -> maiz
        "trigo" -> trigo
        "girasol" -> girasol
        "sorgo" -> sorgo
        else -> null
    }
}

/** Dólar mayorista de un día (api.argentinadatos.com, con CORS). */
@Serializable
data class DolarDelDia(val fecha: String, val compra: Double? = null)

class ServicioPizarra(private val http: HttpClient) {
    suspend fun hoy(): PizarraHoy = http.get("https://granosar.lfcaucino.workers.dev/api/v1/pizarra").body<RespuestaPizarra>().data

    /** Pizarra de la Cámara Arbitral de Rosario por rueda, en ARS/t (granos.ar guarda hasta 365 ruedas). */
    suspend fun historico(dias: Int = 365): List<RuedaPizarra> =
        http.get("https://granosar.lfcaucino.workers.dev/api/v1/pizarra/historico?dias=$dias").body<RespuestaHistoricoPizarra>().data.serie

    /** Serie del dólar mayorista, la misma con la que Android pasa la pizarra a US$ (RosarioMarketRepository). */
    suspend fun dolarMayorista(): List<DolarDelDia> =
        http.get("https://api.argentinadatos.com/v1/cotizaciones/dolares/mayorista").body()
}

// --- Provincia de un lote (Nominatim /reverse, con CORS) ---

/**
 * Provincia del centro de un lote, como SateliteViewModel.detectarProvincia en Android: un solo
 * pedido iniciado por el usuario al guardar (la política de Nominatim prohíbe el autocompletado,
 * no esto), nivel provincia (zoom=5) y caché en memoria por centésimo de grado.
 */
class ServicioProvincias(private val http: HttpClient) {
    private val cache = mutableMapOf<String, String>()

    /** null si no hay una provincia para ese punto (mar, otro país sin "state"). */
    suspend fun provinciaDe(lat: Double, lon: Double): String? {
        val clave = "${(lat * 100).roundToInt()},${(lon * 100).roundToInt()}"
        cache[clave]?.let { return it }
        val cuerpo = http.get("https://nominatim.openstreetmap.org/reverse") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("format", "jsonv2")
            parameter("zoom", 5)
            parameter("accept-language", "es")
        }.bodyAsText()
        return provinciaDeRespuesta(cuerpo)?.also { cache[clave] = it }
    }

    companion object {
        /** address.state de la respuesta jsonv2; null si no viene o el texto no es JSON. */
        fun provinciaDeRespuesta(json: String): String? = runCatching {
            val direccion = (Json.parseToJsonElement(json) as JsonObject)["address"] as? JsonObject
            (direccion?.get("state") as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

// --- Localidades (geocodificación de Open-Meteo, con CORS) ---

class ServicioLocalidades(private val http: HttpClient) {
    /** [cerca] (lat, lon) ordena primero las localidades próximas a ese punto. */
    suspend fun sugerir(texto: String, cerca: Pair<Double, Double>?): List<SugerenciaLocalidad> {
        val cuerpo = http.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", texto.trim())
            parameter("count", LectorLocalidades.CANDIDATOS)
            parameter("language", "es")
            parameter("countryCode", "AR")
            parameter("format", "json")
        }.bodyAsText()
        return LectorLocalidades.parsear(cuerpo, texto, cerca)
    }
}
