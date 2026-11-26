package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.ImagenSentinelRemota
import com.example.data.remote.supabase.SentinelGranex
import com.example.utils.AnalisisRelieve
import com.example.utils.CoberturaLote
import com.example.utils.EscaneoLote
import com.example.utils.FechasIso
import com.example.utils.GeometriaLote
import com.example.utils.ResumenRelieve
import com.example.utils.vra.ArchivoExportado
import com.example.utils.vra.CalculoPotencial
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.Campanas
import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.DosisZona
import com.example.utils.vra.ErrorPotencial
import com.example.utils.vra.ErrorZonificacion
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Labor
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.PotencialProductivo
import com.example.utils.vra.Prescripcion
import com.example.utils.vra.RecomendacionZonas
import com.example.utils.vra.Temporada
import com.example.utils.vra.UbicacionFertilizante
import com.example.utils.vra.Zonificacion
import com.example.utils.vra.Zonificador
import com.example.utils.CapaMapaEscaneo
import com.example.web.datos.LoteWeb
import com.example.web.plataforma.decodificarPng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Sentido de las pasadas para la grilla de máquina (como SentidoSiembra en Android). */
enum class SentidoSiembraWeb(val nombre: String) {
    AUTOMATICO("Lado más largo"),
    NORTE_SUR("Norte-sur"),
    ESTE_OESTE("Este-oeste");

    fun rumbo(lote: List<Pair<Double, Double>>): Double = when (this) {
        AUTOMATICO -> GeometriaLote.rumboLadoMasLargo(lote)
        NORTE_SUR -> 0.0
        ESTE_OESTE -> 90.0
    }
}

/** De dónde salen las zonas (los mapas de rinde todavía están solo en la app Android). */
enum class FuenteZonasWeb(val nombre: String, val descripcion: String) {
    CAMPANAS("Campañas", "Pico de vigor de cada campaña, relativo al lote y promediado: zonas que se repiten año tras año."),
    FECHA("Una fecha", "Una sola imagen: muestra cómo estaba el lote ese día. Útil para refertilizar un cultivo en pie."),
    RELIEVE("Relieve", "Altura del terreno (Copernicus DEM): bajos, medias lomas y lomas. Sirve en lotes con desnivel, donde el agua marca el rinde.")
}

