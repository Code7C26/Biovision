package com.example.utils

import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Cómo se pintan los colores del mapa de índices. */
enum class ModoEscala(val nombre: String, val descripcion: String) {
    ABSOLUTA("Absoluto", "Misma escala en todas las fechas: sirve para comparar el lote en el tiempo."),
    RELATIVA("Zonas del lote", "La escala va del valor más bajo al más alto del lote: muestra dónde rinde más y menos.")
}

/** Rango de valores que cubre la paleta de colores. */
data class EscalaColor(val minimo: Double, val maximo: Double)

enum class ClaseVigor(val nombre: String) { BAJO("Bajo"), MEDIO("Medio"), ALTO("Alto") }

data class ZonaVigor(val clase: ClaseVigor, val hectareas: Double, val porcentaje: Double)

/** Qué hay en el lote según el NDVI típico (mediana). */
enum class CoberturaLote(val nombre: String) {
    SUELO_DESNUDO("Sin cultivo activo"),
    CULTIVO_INICIAL("Cultivo en implantación o poco desarrollado"),
    CULTIVO_ACTIVO("Cultivo activo")
}

/** Estadísticas de un índice sobre el lote en una fecha (solo píxeles sin nubes dentro del lote). */
data class ResumenEscaneo(
    val indice: IndiceVegetacion,
    val fecha: String,
    val media: Double,
    val mediana: Double,
    val p2: Double,
    val p98: Double,
    val desvio: Double,
    val hectareasConDato: Double,
    val zonas: List<ZonaVigor>,
    /** Por el NDVI; null si el resumen es de otro índice. */
    val cobertura: CoberturaLote?,
    val diagnostico: String
) {
    /**
     * Paleta para cada modo. La relativa queda centrada en la mediana (lo típico del lote se ve
     * amarillo) y llega hasta el percentil 2 o 98 más alejado: así un bajo o un desagüe con más verde
     * no tiñe de rojo todo el resto, y los colores coinciden con las zonas bajo / medio / alto.
     */
    fun escala(modo: ModoEscala): EscalaColor = when (modo) {
        ModoEscala.ABSOLUTA -> EscaneoLote.escalaAbsoluta(indice)
        ModoEscala.RELATIVA -> {
            val d = maxOf(p98 - mediana, mediana - p2, EscaneoLote.RANGO_MINIMO / 2)
            EscalaColor(mediana - d, mediana + d)
        }
    }
}

/** Cambio de un índice entre dos fechas, píxel a píxel (fecha nueva − fecha anterior). */
data class ComparacionEscaneo(
    val indice: IndiceVegetacion,
    val fechaAnterior: String,
    val fechaNueva: String,
    /** Diferencia por píxel; NaN donde falta dato en alguna de las dos fechas. */
    val diferencia: FloatArray,
    val cambioMedio: Double,
    val hectareasSubio: Double,
    val hectareasBajo: Double,
    val hectareasEstable: Double
)

/**
 * Análisis de las imágenes Sentinel-2 (10 m) de un lote para la pantalla de escaneo. Puro: sin red ni
 * Android, probado en commonTest.
 *
 * Zonas de vigor: relativas a la mediana del lote. "Bajo" es más de [UMBRAL_ZONA] por debajo de la
 * mediana y "alto" más de [UMBRAL_ZONA] por encima (en unidades del índice), así un lote parejo queda
 * casi todo en "medio" en lugar de partirse en tercios artificiales.
 */
object EscaneoLote {
    const val UMBRAL_ZONA = 0.05
    const val UMBRAL_CAMBIO = 0.05
    /** Rango mínimo de la escala relativa: con menos, el ruido del sensor parecería variación real. */
    const val RANGO_MINIMO = 0.08
    /** Por debajo de esta mediana de NDVI el lote está sin cultivo (suelo desnudo, rastrojo o barbecho). */
    const val NDVI_SUELO = 0.25
    const val NDVI_ACTIVO = 0.45

    fun escalaAbsoluta(indice: IndiceVegetacion): EscalaColor = when (indice) {
        IndiceVegetacion.NDVI -> EscalaColor(0.0, 0.9)
        IndiceVegetacion.NDRE -> EscalaColor(0.0, 0.6)
        IndiceVegetacion.GNDVI -> EscalaColor(0.1, 0.85)
    }

    /** Superficie de un píxel de la imagen, en hectáreas (proyección local a la latitud del lote). */
    fun hectareasPorPixel(imagen: ImagenIndices): Double {
        val g = imagen.grilla
        val latMedia = (g.norte + g.sur) / 2
        val metrosLon = 111_412.84 * cos(radianes(latMedia))
        return g.pasoLon * metrosLon * g.pasoLat * 110_950.0 / 10_000.0
    }

