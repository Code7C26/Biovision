package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ktor.ServicioLocalidades
import com.example.data.repository.SugerenciaLocalidad
import com.example.data.remote.ktor.ServicioProvincias
import com.example.utils.EditorPoligono
import com.example.utils.GeometriaLote
import com.example.utils.EstadoEdicionPoligono
import com.example.web.datos.LoteWeb
import com.example.web.datos.LotesWeb
import com.example.web.ui.numero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Mapa base de la página Leaflet (mismos proveedores que Android, sin MapTiler: su clave quedaría expuesta). */
enum class MapaBaseWeb(val etiqueta: String, val proveedor: String, val etiquetas: Boolean) {
    SATELITE("Satélite", "esri", false),
    HIBRIDO("Satélite + rutas", "esri", true),
    CALLES("Calles (OSM)", "osm", false)
}

/** Llamada a window.GRANEX.<metodo>(...args) de mapa_gis.js, enviada por postMessage. */
data class ComandoMapa(val metodo: String, val args: JsonArray) {
    fun json(): String = buildJsonObject {
        put("metodo", metodo)
        put("args", args)
    }.toString()
}

/** Provincia del lote que se va a guardar, detectada por su ubicación (como DeteccionProvincia en Android). */
sealed interface DeteccionProvinciaWeb {
    data object Inactiva : DeteccionProvinciaWeb
    data object Buscando : DeteccionProvinciaWeb
    data class Detectada(val provincia: String) : DeteccionProvinciaWeb
    /** Sin red o sin resultado: el campo queda en blanco y editable, sin bloquear el guardado. */
    data class NoDisponible(val mensaje: String) : DeteccionProvinciaWeb
}

/** Lote en edición: [origen] null es uno nuevo; [estado] trae vértices, medidas y deshacer/rehacer. */
data class EdicionWeb(val origen: LoteWeb?, val estado: EstadoEdicionPoligono)

/**
 * Satélite web: mapa base, búsqueda y lotes del usuario (lista, selección, dibujo y edición) sobre
 * la misma página Leaflet de Android. Los lotes van a [lotes] (localStorage) y, con cuenta, se suben
 * a Supabase con [sincronizar] después de cada cambio (sin red quedan pendientes y se suben al volver).
 * @param lotes null si el navegador bloquea el almacenamiento: no se pueden guardar lotes.
 */
