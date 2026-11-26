package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.api.ApiKeys
import com.example.data.repository.LoteSatelital
import com.example.data.repository.SugerenciaLocalidad
import com.example.ui.components.FilaSugerenciaLocalidad
import com.example.ui.components.detachFromParent
import com.example.ui.components.mapa.GisMapController
import com.example.ui.components.mapa.jsString
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.BusquedaUbicacionState
import com.example.ui.viewmodel.CapaSatelital
import com.example.ui.viewmodel.DestinoMapa
import com.example.ui.viewmodel.DeteccionLote
import com.example.ui.viewmodel.EdicionLote
import com.example.ui.viewmodel.MapaBaseState
import com.example.ui.viewmodel.ProveedorMapa
import com.example.ui.viewmodel.SateliteUiState
import com.example.ui.viewmodel.SateliteViewModel
import com.example.ui.viewmodel.SugerenciasState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Precisión GPS mínima para usar la posición como vértice de un lote. */
private const val PRECISION_GPS_VERTICE_M = 25f

private val localeAr = Locale.forLanguageTag("es-AR")

fun formatearHa(hectareas: Double): String = String.format(localeAr, "%,.2f ha", hectareas)

private fun formatearDistancia(metros: Double): String =
    if (metros >= 1000) String.format(localeAr, "%.2f km", metros / 1000) else String.format(localeAr, "%.0f m", metros)

/**
 * Sincroniza el estado de Compose con la página del mapa. Cada aspecto se envía por separado y solo
 * cuando cambia, así que mover un vértice no redibuja los lotes ni la capa espectral.
 */
@Composable
fun GisMapEffects(
    controller: GisMapController,
    lotes: List<LoteSatelital>,
    loteSeleccionado: LoteSatelital?,
    capaJson: String,
    opacity: () -> Float,
    edicion: EdicionLote?,
    mapaBase: MapaBaseState,
    destinoMapa: DestinoMapa?,
    onDestinoMapaAplicado: (DestinoMapa) -> Unit,
    deteccion: DeteccionLote = DeteccionLote.Inactiva,
    /** Foto Sentinel de la fecha elegida que tapa el mapa base alrededor del lote; null = mapa base. */
    fondoJson: String? = null
) {
    val currentOpacity by rememberUpdatedState(opacity)
    val currentOnDestinoAplicado by rememberUpdatedState(onDestinoMapaAplicado)

    LaunchedEffect(controller, mapaBase.proveedor, mapaBase.etiquetas) {
        val clave = if (mapaBase.proveedor == ProveedorMapa.MAPTILER) ApiKeys.maptiler.orEmpty() else ""
        controller.runScript("GRANEX.setMapaBase(${jsString(mapaBase.proveedor.id)}, ${jsString(clave)}, ${mapaBase.etiquetas});")
    }

    val editandoId = edicion?.loteOrigen?.id
    LaunchedEffect(controller, lotes, loteSeleccionado?.id, editandoId) {
        controller.runScript("GRANEX.setLotes(${jsString(lotesJson(lotes, loteSeleccionado?.id, editandoId))});")
    }

    // capaJson cambia con la capa o el escaneo elegido: el efecto se relanza y la página redibuja
    LaunchedEffect(controller, capaJson) {
        controller.runScript("GRANEX.setCapa(${jsString(capaJson)});")
    }

    LaunchedEffect(controller, fondoJson) {
        controller.runScript("GRANEX.setFondo(${fondoJson?.let(::jsString) ?: "null"});")
    }

    // La opacidad cambia en cada cuadro mientras se arrastra el slider: se lee con snapshotFlow
    // (sin recomponer) y solo se cambia el estilo de la imagen
    LaunchedEffect(controller) {
        snapshotFlow { currentOpacity() }.collect { controller.runScript("GRANEX.setOpacidad($it);") }
    }

    LaunchedEffect(controller, edicion?.revision, edicion == null) {
        val json = edicion?.let { jsString(edicionJson(it)) } ?: "null"
        controller.runScript("GRANEX.setEdicion($json);")
    }

    // Detección automática: el modo de toque y el punto que se está analizando
    LaunchedEffect(controller, deteccion) {
        val script = when (deteccion) {
            DeteccionLote.EsperandoToque -> "GRANEX.setDeteccion(true);"
            is DeteccionLote.Buscando -> "GRANEX.setDeteccion(false, ${deteccion.lat}, ${deteccion.lon});"
            else -> "GRANEX.setDeteccion(false);"
        }
        controller.runScript(script)
    }

    LaunchedEffect(controller, destinoMapa) {
        destinoMapa?.let { destino ->
            controller.runScript("GRANEX.irA(${destino.lat}, ${destino.lon});")
            currentOnDestinoAplicado(destino)
        }
    }
}

