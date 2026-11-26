package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Lote dibujado por el usuario. Las columnas de sincronización (versión 6) siguen el modelo de
 * `RegistroLote` en :shared: [uuid] es el id del lote en Supabase, [modificadoEn] decide los
 * conflictos, [eliminado] es un borrado lógico que todavía tiene que viajar a la nube y
 * [pendienteSync] marca un cambio local sin subir.
 */
@Entity(tableName = "lotes_satelitales", indices = [Index(value = ["uuid"], unique = true)])
data class LoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val geoJson: String, // Feature GeoJSON con el polígono del lote (anillo cerrado, [lon, lat])
    val superficieHa: Double,
    val fechaCreacion: Long = System.currentTimeMillis(),
    // Los DEFAULT son los mismos que en las migraciones: Room valida que el esquema migrado coincida
    @ColumnInfo(defaultValue = "''") val provincia: String = "",
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val modificadoEn: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val pendienteSync: Boolean = true,
    @ColumnInfo(defaultValue = "0") val eliminado: Boolean = false
)