data class EstadoPrescripcionWeb(
    val lote: LoteWeb,
    /** Por qué no se pueden pedir imágenes (sin servidor o sin sesión); null si se puede. */
    val sinAcceso: String? = null,
    val fuenteZonas: FuenteZonasWeb = FuenteZonasWeb.CAMPANAS,
    val temporada: Temporada = Temporada.VERANO,
    val campanasDisponibles: List<Campana> = emptyList(),
    val campanasElegidas: Set<String> = emptySet(),
    val campanas: Map<String, CargaWeb<CampanaNdvi>> = emptyMap(),
    val potencial: CargaWeb<PotencialProductivo>? = null,
    /** Altura sobre el punto más bajo del lote (en los tres canales), lista para zonificar. */
    val relieve: CargaWeb<ImagenIndices>? = null,
    val resumenRelieve: ResumenRelieve? = null,
    /** Índice del pico de las campañas; null = automático (NDRE si el NDVI está saturado). */
    val indiceCampanas: IndiceVegetacion? = null,
    val recomendacion: RecomendacionZonas? = null,
    val zonasElegidasPorUsuario: Boolean = false,
    val imagenSinCultivo: Boolean = false,
    val sentido: SentidoSiembraWeb = SentidoSiembraWeb.AUTOMATICO,
    val velocidadKmH: Int = 8,
    val respuestaS: Int = 3,
    val escenas: CargaWeb<List<EscenaSentinel>>? = null,
    val escena: EscenaSentinel? = null,
    val imagen: CargaWeb<ImagenIndices>? = null,
    val indice: IndiceVegetacion = IndiceVegetacion.NDVI,
    val perfil: PerfilMonitor = PerfilMonitor.CUFFIA_AG9500,
    val config: ConfigZonificacion = ConfigZonificacion(maxPoligonos = PerfilMonitor.CUFFIA_AG9500.maxPoligonos),
    val zonificacion: CargaWeb<Zonificacion>? = null,
    val labor: Labor = Labor.FERTILIZACION,
    val ubicacionFertilizante: UbicacionFertilizante = UbicacionFertilizante.LINEA,
    val distanciaSurcosM: Double = 0.525,
    val producto: String = "",
    val dosis: Map<Int, DosisZona> = emptyMap()
) {
    val zonas: Zonificacion? get() = (zonificacion as? CargaWeb.Lista)?.valor
    val potencialListo: PotencialProductivo? get() = (potencial as? CargaWeb.Lista)?.valor

    val fechaImagen: String?
        get() = when (fuenteZonas) {
            FuenteZonasWeb.FECHA -> (imagen as? CargaWeb.Lista)?.valor?.fecha
            FuenteZonasWeb.CAMPANAS -> potencialListo?.usadas?.takeIf { it.isNotEmpty() }?.let { u ->
                "${u.first().campana.desde.take(4)}-${u.last().campana.hasta.take(4)}"
            }
            FuenteZonasWeb.RELIEVE -> if (relieve is CargaWeb.Lista) "relieve" else null
        }

    val baseZonas: ImagenIndices?
        get() = when (fuenteZonas) {
            FuenteZonasWeb.FECHA -> (imagen as? CargaWeb.Lista)?.valor
            FuenteZonasWeb.CAMPANAS -> potencialListo?.comoImagen()
            FuenteZonasWeb.RELIEVE -> (relieve as? CargaWeb.Lista)?.valor
        }

    /** El dato del que salen las zonas (para descartar resultados de una base anterior). */
    val datoBase: Any?
        get() = when (fuenteZonas) {
            FuenteZonasWeb.FECHA -> imagen
            FuenteZonasWeb.CAMPANAS -> potencial
            FuenteZonasWeb.RELIEVE -> relieve
        }

    val indiceZonas: IndiceVegetacion get() = if (fuenteZonas == FuenteZonasWeb.FECHA) indice else IndiceVegetacion.NDVI

    val nombreValor: String
        get() = when (fuenteZonas) {
            FuenteZonasWeb.FECHA -> indice.nombre
            FuenteZonasWeb.CAMPANAS -> "Potencial"
            FuenteZonasWeb.RELIEVE -> "Altura (m)"
        }

    /** Metros que recorre la máquina mientras el dosificador pasa de una dosis a otra. */
    val metrosTransicion: Double get() = velocidadKmH / 3.6 * respuestaS

    fun prescripcion(): Prescripcion? {
        val z = zonas ?: return null
        val fecha = fechaImagen ?: return null
        val descripcion = when (fuenteZonas) {
            FuenteZonasWeb.FECHA -> "Sentinel-2 L2A del $fecha, índice ${indice.nombre}"
            FuenteZonasWeb.CAMPANAS -> "Potencial productivo: ${potencialListo?.descripcion ?: fecha} (1,00 = lo típico del lote)"
            FuenteZonasWeb.RELIEVE ->
                "Relieve (Copernicus DEM 30 m): altura sobre el punto más bajo del lote, en metros; ${AnalisisRelieve.AVISO_DEM}"
        }
        return Prescripcion(
            nombreLote = lote.nombre,
            lote = lote.vertices,
            fechaImagen = fecha,
            indice = indiceZonas,
            fuente = descripcion,
            nombreValor = nombreValor,
            zonificacion = z,
            labor = labor,
            ubicacionFertilizante = ubicacionFertilizante,
            distanciaSurcosM = distanciaSurcosM,
            dosis = z.zonas.map { dosis[it.zona] ?: DosisZona(it.zona) },
            producto = producto,
            notas = emptyList()
        )
    }
}

/**
 * Prescripciones de dosis variable en la web, con la misma lógica que PrescripcionViewModel en Android
 * (todo el cálculo está en :shared): zonas por campañas, por una fecha o por relieve, ajuste a la
 * máquina, dosis por zona y los archivos para cada monitor, que se descargan con el navegador.
 * Las imágenes vienen del servidor de GRANEX ([SentinelGranex]): hace falta sesión. Lo bajado se
 * guarda en memoria por lote, así repetir no gasta la cuota diaria.
 */
