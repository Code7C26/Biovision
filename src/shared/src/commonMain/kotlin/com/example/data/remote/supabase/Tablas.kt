package com.example.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Filas de las tablas de Supabase (esquema en supabase/migrations). Los nombres de columna van en
 * snake_case; los de Kotlin, como el resto de la app.
 */
object TablasSupabase {
    const val LOTES = "lotes"
    const val PERFILES = "perfiles"
    /** Bucket privado de Storage: un directorio por usuario (`{usuario_id}/…`). */
    const val BUCKET_LOTES = "lotes"
}

/**
 * Lote de un usuario. `geojson` es el Feature (anillo cerrado, [lon, lat], WGS84) tal como lo guarda
 * Room. La columna PostGIS `geom` la calcula un trigger a partir de este GeoJSON, así el cliente no
 * maneja WKB y las consultas espaciales (ST_Intersects, ST_Area) ya quedan disponibles en el servidor.
 */
@Serializable
data class LoteRemoto(
    /** UUID generado en el dispositivo: el mismo lote tiene el mismo id offline y en la nube. */
    val id: String,
    @SerialName("usuario_id") val usuarioId: String? = null,
    val nombre: String,
    val provincia: String = "",
    @SerialName("superficie_ha") val superficieHa: Double,
    val geojson: JsonElement,
    /** Reloj del dispositivo (ms) en la última edición: decide qué versión gana en un conflicto. */
    @SerialName("modificado_en") val modificadoEn: Long,
    @SerialName("creado_en_ms") val creadoEnMs: Long? = null,
    val eliminado: Boolean = false,
    /** Reloj del servidor (timestamptz): cursor de la sincronización. Lo completa la base. */
    @SerialName("actualizado_en") val actualizadoEn: String? = null
)

/** Datos del productor (uno por cuenta, `id` = auth.users.id). */
@Serializable
data class PerfilRemoto(
    val id: String,
    @SerialName("nombre_productor") val nombreProductor: String? = null,
    val establecimiento: String? = null,
    @SerialName("ubicacion_principal") val ubicacionPrincipal: String? = null,
    @SerialName("hectareas_totales") val hectareasTotales: Double? = null
)
