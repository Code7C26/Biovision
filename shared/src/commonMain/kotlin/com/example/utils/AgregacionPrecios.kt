package com.example.utils

import com.example.data.remote.model.CotizacionHistorica

/** Densidad del gráfico y agrupación de la tabla según el rango temporal elegido. */
enum class Granularidad(val dias: Int, val leyendaGrafico: String) {
    DIARIA(7, "Cierre de cada rueda"),
    TRES_DIAS(30, "Un punto cada 3 días (cierre del tramo)"),
    SEMANAL(92, "Cierre semanal"),
    /** Un año (la web lo arma con las 365 ruedas de granos.ar): igual que SEMANAL, con más semanas. */
    ANUAL(365, "Cierre semanal")
}

/** Fila de la tabla: un día, una semana o un mes. */
data class FilaHistorial(
    val titulo: String,          // "Lun 14/07", "Semana 07/07–11/07", "Julio 2026"
    val detalle: String?,        // rango de fechas o cantidad de ruedas; null en filas diarias
    val cierreUsd: Double,
    val cierreArs: Double,
    val minUsd: Double,
    val maxUsd: Double,
    val promedioUsd: Double,
    val variacionPct: Double?,   // contra la fila anterior (rueda, semana o mes)
    val ruedas: Int
)

data class SerieAgregada(
    val puntos: List<CotizacionHistorica>,
    val filas: List<FilaHistorial>       // de la más reciente a la más antigua
)

object AgregacionPrecios {

    fun agregar(serie: List<CotizacionHistorica>, granularidad: Granularidad): SerieAgregada {
        val ordenada = serie.filter { it.precioUsd > 0.0 && it.fecha.length >= 10 }.sortedBy { it.fecha }
        if (ordenada.isEmpty()) return SerieAgregada(emptyList(), emptyList())

        // Ventana por fecha de calendario (antes era por cantidad de registros: 7 ruedas ≠ 7 días)
        val desde = sumarDias(ordenada.last().fecha, -(granularidad.dias - 1))
        val ventana = ordenada.filter { it.fecha >= desde }.ifEmpty { listOf(ordenada.last()) }

        return when (granularidad) {
            Granularidad.DIARIA -> SerieAgregada(
                puntos = ventana,
                filas = filasDiarias(ventana, ordenada)
            )
            Granularidad.TRES_DIAS -> SerieAgregada(
                puntos = cierres(ventana) { diasEntre(desde, it.fecha) / 3 },
                filas = resumir(ventana, ordenada, ::lunesDe) { inicio -> "Semana del ${corta(inicio)}" }
            )
            Granularidad.SEMANAL, Granularidad.ANUAL -> SerieAgregada(
                puntos = cierres(ventana, ::lunesDe),
                filas = resumir(ventana, ordenada, { it.fecha.substring(0, 7) }) { inicio -> nombreMes(inicio) }
            )
        }
    }

    /** Un punto por grupo: el cierre (último registro) con la fecha del cierre y el tramo en "puerto". */
    private fun cierres(ventana: List<CotizacionHistorica>, clave: (CotizacionHistorica) -> Any): List<CotizacionHistorica> =
        ventana.groupBy(clave).values.map { grupo ->
            val ultimo = grupo.last()
            val tramo = if (grupo.size == 1) corta(ultimo.fecha) else "${corta(grupo.first().fecha)}–${corta(ultimo.fecha)}"
            ultimo.copy(puerto = "Cierre del tramo $tramo (${grupo.size} ruedas)")
        }

    private fun filasDiarias(ventana: List<CotizacionHistorica>, completa: List<CotizacionHistorica>): List<FilaHistorial> =
        ventana.map { c ->
            val anterior = completa.lastOrNull { it.fecha < c.fecha }
            FilaHistorial(
                titulo = "${diaSemana(c.fecha)} ${corta(c.fecha)}",
                detalle = null,
                cierreUsd = c.precioUsd,
                cierreArs = c.precioArs,
                minUsd = c.precioUsd,
                maxUsd = c.precioUsd,
                promedioUsd = c.precioUsd,
                variacionPct = anterior?.let { variacion(it.precioUsd, c.precioUsd) },
                ruedas = 1
            )
        }.reversed()

    private fun resumir(
        ventana: List<CotizacionHistorica>,
        completa: List<CotizacionHistorica>,
        clave: (CotizacionHistorica) -> String,
        titulo: (inicio: String) -> String
    ): List<FilaHistorial> {
        val grupos = ventana.groupBy(clave).values.toList()
        return grupos.map { grupo ->
            val primero = grupo.first()
            val ultimo = grupo.last()
            // Variación del período: cierre contra el cierre del período anterior (aunque quede fuera de la ventana)
            val cierreAnterior = completa.lastOrNull { it.fecha < primero.fecha }
            FilaHistorial(
                titulo = titulo(primero.fecha),
                detalle = (if (grupo.size == 1) corta(primero.fecha) else "${corta(primero.fecha)} al ${corta(ultimo.fecha)}") +
                    " · ${grupo.size} ${if (grupo.size == 1) "rueda" else "ruedas"}",
                cierreUsd = ultimo.precioUsd,
                cierreArs = ultimo.precioArs,
                minUsd = grupo.minOf { it.precioUsd },
                maxUsd = grupo.maxOf { it.precioUsd },
                promedioUsd = grupo.map { it.precioUsd }.average(),
                variacionPct = cierreAnterior?.let { variacion(it.precioUsd, ultimo.precioUsd) },
                ruedas = grupo.size
            )
        }.reversed()
    }

    private fun variacion(antes: Double, despues: Double) = (despues - antes) / antes * 100.0

    // --- Fechas yyyy-MM-dd ---

    fun sumarDias(fecha: String, dias: Int): String = FechasIso.sumarDias(fecha, dias)

    private fun diasEntre(desde: String, hasta: String): Int = FechasIso.diasEntre(desde, hasta)

    /** Lunes de la semana de la cotización (clave de agrupación semanal). */
    private fun lunesDe(c: CotizacionHistorica): String =
        FechasIso.sumarDias(c.fecha, -(FechasIso.diaDeSemana(c.fecha) - 1))

    private fun corta(fecha: String) = "${fecha.substring(8, 10)}/${fecha.substring(5, 7)}"

    private fun diaSemana(fecha: String) =
        listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")[FechasIso.diaDeSemana(fecha) - 1]

    private fun nombreMes(fecha: String): String {
        val meses = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        return "${meses[fecha.substring(5, 7).toInt() - 1]} ${fecha.substring(0, 4)}"
    }
}
