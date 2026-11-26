package com.example.data.repository

import android.content.Context
import com.example.data.remote.api.OpenMeteoApiService
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.model.AgroClimateDashboard
import com.example.data.remote.model.OpenMeteoResponseDto
import com.example.data.remote.model.PronosticoData
import com.example.data.remote.model.PronosticoDiario
import com.example.data.remote.model.PronosticoHorario
import com.example.data.remote.model.describirCodigoClima
import com.example.utils.DiaBalance
import com.example.utils.HoraPronosticoAgro
import com.example.utils.PronosticoAgro
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.math.roundToInt

class ClimaException(message: String) : Exception(message)

class WeatherRepository(
    private val api: OpenMeteoApiService = RetrofitNetworkClient.openMeteoApi
) {

    // Un solo pedido a la vez: si dos pantallas/refrescos piden lo mismo, el segundo usa el caché del primero
    private val mutex = Mutex()
    private var cacheClave: String? = null
    private var cacheDashboard: AgroClimateDashboard? = null
    private var cacheTimestamp = 0L

    /** Vida del caché y período de refresco: Open-Meteo actualiza sus modelos como mucho cada hora. */
    fun intervaloActualizacion(context: Context?): Long {
        val ahorroDatos = context?.let { WeatherNotificationService.isDataSaverEnabled(it) } ?: false
        return if (ahorroDatos) 60 * 60 * 1000L else 15 * 60 * 1000L
    }

    suspend fun getAgroClimateData(
        context: Context?,
        lat: Double,
        lon: Double,
        cityNameOverride: String? = null,
        forceRefresh: Boolean = false
    ): Result<AgroClimateDashboard> = withContext(Dispatchers.IO) {
        val ciudad = cityNameOverride ?: "Ubicación actual"
        // ~1 km: dos pedidos para casi el mismo punto comparten respuesta
        val clave = String.format(Locale.US, "%.2f,%.2f", lat, lon)

        mutex.withLock {
            val cache = cacheDashboard
            val vigente = System.currentTimeMillis() - cacheTimestamp < intervaloActualizacion(context)
            if (!forceRefresh && cache != null && cacheClave == clave && vigente) {
                return@withContext Result.success(cache.copy(ciudad = ciudad, lat = lat, lon = lon))
            }

            try {
                // Pronóstico (ECMWF), UV (modelo por defecto) y balance hídrico en paralelo; los dos
                // últimos son opcionales
                val (respuesta, uv, balance) = coroutineScope {
                    val pronostico = async { api.getPronostico(lat, lon) }
                    val radiacion = async { opcional("el índice UV") { api.getUv(lat, lon) } }
                    val hidrico = async { opcional("el balance hídrico") { api.getBalanceHidrico(lat, lon) } }
                    Triple(pronostico.await(), radiacion.await(), hidrico.await())
                }
                val ahoraS = System.currentTimeMillis() / 1000
                val dashboard = mapearDashboard(respuesta, ciudad, lat, lon).copy(
                    uvActual = uv?.current?.uvIndex,
                    uvMaxHoy = uv?.daily?.uvIndexMax?.firstOrNull(),
                    balanceHidrico = balance?.let { b ->
                        val d = b.daily ?: return@let null
                        PronosticoAgro.balance(
                            d.time.indices.map { i -> DiaBalance(d.time[i], d.precipitacion.getOrNull(i), d.et0.getOrNull(i)) },
                            ahoraS, b.utcOffsetSeconds
                        )
                    }
                )
                cacheClave = clave
                cacheDashboard = dashboard
                cacheTimestamp = System.currentTimeMillis()
                Result.success(dashboard)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Result.failure(
                    ClimaException(
                        when (e.code()) {
                            429 -> "Se alcanzó el límite de consultas del servicio de clima. Intenta de nuevo en unos minutos."
                            in 500..599 -> "El servicio de clima no responde (error ${e.code()}). Intenta más tarde."
                            else -> "Error al consultar el clima (código ${e.code()})."
                        }
                    )
                )
            } catch (e: IOException) {
                Result.failure(ClimaException("Sin conexión con el servicio de clima. Revisa tu conexión a internet."))
            } catch (e: Exception) {
                Result.failure(ClimaException("No se pudieron interpretar los datos del clima."))
            }
        }
    }

    private suspend fun <T> opcional(que: String, pedido: suspend () -> T): T? = try {
        pedido()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("GranexClima", "No se pudo obtener $que", e)
        null
    }

    /** Horas del pronóstico para la ventana de pulverización y la helada. */
    private fun horasAgro(r: OpenMeteoResponseDto): List<HoraPronosticoAgro> {
        val h = r.hourly ?: return emptyList()
        return h.time.indices.map { i ->
            HoraPronosticoAgro(
                epochS = h.time[i],
                temperaturaC = h.temperatura.getOrNull(i),
                humedadPct = h.humedad.getOrNull(i),
                vientoKmh = h.viento.getOrNull(i),
                rafagaKmh = h.rafagas.getOrNull(i),
                lluviaHoraPreviaMm = h.precipitacion.getOrNull(i),
                esDeDia = h.esDeDia.getOrNull(i)?.let { it == 1 }
            )
        }
    }

    private fun mapearDashboard(
        r: OpenMeteoResponseDto,
        ciudad: String,
        lat: Double,
        lon: Double
    ): AgroClimateDashboard {
        val actual = r.current ?: throw ClimaException("Respuesta sin datos actuales")
        // Horas y días en la zona horaria de la localidad consultada, no la del teléfono
        val zona = SimpleTimeZone(r.utcOffsetSeconds * 1000, "local")

        val horario = mapearHorario(r, zona)
        val diario = mapearDiario(r, zona)
        val hoy = diario.firstOrNull()

        val temp = actual.temperatura
        val humedad = (actual.humedad ?: 0.0).roundToInt().coerceIn(0, 100)
        val viento = actual.viento ?: 0.0
        val codigo = actual.codigoClima ?: -1
        val horasAgro = horasAgro(r)
        val ahoraS = System.currentTimeMillis() / 1000

        return AgroClimateDashboard(
            ciudad = ciudad,
            lat = lat,
            lon = lon,
            tempCelsius = temp,
            tempMin = hoy?.tempMin ?: temp,
            tempMax = hoy?.tempMax ?: temp,
            humedadPorcentaje = humedad,
            presionHpa = (actual.presion ?: 0.0).roundToInt(),
            vientoKmH = viento,
            direccionVientoGrados = (actual.direccionViento ?: 0.0).roundToInt(),
            condicionTexto = describirCodigoClima(codigo),
            evapotranspiracionET0 = hoy?.et0Mm ?: 0.0,
            riesgoFuego = calcularRiesgoFuego(temp, humedad, viento),
            codigoClima = codigo,
            esDeDia = actual.esDeDia != 0,
            sensacionTermica = actual.sensacionTermica ?: temp,
            rafagasKmH = actual.rafagas ?: viento,
            nubosidadPorcentaje = (actual.nubosidad ?: 0.0).roundToInt(),
            lluviaHoyMm = hoy?.precipitacionMm ?: 0.0,
            probLluviaHoy = hoy?.probPrecipitacion,
            tempMinProximas24h = minimaProximas24h(r) ?: (hoy?.tempMin ?: temp),
            lastUpdated = System.currentTimeMillis(),
            forecast = PronosticoData(horario, diario),
            pulverizacion = PronosticoAgro.pulverizacion(horasAgro, ahoraS, r.utcOffsetSeconds)
                .takeIf { it.horas.isNotEmpty() },
            helada = PronosticoAgro.helada(horasAgro, ahoraS, r.utcOffsetSeconds)
        )
    }

    private fun indiceHoraActual(r: OpenMeteoResponseDto): Int {
        val horas = r.hourly?.time ?: return 0
        val ahora = System.currentTimeMillis() / 1000
        return horas.indexOfFirst { it > ahora - 3600 }.coerceAtLeast(0)
    }

    private fun minimaProximas24h(r: OpenMeteoResponseDto): Double? {
        val temps = r.hourly?.temperatura ?: return null
        val inicio = indiceHoraActual(r)
        return temps.drop(inicio).take(24).filterNotNull().minOrNull()
    }

    // 8 bloques de 3 h = próximas 24 h, el formato que usan los gráficos del pronóstico detallado
    private fun mapearHorario(r: OpenMeteoResponseDto, zona: SimpleTimeZone): List<PronosticoHorario> {
        val h = r.hourly ?: return emptyList()
        val formato = SimpleDateFormat("HH'h'", Locale.getDefault()).apply { timeZone = zona }
        val inicio = indiceHoraActual(r)

        return (0 until 8).mapNotNull { bloque ->
            val i = inicio + bloque * 3
            val temp = h.temperatura.getOrNull(i) ?: return@mapNotNull null
            // La lluvia horaria de Open-Meteo es la acumulada en la hora anterior: el bloque "15h"
            // (de 15 a 18) suma los valores de las 16, 17 y 18 h
            val rango = (i + 1)..minOf(i + 3, h.time.size - 1)
            PronosticoHorario(
                hour = formato.format(Date(h.time[i] * 1000)),
                tempCelsius = temp,
                precipitationMm = rango.sumOf { h.precipitacion.getOrNull(it) ?: 0.0 },
                windSpeedKmH = h.viento.getOrNull(i) ?: 0.0,
                windDirectionDegrees = (h.direccionViento.getOrNull(i) ?: 0.0).roundToInt(),
                feelsLikeCelsius = h.sensacionTermica.getOrNull(i),
                gustSpeedKmH = rango.mapNotNull { h.rafagas.getOrNull(it) }.maxOrNull()
            )
        }
    }

    private fun mapearDiario(r: OpenMeteoResponseDto, zona: SimpleTimeZone): List<PronosticoDiario> {
        val d = r.daily ?: return emptyList()
        val dias = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        val calendario = Calendar.getInstance(zona)

        return d.time.indices.mapNotNull { i ->
            val max = d.tempMax.getOrNull(i) ?: return@mapNotNull null
            val min = d.tempMin.getOrNull(i) ?: return@mapNotNull null
            calendario.timeInMillis = d.time[i] * 1000
            PronosticoDiario(
                dayName = if (i == 0) "Hoy" else dias[calendario.get(Calendar.DAY_OF_WEEK) - 1],
                tempMin = min,
                tempMax = max,
                codigoClima = d.codigoClima.getOrNull(i) ?: -1,
                precipitacionMm = d.precipitacion.getOrNull(i) ?: 0.0,
                probPrecipitacion = d.probPrecipitacion.getOrNull(i),
                et0Mm = d.et0.getOrNull(i)
            )
        }
    }

    /**
     * Sugerencias del buscador de Clima mientras se escribe (Photon, ver [GeocodificadorLocalidades]).
     * Sin conexión devuelve las localidades agrícolas predefinidas que coinciden con el texto.
     */
    suspend fun sugerirLocalidades(texto: String, cerca: Pair<Double, Double>?): Result<List<SugerenciaLocalidad>> {
        val remoto = GeocodificadorLocalidades.sugerir(texto, cerca)
        if (remoto.isSuccess) return remoto
        val locales = localidadesPredefinidas()
            .filter { normalizar(it.nombre).contains(normalizar(texto)) }
            .map { it.copy(distanciaKm = cerca?.let { c -> GeocodificadorLocalidades.distanciaKm(c, it.lat to it.lon) }) }
        return if (locales.isNotEmpty()) Result.success(locales) else remoto
    }

    /** Tamaño de una de las localidades agrícolas conocidas (para el zoom de las favoritas). */
    fun tipoConocido(nombre: String): TipoLocalidad? =
        localidadesPredefinidas().firstOrNull { normalizar(it.nombre) == normalizar(nombre) }?.tipo

    private fun localidadesPredefinidas(): List<SugerenciaLocalidad> = listOf(
        predefinida("Rosario", -32.9468, -60.6393, "Santa Fe", TipoLocalidad.CIUDAD_GRANDE),
        predefinida("Marcos Juárez", -32.6972, -62.1067, "Córdoba", TipoLocalidad.PUEBLO),
        predefinida("Pergamino", -33.8903, -60.5739, "Buenos Aires", TipoLocalidad.CIUDAD),
        predefinida("Córdoba", -31.4201, -64.1888, "Córdoba", TipoLocalidad.CIUDAD_GRANDE),
        predefinida("Balcarce", -37.8471, -58.2552, "Buenos Aires", TipoLocalidad.PUEBLO),
        predefinida("Tandil", -37.3217, -59.1332, "Buenos Aires", TipoLocalidad.CIUDAD),
        predefinida("Santa Fe", -31.6333, -60.7000, "Santa Fe", TipoLocalidad.CIUDAD),
        predefinida("Venado Tuerto", -33.7456, -61.9688, "Santa Fe", TipoLocalidad.CIUDAD)
    )

    private fun predefinida(nombre: String, lat: Double, lon: Double, provincia: String, tipo: TipoLocalidad) =
        SugerenciaLocalidad(nombre, "$provincia, Argentina", lat, lon, distanciaKm = null, argentina = true, tipo = tipo)

    private fun normalizar(texto: String): String =
        java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(java.util.Locale.ROOT)
            .trim()

    private fun calcularRiesgoFuego(temp: Double, humidity: Int, windKmH: Double): String {
        return when {
            temp > 30.0 && humidity < 30 && windKmH > 25.0 -> "Extremo"
            temp > 25.0 && humidity < 45 && windKmH > 15.0 -> "Alto"
            temp > 20.0 && humidity < 60 -> "Moderado"
            else -> "Bajo"
        }
    }
}
