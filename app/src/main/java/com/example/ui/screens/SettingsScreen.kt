package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.CampoIdentificador
import com.example.ui.components.EnlaceExterno
import com.example.ui.components.EnlacesRegistro
import com.example.ui.components.CampoSecreto
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.AgroSkyBlue
import com.example.ui.theme.AppThemeState
import com.example.ui.theme.ModoTema
import com.example.ui.theme.TemaGranex
import com.example.utils.WeatherNotificationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Cuenta GRANEX y sincronización ([SeccionCuenta]); null en las vistas previas y capturas. */
    seccionCuenta: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Observe app dark theme state
    var isDarkTheme by remember { mutableStateOf(AppThemeState.isDark) }
    var temaActual by remember { mutableStateOf(AppThemeState.tema) }

    // Load initial preference values
    var globalAlertsEnabled by remember { mutableStateOf(WeatherNotificationService.isNotificationsEnabled(context)) }
    var climateRainEnabled by remember { mutableStateOf(WeatherNotificationService.isRainAlertEnabled(context)) }
    var climateFrostEnabled by remember { mutableStateOf(WeatherNotificationService.isFrostAlertEnabled(context)) }

    var marketAlertsEnabled by remember { mutableStateOf(WeatherNotificationService.isMarketAlertEnabled(context)) }
    var marketDolarEnabled by remember { mutableStateOf(WeatherNotificationService.isMarketDolarEnabled(context)) }
    var marketGrainsEnabled by remember { mutableStateOf(WeatherNotificationService.isMarketGrainsEnabled(context)) }

    var newsAlertsEnabled by remember { mutableStateOf(WeatherNotificationService.isNewsAlertEnabled(context)) }
    var dataSaverEnabled by remember { mutableStateOf(WeatherNotificationService.isDataSaverEnabled(context)) }

    Scaffold(
        topBar = {
            // Rediseño: el mismo encabezado que el resto de las pantallas
            com.example.ui.rediseno.EncabezadoR(
                titulo = "Perfil y ajustes",
                onVolver = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                },
                modifier = Modifier.testTag("btn_settings_back_area")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Rediseño: quién sos (datos del productor) arriba de todo, con "Editar"
            var editandoPerfil by remember { mutableStateOf(false) }
            com.example.ui.screens.TarjetaPerfilR(onEditar = { editandoPerfil = true })
            if (editandoPerfil) {
                com.example.ui.components.UserSettingsDialog(
                    onDismissRequest = { editandoPerfil = false },
                    onConfigurarAlertasClick = { editandoPerfil = false }
                )
            }

            seccionCuenta?.invoke()

            // CATEGORY: APARIENCIA (TEMA DE COLORES Y MODO OSCURO)
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Paleta de colores",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Se aplica al instante en toda la app y se recuerda al volver a abrirla.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectorTemas(
                        actual = temaActual,
                        preferenciaOscuro = isDarkTheme,
                        onElegir = { tema ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            temaActual = tema
                            AppThemeState.tema = tema
                            WeatherNotificationService.setTema(context, tema)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val modoFijo = temaActual.modo != ModoTema.SEGUN_AJUSTE
                    val oscuroVisible = temaActual.usaOscuro(isDarkTheme)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (oscuroVisible) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (oscuroVisible) "Modo oscuro" else "Modo claro",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (temaActual.modo) {
                                        ModoTema.SIEMPRE_OSCURO -> "${temaActual.nombre} usa siempre modo oscuro"
                                        ModoTema.SIEMPRE_CLARO -> "${temaActual.nombre} usa siempre modo claro"
                                        ModoTema.SEGUN_AJUSTE -> "Alternar aspecto visual del sistema"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = oscuroVisible,
                            enabled = !modoFijo,
                            onCheckedChange = { checked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isDarkTheme = checked
                                AppThemeState.isDark = checked
                                WeatherNotificationService.setDarkModeEnabled(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AgroGreenPrimary
                            ),
                            modifier = Modifier.testTag("switch_theme_mode")
                        )
                    }
                }
            }

            // CATEGORY: RENDIMIENTO Y CONSUMO DE DATOS
            Text(
                text = "Rendimiento y Consumo de Datos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AgroGreenPrimary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            tint = AgroGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (dataSaverEnabled) "Modo Ahorro de Datos" else "Modo Alta Resolución",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (dataSaverEnabled) "Frecuencia de actualización reducida para APIs de clima y mercados." else "Frecuencia de actualización completa y en tiempo real.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = dataSaverEnabled,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dataSaverEnabled = checked
                            WeatherNotificationService.setDataSaverEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AgroGreenPrimary
                        ),
                        modifier = Modifier.testTag("switch_data_saver")
                    )
                }
            }

            // CATEGORY: CONFIGURACIÓN DE NOTIFICACIONES
            Text(
                text = "Configuración de Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AgroGreenPrimary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // GLOBAL ALERTS CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (globalAlertsEnabled) AgroGreenPrimary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Alertas Globales",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Activa o desactiva todas las alertas de sistema",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = globalAlertsEnabled,
                            onCheckedChange = { checked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                globalAlertsEnabled = checked
                                WeatherNotificationService.setNotificationsEnabled(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AgroGreenPrimary
                            ),
                            modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_global_alert")
                        )
                    }

                    AnimatedVisibility(
                        visible = globalAlertsEnabled,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // CATEGORY: CLIMA
                            Text(
                                text = "Alertas Climáticas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AgroGreenPrimary
                            )

                            // Weather Option: Rain
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = if (climateRainEnabled) AgroSkyBlue else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Precipitaciones e Intensidad",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Avisos de lluvias intensas y estimación",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = climateRainEnabled,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        climateRainEnabled = checked
                                        WeatherNotificationService.setRainAlertEnabled(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AgroGreenPrimary
                                    ),
                                    modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_rain")
                                )
                            }

                            // Weather Option: Extremes (Frost/Storms)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AcUnit,
                                        contentDescription = null,
                                        tint = if (climateFrostEnabled) AgroSkyBlue else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Heladas y Riesgo Térmico",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Avisos de heladas y temperaturas críticas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = climateFrostEnabled,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        climateFrostEnabled = checked
                                        WeatherNotificationService.setFrostAlertEnabled(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AgroGreenPrimary
                                    ),
                                    modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_frost")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // CATEGORY: FINANZAS Y MERCADO
                            Text(
                                text = "Finanzas y Mercados",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AgroGreenPrimary
                            )

                            // Master Market alerts switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AttachMoney,
                                        contentDescription = null,
                                        tint = if (marketAlertsEnabled) AgroGreenPrimary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Alertas Generales de Mercado",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Notificar sobre variaciones de bolsas y cotizaciones",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = marketAlertsEnabled,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        marketAlertsEnabled = checked
                                        WeatherNotificationService.setMarketAlertEnabled(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AgroGreenPrimary
                                    ),
                                    modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_market_master")
                                )
                            }

                            AnimatedVisibility(
                                visible = marketAlertsEnabled,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Option: Dolar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.AttachMoney,
                                                contentDescription = null,
                                                tint = if (marketDolarEnabled) AgroGreenPrimary else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Variaciones del Dólar",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Oficial, blue, MEP o CCL con variación diaria de ±2 % o más",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = marketDolarEnabled,
                                            onCheckedChange = { checked ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                marketDolarEnabled = checked
                                                WeatherNotificationService.setMarketDolarEnabled(context, checked)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AgroGreenPrimary
                                            ),
                                            modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_dolar")
                                        )
                                    }

                                    // Option: Grains
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = if (marketGrainsEnabled) AgroGreenPrimary else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Cambios en Granos",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Futuros de soja, maíz o trigo (A3) con ±2 % o más en el ajuste",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = marketGrainsEnabled,
                                            onCheckedChange = { checked ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                marketGrainsEnabled = checked
                                                WeatherNotificationService.setMarketGrainsEnabled(context, checked)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AgroGreenPrimary
                                            ),
                                            modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_grains")
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            // CATEGORY: NOVEDADES / PRENSA
                            Text(
                                text = "Novedades y Noticias",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AgroGreenPrimary
                            )

                            // Option: News
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Newspaper,
                                        contentDescription = null,
                                        tint = if (newsAlertsEnabled) AgroGreenPrimary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Información Sectorial",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Aviso de noticias nuevas de Clarín Rural, La Voz Agro y Agrofy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = newsAlertsEnabled,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        newsAlertsEnabled = checked
                                        WeatherNotificationService.setNewsAlertEnabled(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AgroGreenPrimary
                                    ),
                                    modifier = Modifier.padding(start = 12.dp).testTag("switch_settings_news")
                                )
                            }
                        }
                    }
                }
            }

            // CATEGORY: CREDENCIALES COPERNICUS (SENTINEL-2)
            // Con backend las imágenes vienen del servidor de GRANEX (solo hace falta la cuenta GRANEX)
            if (com.example.data.remote.api.ApiKeys.supabase == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Imágenes Sentinel-2 (Copernicus)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AgroGreenPrimary
                        )
                        Text(
                            text = "Para los mapas de prescripción a 10 m. Creá una cuenta gratuita en Copernicus y, en el panel de " +
                                "Sentinel Hub (User settings → OAuth clients), generá un cliente. Copiá acá el client ID y el secret: " +
                                "el secret se muestra una sola vez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        var cdseId by remember { mutableStateOf(WeatherNotificationService.getCopernicusClientId(context)) }
                        var cdseSecreto by remember { mutableStateOf(WeatherNotificationService.getCopernicusClientSecret(context)) }
                        var cdseSecretoPendiente by remember { mutableStateOf(false) }

                        LaunchedEffect(cdseSecreto, cdseSecretoPendiente) {
                            if (cdseSecretoPendiente) {
                                kotlinx.coroutines.delay(400)
                                val valor = cdseSecreto
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    WeatherNotificationService.setCopernicusClientSecret(context, valor)
                                }
                                cdseSecretoPendiente = false
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose {
                                if (cdseSecretoPendiente) WeatherNotificationService.setCopernicusClientSecret(context, cdseSecreto)
                            }
                        }

                        CampoIdentificador(
                            valor = cdseId,
                            onValorChange = {
                                cdseId = it
                                WeatherNotificationService.setCopernicusClientId(context, it)
                            },
                            etiqueta = "Client ID",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "input_cdse_id"
                        )

                        CampoSecreto(
                            valor = cdseSecreto,
                            onValorChange = {
                                cdseSecreto = it
                                cdseSecretoPendiente = true
                            },
                            etiqueta = "Client secret",
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "input_cdse_secret"
                        )

                        Column {
                            EnlaceExterno(
                                texto = "¿No tenés cuenta? Registrate gratis en Copernicus",
                                url = EnlacesRegistro.COPERNICUS_REGISTRO,
                                testTag = "link_registro_copernicus"
                            )
                            EnlaceExterno(
                                texto = "Generar client ID y secret (panel de Sentinel Hub)",
                                url = EnlacesRegistro.COPERNICUS_CLIENTES,
                                testTag = "link_clientes_copernicus"
                            )
                        }
                    }
                }
            }

            // CATEGORY: OTROS SERVICIOS DE DATOS (sin campos en la app)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Otros servicios de datos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary
                    )

                    val maptilerConfigurada = com.example.data.remote.api.ApiKeys.maptiler != null
                    Column {
                        Text("MapTiler (imagen satelital del mapa)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (maptilerConfigurada) "Clave configurada." else
                                "Opcional: sin clave se usa la imagen de Esri. La clave se carga en el archivo .env al compilar la app (MAPTILER_API_KEY).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        EnlaceExterno(
                            texto = "Crear cuenta gratis en MapTiler",
                            url = EnlacesRegistro.MAPTILER_REGISTRO,
                            testTag = "link_registro_maptiler"
                        )
                    }

                    Column {
                        Text("Open-Meteo (pronóstico del clima)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "No requiere cuenta. El plan gratuito es para uso no comercial; para uso comercial hace falta un plan con clave.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        EnlaceExterno(
                            texto = "Ver planes y condiciones de Open-Meteo",
                            url = EnlacesRegistro.OPEN_METEO_PLANES,
                            testTag = "link_planes_openmeteo"
                        )
                    }
                }
            }

            // CATEGORY: AYUDA Y SOPORTE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Ayuda y soporte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary
                    )
                    Text(
                        text = "Consultas, errores, sugerencias o pedidos sobre tus datos personales:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.example.ui.components.EnlaceSoporte(asunto = "GRANEX ${com.example.BuildConfig.VERSION_NAME}: consulta")
                    Text(
                        text = "Versión ${com.example.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Tarjetas de 2 × 2 con la vista previa de cada paleta (sus propios colores, no los del tema activo). */
@Composable
private fun SelectorTemas(actual: TemaGranex, preferenciaOscuro: Boolean, onElegir: (TemaGranex) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TemaGranex.entries.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { tema ->
                    TarjetaTema(
                        tema = tema,
                        oscuro = tema.usaOscuro(preferenciaOscuro),
                        seleccionado = tema == actual,
                        onClick = { onElegir(tema) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaTema(tema: TemaGranex, oscuro: Boolean, seleccionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val esquema = tema.esquema(oscuro)
    val marca = tema.marca(oscuro)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = esquema.background,
        border = androidx.compose.foundation.BorderStroke(
            if (seleccionado) 3.dp else 1.dp,
            if (seleccionado) AgroGreenPrimary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .testTag("tema_${tema.name.lowercase()}")
            .semantics { selected = seleccionado; role = Role.RadioButton }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(marca.primario, marca.acento, esquema.surfaceVariant, marca.info).forEach { c ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, esquema.outline.copy(alpha = 0.6f), CircleShape)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (seleccionado) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionado", tint = marca.primario, modifier = Modifier.size(18.dp))
                }
            }
            // Muestra de botón con los colores reales del tema
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(marca.primario),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Text(tema.nombre, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = esquema.onBackground, maxLines = 1)
            Text(tema.descripcion, style = MaterialTheme.typography.labelSmall, color = esquema.onSurfaceVariant, maxLines = 3, minLines = 3)
        }
    }
}
