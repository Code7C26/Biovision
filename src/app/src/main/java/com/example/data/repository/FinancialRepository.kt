package com.example.data.repository

import android.content.Context
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.model.DolarRateDto
import com.example.data.remote.model.FinancialDashboardData
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FinancialRepository(
    private val context: Context,
    private val rofexRepository: RofexRepository
) {
    private val mutex = Mutex()
    private var cacheDolares: List<DolarRateDto>? = null
    private var cacheTimestamp = 0L

    private val prefs by lazy { context.getSharedPreferences("granex_cotizaciones_referencia", Context.MODE_PRIVATE) }

    suspend fun getFinancialAndRofexData(forceRefresh: Boolean = false): Result<FinancialDashboardData> {
        val dolares = obtenerDolares(forceRefresh)
        val (divisas, desdeCache) = dolares.getOrElse { return Result.failure(it) }
        return Result.success(
            FinancialDashboardData(
                divisas = divisas,
                rofex = rofexRepository.obtenerFuturos(forceRefresh),
                timestampCache = cacheTimestamp,
                isFromCache = desdeCache
            )
        )
    }

    /** Cotizaciones del dólar y si vienen del caché. Ante un error de red devuelve el último dato válido. */
    suspend fun obtenerDolares(forceRefresh: Boolean = false): Result<Pair<List<DolarRateDto>, Boolean>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val ahorroDatos = WeatherNotificationService.isDataSaverEnabled(context)
                val ttl = if (ahorroDatos) 60 * 60 * 1000L else 15 * 60 * 1000L
                val cache = cacheDolares
                if (!forceRefresh && cache != null && System.currentTimeMillis() - cacheTimestamp < ttl) {
                    return@withContext Result.success(cache to true)
                }
                try {
                    val divisas = normalizar(RetrofitNetworkClient.dolarApi.getDolarRates())
                    if (divisas.isEmpty()) throw IllegalStateException("Respuesta sin cotizaciones")
                    cacheDolares = divisas
                    cacheTimestamp = System.currentTimeMillis()
                    Result.success(divisas to false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (cache != null) Result.success(cache to true)
                    else Result.failure(Exception("No se pudieron obtener las cotizaciones del dólar. Revisa tu conexión."))
                }
            }
        }

    private fun normalizar(list: List<DolarRateDto>): List<DolarRateDto> {
        val orden = listOf("oficial", "blue", "mep", "ccl", "tarjeta")
        return list.mapNotNull { item ->
            val casa = when (item.casa.lowercase()) {
                "bolsa" -> "mep"
                "contadoconliqui" -> "ccl"
                else -> item.casa.lowercase()
            }
            if (casa !in orden) return@mapNotNull null
            val nombre = when (casa) {
                "oficial" -> "Dólar Oficial"
                "blue" -> "Dólar Blue"
                "mep" -> "Dólar MEP"
                "ccl" -> "Dólar CCL"
                else -> "Dólar Tarjeta"
            }
            item.copy(casa = casa, nombre = nombre, variacion = variacionDiaria(casa, item))
        }.sortedBy { orden.indexOf(it.casa) }
    }

    /**
     * dolarapi.com no informa variación: se calcula contra el último valor de venta visto en una
     * fecha de cotización anterior (fechaActualizacion). Sin referencia todavía, la variación es 0.
     */
    private fun variacionDiaria(casa: String, item: DolarRateDto): Double {
        val fecha = item.fechaActualizacion.take(10).ifEmpty { return 0.0 }
        val fechaUltima = prefs.getString("${casa}_fecha", null)
        // Si cambió el día de cotización, el último valor del día anterior pasa a ser la referencia
        val referencia = if (fechaUltima != null && fechaUltima < fecha) {
            prefs.getFloat("${casa}_venta", 0f)
        } else {
            prefs.getFloat("${casa}_referencia", 0f)
        }
        if (fechaUltima == null || fechaUltima <= fecha) {
            prefs.edit()
                .putString("${casa}_fecha", fecha)
                .putFloat("${casa}_venta", item.venta.toFloat())
                .putFloat("${casa}_referencia", referencia)
                .apply()
        }
        return if (referencia > 0f) (item.venta - referencia) / referencia * 100.0 else 0.0
    }
}
