package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.api.CurrentCropTracker
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.model.CotizacionHistorica
import com.example.data.repository.FinancialRepository
import com.example.data.repository.RosarioMarketRepository
import com.example.data.repository.SerieHistorica
import com.example.utils.AgregacionPrecios
import com.example.utils.FilaHistorial
import com.example.utils.Granularidad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RangoTiempo(val etiqueta: String, val granularidad: Granularidad) {
    SIETE_DIAS("7 días", Granularidad.DIARIA),
    TREINTA_DIAS("30 días", Granularidad.TRES_DIAS),
    TRES_MESES("3 meses", Granularidad.SEMANAL)
}

/** Estado de la serie histórica del cultivo elegido. */
sealed interface EstadoSerie {
    object Cargando : EstadoSerie
    data class Lista(val obtenida: Long, val desactualizada: Boolean, val ruedas: Int) : EstadoSerie
    data class Error(val mensaje: String) : EstadoSerie
}

data class MercadoUiState(
    val rangoSeleccionado: RangoTiempo = RangoTiempo.TREINTA_DIAS,
    /** Puntos ya agregados según el rango: diarios, cada 3 días o semanales. */
    val puntosGrafico: List<CotizacionHistorica> = emptyList(),
    /** Filas de la tabla, de la más reciente a la más antigua: días, semanas o meses. */
    val filasTabla: List<FilaHistorial> = emptyList(),
    val estado: EstadoSerie = EstadoSerie.Cargando
)

