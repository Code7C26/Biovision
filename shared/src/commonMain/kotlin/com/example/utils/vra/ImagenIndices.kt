package com.example.utils.vra

/** Índices de Sentinel-2 L2A disponibles para zonificar. */
enum class IndiceVegetacion(val nombre: String, val descripcion: String, val resolucionM: Int) {
    NDVI("NDVI", "Vigor y biomasa. Se satura en cultivos muy densos.", 10),
    NDRE("NDRE", "Borde rojo: sigue la clorofila y el nitrógeno en cultivos avanzados.", 20),
    GNDVI("GNDVI", "Banda verde: sensible a la clorofila en estadios intermedios.", 10)
}

/**
 * Geometría de una grilla de celdas sobre el terreno: la grilla en grados (alineada al norte) o la
 * grilla de máquina (rotada al sentido de siembra). La celda i está en la fila i / columnas.
 */
interface MarcoCeldas {
    val columnas: Int
    val filas: Int
    val total: Int get() = columnas * filas

    /** Centro (lat, lon) de la celda [indice]. */
    fun centro(indice: Int): Pair<Double, Double>

    /**
     * (lat, lon) del vértice (x, y) de la grilla: x crece hacia la derecha y y "hacia abajo" (al sur en
     * la grilla geográfica). Con esa convención los contornos que recorren las celdas en sentido
     * horario quedan horarios también en el terreno.
     */
    fun vertice(x: Int, y: Int): Pair<Double, Double>

    /** Celda que contiene el punto, o null si cae fuera de la grilla. */
    fun celdaDe(lat: Double, lon: Double): Int?
}

/** Grilla regular en grados WGS84. La fila 0 es la del norte. */
data class GrillaGeografica(
    val oeste: Double,
    val norte: Double,
    val pasoLon: Double,
    val pasoLat: Double,
    override val columnas: Int,
    override val filas: Int
) : MarcoCeldas {
    override val total: Int get() = columnas * filas
    val sur: Double get() = norte - filas * pasoLat
    val este: Double get() = oeste + columnas * pasoLon

    override fun centro(indice: Int): Pair<Double, Double> {
        val fila = indice / columnas
        val columna = indice % columnas
        return (norte - (fila + 0.5) * pasoLat) to (oeste + (columna + 0.5) * pasoLon)
    }

    override fun vertice(x: Int, y: Int): Pair<Double, Double> = (norte - y * pasoLat) to (oeste + x * pasoLon)

    override fun celdaDe(lat: Double, lon: Double): Int? {
        val columna = kotlin.math.floor((lon - oeste) / pasoLon).toInt()
        val fila = kotlin.math.floor((norte - lat) / pasoLat).toInt()
        return if (columna in 0 until columnas && fila in 0 until filas) fila * columnas + columna else null
    }
}

/** Índices por píxel de una escena; NaN = nube, sombra, agua, sin dato o fuera del lote. */
class ImagenIndices(
    val grilla: GrillaGeografica,
    val fecha: String,
    val ndvi: FloatArray,
    val ndre: FloatArray,
    val gndvi: FloatArray
) {
    fun valores(indice: IndiceVegetacion): FloatArray = when (indice) {
        IndiceVegetacion.NDVI -> ndvi
        IndiceVegetacion.NDRE -> ndre
        IndiceVegetacion.GNDVI -> gndvi
    }

    val pixelesValidos: Int get() = ndvi.count { !it.isNaN() }

    companion object {
        /**
         * Valor de un canal de la imagen PNG que devuelve el evalscript: 0 = sin dato, 1..255 = índice
         * de -1 a 1 (paso de 0,0079, suficiente para zonificar).
         */
        fun decodificarCanal(v: Int): Float = if (v <= 0) Float.NaN else ((v - 1) / 254f) * 2f - 1f

        /** [pixeles] en ARGB (como Bitmap.getPixels): R = NDVI, G = NDRE, B = GNDVI. */
        fun desdePixeles(
            pixeles: IntArray,
            ancho: Int,
            alto: Int,
            oeste: Double,
            sur: Double,
            este: Double,
            norte: Double,
            fecha: String
        ): ImagenIndices {
            require(pixeles.size == ancho * alto) { "La imagen no tiene el tamaño esperado" }
            val grilla = GrillaGeografica(oeste, norte, (este - oeste) / ancho, (norte - sur) / alto, ancho, alto)
            return ImagenIndices(
                grilla = grilla,
                fecha = fecha,
                ndvi = FloatArray(pixeles.size) { decodificarCanal((pixeles[it] shr 16) and 0xFF) },
                ndre = FloatArray(pixeles.size) { decodificarCanal((pixeles[it] shr 8) and 0xFF) },
                gndvi = FloatArray(pixeles.size) { decodificarCanal(pixeles[it] and 0xFF) }
            )
        }
    }
}

/** Una fecha con imagen Sentinel-2 sobre el lote y sus promedios (sin nubes ni sombras). */
data class EscenaSentinel(
    val fecha: String,          // yyyy-MM-dd
    val nubesPct: Double,       // % del lote sin dato válido ese día
    val ndvi: Double?,
    val ndre: Double?,
    val gndvi: Double?,
    val pixelesValidos: Int
) {
    fun valor(indice: IndiceVegetacion): Double? = when (indice) {
        IndiceVegetacion.NDVI -> ndvi
        IndiceVegetacion.NDRE -> ndre
        IndiceVegetacion.GNDVI -> gndvi
    }
}
