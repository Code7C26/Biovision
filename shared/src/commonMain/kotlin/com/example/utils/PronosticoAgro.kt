package com.example.utils

/**
 * Herramientas agronómicas sobre el pronóstico de Open-Meteo (modelo ECMWF): ventana de
 * pulverización hora por hora, helada con hora estimada y balance hídrico (lluvia − ET0).
 * Todo es estimado por el modelo, no medido: la pantalla tiene que decirlo.
 */

/**
 * Una hora del pronóstico, tal como la da Open-Meteo. [lluviaHoraPreviaMm] es la lluvia acumulada en
 * la hora ANTERIOR a [epochS] (convención de la API): la que cae entre epochS y epochS + 1 h es la
 * de la hora siguiente.
 */
data class HoraPronosticoAgro(
    val epochS: Long,
    val temperaturaC: Double?,
    val humedadPct: Double?,
    val vientoKmh: Double?,
    val rafagaKmh: Double?,
    val lluviaHoraPreviaMm: Double?,
    val esDeDia: Boolean? = null
)

/** Un día del pronóstico o del pasado reciente; [epochS] es la medianoche local. */
data class DiaBalance(val epochS: Long, val lluviaMm: Double?, val et0Mm: Double?)

data class HoraPulverizacion(
    val epochS: Long,
    /** Hora local, 0 a 23. */
    val hora: Int,
    /** 0 = hoy, 1 = mañana… */
    val dia: Int,
    val aptitud: EstacionMeteo.Aptitud,
    val deltaT: Double?,
    val vientoKmh: Double?,
    val rafagaKmh: Double?,
    val lluviaMm: Double,
    /** Del más grave al más leve; vacío si las condiciones son buenas. */
    val motivos: List<String>
)

/** Horas buenas seguidas: [hastaS] es el final de la última hora (exclusivo). */
data class VentanaPulverizacion(
    val desdeS: Long,
    val hastaS: Long,
    val horas: Int,
    val dia: Int,
    val horaDesde: Int,
    val horaHasta: Int
)

data class PronosticoPulverizacion(
    val horas: List<HoraPulverizacion>,
    val ventanas: List<VentanaPulverizacion>,
    /** Día local de hoy (días desde 1970), para nombrar los días. */
    val hoy: Long
)

enum class NivelHelada { SIN_RIESGO, RIESGO, HELADA }

data class PronosticoHelada(
    val nivel: NivelHelada,
    val minimaC: Double,
    val momentoMinimaS: Long,
    val diaMinima: Int,
    val horaMinima: Int,
    /** Horas seguidas a 3 °C o menos alrededor de la mínima (0 si no baja de 3 °C). */
    val horasRiesgo: Int,
    /** De esas, las que están a 0 °C o menos. */
    val horasBajoCero: Int,
    val desdeHora: Int?,
    val hastaHora: Int?,
    /** Cuántas noches distintas del período bajan de 3 °C. */
    val nochesConRiesgo: Int,
    val horasAnalizadas: Int,
    val hoy: Long
)

enum class EstadoHidrico { SIN_DEFICIT, DEFICIT_MODERADO, DEFICIT_MARCADO }

data class BalanceHidrico(
    val diasPasados: Int,
    val lluviaPasadaMm: Double,
    val et0PasadaMm: Double,
    val lluviaUltimos7Mm: Double,
    val diasProximos: Int,
    val lluviaProximaMm: Double,
    val et0ProximaMm: Double,
    val estado: EstadoHidrico,
    /** Días pasados y próximos en orden; [indiceHoy] marca el primero del pronóstico. */
    val dias: List<DiaBalance>,
    val indiceHoy: Int,
    /** Día local de hoy: el de dias[i] es hoy − indiceHoy + i. */
    val hoy: Long
) {
    val balancePasadoMm: Double get() = lluviaPasadaMm - et0PasadaMm
    val balanceProximoMm: Double get() = lluviaProximaMm - et0ProximaMm
}

object PronosticoAgro {

    /** Desde esta lluvia en una hora (mm) se considera que llueve. */
    const val LLUVIA_HORA_MM = 0.2

    /** Lluvia (mm) en las 2 horas siguientes que puede lavar un producto recién aplicado. */
    const val LLUVIA_LAVADO_MM = 1.0

    /** Horas buenas seguidas para que valga como ventana (llenar, aplicar y algo de margen). */
    const val HORAS_MINIMAS_VENTANA = 2

    const val UMBRAL_RIESGO_HELADA_C = 3.0

    private const val SEG_DIA = 86_400L

    /** Día local (días desde 1970) de un instante, con el desfase de la localidad. */
    fun diaLocal(epochS: Long, desfaseS: Int): Long = (epochS + desfaseS).floorDiv(SEG_DIA)

    fun horaLocal(epochS: Long, desfaseS: Int): Int = ((epochS + desfaseS).mod(SEG_DIA) / 3600).toInt()

