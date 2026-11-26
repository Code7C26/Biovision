package com.example.utils.vra

import com.example.utils.GeometriaLote
import com.example.utils.radianes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos

/** Un punto del monitor de rinde, ya pasado a t/ha. */
data class PuntoRinde(val lat: Double, val lon: Double, val rindeTHa: Double)

/** Unidad del rinde en el archivo del monitor. */
enum class UnidadRinde(val nombre: String, val aTHa: Double) {
    T_HA("t/ha", 1.0),
    QQ_HA("qq/ha", 0.1),
    KG_HA("kg/ha", 0.001);

    companion object {
        /** Por la mediana: maíz rinde 3 a 18 t/ha, soja 30 a 50 qq/ha, en kg/ha son miles. */
        fun sugerida(mediana: Double): UnidadRinde = when {
            mediana > 250 -> KG_HA
            mediana > 25 -> QQ_HA
            else -> T_HA
        }
    }
}

/** Rinde medido dentro de una zona de manejo. */
data class RindeZona(val zona: Int, val rindeTHa: Double, val puntos: Int)

/** Resultado de limpiar un mapa: los puntos que quedan y por qué se descartó el resto. */
class LimpiezaRinde(
    val puntos: List<PuntoRinde>,
    val fueraDelLote: Int,
    val sinRinde: Int,
    val atipicos: Int
) {
    val descartados: Int get() = fueraDelLote + sinRinde + atipicos
}

/**
 * Mapas de rinde del monitor de la cosechadora: limpieza, paso a la grilla de 10 m del lote y rinde
 * por zona. Puro, probado en MapaRindeTest.
 */
object MapaRinde {

    /** Mismas constantes que la Edge Function `sentinel`: la grilla coincide con la de las imágenes. */
    private const val METROS_LAT = 110_950.0
    private const val METROS_LON_ECUADOR = 111_412.84

    /** Vueltas de relleno de huecos entre pasadas (cada una avanza una celda de 10 m). */
    private const val VUELTAS_RELLENO = 3

    /** Fuera de mediana ± 3,5 desvíos robustos: errores del monitor (arranques, cabeceras, tolva). */
    private const val DESVIOS_ATIPICO = 3.5

    /** Pasa la columna elegida a puntos en t/ha. */
    fun puntos(tabla: TablaCampo, columna: String, unidad: UnidadRinde): List<PuntoRinde> {
        val v = tabla.columnas[columna] ?: return emptyList()
        return (0 until tabla.filas).map { PuntoRinde(tabla.lat[it], tabla.lon[it], v[it] * unidad.aTHa) }
    }

    /**
     * Descarta los puntos fuera del lote, los que no tienen rinde (cero, negativos, vacíos) y los
     * atípicos: más allá de 3,5 desvíos robustos (MAD) de la mediana, típicos del llenado del cabezal,
     * las cabeceras y las paradas.
     */
    fun limpiar(puntos: List<PuntoRinde>, lote: List<Pair<Double, Double>>): LimpiezaRinde {
        val anillo = GeometriaLote.anilloAbierto(lote)
        val adentro = if (anillo.size < 3) puntos else puntos.filter { GeometriaLote.dentroDePoligono(it.lat, it.lon, anillo) }
        val conRinde = adentro.filter { !it.rindeTHa.isNaN() && it.rindeTHa > 0 }
        if (conRinde.isEmpty()) return LimpiezaRinde(emptyList(), puntos.size - adentro.size, adentro.size, 0)
        val mediana = mediana(conRinde.map { it.rindeTHa })
        val mad = mediana(conRinde.map { abs(it.rindeTHa - mediana) }) * 1.4826
        val limpios = if (mad <= 0) conRinde else conRinde.filter { abs(it.rindeTHa - mediana) <= DESVIOS_ATIPICO * mad }
        return LimpiezaRinde(limpios, puntos.size - adentro.size, adentro.size - conRinde.size, conRinde.size - limpios.size)
    }

    /** Grilla de 10 m sobre el recuadro del lote, la misma que usan las imágenes Sentinel-2. */
    fun grillaDelLote(lote: List<Pair<Double, Double>>, pasoM: Double = 10.0): GrillaGeografica {
        val lats = lote.map { it.first }
        val lons = lote.map { it.second }
        val norte = lats.max()
        val sur = lats.min()
        val oeste = lons.min()
        val este = lons.max()
        val metrosLon = METROS_LON_ECUADOR * cos(radianes((norte + sur) / 2))
        // El margen evita una celda de más cuando el lado es justo un múltiplo de 10 m (redondeo)
        val columnas = ceil((este - oeste) * metrosLon / pasoM - 1e-6).toInt().coerceAtLeast(1)
        val filas = ceil((norte - sur) * METROS_LAT / pasoM - 1e-6).toInt().coerceAtLeast(1)
        return GrillaGeografica(oeste, norte, (este - oeste) / columnas, (norte - sur) / filas, columnas, filas)
    }

