package com.example.data.repository

import com.example.utils.radianes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Tamaño de la localidad, usado para elegir el zoom del mapa. */
enum class TipoLocalidad {
    /** Capital del país o más de 500 mil habitantes (CABA, Córdoba, Rosario): se muestra su región. */
    CIUDAD_GRANDE,
    /** Capital provincial o más de 50 mil habitantes. */
    CIUDAD,
    /** Cabecera de departamento/partido o más de 10 mil habitantes. */
    PUEBLO,
    /** Pueblo chico, comuna, paraje o colonia. */
    LOCALIDAD;

    /**
     * Zoom de mapa (escala Leaflet/Windy) para que se lea el nombre y se vea el área urbana:
     * más cerca cuanto más chica es la localidad. [maximo] es el tope del mapa (el embed de Windy llega a 11).
     */
    fun zoom(maximo: Int = Int.MAX_VALUE): Int = minOf(
        maximo,
        when (this) {
            CIUDAD_GRANDE -> 9
            CIUDAD -> 10
            PUEBLO -> 12
            LOCALIDAD -> 13
        }
    )

    companion object {
        /** Según el código GeoNames (PPLC capital del país, PPLA provincial, PPLA2 departamental) y la población. */
        fun de(codigo: String?, poblacion: Int?): TipoLocalidad {
            val p = poblacion ?: 0
            return when {
                codigo == "PPLC" || p >= 500_000 -> CIUDAD_GRANDE
                codigo == "PPLA" || p >= 50_000 -> CIUDAD
                codigo == "PPLA2" || p >= 10_000 -> PUEBLO
                else -> LOCALIDAD
            }
        }
    }
}

/** Localidad sugerida por un buscador. [distanciaKm] es desde el punto de sesgo, si hubo. */
data class SugerenciaLocalidad(
    val nombre: String,
    val detalle: String,
    val lat: Double,
    val lon: Double,
    val distanciaKm: Double?,
    val argentina: Boolean,
    val tipo: TipoLocalidad = TipoLocalidad.CIUDAD,
    val poblacion: Int? = null
)

/**
 * Lectura de la API de geocodificación de Open-Meteo (GeoNames), común a Android y la web.
 * La API no tiene sesgo de cercanía, así que el orden por proximidad se calcula acá.
 */
object LectorLocalidades {
    const val MAX_SUGERENCIAS = 6
    const val CANDIDATOS = 30

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lee la respuesta de Open-Meteo: solo lugares poblados (PPL*), sin duplicados. Primero los nombres
     * que empiezan con lo escrito (la API también trae coincidencias por nombres alternativos) y,
     * dentro de cada grupo, las cercanas a [cerca].
     */
    fun parsear(respuesta: String, texto: String, cerca: Pair<Double, Double>?): List<SugerenciaLocalidad> {
        val resultados = (json.parseToJsonElement(respuesta) as? JsonObject)?.get("results") as? JsonArray
            ?: return emptyList()
        val lista = resultados.mapNotNull { elemento ->
            val r = elemento as? JsonObject ?: return@mapNotNull null
            val codigo = r.texto("feature_code").takeIf { it.isNotEmpty() }
            if (codigo != null && !codigo.startsWith("PPL")) return@mapNotNull null
            val nombre = r.texto("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = r.numero("latitude") ?: return@mapNotNull null
            val lon = r.numero("longitude") ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            val poblacion = (r["population"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            val provincia = sinPrefijoProvincia(r.texto("admin1"))
            val detalle = listOf(r.texto("admin2"), provincia)
                .filter { it.isNotBlank() && it != nombre }
                .distinct()
                .joinToString(", ")
            SugerenciaLocalidad(
                nombre = nombre,
                detalle = detalle,
                lat = lat,
                lon = lon,
                distanciaKm = cerca?.let { distanciaKm(it, lat to lon) },
                argentina = r.texto("country_code").equals("AR", ignoreCase = true),
                tipo = TipoLocalidad.de(codigo, poblacion),
                poblacion = poblacion
            )
        }.distinctBy { it.nombre.lowercase() to it.detalle.lowercase() }
        val buscado = normalizar(texto)
        // sortedWith es estable: sin punto de referencia queda el orden de la API (relevancia y población)
        val porNombre = compareBy<SugerenciaLocalidad> { coincidencia(normalizar(it.nombre), buscado) }
        val orden = if (cerca == null) porNombre else porNombre.thenBy(::puntajeCercania)
        return lista.sortedWith(orden).take(MAX_SUGERENCIAS)
    }

    private fun JsonObject.texto(clave: String): String = (this[clave] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.numero(clave: String): Double? = (this[clave] as? JsonPrimitive)?.doubleOrNull

    /**
     * Menor = primero. La distancia se divide por un peso que crece con la población, para que una
     * ciudad grande algo más lejana no quede debajo de un paraje homónimo cercano.
     */
    private fun puntajeCercania(s: SugerenciaLocalidad): Double {
        val peso = 1.0 + log10(1.0 + (s.poblacion ?: 0) / 1000.0)
        return (s.distanciaKm ?: Double.MAX_VALUE) / peso
    }

    /** 0 = el nombre empieza con lo escrito, 1 = alguna palabra empieza con eso, 2 = nombre alternativo. */
    private fun coincidencia(nombre: String, buscado: String): Int = when {
        nombre.startsWith(buscado) -> 0
        nombre.split(' ', '-').any { it.startsWith(buscado) } -> 1
        else -> 2
    }

    /** Minúsculas y sin tildes ni diéresis (lo que hacía Normalizer NFD en la JVM, para nombres en español). */
    fun normalizar(texto: String): String =
        texto.lowercase().map { SIN_TILDE[it] ?: it }.joinToString("").trim()

    private val SIN_TILDE: Map<Char, Char> = buildMap {
        "áàäâã".forEach { put(it, 'a') }
        "éèëê".forEach { put(it, 'e') }
        "íìïî".forEach { put(it, 'i') }
        "óòöôõ".forEach { put(it, 'o') }
        "úùüû".forEach { put(it, 'u') }
        put('ñ', 'n')
        put('ç', 'c')
    }

    private fun sinPrefijoProvincia(admin1: String): String =
        admin1.removePrefix("Provincia de ").removePrefix("Provincia del ").removePrefix("Provincia ").trim()

    /** Distancia de círculo máximo (haversine), suficiente para ordenar y mostrar "a 12 km". */
    fun distanciaKm(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = radianes(b.first - a.first)
        val dLon = radianes(b.second - a.second)
        val h = sin(dLat / 2).pow(2) + cos(radianes(a.first)) * cos(radianes(b.first)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
