package com.example.utils.vra

/**
 * Resultado de laboratorio de una muestra: compuesta de una zona o de un punto georreferenciado.
 * null = no se midió.
 */
data class MuestraSuelo(
    /** Fósforo extractable Bray-1 (ppm), 0–20 cm. */
    val pBrayPpm: Double? = null,
    /** Nitrógeno de nitratos disponible 0–60 cm (kg N/ha). */
    val nNitratosKgHa: Double? = null,
    /** Materia orgánica (%), 0–20 cm. */
    val materiaOrganicaPct: Double? = null,
    val ph: Double? = null
) {
    val vacia: Boolean get() = pBrayPpm == null && nNitratosKgHa == null && materiaOrganicaPct == null && ph == null

    /** Cada valor de esta muestra; los que faltan se completan con [otra]. */
    fun completarCon(otra: MuestraSuelo?): MuestraSuelo = if (otra == null) this else MuestraSuelo(
        pBrayPpm ?: otra.pBrayPpm,
        nNitratosKgHa ?: otra.nNitratosKgHa,
        materiaOrganicaPct ?: otra.materiaOrganicaPct,
        ph ?: otra.ph
    )
}

data class PuntoSuelo(val lat: Double, val lon: Double, val muestra: MuestraSuelo)

/** Promedio de los puntos de muestreo que caen en una zona. */
data class SueloZona(val zona: Int, val muestra: MuestraSuelo, val puntos: Int)

/** Columnas de la planilla del laboratorio con cada análisis (null = no vino). */
data class ColumnasSuelo(
    val pBray: String? = null,
    val nNitratos: String? = null,
    val materiaOrganica: String? = null,
    val ph: String? = null
) {
    val ninguna: Boolean get() = pBray == null && nNitratos == null && materiaOrganica == null && ph == null
}

/** Análisis de suelo por zona de manejo. Puro, probado en AnalisisSueloTest. */
object AnalisisSuelo {

    /**
     * Busca las columnas por el nombre que suelen usar los laboratorios: "P Bray", "Fósforo",
     * "N-NO3", "Nitratos", "MO", "Materia orgánica", "pH".
     */
    fun columnasSugeridas(tabla: TablaCampo): ColumnasSuelo = ColumnasSuelo(
        pBray = tabla.buscar("pbray", "p_bray", "fosforo", "pext", "p_ext", "p"),
        nNitratos = tabla.buscar("nno3", "n_no3", "nitrato", "nno", "n_dis", "ndisp", "n"),
        materiaOrganica = tabla.buscar("mo", "materiaorg", "materia_org", "om"),
        ph = tabla.buscar("ph")
    )

    fun puntos(tabla: TablaCampo, columnas: ColumnasSuelo): List<PuntoSuelo> {
        fun valor(columna: String?, fila: Int): Double? =
            columna?.let { tabla.columnas[it]?.get(fila) }?.takeIf { !it.isNaN() }
        return (0 until tabla.filas).map { f ->
            PuntoSuelo(
                tabla.lat[f], tabla.lon[f],
                MuestraSuelo(valor(columnas.pBray, f), valor(columnas.nNitratos, f), valor(columnas.materiaOrganica, f), valor(columnas.ph, f))
            )
        }.filter { !it.muestra.vacia }
    }

    /** Promedio de cada análisis con los puntos de la zona que lo tienen. */
    fun porZona(puntos: List<PuntoSuelo>, zonificacion: Zonificacion): Map<Int, SueloZona> =
        puntos.mapNotNull { p -> MapaRinde.zonaDe(p.lat, p.lon, zonificacion)?.let { it to p.muestra } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (zona, muestras) ->
                fun prom(valor: (MuestraSuelo) -> Double?): Double? =
                    muestras.mapNotNull(valor).takeIf { it.isNotEmpty() }?.average()
                SueloZona(
                    zona,
                    MuestraSuelo(prom { it.pBrayPpm }, prom { it.nNitratosKgHa }, prom { it.materiaOrganicaPct }, prom { it.ph }),
                    muestras.size
                )
            }

    /**
     * La muestra que vale para cada zona: lo cargado a mano tiene prioridad, valor por valor, sobre el
     * promedio de los puntos.
     */
    fun deZona(zona: Int, manual: Map<Int, MuestraSuelo>, puntos: Map<Int, SueloZona>): MuestraSuelo? {
        val m = manual[zona]?.takeIf { !it.vacia }
        val p = puntos[zona]?.muestra
        return when {
            m != null -> m.completarCon(p)
            else -> p
        }
    }
}
