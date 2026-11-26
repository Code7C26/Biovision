package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.ImagenSentinelRemota
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.utils.AnalisisRelieve
import com.example.utils.ComparacionEscaneo
import com.example.utils.EscaneoLote
import com.example.utils.ModoEscala
import com.example.utils.RelieveLote
import com.example.utils.ResumenEscaneo
import com.example.utils.ResumenRelieve
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Qué muestra el mapa del escaneo. */
enum class VistaEscaneo(val nombre: String) {
    INDICE("Índice"),
    FOTO("Foto del día"),
    RELIEVE("Relieve")
}

/** Escaneo Sentinel-2 (10 m) del lote elegido en Satélite. */
data class EstadoEscaneo(
    val lote: LoteSatelital,
    val disponible: Boolean,
    val mensajeSinConfigurar: String,
    val escenas: Carga<List<EscenaSentinel>> = Carga.Cargando,
    val fecha: String? = null,
    /** null = todavía no se pidió (solo se baja lo que muestra la vista elegida). */
    val imagen: Carga<ImagenIndices>? = null,
    val indice: IndiceVegetacion = IndiceVegetacion.NDVI,
    val modo: ModoEscala = ModoEscala.RELATIVA,
    val resumen: ResumenEscaneo? = null,
    /** Fecha anterior para comparar; null = sin comparación. */
    val fechaComparar: String? = null,
    val imagenComparar: Carga<ImagenIndices>? = null,
    val comparacion: ComparacionEscaneo? = null,
    val vista: VistaEscaneo = VistaEscaneo.INDICE,
    /** Foto en color real de [fecha]. */
    val foto: Carga<ImagenSentinelRemota>? = null,
    /** El relieve no depende de la fecha: se baja una vez por lote. */
    val relieve: Carga<RelieveLote>? = null,
    val resumenRelieve: ResumenRelieve? = null
) {
    val imagenLista: ImagenIndices? get() = (imagen as? Carga.Lista)?.valor

    /** Fechas con el lote mayormente despejado, la más reciente primero. */
    val escenasUtiles: List<EscenaSentinel>
        get() = (escenas as? Carga.Lista)?.valor?.let { EscaneoLote.escenasUtiles(it) }.orEmpty()

    /** Fechas despejadas según el filtro de nubes pero con una caída de un día (posible neblina). */
    val fechasDudosas: Set<String> get() = EscaneoLote.fechasDudosas(escenasUtiles, indice)
}

