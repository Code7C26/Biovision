package com.example.data.remote.model

data class CotizacionHistorica(
    val fecha: String, // format "yyyy-MM-dd"
    val precioUsd: Double,
    val precioArs: Double,
    val variacionPorcentaje: Double = 0.0,
    val puerto: String = "Puerto de Rosario (Cámara Arbitral)"
)
