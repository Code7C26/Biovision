package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.GranexApplication
import java.util.concurrent.TimeUnit

/**
 * Sincroniza los lotes con Supabase. Corre solo con conexión: si se programa sin señal, WorkManager
 * espera a que vuelva (y sobrevive a reinicios del teléfono). Sin backend configurado no hace nada.
 */
class SincronizacionLotesWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GranexApplication).container
        val sincronizador = container.sincronizadorLotes ?: return Result.success()
        val lotes = sincronizador.sincronizar()
        // Suelo, rinde y dosis van después: los lotes nuevos ya tienen su uuid en la nube
        val agronomia = if (lotes is ResultadoSincronizacion.Lista && container.cuenta.usuarioIdCargado() != null) {
            runCatching { container.sincronizadorAgronomia?.sincronizar() }.getOrElse { it.message ?: "error" }
        } else null
        // La Agenda no depende de los lotes (los gastos guardan el uuid): va con cualquier resultado
        val agenda = if (container.cuenta.usuarioIdCargado() != null) {
            container.sincronizadorAgenda?.let { s -> runCatching { s.sincronizar() }.exceptionOrNull() }
        } else null
        val fallo = lotes is ResultadoSincronizacion.Error || agronomia != null || agenda != null
        return when {
            fallo && runAttemptCount < MAX_REINTENTOS -> Result.retry()
            fallo -> Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        private const val NOMBRE = "sincronizacion_lotes"
        private const val MAX_REINTENTOS = 5

        /**
         * Programa una pasada. Si hay una en curso, la nueva va después (APPEND_OR_REPLACE): así un
         * cambio hecho mientras se sincronizaba también se sube.
         */
        fun programar(contexto: Context) {
            val pedido = OneTimeWorkRequestBuilder<SincronizacionLotesWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(contexto).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
        }
    }
}
