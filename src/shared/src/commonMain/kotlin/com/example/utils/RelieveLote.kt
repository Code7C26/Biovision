package com.example.utils

import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import kotlin.math.cos
import kotlin.math.sqrt

/** Altura del terreno del lote (Copernicus DEM GLO-30 remuestreado a 10 m), en metros; NaN = sin dato. */
class RelieveLote(val grilla: GrillaGeografica, val altura: FloatArray) {

    /**
     * Para zonificar por relieve con el mismo Zonificador: la altura sobre el punto más bajo del lote
     * (en metros) va en los tres índices.
     */
    fun comoImagen(lote: List<Pair<Double, Double>>): ImagenIndices {
        val adentro = AnalisisRelieve.adentro(grilla, lote)
        val minimo = altura.indices.filter { adentro[it] && !altura[it].isNaN() }.minOfOrNull { altura[it] } ?: 0f
        val relativa = FloatArray(altura.size) { if (altura[it].isNaN()) Float.NaN else altura[it] - minimo }
        return ImagenIndices(grilla, "Relieve", relativa, relativa, relativa)
    }
}

enum class PosicionRelieve(val nombre: String) { BAJO("Bajo"), MEDIA_LOMA("Media loma"), LOMA("Loma") }

data class ClaseRelieve(val posicion: PosicionRelieve, val hectareas: Double, val porcentaje: Double)

data class ResumenRelieve(
    val minimo: Double,
    val maximo: Double,
    val mediana: Double,
    /** Alturas del 2 % más bajo y del 2 % más alto (sin el ruido de las puntas). */
    val p2: Double,
    val p98: Double,
    /** Diferencia entre el punto más alto y el más bajo del lote, sin el 2 % de cada punta (ruido del DEM). */
    val desnivel: Double,
    val pendienteMediaPct: Double,
    /** Diferencia de altura con la mediana a partir de la cual un lugar es bajo o loma. */
    val umbral: Double,
    val clases: List<ClaseRelieve>,
    val plano: Boolean,
    val diagnostico: String
)

/**
 * Relieve del lote para el escaneo y la prescripción. Puro, probado en commonTest.
 *
 * Bajo, media loma y loma son relativos a la altura típica (mediana) del lote: más de [umbral] por
 * debajo es bajo y más de [umbral] por encima es loma. El umbral crece con el desnivel del lote (un
 * quinto del desnivel) y nunca baja de [UMBRAL_MINIMO_M], para no clasificar el ruido del modelo de
 * elevación (unos decímetros) como relieve.
 */
object AnalisisRelieve {
    /**
     * Aviso que exige la licencia del GLO-30 (COP-DEM-GLO-30-F) cuando se muestra o se distribuye el dato
     * modificado (remuestreado a 10 m, relativo a la mediana). Va en inglés, tal como lo pide la licencia.
     */
    const val AVISO_DEM = "produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space " +
        "GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved"

    const val UMBRAL_MINIMO_M = 0.25
    /** Con menos desnivel que esto el lote se considera plano. */
    const val DESNIVEL_PLANO_M = 1.0

    fun adentro(grilla: GrillaGeografica, lote: List<Pair<Double, Double>>): BooleanArray {
        val anillo = GeometriaLote.anilloAbierto(lote)
        return BooleanArray(grilla.total) { i ->
            anillo.size < 3 || grilla.centro(i).let { (lat, lon) -> GeometriaLote.dentroDePoligono(lat, lon, anillo) }
        }
    }

    fun posicion(altura: Double, mediana: Double, umbral: Double): PosicionRelieve = when {
        altura < mediana - umbral -> PosicionRelieve.BAJO
        altura > mediana + umbral -> PosicionRelieve.LOMA
        else -> PosicionRelieve.MEDIA_LOMA
    }

    fun resumir(relieve: RelieveLote, lote: List<Pair<Double, Double>>): ResumenRelieve? {
        val g = relieve.grilla
        val dentro = adentro(g, lote)
        val indices = relieve.altura.indices.filter { dentro[it] && !relieve.altura[it].isNaN() }
        if (indices.isEmpty()) return null
        val ordenadas = indices.map { relieve.altura[it].toDouble() }.sorted()
        fun p(q: Double) = ordenadas[(q * (ordenadas.size - 1)).toInt()]
        val mediana = p(0.5)
        val p2 = p(0.02)
        val p98 = p(0.98)
        val desnivel = p98 - p2
        val umbral = maxOf(UMBRAL_MINIMO_M, desnivel / 5)

        val latMedia = (g.norte + g.sur) / 2
        val metrosX = g.pasoLon * 111_412.84 * cos(radianes(latMedia))
        val metrosY = g.pasoLat * 110_950.0
        val haPixel = metrosX * metrosY / 10_000.0

        // Pendiente por diferencias centrales (solo donde los vecinos tienen dato)
        val pendientes = indices.mapNotNull { i ->
            val f = i / g.columnas
            val c = i % g.columnas
            if (f == 0 || c == 0 || f == g.filas - 1 || c == g.columnas - 1) return@mapNotNull null
            val e = relieve.altura[i + 1]
            val o = relieve.altura[i - 1]
            val n = relieve.altura[i - g.columnas]
            val s = relieve.altura[i + g.columnas]
            if (e.isNaN() || o.isNaN() || n.isNaN() || s.isNaN()) return@mapNotNull null
            val dx = (e - o) / (2 * metrosX)
            val dy = (n - s) / (2 * metrosY)
            100 * sqrt(dx * dx + dy * dy)
        }

        val conteo = IntArray(3)
        indices.forEach { conteo[posicion(relieve.altura[it].toDouble(), mediana, umbral).ordinal]++ }
        val clases = PosicionRelieve.entries.map { pos ->
            ClaseRelieve(pos, conteo[pos.ordinal] * haPixel, 100.0 * conteo[pos.ordinal] / indices.size)
        }
        val plano = desnivel < DESNIVEL_PLANO_M
        val pendiente = pendientes.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        return ResumenRelieve(
            minimo = ordenadas.first(),
            maximo = ordenadas.last(),
            mediana = mediana,
            p2 = p2,
            p98 = p98,
            desnivel = desnivel,
            pendienteMediaPct = pendiente,
            umbral = umbral,
            clases = clases,
            plano = plano,
            diagnostico = diagnostico(desnivel, pendiente, clases, plano)
        )
    }

    private fun diagnostico(desnivel: Double, pendiente: Double, clases: List<ClaseRelieve>, plano: Boolean): String {
        val bajo = clases.first { it.posicion == PosicionRelieve.BAJO }
        val loma = clases.first { it.posicion == PosicionRelieve.LOMA }
        if (plano) {
            return "Lote plano: ${formatoDecimal(desnivel, 1)} m de desnivel. El relieve casi no explica diferencias de rendimiento acá; " +
                "las zonas de bajo y loma son de pocos centímetros."
        }
        val erosion = if (pendiente >= 1.0) " Pendiente media de ${formatoDecimal(pendiente, 1)} %: atención a la erosión en las lomas." else ""
        return "Desnivel de ${formatoDecimal(desnivel, 1)} m. Bajos: ${formatoDecimal(bajo.hectareas, 1)} ha " +
            "(${formatoDecimal(bajo.porcentaje, 0)} %), que juntan agua: rinden más en años secos y pueden anegarse en años húmedos. " +
            "Lomas: ${formatoDecimal(loma.hectareas, 1)} ha (${formatoDecimal(loma.porcentaje, 0)} %), con menos agua disponible." + erosion
    }

}