    /**
     * Píxeles que cuentan para el lote: con dato y, si se pasa el contorno [lote] en (lat, lon), con
     * el centro adentro. La imagen trae enteros los píxeles del borde que el lote toca apenas; sin
     * este filtro la superficie con dato superaba a la del lote.
     */
    fun dentroDelLote(imagen: ImagenIndices, lote: List<Pair<Double, Double>>?): BooleanArray {
        val g = imagen.grilla
        val anillo = lote?.let { GeometriaLote.anilloAbierto(it) }?.takeIf { it.size >= 3 }
        return BooleanArray(g.total) { i ->
            anillo == null || g.centro(i).let { (lat, lon) -> GeometriaLote.dentroDePoligono(lat, lon, anillo) }
        }
    }

    /** null si la imagen no tiene ningún píxel válido dentro del lote. */
    fun resumir(imagen: ImagenIndices, indice: IndiceVegetacion, lote: List<Pair<Double, Double>>? = null): ResumenEscaneo? {
        val adentro = dentroDelLote(imagen, lote)
        fun validosDe(valores: FloatArray) = valores.filterIndexed { i, v -> adentro[i] && !v.isNaN() }.map { it.toDouble() }.sorted()
        val validos = validosDe(imagen.valores(indice))
        if (validos.isEmpty()) return null
        val haPixel = hectareasPorPixel(imagen)
        val media = validos.average()
        val mediana = percentil(validos, 0.5)
        val desvio = sqrt(validos.sumOf { (it - media) * (it - media) } / validos.size)
        val conteo = IntArray(3)
        for (v in validos) conteo[claseDe(v, mediana).ordinal]++
        val zonas = ClaseVigor.entries.map { c ->
            ZonaVigor(c, conteo[c.ordinal] * haPixel, 100.0 * conteo[c.ordinal] / validos.size)
        }
        // La cobertura sale del NDVI aunque se esté mirando otro índice
        val medianaNdvi = validosDe(imagen.ndvi).takeIf { it.isNotEmpty() }?.let { percentil(it, 0.5) }
        val cobertura = medianaNdvi?.let { coberturaDe(it) }
        return ResumenEscaneo(
            indice = indice,
            fecha = imagen.fecha,
            media = media,
            mediana = mediana,
            p2 = percentil(validos, 0.02),
            p98 = percentil(validos, 0.98),
            desvio = desvio,
            hectareasConDato = validos.size * haPixel,
            zonas = zonas,
            cobertura = cobertura,
            diagnostico = diagnostico(indice, mediana, zonas, cobertura, medianaNdvi)
        )
    }

    fun claseDe(valor: Double, mediana: Double): ClaseVigor = when {
        valor < mediana - UMBRAL_ZONA -> ClaseVigor.BAJO
        valor > mediana + UMBRAL_ZONA -> ClaseVigor.ALTO
        else -> ClaseVigor.MEDIO
    }

    fun coberturaDe(medianaNdvi: Double): CoberturaLote = when {
        medianaNdvi < NDVI_SUELO -> CoberturaLote.SUELO_DESNUDO
        medianaNdvi < NDVI_ACTIVO -> CoberturaLote.CULTIVO_INICIAL
        else -> CoberturaLote.CULTIVO_ACTIVO
    }

    private fun diagnostico(
        indice: IndiceVegetacion,
        mediana: Double,
        zonas: List<ZonaVigor>,
        cobertura: CoberturaLote?,
        medianaNdvi: Double?
    ): String {
        val bajo = zonas.first { it.clase == ClaseVigor.BAJO }
        val alto = zonas.first { it.clase == ClaseVigor.ALTO }
        val ndvi = medianaNdvi?.let { formatoDecimal(it, 2) }
        val zonasTexto = "${formatoDecimal(bajo.hectareas, 1)} ha (${formatoDecimal(bajo.porcentaje, 0)} %) por debajo de lo típico del lote " +
            "y ${formatoDecimal(alto.hectareas, 1)} ha (${formatoDecimal(alto.porcentaje, 0)} %) por encima."
        return when (cobertura) {
            CoberturaLote.SUELO_DESNUDO ->
                "El lote está sin cultivo activo (NDVI típico $ndvi): suelo desnudo, rastrojo o barbecho. " +
                    "Las diferencias de color muestran suelo, humedad o rastrojo, no vigor de un cultivo."
            CoberturaLote.CULTIVO_INICIAL ->
                "Cultivo en implantación o con poca cobertura (NDVI típico $ndvi). Las zonas bajas pueden ser fallas de " +
                    "implantación o manchones: $zonasTexto"
            CoberturaLote.CULTIVO_ACTIVO, null -> {
                val parejo = bajo.porcentaje < 10 && alto.porcentaje < 10
                if (parejo) "Lote parejo: más del 80 % está cerca del valor típico (${indice.nombre} ${formatoDecimal(mediana, 2)})."
                else "Hay variación dentro del lote: $zonasTexto Conviene recorrer las zonas bajas."
            }
        }
    }

