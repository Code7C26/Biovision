package com.example.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.GranexApplication
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Chequeo periódico en segundo plano (también con la app cerrada) de clima, mercado y noticias.
 * Cada servicio de notificación deduplica por su cuenta, así que repetir datos no genera avisos repetidos.
 */
class AlertasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!WeatherNotificationService.isNotificationsEnabled(ctx)) return Result.success()

        // Con ahorro de datos el chequeo efectivo es cada 3 h en lugar de cada hora
        val estado = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        if (WeatherNotificationService.isDataSaverEnabled(ctx) &&
            ahora - estado.getLong(KEY_ULTIMO_CHEQUEO, 0L) < INTERVALO_AHORRO_MS
        ) return Result.success()
        estado.edit().putLong(KEY_ULTIMO_CHEQUEO, ahora).apply()

        val container = (ctx as GranexApplication).container
        coroutineScope {
            launch {
                aislado {
                    val ciudad = UserPreferencesRepository(ctx).getLastSelectedCity()
                    container.weatherRepository
                        .getAgroClimateData(ctx, ciudad?.lat ?: -32.9468, ciudad?.lon ?: -60.6393, ciudad?.name ?: "Rosario")
                        .onSuccess { WeatherNotificationService.checkAndTriggerNotifications(ctx, it) }
                }
            }
            if (WeatherNotificationService.isMarketAlertEnabled(ctx)) launch {
                aislado {
                    container.financialRepository.getFinancialAndRofexData()
                        .onSuccess { WeatherNotificationService.notificarMercado(ctx, it) }
                }
            }
            if (WeatherNotificationService.isNewsAlertEnabled(ctx)) launch {
                aislado { WeatherNotificationService.notificarNoticias(ctx, container.newsRepository.getRealTimeNews()) }
            }
        }
        return Result.success()
    }

    // Un fallo en una fuente (p. ej. sin respuesta de A3 Mercados) no debe impedir las demás alertas
    private suspend fun aislado(bloque: suspend () -> Unit) {
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AlertasWorker", "Chequeo de alertas fallido", e)
        }
    }

    companion object {
        private const val NOMBRE_TRABAJO = "granex_alertas"
        private const val PREFS = "granex_alertas_worker"
        private const val KEY_ULTIMO_CHEQUEO = "ultimo_chequeo"
        private const val INTERVALO_AHORRO_MS = (3 * 60 - 10) * 60 * 1000L

        /** Idempotente: KEEP conserva el trabajo ya programado (sobrevive a reinicios del equipo). */
        fun programar(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertasWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOMBRE_TRABAJO, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
