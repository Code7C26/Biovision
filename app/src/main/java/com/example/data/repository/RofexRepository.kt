package com.example.data.repository

import android.content.Context
import com.example.data.remote.ktor.ServicioFuturosA3
import com.example.data.remote.ktor.crearClienteHttp
import com.example.data.remote.model.EstadoRofex
import com.example.data.remote.model.FuturoRofexContract
import com.example.utils.FuturosA3
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Futuros de soja, maíz y trigo de A3 Mercados (ex MATba ROFEX): precios de ajuste de la API pública
 * del CEM, sin usuario ni contraseña. Antes se leían de reMarkets, que es el entorno de práctica de
 * Primary (sus precios no son los del mercado real) y pedía credenciales a cada usuario.
 */
class RofexRepository(
    private val context: Context,
    private val servicio: ServicioFuturosA3 = ServicioFuturosA3(crearClienteHttp())
) {
    private val mutex = Mutex()
    private var cache: EstadoRofex.Ok? = null

    suspend fun obtenerFuturos(forceRefresh: Boolean = false): EstadoRofex = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vigente = cache?.let { System.currentTimeMillis() - it.actualizado < ttl() } == true
            if (!forceRefresh && vigente) return@withContext cache!!

            try {
                val contratos = FuturosA3.vigentes(servicio.cierres(hoyEnArgentina())).map { c ->
                    FuturoRofexContract(
                        simbolo = c.simbolo,
                        cultivo = c.cultivo,
                        posVencimiento = c.posicion,
                        precioAjusteUSD = c.ajusteUsd,
                        variacionPorcentaje = c.variacionPct,
                        volumenLotes = c.volumenLotes,
                        fechaAjuste = c.fecha,
                        interesAbierto = c.interesAbierto
                    )
                }
                if (contratos.isEmpty()) throw IOException("A3 Mercados no publicó ajustes de soja, maíz o trigo en los últimos días.")
                EstadoRofex.Ok(contratos, System.currentTimeMillis()).also { cache = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si falla una actualización, se sigue mostrando el último ajuste obtenido
                cache ?: EstadoRofex.Error(
                    e.message?.takeIf { it.startsWith("A3") } ?: "No se pudieron leer los futuros de A3 Mercados. Revisá tu conexión."
                )
            }
        }
    }

    // Los ajustes cambian una vez por rueda (17 h): no hace falta consultar seguido
    private fun ttl(): Long =
        if (WeatherNotificationService.isDataSaverEnabled(context)) 3 * 60 * 60 * 1000L else 30 * 60 * 1000L

    private fun hoyEnArgentina(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires") }
            .format(Date())
}
