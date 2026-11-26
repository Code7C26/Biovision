package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.ImagenSentinelRemota
import com.example.data.remote.supabase.SentinelGranex
import com.example.utils.AnalisisRelieve
import com.example.utils.CapaMapaEscaneo
import com.example.utils.ComparacionEscaneo
import com.example.utils.EscaneoLote
import com.example.utils.ModoEscala
import com.example.utils.RelieveLote
import com.example.utils.ResumenEscaneo
import com.example.utils.ResumenRelieve
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.web.datos.LoteWeb
import com.example.web.plataforma.decodificarPng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Algo que se baja del servidor: cargando, listo o con el error para mostrar. */
sealed interface CargaWeb<out T> {
    data object Cargando : CargaWeb<Nothing>
    data class Lista<T>(val valor: T) : CargaWeb<T>
    data class Fallo(val mensaje: String) : CargaWeb<Nothing>
}

/** Qué muestra el mapa del escaneo (como VistaEscaneo en Android). */
enum class VistaEscaneoWeb(val nombre: String) {
    INDICE("Índice"),
    FOTO("Foto del día"),
    RELIEVE("Relieve")
}

/** Escaneo Sentinel-2 (10 m) del lote elegido en Satélite. */
data class EstadoEscaneoWeb(
    val lote: LoteWeb,
    val escenas: CargaWeb<List<EscenaSentinel>> = CargaWeb.Cargando,
    val fecha: String? = null,
    /** null = todavía no se pidió (solo se baja lo que muestra la vista elegida). */
    val imagen: CargaWeb<ImagenIndices>? = null,
    val indice: IndiceVegetacion = IndiceVegetacion.NDVI,
    val modo: ModoEscala = ModoEscala.RELATIVA,
    val resumen: ResumenEscaneo? = null,
    val vista: VistaEscaneoWeb = VistaEscaneoWeb.INDICE,
    val foto: CargaWeb<ImagenSentinelRemota>? = null,
    /** El relieve no depende de la fecha: se baja una vez por lote. */
    val relieve: CargaWeb<RelieveLote>? = null,
    val resumenRelieve: ResumenRelieve? = null,
    /** Fecha para comparar con la elegida; null = sin comparación. */
    val fechaComparar: String? = null,
    val imagenComparar: CargaWeb<ImagenIndices>? = null,
    val comparacion: ComparacionEscaneo? = null
) {
    /** Fechas con el lote mayormente despejado, la más reciente primero. */
    val escenasUtiles: List<EscenaSentinel>
        get() = (escenas as? CargaWeb.Lista)?.valor?.let { EscaneoLote.escenasUtiles(it) }.orEmpty()

    /** Despejadas según el filtro de nubes pero con una caída de un día (posible neblina). */
    val fechasDudosas: Set<String> get() = EscaneoLote.fechasDudosas(escenasUtiles, indice)

    /** Lo que se dibuja en el mapa (GRANEX.setCapa); null si todavía no hay nada que mostrar. */
    fun capaJson(): String? = when (vista) {
        VistaEscaneoWeb.INDICE -> {
            val img = (imagen as? CargaWeb.Lista)?.valor
            val escala = resumen?.escala(modo)
            val c = comparacion
            when {
                img != null && c != null -> CapaMapaEscaneo.cambio(c, img, lote.vertices)
                img != null && escala != null -> CapaMapaEscaneo.indice(img, indice, escala, modo, lote.vertices)
                else -> null
            }
        }
        VistaEscaneoWeb.FOTO -> (foto as? CargaWeb.Lista)?.valor?.let { CapaMapaEscaneo.foto(it.fecha, it.png, it.oeste, it.sur, it.este, it.norte) }
        VistaEscaneoWeb.RELIEVE -> {
            val r = (relieve as? CargaWeb.Lista)?.valor
            val resumen = resumenRelieve
            if (r != null && resumen != null) CapaMapaEscaneo.relieve(r, resumen, lote.vertices) else null
        }
    }
}

