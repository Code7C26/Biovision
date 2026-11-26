package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.dao.AgroDao
import com.example.data.local.entity.FavoriteCityEntity
import com.example.data.remote.model.AgroClimateDashboard
import com.example.data.remote.model.AlertasClima
import com.example.data.remote.model.evaluarAlertas
import com.example.data.repository.SugerenciaLocalidad
import com.example.data.repository.TipoLocalidad
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WeatherRepository
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val dashboard: AgroClimateDashboard) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

/** Capa elegida: define el overlay del mapa Windy y la métrica del pronóstico detallado. */
enum class CapaClima(val etiqueta: String, val overlayWindy: String) {
    TEMPERATURA("Temperatura", "temp"),
    LLUVIA("Precipitaciones", "rain"),
    VIENTO("Viento", "wind")
}

/**
 * Dónde debe mostrar el mapa Windy. Cambia apenas se elige una localidad, antes de que llegue el
 * pronóstico, y el mapa vuela hasta ahí. [id] distingue dos pedidos al mismo lugar.
 */
data class VistaMapaClima(
    val lat: Double,
    val lon: Double,
    val zoom: Int,
    val nombre: String?,
    val id: Long = System.nanoTime()
)

class WeatherViewModel(
    private val repository: WeatherRepository = WeatherRepository(),
    private val agroDao: AgroDao? = null,
    private val context: Context? = null
) : ViewModel() {

    private val preferencesRepository = context?.let { UserPreferencesRepository(it) }

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _capaSeleccionada = MutableStateFlow(CapaClima.LLUVIA)
    val capaSeleccionada: StateFlow<CapaClima> = _capaSeleccionada.asStateFlow()

    private val _alertas = MutableStateFlow(AlertasClima())
    val alertas: StateFlow<AlertasClima> = _alertas.asStateFlow()

    /** Texto escrito y punto de sesgo (ubicación del dispositivo o localidad en pantalla). */
    private val consulta = MutableStateFlow<Pair<String, Pair<Double, Double>?>>("" to null)

    private var sugerenciasAnteriores: List<SugerenciaLocalidad> = emptyList()

    // Una consulta por pausa de tipeo; un texto nuevo cancela la anterior
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val sugerencias: StateFlow<SugerenciasState> = consulta
        .debounce { (texto, _) -> if (texto.trim().length < MIN_LETRAS_SUGERENCIA) 0L else DEBOUNCE_SUGERENCIAS_MS }
        .distinctUntilChanged()
        .flatMapLatest { (texto, cerca) ->
            val limpio = texto.trim()
            if (limpio.length < MIN_LETRAS_SUGERENCIA) {
                flowOf(SugerenciasState())
            } else flow {
                // Las sugerencias anteriores siguen a la vista mientras llegan las nuevas (sin parpadeo)
                emit(SugerenciasState(items = sugerenciasAnteriores, cargando = true))
                emit(
                    repository.sugerirLocalidades(limpio, cerca).fold(
                        onSuccess = { SugerenciasState(items = it, error = if (it.isEmpty()) "Sin localidades con ese nombre" else null) },
                        onFailure = { SugerenciasState(error = it.message) }
                    )
                )
            }
        }
        .onEach { sugerenciasAnteriores = it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SugerenciasState())

    // Solo una carga a la vez: elegir otra ciudad cancela el pedido de la anterior
    private var cargaJob: Job? = null

    // Default: Rosario, Santa Fe (-32.9468, -60.6393)
    private var currentLat = -32.9468
    private var currentLon = -60.6393
    private var currentCityName: String? = "Rosario"
    private var ultimaActualizacionNotificada: Long? = null
    private var ubicacionInicialAplicada = false

    private val _vistaMapa = MutableStateFlow(VistaMapaClima(currentLat, currentLon, ZOOM_REGIONAL, currentCityName))
    val vistaMapa: StateFlow<VistaMapaClima> = _vistaMapa.asStateFlow()

    init {
        viewModelScope.launch {
            val lastCity = withContext(Dispatchers.IO) { preferencesRepository?.getLastSelectedCity() }
            if (lastCity != null) {
                currentLat = lastCity.lat
                currentLon = lastCity.lon
                currentCityName = lastCity.name
            }
            // Al abrir, el mapa vuelve con el acercamiento de esa localidad (o el de la ubicación GPS)
            val zoom = lastCity?.let { c ->
                if (c.name == NOMBRE_UBICACION) ZOOM_UBICACION
                else preferencesRepository?.getZoomLocalidad(c.name) ?: repository.tipoConocido(c.name)?.zoom(ZOOM_MAX_WINDY_EMBED)
            }
            // La ubicación GPS pudo haber llegado mientras se leían las preferencias
            if (cargaJob == null) cargarClimaActual(currentLat, currentLon, currentCityName, zoom = zoom)
        }
    }

    val favoriteCities: StateFlow<List<FavoriteCityEntity>> = (agroDao?.getFavoriteCities() ?: flowOf(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFavorite(cityName: String, lat: Double, lon: Double, isFav: Boolean) {
        val dao = agroDao ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (isFav) {
                dao.deleteFavoriteCity(cityName)
            } else {
                dao.insertFavoriteCity(FavoriteCityEntity(cityName, lat, lon))
            }
        }
    }

    fun seleccionarCapa(capa: CapaClima) {
        _capaSeleccionada.value = capa
    }

    /**
     * Carga el pronóstico de (lat, lon) y mueve el mapa en el acto, sin esperar los datos.
     * [zoom] null conserva el del mapa si la ubicación es la misma (refrescos) y usa el regional si
     * es otra (GPS, favoritos); [volar] fuerza el vuelo aunque sea el mismo lugar (el mapa pudo moverse).
     */
    fun cargarClimaActual(
        lat: Double,
        lon: Double,
        cityName: String? = null,
        forceRefresh: Boolean = false,
        zoom: Int? = null,
        volar: Boolean = false
    ) {
        val vista = _vistaMapa.value
        val mismoLugar = vista.lat == lat && vista.lon == lon
        val nuevoZoom = zoom ?: if (mismoLugar) vista.zoom else ZOOM_REGIONAL
        if (volar || !mismoLugar || nuevoZoom != vista.zoom) {
            _vistaMapa.value = VistaMapaClima(lat, lon, nuevoZoom, cityName)
        }
        currentLat = lat
        currentLon = lon
        currentCityName = cityName
        // También la ubicación GPS: el chequeo de alertas en segundo plano usa la última ubicación
        viewModelScope.launch {
            preferencesRepository?.saveLastSelectedCity(cityName ?: NOMBRE_UBICACION, lat, lon)
        }

        cargaJob?.cancel()
        cargaJob = viewModelScope.launch {
            // Un refresco en segundo plano de la misma ubicación no tapa la pantalla con el indicador de carga
            val actual = _uiState.value
            val mismaUbicacion = actual is WeatherUiState.Success &&
                actual.dashboard.lat == lat && actual.dashboard.lon == lon
            if (forceRefresh || !mismaUbicacion) {
                _uiState.value = WeatherUiState.Loading
            }

            repository.getAgroClimateData(context, lat, lon, cityName, forceRefresh)
                .onSuccess { procesarDatos(it) }
                .onFailure { err ->
                    _uiState.value = WeatherUiState.Error(err.message ?: "Error al cargar datos agrometeorológicos")
                }
        }
    }

    fun reintentar() {
        cargarClimaActual(currentLat, currentLon, currentCityName, forceRefresh = true)
    }

    /**
     * Refresca mientras la pantalla está visible (se llama dentro de repeatOnLifecycle): al volver
     * de segundo plano actualiza solo si los datos vencieron, y nunca consulta con la app cerrada.
     */
    suspend fun mantenerActualizado() {
        while (true) {
            val intervalo = repository.intervaloActualizacion(context)
            val estado = _uiState.value
            val edad = (estado as? WeatherUiState.Success)?.let { System.currentTimeMillis() - it.dashboard.lastUpdated }
            val vencido = when (estado) {
                is WeatherUiState.Success -> edad!! >= intervalo
                is WeatherUiState.Error -> true
                WeatherUiState.Loading -> false
            }
            if (vencido && cargaJob?.isActive != true) {
                cargarClimaActual(currentLat, currentLon, currentCityName)
                delay(intervalo)
            } else {
                delay((intervalo - (edad ?: 0L)).coerceAtLeast(60_000L))
            }
        }
    }

    /** Cada cambio del texto del buscador; [cerca] prioriza las localidades próximas a ese punto. */
    fun buscarCiudades(texto: String, cerca: Pair<Double, Double>?) {
        consulta.value = texto to cerca
    }

    fun cerrarSugerencias() {
        consulta.value = "" to null
    }

    /**
     * Elegir una sugerencia mueve el mapa (con zoom según el tamaño de la localidad: más cerca en
     * pueblos, para que se lea su nombre) y carga su pronóstico al mismo tiempo.
     */
    fun seleccionarCiudad(localidad: SugerenciaLocalidad) {
        cerrarSugerencias()
        val zoom = localidad.tipo.zoom(ZOOM_MAX_WINDY_EMBED)
        // Se recuerda para volar con el mismo acercamiento si después se la elige desde favoritas
        viewModelScope.launch { preferencesRepository?.guardarZoomLocalidad(localidad.nombre, zoom) }
        cargarClimaActual(
            localidad.lat, localidad.lon, localidad.nombre,
            zoom = zoom,
            volar = true
        )
    }

    /**
     * Favorita: el mapa vuela con el zoom recordado de cuando se la buscó, el de las localidades
     * conocidas o, si no hay, el de una ciudad.
     */
    fun seleccionarFavorita(ciudad: FavoriteCityEntity) {
        viewModelScope.launch {
            val zoom = preferencesRepository?.getZoomLocalidad(ciudad.name)
                ?: repository.tipoConocido(ciudad.name)?.zoom(ZOOM_MAX_WINDY_EMBED)
                ?: ZOOM_FAVORITA
            cargarClimaActual(ciudad.lat, ciudad.lon, ciudad.name, zoom = zoom, volar = true)
        }
    }

    /**
     * Ubicación del dispositivo (botón de GPS): el mapa vuela y se acerca como a una ciudad, aunque
     * ya estuviera ahí. Antes quedaba en el zoom regional y parecía que no se movía.
     */
    fun usarUbicacionActual(lat: Double, lon: Double) {
        cargarClimaActual(lat, lon, zoom = ZOOM_UBICACION, volar = true)
    }

    /**
     * Ubicación GPS obtenida al abrir la pantalla: solo se aplica la primera vez en la vida del
     * ViewModel, para no recargar el clima (y pisar la ciudad elegida) en cada cambio de pestaña.
     */
    fun usarUbicacionInicial(lat: Double, lon: Double) {
        if (ubicacionInicialAplicada) return
        ubicacionInicialAplicada = true
        usarUbicacionActual(lat, lon)
    }

    private fun procesarDatos(dashboard: AgroClimateDashboard) {
        _uiState.value = WeatherUiState.Success(dashboard)
        val umbralHelada = context?.let { WeatherNotificationService.getFrostTempThreshold(it).toDouble() } ?: 3.0
        _alertas.value = evaluarAlertas(dashboard, umbralHelada)

        // El caché devuelve el mismo lastUpdated: solo se evalúan notificaciones con datos nuevos
        if (dashboard.lastUpdated == ultimaActualizacionNotificada) return
        ultimaActualizacionNotificada = dashboard.lastUpdated

        val ctx = context ?: return
        viewModelScope.launch(Dispatchers.IO) {
            WeatherNotificationService.checkAndTriggerNotifications(ctx, dashboard)
        }
    }

    companion object {
        /** Nombre con que se guarda y se muestra la ubicación del dispositivo. */
        const val NOMBRE_UBICACION = "Ubicación actual"
        /** Zoom de una ubicación cargada sin zoom ni tipo conocido: la región, para ver el radar. */
        const val ZOOM_REGIONAL = 8
        /** El embed de Windy no acerca más que 11. */
        const val ZOOM_MAX_WINDY_EMBED = 11
        /** Favorita sin tamaño conocido: el de una ciudad. */
        val ZOOM_FAVORITA = TipoLocalidad.CIUDAD.zoom(ZOOM_MAX_WINDY_EMBED)
        /** Ubicación del dispositivo: como una ciudad, se ve el área urbana con su nombre. */
        val ZOOM_UBICACION = TipoLocalidad.CIUDAD.zoom(ZOOM_MAX_WINDY_EMBED)
        private const val MIN_LETRAS_SUGERENCIA = 3
        private const val DEBOUNCE_SUGERENCIAS_MS = 250L
    }
}
