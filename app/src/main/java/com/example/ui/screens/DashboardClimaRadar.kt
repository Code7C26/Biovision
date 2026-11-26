package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.viewmodel.CapaClima
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.ui.components.detachFromParent
import com.example.ui.components.release
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.remote.model.AgroClimateDashboard
import com.example.data.remote.model.NivelUv
import com.example.ui.rediseno.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextFieldDefaults
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroGoldLight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import com.example.ui.theme.AgroGreenDark
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.AgroSkyBlue
import com.example.ui.viewmodel.WeatherViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.ui.components.FilaSugerenciaLocalidad
import com.example.ui.components.WindyEmbed
import com.example.ui.components.urlNavegable
import com.example.ui.viewmodel.VistaMapaClima

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun DashboardClimaRadarScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    /** Tarjeta de la estación propia del productor, debajo del resumen del día. */
    estacionPropia: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sugerencias by viewModel.sugerencias.collectAsStateWithLifecycle()
    val vistaMapa by viewModel.vistaMapa.collectAsStateWithLifecycle()
    val favoriteCities by viewModel.favoriteCities.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Se inicializa con el estado actual del ViewModel para no mostrar la pantalla vacía un frame al volver/rotar
    var lastDashboard by remember {
        mutableStateOf((viewModel.uiState.value as? WeatherUiState.Success)?.dashboard)
    }

    // Geolocation API Permission
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    // Sesgo de cercanía del buscador: la ubicación del dispositivo o, sin ella, la localidad del mapa
    var ubicacionDispositivo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val focusManager = LocalFocusManager.current

    // El ViewModel ignora la ubicación si ya se aplicó una vez (evita recargar en cada cambio de pestaña)
    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            pedirUbicacion(context, alFallar = {}) { lat, lon ->
                ubicacionDispositivo = lat to lon
                viewModel.usarUbicacionInicial(lat, lon)
            }
        }
    }

    // Las notificaciones de alertas las dispara el ViewModel al recibir datos nuevos
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is WeatherUiState.Success) {
            lastDashboard = state.dashboard
        }
    }

    // Refresco periódico solo con la pantalla visible: se suspende en segundo plano
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.mantenerActualizado()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Rediseño: título grande, localidad y hora de actualización, actualizar y GPS
        EncabezadoR(
            titulo = "Clima",
            subtitulo = lastDashboard?.let { d ->
                d.ciudad + " · actualizado " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(d.lastUpdated))
            }
        ) {
            IconButton(
                onClick = {
                    val lat = lastDashboard?.lat ?: -32.9468
                    val lon = lastDashboard?.lon ?: -60.6393
                    viewModel.cargarClimaActual(lat, lon, lastDashboard?.ciudad, forceRefresh = true)
                },
                modifier = Modifier.size(48.dp).testTag("btn_refresh_clima")
            ) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
            IconButton(
                onClick = {
                    if (locationPermissionState.status.isGranted) {
                        pedirUbicacion(
                            context,
                            alFallar = {
                                android.widget.Toast.makeText(
                                    context, "No se pudo obtener tu ubicación. Revisá que la ubicación del teléfono esté activada.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        ) { lat, lon ->
                            ubicacionDispositivo = lat to lon
                            viewModel.usarUbicacionActual(lat, lon)
                        }
                    } else {
                        locationPermissionState.launchPermissionRequest()
                    }
                },
                modifier = Modifier.size(48.dp).testTag("btn_gps_location")
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación", tint = AgroGreenPrimary) }
        }

        // Search Bar container
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
            ) {
                // Buscador con sugerencias en vivo (Photon), priorizando las localidades cercanas
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.buscarCiudades(it, ubicacionDispositivo ?: (vistaMapa.lat to vistaMapa.lon))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_buscar_ciudad"),
                    placeholder = { Text("Buscar localidad (ej. Rosario, Pergamino, Chabás)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar")
                    },
                    trailingIcon = {
                        when {
                            sugerencias.cargando -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            searchQuery.isNotEmpty() -> IconButton(onClick = {
                                searchQuery = ""
                                viewModel.cerrarSugerencias()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Words),
                    // Enter elige la primera sugerencia, si ya llegó
                    keyboardActions = KeyboardActions(onSearch = {
                        sugerencias.items.firstOrNull()?.let { primera ->
                            searchQuery = ""
                            focusManager.clearFocus()
                            viewModel.seleccionarCiudad(primera)
                        }
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Lista desplegable de sugerencias
                AnimatedVisibility(visible = sugerencias.items.isNotEmpty() || sugerencias.error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("sugerencias_clima"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            sugerencias.items.forEach { localidad ->
                                FilaSugerenciaLocalidad(localidad) {
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                    viewModel.seleccionarCiudad(localidad)
                                }
                            }
                            if (sugerencias.items.isEmpty()) {
                                sugerencias.error?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Favorite Cities Selector
                if (favoriteCities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(favoriteCities, key = { it.name }) { city ->
                            val isSelected = lastDashboard?.ciudad?.equals(city.name, ignoreCase = true) == true
                            ChipElegibleR(
                                texto = city.name,
                                elegido = isSelected,
                                onClick = { viewModel.seleccionarFavorita(city) }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val currentDashboard = lastDashboard
            if (currentDashboard != null) {
                val isFavorite = favoriteCities.any { it.name.equals(currentDashboard.ciudad, ignoreCase = true) }
                AgroClimateContent(
                    dashboard = currentDashboard,
                    vistaMapa = vistaMapa,
                    errorMessage = (uiState as? WeatherUiState.Error)?.message,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        viewModel.toggleFavorite(
                            currentDashboard.ciudad,
                            currentDashboard.lat,
                            currentDashboard.lon,
                            isFavorite
                        )
                    },
                    viewModel = viewModel,
                    estacionPropia = estacionPropia
                )

                // Aviso sin velo: el mapa vuela a la localidad elegida mientras llega su pronóstico
                if (uiState is WeatherUiState.Loading) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .testTag("aviso_cargando_clima")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = AgroGreenPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = vistaMapa.nombre?.let { "Cargando clima de $it…" } ?: "Actualizando clima…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                if (uiState is WeatherUiState.Loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AgroGreenPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargando estaciones climáticas y radar...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (uiState is WeatherUiState.Error) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = (uiState as WeatherUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.reintentar() }) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}

/** Una ubicación guardada más vieja que esto no sirve para "mi ubicación": se pide una nueva. */
private const val UBICACION_VIGENTE_MS = 10 * 60 * 1000L

/**
 * Ubicación del dispositivo: la última conocida si es reciente; si no (pasa a menudo al prender la
 * ubicación o después de un tiempo sin usarla), una lectura nueva. Antes, sin última ubicación el
 * botón de GPS no hacía nada. Solo se llama con el permiso concedido.
 */
@android.annotation.SuppressLint("MissingPermission")
private fun pedirUbicacion(context: Context, alFallar: () -> Unit, alObtener: (Double, Double) -> Unit) {
    val cliente = LocationServices.getFusedLocationProviderClient(context)
    fun lecturaNueva() {
        cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc -> if (loc != null) alObtener(loc.latitude, loc.longitude) else alFallar() }
            .addOnFailureListener { alFallar() }
    }
    cliente.lastLocation
        .addOnSuccessListener { ultima ->
            if (ultima != null && System.currentTimeMillis() - ultima.time < UBICACION_VIGENTE_MS) alObtener(ultima.latitude, ultima.longitude)
            else lecturaNueva()
        }
        .addOnFailureListener { lecturaNueva() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AgroClimateContent(
    dashboard: AgroClimateDashboard,
    vistaMapa: VistaMapaClima,
    errorMessage: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    viewModel: WeatherViewModel,
    estacionPropia: (@Composable () -> Unit)? = null
) {
    var showDetailSheet by rememberSaveable { mutableStateOf(false) }
    var showExpandedMap by rememberSaveable { mutableStateOf(false) }
    val capa by viewModel.capaSeleccionada.collectAsStateWithLifecycle()

    // Creada fuera del LazyColumn: sobrevive a que su item salga de pantalla al hacer scroll
    val windyWebView = rememberWindyWebView()

    if (showExpandedMap) {
        Dialog(
            onDismissRequest = { showExpandedMap = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Row with GRANEX logo, title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_granex),
                                contentDescription = "GRANEX Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Radar de Precipitaciones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AgroGreenPrimary
                            )
                        }

                        IconButton(
                            onClick = { showExpandedMap = false },
                            modifier = Modifier.testTag("btn_close_expanded_map")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Expanded Windy WebView inside the body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        WindyFullMapView(
                            vista = vistaMapa,
                            overlay = capa.overlayWindy,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    val alertas by viewModel.alertas.collectAsStateWithLifecycle()

    // Rediseño: el clima de ahora, las decisiones del día y después el detalle
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Falló la actualización: se siguen mostrando los últimos datos válidos
        if (errorMessage != null) {
            item {
                TarjetaR(modifier = Modifier.testTag("weather_error_banner"), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$errorMessage Se muestran los últimos datos de ${dashboard.ciudad}.",
                            fontSize = 13.sp, color = colorSecundario, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.reintentar() }) { Text("Reintentar", color = AgroGreenPrimary) }
                    }
                }
            }
        }

        if (alertas.hayAlguna) {
            item {
                val c = colores(Tono.ALERTA)
                TarjetaR(color = c.fondo, modifier = Modifier.testTag("weather_alert_banner")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = c.texto, modifier = Modifier.size(22.dp))
                        Text("Alerta meteorológica", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.texto)
                    }
                    listOfNotNull(alertas.helada, alertas.lluvia, alertas.viento).forEach { detalle ->
                        Text("• $detalle", fontSize = 14.sp, color = c.texto)
                    }
                }
            }
        }

        // Ahora: la condición destacada (tocarla abre el pronóstico detallado), los seis datos del momento
        // con su lectura agronómica y la radiación UV, igual que el clima de la web
        item(key = "ahora") {
            TarjetaAhora(dashboard, isFavorite, onToggleFavorite) { showDetailSheet = true }
        }
        item(key = "metricas_ahora") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricasAhora(dashboard)
                TarjetaRadiacionUv(uvActual = dashboard.uvActual, uvMaxHoy = dashboard.uvMaxHoy)
                Text(
                    String.format(Locale("es", "AR"), "Evapotranspiración (ET0) %.1f mm/día", dashboard.evapotranspiracionET0),
                    fontSize = 12.sp, color = colorSecundario, modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Las decisiones del día (estimadas por el modelo, no medidas)
        if (dashboard.pulverizacion != null || dashboard.helada != null || dashboard.balanceHidrico != null) {
            item(key = "que_hago_hoy") { SeccionR("¿Qué puedo hacer hoy?") }
        }
        dashboard.pulverizacion?.let { p -> item(key = "ventana_pulverizacion") { TarjetaVentanaPulverizacion(p) } }
        dashboard.helada?.let { h -> item(key = "helada_pronostico") { TarjetaHeladaPronostico(h) } }
        dashboard.balanceHidrico?.let { b -> item(key = "balance_hidrico") { TarjetaBalanceHidrico(b) } }

        // Próximas 24 horas, cada 3 horas
        dashboard.forecast?.hourlyList?.takeIf { it.isNotEmpty() }?.let { horas ->
            item(key = "proximas_horas") {
                TarjetaR {
                    TituloTarjetaR("Próximas 24 horas")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        horas.take(24).filterIndexed { i, _ -> i % 3 == 0 }.forEachIndexed { i, h ->
                            // La lluvia de cada hora es la de la hora anterior: el bloque suma las 3 siguientes
                            val lluvia = horas.drop(i * 3 + 1).take(3).sumOf { it.precipitationMm }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(formatHourLabel(h.hour), fontSize = 12.sp, color = colorSecundario)
                                Text("${h.tempCelsius.roundToInt()}°", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (lluvia >= 0.1) String.format(Locale("es", "AR"), "%.1f", lluvia) else " ",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0369A1)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Próximos días: tocar uno abre el pronóstico detallado
        dashboard.forecast?.dailyList?.takeIf { it.isNotEmpty() }?.let { dias ->
            item(key = "proximos_dias") {
                val minimo = dias.minOf { it.tempMin }
                val rango = (dias.maxOf { it.tempMax } - minimo).coerceAtLeast(1.0)
                TarjetaR(espacio = 0.dp) {
                    TituloTarjetaR("Próximos ${dias.size} días")
                    Spacer(Modifier.height(6.dp))
                    dias.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = "Ver pronóstico de ${d.dayName}") { showDetailSheet = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(d.dayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp), maxLines = 1)
                            Text(
                                if (d.precipitacionMm >= 0.5) String.format(Locale("es", "AR"), "%.0f mm", d.precipitacionMm) else "—",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0369A1), modifier = Modifier.width(46.dp)
                            )
                            Text("${d.tempMin.roundToInt()}°", fontSize = 13.sp, color = colorSecundario, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                            val izq = ((d.tempMin - minimo) / rango).toFloat().coerceIn(0f, 1f)
                            val ancho = ((d.tempMax - d.tempMin) / rango).toFloat().coerceIn(0.02f, 1f - izq)
                            Row(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (izq > 0.001f) Spacer(Modifier.weight(izq))
                                Box(Modifier.weight(ancho.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(0xFFD97706)))
                                val resto = 1f - izq - ancho
                                if (resto > 0.001f) Spacer(Modifier.weight(resto))
                            }
                            Text("${d.tempMax.roundToInt()}°", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colorSecundario, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Estación meteorológica propia del productor (datos medidos en su campo)
        if (estacionPropia != null) {
            item(key = "estacion_propia") { estacionPropia() }
        }

        // Mapa Windy con la capa elegida
        item(key = "mapa_windy") {
            TarjetaR {
                TituloTarjetaR("Mapa de ${capa.etiqueta.lowercase()}")
                // La capa es compartida con el pronóstico detallado: cambiarla aquí cambia también su gráfico
                SegmentadoR(
                    opciones = CapaClima.entries.map { if (it == CapaClima.LLUVIA) "Lluvia" else it.etiqueta },
                    elegida = capa.ordinal,
                    alto = 38.dp
                ) { viewModel.seleccionarCapa(CapaClima.entries[it]) }
                Box(
                    modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp)).testTag("windy_map_container")
                ) {
                    WindyMapView(webView = windyWebView, vista = vistaMapa, overlay = capa.overlayWindy, modifier = Modifier.fillMaxSize())
                    // Botón explícito para ampliar: la WebView consume los toques del contenedor
                    Surface(
                        onClick = { showExpandedMap = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).testTag("btn_expand_windy_map")
                    ) {
                        Text("Ampliar mapa", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }

        item(key = "atribucion") {
            Text(
                "Pronóstico ECMWF · Datos: Open-Meteo.com · actualizado " +
                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(dashboard.lastUpdated)),
                fontSize = 12.sp, color = colorSecundario,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }

    if (showDetailSheet) {
        // Rediseño: la hoja con los colores claros del tema (antes era siempre oscura)
        val cFondoHoja = MaterialTheme.colorScheme.background
        val cTarjetaHoja = MaterialTheme.colorScheme.surface
        val cTextoHoja = MaterialTheme.colorScheme.onSurface
        val cSecundarioHoja = MaterialTheme.colorScheme.onSurfaceVariant
        val cLineaHoja = MaterialTheme.colorScheme.outlineVariant

        // Bottom sheet real: se cierra arrastrando o con "atrás", y TalkBack lo anuncia como hoja modal
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cFondoHoja,
            dragHandle = { BottomSheetDefaults.DragHandle(color = cLineaHoja) }
        ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    ) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = dashboard.ciudad,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cTextoHoja
                                )
                                Text(
                                    text = "Pronóstico Detallado • ${dashboard.condicionTexto}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { showDetailSheet = false },
                                modifier = Modifier
                                    .background(cLineaHoja, CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = cTextoHoja,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // El pronóstico llega con el mismo pedido que el dashboard: no hay carga aparte
                        val data = dashboard.forecast
                        if (data == null || data.dailyList.isEmpty()) {
                            Text(
                                text = "No hay pronóstico disponible para esta ubicación.",
                                color = cTextoHoja,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(24.dp)
                            )
                        } else {
                                Text(
                                    text = "PRONÓSTICO SEMANAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cSecundarioHoja,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(data.dailyList) { day ->
                                        val isToday = day.dayName == "Hoy"
                                        // Ícono según el código WMO del día: 0-1 despejado, 2-48 nubes/niebla, resto precipitación
                                        val dayIcon = when {
                                            day.isRainy -> Icons.Default.WaterDrop
                                            day.codigoClima in 2..48 -> Icons.Default.Cloud
                                            else -> Icons.Default.WbSunny
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isToday) Color(0xFF047857) else cTarjetaHoja
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.width(76.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = day.dayName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cTextoHoja
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Icon(
                                                    imageVector = dayIcon,
                                                    contentDescription = when (dayIcon) {
                                                        Icons.Default.WaterDrop -> "Lluvia"
                                                        Icons.Default.Cloud -> "Nublado"
                                                        else -> "Soleado"
                                                    },
                                                    tint = if (isToday) cTextoHoja else Color(0xFF047857),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%.0f°", day.tempMax),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cTextoHoja
                                                )
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%.0f°", day.tempMin),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isToday) cTextoHoja else cSecundarioHoja
                                                )
                                                if (day.precipitacionMm > 0.0) {
                                                    Text(
                                                        text = String.format(Locale.getDefault(), "%.0f mm", day.precipitacionMm),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isToday) cTextoHoja else Color(0xFF0284C7)
                                                    )
                                                }
                                                day.et0Mm?.let { et0 ->
                                                    Text(
                                                        text = String.format(Locale.getDefault(), "ET0 %.1f", et0),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = if (isToday) cTextoHoja else cSecundarioHoja
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cTarjetaHoja, RoundedCornerShape(12.dp))
                                        .padding(4.dp)
                                        .selectableGroup(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CapaClima.entries.forEach { metric ->
                                        val label = metric.etiqueta
                                        val isSelected = capa == metric
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Color(0xFF047857) else Color.Transparent)
                                                // Rol de pestaña: TalkBack anuncia cuál está seleccionada
                                                .selectable(
                                                    selected = isSelected,
                                                    role = Role.Tab,
                                                    onClick = { viewModel.seleccionarCapa(metric) }
                                                )
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) cTextoHoja else cSecundarioHoja,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cTarjetaHoja),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, cTarjetaHoja)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        when (capa) {
                                            CapaClima.TEMPERATURA -> {
                                                Text(
                                                    text = "CURVA DE TEMPERATURA POR HORAS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Arriba la temperatura; en gris, la sensación térmica.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                                
                                                val hourlyTempData = data.hourlyList
                                                
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    BoxWithConstraints(
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        val parentWidth = maxWidth
                                                        val divisor = (hourlyTempData.size - 1).coerceAtLeast(1).toFloat()
                                                        // Cada etiqueta, centrada sobre su punto de la curva (que va de 20 dp
                                                        // a ancho − 20 dp). Antes eran de 60 dp con un decimal y se pisaban
                                                        // ("18.216.216.3…") en un teléfono
                                                        hourlyTempData.forEachIndexed { index, hourData ->
                                                            val xOffset = (parentWidth - 40.dp) * (index.toFloat() / divisor)
                                                            Column(
                                                                modifier = Modifier
                                                                    .width(40.dp)
                                                                    .offset(x = xOffset),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text(
                                                                    text = String.format(Locale.getDefault(), "%.0f°", hourData.tempCelsius),
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = cTextoHoja,
                                                                    textAlign = TextAlign.Center,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                                hourData.feelsLikeCelsius?.let { feelsLike ->
                                                                    Text(
                                                                        text = String.format(Locale.getDefault(), "%.0f°", feelsLike),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = cSecundarioHoja,
                                                                        fontSize = 11.sp,
                                                                        textAlign = TextAlign.Center,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    
                                                    // Mínimo y máximo se calculan una vez, no por cada punto en cada dibujo
                                                    val tempMinVal = (hourlyTempData.minOfOrNull { it.tempCelsius } ?: 15.0) - 1.0
                                                    val tempMaxVal = (hourlyTempData.maxOfOrNull { it.tempCelsius } ?: 25.0) + 1.0
                                                    val tempRange = if (tempMaxVal - tempMinVal == 0.0) 1.0 else (tempMaxVal - tempMinVal)
                                                    val chartDescription = remember(hourlyTempData) {
                                                        "Curva de temperatura por hora: " + hourlyTempData.joinToString(", ") {
                                                            String.format(Locale.getDefault(), "%s %.0f°", formatHourLabel(it.hour), it.tempCelsius)
                                                        }
                                                    }

                                                    androidx.compose.foundation.Canvas(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(80.dp)
                                                            .padding(horizontal = 20.dp)
                                                            .semantics { contentDescription = chartDescription }
                                                    ) {
                                                        // Con un solo punto se centra (antes: división por cero y gráfico vacío)
                                                        val stepX = if (hourlyTempData.size > 1) size.width / (hourlyTempData.size - 1) else 0f
                                                        val points = hourlyTempData.mapIndexed { index, hd ->
                                                            val x = if (hourlyTempData.size > 1) stepX * index else size.width / 2
                                                            val y = size.height - ((hd.tempCelsius - tempMinVal) / tempRange * size.height).toFloat()
                                                            Offset(x, y)
                                                        }
                                                        
                                                        val path = Path().apply {
                                                            if (points.isNotEmpty()) {
                                                                moveTo(points.first().x, points.first().y)
                                                                for (i in 1 until points.size) {
                                                                    val prev = points[i - 1]
                                                                    val curr = points[i]
                                                                    val cp1X = prev.x + (curr.x - prev.x) / 2
                                                                    val cp1Y = prev.y
                                                                    val cp2X = prev.x + (curr.x - prev.x) / 2
                                                                    val cp2Y = curr.y
                                                                    cubicTo(cp1X, cp1Y, cp2X, cp2Y, curr.x, curr.y)
                                                                }
                                                            }
                                                        }
                                                        
                                                        val fillPath = Path().apply {
                                                            addPath(path)
                                                            if (points.isNotEmpty()) {
                                                                lineTo(points.last().x, size.height)
                                                                lineTo(points.first().x, size.height)
                                                                close()
                                                            }
                                                        }
                                                        
                                                        drawPath(
                                                            path = fillPath,
                                                            brush = Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color(0x22047857),
                                                                    Color.Transparent
                                                                )
                                                            )
                                                        )
                                                        
                                                        drawPath(
                                                            path = path,
                                                            color = Color(0xFF047857),
                                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                                        )
                                                        
                                                        points.forEach { point ->
                                                            drawCircle(
                                                                color = cTextoHoja,
                                                                radius = 4.dp.toPx(),
                                                                center = point
                                                            )
                                                            drawCircle(
                                                                color = Color(0xFF047857),
                                                                radius = 2.dp.toPx(),
                                                                center = point
                                                            )
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    
                                                    BoxWithConstraints(
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        val parentWidth = maxWidth
                                                        val divisor = (hourlyTempData.size - 1).coerceAtLeast(1).toFloat()
                                                        hourlyTempData.forEachIndexed { index, hd ->
                                                            val xOffset = (parentWidth - 40.dp) * (index.toFloat() / divisor) - 10.dp
                                                            Text(
                                                                text = formatHourLabel(hd.hour),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = cSecundarioHoja,
                                                                modifier = Modifier
                                                                    .width(60.dp)
                                                                    .offset(x = xOffset),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            CapaClima.LLUVIA -> {
                                                Text(
                                                    text = "PRECIPITACIONES ESPERADAS (mm cada 3 h)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                // Un pronóstico de modelo (ECMWF, 9 km) no ubica una tormenta con
                                                // precisión de pocos kilómetros: se aclara para no confundirlo con lo medido
                                                Text(
                                                    text = "Pronóstico del modelo ECMWF (9 km): una tormenta puede caer a pocos kilómetros de lo previsto.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                                
                                                val maxPrecip = data.hourlyList.maxOfOrNull { it.precipitationMm } ?: 1.0
                                                val resolvedMax = if (maxPrecip == 0.0) 1.0 else maxPrecip
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(horizontal = 10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.Bottom
                                                ) {
                                                    data.hourlyList.forEach { hourData ->
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier.width(55.dp)
                                                        ) {
                                                            // Número y unidad en dos renglones, como en Viento: "1,6 mm" en uno
                                                            // se cortaba en el borde de la última columna visible
                                                            Text(
                                                                text = if (hourData.precipitationMm > 0.0) {
                                                                    String.format(Locale.getDefault(), "%.1f", hourData.precipitationMm)
                                                                } else {
                                                                    "0"
                                                                },
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (hourData.precipitationMm > 0.0) Color(0xFF0284C7) else cSecundarioHoja,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                            Text(
                                                                text = "mm",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = cSecundarioHoja,
                                                                fontSize = 11.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(16.dp)
                                                                    .height(60.dp)
                                                                    .background(cTarjetaHoja, RoundedCornerShape(4.dp))
                                                                    .align(Alignment.CenterHorizontally),
                                                                contentAlignment = Alignment.BottomCenter
                                                            ) {
                                                                val barHeightRatio = (hourData.precipitationMm / resolvedMax).toFloat()
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .fillMaxHeight(barHeightRatio.coerceIn(0.05f, 1.0f))
                                                                        .background(
                                                                            Brush.verticalGradient(
                                                                                colors = listOf(Color(0xFF0284C7), Color(0xFF0284C7))
                                                                            ),
                                                                            RoundedCornerShape(4.dp)
                                                                        )
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = hourData.hour,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = cSecundarioHoja,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            CapaClima.VIENTO -> {
                                                Text(
                                                    text = "VELOCIDAD Y DIRECCIÓN DEL VIENTO",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "La flecha indica hacia dónde sopla; la letra, de dónde viene (NE = viento del noreste).",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cSecundarioHoja,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    data.hourlyList.forEach { hourData ->
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier.width(55.dp)
                                                        ) {
                                                            Text(
                                                                text = hourData.hour,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = cSecundarioHoja
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            // Open-Meteo da de dónde viene el viento (0° = del norte); la flecha
                                                            // apunta hacia donde sopla, como en Windy. Antes quedaba al revés.
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowUpward,
                                                                contentDescription = "Viento del ${puntoCardinal(hourData.windDirectionDegrees)}",
                                                                tint = Color(0xFF047857),
                                                                modifier = Modifier
                                                                    .size(22.dp)
                                                                    .rotate(hourData.windDirectionDegrees + 180f)
                                                            )
                                                            Text(
                                                                text = puntoCardinal(hourData.windDirectionDegrees),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF047857)
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = String.format(Locale.getDefault(), "%.1f", hourData.windSpeedKmH),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = cTextoHoja
                                                            )
                                                            Text(
                                                                text = "km/h",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = cSecundarioHoja,
                                                                fontSize = 11.sp
                                                            )
                                                            hourData.gustSpeedKmH?.let { gustsVal ->
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = String.format(Locale.getDefault(), "Ráfagas:\n%.0f", gustsVal),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = cSecundarioHoja,
                                                                    fontSize = 11.sp,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
        }
    }
}

/**
 * El clima de ahora destacado, como en la web: ciudad, ícono del tiempo (sol o luna según is_day),
 * temperatura con un decimal, condición, sensación y la mínima / máxima de hoy, sobre el degradé de la
 * marca. La estrella guarda la localidad; tocar la tarjeta abre el pronóstico detallado.
 */
@Composable
internal fun TarjetaAhora(d: AgroClimateDashboard, favorita: Boolean, onFavorita: () -> Unit, onAbrir: () -> Unit) {
    val ar = Locale("es", "AR")
    val (icono, colorIcono) = iconoClimaAhora(d.codigoClima, d.esDeDia)
    Surface(
        onClick = onAbrir,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().testTag("tarjeta_ahora")
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AgroGreenDark, AgroGreenPrimary))).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.ciudad, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFavorita, modifier = Modifier.size(40.dp).testTag("btn_favorito_clima")) {
                        Icon(
                            if (favorita) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (favorita) "Quitar de guardadas" else "Guardar localidad",
                            tint = if (favorita) Color(0xFFFFD54F) else Color.White
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(38.dp))
                    }
                    Column {
                        Text(
                            String.format(ar, "%.1f\u00A0°C", d.tempCelsius),
                            fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 50.sp, maxLines = 1
                        )
                        Text(d.condicionTexto, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                Text(
                    String.format(ar, "Sensación %.1f\u00A0°C", d.sensacionTermica) +
                        "  ·  Hoy ${d.tempMin.roundToInt()}° / ${d.tempMax.roundToInt()}°",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/** Ícono y color del clima de ahora según el código WMO y si es de día. */
private fun iconoClimaAhora(codigo: Int, deDia: Boolean): Pair<ImageVector, Color> = when {
    codigo >= 95 -> Icons.Default.Thunderstorm to Color.White
    codigo >= 0 && com.example.data.remote.model.esCodigoLluvia(codigo) -> Icons.Default.WaterDrop to Color.White
    codigo in 71..86 -> Icons.Default.AcUnit to Color.White
    codigo in 2..48 -> Icons.Default.Cloud to Color.White
    deDia -> Icons.Default.WbSunny to Color(0xFFFFD54F)
    else -> Icons.Default.NightsStay to Color(0xFFFDE68A)
}

/**
 * Los seis datos del momento en dos columnas, cada uno con su lectura agronómica (los mismos criterios
 * que el clima de la web): humedad para aplicar, viento y ráfagas, sensación y nubosidad, lluvia de hoy,
 * presión y riesgo de incendio. Cada fila tiene el alto de su tarjeta más alta.
 */
@Composable
internal fun MetricasAhora(d: AgroClimateDashboard) {
    val ar = Locale("es", "AR")
    val presion = when {
        d.presionHpa < 1005 -> "Baja: inestabilidad"
        d.presionHpa > 1020 -> "Alta: tiempo estable"
        else -> "Normal"
    }
    val riesgoBajo = d.riesgoFuego == "Bajo"
    val tarjetas: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            TarjetaDatoAhora(
                Icons.Default.WaterDrop, "Humedad relativa", "${d.humedadPorcentaje} %",
                if (d.humedadPorcentaje in 50..80) "Óptima aplicación" else "Extrema para aplicar", m
            )
        },
        { m ->
            TarjetaDatoAhora(
                Icons.Default.Air, "Viento", String.format(ar, "%.1f km/h", d.vientoKmH),
                "${puntoCardinal(d.direccionVientoGrados)} (${d.direccionVientoGrados}°) · Ráf. ${d.rafagasKmH.roundToInt()}", m
            )
        },
        { m ->
            TarjetaDatoAhora(
                Icons.Default.Thermostat, "Sensación térmica", String.format(ar, "%.1f\u00A0°C", d.sensacionTermica),
                "Nubosidad ${d.nubosidadPorcentaje} %", m
            )
        },
        { m ->
            TarjetaDatoAhora(
                Icons.Default.Umbrella, "Lluvia hoy", String.format(ar, "%.1f mm", d.lluviaHoyMm),
                d.probLluviaHoy?.let { "Probabilidad $it %" } ?: "Sin probabilidad", m, color = AgroSkyBlue
            )
        },
        { m ->
            TarjetaDatoAhora(Icons.Default.Compress, "Presión atmosférica", String.format(ar, "%,d hPa", d.presionHpa), presion, m)
        },
        { m ->
            TarjetaDatoAhora(
                Icons.Default.Warning, "Riesgo de incendio", d.riesgoFuego,
                if (riesgoBajo) "Baja alerta" else "Monitorear rastrojo", m,
                color = if (riesgoBajo) AgroGreenPrimary else Color(0xFFDC2626)
            )
        }
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("metricas_ahora")) {
        tarjetas.chunked(2).forEach { fila ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Tarjeta de un dato del clima: ícono en círculo, título, valor grande y su lectura (TarjetaMetrica en la web). */
@Composable
private fun TarjetaDatoAhora(
    icono: ImageVector,
    titulo: String,
    valor: String,
    detalle: String,
    modifier: Modifier,
    color: Color = AgroGreenPrimary
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
                }
                Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 19.sp)
            }
            Text(valor, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, fontSize = 13.sp, color = colorSecundario, maxLines = 2, lineHeight = 17.sp)
        }
    }
}

@Composable
fun AgroMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AgroGreenPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Título de alto contraste: antes labelSmall en onSurfaceVariant costaba leerlo
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(text = status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Colores de la escala UV de la OMS (tonos oscurecidos para que el texto blanco se lea encima)
private fun colorUv(nivel: NivelUv): Color = when (nivel) {
    NivelUv.BAJO -> Color(0xFF2E7D32)
    NivelUv.MODERADO -> Color(0xFFB58900)
    NivelUv.ALTO -> Color(0xFFE65100)
    NivelUv.MUY_ALTO -> Color(0xFFC62828)
    NivelUv.EXTREMO -> Color(0xFF6A1B9A)
}

private const val UV_ESCALA_MAX = 14.0

/** Índice UV a todo el ancho de la grilla: valor, categoría, escala de la OMS y recomendaciones. */
@Composable
internal fun TarjetaRadiacionUv(uvActual: Double?, uvMaxHoy: Double?) {
    // De noche el UV actual es 0: se muestra el máximo de hoy para planificar el día
    val referencia = uvActual?.takeIf { it > 0.0 } ?: uvMaxHoy
    val nivel = referencia?.let { NivelUv.de(it) }
    val color = nivel?.let(::colorUv) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().testTag("tarjeta_uv"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Radiación UV",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (referencia == null || nivel == null) {
                    Text("Sin dato de UV para esta ubicación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f UV", referencia),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Riesgo ${nivel.etiqueta.lowercase()}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = when {
                        uvActual != null && uvActual > 0.0 -> uvMaxHoy?.let { String.format(Locale.getDefault(), "Ahora · máximo de hoy %.1f", it) } ?: "Ahora"
                        else -> "Sin sol ahora · máximo de hoy"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EscalaUv(referencia)
                Text(
                    text = nivel.proteccion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = nivel.pulverizacion,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Barra con los 5 tramos de la OMS proporcionales a su rango y un marcador en el valor. */
@Composable
private fun EscalaUv(uv: Double) {
    val tramos = NivelUv.entries.mapIndexed { i, n ->
        val hasta = NivelUv.entries.getOrNull(i + 1)?.desde ?: UV_ESCALA_MAX
        n to (hasta - n.desde).toFloat()
    }
    val fraccion = (uv / UV_ESCALA_MAX).coerceIn(0.0, 1.0).toFloat()
    val colorMarcador = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Escala UV de 0 a 11 o más; valor actual " + String.format(Locale.getDefault(), "%.1f", uv) }) {
        Box(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                tramos.forEach { (n, peso) -> Box(Modifier.weight(peso).fillMaxHeight().background(colorUv(n))) }
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = size.width * fraccion
                drawLine(colorMarcador, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            tramos.forEach { (n, peso) ->
                Text(
                    text = n.etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(peso)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWindyWebView(context: Context): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webViewClient = ClienteWindy()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // Windy guarda sus preferencias de capa en localStorage
            useWideViewPort = true
            loadWithOverviewMode = true
            // Sitio externo: sin acceso a archivos ni content:// del teléfono y sin contenido mixto
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
        }
    }

/**
 * La WebView de Windy solo navega dentro de windy.com (https). Cualquier otro enlace (publicidad,
 * redes, la web de un modelo) se abre en el navegador del teléfono, nunca dentro de la app.
 */
private class ClienteWindy : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
        val url = request.url
        if (esUrlWindy(url.scheme, url.host)) return false
        if (request.isForMainFrame) {
            urlNavegable(url.toString())?.let { segura ->
                try {
                    view.context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(segura))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    // Sin navegador: el enlace simplemente no se abre
                }
            }
        }
        return true
    }
}

/** true para https://windy.com y sus subdominios (embed, tiles, ims…). */
internal fun esUrlWindy(esquema: String?, host: String?): Boolean =
    esquema == "https" && host != null && (host == "windy.com" || host.endsWith(".windy.com"))

/** WebView de Windy ligada al composable que la llama; se destruye cuando éste sale de la composición. */
@Composable
fun rememberWindyWebView(): WebView {
    val context = LocalContext.current
    val webView = remember { createWindyWebView(context) }
    DisposableEffect(webView) {
        onDispose { webView.release() }
    }
    return webView
}

// Carga la URL solo si cambió (el tag guarda la última pedida; webView.url puede diferir por redirecciones)
private fun WebView.loadUrlIfChanged(url: String) {
    if (tag != url) {
        tag = url
        loadUrl(url)
    }
}

/** Lo último que se pidió a la WebView del embed: con qué capa se cargó y a qué vista se llevó. */
private data class EstadoWindy(val overlay: String, val vistaId: Long)

/**
 * Embed de Windy. Cambiar de capa recarga la página (Windy no expone cómo cambiarla en caliente);
 * cambiar de ubicación no: la cámara vuela hasta la vista nueva con [WindyEmbed.scriptVolar].
 */
@Composable
fun WindyMapView(
    webView: WebView,
    vista: VistaMapaClima,
    overlay: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        AndroidView(
            factory = { webView.detachFromParent() },
            update = { it.mostrarVista(vista, overlay) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun WebView.mostrarVista(vista: VistaMapaClima, overlay: String) {
    val estado = tag as? EstadoWindy
    when {
        estado == null || estado.overlay != overlay -> {
            tag = EstadoWindy(overlay, vista.id)
            loadUrl(WindyEmbed.urlEmbed(vista, overlay))
        }
        estado.vistaId != vista.id -> {
            tag = EstadoWindy(overlay, vista.id)
            evaluateJavascript(WindyEmbed.scriptVolar(vista)) { resultado ->
                // La página todavía carga o Windy cambió su API interna: se recarga con la vista nueva
                if (resultado != "\"ok\"" && (tag as? EstadoWindy)?.vistaId == vista.id) {
                    loadUrl(WindyEmbed.urlEmbed(vista, overlay))
                }
            }
        }
    }
}

@Composable
fun WindyFullMapView(
    vista: VistaMapaClima,
    overlay: String,
    modifier: Modifier = Modifier
) {
    val fullUrl = remember(vista, overlay) { WindyEmbed.urlCompleta(vista, overlay) }

    // Vive solo mientras el diálogo está abierto: se destruye al cerrarlo
    AndroidView(
        factory = { context -> createWindyWebView(context) },
        update = { it.loadUrlIfChanged(fullUrl) },
        onRelease = { it.release() },
        modifier = modifier
    )
}

@Composable
fun FavoriteCityChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Surface seleccionable: TalkBack anuncia qué ciudad favorita está activa
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AgroGreenPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) AgroGreenPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        modifier = modifier.testTag("chip_fav_$name")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isSelected) AgroGoldLight else AgroGreenPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun puntoCardinal(grados: Int): String {
    val puntos = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return puntos[(((grados % 360) + 360) % 360 + 22) / 45 % 8]
}

private fun formatHourLabel(rawHour: String): String {
    val lower = rawHour.lowercase(Locale.getDefault()).trim()
    val numberStr = lower.filter { it.isDigit() }
    val num = numberStr.toIntOrNull() ?: return rawHour
    return when {
        lower.contains("p.m.") || lower.contains("pm") -> {
            if (num == 12) "12h" else "${num + 12}h"
        }
        lower.contains("a.m.") || lower.contains("am") -> {
            if (num == 12) "00h" else "${num}h"
        }
        else -> "${num}h"
    }
}

