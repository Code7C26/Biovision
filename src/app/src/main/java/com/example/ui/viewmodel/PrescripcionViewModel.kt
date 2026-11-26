package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AlmacenAgronomia
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.LoteSatelital
import com.example.data.repository.MapaRindeLote
import com.example.data.repository.SueloLote
import com.example.utils.AnalisisRelieve
import com.example.utils.CoberturaLote
import com.example.utils.GeometriaLote
import com.example.utils.EscaneoLote
import com.example.utils.ResumenRelieve
import com.example.utils.vra.AnalisisSuelo
import com.example.utils.vra.ArchivoExportado
import com.example.utils.vra.CapaRinde
import com.example.utils.vra.ColumnasSuelo
import com.example.utils.vra.CultivoVra
import com.example.utils.vra.DosisSugerida
import com.example.utils.vra.DosisSugeridaZona
import com.example.utils.vra.MapaRinde
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.ParametrosDosis
import com.example.utils.vra.RindeZona
import com.example.utils.vra.SueloZona
import com.example.utils.vra.TablaCampo
import com.example.utils.vra.UnidadRinde
import com.example.utils.vra.CalculoPotencial
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.Campanas
import com.example.utils.vra.ErrorPotencial
import com.example.utils.vra.PotencialProductivo
import com.example.utils.vra.RecomendacionZonas
import com.example.utils.vra.Temporada
import com.example.utils.vra.ConfigZonificacion
import com.example.utils.vra.DosisZona
import com.example.utils.vra.ErrorZonificacion
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.ImagenIndices
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Labor
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.Prescripcion
import com.example.utils.vra.UbicacionFertilizante
import com.example.utils.vra.Zonificacion
import com.example.utils.vra.Zonificador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resultado de una carga asíncrona. */
sealed interface Carga<out T> {
    object Cargando : Carga<Nothing>
    data class Lista<T>(val valor: T) : Carga<T>
    data class Fallo(val mensaje: String) : Carga<Nothing>
}

/** Sentido de las pasadas para la grilla de máquina. */
enum class SentidoSiembra(val nombre: String) {
    AUTOMATICO("Lado más largo"),
    NORTE_SUR("Norte-sur"),
    ESTE_OESTE("Este-oeste");

    fun rumbo(lote: List<Pair<Double, Double>>): Double = when (this) {
        AUTOMATICO -> GeometriaLote.rumboLadoMasLargo(lote)
        NORTE_SUR -> 0.0
        ESTE_OESTE -> 90.0
    }
}

/** De dónde salen las zonas. */
enum class FuenteZonas(val nombre: String, val descripcion: String) {
    CAMPANAS(
        "Campañas",
        "Pico de vigor de cada campaña, relativo al lote y promediado: zonas que se repiten año tras año."
    ),
    FECHA(
        "Una fecha",
        "Una sola imagen: muestra cómo estaba el lote ese día. Útil para refertilizar un cultivo en pie."
    ),
    RELIEVE(
        "Relieve",
        "Altura del terreno (Copernicus DEM): bajos, medias lomas y lomas. Sirve en lotes con desnivel, donde el agua marca el rinde."
    ),
    RINDE(
        "Rinde",
        "Mapas de rinde de la cosechadora, relativos al lote y promediados: lo que realmente rindió cada sector."
    )
}

/** Archivo de rinde leído que espera que el usuario confirme la columna, la unidad, el cultivo y la campaña. */
data class ImportacionRinde(
    val archivo: String,
    val tabla: TablaCampo,
    val columna: String?,
    val unidad: UnidadRinde,
    val cultivo: CultivoVra = CultivoVra.MAIZ,
    val campana: String = ""
)

/** Planilla del laboratorio leída que espera que el usuario confirme qué columna es cada análisis. */
data class ImportacionSuelo(val archivo: String, val tabla: TablaCampo, val columnas: ColumnasSuelo)

