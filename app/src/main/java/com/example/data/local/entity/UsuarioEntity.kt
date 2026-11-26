package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey val id: Int = 1,
    val nombreProductor: String,
    val establecimiento: String,
    val ubicacionPrincipal: String,
    val hectareasTotales: Int
)