class SateliteWebViewModel(
    localidades: ServicioLocalidades,
    private val provincias: ServicioProvincias,
    private val lotes: LotesWeb?,
    private val sincronizar: suspend () -> Unit
) : ViewModel() {
    private val _mapaBase = MutableStateFlow(MapaBaseWeb.HIBRIDO)
    val mapaBase: StateFlow<MapaBaseWeb> = _mapaBase.asStateFlow()

    /** Centro del mapa informado por la página (evento "centro"), para mostrarlo y sesgar la búsqueda. */
    private val _centro = MutableStateFlow<Pair<Double, Double>?>(null)
    val centro: StateFlow<Pair<Double, Double>?> = _centro.asStateFlow()

    /**
     * Generación de la página del mapa: 0 = todavía no avisó "listo"; sube con cada "listo". Si el
     * iframe se recarga solo (el navegador recarga un iframe que se mueve en el DOM, por ejemplo al
     * cambiar de diseño) la generación nueva hace que la pantalla vuelva a mandar todo el estado.
     */
    private val _listo = MutableStateFlow(0)
    val listo: StateFlow<Int> = _listo.asStateFlow()

    /** Destino pendiente de "volar"; se consume al enviarlo (como DestinoMapa en Android). */
    private val _destino = MutableStateFlow<SugerenciaLocalidad?>(null)
    val destino: StateFlow<SugerenciaLocalidad?> = _destino.asStateFlow()

    val buscador = BuscadorLocalidades(localidades, viewModelScope) { _centro.value }

    // --- Lotes ---
    val puedeGuardarLotes: Boolean get() = lotes != null
    val listaLotes: StateFlow<List<LoteWeb>> = lotes?.lotes ?: MutableStateFlow(emptyList())
    val pendientes: StateFlow<Int> = lotes?.pendientes ?: MutableStateFlow(0)

    private val _seleccionado = MutableStateFlow<String?>(null)
    val seleccionado: StateFlow<String?> = _seleccionado.asStateFlow()

    private var editor: EditorPoligono? = null
    private val _edicion = MutableStateFlow<EdicionWeb?>(null)
    val edicion: StateFlow<EdicionWeb?> = _edicion.asStateFlow()

    /** Superficie mientras se arrastra un vértice (la página no redibuja hasta soltarlo). */
    private val _areaEnVivo = MutableStateFlow<Double?>(null)
    val areaEnVivo: StateFlow<Double?> = _areaEnVivo.asStateFlow()

    /** Diálogo "Guardar lote" abierto (también al cerrar el polígono tocando el primer vértice). */
    private val _dialogoGuardar = MutableStateFlow(false)
    val dialogoGuardar: StateFlow<Boolean> = _dialogoGuardar.asStateFlow()

    fun seleccionarLote(id: String?) {
        if (_edicion.value != null) return
        _seleccionado.value = id
    }

    fun nuevoLote() = empezarEdicion(null, emptyList())

    fun editarLote(lote: LoteWeb) = empezarEdicion(lote, lote.vertices)

    private fun empezarEdicion(origen: LoteWeb?, vertices: List<Pair<Double, Double>>) {
        if (lotes == null) return
        val nuevo = EditorPoligono(vertices)
        editor = nuevo
        _areaEnVivo.value = null
        _edicion.value = EdicionWeb(origen, nuevo.estado)
    }

    fun cancelarEdicion() {
        editor = null
        _areaEnVivo.value = null
        _dialogoGuardar.value = false
        _edicion.value = null
    }

    fun deshacer() = editar { it.deshacer() }
    fun rehacer() = editar { it.rehacer() }
    fun eliminarVerticeSeleccionado() = editar { it.eliminarSeleccionado() }
    fun limpiarVertices() = editar { it.limpiar() }

    private fun editar(cambio: (EditorPoligono) -> EstadoEdicionPoligono) {
        val e = _edicion.value ?: return
        val ed = editor ?: return
        _areaEnVivo.value = null
        _edicion.value = e.copy(estado = cambio(ed))
    }

    private val _deteccionProvincia = MutableStateFlow<DeteccionProvinciaWeb>(DeteccionProvinciaWeb.Inactiva)
    val deteccionProvincia: StateFlow<DeteccionProvinciaWeb> = _deteccionProvincia.asStateFlow()
    private var trabajoProvincia: Job? = null

    fun abrirDialogoGuardar() {
        val e = _edicion.value ?: return
        if (!e.estado.puedeGuardar) return
        _dialogoGuardar.value = true
        // Un lote editado ya tiene provincia: solo se busca para uno nuevo o si quedó vacía
        if (e.origen?.registro?.provincia.isNullOrBlank()) detectarProvincia(e.estado.vertices)
    }

    fun cerrarDialogoGuardar() {
        _dialogoGuardar.value = false
        trabajoProvincia?.cancel()
        _deteccionProvincia.value = DeteccionProvinciaWeb.Inactiva
    }

    /** Geocodificación inversa del centro del polígono, con tope de 10 s para no dejar el indicador girando. */
    private fun detectarProvincia(vertices: List<Pair<Double, Double>>) {
        val centro = GeometriaLote.centroide(vertices) ?: return
        trabajoProvincia?.cancel()
        trabajoProvincia = viewModelScope.launch {
            _deteccionProvincia.value = DeteccionProvinciaWeb.Buscando
            val resultado = withTimeoutOrNull(TIEMPO_MAX_PROVINCIA_MS) {
                runCatching { provincias.provinciaDe(centro.first, centro.second) }
            }
            _deteccionProvincia.value = when {
                resultado == null -> DeteccionProvinciaWeb.NoDisponible("La ubicación tardó demasiado")
                resultado.isFailure -> DeteccionProvinciaWeb.NoDisponible("Sin conexión para detectar la provincia")
                resultado.getOrNull() == null -> DeteccionProvinciaWeb.NoDisponible("No hay una provincia para esa ubicación")
                else -> DeteccionProvinciaWeb.Detectada(resultado.getOrThrow()!!)
            }
        }
    }

    /** Guarda el lote dibujado; false si falta el nombre o el polígono no es válido. */
    fun guardarLote(nombre: String, provincia: String): Boolean {
        val e = _edicion.value ?: return false
        val almacen = lotes ?: return false
        if (nombre.isBlank() || !e.estado.puedeGuardar) return false
        val registro = almacen.guardar(e.origen?.id, nombre, provincia, e.estado.vertices)
        cerrarDialogoGuardar()
        cancelarEdicion()
        _seleccionado.value = registro.id
        subirCambios()
        return true
    }

    fun eliminarLote(id: String) {
        val almacen = lotes ?: return
        almacen.eliminar(id)
        if (_seleccionado.value == id) _seleccionado.value = null
        subirCambios()
    }

    private fun subirCambios() {
        viewModelScope.launch { sincronizar() }
    }

    fun elegirMapaBase(base: MapaBaseWeb) {
        _mapaBase.value = base
    }

    fun irA(s: SugerenciaLocalidad) {
        _destino.value = s
        buscador.limpiar()
    }

    fun destinoEnviado() {
        _destino.value = null
    }

    /** Eventos de mapa_gis.js ({t: "listo" | "centro" | "toque" | ...}). */
    fun alMensajeMapa(json: String) {
        val o = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return
        when ((o["t"] as? JsonPrimitive)?.content) {
            "listo" -> _listo.value += 1
            "loteTocado" -> (o["id"] as? JsonPrimitive)?.content?.let(::seleccionarLote)
            "toque" -> {
                val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return
                val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return
                editar { it.agregar(lat, lon) }
            }
            "verticeTocado" -> {
                val i = o["i"]?.jsonPrimitive?.intOrNull ?: return
                val e = _edicion.value ?: return
                // Tocar el primer vértice con el polígono completo lo cierra: se pasa a guardar
                if (e.estado.cierraAlTocar(i)) abrirDialogoGuardar() else editar { it.seleccionar(i) }
            }
            "arrastrando" -> {
                val i = o["i"]?.jsonPrimitive?.intOrNull ?: return
                val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return
                val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return
                _areaEnVivo.value = editor?.areaProvisoria(i, lat, lon, o["ins"]?.jsonPrimitive?.booleanOrNull == true)
            }
            "verticeSoltado" -> {
                val i = o["i"]?.jsonPrimitive?.intOrNull ?: return
                val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return
                val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return
                val insercion = o["ins"]?.jsonPrimitive?.booleanOrNull == true
                editar { if (insercion) it.insertar(i, lat, lon) else it.mover(i, lat, lon) }
            }
            "centro" -> {
                val lat = o["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = o["lon"]?.jsonPrimitive?.doubleOrNull
                if (lat != null && lon != null) _centro.value = lat to lon
            }
        }
    }

    /** El iframe se recrea (cambio de sección): la página nueva vuelve a avisar "listo". */
    fun mapaDescartado() {
        _listo.value = 0
    }

    companion object {
        const val TIEMPO_MAX_PROVINCIA_MS = 10_000L

        fun comandoMapaBase(base: MapaBaseWeb) = ComandoMapa(
            "setMapaBase",
            buildJsonArray {
                add(JsonPrimitive(base.proveedor))
                add(JsonPrimitive(""))
                add(JsonPrimitive(base.etiquetas))
            }
        )

        /**
         * GRANEX.setLotes: contornos [[lat, lon]], nombre y superficie (los textos los pone la página
         * con textContent). El lote en edición se oculta para dibujar solo sus vértices.
         */
        fun comandoLotes(lotes: List<LoteWeb>, seleccionado: String?, editando: String?) = ComandoMapa(
            "setLotes",
            buildJsonArray {
                add(JsonPrimitive(buildJsonObject {
                    put("lotes", buildJsonArray {
                        lotes.forEach { l ->
                            add(buildJsonObject {
                                put("id", l.id)
                                put("nombre", l.nombre)
                                put("area", "${numero(l.registro.superficieHa, 1)} ha")
                                put("coords", coordenadas(l.vertices))
                            })
                        }
                    })
                    put("seleccionado", seleccionado?.let(::JsonPrimitive) ?: JsonNull)
                    put("editando", editando?.let(::JsonPrimitive) ?: JsonNull)
                }.toString()))
            }
        )

        /** GRANEX.setCapa: la capa del escaneo (CapaMapaEscaneo) o "null" para quitarla. */
        fun comandoCapa(json: String?) = ComandoMapa("setCapa", buildJsonArray { add(JsonPrimitive(json ?: "null")) })

        /** GRANEX.setEdicion: vértices del polígono en edición (modo toque, sin mira), o null para terminar. */
        fun comandoEdicion(estado: EstadoEdicionPoligono?) = ComandoMapa(
            "setEdicion",
            buildJsonArray {
                if (estado == null) {
                    add(JsonNull)
                } else {
                    add(JsonPrimitive(buildJsonObject {
                        put("vertices", coordenadas(estado.vertices))
                        put("seleccionado", estado.seleccionado?.let(::JsonPrimitive) ?: JsonNull)
                        put("mira", false)
                    }.toString()))
                }
            }
        )

        private fun coordenadas(vertices: List<Pair<Double, Double>>) = buildJsonArray {
            vertices.forEach { (lat, lon) -> add(buildJsonArray { add(JsonPrimitive(lat)); add(JsonPrimitive(lon)) }) }
        }

        fun comandoIrA(lat: Double, lon: Double) = ComandoMapa(
            "irA",
            buildJsonArray {
                add(JsonPrimitive(lat))
                add(JsonPrimitive(lon))
            }
        )
    }
}