data class EstadoPrescripcion(
    val lote: LoteSatelital,
    val configurada: Boolean,
    val fuenteZonas: FuenteZonas = FuenteZonas.CAMPANAS,
    val temporada: Temporada = Temporada.VERANO,
    val campanasDisponibles: List<Campana> = emptyList(),
    /** Etiquetas de las campañas elegidas para el potencial. */
    val campanasElegidas: Set<String> = emptySet(),
    val campanas: Map<String, Carga<CampanaNdvi>> = emptyMap(),
    val potencial: Carga<PotencialProductivo>? = null,
    /** Altura sobre el punto más bajo del lote (en los tres canales), lista para zonificar. */
    val relieve: Carga<ImagenIndices>? = null,
    val resumenRelieve: ResumenRelieve? = null,
    /** Índice del pico de las campañas; null = automático (NDRE si el NDVI está saturado). */
    val indiceCampanas: IndiceVegetacion? = null,
    val recomendacion: RecomendacionZonas? = null,
    /** Si el usuario eligió la cantidad de zonas a mano, ya no se aplica la recomendada. */
    val zonasElegidasPorUsuario: Boolean = false,
    /** La imagen de una fecha es de suelo sin cultivo: no sirve para prescribir. */
    val imagenSinCultivo: Boolean = false,
    /** Grilla de máquina: sentido de las pasadas (el ancho de labor va en config.anchoLaborM). */
    val sentido: SentidoSiembra = SentidoSiembra.AUTOMATICO,
    /** Velocidad de trabajo y tiempo que tarda el dosificador en cambiar de dosis (solo para avisar). */
    val velocidadKmH: Int = 8,
    val respuestaS: Int = 3,
    val escenas: Carga<List<EscenaSentinel>> = Carga.Cargando,
    val escena: EscenaSentinel? = null,
    val imagen: Carga<ImagenIndices>? = null,
    val indice: IndiceVegetacion = IndiceVegetacion.NDVI,
    val perfil: PerfilMonitor = PerfilMonitor.CUFFIA_AG9500,
    val config: ConfigZonificacion = ConfigZonificacion(maxPoligonos = PerfilMonitor.CUFFIA_AG9500.maxPoligonos),
    val zonificacion: Carga<Zonificacion>? = null,
    val labor: Labor = Labor.FERTILIZACION,
    val ubicacionFertilizante: UbicacionFertilizante = UbicacionFertilizante.LINEA,
    val distanciaSurcosM: Double = 0.525,
    val producto: String = "",
    val dosis: Map<Int, DosisZona> = emptyMap(),
    /** Mapas de rinde del lote (guardados en el teléfono). */
    val rindes: List<MapaRindeLote> = emptyList(),
    /** Con varias campañas: los mapas de rinde cuentan como campañas más. */
    val sumarRindes: Boolean = true,
    /** Potencial calculado solo con los mapas de rinde (base "Rinde"). */
    val potencialRinde: Carga<PotencialProductivo>? = null,
    val importacionRinde: ImportacionRinde? = null,
    val importacionSuelo: ImportacionSuelo? = null,
    /** Resultado o error de la última importación. */
    val mensajeArchivo: String? = null,
    val suelo: SueloLote = SueloLote(),
    val parametrosDosis: ParametrosDosis = ParametrosDosis(),
    /** Rinde medido en cada zona, por mapa (id del mapa → zonas). */
    val rindePorZona: Map<String, List<RindeZona>> = emptyMap(),
    /** Promedio de los puntos de suelo de cada zona. */
    val sueloPorZona: Map<Int, SueloZona> = emptyMap()
) {
    val zonas: Zonificacion? get() = (zonificacion as? Carga.Lista)?.valor
    val potencialListo: PotencialProductivo? get() = (potencial as? Carga.Lista)?.valor

    /** Fecha (una imagen) o años de las campañas, para nombres de archivo y la prescripción. */
    val fechaImagen: String?
        get() = when (fuenteZonas) {
            FuenteZonas.FECHA -> (imagen as? Carga.Lista)?.valor?.fecha
            FuenteZonas.CAMPANAS -> potencialListo?.usadas?.takeIf { it.isNotEmpty() }?.let { u ->
                "${u.first().campana.desde.take(4)}-${u.last().campana.hasta.take(4)}"
            }
            FuenteZonas.RELIEVE -> if (relieve is Carga.Lista) "relieve" else null
            FuenteZonas.RINDE -> if (potencialRinde is Carga.Lista) "rinde" else null
        }

    /** Lo que se zonifica: la imagen de la fecha, el potencial de las campañas o el relieve (como imagen). */
    val baseZonas: ImagenIndices?
        get() = when (fuenteZonas) {
            FuenteZonas.FECHA -> (imagen as? Carga.Lista)?.valor
            FuenteZonas.CAMPANAS -> potencialListo?.comoImagen()
            FuenteZonas.RELIEVE -> (relieve as? Carga.Lista)?.valor
            FuenteZonas.RINDE -> (potencialRinde as? Carga.Lista)?.valor?.comoImagen()
        }

    /** El dato del que salen las zonas (para saber si un resultado de zonas es de la base actual). */
    val datoBase: Any?
        get() = when (fuenteZonas) {
            FuenteZonas.FECHA -> imagen
            FuenteZonas.CAMPANAS -> potencial
            FuenteZonas.RELIEVE -> relieve
            FuenteZonas.RINDE -> potencialRinde
        }

    /** El potencial y el relieve van en el canal de NDVI. */
    val indiceZonas: IndiceVegetacion get() = if (fuenteZonas == FuenteZonas.FECHA) indice else IndiceVegetacion.NDVI

    /** Nombre del valor de cada zona: el índice, el potencial relativo o la altura. */
    val nombreValor: String
        get() = when (fuenteZonas) {
            FuenteZonas.FECHA -> indice.nombre
            FuenteZonas.CAMPANAS -> "Potencial"
            FuenteZonas.RELIEVE -> "Altura (m)"
            FuenteZonas.RINDE -> "Rinde relativo"
        }

    /** Metros que recorre la máquina mientras el dosificador pasa de una dosis a otra. */
    val metrosTransicion: Double get() = velocidadKmH / 3.6 * respuestaS

    fun prescripcion(): Prescripcion? {
        val z = zonas ?: return null
        val fecha = fechaImagen ?: return null
        val descripcion = when (fuenteZonas) {
            FuenteZonas.FECHA -> "Sentinel-2 L2A del $fecha, índice ${indice.nombre}"
            FuenteZonas.CAMPANAS -> "Potencial productivo: ${potencialListo?.descripcion ?: fecha} (1,00 = lo típico del lote)"
            FuenteZonas.RELIEVE ->
                "Relieve (Copernicus DEM 30 m): altura sobre el punto más bajo del lote, en metros; ${AnalisisRelieve.AVISO_DEM}"
            FuenteZonas.RINDE ->
                "${(potencialRinde as? Carga.Lista)?.valor?.descripcion ?: "Mapas de rinde"} (1,00 = lo típico del lote)"
        }
        return Prescripcion(
            nombreLote = lote.nombre,
            lote = lote.coordenadas,
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
            notas = notasAgronomicas()
        )
    }

    /**
     * Identifica la zonificación actual: lo cargado a mano por zona vale solo para ella. Se guarda en
     * el teléfono, así que tiene que ser igual al reabrir la app: se usa el texto de la configuración
     * y no su hashCode (el de un enum cambia en cada ejecución).
     */
    val firmaZonas: String?
        get() = zonas?.let { z -> "$fuenteZonas|$fechaImagen|${z.zonas.size}|${config.toString().hashCode()}|${lote.coordenadas.hashCode()}" }

    /** Valores de suelo cargados a mano que corresponden a las zonas actuales. */
    val sueloManual: Map<Int, MuestraSuelo>
        get() = if (suelo.firmaManual != null && suelo.firmaManual == firmaZonas) suelo.manual else emptyMap()

    fun sueloDeZona(zona: Int): MuestraSuelo? = AnalisisSuelo.deZona(zona, sueloManual, sueloPorZona)

    /**
     * Productividad relativa de cada zona (1,00 = lo típico) para el rinde objetivo: el potencial o
     * el rinde relativo de la base; si no, el rinde medido en los mapas; si no hay mapas, 1,00.
     */
    val productividadZonas: Map<Int, Double>
        get() {
            val z = zonas ?: return emptyMap()
            if (fuenteZonas == FuenteZonas.CAMPANAS || fuenteZonas == FuenteZonas.RINDE) {
                return z.zonas.associate { it.zona to it.indicePromedio }
            }
            val porMapa = rindePorZona.values.filter { it.isNotEmpty() }
            if (porMapa.isEmpty()) return emptyMap()
            return z.zonas.associate { zona ->
                zona.zona to porMapa.mapNotNull { rz ->
                    val media = rz.sumOf { it.rindeTHa * it.puntos } / rz.sumOf { it.puntos }.coerceAtLeast(1)
                    rz.firstOrNull { it.zona == zona.zona }?.let { it.rindeTHa / media }
                }.ifEmpty { listOf(1.0) }.average()
            }
        }

    val sugerencias: List<DosisSugeridaZona>
        get() = zonas?.let { z -> DosisSugerida.calcular(parametrosDosis, z.zonas.map { it.zona }, productividadZonas, ::sueloDeZona) }.orEmpty()

    /** Análisis de suelo, rinde por zona y parámetros de la dosis, para el LEEME. */
    fun notasAgronomicas(): List<String> = buildList {
        val z = zonas ?: return@buildList
        val conSuelo = z.zonas.mapNotNull { zona -> sueloDeZona(zona.zona)?.takeIf { !it.vacia }?.let { zona.zona to it } }
        if (conSuelo.isNotEmpty()) {
            add("Análisis de suelo por zona (P Bray ppm · N-nitratos 0-60 cm kg/ha · MO % · pH):")
            conSuelo.forEach { (n, m) ->
                fun f(v: Double?, d: Int) = v?.let { String.format(Locale.US, "%.${d}f", it) } ?: "-"
                add("  Zona $n: ${f(m.pBrayPpm, 1)} · ${f(m.nNitratosKgHa, 0)} · ${f(m.materiaOrganicaPct, 1)} · ${f(m.ph, 1)}")
            }
        }
        rindes.forEach { mapa ->
            val rz = rindePorZona[mapa.id].orEmpty()
            if (rz.isNotEmpty()) add("Rinde ${mapa.etiqueta} por zona: " + rz.joinToString(" · ") { String.format(Locale.US, "Zona %d %.2f t/ha", it.zona, it.rindeTHa) })
        }
        if (conSuelo.isNotEmpty()) {
            val p = parametrosDosis
            add(
                String.format(
                    Locale.US,
                    "Dosis sugerida (a verificar con el agrónomo): %s, rinde objetivo %.1f t/ha, N %.0f kg/t − nitratos, P por %s (umbral Bray %.0f ppm, %.1f kg P/ppm), %s.",
                    p.cultivo.nombre, p.rindeObjetivoTHa, p.nPorToneladaKg, p.estrategiaP.nombre.lowercase(), p.umbralPBrayPpm, p.kgPPorPpm, p.producto.nombre
                )
            )
        }
    }
}

