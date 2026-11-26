package com.example.utils

import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.round

/**
 * Capas del escaneo Sentinel-2 para la página del mapa (GRANEX.setCapa), en el mismo formato que
 * arma Android en SateliteEscaneo.capaSentinelJson. Puro, para la web y los tests:
 * - grilla: {capa:"s2", id, rampa, etiqueta, fecha, escala{min,max}, geo{...}, valores[]} — los
 *   píxeles sin dato o con el centro fuera del lote van como null;
 * - foto: {capa:"s2foto", id, etiqueta, fecha, imagen:"data:image/png;base64,…", limites{...}}.
 * El [id] identifica lo dibujado: la página no redibuja si llega el mismo.
 */
object CapaMapaEscaneo {

    /** El índice de una fecha con la escala del modo elegido. */
    fun indice(imagen: ImagenIndices, indice: IndiceVegetacion, escala: EscalaColor, modo: ModoEscala, lote: List<Pair<Double, Double>>): String =
        grilla(
            id = "${imagen.fecha}|$indice|$modo",
            rampa = "indice",
            etiqueta = indice.nombre,
            pie = "Sentinel-2 · ${fechaLarga(imagen.fecha)}",
            min = escala.minimo,
            max = escala.maximo,
            grilla = imagen.grilla,
            valores = imagen.valores(indice),
            adentro = EscaneoLote.dentroDelLote(imagen, lote)
        )

    /** Mitad del rango fijo del mapa de cambio entre fechas (unidades del índice), como en Android. */
    const val ESCALA_CAMBIO = 0.3

    /** Cambio del índice entre dos fechas (nueva − anterior), sobre la grilla de [nueva]. */
    fun cambio(c: ComparacionEscaneo, nueva: ImagenIndices, lote: List<Pair<Double, Double>>): String =
        grilla(
            id = "cambio|${c.fechaAnterior}|${c.fechaNueva}|${c.indice}",
            rampa = "cambio",
            etiqueta = "Cambio de ${c.indice.nombre}",
            pie = "${fechaLarga(c.fechaAnterior)} → ${fechaLarga(c.fechaNueva)}",
            min = -ESCALA_CAMBIO,
            max = ESCALA_CAMBIO,
            grilla = nueva.grilla,
            valores = c.diferencia,
            adentro = EscaneoLote.dentroDelLote(nueva, lote),
            extra = ",\"signo\":true"
        )

    /** Altura de cada píxel respecto de la típica del lote (mediana), en metros. */
    fun relieve(relieve: RelieveLote, resumen: ResumenRelieve, lote: List<Pair<Double, Double>>): String {
        val escala = escalaRelieve(resumen)
        return grilla(
            id = "relieve|${relieve.grilla.oeste}|${relieve.grilla.norte}",
            rampa = "relieve",
            etiqueta = "Altura",
            pie = "respecto de la típica del lote (${formatoDecimal(resumen.mediana, 0)} m s. n. m.)",
            min = -escala,
            max = escala,
            grilla = relieve.grilla,
            valores = FloatArray(relieve.altura.size) { (relieve.altura[it] - resumen.mediana).toFloat() },
            adentro = AnalisisRelieve.adentro(relieve.grilla, lote),
            extra = ",\"signo\":true,\"unidad\":\"m\""
        )
    }

    /** La foto en color real ya trae su transparencia fuera de la imagen: va como PNG con sus límites. */
    @OptIn(ExperimentalEncodingApi::class)
    fun foto(fecha: String, png: ByteArray, oeste: Double, sur: Double, este: Double, norte: Double): String = buildString(png.size * 4 / 3 + 400) {
        append("{\"capa\":\"s2foto\",\"id\":").append(texto("foto|$fecha"))
        append(",\"etiqueta\":\"Color real\"")
        append(",\"fecha\":").append(texto("Sentinel-2 · ${fechaLarga(fecha)}"))
        append(",\"imagen\":\"data:image/png;base64,").append(Base64.encode(png)).append('"')
        append(",\"limites\":{\"oeste\":").append(oeste).append(",\"sur\":").append(sur)
            .append(",\"este\":").append(este).append(",\"norte\":").append(norte).append("}}")
    }

    /**
     * Mitad del rango de colores del relieve, en metros: hasta las puntas sin ruido (p2 / p98) y nunca
     * menos de medio metro, para que un lote plano no parezca lleno de lomas y bajos por centímetros.
     */
    fun escalaRelieve(r: ResumenRelieve): Double = maxOf(r.mediana - r.p2, r.p98 - r.mediana, 0.5)

    /** PNG del relieve (en ARGB, como Bitmap.getPixels): altura = (R·256 + G) / 10 − 500 m; B = 255 con dato. */
    fun relieveDesdePixeles(pixeles: IntArray, ancho: Int, alto: Int, oeste: Double, sur: Double, este: Double, norte: Double): RelieveLote {
        require(pixeles.size == ancho * alto) { "El relieve no tiene el tamaño esperado" }
        val grilla = GrillaGeografica(oeste, norte, (este - oeste) / ancho, (norte - sur) / alto, ancho, alto)
        val altura = FloatArray(pixeles.size) { i ->
            val p = pixeles[i]
            if ((p and 0xFF) == 0) Float.NaN else ((((p shr 16) and 0xFF) * 256 + ((p shr 8) and 0xFF)) / 10f - 500f)
        }
        return RelieveLote(grilla, altura)
    }

    /** "2026-10-12" → "12/10/2026". */
    fun fechaLarga(fecha: String): String =
        if (fecha.length >= 10) "${fecha.substring(8, 10)}/${fecha.substring(5, 7)}/${fecha.take(4)}" else fecha

    private fun grilla(
        id: String,
        rampa: String,
        etiqueta: String,
        pie: String,
        min: Double,
        max: Double,
        grilla: GrillaGeografica,
        valores: FloatArray,
        adentro: BooleanArray,
        extra: String = ""
    ): String = buildString(valores.size * 6 + 400) {
        val g = grilla
        append("{\"capa\":\"s2\",\"id\":").append(texto(id))
        append(",\"rampa\":").append(texto(rampa))
        append(",\"etiqueta\":").append(texto(etiqueta))
        append(",\"fecha\":").append(texto(pie))
        append(extra)
        append(",\"escala\":{\"min\":").append(min).append(",\"max\":").append(max).append('}')
        append(",\"geo\":{\"oeste\":").append(g.oeste).append(",\"norte\":").append(g.norte)
            .append(",\"pasoLon\":").append(g.pasoLon).append(",\"pasoLat\":").append(g.pasoLat)
            .append(",\"columnas\":").append(g.columnas).append(",\"filas\":").append(g.filas).append('}')
        append(",\"valores\":[")
        valores.forEachIndexed { i, v ->
            if (i > 0) append(',')
            if (v.isNaN() || !adentro[i]) append("null") else append(round(v * 1000.0) / 1000.0)
        }
        append("]}")
    }

    /** Texto JSON con comillas y escapes (los nombres los escribe el usuario). */
    private fun texto(s: String) = JsonPrimitive(s).toString()
}
