package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.sync.SincronizacionLotesWorker
import com.example.data.remote.api.ApiKeys
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.mapa.rememberGisMapHolder
import androidx.lifecycle.lifecycleScope
import com.example.utils.AlertasWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DashboardClimaRadarScreen
import com.example.ui.screens.DashboardFinancieroRofexScreen
import com.example.ui.screens.SateliteScreen
import com.example.ui.screens.MercadoScreen
import com.example.ui.screens.NovedadesScreen
import com.example.ui.screens.SeccionCuenta
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.GRANEXTheme
import com.example.ui.theme.AppThemeState
import com.example.utils.WeatherNotificationService
import com.example.ui.viewmodel.CuentaViewModel
import com.example.ui.viewmodel.EscaneoViewModel
import com.example.ui.viewmodel.FinancialViewModel
import com.example.ui.viewmodel.NovedadesViewModel
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.ui.viewmodel.SateliteViewModel
import androidx.compose.material.icons.filled.Satellite
import com.example.ui.viewmodel.MercadoViewModel
import com.example.ui.viewmodel.WeatherViewModel

class MainActivity : ComponentActivity() {
    companion object {
        init {
            try {
                android.system.Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                android.system.Os.setenv("GALLIUM_DRIVER", "swrast", true)
            } catch (e: Throwable) {
                // Safe ignore if environment cannot be changed
            }
        }
    }

    // La app entera (con sus desplegables, diálogos y menús, que son ventanas aparte) se dibuja con la
    // densidad del Pixel de referencia escalada a esta pantalla y la letra en 1×: ver EscalaPantalla
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.ui.components.EscalaPantalla.contexto(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemeState.isDark = WeatherNotificationService.isDarkModeEnabled(this)
        AppThemeState.tema = WeatherNotificationService.getTema(this)
        AlertasWorker.programar(this)
        // Con backend: al abrir la app se suben los cambios hechos sin señal y se bajan los de otros
        // dispositivos (WorkManager espera a que haya conexión)
        if (ApiKeys.supabase != null) SincronizacionLotesWorker.programar(this)
        // Cifra (fuera del hilo principal) cualquier credencial que una versión anterior guardó en claro
        lifecycleScope.launch(Dispatchers.IO) { WeatherNotificationService.cifrarSecretosPendientes(applicationContext) }
        enableEdgeToEdge()
        setContent {
            GRANEXTheme {
                // Con backend hace falta una cuenta: sin sesión se muestra la pantalla de acceso
                com.example.ui.screens.AccesoGranex {
                    GRANEXMainApp()
                }
            }
        }
    }
}

/** Pantalla para conectar y administrar las estaciones meteorológicas propias (sin barra inferior). */
const val RUTA_ESTACIONES = "estaciones"

