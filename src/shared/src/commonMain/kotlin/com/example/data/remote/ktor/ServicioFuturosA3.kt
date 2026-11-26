package com.example.data.remote.ktor

import com.example.utils.FechasIso
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/*
 * Futuros de granos de A3 Mercados (ex MATba ROFEX) desde su Centro de Estadísticas de Mercado (CEM):
 * API pública y documentada (https://apicem.matbarofex.com.ar/swagger), sin usuario ni clave. Da los
 * precios de AJUSTE de cada rueda, no el tiempo real (eso requiere una licencia de datos paga).
 *
 * No responde con CORS: Android la llama directo y la web, por el proxy de GRANEX ([base]).
 */

/** Un cierre diario de un contrato, tal como lo devuelve /api/v2/closing-prices. */
@Serializable
data class CierreA3(
    val dateTime: String,
    val symbol: String,
    val settlement: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val volume: Double? = null,
    val openInterest: Double? = null,
    val product: String? = null
)

@Serializable
private data class RespuestaCierresA3(val data: List<CierreA3> = emptyList())

/**
 * [base] es el CEM (Android) o la función "publico" de GRANEX (web), que exige la clave pública de
 * Supabase en [cabeceras].
 */
class ServicioFuturosA3(
    private val http: HttpClient,
    private val base: String = BASE_CEM,
    private val cabeceras: Map<String, String> = emptyMap()
) {
    /**
     * Cierres de soja, maíz y trigo (contratos en dólares) de los últimos [dias] días hasta [hoy]
     * (yyyy-MM-dd, fecha de Argentina). Diez días cubren fines de semana largos y feriados.
     */
    suspend fun cierres(hoy: String, dias: Int = 10): List<CierreA3> =
        PRODUCTOS.flatMap { producto ->
            http.get("$base/api/v2/closing-prices") {
                parameter("product", producto)
                parameter("type", "FUT")
                parameter("from", FechasIso.sumarDias(hoy, -dias))
                parameter("to", FechasIso.sumarDias(hoy, 1))
                parameter("page", 1)
                parameter("pageSize", 300)
                parameter("sortDir", "DESC")
                parameter("market", "ROFX")
                cabeceras.forEach { (nombre, valor) -> header(nombre, valor) }
            }.body<RespuestaCierresA3>().data
        }

    companion object {
        const val BASE_CEM = "https://apicem.matbarofex.com.ar"
        const val FUENTE = "A3 Mercados (CEM)"
        val PRODUCTOS = listOf("SOJ Dolar MATba", "MAI Dolar MATba", "TRI Dolar MATba")
    }
}
