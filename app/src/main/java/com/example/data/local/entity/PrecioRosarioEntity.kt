package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "precios_rosario")
data class PrecioRosarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cultivo: String, // "Soja", "Maíz", "Trigo", "Girasol"
    val fecha: String,
    val precioUSD: Double,
    val precioARS: Double,
    val variacionPorcentaje: Double,
    val puerto: String = "Puerto de Rosario (Cámara Arbitral)"
)
