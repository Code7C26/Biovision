package com.example.utils

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * Reemplazos multiplataforma de APIs de la JVM (Math, SimpleDateFormat/Calendar, String.format)
 * para el código común que usan Android y la web.
 */

/** Igual que Math.toRadians: multiplica por la misma constante, así los resultados no cambian. */
fun radianes(grados: Double): Double = grados * (PI / 180.0)

/** Igual que Math.toDegrees. */
fun grados(radianes: Double): Double = radianes * (180.0 / PI)

/** Fechas "yyyy-MM-dd" (las series de precios y satelitales las guardan así). */
object FechasIso {
    fun sumarDias(fecha: String, dias: Int): String =
        LocalDate.parse(fecha.take(10)).plus(DatePeriod(days = dias)).toString()

    fun diasEntre(desde: String, hasta: String): Int =
        LocalDate.parse(desde.take(10)).daysUntil(LocalDate.parse(hasta.take(10)))

    /** 1 = lunes … 7 = domingo (ISO). */
    fun diaDeSemana(fecha: String): Int = LocalDate.parse(fecha.take(10)).dayOfWeek.ordinal + 1

    fun diaDelAnio(fecha: String): Int = LocalDate.parse(fecha.take(10)).dayOfYear

    /** Fecha del día [dia] (1..366) del año [anio]. */
    fun desdeDiaDelAnio(anio: Int, dia: Int): String =
        LocalDate(anio, 1, 1).plus(DatePeriod(days = dia - 1)).toString()

    /**
     * Fecha de Argentina para el instante [epochMs]. Argentina está en UTC−3 todo el año (sin horario de
     * verano), así no hace falta la base de husos horarios, que en la web pesa varios cientos de KB.
     */
    fun deArgentina(epochMs: Long): String =
        LocalDate.fromEpochDays((epochMs - 3 * 3_600_000L).floorDiv(86_400_000L).toInt()).toString()
}

/**
 * Número con [decimales] fijos y coma decimal, como String.format("%.Nf") en es-AR
 * (redondeo hacia arriba en el medio). Sin separador de miles.
 */
fun formatoDecimal(valor: Double, decimales: Int): String {
    if (valor.isNaN()) return "NaN"
    val factor = 10.0.pow(decimales)
    val escalado = (abs(valor) * factor).roundToLong()
    val entero = escalado / factor.toLong()
    val signo = if (valor < 0 && escalado != 0L) "-" else ""
    if (decimales == 0) return "$signo$entero"
    val fraccion = (escalado % factor.toLong()).toString().padStart(decimales, '0')
    return "$signo$entero,$fraccion"
}