private fun coordenadasJson(puntos: List<Pair<Double, Double>>) =
    JSONArray().apply { puntos.forEach { (lat, lon) -> put(JSONArray().put(lat).put(lon)) } }

private fun lotesJson(lotes: List<LoteSatelital>, seleccionado: String?, editando: String?): String {
    val lista = JSONArray()
    lotes.forEach { lote ->
        lista.put(
            JSONObject()
                .put("id", lote.id)
                .put("nombre", lote.nombre)
                .put("area", formatearHa(lote.areaHectareas))
                .put("coords", coordenadasJson(lote.coordenadas))
        )
    }
    return JSONObject()
        .put("lotes", lista)
        .put("seleccionado", seleccionado ?: JSONObject.NULL)
        .put("editando", editando ?: JSONObject.NULL)
        .toString()
}

private fun edicionJson(edicion: EdicionLote): String =
    JSONObject()
        .put("vertices", coordenadasJson(edicion.vertices))
        .put("seleccionado", edicion.seleccionado ?: JSONObject.NULL)
        .put("mira", edicion.modoMira)
        .toString()

/** Contenido del item del mapa: la WebView compartida y los controles superpuestos. */
@Composable
fun MapaLote(
    gisMap: GisMapController?,
    state: SateliteUiState.Success,
    edicion: EdicionLote?,
    areaEnVivo: Double?,
    mapaBase: MapaBaseState,
    avisoMapa: String?,
    busqueda: BusquedaUbicacionState,
    isFullscreen: Boolean,
    mapOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    viewModel: SateliteViewModel,
    onFullscreenChange: (Boolean) -> Unit,
    onGuardarClick: () -> Unit,
    onCancelarEdicionClick: () -> Unit,
    /** Fecha de la foto Sentinel que hace de fondo alrededor del lote; null = se ve el mapa base. */
    fechaFondoSentinel: String? = null
) {
    val context = LocalContext.current
    val fechaMapaBase by viewModel.fechaMapaBase.collectAsStateWithLifecycle()
    var showOpacitySlider by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sugerencias by viewModel.sugerencias.collectAsStateWithLifecycle()
    val deteccion by viewModel.deteccionLote.collectAsStateWithLifecycle()

    // Ubicación: solo al tocar un botón; como vértice exige buena precisión
    val ubicacion = remember { LocationServices.getFusedLocationProviderClient(context) }
    var ubicacionComoVertice by remember { mutableStateOf(false) }
    val usarUbicacion: (Boolean) -> Unit = { comoVertice ->
        val prioridad = if (comoVertice) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        try {
            ubicacion.getCurrentLocation(prioridad, null).addOnSuccessListener { loc ->
                when {
                    loc == null -> Toast.makeText(context, "No se pudo obtener la ubicación. Activa el GPS.", Toast.LENGTH_SHORT).show()
                    comoVertice && (!loc.hasAccuracy() || loc.accuracy > PRECISION_GPS_VERTICE_M) -> Toast.makeText(
                        context,
                        String.format(localeAr, "Precisión GPS de %.0f m: espera unos segundos a cielo abierto y vuelve a intentar.", loc.accuracy),
                        Toast.LENGTH_LONG
                    ).show()
                    else -> {
                        if (comoVertice) viewModel.agregarVertice(loc.latitude, loc.longitude)
                        viewModel.irAUbicacion(loc.latitude, loc.longitude)
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Sin permiso de ubicación.", Toast.LENGTH_SHORT).show()
        }
    }
    // Sesgo de cercanía de las sugerencias: la vista del mapa y, si todavía no hay, la última ubicación conocida
    var ubicacionDispositivo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) {
        val permitido = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (permitido) {
            try {
                ubicacion.lastLocation.addOnSuccessListener { loc -> loc?.let { ubicacionDispositivo = it.latitude to it.longitude } }
            } catch (e: SecurityException) {
                // Sin permiso: las sugerencias usan solo el centro del mapa
            }
        }
    }

    // Sin lotes el mapa arranca donde está el usuario (una vez), para dibujar el primero ahí mismo
    var centradoInicial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ubicacionDispositivo, state.lotes.isEmpty()) {
        val aca = ubicacionDispositivo
        if (!centradoInicial && state.lotes.isEmpty() && aca != null) {
            centradoInicial = true
            viewModel.irAUbicacion(aca.first, aca.second)
        }
    }

    val permisoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
        if (permisos.values.any { it }) usarUbicacion(ubicacionComoVertice)
    }
    val pedirUbicacion: (Boolean) -> Unit = { comoVertice ->
        ubicacionComoVertice = comoVertice
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (concedido) usarUbicacion(comoVertice)
        else permisoLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (gisMap != null) {
            key(gisMap) {
                AndroidView(
                    factory = { gisMap.webView.detachFromParent() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0F0C)))
        }

        if (gisMap == null || gisMap.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = AgroGreenPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Cargando mapa…", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        if (edicion == null) {
            // Capas espectrales y mapa base (arriba a la derecha)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectorMapaBase(mapaBase, viewModel)
                CapaSatelital.entries.forEach { c ->
                    val isSelected = c == state.capaActiva
                    BotonMapa(
                        icono = when (c) {
                            CapaSatelital.INDICE -> Icons.Default.Eco
                            CapaSatelital.RGB -> Icons.Default.Image
                        },
                        descripcion = c.nombre,
                        activo = isSelected,
                        onClick = { viewModel.cambiarCapa(c) },
                        modifier = Modifier.semantics {
                            selected = isSelected
                            stateDescription = if (isSelected) "Capa activa" else "Capa inactiva"
                        }
                    )
                }
            }

            if (state.capaActiva == CapaSatelital.RGB) {
                AvisoColorReal(Modifier.align(Alignment.CenterStart).padding(12.dp))
            }

            // De qué fecha es la imagen de fondo: la del mapa base puede tener más de un año
            val textoFondo = when {
                fechaFondoSentinel != null -> "Fondo: Sentinel-2 del ${formatearFechaCorta(fechaFondoSentinel)}"
                fechaMapaBase != null && mapaBase.proveedor == ProveedorMapa.ESRI ->
                    "Foto de fondo del ${formatearFechaCorta(fechaMapaBase!!)} (Esri)"
                else -> null
            }
            if (textoFondo != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 78.dp)
                        .testTag("fecha_fondo")
                ) {
                    Text(
                        textoFondo,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Opacidad de la capa espectral (arriba a la izquierda)
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BotonMapa(
                    icono = Icons.Default.Layers,
                    descripcion = "Opacidad de la capa",
                    activo = showOpacitySlider,
                    onClick = { showOpacitySlider = !showOpacitySlider }
                )
                if (showOpacitySlider) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.width(150.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Opacidad: ${(mapOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = mapOpacity,
                                onValueChange = onOpacityChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = AgroGreenPrimary, activeTrackColor = AgroGreenPrimary),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            BarraBusqueda(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.actualizarConsulta(it, gisMap?.centro ?: ubicacionDispositivo)
                },
                busqueda = busqueda,
                sugerencias = sugerencias,
                onBuscar = { viewModel.buscarUbicacion(searchQuery) },
                onSugerencia = { s ->
                    searchQuery = s.nombre
                    viewModel.elegirSugerencia(s)
                },
                onCerrarSugerencias = viewModel::cerrarSugerencias,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 64.dp, end = 64.dp)
            )

            // Abajo queda libre una franja para la atribución de los mapas
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 28.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { pedirUbicacion(false) },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = AgroGreenPrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación", modifier = Modifier.size(20.dp))
                }
                FloatingActionButton(
                    onClick = { onFullscreenChange(!isFullscreen) },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = AgroGreenPrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (deteccion == DeteccionLote.Inactiva) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Detección automática: tocar dentro de un lote y la app busca sus bordes
                    ExtendedFloatingActionButton(
                        onClick = viewModel::iniciarDeteccion,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = AgroGreenPrimary,
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        text = { Text("Detectar lote", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("btn_detectar_lote")
                    )
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.iniciarNuevoLote() },
                        containerColor = AgroGreenPrimary,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        text = { Text("Crear lote", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("btn_crear_lote_map")
                    )
                }
            } else {
                AvisoDeteccion(
                    deteccion = deteccion,
                    onCancelar = viewModel::cancelarDeteccion,
                    onReintentar = viewModel::reintentarDeteccion,
                    onDibujarAMano = {
                        viewModel.cancelarDeteccion()
                        viewModel.iniciarNuevoLote()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).testTag("aviso_deteccion")
                )
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                SelectorMapaBase(mapaBase, viewModel)
            }
            if (edicion.modoMira) MiraCentral(Modifier.align(Alignment.Center))
            BarraEdicionLote(
                edicion = edicion,
                areaEnVivo = areaEnVivo,
                onDeshacer = viewModel::deshacer,
                onRehacer = viewModel::rehacer,
                onBorrarVertice = viewModel::eliminarVerticeSeleccionado,
                onAlternarMira = viewModel::alternarModoMira,
                onAgregarEnMira = {
                    gisMap?.centro?.let { (lat, lon) -> viewModel.agregarVertice(lat, lon) }
                },
                onPuntoGps = { pedirUbicacion(true) },
                onCancelar = onCancelarEdicionClick,
                onGuardar = onGuardarClick,
                modifier = Modifier.align(Alignment.BottomCenter).testTag("drawing_toolbar")
            )
        }

        avisoMapa?.let { aviso ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 64.dp, end = 64.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        aviso,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::descartarAviso) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Estado de la detección automática: esperando el toque, buscando o el motivo de un fallo. */
@Composable
private fun AvisoDeteccion(
    deteccion: DeteccionLote,
    onCancelar: () -> Unit,
    onReintentar: () -> Unit,
    onDibujarAMano: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (deteccion) {
                DeteccionLote.EsperandoToque -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tocá en el medio del lote", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "GRANEX busca sus bordes con imágenes Sentinel-2 y después podés corregirlos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onCancelar, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
                }
                is DeteccionLote.Buscando -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AgroGreenPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Buscando los bordes del lote…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Se bajan dos fechas sin nubes de la zona: puede tardar unos 20 segundos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onCancelar, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
                }
                is DeteccionLote.Fallo -> {
                    Text("No se pudo detectar el lote", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(deteccion.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onCancelar) { Text("Cerrar") }
                        TextButton(onClick = onDibujarAMano) { Text("Dibujar a mano") }
                        if (deteccion.lat != null) TextButton(onClick = onReintentar) { Text("Reintentar") }
                    }
                }
                DeteccionLote.Inactiva -> Unit
            }
        }
    }
}

