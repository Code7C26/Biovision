package com.example.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GeoJSON del contorno de un lote, el mismo formato que guarda Android (AgroSatRepository.generarGeoJson)
 * y que sincroniza Supabase: Feature con un Polygon de un solo anillo, cerrado y en [lon, lat]
 * (RFC 7946). En el código los vértices van como (lat, lon).
 */
object GeoJsonLote {

    fun generar(vertices: List<Pair<Double, Double>>): String {
        val abierto = GeometriaLote.anilloAbierto(vertices)
        val anillo = if (abierto.isEmpty()) abierto else abierto + abierto.first()
        return buildJsonObject {
            put("type", "Feature")
            put("properties", JsonObject(emptyMap()))
            put("geometry", buildJsonObject {
                put("type", "Polygon")
                put("coordinates", buildJsonArray {
                    add(buildJsonArray { anillo.forEach { (lat, lon) -> add(buildJsonArray { add(JsonPrimitive(lon)); add(JsonPrimitive(lat)) }) } })
                })
            })
        }.toString()
    }

    /**
     * Vértices (lat, lon) del anillo exterior, sin repetir el primero; null si el texto no es un
     * polígono válido de al menos 3 vértices. Acepta un Feature o una geometría suelta.
     */
    fun leer(geojson: String): List<Pair<Double, Double>>? = runCatching {
        val raiz = Json.parseToJsonElement(geojson) as JsonObject
        val geometria = (raiz["geometry"] as? JsonObject) ?: raiz
        if ((geometria["type"] as? JsonPrimitive)?.content != "Polygon") return null
        val anillo = (geometria["coordinates"] as JsonArray)[0].jsonArray
        val puntos = anillo.map { c ->
            val par = c.jsonArray
            val lon = par[0].jsonPrimitive.doubleOrNull ?: return null
            val lat = par[1].jsonPrimitive.doubleOrNull ?: return null
            lat to lon
        }
        GeometriaLote.anilloAbierto(puntos).takeIf { it.size >= 3 }
    }.getOrNull()
}
