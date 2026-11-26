package com.example.utils

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cálculos sobre polígonos de lotes con vértices (lat, lon) en grados WGS84.
 *
 * Proyecta a un plano tangente en el centro del lote usando los radios de curvatura del
 * elipsoide WGS84 en esa latitud; para lotes de hasta decenas de km el error es menor al 0,01 %.
 */
object GeometriaLote {
    private const val SEMIEJE_MAYOR = 6378137.0
    private const val EXCENTRICIDAD2 = 6.69437999014e-3

    /** Quita el último vértice si repite el primero (anillo cerrado de GeoJSON). */
    fun anilloAbierto(puntos: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
        if (puntos.size > 1 && puntos.first() == puntos.last()) puntos.dropLast(1) else puntos

    private fun proyectar(puntos: List<Pair<Double, Double>>): List<DoubleArray> {
        val lat0 = puntos.sumOf { it.first } / puntos.size
        val lon0 = puntos.sumOf { it.second } / puntos.size
        val s = sin(radianes(lat0))
        val w = 1 - EXCENTRICIDAD2 * s * s
        val radioPrimerVertical = SEMIEJE_MAYOR / sqrt(w)
        val radioMeridiano = SEMIEJE_MAYOR * (1 - EXCENTRICIDAD2) / (w * sqrt(w))
        val metrosPorGradoLon = radianes(1.0) * radioPrimerVertical * cos(radianes(lat0))
        val metrosPorGradoLat = radianes(1.0) * radioMeridiano
        return puntos.map { doubleArrayOf((it.second - lon0) * metrosPorGradoLon, (it.first - lat0) * metrosPorGradoLat) }
    }

    fun areaHectareas(puntos: List<Pair<Double, Double>>): Double {
        val p = anilloAbierto(puntos)
        if (p.size < 3) return 0.0
        val xy = proyectar(p)
        var doble = 0.0
        for (i in xy.indices) {
            val a = xy[i]
            val b = xy[(i + 1) % xy.size]
            doble += a[0] * b[1] - b[0] * a[1]
        }
        return abs(doble) / 2.0 / 10_000.0
    }

    /**
     * Rumbo (0 = norte-sur, 90 = este-oeste; de 0 a 180) del lado más largo del lote: en la mayoría
     * de los lotes es el sentido de siembra, para hacer pasadas largas y pocas cabeceras.
     */
    fun rumboLadoMasLargo(puntos: List<Pair<Double, Double>>): Double {
        val p = anilloAbierto(puntos)
        if (p.size < 2) return 0.0
        val xy = proyectar(p)
        val (a, b) = xy.indices.map { xy[it] to xy[(it + 1) % xy.size] }
            .maxBy { (a, b) -> hypot(b[0] - a[0], b[1] - a[1]) }
        // Ángulo desde el norte en sentido horario, llevado a [0, 180)
        val grados = grados(kotlin.math.atan2(b[0] - a[0], b[1] - a[1]))
        return ((grados % 180.0) + 180.0) % 180.0
    }

    /** Perímetro cerrado (incluye el lado que une el último vértice con el primero) en metros. */
    fun perimetroMetros(puntos: List<Pair<Double, Double>>): Double {
        val p = anilloAbierto(puntos)
        if (p.size < 2) return 0.0
        val xy = proyectar(p)
        val lados = if (p.size == 2) 1 else xy.size
        return (0 until lados).sumOf { i ->
            val a = xy[i]
            val b = xy[(i + 1) % xy.size]
            hypot(b[0] - a[0], b[1] - a[1])
        }
    }

    /** true si dos lados no consecutivos se cruzan (polígono "moño"): su superficie no sería válida. */
    fun tieneAutointerseccion(puntos: List<Pair<Double, Double>>): Boolean {
        val p = anilloAbierto(puntos)
        val n = p.size
        if (n < 4) return false
        val xy = proyectar(p)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val consecutivos = j == i + 1 || (i == 0 && j == n - 1)
                if (!consecutivos && segmentosSeCruzan(xy[i], xy[(i + 1) % n], xy[j], xy[(j + 1) % n])) return true
            }
        }
        return false
    }

    /** Si el punto (lat, lon) cae dentro del polígono [poligono] de (lat, lon), por ray casting. */
    fun dentroDePoligono(lat: Double, lon: Double, poligono: List<Pair<Double, Double>>): Boolean {
        var dentro = false
        var j = poligono.lastIndex
        for (i in poligono.indices) {
            val (latI, lonI) = poligono[i]
            val (latJ, lonJ) = poligono[j]
            if ((latI > lat) != (latJ > lat) && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI) dentro = !dentro
            j = i
        }
        return dentro
    }

    /**
     * Centroide (lat, lon) del polígono: centro de masa del área en el plano proyectado. Para menos
     * de 3 vértices o área nula (puntos alineados) devuelve el promedio de los vértices.
     */
    fun centroide(puntos: List<Pair<Double, Double>>): Pair<Double, Double>? {
        val p = anilloAbierto(puntos)
        if (p.isEmpty()) return null
        val lat0 = p.sumOf { it.first } / p.size
        val lon0 = p.sumOf { it.second } / p.size
        if (p.size < 3) return lat0 to lon0
        val xy = proyectar(p)
        var doble = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in xy.indices) {
            val a = xy[i]
            val b = xy[(i + 1) % xy.size]
            val cruz = a[0] * b[1] - b[0] * a[1]
            doble += cruz
            cx += (a[0] + b[0]) * cruz
            cy += (a[1] + b[1]) * cruz
        }
        if (abs(doble) < 1e-9) return lat0 to lon0
        // De metros (origen en el promedio de vértices) de vuelta a grados, con las mismas escalas
        val s = sin(radianes(lat0))
        val w = 1 - EXCENTRICIDAD2 * s * s
        val metrosPorGradoLon = radianes(1.0) * SEMIEJE_MAYOR / sqrt(w) * cos(radianes(lat0))
        val metrosPorGradoLat = radianes(1.0) * SEMIEJE_MAYOR * (1 - EXCENTRICIDAD2) / (w * sqrt(w))
        val x = cx / (3 * doble)
        val y = cy / (3 * doble)
        return (lat0 + y / metrosPorGradoLat) to (lon0 + x / metrosPorGradoLon)
    }

    private fun orientacion(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double =
        (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])

    private fun segmentosSeCruzan(p1: DoubleArray, p2: DoubleArray, q1: DoubleArray, q2: DoubleArray): Boolean {
        val d1 = orientacion(q1, q2, p1)
        val d2 = orientacion(q1, q2, p2)
        val d3 = orientacion(p1, p2, q1)
        val d4 = orientacion(p1, p2, q2)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