@OptIn(ExperimentalTime::class)
class PrescripcionWebViewModel(
    private val sentinel: SentinelGranex?,
    private val haySesion: () -> Boolean,
    private val hoy: () -> String = { FechasIso.deArgentina(Clock.System.now().toEpochMilliseconds()) }
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoPrescripcionWeb?>(null)
    val estado: StateFlow<EstadoPrescripcionWeb?> = _estado.asStateFlow()

    private val cacheCampanas = HashMap<String, CampanaNdvi>()
    private val cacheRelieve = HashMap<String, Pair<ImagenIndices, ResumenRelieve?>>()
    private val cacheImagenes = HashMap<String, ImagenIndices>()
    private var trabajoEscenas: Job? = null
    private var trabajoImagen: Job? = null
    private var trabajoZonas: Job? = null
    private var trabajoCampanas: Job? = null
    private var trabajoRelieve: Job? = null

    fun abrir(lote: LoteWeb) {
        cancelarTodo()
        val temporada = _estado.value?.temporada ?: Temporada.VERANO
        val disponibles = Campanas.terminadas(hoy(), temporada)
        val sinAcceso = when {
            sentinel == null -> "Esta versión web no tiene el servidor de GRANEX configurado: las prescripciones no están disponibles."
            !haySesion() -> "Ingresá con tu cuenta GRANEX para usar las imágenes Sentinel-2 de 10 m."
            else -> null
        }
        _estado.value = EstadoPrescripcionWeb(
            lote = lote,
            sinAcceso = sinAcceso,
            temporada = temporada,
            campanasDisponibles = disponibles,
            campanasElegidas = disponibles.takeLast(Campanas.POR_DEFECTO).map { it.etiqueta }.toSet()
        )
        if (sinAcceso == null) cargarCampanas()
    }

    fun cerrar() {
        cancelarTodo()
        _estado.value = null
    }

    fun elegirFuenteZonas(f: FuenteZonasWeb) {
        val e = _estado.value ?: return
        if (e.fuenteZonas == f) return
        _estado.value = e.copy(fuenteZonas = f, zonificacion = null, recomendacion = null)
        when (f) {
            FuenteZonasWeb.CAMPANAS -> cargarCampanas()
            FuenteZonasWeb.RELIEVE -> if (e.relieve == null || e.relieve is CargaWeb.Fallo) cargarRelieve()
            FuenteZonasWeb.FECHA -> if (e.escenas == null || e.escenas is CargaWeb.Fallo) cargarEscenas()
                else if (e.imagen == null) (e.escenas as? CargaWeb.Lista)?.valor?.firstOrNull { it.nubesPct <= 10 }?.let(::elegirEscena)
        }
        rezonificar()
    }

    fun reintentar() {
        val e = _estado.value ?: return
        when (e.fuenteZonas) {
            FuenteZonasWeb.CAMPANAS -> cargarCampanas()
            FuenteZonasWeb.RELIEVE -> cargarRelieve()
            FuenteZonasWeb.FECHA -> if (e.escenas is CargaWeb.Fallo) cargarEscenas() else e.escena?.let(::elegirEscena)
        }
    }

    // ---------- Campañas ----------

    fun elegirTemporada(t: Temporada) {
        val e = _estado.value ?: return
        if (e.temporada == t) return
        trabajoCampanas?.cancel()
        val disponibles = Campanas.terminadas(hoy(), t)
        _estado.value = e.copy(
            temporada = t,
            campanasDisponibles = disponibles,
            campanasElegidas = disponibles.takeLast(Campanas.POR_DEFECTO).map { it.etiqueta }.toSet(),
            campanas = emptyMap(),
            potencial = null,
            zonificacion = null,
            recomendacion = null
        )
        cargarCampanas()
    }

    fun alternarCampana(etiqueta: String) {
        val e = _estado.value ?: return
        val elegidas = if (etiqueta in e.campanasElegidas) e.campanasElegidas - etiqueta else e.campanasElegidas + etiqueta
        if (elegidas.isEmpty()) return
        _estado.value = e.copy(campanasElegidas = elegidas)
        cargarCampanas()
    }

    fun elegirIndiceCampanas(indice: IndiceVegetacion?) {
        val e = _estado.value ?: return
        if (e.indiceCampanas == indice) return
        _estado.value = e.copy(indiceCampanas = indice)
        trabajoCampanas?.cancel()
        trabajoCampanas = viewModelScope.launch { calcularPotencial() }
    }

    /** Baja de a una las campañas elegidas que falten (la cuota admite pocos pedidos simultáneos). */
    private fun cargarCampanas() {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        if (e.sinAcceso != null || e.fuenteZonas != FuenteZonasWeb.CAMPANAS) return
        trabajoCampanas?.cancel()
        trabajoCampanas = viewModelScope.launch {
            val anillo = GeometriaLote.anilloAbierto(e.lote.vertices)
            val pendientes = e.campanasDisponibles.filter { it.etiqueta in e.campanasElegidas && e.campanas[it.etiqueta] !is CargaWeb.Lista }
            for (c in pendientes) {
                val clave = "${clave(e.lote)}|${c.desde}|${c.hasta}"
                val guardada = cacheCampanas[clave]
                val carga: CargaWeb<CampanaNdvi> = if (guardada != null) CargaWeb.Lista(guardada) else {
                    _estado.update { it?.copy(campanas = it.campanas + (c.etiqueta to CargaWeb.Cargando)) }
                    try {
                        val campana = decodificarCampana(cliente.campana(anillo, c.desde, c.hasta), c)
                        cacheCampanas[clave] = campana
                        CargaWeb.Lista(campana)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Throwable) {
                        CargaWeb.Fallo(mensaje(ex, "No se pudo bajar la campaña ${c.etiqueta}."))
                    }
                }
                _estado.update { actual -> if (actual?.lote != e.lote) actual else actual.copy(campanas = actual.campanas + (c.etiqueta to carga)) }
            }
            calcularPotencial()
        }
    }

    private suspend fun calcularPotencial() {
        val e = _estado.value ?: return
        val listas = e.campanasDisponibles
            .filter { it.etiqueta in e.campanasElegidas }
            .mapNotNull { (e.campanas[it.etiqueta] as? CargaWeb.Lista)?.valor }
        if (listas.isEmpty()) {
            val fallo = e.campanas.values.filterIsInstance<CargaWeb.Fallo>().firstOrNull()?.mensaje
            _estado.update { it?.copy(potencial = CargaWeb.Fallo(fallo ?: "No se pudo bajar ninguna campaña.")) }
            return
        }
        _estado.update { it?.copy(potencial = CargaWeb.Cargando) }
        yield()
        val resultado = try {
            val indice = e.indiceCampanas ?: CalculoPotencial.indiceSugerido(listas, e.lote.vertices)
            CargaWeb.Lista(CalculoPotencial.combinar(listas, e.lote.vertices, indice))
        } catch (ex: ErrorPotencial) {
            CargaWeb.Fallo(ex.message ?: "No se pudo calcular el potencial.")
        }
        _estado.update { if (it?.lote != e.lote) it else it.copy(potencial = resultado) }
        rezonificar()
    }

    // ---------- Relieve ----------

    private fun cargarRelieve() {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        if (e.sinAcceso != null) return
        cacheRelieve[clave(e.lote)]?.let { (imagen, resumen) ->
            _estado.value = e.copy(relieve = CargaWeb.Lista(imagen), resumenRelieve = resumen)
            rezonificar()
            return
        }
        _estado.value = e.copy(relieve = CargaWeb.Cargando, resumenRelieve = null)
        trabajoRelieve?.cancel()
        trabajoRelieve = viewModelScope.launch {
            val resultado: CargaWeb<ImagenIndices>
            var resumen: ResumenRelieve? = null
            try {
                val r = cliente.relieve(GeometriaLote.anilloAbierto(e.lote.vertices))
                val pixeles = decodificarPng(r.png) ?: throw IllegalStateException("El servicio no devolvió un relieve válido.")
                check(pixeles.ancho == r.ancho && pixeles.alto == r.alto) { "El servicio devolvió un relieve de otro tamaño." }
                val relieve = CapaMapaEscaneo.relieveDesdePixeles(pixeles.argb, r.ancho, r.alto, r.oeste, r.sur, r.este, r.norte)
                resumen = AnalisisRelieve.resumir(relieve, e.lote.vertices)
                val imagen = relieve.comoImagen(e.lote.vertices)
                resultado = if (resumen == null) CargaWeb.Fallo("El modelo de elevación no tiene datos dentro del lote.") else CargaWeb.Lista(imagen)
                if (resumen != null) cacheRelieve[clave(e.lote)] = imagen to resumen
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Throwable) {
                _estado.update { if (it?.lote != e.lote) it else it.copy(relieve = CargaWeb.Fallo(mensaje(ex, "No se pudo obtener el relieve."))) }
                return@launch
            }
            _estado.update { if (it?.lote != e.lote) it else it.copy(relieve = resultado, resumenRelieve = resumen) }
            rezonificar()
        }
    }

    // ---------- Una fecha ----------

    private fun cargarEscenas() {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        if (e.sinAcceso != null) return
        _estado.value = e.copy(escenas = CargaWeb.Cargando)
        trabajoEscenas?.cancel()
        trabajoEscenas = viewModelScope.launch {
            val resultado = runCatching { cliente.escenas(GeometriaLote.anilloAbierto(e.lote.vertices), DIAS_ESCENAS) }
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else actual.copy(escenas = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudieron obtener las fechas.")) }))
            }
            // Se propone la más reciente con el lote casi despejado
            if (_estado.value?.fuenteZonas == FuenteZonasWeb.FECHA) {
                resultado.getOrNull()?.firstOrNull { it.nubesPct <= 10 }?.let(::elegirEscena)
            }
        }
    }

    fun elegirEscena(escena: EscenaSentinel) {
        val e = _estado.value ?: return
        val cliente = sentinel ?: return
        _estado.value = e.copy(escena = escena, imagen = CargaWeb.Cargando, zonificacion = null, imagenSinCultivo = false)
        trabajoImagen?.cancel()
        trabajoImagen = viewModelScope.launch {
            val claveImagen = "${clave(e.lote)}|${escena.fecha}"
            val resultado = runCatching {
                cacheImagenes[claveImagen] ?: run {
                    val r = cliente.imagen(GeometriaLote.anilloAbierto(e.lote.vertices), escena.fecha)
                    val pixeles = decodificarPng(r.png) ?: throw IllegalStateException("El servicio de imágenes no devolvió una imagen válida.")
                    check(pixeles.ancho == r.ancho && pixeles.alto == r.alto) { "El servicio de imágenes devolvió una imagen de otro tamaño." }
                    ImagenIndices.desdePixeles(pixeles.argb, r.ancho, r.alto, r.oeste, r.sur, r.este, r.norte, r.fecha).also { cacheImagenes[claveImagen] = it }
                }
            }
            // Una imagen de suelo sin cultivo no sirve para prescribir: se avisa
            val sinCultivo = resultado.getOrNull()?.let { img ->
                EscaneoLote.resumir(img, IndiceVegetacion.NDVI, e.lote.vertices)?.cobertura == CoberturaLote.SUELO_DESNUDO
            } ?: false
            _estado.update { actual ->
                if (actual?.escena != escena) actual
                else actual.copy(
                    imagen = resultado.fold({ CargaWeb.Lista(it) }, { CargaWeb.Fallo(mensaje(it, "No se pudo descargar la imagen.")) }),
                    imagenSinCultivo = sinCultivo
                )
            }
            rezonificar()
        }
    }

    fun elegirIndice(indice: IndiceVegetacion) {
        _estado.update { it?.copy(indice = indice) }
        rezonificar()
    }

    // ---------- Zonas y máquina ----------

    fun elegirPerfil(perfil: PerfilMonitor) {
        _estado.update { e ->
            e?.copy(
                perfil = perfil,
                config = e.config.copy(maxPoligonos = perfil.maxPoligonos),
                // Altina dosifica un solo producto
                labor = if (perfil == PerfilMonitor.CUFFIA_ALTINA && e.labor == Labor.SIEMBRA_Y_FERTILIZACION) Labor.FERTILIZACION else e.labor
            )
        }
        rezonificar()
    }

    fun cambiarConfig(transformar: (ConfigZonificacion) -> ConfigZonificacion) {
        _estado.update { e ->
            e?.let {
                val nueva = transformar(it.config).copy(maxPoligonos = it.perfil.maxPoligonos)
                it.copy(config = nueva, zonasElegidasPorUsuario = it.zonasElegidasPorUsuario || nueva.zonas != it.config.zonas)
            }
        }
        rezonificar()
    }

    /** Ancho de labor de la máquina (null = celdas cuadradas al norte). */
    fun cambiarAnchoLabor(anchoM: Double?) {
        val e = _estado.value ?: return
        cambiarConfig { it.copy(anchoLaborM = anchoM, rumboGrados = anchoM?.let { e.sentido.rumbo(e.lote.vertices) }) }
    }

    fun cambiarSentido(sentido: SentidoSiembraWeb) {
        val e = _estado.value ?: return
        _estado.value = e.copy(sentido = sentido)
        if (e.config.anchoLaborM != null) cambiarConfig { it.copy(rumboGrados = sentido.rumbo(e.lote.vertices)) }
    }

    fun cambiarVelocidad(kmH: Int) = _estado.update { it?.copy(velocidadKmH = kmH) }

    fun cambiarRespuesta(segundos: Int) = _estado.update { it?.copy(respuestaS = segundos) }

    /**
     * Recomendación de zonas y zonificación con la base actual. Mientras el usuario no elija la
     * cantidad a mano, se usa la recomendada. Un resultado de una base anterior no se aplica.
     */
    private fun rezonificar() {
        val e = _estado.value ?: return
        trabajoZonas?.cancel()
        val base = e.baseZonas ?: return
        _estado.value = e.copy(zonificacion = CargaWeb.Cargando)
        trabajoZonas = viewModelScope.launch {
            // Deja dibujar el "Calculando zonas…" antes del cálculo (en el navegador corre en el mismo hilo)
            yield()
            val rec = runCatching { Zonificador.recomendarZonas(base, e.indiceZonas, e.lote.vertices, e.config) }.getOrNull()
                ?.let { r ->
                    if (e.fuenteZonas != FuenteZonasWeb.RELIEVE) r
                    else (e.resumenRelieve?.plano ?: false).let { plano ->
                        r.copy(homogeneo = plano, zonas = Zonificador.cantidadRecomendada(r.explicada, plano))
                    }
                }
            val config = if (!e.zonasElegidasPorUsuario && rec != null) e.config.copy(zonas = rec.zonas) else e.config
            yield()
            val resultado = try {
                CargaWeb.Lista(Zonificador.zonificar(base, e.indiceZonas, e.lote.vertices, config))
            } catch (ex: ErrorZonificacion) {
                CargaWeb.Fallo(ex.message ?: "No se pudieron calcular las zonas.")
            }
            _estado.update { actual ->
                if (actual == null || actual.fuenteZonas != e.fuenteZonas || actual.datoBase !== e.datoBase) actual
                else actual.copy(zonificacion = resultado, recomendacion = rec, config = config)
            }
        }
    }

    // ---------- Dosis ----------

    fun cambiarLabor(labor: Labor) = _estado.update { it?.copy(labor = labor) }

    fun cambiarUbicacion(ubicacion: UbicacionFertilizante) = _estado.update { it?.copy(ubicacionFertilizante = ubicacion) }

    fun cambiarDistanciaSurcos(metros: Double) = _estado.update { it?.copy(distanciaSurcosM = metros) }

    fun cambiarProducto(producto: String) = _estado.update { it?.copy(producto = producto) }

    fun cambiarDosis(zona: Int, fertilizanteKgHa: Int?, semillasHa: Int?) = _estado.update { e ->
        e?.copy(dosis = e.dosis + (zona to DosisZona(zona, fertilizanteKgHa, semillasHa)))
    }

    // ---------- Archivos ----------

    /** ZIP con los archivos del monitor elegido y un LEEME con las instrucciones de carga. */
    fun zipMonitor(): Result<ByteArray> = generar { p, perfil ->
        ExportacionPrescripcion.zip(
            ExportacionPrescripcion.paraMonitor(p, perfil) + ArchivoExportado("LEEME.txt", ExportacionPrescripcion.leeme(p, perfil).encodeToByteArray())
        )
    }

    /** ZIP con todos los formatos (Shapefile, ISO-XML y GeoJSON) en carpetas. */
    fun zipTodos(): Result<ByteArray> = generar { p, perfil -> ExportacionPrescripcion.zip(ExportacionPrescripcion.todosLosFormatos(p, perfil)) }

    /** Los archivos del monitor sueltos, para copiarlos directo a la raíz del pendrive. */
    fun archivosMonitor(): Result<List<ArchivoExportado>> = generar { p, perfil -> ExportacionPrescripcion.paraMonitor(p, perfil) }

    fun nombreArchivo(sufijo: String): String {
        val e = _estado.value
        val base = e?.let { ExportacionPrescripcion.nombreCorto(it.lote.nombre) } ?: "PRESCRIP"
        return "${base}_${e?.fechaImagen.orEmpty()}$sufijo"
    }

    private fun <T> generar(accion: (Prescripcion, PerfilMonitor) -> T): Result<T> {
        val e = _estado.value ?: return Result.failure(IllegalStateException("No hay prescripción abierta."))
        val p = e.prescripcion() ?: return Result.failure(IllegalStateException("Primero calculá las zonas."))
        val problemas = p.problemas(e.perfil)
        if (problemas.isNotEmpty()) return Result.failure(IllegalStateException(problemas.first()))
        return runCatching { accion(p, e.perfil) }
    }

    // ---------- Apoyo ----------

    private fun cancelarTodo() {
        trabajoEscenas?.cancel()
        trabajoImagen?.cancel()
        trabajoZonas?.cancel()
        trabajoCampanas?.cancel()
        trabajoRelieve?.cancel()
    }

    /** El mismo lote con otro contorno es otra prescripción. */
    private fun clave(lote: LoteWeb) = "${lote.id}|${lote.registro.modificadoEn}"

    private suspend fun decodificarCampana(r: ImagenSentinelRemota, campana: Campana): CampanaNdvi {
        val pixeles = decodificarPng(r.png) ?: throw IllegalStateException("El servicio de imágenes no devolvió una imagen válida.")
        check(pixeles.ancho == r.ancho && pixeles.alto == r.alto) { "El servicio de imágenes devolvió una imagen de otro tamaño." }
        val argb = pixeles.argb
        val grilla = GrillaGeografica(r.oeste, r.norte, (r.este - r.oeste) / r.ancho, (r.norte - r.sur) / r.alto, r.ancho, r.alto)
        return CampanaNdvi(
            campana = campana,
            grilla = grilla,
            ndvi = FloatArray(argb.size) { ImagenIndices.decodificarCanal((argb[it] shr 16) and 0xFF) },
            observaciones = IntArray(argb.size) { (argb[it] shr 8) and 0xFF },
            ndre = FloatArray(argb.size) { ImagenIndices.decodificarCanal(argb[it] and 0xFF) }
        )
    }

    /** Los errores del servidor ya vienen en castellano; los de red se reemplazan. */
    private fun mensaje(e: Throwable, porDefecto: String): String {
        val texto = e.message?.takeIf { it.isNotBlank() } ?: return porDefecto
        val esRed = listOf("fetch", "network", "timeout", "Failed", "TypeError").any { it in texto }
        return if (esRed) "$porDefecto Revisá la conexión e intentá de nuevo." else texto
    }

    companion object {
        /** Fechas de imagen que se ofrecen para zonificar con una sola fecha (como Android: 4 meses). */
        const val DIAS_ESCENAS = 120
    }
}
