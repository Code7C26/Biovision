package com.example.utils.vra

import com.example.utils.radianes
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Grilla de máquina: cada columna es una pasada de [anchoM] metros (el ancho de labor) y cada celda
 * mide [largoM] a lo largo de la pasada. Está rotada al [rumboGrados] de siembra (0 = norte-sur,
 * 90 = este-oeste), así las zonas siguen las pasadas y ningún parche es más angosto que la máquina.
 *
 * Se calcula en metros, en una proyección local del lote. Los ejes son los de la grilla geográfica
 * (x al este, y al sur) girados en sentido horario, para conservar la orientación de los contornos.
 */
class MarcoMaquina private constructor(
    private val lat0: Double,
    private val lon0: Double,
    private val metrosLat: Double,
    private val metrosLon: Double,
    val rumboGrados: Double,
    val anchoM: Double,
    val largoM: Double,
    private val u0: Double,
    private val v0: Double,
    override val columnas: Int,
    override val filas: Int
) : MarcoCeldas {

    private val coseno = cos(radianes(rumboGrados))
    private val seno = sin(radianes(rumboGrados))

    // Eje x (de costado a la pasada) y eje y (a lo largo de la pasada), en (este, norte)
    private val exE = coseno
    private val exN = -seno
    private val eyE = -seno
    private val eyN = -coseno

    val areaCeldaHa: Double get() = anchoM * largoM / 10_000.0

    private fun uv(lat: Double, lon: Double): Pair<Double, Double> {
        val e = (lon - lon0) * metrosLon
        val n = (lat - lat0) * metrosLat
        return (e * exE + n * exN) to (e * eyE + n * eyN)
    }

    private fun geo(u: Double, v: Double): Pair<Double, Double> {
        val e = u * exE + v * eyE
        val n = u * exN + v * eyN
        return (lat0 + n / metrosLat) to (lon0 + e / metrosLon)
    }

    override fun vertice(x: Int, y: Int): Pair<Double, Double> = geo(u0 + x * anchoM, v0 + y * largoM)

    override fun centro(indice: Int): Pair<Double, Double> {
        val fila = indice / columnas
        val columna = indice % columnas
        return geo(u0 + (columna + 0.5) * anchoM, v0 + (fila + 0.5) * largoM)
    }

    override fun celdaDe(lat: Double, lon: Double): Int? {
        val (u, v) = uv(lat, lon)
        val columna = floor((u - u0) / anchoM).toInt()
        val fila = floor((v - v0) / largoM).toInt()
        return if (columna in 0 until columnas && fila in 0 until filas) fila * columnas + columna else null
    }

    companion object {
        /** La grilla que cubre el [anillo] (lat, lon) del lote. */
        fun para(anillo: List<Pair<Double, Double>>, anchoM: Double, largoM: Double, rumboGrados: Double): MarcoMaquina {
            require(anchoM > 0 && largoM > 0) { "El ancho de labor y el largo de celda tienen que ser positivos" }
            val norte = anillo.maxOf { it.first }
            val sur = anillo.minOf { it.first }
            val lat0 = (norte + sur) / 2
            val lon0 = (anillo.maxOf { it.second } + anillo.minOf { it.second }) / 2
            val lat = radianes(lat0)
            val metrosLat = 111132.92 - 559.82 * cos(2 * lat) + 1.175 * cos(4 * lat)
            val metrosLon = 111412.84 * cos(lat) - 93.5 * cos(3 * lat)
            // Marco sin desplazar para proyectar los vértices y encontrar la esquina de la grilla
            val base = MarcoMaquina(lat0, lon0, metrosLat, metrosLon, rumboGrados, anchoM, largoM, 0.0, 0.0, 1, 1)
            val uvs = anillo.map { (la, lo) -> base.uv(la, lo) }
            val u0 = uvs.minOf { it.first }
            val v0 = uvs.minOf { it.second }
            val columnas = max(1, ceil((uvs.maxOf { it.first } - u0) / anchoM - 1e-9).toInt())
            val filas = max(1, ceil((uvs.maxOf { it.second } - v0) / largoM - 1e-9).toInt())
            return MarcoMaquina(lat0, lon0, metrosLat, metrosLon, rumboGrados, anchoM, largoM, u0, v0, columnas, filas)
        }
    }
}
