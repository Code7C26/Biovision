package com.example.utils.vra

import com.example.utils.GeometriaLote
import com.example.utils.formatoDecimal
import kotlin.math.sqrt

/** Época de la campaña: define qué meses se miran para encontrar el pico de vigor. */
enum class Temporada(val nombre: String, val meses: String) {
    VERANO("Gruesa (verano)", "octubre a abril"),
    INVIERNO("Fina (invierno)", "junio a noviembre")
}

/** Una campaña agrícola: rango de fechas en el que se busca el pico de vigor de cada píxel. */
data class Campana(val etiqueta: String, val desde: String, val hasta: String)

object Campanas {
    /** Sentinel-2 L2A cubre toda Argentina desde fines de 2018. */
    const val PRIMER_ANIO = 2019
    const val POR_DEFECTO = 4

    /**
     * Campañas ya terminadas al [hoy] (yyyy-MM-dd), de la más vieja a la más nueva. La gruesa va de
     * octubre a abril ("2024/25"); la fina, de junio a noviembre del mismo año ("2025").
     */
    fun terminadas(hoy: String, temporada: Temporada): List<Campana> {
        val anio = hoy.take(4).toInt()
        return when (temporada) {
            Temporada.VERANO -> (PRIMER_ANIO until anio).map { a ->
                Campana("$a/${((a + 1) % 100).toString().padStart(2, '0')}", "$a-10-01", "${a + 1}-04-30")
            }.filter { it.hasta < hoy }
            Temporada.INVIERNO -> (PRIMER_ANIO..anio).map { a -> Campana("$a", "$a-06-01", "$a-11-30") }.filter { it.hasta < hoy }
        }
    }

    /** Las últimas [POR_DEFECTO] campañas terminadas. */
    fun porDefecto(hoy: String, temporada: Temporada): List<Campana> = terminadas(hoy, temporada).takeLast(POR_DEFECTO)
}

/**
 * Pico de vigor de cada píxel en una campaña: percentil 90 del NDVI y del NDRE de las pasadas sin
 * nubes (más robusto que el máximo ante nubes que el filtro no detecta).
 */
class CampanaNdvi(
    val campana: Campana,
    val grilla: GrillaGeografica,
    /** NaN = sin dato (menos de 3 pasadas sin nubes o fuera del lote). */
    val ndvi: FloatArray,
    val observaciones: IntArray,
    /** Borde rojo (20 m): se satura menos que el NDVI en cultivos densos. */
    val ndre: FloatArray = FloatArray(ndvi.size) { Float.NaN }
) {
    fun valores(indice: IndiceVegetacion): FloatArray = if (indice == IndiceVegetacion.NDRE) ndre else ndvi
}

/** Cómo quedó cada campaña en el cálculo. */
data class ResumenCampana(
    val campana: Campana,
    val medianaNdvi: Double?,
    val coberturaPct: Double,
    val usada: Boolean,
    /** Por qué no se usó (null si se usó). */
    val motivo: String?
)

/**
 * Potencial productivo del lote: para cada píxel, su pico de vigor relativo a la mediana del lote en
 * cada campaña (1,00 = lo típico; 1,10 = 10 % por encima), promediado entre campañas. Las zonas que
 * salen de acá se repiten año tras año, no dependen de un solo día.
 */
class PotencialProductivo(
    /** Índice con el que se calculó el pico: NDVI o NDRE. */
    val indiceVegetacion: IndiceVegetacion,
    val grilla: GrillaGeografica,
    /** Índice relativo promedio; NaN = sin dato. */
    val indice: FloatArray,
    /** Desvío del índice relativo entre campañas: alto = zona que cambia de un año a otro. */
    val variacionEntreCampanas: FloatArray,
    val campanas: List<ResumenCampana>,
    /** Mapas de rinde que entraron en el promedio (sus etiquetas, p. ej. "Maíz 2024/25"). */
    val rindes: List<String> = emptyList()
) {
    val usadas: List<ResumenCampana> get() = campanas.filter { it.usada }

    /** Texto para la prescripción y las exportaciones, p. ej. "NDVI pico de 4 campañas (2022/23 a 2025/26)". */
    val descripcion: String
        get() {
            val u = usadas.map { it.campana.etiqueta }
            val n = indiceVegetacion.nombre
            val r = when (rindes.size) {
                0 -> null
                1 -> "mapa de rinde ${rindes[0]}"
                else -> "${rindes.size} mapas de rinde (${rindes.joinToString(", ")})"
            }
            val satelite = when (u.size) {
                0 -> null
                1 -> "$n pico de la campaña ${u[0]}"
                else -> "$n pico de ${u.size} campañas (${u.first()} a ${u.last()})"
            }
            return when {
                satelite == null -> "Rinde relativo: ${r ?: "sin mapas"}"
                r == null -> satelite
                else -> "$satelite + $r"
            }
        }

    /** Para zonificar con el mismo Zonificador que una imagen: los tres índices llevan el potencial. */
    fun comoImagen(): ImagenIndices = ImagenIndices(grilla, descripcion, indice, indice, indice)

    /** Porcentaje del lote cuyo índice varía más de 0,10 entre campañas (zonas poco confiables). */
    fun inestablePct(): Double {
        val v = variacionEntreCampanas.filter { !it.isNaN() }
        return if (v.isEmpty()) 0.0 else 100.0 * v.count { it > 0.10f } / v.size
    }
}

class ErrorPotencial(mensaje: String) : Exception(mensaje)

/** Un mapa de rinde ya pasado a la grilla de las campañas, relativo a su mediana (1,00 = lo típico). */
class CapaRinde(val etiqueta: String, val relativo: FloatArray)

