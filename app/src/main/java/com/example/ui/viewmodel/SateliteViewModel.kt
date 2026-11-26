package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AgroSatRepository
import com.example.data.repository.DetectorLotes
import com.example.data.repository.LoteSatelital
import com.example.data.repository.SugerenciaLocalidad
import com.example.utils.GeometriaLote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SateliteUiState {
    object Loading : SateliteUiState
    data class Success(
        val lotes: List<LoteSatelital>,
        /** null mientras el usuario no tenga lotes: se muestra el mapa para dibujar el primero. */
        val loteSeleccionado: LoteSatelital?,
        val capaActiva: CapaSatelital
    ) : SateliteUiState
    data class Error(val message: String) : SateliteUiState
}

/** Qué se ve sobre la imagen base del mapa. El índice es el elegido en la tarjeta de escaneo. */
enum class CapaSatelital(val id: String, val nombre: String, val descripcion: String) {
    INDICE("indice", "Escaneo del lote", "Lo elegido en el escaneo Sentinel-2: índice, foto del día o relieve (10 m)."),
    RGB("rgb", "Solo mapa base", "Solo la imagen satelital de fondo del proveedor, sin el escaneo.")
}

enum class ProveedorMapa(val id: String, val nombre: String) {
    MAPTILER("maptiler", "Satélite MapTiler"),
    ESRI("esri", "Satélite Esri"),
    OSM("osm", "Calles OpenStreetMap")
}

data class MapaBaseState(
    val proveedor: ProveedorMapa,
    val etiquetas: Boolean = true,
    val maptilerDisponible: Boolean
)

/**
 * Lote que se está dibujando o editando. [revision] cambia con cada modificación confirmada y es
 * la señal para reenviar los vértices al mapa; arrastrar un vértice no la cambia (ver areaEnVivo).
 */
data class EdicionLote(
    val loteOrigen: LoteSatelital?,
    val vertices: List<Pair<Double, Double>>,
    val seleccionado: Int? = null,
    val modoMira: Boolean = false,
    val revision: Int = 0,
    val areaHa: Double = 0.0,
    val perimetroM: Double = 0.0,
    val autointerseccion: Boolean = false,
    val puedeDeshacer: Boolean = false,
    val puedeRehacer: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
    /** El contorno lo encontró la detección automática: la pantalla pide revisarlo. */
    val detectado: Boolean = false,
    /** El lote detectado sigue más allá del recuadro analizado y quedó cortado. */
    val detectadoCortado: Boolean = false
) {
    val puedeGuardar: Boolean get() = vertices.size >= 3 && !autointerseccion && !guardando
}

/** Detección automática de un lote tocando el mapa. */
sealed interface DeteccionLote {
    object Inactiva : DeteccionLote
    /** Esperando que el usuario toque dentro del lote. */
    object EsperandoToque : DeteccionLote
    data class Buscando(val lat: Double, val lon: Double) : DeteccionLote
    data class Fallo(val mensaje: String, val lat: Double?, val lon: Double?) : DeteccionLote
}

data class BusquedaUbicacionState(
    val buscando: Boolean = false,
    val error: String? = null
)

/** Provincia del lote que se va a guardar, detectada por su ubicación. */
sealed interface DeteccionProvincia {
    object Inactiva : DeteccionProvincia
    object Buscando : DeteccionProvincia
    data class Detectada(val provincia: String) : DeteccionProvincia
    /** Sin red o sin resultado: el campo queda en blanco y editable, sin bloquear el guardado. */
    data class NoDisponible(val mensaje: String) : DeteccionProvincia
}

/** Destino al que debe volar el mapa. El id distingue dos pedidos a las mismas coordenadas. */
data class DestinoMapa(
    val lat: Double,
    val lon: Double,
    val id: Long = System.nanoTime()
)

