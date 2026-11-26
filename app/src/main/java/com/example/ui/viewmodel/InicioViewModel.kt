package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.model.PizarraResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Precio de pizarra de un grano para la pantalla de Inicio. */
data class PrecioInicio(val grano: String, val usdTn: Double, val variacionPct: Double?, val arsTn: Double? = null)

sealed interface EstadoPreciosInicio {
    data object Cargando : EstadoPreciosInicio
    /** [fecha] es la de la pizarra (yyyy-MM-dd). */
    data class Listo(val fecha: String, val precios: List<PrecioInicio>) : EstadoPreciosInicio
    data class Error(val mensaje: String) : EstadoPreciosInicio
}

/**
 * Rediseño: lo que la pantalla de Inicio necesita y ninguna otra pantalla carga junto, los precios
 * de pizarra de hoy de los granos principales (granos.ar, la misma fuente que usa Mercado para la
 * rueda del día). El clima, los lotes, el dólar y las noticias salen de sus propios ViewModels.
 */
class InicioViewModel(
    private val leerPizarra: suspend () -> PizarraResponse = { RetrofitNetworkClient.granosApi.getPizarraHoy() }
) : ViewModel() {

    private val _precios = MutableStateFlow<EstadoPreciosInicio>(EstadoPreciosInicio.Cargando)
    val precios: StateFlow<EstadoPreciosInicio> = _precios.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            if (_precios.value !is EstadoPreciosInicio.Listo) _precios.value = EstadoPreciosInicio.Cargando
            _precios.value = try {
                val r = leerPizarra()
                val precios = GRANOS.mapNotNull { (nombre, clave) ->
                    r.granos[clave]?.usd_tn?.takeIf { it > 0.0 }?.let { usd ->
                        PrecioInicio(nombre, usd, r.granos[clave]?.variacion_pct_vs_rueda_anterior, r.granos[clave]?.ars_tn)
                    }
                }
                if (precios.isEmpty()) EstadoPreciosInicio.Error("La pizarra de hoy todavía no tiene precios.")
                else EstadoPreciosInicio.Listo(r.fecha, precios)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GranexInicio", "No se pudo leer la pizarra de hoy", e)
                // Si ya había precios, se dejan: es mejor un dato de hace un rato que nada
                (_precios.value as? EstadoPreciosInicio.Listo) ?: EstadoPreciosInicio.Error("Sin conexión con la pizarra.")
            }
        }
    }

    companion object {
        /** Nombre visible → clave de granos.ar. */
        val GRANOS = listOf("Soja" to "soja", "Maíz" to "maiz", "Trigo" to "trigo", "Girasol" to "girasol", "Sorgo" to "sorgo")
    }
}