@Composable
private fun BotonMapa(
    icono: ImageVector,
    descripcion: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (activo) AgroGreenPrimary else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = if (activo) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .size(38.dp)
            .border(1.dp, if (activo) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
    ) {
        Icon(icono, contentDescription = descripcion, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SelectorMapaBase(mapaBase: MapaBaseState, viewModel: SateliteViewModel) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        BotonMapa(icono = Icons.Default.Map, descripcion = "Mapa base", activo = abierto, onClick = { abierto = true })
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            ProveedorMapa.entries.forEach { proveedor ->
                val disponible = proveedor != ProveedorMapa.MAPTILER || mapaBase.maptilerDisponible
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(proveedor.nombre)
                            if (!disponible) {
                                Text(
                                    "Requiere una clave de MapTiler en .env",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    leadingIcon = { RadioButton(selected = mapaBase.proveedor == proveedor, onClick = null, enabled = disponible) },
                    enabled = disponible,
                    onClick = {
                        viewModel.cambiarProveedor(proveedor)
                        abierto = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Rutas y localidades") },
                leadingIcon = { Checkbox(checked = mapaBase.etiquetas, onCheckedChange = null) },
                enabled = mapaBase.proveedor != ProveedorMapa.OSM,
                onClick = viewModel::alternarEtiquetas
            )
        }
    }
}

// Mismos cortes y colores que PALETAS en mapa_gis.js
/** Con "Solo mapa base" el mapa muestra la imagen base del proveedor, que no es la del escaneo. */
@Composable
private fun AvisoColorReal(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.widthIn(max = 120.dp)
    ) {
        Text(
            "Mapa base del proveedor, de fecha variable. Para ver el lote en una fecha, usá «Foto del día» en el escaneo.",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            modifier = Modifier.padding(6.dp)
        )
    }
}

/** yyyy-MM-dd → dd/MM/yyyy */
fun formatearFechaCorta(fecha: String): String =
    fecha.split("-").takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" } ?: fecha

/** Etiqueta del origen de un dato, para distinguir datos medidos de estimaciones. */
enum class OrigenDato(val etiqueta: String) {
    HISTORICO_REAL("Histórico real")
}

@Composable
fun EtiquetaOrigen(origen: OrigenDato, detalle: String, modifier: Modifier = Modifier) {
    val (fondo, texto, icono) = when (origen) {
        OrigenDato.HISTORICO_REAL -> Triple(AgroGreenPrimary.copy(alpha = 0.15f), AgroGreenPrimary, Icons.Default.SatelliteAlt)
    }
    Surface(color = fondo, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icono, contentDescription = null, tint = texto, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "${origen.etiqueta} · $detalle",
                style = MaterialTheme.typography.labelSmall,
                color = texto,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Buscador de localidades con sugerencias en vivo. La lista va dentro del mismo Column (no en un
 * DropdownMenu/Popup) para que el campo conserve el foco y el teclado mientras se escribe.
 */
@Composable
internal fun BarraBusqueda(
    query: String,
    onQueryChange: (String) -> Unit,
    busqueda: BusquedaUbicacionState,
    sugerencias: SugerenciasState,
    onBuscar: () -> Unit,
    onSugerencia: (SugerenciaLocalidad) -> Unit,
    onCerrarSugerencias: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (sugerencias.visible) 1f else 0.9f),
        shadowElevation = if (sugerencias.visible) 6.dp else 0.dp
    ) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Ciudad o lat, lon", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = AgroGreenPrimary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Words),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onBuscar()
                }),
                modifier = Modifier.fillMaxWidth().testTag("buscador_localidad"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgroGreenPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            if (busqueda.buscando || sugerencias.cargando) {
                LinearProgressIndicator(color = AgroGreenPrimary, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            busqueda.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            val errorSugerencias = sugerencias.error
            if (sugerencias.items.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp).testTag("sugerencias_localidad")) {
                    items(sugerencias.items, key = { "${it.lat},${it.lon},${it.nombre}" }) { s ->
                        FilaSugerenciaLocalidad(s) {
                            focusManager.clearFocus()
                            onSugerencia(s)
                        }
                    }
                }
            } else if (errorSugerencias != null && !sugerencias.cargando) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorSugerencias,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCerrarSugerencias) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar sugerencias", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Cruz fija en el centro del mapa: el vértice se agrega donde apunta, con precisión de pocos metros. */
@Composable
private fun MiraCentral(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(56.dp).semantics { contentDescription = "Mira central" }) {
        val c = center
        val brazo = size.minDimension / 2
        val hueco = 7.dp.toPx()
        listOf(Color.Black.copy(alpha = 0.7f) to 4.dp.toPx(), Color.White to 2.dp.toPx()).forEach { (color, ancho) ->
            drawLine(color, Offset(c.x - brazo, c.y), Offset(c.x - hueco, c.y), ancho)
            drawLine(color, Offset(c.x + hueco, c.y), Offset(c.x + brazo, c.y), ancho)
            drawLine(color, Offset(c.x, c.y - brazo), Offset(c.x, c.y - hueco), ancho)
            drawLine(color, Offset(c.x, c.y + hueco), Offset(c.x, c.y + brazo), ancho)
            drawCircle(color, radius = hueco + 5.dp.toPx(), center = c, style = Stroke(ancho))
        }
        drawCircle(AgroGoldAccent, radius = 2.dp.toPx(), center = c)
    }
}