/** Anotar o editar un gasto de la Agenda: "agenda_gasto/nuevo" o "agenda_gasto/<id>" (sin barra inferior). */
const val RUTA_GASTO = "agenda_gasto"

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // Pestañas Inicio · Clima · Campo · Mercado · Agenda. Finanzas (dólar y futuros) se
    // abre desde Mercado y Novedades desde el Inicio: siguen siendo rutas, pero sin pestaña propia.
    object Inicio : Screen("inicio", "Inicio", Icons.Default.Home)
    object Finanzas : Screen("finanzas", "Finanzas", Icons.Default.AttachMoney)
    object Clima : Screen("clima", "Clima", Icons.Default.Cloud)
    object MercadoRosario : Screen("mercado_rosario", "Mercado", Icons.Default.ShowChart)
    object Satelital : Screen("satelital", "Campo", Icons.Default.Map)
    object Novedades : Screen("novedades", "Noticias", Icons.Default.Newspaper)
    object Agenda : Screen("agenda", "Agenda", Icons.Default.EditNote)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GRANEXMainApp() {
    val app = LocalContext.current.applicationContext as GranexApplication
    val container = app.container

    // ViewModels con alcance de Activity: sobreviven a rotaciones y se limpian (onCleared) al cerrar
    val financialViewModel = viewModel { FinancialViewModel(container.financialRepository) }
    val weatherViewModel = viewModel { WeatherViewModel(container.weatherRepository, container.agroDao, app) }
    val rosarioMarketViewModel = viewModel { MercadoViewModel(container.rosarioMarketRepository, container.financialRepository) }
    val novedadesViewModel = viewModel { NovedadesViewModel(container.newsRepository, app) }

    // Android 13+: sin este permiso las alertas (incluidas las de segundo plano) no se muestran
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permisoNotificaciones = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        var permisoSolicitado by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!permisoNotificaciones.status.isGranted && !permisoSolicitado) {
                permisoSolicitado = true
                permisoNotificaciones.launchPermissionRequest()
            }
        }
    }
    val sateliteViewModel = viewModel {
        SateliteViewModel(
            container.agroSatRepository,
            maptilerDisponible = ApiKeys.maptiler != null,
            detector = com.example.data.repository.DetectorLotes(
                container.fuenteSentinel,
                volcado = if (com.example.BuildConfig.DEBUG) java.io.File(app.cacheDir, "deteccion.bin") else null
            ),
            fechaEsri = com.example.data.repository.FechaImagenBase::esri
        )
    }
    val prescripcionViewModel = viewModel { PrescripcionViewModel(container.fuenteSentinel, almacen = container.almacenAgronomia) }
    val escaneoViewModel = viewModel { EscaneoViewModel(container.fuenteSentinel) }
    val estacionesViewModel = viewModel {
        // Solo el id de la estación elegida (no es un dato sensible)
        val prefs = app.getSharedPreferences("granex_estaciones", android.content.Context.MODE_PRIVATE)
        com.example.ui.viewmodel.EstacionesViewModel(
            container.estaciones,
            leerSeleccion = { prefs.getString("seleccionada", null) },
            guardarSeleccion = { prefs.edit().putString("seleccionada", it).apply() }
        )
    }
    val cuentaViewModel = com.example.ui.viewmodel.cuentaViewModelDeLaApp()
    val perfilViewModel = com.example.ui.viewmodel.perfilViewModelDeLaApp()
    val inicioViewModel = viewModel { com.example.ui.viewmodel.InicioViewModel() }
    val agendaViewModel = viewModel {
        com.example.ui.viewmodel.AgendaViewModel(
            container.almacenAgenda,
            // Los lotes por uuid: es el id que comparten el teléfono, la web y la copia en la nube
            lotes = container.agroDao.observarLotes().map { lista ->
                lista.map { com.example.data.agenda.LoteAgenda(it.uuid, it.nombre, it.superficieHa) }
            },
            dolarOficial = {
                container.financialRepository.obtenerDolares().getOrNull()?.first?.firstOrNull { it.casa == "oficial" }?.venta
            }
        )
    }
    val alcance = rememberCoroutineScope()
    // Una sola WebView del mapa satelital por Activity: sobrevive a los cambios de pestaña
    val gisMapHolder = rememberGisMapHolder()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Inicio.route

    val navigationItems = listOf(
        Screen.Inicio,
        Screen.Clima,
        Screen.Satelital,
        Screen.MercadoRosario,
        Screen.Agenda
    )
    /** Cambia de pestaña conservando el estado de cada una (como tocar la barra inferior). */
    fun irA(ruta: String) {
        if (currentRoute == ruta) return
        navController.navigate(ruta) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Granos y Dólar y futuros son dos vistas de la pestaña Mercado
    val selectorMercado: @Composable () -> Unit = {
        com.example.ui.components.SelectorSeccionMercado(
            enGranos = currentRoute == Screen.MercadoRosario.route,
            onGranos = { irA(Screen.MercadoRosario.route) },
            onDolar = { irA(Screen.Finanzas.route) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Cada pantalla maneja la barra de estado en su encabezado (GranexHeader / TopAppBar). Si el
        // Scaffold también la reservara, el margen se sumaría dos veces y quedaría una franja vacía.
        // El margen inferior lo aporta la barra de navegación (o el Scaffold de Ajustes).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute != "settings" && currentRoute != RUTA_ESTACIONES && !currentRoute.startsWith(RUTA_GASTO)) {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    navigationItems.forEach { screen ->
                        val selected = currentRoute == screen.route ||
                            (screen == Screen.MercadoRosario && currentRoute == Screen.Finanzas.route) ||
                            (screen == Screen.Inicio && currentRoute == Screen.Novedades.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { irA(screen.route) },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (selected) AgroGreenPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) AgroGreenPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("nav_item_${screen.route}"),
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AgroGreenPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Inicio.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Inicio.route) {
                com.example.ui.screens.InicioScreen(
                    inicio = inicioViewModel,
                    clima = weatherViewModel,
                    finanzas = financialViewModel,
                    satelite = sateliteViewModel,
                    agenda = agendaViewModel,
                    novedades = novedadesViewModel,
                    perfil = perfilViewModel,
                    onAbrirClima = { irA(Screen.Clima.route) },
                    onAbrirMercado = { irA(Screen.MercadoRosario.route) },
                    onAbrirCampo = { irA(Screen.Satelital.route) },
                    onAbrirLote = { lote ->
                        sateliteViewModel.seleccionarLote(lote)
                        irA(Screen.Satelital.route)
                    },
                    onAbrirAgenda = { irA(Screen.Agenda.route) },
                    onAnotarGasto = { navController.navigate("$RUTA_GASTO/nuevo") },
                    onAbrirNoticias = { navController.navigate(Screen.Novedades.route) },
                    onAbrirPerfil = { navController.navigate("settings") }
                )
            }
            composable(Screen.Agenda.route) {
                com.example.ui.screens.AgendaScreen(
                    viewModel = agendaViewModel,
                    onAnotar = { id -> navController.navigate("$RUTA_GASTO/${id ?: "nuevo"}") }
                )
            }
            composable("$RUTA_GASTO/{id}") { entrada ->
                com.example.ui.screens.AnotarGastoScreen(
                    viewModel = agendaViewModel,
                    gastoId = entrada.arguments?.getString("id")?.takeIf { it != "nuevo" },
                    onListo = { navController.popBackStack() }
                )
            }
            composable(Screen.Finanzas.route) {
                DashboardFinancieroRofexScreen(
                    viewModel = financialViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    selectorSeccion = selectorMercado
                )
            }
            composable(Screen.Clima.route) {
                DashboardClimaRadarScreen(
                    viewModel = weatherViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    estacionPropia = if (container.estaciones == null) null else {
                        {
                            com.example.ui.screens.TarjetaEstacionPropia(
                                estacionesViewModel,
                                onAdministrar = { navController.navigate(RUTA_ESTACIONES) }
                            )
                        }
                    }
                )
            }
            composable(RUTA_ESTACIONES) {
                com.example.ui.screens.PantallaEstaciones(
                    viewModel = estacionesViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.MercadoRosario.route) {
                val estadoAgenda by agendaViewModel.estado.collectAsStateWithLifecycle()
                val preciosHoy by inicioViewModel.precios.collectAsStateWithLifecycle()
                val estadoFinanzas by financialViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { financialViewModel.cargarDatosFinancieros() }
                MercadoScreen(
                    viewModel = rosarioMarketViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    selectorSeccion = selectorMercado,
                    preciosHoy = {
                        com.example.ui.screens.PreciosDeHoyMercado(
                            precios = preciosHoy,
                            rofex = (estadoFinanzas as? com.example.ui.viewmodel.FinancialUiState.Success)?.data?.rofex,
                            onReintentar = inicioViewModel::cargar
                        )
                    },
                    onActualizar = {
                        inicioViewModel.cargar()
                        financialViewModel.cargarDatosFinancieros(forceRefresh = true)
                    },
                    costosAgenda = estadoAgenda.costos.let { costos ->
                        fun r(v: Double) = kotlin.math.round(v * 10) / 10
                        costos.lotes.filter { it.totalUsd > 0 && it.lote.hectareas > 0 }.map { c ->
                            val ha = c.lote.hectareas
                            fun porHa(vararg cat: com.example.data.agenda.CategoriaGasto) = r(cat.sumOf { c.porCategoria[it] ?: 0.0 } / ha)
                            com.example.ui.components.CostosAgendaLote(
                                nombre = c.lote.nombre,
                                hectareas = r(ha),
                                semillaHa = porHa(com.example.data.agenda.CategoriaGasto.SEMILLA),
                                fertilizantesHa = porHa(com.example.data.agenda.CategoriaGasto.FERTILIZANTE),
                                fitosanitariosHa = porHa(com.example.data.agenda.CategoriaGasto.FITOSANITARIO),
                                laboresHa = porHa(
                                    com.example.data.agenda.CategoriaGasto.LABOR,
                                    com.example.data.agenda.CategoriaGasto.COMBUSTIBLE,
                                    com.example.data.agenda.CategoriaGasto.OTRO
                                ),
                                generalesHa = r(costos.generalesPorHa)
                            )
                        }
                    }
                )
            }
            composable(Screen.Satelital.route) {
                SateliteScreen(
                    viewModel = sateliteViewModel,
                    mapaHolder = gisMapHolder,
                    prescripcionViewModel = prescripcionViewModel,
                    escaneoViewModel = escaneoViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onAbrirGastosLote = { lote ->
                        // La Agenda guarda el lote por uuid: se busca el del lote elegido
                        alcance.launch {
                            val uuid = lote.id.removePrefix(com.example.data.repository.AgroSatRepository.PREFIJO_DB).toIntOrNull()
                                ?.let { container.agroDao.lotePorId(it)?.uuid }
                            agendaViewModel.filtrar(com.example.ui.viewmodel.FiltroAgenda(loteId = uuid))
                            irA(Screen.Agenda.route)
                        }
                    }
                )
            }
            composable(Screen.Novedades.route) {
                NovedadesScreen(
                    viewModel = novedadesViewModel,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onVolver = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    seccionCuenta = { SeccionCuenta(cuentaViewModel) }
                )
            }
        }
    }
}