/**
 * Escaneo del lote en la web, sobre las mismas piezas compartidas que EscaneoViewModel en Android:
 * [SentinelGranex] (Edge Function, hace falta sesión), [EscaneoLote] y [AnalisisRelieve].
 * Cada pedido cuenta para el límite diario del usuario, así que lo bajado se guarda en memoria por
 * lote (id + contorno) y fecha: cambiar de índice, de modo o volver a una fecha no gasta otro.
 *
 * @param sentinel null sin backend configurado.
 * @param haySesion la Edge Function rechaza los pedidos sin sesión: se avisa antes de intentar.
 */
class EscaneoWebViewModel(private val sentinel: SentinelGranex?, private val haySesion: () -> Boolean) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoEscaneoWeb?>(null)
    val estado: StateFlow<EstadoEscaneoWeb?> = _estado.asStateFlow()

    /** Por qué no se puede escanear (sin servidor o sin sesión); null si se puede. */
    private val _sinAcceso = MutableStateFlow<String?>(null)
    val sinAcceso: StateFlow<String?> = _sinAcceso.asStateFlow()

    private val escenasCache = LinkedHashMap<String, List<EscenaSentinel>>()
    private val imagenes = LinkedHashMap<String, ImagenIndices>()
    private val fotos = LinkedHashMap<String, ImagenSentinelRemota>()
    private val relieves = LinkedHashMap<String, Pair<RelieveLote, ResumenRelieve?>>()
    private var trabajoEscenas: Job? = null
    private var trabajoImagen: Job? = null
    private var trabajoFoto: Job? = null
    private var trabajoRelieve: Job? = null
    private var trabajoComparar: Job? = null

    /** Lo llama la pantalla cuando cambia el lote elegido (o su contorno), o al iniciar sesión. */
    fun abrir(lote: LoteWeb?) {
        val actual = _estado.value
        if (lote == null) {
            cancelarTodo()
            _estado.value = null
            return
        }
        val acceso = motivoSinAcceso()
        if (actual != null && actual.lote == lote && acceso == _sinAcceso.value) return
        cancelarTodo()
        _sinAcceso.value = acceso
        _estado.value = EstadoEscaneoWeb(
            lote = lote,
            escenas = if (acceso == null) CargaWeb.Cargando else CargaWeb.Fallo(acceso),
            indice = actual?.indice ?: IndiceVegetacion.NDVI,
            modo = actual?.modo ?: ModoEscala.RELATIVA,
            vista = actual?.vista ?: VistaEscaneoWeb.INDICE
        )
        if (acceso == null) {
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

    fun elegirFecha(fecha: String) {
        val e = _estado.value ?: return
        if (e.fecha == fecha) {
            asegurarVista()
            return
        }
        trabajoImagen?.cancel()
        trabajoFoto?.cancel()
        val mismaQueComparada = e.fechaComparar == fecha
        if (mismaQueComparada) trabajoComparar?.cancel()
        _estado.value = e.copy(
            fecha = fecha, imagen = null, resumen = null, foto = null,
            fechaComparar = if (mismaQueComparada) null else e.fechaComparar,
            imagenComparar = if (mismaQueComparada) null else e.imagenComparar,
            comparacion = null
        )
        asegurarVista()
    }

    fun elegirVista(vista: VistaEscaneoWeb) {
        _estado.update { it?.copy(vista = vista) }
        asegurarVista()
    }

    fun elegirIndice(indice: IndiceVegetacion) {
        _estado.update { it?.copy(indice = indice) }
        recalcular()
    }

    /** null quita la comparación. Cada fecha nueva cuenta un pedido (las ya bajadas, no). */
    fun compararCon(fecha: String?) {
        val e = _estado.value ?: return
        trabajoComparar?.cancel()
        if (fecha == null || fecha == e.fecha) {
            _estado.value = e.copy(fechaComparar = null, imagenComparar = null, comparacion = null)
            return
        }
        _estado.value = e.copy(fechaComparar = fecha, imagenComparar = CargaWeb.Cargando, comparacion = null)
        trabajoComparar = viewModelScope.launch {
            val resultado = runCatching { bajarImagen(e.lote, fecha) }
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fechaComparar != fecha) actual
                else actual.copy(imagenComparar = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudo bajar la imagen para comparar.")) }))
            }
            recalcular()
        }
    }

    fun elegirModo(modo: ModoEscala) {
        _estado.update { it?.copy(modo = modo) }
    }

    private fun motivoSinAcceso(): String? = when {
        sentinel == null -> "Esta versión web no tiene el servidor de GRANEX configurado: el escaneo satelital no está disponible."
        !haySesion() -> "Ingresá con tu cuenta GRANEX para ver las imágenes Sentinel-2 de 10 m de tus lotes."
        else -> null
    }

    private fun cargarEscenas() {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        val clave = clave(e.lote)
        escenasCache[clave]?.let { guardadas ->
            _estado.value = e.copy(escenas = CargaWeb.Lista(guardadas))
            _estado.value?.escenasUtiles?.firstOrNull()?.let { elegirFecha(it.fecha) }
            return
        }
        trabajoEscenas = viewModelScope.launch {
            val resultado = runCatching { cliente.escenas(e.lote.vertices, DIAS_HISTORIAL) }
            resultado.getOrNull()?.let { escenasCache[clave] = it }
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else actual.copy(escenas = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudieron obtener las fechas.")) }))
            }
            // Se abre la fecha útil más reciente (nunca es dudosa: hace falta la siguiente para juzgarla)
            _estado.value?.escenasUtiles?.firstOrNull()?.let { elegirFecha(it.fecha) }
        }
    }

    /** Baja solo lo que necesita la vista elegida y todavía no está (o falló). */
    private fun asegurarVista() {
        val e = _estado.value ?: return
        if (_sinAcceso.value != null) return
        fun falta(c: CargaWeb<*>?) = c == null || c is CargaWeb.Fallo
        when (e.vista) {
            VistaEscaneoWeb.INDICE -> if (e.fecha != null && falta(e.imagen)) cargarImagen(e.fecha)
            VistaEscaneoWeb.FOTO -> if (e.fecha != null && falta(e.foto)) cargarFoto(e.fecha)
            VistaEscaneoWeb.RELIEVE -> if (falta(e.relieve)) cargarRelieve()
        }
    }

    private fun cargarImagen(fecha: String) {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        val clave = "${clave(e.lote)}|$fecha"
        imagenes[clave]?.let { guardada ->
            _estado.value = e.copy(imagen = CargaWeb.Lista(guardada))
            recalcular()
            return
        }
        _estado.value = e.copy(imagen = CargaWeb.Cargando)
        trabajoImagen?.cancel()
        trabajoImagen = viewModelScope.launch {
            val resultado = runCatching { bajarImagen(e.lote, fecha) }
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fecha != fecha) actual
                else actual.copy(imagen = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudo bajar la imagen.")) }))
            }
            recalcular()
        }
    }

    private suspend fun bajarImagen(lote: LoteWeb, fecha: String): ImagenIndices {
        val clave = "${clave(lote)}|$fecha"
        imagenes[clave]?.let { return it }
        val cliente = sentinel ?: throw IllegalStateException("Sin servidor de imágenes.")
        val r = cliente.imagen(lote.vertices, fecha)
        val pixeles = decodificarPng(r.png) ?: throw IllegalStateException("El servicio de imágenes no devolvió una imagen válida.")
        check(pixeles.ancho == r.ancho && pixeles.alto == r.alto) { "El servicio de imágenes devolvió una imagen de otro tamaño." }
        val imagen = ImagenIndices.desdePixeles(pixeles.argb, r.ancho, r.alto, r.oeste, r.sur, r.este, r.norte, r.fecha)
        check(imagen.pixelesValidos > 0) { "Ese día el lote estaba cubierto por nubes o sombras: elegí otra fecha." }
        imagenes[clave] = imagen
        return imagen
    }

    private fun cargarFoto(fecha: String) {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        val clave = "${clave(e.lote)}|$fecha"
        fotos[clave]?.let {
            _estado.value = e.copy(foto = CargaWeb.Lista(it))
            return
        }
        _estado.value = e.copy(foto = CargaWeb.Cargando)
        trabajoFoto?.cancel()
        trabajoFoto = viewModelScope.launch {
            val resultado = runCatching { cliente.colorReal(e.lote.vertices, fecha) }
            resultado.getOrNull()?.let { fotos[clave] = it }
            _estado.update { actual ->
                if (actual?.lote != e.lote || actual.fecha != fecha) actual
                else actual.copy(foto = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudo bajar la foto del día.")) }))
            }
        }
    }

    private fun cargarRelieve() {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        val clave = clave(e.lote)
        relieves[clave]?.let { (r, resumen) ->
            _estado.value = e.copy(relieve = CargaWeb.Lista(r), resumenRelieve = resumen)
            return
        }
        _estado.value = e.copy(relieve = CargaWeb.Cargando)
        trabajoRelieve?.cancel()
        trabajoRelieve = viewModelScope.launch {
            val resultado = runCatching {
                val r = cliente.relieve(e.lote.vertices)
                val pixeles = decodificarPng(r.png) ?: throw IllegalStateException("El servicio no devolvió un relieve válido.")
                check(pixeles.ancho == r.ancho && pixeles.alto == r.alto) { "El servicio devolvió un relieve de otro tamaño." }
                val relieve = CapaMapaEscaneo.relieveDesdePixeles(pixeles.argb, r.ancho, r.alto, r.oeste, r.sur, r.este, r.norte)
                relieve to AnalisisRelieve.resumir(relieve, e.lote.vertices)
            }
            resultado.getOrNull()?.let { relieves[clave] = it }
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else resultado.fold(
                    { (r, resumen) -> actual.copy(relieve = CargaWeb.Lista(r), resumenRelieve = resumen) },
                    { actual.copy(relieve = CargaWeb.Fallo(mensaje(it, "No se pudo bajar el relieve."))) }
                )
            }
        }
    }

    private fun recalcular() {
        _estado.update { e ->
            val img = (e?.imagen as? CargaWeb.Lista)?.valor
            if (e == null || img == null) e
            else {
                // La comparación siempre va de la fecha más vieja a la más nueva
                val otra = (e.imagenComparar as? CargaWeb.Lista)?.valor
                val comparacion = otra?.let {
                    if (it.fecha <= img.fecha) EscaneoLote.comparar(it, img, e.indice, e.lote.vertices)
                    else EscaneoLote.comparar(img, it, e.indice, e.lote.vertices)
                }
                e.copy(resumen = EscaneoLote.resumir(img, e.indice, e.lote.vertices), comparacion = comparacion)
            }
        }
    }

    private fun cancelarTodo() {
        trabajoEscenas?.cancel()
        trabajoImagen?.cancel()
        trabajoFoto?.cancel()
        trabajoRelieve?.cancel()
        trabajoComparar?.cancel()
    }

    /** El mismo lote con otro contorno es otro escaneo. */
    private fun clave(lote: LoteWeb) = "${lote.id}|${lote.registro.modificadoEn}"

    /** Los errores de la Edge Function ya vienen en castellano; los de red se reemplazan. */
    private fun mensaje(e: Throwable, porDefecto: String): String {
        val texto = e.message?.takeIf { it.isNotBlank() } ?: return porDefecto
        val esRed = listOf("fetch", "network", "timeout", "Failed", "TypeError").any { it in texto }
        return if (esRed) "$porDefecto Revisá la conexión e intentá de nuevo." else texto
    }

    companion object {
        /** Igual que Android: medio año de fechas. */
        const val DIAS_HISTORIAL = 180
    }
}