class EscaneoViewModel(private val fuente: FuenteSentinel) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoEscaneo?>(null)
    val estado: StateFlow<EstadoEscaneo?> = _estado.asStateFlow()

    // Imágenes ya bajadas por lote (id + contorno) y fecha: cambiar de índice, de modo o volver a una
    // fecha no gasta otra consulta del cupo diario
    private val cache = LinkedHashMap<String, ImagenIndices>()
    private val fotos = LinkedHashMap<String, ImagenSentinelRemota>()
    private val relieves = LinkedHashMap<String, Pair<RelieveLote, ResumenRelieve?>>()
    private var trabajoEscenas: Job? = null
    private var trabajoImagen: Job? = null
    private var trabajoComparar: Job? = null
    private var trabajoFoto: Job? = null
    private var trabajoRelieve: Job? = null

    /** Lo llama la pantalla cuando cambia el lote elegido (o su contorno). */
    fun abrir(lote: LoteSatelital?) {
        val actual = _estado.value
        if (lote == null) {
            _estado.value = null
            return
        }
        if (actual != null && actual.lote == lote && actual.disponible == fuente.configurada()) return
        trabajoEscenas?.cancel()
        trabajoImagen?.cancel()
        trabajoComparar?.cancel()
        trabajoFoto?.cancel()
        trabajoRelieve?.cancel()
        val disponible = fuente.configurada()
        _estado.value = EstadoEscaneo(
            lote = lote,
            disponible = disponible,
            mensajeSinConfigurar = fuente.mensajeSinConfigurar,
            escenas = if (disponible) Carga.Cargando else Carga.Fallo(fuente.mensajeSinConfigurar),
            indice = actual?.indice ?: IndiceVegetacion.NDVI,
            modo = actual?.modo ?: ModoEscala.RELATIVA,
            vista = actual?.vista ?: VistaEscaneo.INDICE
        )
        if (disponible) {
            cargarEscenas()
            asegurarVista()
        }
    }

    /** Después de iniciar sesión o si falló la red. */
    fun reintentar() {
        val e = _estado.value ?: return
        _estado.value = null
        abrir(e.lote)
    }

    private fun cargarEscenas() {
        val e = _estado.value ?: return
        trabajoEscenas = viewModelScope.launch {
            val resultado = fuente.escenas(e.lote, DIAS_HISTORIAL)
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else actual.copy(escenas = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudieron obtener las fechas.") }))
            }
            // Se abre la fecha útil más reciente (nunca es dudosa: hace falta la siguiente para juzgarla)
            _estado.value?.escenasUtiles?.firstOrNull()?.let { elegirFecha(it.fecha) }
        }
    }

    fun elegirFecha(fecha: String) {
        val e = _estado.value ?: return
        if (e.fecha == fecha) {
            // La misma fecha: solo se reintenta lo que haya fallado
            asegurarVista()
            return
        }
        // Comparar con la misma fecha no tiene sentido: esa comparación se quita
        val mismaQueComparada = e.fechaComparar == fecha
        if (mismaQueComparada) trabajoComparar?.cancel()
        trabajoImagen?.cancel()
        trabajoFoto?.cancel()
        _estado.value = e.copy(
            fecha = fecha,
            imagen = null,
            resumen = null,
            foto = null,
            fechaComparar = if (mismaQueComparada) null else e.fechaComparar,
            imagenComparar = if (mismaQueComparada) null else e.imagenComparar,
            comparacion = null
        )
        asegurarVista()
    }

    fun elegirVista(vista: VistaEscaneo) {
        _estado.update { it?.copy(vista = vista) }
        asegurarVista()
    }

    fun elegirIndice(indice: IndiceVegetacion) {
        _estado.update { it?.copy(indice = indice) }
        recalcular()
    }

    fun elegirModo(modo: ModoEscala) {
        _estado.update { it?.copy(modo = modo) }
    }

    /** null quita la comparación. */
    fun compararCon(fecha: String?) {
        val e = _estado.value ?: return
        trabajoComparar?.cancel()
        if (fecha == null || fecha == e.fecha) {
            _estado.value = e.copy(fechaComparar = null, imagenComparar = null, comparacion = null)
            return
        }
        _estado.value = e.copy(fechaComparar = fecha, imagenComparar = Carga.Cargando, comparacion = null)
        trabajoComparar = viewModelScope.launch {
            val resultado = descargar(e.lote, fecha)
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fechaComparar != fecha) actual
                else actual.copy(imagenComparar = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo descargar la imagen.") }))
            }
            recalcular()
        }
    }

    /** Baja lo que necesita la vista elegida y todavía no está (o falló). */
    private fun asegurarVista() {
        val e = _estado.value ?: return
        if (!e.disponible) return
        fun falta(c: Carga<*>?) = c == null || c is Carga.Fallo
        when (e.vista) {
            VistaEscaneo.INDICE -> if (e.fecha != null) {
                if (falta(e.imagen)) cargarImagen(e.fecha)
                // La foto de esa fecha es el fondo del mapa alrededor del lote: la del proveedor de
                // mapas puede tener más de un año y no coincide con lo que muestra el índice
                if (falta(e.foto)) cargarFoto(e.fecha)
            }
            VistaEscaneo.FOTO -> if (e.fecha != null && falta(e.foto)) cargarFoto(e.fecha)
            VistaEscaneo.RELIEVE -> if (falta(e.relieve)) cargarRelieve()
        }
    }

    private fun cargarImagen(fecha: String) {
        val e = _estado.value ?: return
        _estado.value = e.copy(imagen = Carga.Cargando, resumen = null)
        trabajoImagen?.cancel()
        trabajoImagen = viewModelScope.launch {
            val resultado = descargar(e.lote, fecha)
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fecha != fecha) actual
                else actual.copy(imagen = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo descargar la imagen.") }))
            }
            recalcular()
        }
    }

    private fun cargarFoto(fecha: String) {
        val e = _estado.value ?: return
        val clave = claveCache(e.lote, fecha)
        fotos[clave]?.let {
            _estado.value = e.copy(foto = Carga.Lista(it))
            return
        }
        _estado.value = e.copy(foto = Carga.Cargando)
        trabajoFoto?.cancel()
        trabajoFoto = viewModelScope.launch {
            val resultado = fuente.colorReal(zonaFoto(e.lote), fecha).onSuccess { foto ->
                fotos[clave] = foto
                while (fotos.size > MAX_FOTOS) fotos.remove(fotos.keys.first())
            }
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fecha != fecha) actual
                else actual.copy(foto = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo descargar la foto.") }))
            }
        }
    }

    private fun cargarRelieve() {
        val e = _estado.value ?: return
        val clave = claveLote(e.lote)
        relieves[clave]?.let { (relieve, resumen) ->
            _estado.value = e.copy(relieve = Carga.Lista(relieve), resumenRelieve = resumen)
            return
        }
        _estado.value = e.copy(relieve = Carga.Cargando, resumenRelieve = null)
        trabajoRelieve?.cancel()
        trabajoRelieve = viewModelScope.launch {
            val resultado = fuente.relieve(e.lote)
            val resumen = resultado.getOrNull()?.let { r ->
                withContext(Dispatchers.Default) { AnalisisRelieve.resumir(r, e.lote.coordenadas) }
            }
            resultado.onSuccess { r ->
                relieves[clave] = r to resumen
                while (relieves.size > MAX_RELIEVES) relieves.remove(relieves.keys.first())
            }
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else actual.copy(
                    relieve = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo obtener el relieve.") }),
                    resumenRelieve = resumen
                )
            }
        }
    }

    private suspend fun descargar(lote: LoteSatelital, fecha: String): Result<ImagenIndices> {
        val clave = claveCache(lote, fecha)
        cache[clave]?.let { return Result.success(it) }
        return fuente.imagen(lote, fecha).onSuccess { imagen ->
            cache[clave] = imagen
            while (cache.size > MAX_CACHE) cache.remove(cache.keys.first())
        }
    }

    /** Resumen y comparación del índice elegido (cálculo en segundo plano: miles de píxeles). */
    private fun recalcular() {
        val e = _estado.value ?: return
        val imagen = e.imagenLista ?: return
        val anterior = (e.imagenComparar as? Carga.Lista)?.valor
        viewModelScope.launch {
            val (resumen, comparacion) = withContext(Dispatchers.Default) {
                val contorno = e.lote.coordenadas
                val r = EscaneoLote.resumir(imagen, e.indice, contorno)
                // La comparación siempre va de la fecha más vieja a la más nueva
                val c = anterior?.let {
                    if (it.fecha <= imagen.fecha) EscaneoLote.comparar(it, imagen, e.indice, contorno)
                    else EscaneoLote.comparar(imagen, it, e.indice, contorno)
                }
                r to c
            }
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fecha != e.fecha || actual.indice != e.indice) actual
                else actual.copy(resumen = resumen, comparacion = comparacion)
            }
        }
    }

    /**
     * La foto del día cubre la zona y no solo el lote: el lote con [MARGEN_FOTO_M] por lado (el
     * servidor le suma un 20 %), así también es el fondo del mapa alrededor. Sigue siendo un pedido.
     */
    private fun zonaFoto(lote: LoteSatelital): LoteSatelital {
        val lats = lote.coordenadas.map { it.first }
        val lons = lote.coordenadas.map { it.second }
        val dLat = MARGEN_FOTO_M / METROS_LAT
        val dLon = MARGEN_FOTO_M / (METROS_LAT * kotlin.math.cos(Math.toRadians((lats.min() + lats.max()) / 2)))
        val norte = lats.max() + dLat
        val sur = lats.min() - dLat
        val oeste = lons.min() - dLon
        val este = lons.max() + dLon
        return lote.copy(coordenadas = listOf(norte to oeste, norte to este, sur to este, sur to oeste), geojson = "")
    }

    private fun claveLote(lote: LoteSatelital) = "${lote.id}|${lote.coordenadas.hashCode()}"

    private fun claveCache(lote: LoteSatelital, fecha: String) = "${claveLote(lote)}|$fecha"

    companion object {
        /** Historial de fechas: una campaña (Sentinel-2 pasa cada 2 o 3 días). */
        const val DIAS_HISTORIAL = 180
        private const val MAX_CACHE = 12
        private const val MAX_FOTOS = 6
        private const val MAX_RELIEVES = 4
        private const val MARGEN_FOTO_M = 1200.0
        private const val METROS_LAT = 110_950.0
    }
}
