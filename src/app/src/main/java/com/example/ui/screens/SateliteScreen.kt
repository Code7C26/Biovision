package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import com.example.ui.rediseno.EncabezadoR
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.components.mapa.EventoMapa
import com.example.ui.components.mapa.GisMapController
import com.example.ui.components.mapa.GisMapHolder
import com.example.ui.theme.AgroGreenLight
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.EscaneoViewModel
import com.example.ui.viewmodel.EstadoEscaneo
import com.example.ui.viewmodel.BusquedaUbicacionState
import com.example.ui.viewmodel.DestinoMapa
import com.example.ui.viewmodel.CapaSatelital
import com.example.ui.viewmodel.EdicionLote
import com.example.ui.viewmodel.MapaBaseState
import com.example.ui.viewmodel.DeteccionProvincia
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.ui.viewmodel.SateliteUiState
import com.example.ui.viewmodel.SateliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SateliteScreen(
    viewModel: SateliteViewModel,
    mapaHolder: GisMapHolder,
    prescripcionViewModel: PrescripcionViewModel,
    escaneoViewModel: EscaneoViewModel,
    onNavigateToSettings: () -> Unit,
    /** Rediseño: abre la Agenda con los gastos del lote elegido. */
    onAbrirGastosLote: ((com.example.data.repository.LoteSatelital) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val escaneo by escaneoViewModel.estado.collectAsStateWithLifecycle()
    val busqueda by viewModel.busqueda.collectAsStateWithLifecycle()
    val destinoMapa by viewModel.destinoMapa.collectAsStateWithLifecycle()
    val edicion by viewModel.edicion.collectAsStateWithLifecycle()
    val areaEnVivo by viewModel.areaEnVivo.collectAsStateWithLifecycle()
    val mapaBase by viewModel.mapaBase.collectAsStateWithLifecycle()
    val avisoMapa by viewModel.avisoMapa.collectAsStateWithLifecycle()
    val prescripcion by prescripcionViewModel.estado.collectAsStateWithLifecycle()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showNewLoteDialog by rememberSaveable { mutableStateOf(false) }
    var nombreLoteInput by rememberSaveable { mutableStateOf("") }
    var provinciaLoteInput by rememberSaveable { mutableStateOf("") }
    // true cuando el usuario escribió la provincia: la detección automática ya no la pisa
    var provinciaEditada by rememberSaveable { mutableStateOf(false) }
    val deteccionProvincia by viewModel.deteccionProvincia.collectAsStateWithLifecycle()
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showDescartarDialog by rememberSaveable { mutableStateOf(false) }
    var showEliminarDialog by rememberSaveable { mutableStateOf(false) }

    // Dibujar o editar un lote siempre se hace con el mapa a pantalla completa
    val pantallaCompleta = isFullscreen || edicion != null || prescripcion != null

    // La WebView se crea después de dibujar la pantalla: la primera del proceso inicializa Chromium en
    // el hilo principal y, hecha durante la composición, congelaba la apertura de Satélite
    val context = LocalContext.current
    val gisMap = mapaHolder.controller
    LaunchedEffect(mapaHolder) {
        if (mapaHolder.controller == null) {
            withFrameNanos { }
            withFrameNanos { }
            mapaHolder.obtener(context)
        }
    }

    // Fuera de pantalla el mapa se pausa (timers, animaciones y descargas)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(gisMap, lifecycleOwner) {
        if (gisMap == null) return@DisposableEffect onDispose { }
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> gisMap.reanudar()
                Lifecycle.Event.ON_PAUSE -> gisMap.pausar()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observador)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observador)
            gisMap.pausar()
        }
    }

    val abrirGuardado: () -> Unit = {
        val e = edicion
        val state = uiState as? SateliteUiState.Success
        if (e != null && e.puedeGuardar) {
            val origen = e.loteOrigen?.takeIf { it.editable }
            nombreLoteInput = origen?.nombre ?: "Lote ${(state?.lotes?.count { it.editable } ?: 0) + 1}"
            val provinciaGuardada = origen?.provincia?.takeIf { it.isNotBlank() }
            provinciaLoteInput = provinciaGuardada.orEmpty()
            provinciaEditada = provinciaGuardada != null
            // Lote nuevo (o sin provincia): se detecta por la ubicación del polígono
            if (provinciaGuardada == null) viewModel.detectarProvincia()
            showNewLoteDialog = true
        }
    }
    val cancelarEdicion: () -> Unit = {
        if (edicion?.puedeDeshacer == true) showDescartarDialog = true else viewModel.cancelarEdicion()
    }

    BackHandler(enabled = edicion != null) { cancelarEdicion() }
    BackHandler(enabled = prescripcion != null) { prescripcionViewModel.cerrar() }
    BackHandler(enabled = edicion == null && isFullscreen) { isFullscreen = false }

    // Chips de contexto: lote elegido, superficie y fecha del escaneo que se ve en el mapa
    val chipsSatelite = (uiState as? SateliteUiState.Success)?.let { s ->
        val lote = s.loteSeleccionado ?: return@let listOf(ChipEncabezado("Sin lotes", icono = Icons.Default.Landscape))
        buildList {
            // Nombre corto: sin "Lote" ni la localidad entre paréntesis
            val nombreCorto = lote.nombre.substringBefore(" · ").substringBefore(" (")
                .removePrefix("Lote ").trim().ifEmpty { lote.nombre }.take(22)
            add(ChipEncabezado(nombreCorto, icono = Icons.Default.Landscape))
            add(ChipEncabezado(formatearHa(lote.areaHectareas), icono = Icons.Default.CropFree))
            escaneo?.takeIf { it.lote.id == lote.id }?.fecha?.let { fecha ->
                add(ChipEncabezado("Sentinel-2 " + fechaCorta(fecha), icono = Icons.Default.SatelliteAlt))
            }
        }
    }.orEmpty()

    Scaffold(
        // El encabezado maneja la barra de estado; el margen inferior ya lo da la barra de navegación
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!pantallaCompleta) {
                // Rediseño: "Campo", con el lote elegido, su superficie y la fecha del escaneo
                EncabezadoR(
                    titulo = "Campo",
                    subtitulo = chipsSatelite.joinToString(" · ") { it.texto }.ifEmpty { "Tus lotes y el escaneo satelital" }
                ) {
                    IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Cómo funciona",
                            tint = AgroGreenPrimary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (pantallaCompleta) PaddingValues(0.dp) else paddingValues)
                // Sin encabezado (mapa a pantalla completa, edición, prescripción) el contenido no debe quedar bajo la barra de estado
                .then(if (pantallaCompleta) Modifier.statusBarsPadding() else Modifier)
        ) {
            val estadoPrescripcion = prescripcion
            if (estadoPrescripcion != null) {
                PantallaPrescripcion(
                    estado = estadoPrescripcion,
                    viewModel = prescripcionViewModel,
                    gisMap = gisMap,
                    onCerrar = prescripcionViewModel::cerrar,
                    onIrAAjustes = onNavigateToSettings
                )
            } else when (val state = uiState) {
                is SateliteUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AgroGreenPrimary)
                    }
                }
                is SateliteUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is SateliteUiState.Success -> {
                    SateliteContent(
                        state = state,
                        viewModel = viewModel,
                        gisMap = gisMap,
                        edicion = edicion,
                        areaEnVivo = areaEnVivo,
                        mapaBase = mapaBase,
                        avisoMapa = avisoMapa,
                        isFullscreen = pantallaCompleta,
                        onFullscreenChange = { isFullscreen = it },
                        busqueda = busqueda,
                        destinoMapa = destinoMapa,
                        onGuardarClick = abrirGuardado,
                        onCancelarEdicionClick = cancelarEdicion,
                        onEliminarLoteClick = { showEliminarDialog = true },
                        onAbrirPrescripcion = { state.loteSeleccionado?.let(prescripcionViewModel::abrir) },
                        escaneo = escaneo,
                        escaneoViewModel = escaneoViewModel,
                        onIrAAjustes = onNavigateToSettings,
                        onAbrirGastos = onAbrirGastosLote?.let { abrir -> { state.loteSeleccionado?.let(abrir) } }
                    )
                }
            }
        }
    }

    // La provincia detectada completa el campo mientras el usuario no lo haya tocado
    LaunchedEffect(deteccionProvincia, showNewLoteDialog) {
        val d = deteccionProvincia
        if (showNewLoteDialog && !provinciaEditada && d is DeteccionProvincia.Detectada) provinciaLoteInput = d.provincia
    }

    if (showNewLoteDialog) {
        val e = edicion
        val cerrar = {
            showNewLoteDialog = false
            viewModel.cerrarDeteccionProvincia()
        }
        DialogoGuardarLote(
            editando = e?.loteOrigen?.editable == true,
            nombre = nombreLoteInput,
            onNombre = { nombreLoteInput = it },
            provincia = provinciaLoteInput,
            onProvincia = {
                provinciaLoteInput = it
                provinciaEditada = true
            },
            deteccion = deteccionProvincia,
            superficieHa = e?.areaHa,
            vertices = e?.vertices?.size ?: 0,
            onGuardar = {
                viewModel.guardarLote(nombreLoteInput, provinciaLoteInput)
                cerrar()
            },
            onCancelar = cerrar
        )
    }

    if (showDescartarDialog) {
        AlertDialog(
            onDismissRequest = { showDescartarDialog = false },
            title = { Text("¿Descartar el trazado?") },
            text = { Text("Se perderán los vértices que marcaste.") },
            confirmButton = {
                TextButton(onClick = {
                    showDescartarDialog = false
                    viewModel.cancelarEdicion()
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDescartarDialog = false }) { Text("Seguir editando") }
            }
        )
    }

    if (showEliminarDialog) {
        val lote = (uiState as? SateliteUiState.Success)?.loteSeleccionado
        AlertDialog(
            onDismissRequest = { showEliminarDialog = false },
            title = { Text("¿Eliminar el lote?") },
            text = { Text("Se eliminará \"${lote?.nombre.orEmpty()}\" del teléfono. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showEliminarDialog = false
                    lote?.let(viewModel::eliminarLote)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEliminarDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showHelpDialog) {
        DialogoAyudaSatelite(onDismiss = { showHelpDialog = false })
    }
}

/** Ayuda de índices y dibujo de lotes. */
@Composable
internal fun DialogoAyudaSatelite(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        // Se cierra con la X, con atrás y tocando fuera del recuadro
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        // Sin padding exterior: un margen dentro del Dialog contaría como "adentro" y no lo cerraría al tocarlo
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("dialogo_ayuda")
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 24.dp)
                    .heightIn(max = 480.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // weight: un título largo se parte en dos líneas en vez de empujar la X fuera del recuadro
                    Text(
                        "Ayuda: Índices Satelitales",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("cerrar_ayuda")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar ayuda")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // La X va pegada a la esquina (end = 8.dp arriba); el texto conserva su margen de 24 dp
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 16.dp)) {
                    item {
                        Text(
                            "Origen de los datos",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Los índices vienen de Sentinel-2, de la Agencia Espacial Europea (Copernicus): píxeles de 10 m y una pasada cada 2 o 3 días. Las imágenes se piden a través del servidor de GRANEX, por eso hace falta iniciar sesión. La imagen de fondo del mapa es un mosaico del proveedor de mapas, de fecha variable.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Text(
                            "Cómo dibujar un lote",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Toca \"Crear lote\" y marca las esquinas tocando el mapa. Con \"Mira\" agregas el punto que queda bajo la cruz central, y con \"GPS\" tu posición actual. Arrastra un vértice para ajustarlo (queda por encima del dedo para verlo) o el punto intermedio de un lado para agregar otro. Toca el primer vértice (verde) para terminar.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Text(
                            "¿Qué es el NDVI?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "El Índice de Vegetación de Diferencia Normalizada mide el vigor de los cultivos. Valores de 0,6 a 0,9 indican cultivos densos; por debajo de 0,25 el lote está sin cultivo activo (suelo desnudo, rastrojo o barbecho).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Text(
                            "NDRE y GNDVI",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "El NDRE usa el borde rojo (20 m) y sigue mejor la clorofila y el nitrógeno en cultivos avanzados, cuando el NDVI se satura. El GNDVI usa la banda verde y es sensible a la clorofila en estadios intermedios.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Text(
                            "Escala de colores y nubes",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "\"Absoluto\" usa la misma escala en todas las fechas, para comparar el lote en el tiempo. \"Zonas del lote\" ajusta la escala a los valores del propio lote, para ver dónde rinde más y menos. Los píxeles con nubes, sombras o agua quedan sin color, y las fechas con más del 30 % del lote sin dato no se ofrecen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SateliteContent(
    state: SateliteUiState.Success,
    viewModel: SateliteViewModel,
    gisMap: GisMapController?,
    edicion: EdicionLote?,
    areaEnVivo: Double?,
    mapaBase: MapaBaseState,
    avisoMapa: String?,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    busqueda: BusquedaUbicacionState,
    destinoMapa: DestinoMapa?,
    onGuardarClick: () -> Unit,
    onCancelarEdicionClick: () -> Unit,
    onEliminarLoteClick: () -> Unit,
    onAbrirPrescripcion: () -> Unit,
    escaneo: EstadoEscaneo?,
    escaneoViewModel: EscaneoViewModel,
    onIrAAjustes: () -> Unit,
    onAbrirGastos: (() -> Unit)? = null
) {
    var expandedDropdown by remember { mutableStateOf(false) }
    var showGeoJsonDialog by rememberSaveable { mutableStateOf(false) }
    var mapOpacity by rememberSaveable { mutableFloatStateOf(0.85f) }

    val lote = state.loteSeleccionado

    // El escaneo Sentinel-2 sigue al lote elegido (y a su contorno, si se editó)
    LaunchedEffect(lote) { escaneoViewModel.abrir(lote) }
    // Todo lo que ve el mapa en un solo valor: si cambia la capa, la fecha, el índice o la escala,
    // cambia el texto y GisMapEffects lo reenvía de inmediato (sin esperar un toque en el mapa)
    val contenidoCapa = remember(state.capaActiva, escaneo) {
        // "Solo mapa base" muestra la imagen del proveedor; el resto, lo elegido en el escaneo (10 m)
        (if (state.capaActiva == CapaSatelital.RGB) null else capaSentinelJson(escaneo))
            ?: """{"capa":"${state.capaActiva.id}","sinDatos":true}"""
    }

    // La foto Sentinel de la fecha elegida tapa el mapa base alrededor del lote ("Solo mapa base" la quita)
    val fotoDeFondo = if (state.capaActiva == CapaSatelital.RGB) null else fotoFondo(escaneo)
    val fondo = remember(fotoDeFondo) { fotoDeFondo?.let(::fondoSentinelJson) }

    val deteccionLote by viewModel.deteccionLote.collectAsStateWithLifecycle()
    if (gisMap != null) {
        // El centro del mapa decide de qué fecha es la foto de Esri que se está viendo
        LaunchedEffect(gisMap, viewModel) {
            snapshotFlow { gisMap.centro }.collect { c -> c?.let { (lat, lon) -> viewModel.centroMovido(lat, lon) } }
        }
        GisMapEffects(
            controller = gisMap,
            lotes = state.lotes,
            loteSeleccionado = state.loteSeleccionado,
            capaJson = contenidoCapa,
            opacity = { mapOpacity },
            edicion = edicion,
            mapaBase = mapaBase,
            destinoMapa = destinoMapa,
            onDestinoMapaAplicado = viewModel::destinoMapaAplicado,
            deteccion = deteccionLote,
            fondoJson = fondo
        )

        // Eventos del mapa → ViewModel. El controlador sobrevive a la pantalla, así que el receptor
        // se registra al entrar y se quita al salir
        val edicionActual by rememberUpdatedState(edicion)
        val alGuardar by rememberUpdatedState(onGuardarClick)
        DisposableEffect(gisMap, viewModel) {
            gisMap.onEvento = { evento ->
                when (evento) {
                    is EventoMapa.Toque -> viewModel.agregarVertice(evento.lat, evento.lon)
                    is EventoMapa.VerticeTocado -> {
                        val e = edicionActual
                        val cierra = e != null && evento.indice == 0 && e.vertices.size >= 3 &&
                            e.seleccionado == null && !e.modoMira && e.puedeGuardar
                        if (cierra) alGuardar() else viewModel.seleccionarVertice(evento.indice)
                    }
                    is EventoMapa.Arrastrando ->
                        viewModel.previsualizarArrastre(evento.indice, evento.lat, evento.lon, evento.insercion)
                    is EventoMapa.VerticeSoltado ->
                        if (evento.insercion) viewModel.insertarVertice(evento.indice, evento.lat, evento.lon)
                        else viewModel.moverVertice(evento.indice, evento.lat, evento.lon)
                    is EventoMapa.LoteTocado -> viewModel.seleccionarLoteId(evento.id)
                    is EventoMapa.DetectarEn -> viewModel.detectarEn(evento.lat, evento.lon)
                    is EventoMapa.ProveedorFallido -> viewModel.proveedorFallido(evento.proveedor)
                }
            }
            onDispose { gisMap.onEvento = {} }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("satelite_screen_scroll"),
        contentPadding = if (isFullscreen) PaddingValues(0.dp) else PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = if (isFullscreen) Arrangement.Top else Arrangement.spacedBy(14.dp),
        // A pantalla completa la lista no debe desplazarse: todos los gestos son del mapa
        userScrollEnabled = !isFullscreen
    ) {
        // Section 1: Saved Lots & Drawing Controls
        if (!isFullscreen) {
            item(key = "gestion_lotes") {
                // Rediseño: "Mis lotes" con la lista (tocar uno lo elige) y las acciones del elegido
                com.example.ui.rediseno.TarjetaR {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis lotes", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (state.lotes.isNotEmpty()) {
                            Text(
                                "${state.lotes.size} " + (if (state.lotes.size == 1) "lote" else "lotes") + " · " + formatearHa(state.lotes.sumOf { it.areaHectareas }),
                                fontSize = 13.sp, color = com.example.ui.rediseno.colorSecundario
                            )
                        }
                    }
                    if (lote == null) {
                        SinLotesRegistrados()
                    } else {
                        Column {
                            state.lotes.forEachIndexed { i, l ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                val elegido = l.id == lote.id
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (elegido) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                                        .clickable(onClickLabel = "Elegir ${l.nombre}") { viewModel.seleccionarLote(l) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("lote_${l.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                            .background(if (elegido) AgroGreenPrimary else MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Landscape, contentDescription = null, tint = if (elegido) Color.White else AgroGreenPrimary, modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(l.nombre, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            formatearHa(l.areaHectareas) + (l.provincia.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                            fontSize = 13.sp, color = com.example.ui.rediseno.colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (elegido) com.example.ui.rediseno.ChipEstadoR("En el mapa", com.example.ui.rediseno.Tono.OK)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = viewModel::iniciarNuevoLote,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear lote")
                        }
                        // Editar y eliminar solo existen con un lote elegido
                        if (lote != null && lote.editable) {
                            OutlinedButton(onClick = { viewModel.editarLote(lote) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar")
                            }
                            IconButton(onClick = onEliminarLoteClick) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar lote", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (lote != null) {
                            IconButton(onClick = { showGeoJsonDialog = true }) {
                                Icon(Icons.Default.Code, contentDescription = "Ver GeoJSON del lote", tint = com.example.ui.rediseno.colorSecundario)
                            }
                        }
                    }

                    // Sin lote no hay sobre qué calcular la prescripción: el botón queda deshabilitado
                    Button(
                        onClick = onAbrirPrescripcion,
                        enabled = lote != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("btn_prescripcion"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF064E3B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Agriculture, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Armar prescripción de dosis variable", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                    if (onAbrirGastos != null && lote != null) {
                        OutlinedButton(
                            onClick = onAbrirGastos,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("btn_gastos_lote"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp), tint = AgroGreenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gastos de este lote", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = com.example.ui.rediseno.colorSecundario)
                        }
                    }
                }
            }
        }

        // Section 2: mapa satelital interactivo
        item(key = "mapa") {
            Card(
                modifier = if (isFullscreen) Modifier.fillParentMaxSize() else Modifier.fillMaxWidth().height(420.dp),
                shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp),
                border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                MapaLote(
                    gisMap = gisMap,
                    state = state,
                    edicion = edicion,
                    areaEnVivo = areaEnVivo,
                    mapaBase = mapaBase,
                    avisoMapa = avisoMapa,
                    busqueda = busqueda,
                    isFullscreen = isFullscreen,
                    mapOpacity = mapOpacity,
                    onOpacityChange = { mapOpacity = it },
                    viewModel = viewModel,
                    onFullscreenChange = onFullscreenChange,
                    onGuardarClick = onGuardarClick,
                    onCancelarEdicionClick = onCancelarEdicionClick,
                    fechaFondoSentinel = fotoDeFondo?.fecha
                )
            }
        }

        if (!isFullscreen && lote != null) {
            // Escaneo Sentinel-2 (10 m): fechas, mapa, zonas, comparación y serie
            item(key = "escaneo_s2") {
                TarjetaEscaneoSentinel(escaneo, escaneoViewModel, onIrAAjustes)
            }
        }
    }

    if (showGeoJsonDialog && lote != null) {
        Dialog(onDismissRequest = { showGeoJsonDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GeoJSON de Polígono",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showGeoJsonDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = lote.geojson,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Polígono del lote en formato GeoJSON (coordenadas [longitud, latitud], WGS84). Sirve para cargarlo en otros sistemas GIS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Lugar del selector cuando todavía no hay lotes: indica cómo crear el primero. */
@Composable
private fun SinLotesRegistrados() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AgroGreenPrimary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().testTag("satelite_sin_lotes")
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.AddLocationAlt,
                contentDescription = null,
                tint = AgroGreenPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "No tenés lotes registrados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tocá \"Crear lote\" para delimitar tu primer lote en el mapa. Después vas a ver sus índices satelitales y podrás armar prescripciones de dosis variable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Diálogo para guardar el lote dibujado. La provincia llega sola por geocodificación inversa del
 * centroide; mientras se busca hay un indicador en el campo, y si falla queda vacío y editable:
 * nunca bloquea el guardado (solo el nombre es obligatorio).
 */
@Composable
internal fun DialogoGuardarLote(
    editando: Boolean,
    nombre: String,
    onNombre: (String) -> Unit,
    provincia: String,
    onProvincia: (String) -> Unit,
    deteccion: DeteccionProvincia,
    superficieHa: Double?,
    vertices: Int,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = AgroGreenPrimary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(if (editando) "Guardar cambios del lote" else "Registrar nuevo lote")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = onNombre,
                    label = { Text("Nombre del lote") },
                    modifier = Modifier.fillMaxWidth().testTag("input_nombre_lote"),
                    singleLine = true,
                    isError = nombre.isBlank()
                )
                val buscando = deteccion is DeteccionProvincia.Buscando
                OutlinedTextField(
                    value = provincia,
                    onValueChange = onProvincia,
                    label = { Text("Provincia / Región") },
                    placeholder = { Text(if (buscando) "Detectando…" else "Ej.: Santa Fe") },
                    trailingIcon = {
                        if (buscando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).testTag("cargando_provincia"),
                                strokeWidth = 2.dp,
                                color = AgroGreenPrimary
                            )
                        }
                    },
                    supportingText = {
                        val texto = when (deteccion) {
                            is DeteccionProvincia.Buscando -> "Buscando la provincia por la ubicación del lote…"
                            is DeteccionProvincia.Detectada ->
                                if (provincia == deteccion.provincia) "Detectada por la ubicación del lote (OpenStreetMap). Podés cambiarla."
                                else "Detectada: ${deteccion.provincia}. Se usa lo que escribiste."
                            is DeteccionProvincia.NoDisponible -> "${deteccion.mensaje}. Escribila a mano o dejala vacía."
                            DeteccionProvincia.Inactiva -> null
                        }
                        if (texto != null) Text(texto, modifier = Modifier.testTag("ayuda_provincia"))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("input_provincia_lote"),
                    singleLine = true
                )
                if (superficieHa != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Superficie: ${formatearHa(superficieHa)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$vertices vértices. El lote se guarda en el teléfono.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGuardar,
                enabled = nombre.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                modifier = Modifier.testTag("btn_guardar_lote")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Seguir editando")
            }
        }
    )
}