    /**
     * Rinde medio (t/ha) de cada celda del lote. Las celdas sin puntos (huecos entre pasadas o puntos
     * descartados) toman el promedio de sus vecinas, hasta 30 m. NaN fuera del lote.
     */
    fun rasterizar(puntos: List<PuntoRinde>, grilla: GrillaGeografica, lote: List<Pair<Double, Double>>): FloatArray {
        val suma = DoubleArray(grilla.total)
        val cuenta = IntArray(grilla.total)
        for (p in puntos) {
            val i = grilla.celdaDe(p.lat, p.lon) ?: continue
            suma[i] += p.rindeTHa
            cuenta[i]++
        }
        val anillo = GeometriaLote.anilloAbierto(lote)
        val adentro = BooleanArray(grilla.total) { i ->
            anillo.size < 3 || grilla.centro(i).let { (lat, lon) -> GeometriaLote.dentroDePoligono(lat, lon, anillo) }
        }
        val valores = FloatArray(grilla.total) { i -> if (adentro[i] && cuenta[i] > 0) (suma[i] / cuenta[i]).toFloat() else Float.NaN }
        repeat(VUELTAS_RELLENO) {
            val nuevos = mutableListOf<Pair<Int, Float>>()
            for (i in 0 until grilla.total) {
                if (!adentro[i] || !valores[i].isNaN()) continue
                val fila = i / grilla.columnas
                val col = i % grilla.columnas
                var s = 0.0
                var n = 0
                for (df in -1..1) for (dc in -1..1) {
                    val f = fila + df
                    val c = col + dc
                    if (f !in 0 until grilla.filas || c !in 0 until grilla.columnas) continue
                    val v = valores[f * grilla.columnas + c]
                    if (!v.isNaN()) {
                        s += v
                        n++
                    }
                }
                if (n > 0) nuevos += i to (s / n).toFloat()
            }
            nuevos.forEach { (i, v) -> valores[i] = v }
        }
        return valores
    }

    /** Rinde relativo a la mediana del lote (1,00 = lo típico), para comparar o sumar campañas. */
    fun relativo(valores: FloatArray): FloatArray {
        val validos = valores.filter { !it.isNaN() }
        if (validos.isEmpty()) return valores.copyOf()
        val m = mediana(validos.map { it.toDouble() }).toFloat()
        return FloatArray(valores.size) { if (valores[it].isNaN() || m <= 0f) Float.NaN else valores[it] / m }
    }

    /** Rinde medio de los puntos que caen en cada zona (las zonas sin puntos no aparecen). */
    fun rindePorZona(puntos: List<PuntoRinde>, zonificacion: Zonificacion): List<RindeZona> {
        val suma = mutableMapOf<Int, Double>()
        val cuenta = mutableMapOf<Int, Int>()
        for (p in puntos) {
            val zona = zonaDe(p.lat, p.lon, zonificacion) ?: continue
            suma[zona] = (suma[zona] ?: 0.0) + p.rindeTHa
            cuenta[zona] = (cuenta[zona] ?: 0) + 1
        }
        return suma.keys.sorted().map { z -> RindeZona(z, suma.getValue(z) / cuenta.getValue(z), cuenta.getValue(z)) }
    }

    /** Zona de manejo del punto, o null si cae fuera del lote. */
    fun zonaDe(lat: Double, lon: Double, zonificacion: Zonificacion): Int? {
        val i = zonificacion.grilla.celdaDe(lat, lon) ?: return null
        return zonificacion.etiquetas[i].takeIf { it > 0 }
    }

    /**
     * Si el rinde acompaña a las zonas: true cuando cada zona rindió más que la anterior (zona 1 =
     * la de menor índice o potencial).
     */
    fun zonasOrdenadas(rindes: List<RindeZona>): Boolean =
        rindes.size >= 2 && rindes.sortedBy { it.zona }.zipWithNext().all { (a, b) -> b.rindeTHa > a.rindeTHa }

    internal fun mediana(v: List<Double>): Double {
        val o = v.sorted()
        return if (o.size % 2 == 1) o[o.size / 2] else (o[o.size / 2 - 1] + o[o.size / 2]) / 2
    }
}
