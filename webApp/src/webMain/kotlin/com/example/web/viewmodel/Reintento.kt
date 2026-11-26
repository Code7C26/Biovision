package com.example.web.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Pedido a un servicio externo con reintentos ante fallas pasajeras: un teléfono que sale del
 * reposo o cambia de red pierde las primeras conexiones (ERR_CONNECTION_ABORTED), y sin esto la
 * pantalla quedaba en error hasta tocar "Reintentar". Prueba de nuevo a los 2 s y a los 5 s.
 */
suspend fun <T> conReintentos(esperasMs: List<Long> = listOf(2_000L, 5_000L), pedido: suspend () -> T): T {
    var ultimo: Throwable? = null
    for (espera in listOf(0L) + esperasMs) {
        if (espera > 0) delay(espera)
        try {
            return pedido()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ultimo = e
        }
    }
    throw ultimo ?: IllegalStateException("Sin intentos")
}
