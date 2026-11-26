package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Respuesta de Open-Meteo (/v1/forecast con timeformat=unixtime). Los arrays pueden traer null al final del horizonte.
@JsonClass(generateAdapter = true)
data class OpenMeteoResponseDto(
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    @Json(name = "current") val current: OpenMeteoCurrentDto? = null,
    @Json(name = "hourly") val hourly: OpenMeteoHourlyDto? = null,
    @Json(name = "daily") val daily: OpenMeteoDailyDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrentDto(
    @Json(name = "time") val time: Long,
    @Json(name = "temperature_2m") val temperatura: Double,
    @Json(name = "relative_humidity_2m") val humedad: Double? = null,
    @Json(name = "apparent_temperature") val sensacionTermica: Double? = null,
    @Json(name = "precipitation") val precipitacion: Double? = null,
    @Json(name = "weather_code") val codigoClima: Int? = null,
    @Json(name = "cloud_cover") val nubosidad: Double? = null,
    @Json(name = "pressure_msl") val presion: Double? = null,
    @Json(name = "wind_speed_10m") val viento: Double? = null,
    @Json(name = "wind_direction_10m") val direccionViento: Double? = null,
    @Json(name = "wind_gusts_10m") val rafagas: Double? = null,
    /** 1 de día, 0 de noche en el lugar (el ícono del clima de ahora: sol o luna). */
    @Json(name = "is_day") val esDeDia: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourlyDto(
    @Json(name = "time") val time: List<Long> = emptyList(),
    @Json(name = "temperature_2m") val temperatura: List<Double?> = emptyList(),
    @Json(name = "relative_humidity_2m") val humedad: List<Double?> = emptyList(),
    @Json(name = "apparent_temperature") val sensacionTermica: List<Double?> = emptyList(),
    @Json(name = "precipitation") val precipitacion: List<Double?> = emptyList(),
    @Json(name = "wind_speed_10m") val viento: List<Double?> = emptyList(),
    @Json(name = "wind_direction_10m") val direccionViento: List<Double?> = emptyList(),
    @Json(name = "wind_gusts_10m") val rafagas: List<Double?> = emptyList(),
    @Json(name = "is_day") val esDeDia: List<Int?> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDailyDto(
    @Json(name = "time") val time: List<Long> = emptyList(),
    @Json(name = "weather_code") val codigoClima: List<Int?> = emptyList(),
    @Json(name = "temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @Json(name = "temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    @Json(name = "precipitation_sum") val precipitacion: List<Double?> = emptyList(),
    @Json(name = "precipitation_probability_max") val probPrecipitacion: List<Int?> = emptyList(),
    @Json(name = "et0_fao_evapotranspiration") val et0: List<Double?> = emptyList()
)

/** Bloque de 3 horas del pronóstico: valores al inicio del bloque, lluvia acumulada y ráfaga máxima. */
data class PronosticoHorario(
    val hour: String,
    val tempCelsius: Double,
    val precipitationMm: Double,
    val windSpeedKmH: Double,
    val windDirectionDegrees: Int,
    val feelsLikeCelsius: Double? = null,
    val gustSpeedKmH: Double? = null
)

data class PronosticoDiario(
    val dayName: String,
    val tempMin: Double,
    val tempMax: Double,
    val codigoClima: Int,
    val precipitacionMm: Double,
    val probPrecipitacion: Int?,
    val et0Mm: Double?
) {
    val isRainy: Boolean get() = precipitacionMm >= 1.0 || esCodigoLluvia(codigoClima)
}

data class PronosticoData(
    val hourlyList: List<PronosticoHorario>,
    val dailyList: List<PronosticoDiario>
)

data class AgroClimateDashboard(
    val ciudad: String,
    val lat: Double,
    val lon: Double,
    val tempCelsius: Double,
    val tempMin: Double,
    val tempMax: Double,
    val humedadPorcentaje: Int,
    val presionHpa: Int,
    val vientoKmH: Double,
    val direccionVientoGrados: Int,
    val condicionTexto: String,
    val evapotranspiracionET0: Double, // FAO-56 Penman-Monteith, mm/día (Open-Meteo)
    val riesgoFuego: String, // "Bajo", "Moderado", "Alto", "Extremo"
    val codigoClima: Int = 0,
    /** De día en el lugar (is_day de Open-Meteo); de noche el clima de ahora muestra la luna. */
    val esDeDia: Boolean = true,
    val sensacionTermica: Double = tempCelsius,
    val rafagasKmH: Double = vientoKmH,
    val nubosidadPorcentaje: Int = 0,
    val lluviaHoyMm: Double = 0.0,
    val probLluviaHoy: Int? = null,
    val tempMinProximas24h: Double = tempMin,
    val lastUpdated: Long = System.currentTimeMillis(),
    val forecast: PronosticoData? = null,
    /** Índice UV actual y máximo de hoy (Open-Meteo, modelo por defecto); null si no se pudo obtener. */
    val uvActual: Double? = null,
    val uvMaxHoy: Double? = null,
    /** Aptitud para pulverizar hora por hora en las próximas 48 h (estimada por el modelo). */
    val pulverizacion: com.example.utils.PronosticoPulverizacion? = null,
    /** Mínima de las próximas 72 h y cuánto dura el frío. */
    val helada: com.example.utils.PronosticoHelada? = null,
    /** Lluvia frente a ET0 de los últimos 30 días y los próximos 7; null si no se pudo obtener. */
    val balanceHidrico: com.example.utils.BalanceHidrico? = null
)

data class AlertasClima(
    val helada: String? = null,
    val lluvia: String? = null,
    val viento: String? = null
) {
    val hayAlguna: Boolean get() = helada != null || lluvia != null || viento != null
}

/** Reglas comunes para el banner de la pantalla y las notificaciones. */
fun evaluarAlertas(d: AgroClimateDashboard, umbralHeladaC: Double): AlertasClima {
    // Los bloques horarios son de 3 h: los dos primeros cubren las próximas 6 h
    val proximas6h = d.forecast?.hourlyList?.take(2).orEmpty()
    val lluvia6h = proximas6h.sumOf { it.precipitationMm }
    val rafagaMax = proximas6h.mapNotNull { it.gustSpeedKmH }.maxOrNull() ?: d.rafagasKmH
    val loc = java.util.Locale.getDefault()
    return AlertasClima(
        helada = if (d.tempMinProximas24h <= umbralHeladaC) {
            String.format(loc, "Mínima pronosticada de %.1f °C en las próximas 24 horas.", d.tempMinProximas24h)
        } else null,
        lluvia = if (lluvia6h >= 5.0) {
            String.format(loc, "Se esperan %.1f mm de lluvia en las próximas 6 horas.", lluvia6h)
        } else null,
        viento = if (rafagaMax >= 50.0) {
            String.format(loc, "Ráfagas de hasta %.0f km/h en las próximas 6 horas.", rafagaMax)
        } else null
    )
}

// Respuesta de /v1/forecast?current=uv_index&daily=uv_index_max
@JsonClass(generateAdapter = true)
data class OpenMeteoUvDto(
    @Json(name = "current") val current: OpenMeteoUvActualDto? = null,
    @Json(name = "daily") val daily: OpenMeteoUvDiarioDto? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoUvActualDto(@Json(name = "uv_index") val uvIndex: Double? = null)

@JsonClass(generateAdapter = true)
data class OpenMeteoUvDiarioDto(@Json(name = "uv_index_max") val uvIndexMax: List<Double?> = emptyList())
