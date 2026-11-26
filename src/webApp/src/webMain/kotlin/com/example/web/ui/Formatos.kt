package com.example.web.ui

import com.example.utils.FechasIso
import com.example.utils.formatoDecimal
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Número en formato argentino para mostrar: punto de miles y coma decimal ("1.550,50", "−3,2"). */
fun numero(valor: Double, decimales: Int = 0): String {
    val texto = formatoDecimal(valor, decimales)
    val negativo = texto.startsWith("-")
    val sinSigno = texto.removePrefix("-")
    val entero = sinSigno.substringBefore(',')
    val fraccion = sinSigno.substringAfter(',', "")
    val conMiles = entero.reversed().chunked(3).joinToString(".").reversed()
    // Signo menos (U+2212): mismo ancho que "+" con cifras tabulares, así las columnas quedan alineadas
    return (if (negativo) "−" else "") + conMiles + if (fraccion.isEmpty()) "" else ",$fraccion"
}

/** Espacio no separable: el signo nunca queda solo al final de un renglón ("$" / "1.550,00"). */
private const val NBSP = ' '

fun pesos(valor: Double, decimales: Int = 0) = "$$NBSP${numero(valor, decimales)}"

fun dolares(valor: Double, decimales: Int = 0) = "US$$NBSP${numero(valor, decimales)}"

/** Variación con signo: "+1,25 %" / "-0,40 %". */
fun variacion(pct: Double, decimales: Int = 2) = (if (pct > 0) "+" else "") + numero(pct, decimales) + " %"

/** Coordenada con punto decimal para URLs ("-32.94682"). */
fun coordenadaUrl(valor: Double) = formatoDecimal(valor, 5).replace(',', '.')

/** Hora local actual "hh:mm" para los chips de "Actualizado". */
@OptIn(ExperimentalTime::class)
fun horaActual(): String {
    val ahora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ahora.hour.toString().padStart(2, '0')}:${ahora.minute.toString().padStart(2, '0')}"
}

private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** "2026-10-25" → "Dom 25/10". */
fun diaCorto(fechaIso: String): String {
    val dia = DIAS.getOrNull(FechasIso.diaDeSemana(fechaIso) - 1) ?: ""
    return "$dia ${fechaIso.substring(8, 10)}/${fechaIso.substring(5, 7)}"
}