class SateliteViewModel(
    private val repository: AgroSatRepository,
    maptilerDisponible: Boolean,
    /** Detección automática de lotes por imágenes; null sin backend de imágenes (y en pruebas). */
    private val detector: DetectorLotes? = null,
    /** Fecha (yyyy-MM-dd) de la foto de Esri en un punto; null en pruebas (sin red). */
    private val fechaEsri: (suspend (Double, Double) -> String?)? = null
) : ViewModel() {

    private val selectedLoteId = MutableStateFlow<String?>(null)
    private val activeLayer = MutableStateFlow(CapaSatelital.INDICE)

    private val seleccion = combine(repository.lotes, selectedLoteId) { lotes, id ->
        lotes to (lotes.firstOrNull { it.id == id } ?: lotes.firstOrNull())
    }

    val uiState: StateFlow<SateliteUiState> = combine(seleccion, activeLayer) { (lotes, lote), capa ->
        // Sin lotes no es un error: la pantalla muestra el estado vacío y el mapa para dibujar
        SateliteUiState.Success(lotes, lote, capa) as SateliteUiState
    }
        .catch { emit(SateliteUiState.Error(it.localizedMessage ?: "Error al procesar índices satelitales")) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SateliteUiState.Loading)

    private val _mapaBase = MutableStateFlow(
        MapaBaseState(
            proveedor = if (maptilerDisponible) ProveedorMapa.MAPTILER else ProveedorMapa.ESRI,
            maptilerDisponible = maptilerDisponible
        )
    )
    val mapaBase: StateFlow<MapaBaseState> = _mapaBase.asStateFlow()

    private val _avisoMapa = MutableStateFlow<String?>(null)
    val avisoMapa: StateFlow<String?> = _avisoMapa.asStateFlow()

    private val _edicion = MutableStateFlow<EdicionLote?>(null)
    val edicion: StateFlow<EdicionLote?> = _edicion.asStateFlow()

    /** Superficie mientras se arrastra un vértice (se actualiza ~12 veces por segundo); null si no hay arrastre. */
    private val _areaEnVivo = MutableStateFlow<Double?>(null)
    val areaEnVivo: StateFlow<Double?> = _areaEnVivo.asStateFlow()

    private val pilaDeshacer = ArrayDeque<List<Pair<Double, Double>>>()
    private val pilaRehacer = ArrayDeque<List<Pair<Double, Double>>>()

    // Búsqueda de ubicación y destino del mapa (búsqueda o GPS)
    private val _busqueda = MutableStateFlow(BusquedaUbicacionState())
    val busqueda: StateFlow<BusquedaUbicacionState> = _busqueda.asStateFlow()

    private val _destinoMapa = MutableStateFlow<DestinoMapa?>(null)
    val destinoMapa: StateFlow<DestinoMapa?> = _destinoMapa.asStateFlow()

    private var busquedaJob: Job? = null

    private val _deteccionLote = MutableStateFlow<DeteccionLote>(DeteccionLote.Inactiva)
    val deteccionLote: StateFlow<DeteccionLote> = _deteccionLote.asStateFlow()
    private var deteccionJob: Job? = null

    private val _deteccionProvincia = MutableStateFlow<DeteccionProvincia>(DeteccionProvincia.Inactiva)
    val deteccionProvincia: StateFlow<DeteccionProvincia> = _deteccionProvincia.asStateFlow()
    private var provinciaJob: Job? = null

    /** Texto escrito y punto de sesgo (centro del mapa o GPS). */
    private val consulta = MutableStateFlow<Pair<String, Pair<Double, Double>?>>("" to null)

    private var sugerenciasAnteriores: List<SugerenciaLocalidad> = emptyList()

    // Una consulta por pausa de tipeo; un texto nuevo cancela la anterior
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val sugerencias: StateFlow<SugerenciasState> = consulta
        .debounce { (texto, _) -> if (texto.trim().length < MIN_LETRAS_SUGERENCIA) 0L else DEBOUNCE_SUGERENCIAS_MS }
        .distinctUntilChanged()
        .flatMapLatest { (texto, cerca) ->
            val limpio = texto.trim()
            if (limpio.length < MIN_LETRAS_SUGERENCIA || parsearCoordenadas(limpio) != null) {
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

    // --- Búsqueda y navegación del mapa ---

    fun buscarUbicacion(query: String) {
        val texto = query.trim()
        if (texto.isEmpty()) return
        cerrarSugerencias()
        parsearCoordenadas(texto)?.let { (lat, lon) ->
            _busqueda.value = BusquedaUbicacionState()
            _destinoMapa.value = DestinoMapa(lat, lon)
            return
        }
        busquedaJob?.cancel()
        busquedaJob = viewModelScope.launch {
            _busqueda.value = BusquedaUbicacionState(buscando = true)
            repository.geocodificar(texto)
                .onSuccess { (lat, lon) ->
                    _busqueda.value = BusquedaUbicacionState()
                    _destinoMapa.value = DestinoMapa(lat, lon)
                }
                .onFailure { err ->
                    _busqueda.value = BusquedaUbicacionState(error = err.message ?: "Error al buscar ubicación")
                }
        }
    }

    /** Cada cambio del texto del buscador; [cerca] es el centro del mapa (o la ubicación del dispositivo). */
    fun actualizarConsulta(texto: String, cerca: Pair<Double, Double>?) {
        consulta.value = texto to cerca
    }

    /** Elegir una sugerencia vuela el mapa hasta ella y cierra la lista. */
    fun elegirSugerencia(sugerencia: SugerenciaLocalidad) {
        busquedaJob?.cancel()
        consulta.value = "" to null
        _busqueda.value = BusquedaUbicacionState()
        _destinoMapa.value = DestinoMapa(sugerencia.lat, sugerencia.lon)
    }

    fun cerrarSugerencias() {
        consulta.value = "" to null
    }

    fun irAUbicacion(lat: Double, lon: Double) {
        _destinoMapa.value = DestinoMapa(lat, lon)
    }

    /** El mapa ya voló al destino: se limpia para no repetirlo al volver a la pantalla. */
    fun destinoMapaAplicado(destino: DestinoMapa) {
        _destinoMapa.compareAndSet(destino, null)
    }

    fun seleccionarLote(lote: LoteSatelital) = seleccionarLoteId(lote.id)

    fun seleccionarLoteId(id: String) {
        if (_edicion.value == null) selectedLoteId.value = id
    }

    fun cambiarCapa(capa: CapaSatelital) {
        activeLayer.value = capa
    }

    fun cambiarProveedor(proveedor: ProveedorMapa) {
        val actual = _mapaBase.value
        if (proveedor == ProveedorMapa.MAPTILER && !actual.maptilerDisponible) return
        _mapaBase.value = actual.copy(proveedor = proveedor)
        _avisoMapa.value = null
        ultimoCentro?.let { (lat, lon) -> centroMovido(lat, lon) }
    }

    // La foto de Esri es un mosaico de fechas distintas: se muestra la del lugar que se está viendo
    private val _fechaMapaBase = MutableStateFlow<String?>(null)
    /** Fecha (yyyy-MM-dd) de la foto de Esri en el centro del mapa; null con otro proveedor o sin dato. */
    val fechaMapaBase: StateFlow<String?> = _fechaMapaBase.asStateFlow()
    private var ultimoCentro: Pair<Double, Double>? = null
    private var trabajoFechaBase: Job? = null

    /** La página informa el centro cada vez que el mapa deja de moverse. */
    fun centroMovido(lat: Double, lon: Double) {
        ultimoCentro = lat to lon
        val consultar = fechaEsri ?: return
        trabajoFechaBase?.cancel()
        if (_mapaBase.value.proveedor != ProveedorMapa.ESRI) {
            _fechaMapaBase.value = null
            return
        }
        trabajoFechaBase = viewModelScope.launch {
            // Arrastrar el mapa informa varios centros seguidos: se consulta el último
            kotlinx.coroutines.delay(600)
            _fechaMapaBase.value = consultar(lat, lon)
        }
    }

    fun alternarEtiquetas() {
        _mapaBase.update { it.copy(etiquetas = !it.etiquetas) }
    }

    /** La página informa que MapTiler no entrega teselas (clave inválida, sin cuota o sin servicio). */
    fun proveedorFallido(id: String) {
        if (id != ProveedorMapa.MAPTILER.id || _mapaBase.value.proveedor != ProveedorMapa.MAPTILER) return
        _mapaBase.update { it.copy(proveedor = ProveedorMapa.ESRI) }
        _avisoMapa.value = "MapTiler no respondió (clave inválida o sin cuota). Se muestra la imagen de Esri."
        ultimoCentro?.let { (lat, lon) -> centroMovido(lat, lon) }
    }

    fun descartarAviso() {
        _avisoMapa.value = null
    }

    fun eliminarLote(lote: LoteSatelital) {
        if (!lote.editable) return
        viewModelScope.launch {
            repository.eliminarLote(lote)
            if (selectedLoteId.value == lote.id) selectedLoteId.value = null
        }
    }

    // --- Detección automática de lotes ---

    /** "Detectar lote": el próximo toque en el mapa busca el contorno del lote que hay ahí. */
    fun iniciarDeteccion() {
        val d = detector
        _deteccionLote.value = when {
            d == null -> DeteccionLote.Fallo("La detección automática necesita el servidor de imágenes de GRANEX.", null, null)
            !d.disponible -> DeteccionLote.Fallo(d.mensajeNoDisponible, null, null)
            else -> DeteccionLote.EsperandoToque
        }
    }

    fun cancelarDeteccion() {
        deteccionJob?.cancel()
        _deteccionLote.value = DeteccionLote.Inactiva
    }

    /** Toque en el mapa con la detección activa: busca el contorno y, si lo encuentra, lo abre en el editor. */
    fun detectarEn(lat: Double, lon: Double) {
        val d = detector ?: return
        if (!coordenadaValida(lat, lon)) return
        deteccionJob?.cancel()
        _deteccionLote.value = DeteccionLote.Buscando(lat, lon)
        deteccionJob = viewModelScope.launch {
            d.detectar(lat, lon).fold(
                onSuccess = { c ->
                    _deteccionLote.value = DeteccionLote.Inactiva
                    iniciarEdicion(null, c.vertices)
                    _edicion.value = _edicion.value?.copy(detectado = true, detectadoCortado = c.cortadoPorBorde)
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _deteccionLote.value = DeteccionLote.Fallo(e.message ?: "No se pudo detectar el lote.", lat, lon)
                }
            )
        }
    }

    fun reintentarDeteccion() {
        val f = _deteccionLote.value as? DeteccionLote.Fallo ?: return
        if (f.lat != null && f.lon != null) detectarEn(f.lat, f.lon) else iniciarDeteccion()
    }

    // --- Dibujo y edición de lotes ---

    fun iniciarNuevoLote() = iniciarEdicion(null, emptyList())

    fun editarLote(lote: LoteSatelital) = iniciarEdicion(lote, GeometriaLote.anilloAbierto(lote.coordenadas))

    private fun iniciarEdicion(origen: LoteSatelital?, vertices: List<Pair<Double, Double>>) {
        pilaDeshacer.clear()
        pilaRehacer.clear()
        _areaEnVivo.value = null
        _edicion.value = calcular(EdicionLote(loteOrigen = origen, vertices = vertices), vertices, null, 0)
    }

    fun cancelarEdicion() {
        pilaDeshacer.clear()
        pilaRehacer.clear()
        _areaEnVivo.value = null
        _edicion.value = null
    }

    fun agregarVertice(lat: Double, lon: Double) {
        val e = _edicion.value ?: return
        if (!coordenadaValida(lat, lon)) return
        aplicar(e.vertices + (lat to lon), seleccion = null)
    }

    fun moverVertice(indice: Int, lat: Double, lon: Double) {
        val e = _edicion.value ?: return
        if (indice !in e.vertices.indices || !coordenadaValida(lat, lon)) return
        aplicar(e.vertices.toMutableList().also { it[indice] = lat to lon }, seleccion = indice)
    }

    /** Inserta un vértice en [indice] (lo que antes estaba en esa posición pasa a la siguiente). */
    fun insertarVertice(indice: Int, lat: Double, lon: Double) {
        val e = _edicion.value ?: return
        if (indice !in 0..e.vertices.size || !coordenadaValida(lat, lon)) return
        aplicar(e.vertices.toMutableList().also { it.add(indice, lat to lon) }, seleccion = indice)
    }

    fun eliminarVerticeSeleccionado() {
        val e = _edicion.value ?: return
        val i = e.seleccionado ?: return
        if (i !in e.vertices.indices) return
        aplicar(e.vertices.toMutableList().also { it.removeAt(i) }, seleccion = null)
    }

    /** Tocar un vértice lo selecciona; tocar el ya seleccionado lo deselecciona. */
    fun seleccionarVertice(indice: Int) {
        val e = _edicion.value ?: return
        if (indice !in e.vertices.indices) return
        val nueva = if (e.seleccionado == indice) null else indice
        _edicion.value = e.copy(seleccionado = nueva, revision = e.revision + 1)
    }

    fun limpiarVertices() {
        val e = _edicion.value ?: return
        if (e.vertices.isNotEmpty()) aplicar(emptyList(), seleccion = null)
    }

    fun deshacer() {
        val e = _edicion.value ?: return
        val anterior = pilaDeshacer.removeLastOrNull() ?: return
        pilaRehacer.addLast(e.vertices)
        _edicion.value = calcular(e, anterior, null, e.revision + 1)
    }

    fun rehacer() {
        val e = _edicion.value ?: return
        val siguiente = pilaRehacer.removeLastOrNull() ?: return
        pilaDeshacer.addLast(e.vertices)
        _edicion.value = calcular(e, siguiente, null, e.revision + 1)
    }

    fun alternarModoMira() {
        val e = _edicion.value ?: return
        _edicion.value = e.copy(modoMira = !e.modoMira, revision = e.revision + 1)
    }

    /** Superficie provisoria mientras se arrastra un vértice o un punto intermedio. */
    fun previsualizarArrastre(indice: Int, lat: Double, lon: Double, insercion: Boolean) {
        val e = _edicion.value ?: return
        if (!coordenadaValida(lat, lon)) return
        val temporal = e.vertices.toMutableList()
        when {
            insercion && indice in 0..temporal.size -> temporal.add(indice, lat to lon)
            !insercion && indice in temporal.indices -> temporal[indice] = lat to lon
            else -> return
        }
        _areaEnVivo.value = GeometriaLote.areaHectareas(temporal)
    }

    /**
     * Al abrir el diálogo de guardado: busca la provincia del centroide del polígono (geocodificación
     * inversa). Con un tope de 10 s para no dejar el indicador girando sin red.
     */
    fun detectarProvincia() {
        val vertices = _edicion.value?.vertices ?: return
        val centro = GeometriaLote.centroide(vertices) ?: return
        provinciaJob?.cancel()
        provinciaJob = viewModelScope.launch {
            _deteccionProvincia.value = DeteccionProvincia.Buscando
            val resultado = withTimeoutOrNull(TIEMPO_MAX_PROVINCIA_MS) { repository.provinciaDe(centro.first, centro.second) }
            _deteccionProvincia.value = when {
                resultado == null -> DeteccionProvincia.NoDisponible("La ubicación tardó demasiado")
                resultado.isSuccess -> DeteccionProvincia.Detectada(resultado.getOrThrow())
                else -> DeteccionProvincia.NoDisponible(resultado.exceptionOrNull()?.message ?: "No se pudo detectar")
            }
        }
    }

    fun cerrarDeteccionProvincia() {
        provinciaJob?.cancel()
        _deteccionProvincia.value = DeteccionProvincia.Inactiva
    }

    fun guardarLote(nombre: String, provincia: String) {
        val e = _edicion.value ?: return
        if (!e.puedeGuardar || nombre.isBlank()) return
        _edicion.value = e.copy(guardando = true, error = null)
        viewModelScope.launch {
            try {
                val lote = repository.guardarLote(
                    idExistente = e.loteOrigen?.takeIf { it.editable }?.id,
                    nombre = nombre.trim(),
                    provincia = provincia.trim(),
                    coordenadas = e.vertices
                )
                cancelarEdicion()
                selectedLoteId.value = lote.id
            } catch (ex: Exception) {
                _edicion.value = _edicion.value?.copy(
                    guardando = false,
                    error = "No se pudo guardar el lote: ${ex.localizedMessage ?: "error desconocido"}"
                )
            }
        }
    }

    private fun aplicar(nuevos: List<Pair<Double, Double>>, seleccion: Int?) {
        val e = _edicion.value ?: return
        pilaDeshacer.addLast(e.vertices)
        if (pilaDeshacer.size > MAX_HISTORIAL) pilaDeshacer.removeFirst()
        pilaRehacer.clear()
        _areaEnVivo.value = null
        _edicion.value = calcular(e, nuevos, seleccion, e.revision + 1)
    }

    private fun calcular(base: EdicionLote, vertices: List<Pair<Double, Double>>, seleccion: Int?, revision: Int) =
        base.copy(
            vertices = vertices,
            seleccionado = seleccion?.takeIf { it in vertices.indices },
            revision = revision,
            areaHa = GeometriaLote.areaHectareas(vertices),
            perimetroM = GeometriaLote.perimetroMetros(vertices),
            autointerseccion = GeometriaLote.tieneAutointerseccion(vertices),
            puedeDeshacer = pilaDeshacer.isNotEmpty(),
            puedeRehacer = pilaRehacer.isNotEmpty(),
            error = null
        )

    private fun coordenadaValida(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun parsearCoordenadas(texto: String): Pair<Double, Double>? {
        val partes = texto.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.size != 2) return null
        val lat = partes[0].toDoubleOrNull() ?: return null
        val lon = partes[1].toDoubleOrNull() ?: return null
        return (lat to lon).takeIf { coordenadaValida(lat, lon) }
    }

    private companion object {
        const val MAX_HISTORIAL = 100
        const val TIEMPO_MAX_PROVINCIA_MS = 10_000L
        const val MIN_LETRAS_SUGERENCIA = 3
        const val DEBOUNCE_SUGERENCIAS_MS = 250L
    }
}