    /**
     * Cambio entre [anterior] y [nueva]. Las dos imágenes tienen que ser del mismo lote (misma grilla);
     * si no coinciden devuelve null.
     */
    fun comparar(
        anterior: ImagenIndices,
        nueva: ImagenIndices,
        indice: IndiceVegetacion,
        lote: List<Pair<Double, Double>>? = null
    ): ComparacionEscaneo? {
        val ga = anterior.grilla
        val gn = nueva.grilla
        if (ga.columnas != gn.columnas || ga.filas != gn.filas ||
            abs(ga.oeste - gn.oeste) > 1e-7 || abs(ga.norte - gn.norte) > 1e-7
        ) return null
        val a = anterior.valores(indice)
        val n = nueva.valores(indice)
        val adentro = dentroDelLote(nueva, lote)
        val diferencia = FloatArray(a.size) { if (!adentro[it] || a[it].isNaN() || n[it].isNaN()) Float.NaN else n[it] - a[it] }
        val validos = diferencia.filter { !it.isNaN() }
        if (validos.isEmpty()) return null
        val haPixel = hectareasPorPixel(nueva)
        return ComparacionEscaneo(
            indice = indice,
            fechaAnterior = anterior.fecha,
            fechaNueva = nueva.fecha,
            diferencia = diferencia,
            cambioMedio = validos.average(),
            hectareasSubio = validos.count { it > UMBRAL_CAMBIO } * haPixel,
            hectareasBajo = validos.count { it < -UMBRAL_CAMBIO } * haPixel,
            hectareasEstable = validos.count { abs(it) <= UMBRAL_CAMBIO } * haPixel
        )
    }

    /** Caída mínima respecto de las fechas vecinas para sospechar neblina o nubes finas. */
    const val CAIDA_DUDOSA = 0.08

    /**
     * Fechas con neblina o nubes finas que el filtro de nubes no detecta: su promedio cae más de
     * [CAIDA_DUDOSA] por debajo de las fechas anteriores (mediana de hasta 2) y también de las
     * posteriores. Un cultivo no pierde y recupera tanto vigor en pocos días; una cosecha o una helada
     * bajan y no se recuperan, así que no se marcan. La primera y la última fecha no se pueden juzgar
     * (falta un lado) hasta que llegue la siguiente.
     */
    fun fechasDudosas(escenas: List<EscenaSentinel>, indice: IndiceVegetacion): Set<String> {
        val serie = escenas.mapNotNull { e -> e.valor(indice)?.let { e.fecha to it } }.sortedBy { it.first }
        if (serie.size < 3) return emptySet()
        fun mediana(v: List<Double>): Double {
            val o = v.sorted()
            return if (o.size % 2 == 1) o[o.size / 2] else (o[o.size / 2 - 1] + o[o.size / 2]) / 2
        }
        return (1 until serie.lastIndex).filter { i ->
            val antes = mediana(((i - 2).coerceAtLeast(0) until i).map { serie[it].second })
            val despues = mediana((i + 1..(i + 2).coerceAtMost(serie.lastIndex)).map { serie[it].second })
            val v = serie[i].second
            v < antes - CAIDA_DUDOSA && v < despues - CAIDA_DUDOSA
        }.map { serie[it].first }.toSet()
    }

    /** Fechas útiles para el mapa y la serie: con al menos [maxNubesPct] % del lote despejado. */
    fun escenasUtiles(escenas: List<EscenaSentinel>, maxNubesPct: Double = 30.0): List<EscenaSentinel> =
        escenas.filter { it.nubesPct <= maxNubesPct && it.ndvi != null }

    private fun percentil(ordenados: List<Double>, p: Double): Double {
        if (ordenados.size == 1) return ordenados[0]
        val pos = p * (ordenados.size - 1)
        val i = pos.toInt()
        val f = pos - i
        return if (i + 1 < ordenados.size) ordenados[i] * (1 - f) + ordenados[i + 1] * f else ordenados[i]
    }
}
