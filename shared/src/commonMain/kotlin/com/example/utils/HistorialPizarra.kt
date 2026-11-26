package com.example.utils

import com.example.data.remote.ktor.DolarDelDia
import com.example.data.remote.ktor.RuedaPizarra
import com.example.data.remote.model.CotizacionHistorica

/**
 * Serie histórica de un grano a partir de la pizarra de granos.ar (ARS/t) y el dólar mayorista
 * comprador de cada día, con el mismo criterio que RosarioMarketRepository en Android: si un día no
 * tiene dólar (feriado cambiario), se usa el último anterior. Las ruedas sin precio o sin ningún dólar
 * previo se descartan; nunca se inventa un valor.
 */
object HistorialPizarra {

    const val PUERTO = "Puerto de Rosario (Cámara Arbitral, vía granos.ar)"

    fun serie(ruedas: List<RuedaPizarra>, clave: String, dolar: List<DolarDelDia>): List<CotizacionHistorica> {
        val cambio = dolar
            .mapNotNull { d -> d.compra?.takeIf { it > 0.0 }?.let { d.fecha.take(10) to it } }
            .sortedBy { it.first }
        if (cambio.isEmpty()) return emptyList()
        return ruedas
            .filter { it.fecha.length >= 10 }
            .sortedBy { it.fecha }
            .mapNotNull { r ->
                val ars = r.precio(clave)?.takeIf { it > 0.0 } ?: return@mapNotNull null
                val fecha = r.fecha.take(10)
                val tc = ultimoHasta(cambio, fecha) ?: return@mapNotNull null
                CotizacionHistorica(fecha = fecha, precioUsd = ars / tc, precioArs = ars, puerto = PUERTO)
            }
    }

    /** Valor de la última fecha ≤ [fecha] (búsqueda binaria sobre la lista ordenada). */
    private fun ultimoHasta(cambio: List<Pair<String, Double>>, fecha: String): Double? {
        var bajo = 0
        var alto = cambio.lastIndex
        var hallado: Double? = null
        while (bajo <= alto) {
            val medio = (bajo + alto) ushr 1
            if (cambio[medio].first <= fecha) {
                hallado = cambio[medio].second
                bajo = medio + 1
            } else {
                alto = medio - 1
            }
        }
        return hallado
    }
}