class MercadoViewModel(
    private val repository: RosarioMarketRepository,
    private val financialRepository: FinancialRepository
) : ViewModel() {

    private val _selectedCultivo = MutableStateFlow("Soja")
    val selectedCultivo: StateFlow<String> = _selectedCultivo.asStateFlow()

    val cultivosDisponibles = CULTIVOS

    private val _selectedRango = MutableStateFlow(RangoTiempo.TREINTA_DIAS)

    private val _selectedInterval = MutableStateFlow(RangoTiempo.TREINTA_DIAS.etiqueta)
    val selectedInterval: StateFlow<String> = _selectedInterval.asStateFlow()

    val intervals = RangoTiempo.entries.map { it.etiqueta }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Dólar BNA oficial comprador, para la calculadora de rendimiento. */
    private val _selectedDolarValue = MutableStateFlow(1040.0)
    val selectedDolarValue: StateFlow<Double> = _selectedDolarValue.asStateFlow()

    private val _serie = MutableStateFlow<List<CotizacionHistorica>>(emptyList())
    private val _estado = MutableStateFlow<EstadoSerie>(EstadoSerie.Cargando)

    val uiState: StateFlow<MercadoUiState> = combine(_serie, _selectedRango, _estado) { serie, rango, estado ->
        val agregada = AgregacionPrecios.agregar(serie, rango.granularidad)
        MercadoUiState(
            rangoSeleccionado = rango,
            puntosGrafico = agregada.puntos,
            filasTabla = agregada.filas,
            estado = estado
        )
    }
        // La agregación (ordenar, agrupar por semana o mes) corre fuera del hilo principal
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MercadoUiState())

    private var carga: Job? = null

    init {
        fetchDolarRates()
        fetchGranosData("Soja")
    }

    fun seleccionarCultivo(cultivo: String) {
        if (cultivo in cultivosDisponibles) {
            _selectedCultivo.value = cultivo
            _errorMessage.value = null
            fetchGranosData(cultivo)
        }
    }

    fun seleccionarRango(rango: RangoTiempo) {
        _selectedRango.value = rango
        _selectedInterval.value = rango.etiqueta
    }

    fun fetchDolarRates() {
        viewModelScope.launch(Dispatchers.IO) {
            financialRepository.obtenerDolares()
                .onSuccess { (divisas, _) -> divisas.find { it.casa == "oficial" }?.let { _selectedDolarValue.value = it.compra } }
                .onFailure { Log.e(TAG, "No se pudo obtener el dólar oficial", it) }
        }
    }

    /** Carga (o recarga, con [forzar]) el historial real del cultivo y le suma la pizarra de hoy. */
    fun fetchGranosData(cultivo: String, forzar: Boolean = false) {
        carga?.cancel()
        carga = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            // Al cambiar de cultivo no se muestran los precios del anterior
            if (_serie.value.isEmpty() || CurrentCropTracker.selectedCrop != cultivo) {
                _serie.value = emptyList()
                _estado.value = EstadoSerie.Cargando
            }
            CurrentCropTracker.selectedCrop = cultivo
            try {
                repository.obtenerHistorial(cultivo, forzar)
                    .onSuccess { historial -> publicar(historial, pizarraDeHoy(cultivo)) }
                    .onFailure { e ->
                        _serie.value = emptyList()
                        _estado.value = EstadoSerie.Error(e.message ?: "No se pudo obtener el historial de precios.")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun publicar(historial: SerieHistorica, hoy: CotizacionHistorica?) {
        var serie = historial.cotizaciones
        // La pizarra del día (granos.ar) suele estar antes que en el histórico de la Cámara Arbitral
        if (hoy != null && serie.none { it.fecha >= hoy.fecha }) serie = serie + hoy
        // Variación diaria en el último registro, para la tarjeta resumen y los chips
        if (serie.size >= 2) {
            val ultimo = serie.last()
            val anterior = serie[serie.size - 2]
            serie = serie.dropLast(1) + ultimo.copy(variacionPorcentaje = (ultimo.precioUsd - anterior.precioUsd) / anterior.precioUsd * 100)
        }
        Log.d(TAG, "Serie publicada: ${serie.size} ruedas, ${serie.firstOrNull()?.fecha} → ${serie.lastOrNull()?.fecha}")
        _serie.value = serie
        _estado.value = EstadoSerie.Lista(historial.obtenida, historial.desactualizada, serie.size)
        if (historial.desactualizada) {
            _errorMessage.value = "Sin conexión: se muestran los precios guardados. Tocá actualizar para reintentar."
        }
    }

    /** Precio de pizarra del día según granos.ar; null si no responde (el historial alcanza igual). */
    private suspend fun pizarraDeHoy(cultivo: String): CotizacionHistorica? = try {
        val respuesta = RetrofitNetworkClient.granosApi.getPizarraHoy()
        val clave = if (cultivo.lowercase() in listOf("maíz", "maiz")) "maiz" else cultivo.lowercase()
        val grano = respuesta.granos[clave]
        val usd = grano?.usd_tn
        val ars = grano?.ars_tn
        if (usd == null || ars == null || usd <= 0.0 || ars <= 0.0) {
            Log.e(TAG, "Pizarra de hoy sin precio para $clave (fecha ${respuesta.fecha})")
            null
        } else {
            CotizacionHistorica(respuesta.fecha, usd, ars, puerto = "Pizarra Rosario (granos.ar)")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo leer la pizarra de hoy de granos.ar", e)
        null
    }

    // Lo cargado en la calculadora de margen, por grano: sobrevive a cerrar la hoja y a rotar
    private val calculadoras = mutableMapOf<String, Map<String, String>>()

    fun valoresCalculadora(cultivo: String): Map<String, String> = calculadoras[cultivo].orEmpty()

    fun guardarCampoCalculadora(cultivo: String, clave: String, valor: String) {
        calculadoras[cultivo] = valoresCalculadora(cultivo) + (clave to valor)
    }

    fun limpiarCalculadora(cultivo: String) {
        calculadoras.remove(cultivo)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "GranexMercado"

        /**
         * Granos con pizarra diaria en la Cámara Arbitral de Rosario (códigos en RosarioMarketRepository).
         * El sorgo no tiene futuros en MATba-ROFEX: su serie es solo la pizarra.
         */
        val CULTIVOS = listOf("Soja", "Maíz", "Trigo", "Sorgo", "Girasol")
    }
}
