package com.example.data.remote.model

data class PizarraResponse(
    val fecha: String,
    val tipo_cambio_bna_divisas: Double,
    val granos: Map<String, GranoDetalle>
)

data class GranoDetalle(
    val ars_tn: Double?,
    val usd_tn: Double?,
    val variacion_pct_vs_rueda_anterior: Double?
)