@Composable
private fun BarraEdicionLote(
    edicion: EdicionLote,
    areaEnVivo: Double?,
    onDeshacer: () -> Unit,
    onRehacer: () -> Unit,
    onBorrarVertice: () -> Unit,
    onAlternarMira: () -> Unit,
    onAgregarEnMira: () -> Unit,
    onPuntoGps: () -> Unit,
    onCancelar: () -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val n = edicion.vertices.size
    val area = areaEnVivo ?: edicion.areaHa
    val seleccionado = edicion.seleccionado
    val (indicacion, esProblema) = when {
        edicion.autointerseccion -> "Los lados se cruzan: mueve un vértice para corregir el trazado." to true
        seleccionado != null -> "Vértice ${seleccionado + 1} seleccionado: arrástralo o bórralo." to false
        edicion.detectado && edicion.detectadoCortado ->
            "Bordes detectados, pero el lote sigue más allá de la zona analizada: completalo arrastrando los vértices." to true
        edicion.detectado ->
            "Bordes detectados con imágenes de 10 m: revisalos, arrastrá un vértice para corregir o tocá el primero (verde) para guardar." to false
        n == 0 && edicion.modoMira -> "Mueve el mapa hasta que la mira quede sobre una esquina del lote." to false
        n == 0 -> "Toca el mapa en una esquina del lote." to false
        n < 3 -> "Marca al menos 3 vértices ($n/3)." to false
        edicion.modoMira -> "$n vértices. Mueve el mapa y agrega el siguiente punto." to false
        else -> "$n vértices. Arrastra un punto para ajustarlo o toca el primero (verde) para terminar." to false
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (edicion.loteOrigen?.editable == true) "Editando ${edicion.loteOrigen.nombre}" else "Nuevo lote",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        indicacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (esProblema) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatearHa(area),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (esProblema) MaterialTheme.colorScheme.error else AgroGreenPrimary
                    )
                    if (edicion.perimetroM > 0) {
                        Text(
                            "Perímetro ${formatearDistancia(edicion.perimetroM)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Herramienta(Icons.AutoMirrored.Filled.Undo, "Deshacer", edicion.puedeDeshacer, onClick = onDeshacer)
                Herramienta(Icons.AutoMirrored.Filled.Redo, "Rehacer", edicion.puedeRehacer, onClick = onRehacer)
                Herramienta(Icons.Default.Delete, "Borrar", seleccionado != null, onClick = onBorrarVertice)
                Herramienta(
                    icono = Icons.Default.CenterFocusStrong,
                    etiqueta = "Mira",
                    habilitado = true,
                    activo = edicion.modoMira,
                    onClick = onAlternarMira
                )
                Herramienta(Icons.Default.MyLocation, "GPS", true, onClick = onPuntoGps)
            }

            if (edicion.modoMira) {
                FilledTonalButton(onClick = onAgregarEnMira, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar punto en la mira")
                }
            }

            edicion.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f), enabled = !edicion.guardando) {
                    Text("Cancelar")
                }
                Button(
                    onClick = onGuardar,
                    enabled = edicion.puedeGuardar,
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGoldAccent, contentColor = Color.Black)
                ) {
                    if (edicion.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar lote", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Herramienta(
    icono: ImageVector,
    etiqueta: String,
    habilitado: Boolean,
    activo: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 56.dp)) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = habilitado,
            colors = if (activo) {
                IconButtonDefaults.filledTonalIconButtonColors(containerColor = AgroGreenPrimary, contentColor = Color.White)
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            },
            modifier = Modifier.semantics { if (activo) stateDescription = "Activado" }
        ) {
            Icon(icono, contentDescription = etiqueta)
        }
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