object CalculoPotencial {
    /** Una campaña con mediana de NDVI pico menor a esto no tuvo cultivo (o falló): no aporta. */
    const val NDVI_MINIMO_CAMPANA = 0.45
    /** Parte del lote que tiene que tener dato para usar la campaña. */
    const val COBERTURA_MINIMA = 0.7
    /** Con un NDVI pico típico mayor a esto el NDVI está saturado: conviene el NDRE. */
    const val NDVI_SATURADO = 0.85

    /**
     * NDRE si el NDVI pico típico de las campañas con cultivo está saturado (cultivos densos, donde un
     * lote muy bueno y uno bueno dan casi el mismo NDVI); si no, NDVI (10 m, más detalle).
     */
    fun indiceSugerido(campanas: List<CampanaNdvi>, lote: List<Pair<Double, Double>>): IndiceVegetacion {
        val anillo = GeometriaLote.anilloAbierto(lote)
        val medianas = campanas.mapNotNull { c ->
            val v = c.ndvi.indices
                .filter { i -> !c.ndvi[i].isNaN() && (anillo.size < 3 || c.grilla.centro(i).let { (lat, lon) -> GeometriaLote.dentroDePoligono(lat, lon, anillo) }) }
                .map { c.ndvi[it].toDouble() }.sorted()
            v.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
        }.filter { it >= NDVI_MINIMO_CAMPANA }
        val tieneNdre = campanas.any { c -> c.ndre.any { !it.isNaN() } }
        return if (tieneNdre && medianas.isNotEmpty() && medianas.average() >= NDVI_SATURADO) IndiceVegetacion.NDRE else IndiceVegetacion.NDVI
    }

    /**
     * [rindes] son mapas de rinde en la misma grilla, relativos a su mediana: cuentan cada uno como una
     * campaña más (o son la única base, sin campañas, con [grilla] = la del lote).
     */
    fun combinar(
        campanas: List<CampanaNdvi>,
        lote: List<Pair<Double, Double>>,
        indice: IndiceVegetacion = IndiceVegetacion.NDVI,
        rindes: List<CapaRinde> = emptyList(),
        grilla: GrillaGeografica? = campanas.firstOrNull()?.grilla
    ): PotencialProductivo {
        if (campanas.isEmpty() && rindes.isEmpty()) throw ErrorPotencial("Elegí al menos una campaña.")
        if (grilla == null) throw ErrorPotencial("Falta la grilla del lote.")
        val anillo = GeometriaLote.anilloAbierto(lote)
        val adentro = BooleanArray(grilla.total) { i ->
            anillo.size < 3 || grilla.centro(i).let { (lat, lon) -> GeometriaLote.dentroDePoligono(lat, lon, anillo) }
        }
        val pixelesLote = adentro.count { it }.coerceAtLeast(1)

        val resumenes = mutableListOf<ResumenCampana>()
        val relativos = mutableListOf<FloatArray>()
        for (c in campanas) {
            if (c.grilla.columnas != grilla.columnas || c.grilla.filas != grilla.filas) {
                resumenes += ResumenCampana(c.campana, null, 0.0, false, "La imagen no coincide con el contorno del lote.")
                continue
            }
            val validos = c.ndvi.indices.filter { adentro[it] && !c.ndvi[it].isNaN() }.map { c.ndvi[it].toDouble() }.sorted()
            val cobertura = validos.size.toDouble() / pixelesLote
            val mediana = validos.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
            val motivo = when {
                mediana == null || cobertura < COBERTURA_MINIMA ->
                    "Pocas pasadas sin nubes (${formatoDecimal(cobertura * 100, 0)} % del lote con dato)."
                mediana < NDVI_MINIMO_CAMPANA ->
                    "Sin cultivo o con un cultivo que falló (NDVI pico ${formatoDecimal(mediana, 2)})."
                else -> null
            }
            // Si hubo cultivo (eso se mira siempre con el NDVI), se usa el índice elegido
            val v = c.valores(indice)
            val medianaIndice = if (motivo != null) null
                else v.indices.filter { adentro[it] && !v[it].isNaN() }.map { v[it].toDouble() }.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
            val usable = motivo == null && medianaIndice != null && medianaIndice > 0.0
            resumenes += ResumenCampana(
                c.campana, mediana, cobertura * 100, usable,
                motivo ?: if (!usable) "Sin dato de ${indice.nombre} en esta campaña." else null
            )
            if (usable) {
                val m = medianaIndice!!.toFloat()
                relativos += FloatArray(grilla.total) { i -> if (adentro[i] && !v[i].isNaN()) v[i] / m else Float.NaN }
            }
        }
        val rindesUsados = rindes.filter { r -> r.relativo.size == grilla.total && r.relativo.any { !it.isNaN() } }
        rindesUsados.forEach { relativos += it.relativo }
        if (relativos.isEmpty()) {
            throw ErrorPotencial(
                if (campanas.isEmpty()) "Los mapas de rinde no tienen puntos dentro del lote."
                else "Ninguna campaña tuvo cultivo con suficientes imágenes sin nubes. Probá con otras campañas o con la otra temporada."
            )
        }

        val indiceRelativo = FloatArray(grilla.total)
        val variacion = FloatArray(grilla.total)
        for (i in 0 until grilla.total) {
            val v = relativos.map { it[i] }.filter { !it.isNaN() }
            if (v.isEmpty()) {
                indiceRelativo[i] = Float.NaN
                variacion[i] = Float.NaN
                continue
            }
            val media = v.average()
            indiceRelativo[i] = media.toFloat()
            variacion[i] = if (v.size < 2) Float.NaN else sqrt(v.sumOf { (it - media) * (it - media) } / (v.size - 1)).toFloat()
        }
        return PotencialProductivo(indice, grilla, indiceRelativo, variacion, resumenes, rindesUsados.map { it.etiqueta })
    }
}
