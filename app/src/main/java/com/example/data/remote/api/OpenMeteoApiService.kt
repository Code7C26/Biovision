package com.example.data.remote.api

import com.example.data.remote.model.OpenMeteoResponseDto
import com.example.data.remote.model.OpenMeteoUvDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    @GET("v1/forecast")
    suspend fun getPronostico(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day",
        // Humedad e is_day: ventana de pulverización (Delta T e inversión térmica nocturna)
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,et0_fao_evapotranspiration",
        // "America/Argentina/Rosario" no es aceptada por la API; "auto" resuelve la zona por coordenadas
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeformat: String = "unixtime",
        @Query("forecast_days") dias: Int = 7,
        // ECMWF IFS HRES de 9 km: el mismo modelo que el mapa de Windy (product=ecmwf) y el que elige
        // best_match en Argentina. Antes era ecmwf_ifs025 (grilla de 0,25°): tomaba el punto de grilla
        // más cercano, a ~10 km (en Alta Gracia, -31,75 / -64,50), y el viento y la lluvia no coincidían
        @Query("models") modelo: String = "ecmwf_ifs"
    ): OpenMeteoResponseDto

    /**
     * Lluvia y ET0 de los últimos 30 días y los próximos 7, para el balance hídrico. Va aparte para
     * no correr los índices del pronóstico diario (past_days agrega días al principio). Lo pasado
     * también sale del modelo: es una estimación, no una medición.
     */
    @GET("v1/forecast")
    suspend fun getBalanceHidrico(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "precipitation_sum,et0_fao_evapotranspiration",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeformat: String = "unixtime",
        @Query("past_days") diasPasados: Int = 30,
        @Query("forecast_days") dias: Int = 7,
        @Query("models") modelo: String = "ecmwf_ifs"
    ): OpenMeteoResponseDto

    /**
     * Índice UV actual y máximo del día. Va aparte y sin "models": el modelo ECMWF de getPronostico
     * no publica UV (la API devuelve null) y el modelo por defecto sí.
     */
    @GET("v1/forecast")
    suspend fun getUv(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "uv_index",
        @Query("daily") daily: String = "uv_index_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") dias: Int = 1
    ): OpenMeteoUvDto
}