    /** "Hoy", "Mañana" o el día de la semana. */
    fun nombreDia(diaRelativo: Int, diaLocal: Long): String = when (diaRelativo) {
        0 -> "Hoy"
        1 -> "Mañana"
        else -> listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")[
            // El 1/1/1970 fue jueves
            ((diaLocal + 4).mod(7L)).toInt()
        ]
    }

    fun hora(h: Int): String = h.toString().padStart(2, '0') + ":00"

    /** "16/11" de un día local (días desde 1970; algoritmo civil_from_days de Howard Hinnant). */
    fun fechaDiaMes(diaLocal: Long): String {
        val z = diaLocal + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = doy - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        return dia.toString().padStart(2, '0') + "/" + mes.toString().padStart(2, '0')
    }

    /** Índice de la hora en curso: la primera que todavía no terminó. */
    private fun indiceActual(horas: List<HoraPronosticoAgro>, ahoraS: Long): Int =
        horas.indexOfFirst { it.epochS + 3600 > ahoraS }.let { if (it < 0) horas.size else it }

    /** Lluvia que cae en la hora i (de epochS a epochS + 1 h). */
    private fun lluviaEnHora(horas: List<HoraPronosticoAgro>, i: Int): Double =
        horas.getOrNull(i + 1)?.lluviaHoraPreviaMm ?: 0.0

    /**
     * Aptitud para pulverizar en cada una de las próximas [cantidad] horas, con las mismas reglas que
     * la estación propia ([EstacionMeteo.pulverizacion]: Delta T 2–8, viento 3–15 km/h, < 30 °C), más
     * dos propias del pronóstico: lluvia en esa hora (no) o en las 2 siguientes (puede lavar el
     * producto), y de noche con menos de 6 km/h (posible inversión térmica).
     */
    fun pulverizacion(
        horas: List<HoraPronosticoAgro>,
        ahoraS: Long,
        desfaseS: Int,
        cantidad: Int = 48
    ): PronosticoPulverizacion {
        val inicio = indiceActual(horas, ahoraS)
        val hoy = diaLocal(ahoraS, desfaseS)
        val resultado = (inicio until minOf(horas.size, inicio + cantidad)).mapNotNull { i ->
            val h = horas[i]
            val base = EstacionMeteo.pulverizacion(h.temperaturaC, h.humedadPct, h.vientoKmh, h.rafagaKmh)
                ?: return@mapNotNull null
            val lluvia = lluviaEnHora(horas, i)
            val lluviaDespues = lluviaEnHora(horas, i + 1) + lluviaEnHora(horas, i + 2)
            val graves = mutableListOf<String>()
            val leves = mutableListOf<String>()
            if (lluvia >= LLUVIA_HORA_MM) graves += "Lluvia pronosticada (${formatoDecimal(lluvia, 1)} mm)"
            else if (lluviaDespues >= LLUVIA_LAVADO_MM) {
                leves += "Lluvia en las 2 horas siguientes (${formatoDecimal(lluviaDespues, 1)} mm): puede lavar el producto"
            }
            val viento = h.vientoKmh
            if (h.esDeDia == false && viento != null && viento >= 3.0 && viento < 6.0) {
                leves += "De noche y con poco viento: posible inversión térmica"
            }
            val aptitud = when {
                graves.isNotEmpty() -> EstacionMeteo.Aptitud.NO_RECOMENDADA
                leves.isNotEmpty() && base.aptitud == EstacionMeteo.Aptitud.BUENA -> EstacionMeteo.Aptitud.PRECAUCION
                else -> base.aptitud
            }
            HoraPulverizacion(
                epochS = h.epochS,
                hora = horaLocal(h.epochS, desfaseS),
                dia = (diaLocal(h.epochS, desfaseS) - hoy).toInt(),
                aptitud = aptitud,
                deltaT = base.deltaT,
                vientoKmh = h.vientoKmh,
                rafagaKmh = h.rafagaKmh,
                lluviaMm = lluvia,
                motivos = graves + base.motivos + leves
            )
        }
        return PronosticoPulverizacion(resultado, ventanas(resultado), hoy)
    }

    private fun ventanas(horas: List<HoraPulverizacion>): List<VentanaPulverizacion> {
        val salida = mutableListOf<VentanaPulverizacion>()
        var i = 0
        while (i < horas.size) {
            if (horas[i].aptitud != EstacionMeteo.Aptitud.BUENA) { i++; continue }
            var j = i
            // Solo horas consecutivas (sin huecos en los datos)
            while (j + 1 < horas.size && horas[j + 1].aptitud == EstacionMeteo.Aptitud.BUENA &&
                horas[j + 1].epochS - horas[j].epochS == 3600L
            ) j++
            val cantidad = j - i + 1
            if (cantidad >= HORAS_MINIMAS_VENTANA) {
                salida += VentanaPulverizacion(
                    desdeS = horas[i].epochS,
                    hastaS = horas[j].epochS + 3600,
                    horas = cantidad,
                    dia = horas[i].dia,
                    horaDesde = horas[i].hora,
                    horaHasta = (horas[j].hora + 1) % 24
                )
            }
            i = j + 1
        }
        return salida
    }

    /**
     * Mínima de las próximas [cantidad] horas y cuánto dura el frío alrededor de ella. Es la
     * temperatura del abrigo (2 m): con cielo despejado y sin viento el suelo y los bajos pueden
     * estar varios grados más fríos, por eso 3 °C ya es riesgo.
     */
    fun helada(
        horas: List<HoraPronosticoAgro>,
        ahoraS: Long,
        desfaseS: Int,
        cantidad: Int = 72
    ): PronosticoHelada? {
        val inicio = indiceActual(horas, ahoraS)
        val tramo = horas.subList(inicio, minOf(horas.size, inicio + cantidad)).filter { it.temperaturaC != null }
        if (tramo.isEmpty()) return null
        val iMin = tramo.indices.minBy { tramo[it].temperaturaC!! }
        val minima = tramo[iMin]
        val tMin = minima.temperaturaC!!
        val hoy = diaLocal(ahoraS, desfaseS)

        var desde = iMin
        var hasta = iMin
        if (tMin <= UMBRAL_RIESGO_HELADA_C) {
            while (desde > 0 && tramo[desde - 1].temperaturaC!! <= UMBRAL_RIESGO_HELADA_C &&
                tramo[desde].epochS - tramo[desde - 1].epochS == 3600L
            ) desde--
            while (hasta < tramo.lastIndex && tramo[hasta + 1].temperaturaC!! <= UMBRAL_RIESGO_HELADA_C &&
                tramo[hasta + 1].epochS - tramo[hasta].epochS == 3600L
            ) hasta++
        }
        val evento = if (tMin <= UMBRAL_RIESGO_HELADA_C) tramo.subList(desde, hasta + 1) else emptyList()
        // Una "noche" va del mediodía al mediodía siguiente: corremos 12 h para agruparla en un día
        val noches = tramo.filter { it.temperaturaC!! <= UMBRAL_RIESGO_HELADA_C }
            .map { diaLocal(it.epochS + 12 * 3600, desfaseS) }
            .distinct().size

        return PronosticoHelada(
            nivel = when {
                tMin <= 0.0 -> NivelHelada.HELADA
                tMin <= UMBRAL_RIESGO_HELADA_C -> NivelHelada.RIESGO
                else -> NivelHelada.SIN_RIESGO
            },
            minimaC = tMin,
            momentoMinimaS = minima.epochS,
            diaMinima = (diaLocal(minima.epochS, desfaseS) - hoy).toInt(),
            horaMinima = horaLocal(minima.epochS, desfaseS),
            horasRiesgo = evento.size,
            horasBajoCero = evento.count { it.temperaturaC!! <= 0.0 },
            desdeHora = evento.firstOrNull()?.let { horaLocal(it.epochS, desfaseS) },
            hastaHora = evento.lastOrNull()?.let { (horaLocal(it.epochS, desfaseS) + 1) % 24 },
            nochesConRiesgo = noches,
            horasAnalizadas = tramo.size,
            hoy = hoy
        )
    }

    /**
     * Lluvia frente a evapotranspiración de referencia (ET0, FAO-56) en los últimos [diasPasados]
     * días y en los próximos [diasProximos] (hoy incluido). ET0 es la demanda de un pasto de
     * referencia: un cultivo cerrado consume algo más y uno recién emergido bastante menos, y no
     * cuenta el agua guardada en el perfil ni la que escurre. Sirve como tendencia, no como lámina.
     */
    fun balance(
        dias: List<DiaBalance>,
        ahoraS: Long,
        desfaseS: Int,
        diasPasados: Int = 30,
        diasProximos: Int = 7
    ): BalanceHidrico? {
        val hoy = diaLocal(ahoraS, desfaseS)
        val iHoy = dias.indexOfFirst { diaLocal(it.epochS, desfaseS) >= hoy }
        if (iHoy <= 0) return null
        val pasados = dias.subList(maxOf(0, iHoy - diasPasados), iHoy)
        val proximos = dias.subList(iHoy, minOf(dias.size, iHoy + diasProximos))
        // Sin ET0 en la mayoría de los días no hay balance que mostrar
        if (pasados.count { it.et0Mm != null } < pasados.size / 2) return null
        val lluvia = pasados.sumOf { it.lluviaMm ?: 0.0 }
        val et0 = pasados.sumOf { it.et0Mm ?: 0.0 }
        val proporcion = if (et0 > 0) lluvia / et0 else 1.0
        val visibles = pasados + proximos
        return BalanceHidrico(
            diasPasados = pasados.size,
            lluviaPasadaMm = lluvia,
            et0PasadaMm = et0,
            lluviaUltimos7Mm = pasados.takeLast(7).sumOf { it.lluviaMm ?: 0.0 },
            diasProximos = proximos.size,
            lluviaProximaMm = proximos.sumOf { it.lluviaMm ?: 0.0 },
            et0ProximaMm = proximos.sumOf { it.et0Mm ?: 0.0 },
            estado = when {
                proporcion >= 1.0 -> EstadoHidrico.SIN_DEFICIT
                proporcion >= 0.5 -> EstadoHidrico.DEFICIT_MODERADO
                else -> EstadoHidrico.DEFICIT_MARCADO
            },
            dias = visibles,
            indiceHoy = pasados.size,
            hoy = hoy
        )
    }
}
