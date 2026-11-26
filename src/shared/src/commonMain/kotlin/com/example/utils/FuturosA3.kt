package com.example.utils

import com.example.data.remote.ktor.CierreA3

/** Un contrato de futuro con su último ajuste publicado. */
data class ContratoFuturo(
    val simbolo: String,          // "SOJ.ROS/MAY27"
    val cultivo: String,          // "Soja", "Maíz", "Trigo"
    val posicion: String,         // "Mayo 2027"
    val fecha: String,            // rueda del ajuste, yyyy-MM-dd
    val ajusteUsd: Double,
    val variacionPct: Double?,    // contra el ajuste anterior
    val volumenLotes: Int,
    val interesAbierto: Int
)

/**
 * Elige qué mostrar de los cierres del CEM: de cada cultivo, la última rueda publicada y sus
 * [porCultivo] posiciones más cercanas con entrega en Rosario (`.ROS/`). Se descartan los contratos
 * mini y los de otros puertos (`SOJ.VISEC/…`), que también vienen en el mismo producto.
 */
object FuturosA3 {

    private val PATRON = Regex("""^(SOJ|MAI|TRI)\.ROS/([A-Z]{3})(\d{2})$""")
    private val CULTIVOS = listOf("SOJ" to "Soja", "MAI" to "Maíz", "TRI" to "Trigo")
    private val MESES = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
    private val NOMBRES_MES = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    fun vigentes(cierres: List<CierreA3>, porCultivo: Int = 3): List<ContratoFuturo> =
        CULTIVOS.flatMap { (codigo, nombre) ->
            val delCultivo = cierres.mapNotNull { c ->
                val m = PATRON.matchEntire(c.symbol) ?: return@mapNotNull null
                if (m.groupValues[1] != codigo) return@mapNotNull null
                val ajuste = c.settlement?.takeIf { it > 0.0 } ?: return@mapNotNull null
                val mes = MESES.indexOf(m.groupValues[2]).takeIf { it >= 0 } ?: return@mapNotNull null
                val anio = 2000 + m.groupValues[3].toInt()
                Triple(c, anio * 100 + mes + 1, ContratoFuturo(
                    simbolo = c.symbol,
                    cultivo = nombre,
                    posicion = "${NOMBRES_MES[mes]} $anio",
                    fecha = c.dateTime.take(10),
                    ajusteUsd = ajuste,
                    variacionPct = c.changePercent,
                    volumenLotes = c.volume?.toInt() ?: 0,
                    interesAbierto = c.openInterest?.toInt() ?: 0
                ))
            }
            val ultimaRueda = delCultivo.maxOfOrNull { it.third.fecha } ?: return@flatMap emptyList()
            delCultivo
                .filter { it.third.fecha == ultimaRueda }
                .distinctBy { it.third.simbolo }
                .sortedBy { it.second }
                .take(porCultivo)
                .map { it.third }
        }
}
