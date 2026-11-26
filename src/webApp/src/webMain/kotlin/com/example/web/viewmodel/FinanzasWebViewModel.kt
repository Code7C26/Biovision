package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ktor.CotizacionDolar
import com.example.data.remote.ktor.ServicioCotizaciones
import com.example.data.remote.ktor.ServicioFuturosA3
import com.example.utils.ContratoFuturo
import com.example.utils.FechasIso
import com.example.utils.FuturosA3
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.example.web.ui.horaActual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EstadoCotizaciones {
    data object Cargando : EstadoCotizaciones
    data class Ok(val dolares: List<CotizacionDolar>, val hora: String) : EstadoCotizaciones
    data class Error(val mensaje: String) : EstadoCotizaciones
}

/** Ajustes de futuros de A3 (por la función "publico" de GRANEX, porque el CEM no responde con CORS). */
sealed interface EstadoFuturos {
    data object Cargando : EstadoFuturos
    /** Build sin backend: no hay por dónde pedirlos. */
    data object SinServidor : EstadoFuturos
    data class Ok(val contratos: List<ContratoFuturo>) : EstadoFuturos
    data class Error(val mensaje: String) : EstadoFuturos
}

class FinanzasWebViewModel(
    private val servicio: ServicioCotizaciones,
    private val futuros: ServicioFuturosA3?
) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoCotizaciones>(EstadoCotizaciones.Cargando)
    val estado: StateFlow<EstadoCotizaciones> = _estado.asStateFlow()

    private val _futuros = MutableStateFlow<EstadoFuturos>(if (futuros == null) EstadoFuturos.SinServidor else EstadoFuturos.Cargando)
    val estadoFuturos: StateFlow<EstadoFuturos> = _futuros.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        cargarFuturos()
        viewModelScope.launch {
            _estado.value = EstadoCotizaciones.Cargando
            _estado.value = try {
                EstadoCotizaciones.Ok(conReintentos { servicio.dolares() }, horaActual())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                EstadoCotizaciones.Error("No se pudieron obtener las cotizaciones. Revisá la conexión.")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun cargarFuturos() {
        val servicioFuturos = futuros ?: return
        viewModelScope.launch {
            if (_futuros.value !is EstadoFuturos.Ok) _futuros.value = EstadoFuturos.Cargando
            _futuros.value = try {
                val hoy = FechasIso.deArgentina(Clock.System.now().toEpochMilliseconds())
                val contratos = FuturosA3.vigentes(conReintentos { servicioFuturos.cierres(hoy) })
                if (contratos.isEmpty()) EstadoFuturos.Error("A3 Mercados no publicó ajustes de soja, maíz o trigo en los últimos días.")
                else EstadoFuturos.Ok(contratos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Un reintento fallido no borra los ajustes que ya se ven
                _futuros.value as? EstadoFuturos.Ok
                    ?: EstadoFuturos.Error("No se pudieron leer los futuros de A3 Mercados. Revisá la conexión.")
            }
        }
    }
}