class PrescripcionViewModel(
    private val fuente: FuenteSentinel,
    /** Fecha de hoy (yyyy-MM-dd): define qué campañas ya terminaron. */
    private val hoy: () -> String = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
    /** Mapas de rinde, suelo y parámetros por lote; null en pruebas que no los usan. */
    private val almacen: AlmacenAgronomia? = null
) : ViewModel() {

    /** Imágenes del backend de GRANEX: alcanza con iniciar sesión (no hace falta cuenta de Copernicus). */
    val usaCuentaGranex: Boolean get() = fuente.usaCuentaGranex

    private val _estado = MutableStateFlow<EstadoPrescripcion?>(null)
    val estado: StateFlow<EstadoPrescripcion?> = _estado.asStateFlow()

    private var trabajoEscenas: Job? = null
    private var trabajoImagen: Job? = null
    private var trabajoZonas: Job? = null
    private var trabajoCampanas: Job? = null
    private var trabajoRelieve: Job? = null
    private var trabajoRinde: Job? = null

    fun abrir(lote: LoteSatelital) {
        val configurada = fuente.configurada()
        val temporada = _estado.value?.temporada ?: Temporada.VERANO
        val disponibles = Campanas.terminadas(hoy(), temporada)
        _estado.value = EstadoPrescripcion(
            lote = lote,
            configurada = configurada,
            temporada = temporada,
            campanasDisponibles = disponibles,
            campanasElegidas = disponibles.takeLast(Campanas.POR_DEFECTO).map { it.etiqueta }.toSet(),
            escenas = if (configurada) Carga.Cargando else Carga.Fallo(fuente.mensajeSinConfigurar)
        )
        if (configurada) {
            cargarEscenas()
            cargarCampanas()
        }
        cargarAgronomia(lote)
    }

    /** Mapas de rinde, suelo y parámetros guardados del lote. */
    private fun cargarAgronomia(lote: LoteSatelital) {
        val a = almacen ?: return
        viewModelScope.launch {
            val (rindes, suelo, parametros) = withContext(Dispatchers.IO) {
                Triple(a.rindes(lote.id), a.suelo(lote.id), a.parametros(lote.id))
            }
            _estado.update { e ->
                if (e?.lote != lote) e
                else e.copy(rindes = rindes, suelo = suelo, parametrosDosis = parametros ?: e.parametrosDosis)
            }
            if (rindes.isNotEmpty()) recalcularConRindes() else actualizarDerivados()
        }
    }

    fun cerrar() {
        trabajoEscenas?.cancel()
        trabajoImagen?.cancel()
        trabajoZonas?.cancel()
        trabajoCampanas?.cancel()
        trabajoRelieve?.cancel()
        trabajoRinde?.cancel()
        _estado.value = null
    }

    fun elegirFuenteZonas(f: FuenteZonas) {
        val e = _estado.value ?: return
        if (e.fuenteZonas == f) return
        _estado.value = e.copy(fuenteZonas = f, zonificacion = null, recomendacion = null)
        if (f == FuenteZonas.FECHA && e.imagen == null) {
            (e.escenas as? Carga.Lista)?.valor?.firstOrNull { it.nubesPct <= 10 }?.let(::elegirEscena)
        }
        if (f == FuenteZonas.CAMPANAS) cargarCampanas()
        if (f == FuenteZonas.RELIEVE && (e.relieve == null || e.relieve is Carga.Fallo)) cargarRelieve()
        if (f == FuenteZonas.RINDE) calcularPotencialRinde()
        rezonificar()
    }

    fun reintentarRelieve() = cargarRelieve()

    /** El terreno no cambia: el repositorio lo guarda en disco y no se vuelve a pedir. */
    private fun cargarRelieve() {
        val e = _estado.value ?: return
        if (!e.configurada) return
        _estado.value = e.copy(relieve = Carga.Cargando, resumenRelieve = null)
        trabajoRelieve?.cancel()
        trabajoRelieve = viewModelScope.launch {
            val resultado = fuente.relieve(e.lote)
            val (imagen, resumen) = resultado.getOrNull()?.let { r ->
                withContext(Dispatchers.Default) { r.comoImagen(e.lote.coordenadas) to AnalisisRelieve.resumir(r, e.lote.coordenadas) }
            } ?: (null to null)
            _estado.update { actual ->
                if (actual?.lote != e.lote) actual
                else actual.copy(
                    relieve = when {
                        imagen != null && resumen != null -> Carga.Lista(imagen)
                        imagen != null -> Carga.Fallo("El modelo de elevación no tiene datos dentro del lote.")
                        else -> Carga.Fallo(resultado.exceptionOrNull()?.message ?: "No se pudo obtener el relieve.")
                    },
                    resumenRelieve = resumen
                )
            }
            rezonificar()
        }
    }

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

    /** Ancho de labor de la máquina (null = celdas cuadradas al norte, como antes). */
    fun cambiarAnchoLabor(anchoM: Double?) {
        val e = _estado.value ?: return
        cambiarConfig { it.copy(anchoLaborM = anchoM, rumboGrados = anchoM?.let { e.sentido.rumbo(e.lote.coordenadas) }) }
    }

    fun cambiarSentido(sentido: SentidoSiembra) {
        val e = _estado.value ?: return
        _estado.value = e.copy(sentido = sentido)
        if (e.config.anchoLaborM != null) cambiarConfig { it.copy(rumboGrados = sentido.rumbo(e.lote.coordenadas)) }
    }

    fun cambiarVelocidad(kmH: Int) = _estado.update { it?.copy(velocidadKmH = kmH) }

    fun cambiarRespuesta(segundos: Int) = _estado.update { it?.copy(respuestaS = segundos) }

    /** Índice del pico de las campañas; null = automático. No vuelve a bajar nada: recalcula. */
    fun elegirIndiceCampanas(indice: IndiceVegetacion?) {
        val e = _estado.value ?: return
        if (e.indiceCampanas == indice) return
        _estado.value = e.copy(indiceCampanas = indice)
        trabajoCampanas?.cancel()
        trabajoCampanas = viewModelScope.launch { calcularPotencial() }
    }

    /** Suma o quita una campaña del potencial (siempre queda al menos una). */
    fun alternarCampana(etiqueta: String) {
        val e = _estado.value ?: return
        val elegidas = if (etiqueta in e.campanasElegidas) e.campanasElegidas - etiqueta else e.campanasElegidas + etiqueta
        if (elegidas.isEmpty()) return
        _estado.value = e.copy(campanasElegidas = elegidas)
        cargarCampanas()
    }

    /**
     * Baja las campañas elegidas que falten, de a una (la cuota admite pocos pedidos simultáneos), y
     * recalcula el potencial cuando están todas.
     */
    private fun cargarCampanas() {
        val e = _estado.value ?: return
        if (!e.configurada || e.fuenteZonas != FuenteZonas.CAMPANAS) return
        trabajoCampanas?.cancel()
        trabajoCampanas = viewModelScope.launch {
            val pendientes = e.campanasDisponibles.filter { it.etiqueta in e.campanasElegidas && e.campanas[it.etiqueta] !is Carga.Lista }
            for (c in pendientes) {
                _estado.update { it?.copy(campanas = it.campanas + (c.etiqueta to Carga.Cargando)) }
                val r = fuente.campana(e.lote, c)
                _estado.update { actual ->
                    if (actual?.lote != e.lote) actual
                    else actual.copy(
                        campanas = actual.campanas + (c.etiqueta to r.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo bajar la campaña.") }))
                    )
                }
            }
            calcularPotencial()
        }
    }

    private suspend fun calcularPotencial() {
        val e = _estado.value ?: return
        val listas = e.campanasDisponibles
            .filter { it.etiqueta in e.campanasElegidas }
            .mapNotNull { (e.campanas[it.etiqueta] as? Carga.Lista)?.valor }
        if (listas.isEmpty()) {
            val fallo = e.campanas.values.filterIsInstance<Carga.Fallo>().firstOrNull()?.mensaje
            _estado.update { it?.copy(potencial = Carga.Fallo(fallo ?: "No se pudo bajar ninguna campaña.")) }
            return
        }
        _estado.update { it?.copy(potencial = Carga.Cargando) }
        val resultado = withContext(Dispatchers.Default) {
            try {
                val indice = e.indiceCampanas ?: CalculoPotencial.indiceSugerido(listas, e.lote.coordenadas)
                val rindes = if (e.sumarRindes) capasRinde(e, listas.first().grilla) else emptyList()
                Carga.Lista(CalculoPotencial.combinar(listas, e.lote.coordenadas, indice, rindes))
            } catch (ex: ErrorPotencial) {
                Carga.Fallo(ex.message ?: "No se pudo calcular el potencial.")
            }
        }
        _estado.update { if (it?.lote != e.lote) it else it.copy(potencial = resultado) }
        rezonificar()
    }

    /** Al volver de Ajustes: si ahora hay credenciales, se cargan las escenas. */
    fun reintentar() {
        val e = _estado.value ?: return
        abrir(e.lote)
    }

    private fun cargarEscenas() {
        val e = _estado.value ?: return
        trabajoEscenas?.cancel()
        trabajoEscenas = viewModelScope.launch {
            val resultado = fuente.escenas(e.lote)
            _estado.update { actual ->
                actual?.copy(
                    escenas = resultado.fold(
                        onSuccess = { Carga.Lista(it) },
                        onFailure = { Carga.Fallo(it.message ?: "No se pudieron obtener las escenas.") }
                    )
                )
            }
            // Con una fecha se propone la más reciente con el lote casi despejado (con varias campañas
            // no se baja: no hace falta y gastaría cuota)
            if (_estado.value?.fuenteZonas == FuenteZonas.FECHA) {
                resultado.getOrNull()?.firstOrNull { it.nubesPct <= 10 }?.let(::elegirEscena)
            }
        }
    }

    fun elegirEscena(escena: EscenaSentinel) {
        val e = _estado.value ?: return
        _estado.value = e.copy(escena = escena, imagen = Carga.Cargando, zonificacion = null)
        trabajoImagen?.cancel()
        trabajoImagen = viewModelScope.launch {
            val resultado = fuente.imagen(e.lote, escena.fecha)
            // Una imagen de suelo sin cultivo no sirve para prescribir: se avisa
            val sinCultivo = resultado.getOrNull()?.let { img ->
                withContext(Dispatchers.Default) {
                    EscaneoLote.resumir(img, IndiceVegetacion.NDVI, e.lote.coordenadas)?.cobertura == CoberturaLote.SUELO_DESNUDO
                }
            } ?: false
            _estado.update { actual ->
                if (actual?.escena != escena) actual
                else actual.copy(
                    imagen = resultado.fold({ Carga.Lista(it) }, { Carga.Fallo(it.message ?: "No se pudo descargar la imagen.") }),
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

    /**
     * Recalcula la recomendación de zonas y la zonificación con la base actual (fecha o campañas).
     * Mientras el usuario no elija la cantidad de zonas a mano, se usa la recomendada.
     */
    private fun rezonificar() {
        val e = _estado.value ?: return
        // Lo que se estaba calculando era de la base anterior: no debe pisar a la nueva
        trabajoZonas?.cancel()
        val base = e.baseZonas ?: return
        _estado.value = e.copy(zonificacion = Carga.Cargando)
        trabajoZonas = viewModelScope.launch {
            val (recomendacion, config, resultado) = withContext(Dispatchers.Default) {
                val rec = runCatching { Zonificador.recomendarZonas(base, e.indiceZonas, e.lote.coordenadas, e.config) }.getOrNull()
                    // La altura no tiene un "típico" con el que comparar el desvío: un lote es parejo si es plano
                    ?.let { r ->
                        if (e.fuenteZonas != FuenteZonas.RELIEVE) r
                        else (e.resumenRelieve?.plano ?: false).let { plano ->
                            r.copy(homogeneo = plano, zonas = Zonificador.cantidadRecomendada(r.explicada, plano))
                        }
                    }
                val config = if (!e.zonasElegidasPorUsuario && rec != null) e.config.copy(zonas = rec.zonas) else e.config
                val zon = try {
                    Carga.Lista(Zonificador.zonificar(base, e.indiceZonas, e.lote.coordenadas, config))
                } catch (ex: ErrorZonificacion) {
                    Carga.Fallo(ex.message ?: "No se pudieron calcular las zonas.")
                }
                Triple(rec, config, zon)
            }
            _estado.update { actual ->
                // Si mientras tanto cambió la base, este resultado es de la anterior: no se aplica
                if (actual == null || actual.fuenteZonas != e.fuenteZonas || actual.datoBase !== e.datoBase) actual
                else actual.copy(zonificacion = resultado, recomendacion = recomendacion, config = config)
            }
            actualizarDerivados()
        }
    }

    // ---------- Mapas de rinde ----------

    /** Los mapas del lote pasados a [grilla], relativos a su mediana. */
    private fun capasRinde(e: EstadoPrescripcion, grilla: com.example.utils.vra.GrillaGeografica): List<CapaRinde> =
        e.rindes.map { m -> CapaRinde(m.etiqueta, MapaRinde.relativo(MapaRinde.rasterizar(m.puntos, grilla, e.lote.coordenadas))) }

    /** Base "Rinde": potencial solo con los mapas de rinde, en la grilla de 10 m del lote. */
    private fun calcularPotencialRinde() {
        val e = _estado.value ?: return
        trabajoRinde?.cancel()
        if (e.rindes.isEmpty()) {
            _estado.update { it?.copy(potencialRinde = null) }
            return
        }
        _estado.update { it?.copy(potencialRinde = Carga.Cargando) }
        trabajoRinde = viewModelScope.launch {
            val resultado = withContext(Dispatchers.Default) {
                try {
                    val grilla = MapaRinde.grillaDelLote(e.lote.coordenadas)
                    Carga.Lista(CalculoPotencial.combinar(emptyList(), e.lote.coordenadas, rindes = capasRinde(e, grilla), grilla = grilla))
                } catch (ex: ErrorPotencial) {
                    Carga.Fallo(ex.message ?: "No se pudo usar el mapa de rinde.")
                }
            }
            _estado.update { if (it?.lote != e.lote) it else it.copy(potencialRinde = resultado) }
            rezonificar()
        }
    }

    /** Cuando cambian los mapas: la base que los usa se recalcula; si no, solo la validación por zona. */
    private fun recalcularConRindes() {
        val e = _estado.value ?: return
        when {
            e.fuenteZonas == FuenteZonas.RINDE -> calcularPotencialRinde()
            e.fuenteZonas == FuenteZonas.CAMPANAS && e.potencial != null -> {
                trabajoCampanas?.cancel()
                trabajoCampanas = viewModelScope.launch { calcularPotencial() }
            }
            else -> actualizarDerivados()
        }
    }

    /** Rinde y suelo por zona: se recalculan con cada zonificación nueva y cada archivo importado. */
    private fun actualizarDerivados() {
        val e = _estado.value ?: return
        val z = e.zonas
        if (z == null) {
            _estado.update { it?.copy(rindePorZona = emptyMap(), sueloPorZona = emptyMap()) }
            return
        }
        viewModelScope.launch {
            val (rinde, suelo) = withContext(Dispatchers.Default) {
                e.rindes.associate { it.id to MapaRinde.rindePorZona(it.puntos, z) } to AnalisisSuelo.porZona(e.suelo.puntos, z)
            }
            _estado.update { if (it?.zonas != z) it else it.copy(rindePorZona = rinde, sueloPorZona = suelo) }
        }
    }

    /** Archivo del monitor leído: se propone la columna de rinde y la unidad para confirmar. */
    fun importarRinde(archivo: String, tabla: TablaCampo) {
        val columna = tabla.buscar("yld_mass", "yld_vol", "yld", "rinde", "rend", "rto", "yield", "masa", "dry", "seco")
            ?: tabla.columnas.keys.firstOrNull()
        val unidad = columna?.let(tabla::mediana)?.let(UnidadRinde::sugerida) ?: UnidadRinde.T_HA
        val cultivo = _estado.value?.parametrosDosis?.cultivo ?: CultivoVra.MAIZ
        _estado.update { it?.copy(importacionRinde = ImportacionRinde(archivo, tabla, columna, unidad, cultivo), mensajeArchivo = null) }
    }

    fun cambiarImportacionRinde(transformar: (ImportacionRinde) -> ImportacionRinde) =
        _estado.update { e -> e?.copy(importacionRinde = e.importacionRinde?.let(transformar)) }

    fun cancelarImportacion() = _estado.update { it?.copy(importacionRinde = null, importacionSuelo = null) }

    fun avisoArchivo(mensaje: String?) = _estado.update { it?.copy(mensajeArchivo = mensaje) }

    fun confirmarImportacionRinde() {
        val e = _estado.value ?: return
        val imp = e.importacionRinde ?: return
        val columna = imp.columna ?: return
        _estado.update { it?.copy(importacionRinde = null) }
        viewModelScope.launch {
            val limpieza = withContext(Dispatchers.Default) {
                MapaRinde.limpiar(MapaRinde.puntos(imp.tabla, columna, imp.unidad), e.lote.coordenadas)
            }
            if (limpieza.puntos.isEmpty()) {
                avisoArchivo(
                    if (limpieza.fueraDelLote == imp.tabla.filas) "Ningún punto del archivo cae dentro del lote ${e.lote.nombre}."
                    else "La columna \"$columna\" no tiene rindes válidos dentro del lote."
                )
                return@launch
            }
            val mapa = MapaRindeLote(
                id = System.currentTimeMillis().toString(),
                etiqueta = listOf(imp.cultivo.nombre, imp.campana.trim()).filter { it.isNotEmpty() }.joinToString(" "),
                cultivo = imp.cultivo,
                puntos = limpieza.puntos,
                descartados = limpieza.descartados,
                archivo = imp.archivo
            )
            withContext(Dispatchers.IO) { almacen?.guardarRinde(e.lote.id, mapa) }
            _estado.update {
                if (it?.lote != e.lote) it
                else it.copy(
                    rindes = it.rindes + mapa,
                    mensajeArchivo = String.format(
                        Locale("es", "AR"), "%s: %,d puntos dentro del lote (%,d descartados), mediana %.2f t/ha.",
                        mapa.etiqueta, mapa.puntos.size, mapa.descartados, mapa.medianaTHa
                    )
                )
            }
            recalcularConRindes()
        }
    }

    fun borrarRinde(id: String) {
        val e = _estado.value ?: return
        _estado.update { it?.copy(rindes = it.rindes.filter { m -> m.id != id }, mensajeArchivo = null) }
        viewModelScope.launch(Dispatchers.IO) { almacen?.borrarRinde(e.lote.id, id) }
        recalcularConRindes()
    }

    fun alternarSumarRindes() {
        _estado.update { it?.copy(sumarRindes = !it.sumarRindes) }
        recalcularConRindes()
    }

    // ---------- Análisis de suelo ----------

    fun importarSuelo(archivo: String, tabla: TablaCampo) {
        val columnas = AnalisisSuelo.columnasSugeridas(tabla)
        _estado.update { it?.copy(importacionSuelo = ImportacionSuelo(archivo, tabla, columnas), mensajeArchivo = null) }
    }

    fun cambiarImportacionSuelo(columnas: ColumnasSuelo) =
        _estado.update { e -> e?.copy(importacionSuelo = e.importacionSuelo?.copy(columnas = columnas)) }

    /** Los puntos nuevos reemplazan a los anteriores del lote. */
    fun confirmarImportacionSuelo() {
        val e = _estado.value ?: return
        val imp = e.importacionSuelo ?: return
        val puntos = AnalisisSuelo.puntos(imp.tabla, imp.columnas)
        if (puntos.isEmpty()) {
            _estado.update { it?.copy(mensajeArchivo = "Ninguna fila tiene análisis en las columnas elegidas.") }
            return
        }
        val dentro = e.zonas?.let { z -> puntos.count { MapaRinde.zonaDe(it.lat, it.lon, z) != null } }
        guardarSuelo(e.suelo.copy(puntos = puntos))
        _estado.update {
            it?.copy(
                importacionSuelo = null,
                mensajeArchivo = "${puntos.size} puntos de muestreo" + (dentro?.let { d -> ", $d dentro de las zonas." } ?: ".")
            )
        }
    }

    fun borrarPuntosSuelo() {
        val e = _estado.value ?: return
        guardarSuelo(e.suelo.copy(puntos = emptyList()))
    }

    /** Valores de laboratorio cargados a mano para una zona (valen para la zonificación actual). */
    fun cambiarSueloManual(zona: Int, muestra: MuestraSuelo) {
        val e = _estado.value ?: return
        val firma = e.firmaZonas ?: return
        val anteriores = if (e.suelo.firmaManual == firma) e.suelo.manual else emptyMap()
        guardarSuelo(e.suelo.copy(manual = anteriores + (zona to muestra), firmaManual = firma))
    }

    private fun guardarSuelo(suelo: SueloLote) {
        val e = _estado.value ?: return
        _estado.update { it?.copy(suelo = suelo) }
        viewModelScope.launch(Dispatchers.IO) { almacen?.guardarSuelo(e.lote.id, suelo) }
        actualizarDerivados()
    }

    // ---------- Dosis sugerida ----------

    fun cambiarParametros(transformar: (ParametrosDosis) -> ParametrosDosis) {
        val e = _estado.value ?: return
        val nuevos = transformar(e.parametrosDosis)
        _estado.update { it?.copy(parametrosDosis = nuevos) }
        viewModelScope.launch(Dispatchers.IO) { almacen?.guardarParametros(e.lote.id, nuevos) }
    }

    /**
     * Pasa la dosis sugerida de cada zona al campo de fertilizante (las semillas no se tocan). Las
     * zonas a las que les falta un análisis quedan como estaban. Devuelve cuántas se completaron.
     */
    fun aplicarSugerencia(): Int {
        val e = _estado.value ?: return 0
        val listas = e.sugerencias.filter { it.productoKgHa != null }
        if (listas.isEmpty()) return 0
        val dosis = e.dosis + listas.associate { s -> s.zona to DosisZona(s.zona, s.productoKgHa, e.dosis[s.zona]?.semillasHa) }
        val producto = e.producto.ifBlank { e.parametrosDosis.producto.takeIf { it != com.example.utils.vra.ProductoFertilizante.OTRO }?.nombre.orEmpty() }
        _estado.update { it?.copy(dosis = dosis, producto = producto) }
        return listas.size
    }

    fun cambiarLabor(labor: Labor) = _estado.update { it?.copy(labor = labor) }

    fun cambiarUbicacion(ubicacion: UbicacionFertilizante) = _estado.update { it?.copy(ubicacionFertilizante = ubicacion) }

    fun cambiarDistanciaSurcos(metros: Double) = _estado.update { it?.copy(distanciaSurcosM = metros) }

    fun cambiarProducto(producto: String) = _estado.update { it?.copy(producto = producto) }

    fun cambiarDosis(zona: Int, fertilizanteKgHa: Int?, semillasHa: Int?) = _estado.update { e ->
        e?.copy(dosis = e.dosis + (zona to DosisZona(zona, fertilizanteKgHa, semillasHa)))
    }

    /** Archivos para el monitor elegido (para copiarlos sueltos al pendrive). */
    fun archivosMonitor(): Result<List<ArchivoExportado>> = generar { p, perfil -> ExportacionPrescripcion.paraMonitor(p, perfil) }

    /** ZIP para el monitor elegido, con un LEEME con las instrucciones de carga. */
    fun zipMonitor(): Result<ByteArray> = generar { p, perfil ->
        ExportacionPrescripcion.zip(
            ExportacionPrescripcion.paraMonitor(p, perfil) +
                ArchivoExportado("LEEME.txt", ExportacionPrescripcion.leeme(p, perfil).toByteArray(Charsets.UTF_8))
        )
    }

    /** ZIP con todos los formatos (shapefile, ISO-XML y GeoJSON) en carpetas. */
    fun zipTodos(): Result<ByteArray> = generar { p, perfil -> ExportacionPrescripcion.zip(ExportacionPrescripcion.todosLosFormatos(p, perfil)) }

    fun nombreArchivo(sufijo: String): String {
        val e = _estado.value
        val base = e?.let { ExportacionPrescripcion.nombreCorto(it.lote.nombre) } ?: "PRESCRIP"
        return "${base}_${e?.fechaImagen.orEmpty()}$sufijo"
    }

    private fun <T> generar(accion: (Prescripcion, PerfilMonitor) -> T): Result<T> {
        val e = _estado.value ?: return Result.failure(IllegalStateException("No hay prescripción abierta."))
        val p = e.prescripcion() ?: return Result.failure(IllegalStateException("Primero elige una imagen y calcula las zonas."))
        val problemas = p.problemas(e.perfil)
        if (problemas.isNotEmpty()) return Result.failure(IllegalStateException(problemas.first()))
        return runCatching { accion(p, e.perfil) }
    }
}
