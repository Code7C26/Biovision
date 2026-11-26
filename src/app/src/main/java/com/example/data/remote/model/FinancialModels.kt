package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DolarRateDto(
    @Json(name = "moneda") val moneda: String = "USD",
    @Json(name = "casa") val casa: String, // "oficial", "blue", "mep", "ccl", "tarjeta"
    @Json(name = "nombre") val nombre: String,
    @Json(name = "compra") val compra: Double,
    @Json(name = "venta") val venta: Double,
    @Json(name = "fechaActualizacion") val fechaActualizacion: String = "",
    @Json(name = "variacion") val variacion: Double = 0.0
)

data class FuturoRofexContract(
    val simbolo: String, // "SOJ.ROS/MAY27"
    val cultivo: String, // "Soja", "Maíz", "Trigo"
    val posVencimiento: String, // "Mayo 2027"
    val precioAjusteUSD: Double, // Precio de ajuste de la rueda (A3 Mercados)
    val variacionPorcentaje: Double?, // Contra el ajuste anterior
    val volumenLotes: Int,
    val fechaAjuste: String? = null, // Rueda del ajuste, yyyy-MM-dd
    val interesAbierto: Int = 0,
    val moneda: String = "USD/Ton"
)

sealed interface EstadoRofex {
    data class Ok(val contratos: List<FuturoRofexContract>, val actualizado: Long) : EstadoRofex
    data class Error(val mensaje: String) : EstadoRofex
}

data class FinancialDashboardData(
    val divisas: List<DolarRateDto>,
    val rofex: EstadoRofex,
    val timestampCache: Long = System.currentTimeMillis(),
    val isFromCache: Boolean = false
)
