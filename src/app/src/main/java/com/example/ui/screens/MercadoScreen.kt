package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import com.example.ui.components.Grano
import com.example.ui.components.IconoGrano
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.utils.FilaHistorial
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.MercadoViewModel
import com.example.ui.viewmodel.EstadoSerie
import com.example.ui.viewmodel.RangoTiempo
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MercadoScreen(
    viewModel: MercadoViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    /** Rediseño: selector Granos / Dólar y futuros debajo del encabezado. */
    selectorSeccion: (@Composable () -> Unit)? = null,
    /** Rediseño: tarjetas de la pizarra de hoy con los futuros (arriba del historial). */
    preciosHoy: (@Composable () -> Unit)? = null,
    onActualizar: () -> Unit = {},
    /** Rediseño: costos por hectárea de cada lote según la Agenda, para la calculadora. */
    costosAgenda: List<com.example.ui.components.CostosAgendaLote> = emptyList()
) {
    val selectedCultivo by viewModel.selectedCultivo.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cultivosDisponibles = viewModel.cultivosDisponibles
    val selectedInterval by viewModel.selectedInterval.collectAsStateWithLifecycle()
    val selectedRango = uiState.rangoSeleccionado
    val puntosGrafico = uiState.puntosGrafico
    val filasTabla = uiState.filasTabla
    val intervals = viewModel.intervals
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedPointIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCalculatorSheet by rememberSaveable { mutableStateOf(false) }

    // Dialogo de Error
    if (errorMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error de Conexión", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(errorMessage ?: "")
                    Text(
                        "Nota: Se ha producido un error al intentar descargar los precios actuales o históricos de granos.ar. Por favor comprueba tu conexión a internet e inténtalo de nuevo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.clearError() },
                    modifier = Modifier.testTag("btn_close_error")
                ) {
                    Text("Entendido", color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Bar Header with Crop Selector Dropdown
            // Rediseño: título grande, actualizar y el grano del historial
            com.example.ui.rediseno.EncabezadoR(
                titulo = "Mercado",
                subtitulo = "Pizarra Rosario, futuros y dólar"
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.fetchGranosData(selectedCultivo, forzar = true)
                        onActualizar()
                    },
                    modifier = Modifier.testTag("btn_refresh_mercado_granos")
                ) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                    // Selector de grano (Soja, Maíz, Trigo, Sorgo, Girasol) con su ícono
                    Box {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(start = 6.dp, end = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("dropdown_selector_cultivo")
                        ) {
                            IconoGrano(Grano.de(selectedCultivo), tamano = 22.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedCultivo,
                                fontWeight = FontWeight.Bold,
                                color = AgroGreenPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Seleccionar cultivo",
                                tint = AgroGreenPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            cultivosDisponibles.forEach { cultivo ->
                                DropdownMenuItem(
                                    leadingIcon = { IconoGrano(Grano.de(cultivo), tamano = 28.dp) },
                                    modifier = Modifier.testTag("opcion_cultivo_${cultivo.lowercase()}"),
                                    text = {
                                        Text(
                                            text = cultivo,
                                            fontWeight = if (cultivo == selectedCultivo) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.seleccionarCultivo(cultivo)
                                        selectedPointIndex = null
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            selectorSeccion?.invoke()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Rediseño: la pizarra de hoy de cada grano con sus futuros, y después el historial
                if (preciosHoy != null) {
                    item(key = "precios_hoy") { preciosHoy() }
                    item(key = "titulo_historial") { com.example.ui.rediseno.SeccionR("Historial de $selectedCultivo") }
                }
                // Summary Card
                item {
                    val ultimo = puntosGrafico.lastOrNull()
                    val variacionTotal = ultimo?.variacionPorcentaje ?: 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Fila 1: rueda de pizarra e insignia de variación
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val fechaLabel = if (ultimo != null) {
                                    // Se formatea solo cuando cambia la fecha, no en cada recomposición
                                    val formattedDate = remember(ultimo.fecha) { formatFechaPizarra(ultimo.fecha) }
                                    "Pizarra $selectedCultivo ($formattedDate)"
                                } else {
                                    "Pizarra $selectedCultivo"
                                }
                                IconoGrano(Grano.de(selectedCultivo), tamano = 32.dp, modifier = Modifier.padding(end = 10.dp))
                                Text(
                                    text = fechaLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (variacionTotal >= 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%+.2f%% rueda ant.", variacionTotal),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (variacionTotal >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Fila 2: precio en dólares, todo el ancho para él
                            Text(
                                text = String.format(Locale.getDefault(), "US$ %.2f / Ton", ultimo?.precioUsd ?: 0.0),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = AgroGreenPrimary,
                                maxLines = 1
                            )

                            // Fila 3: precio en pesos y acceso a la calculadora
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.getDefault(), "$ %,.0f ARS/Ton", ultimo?.precioArs ?: 0.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCalculatorSheet = true
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AgroGreenPrimary.copy(alpha = 0.12f),
                                        contentColor = AgroGreenPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .height(34.dp)
                                        .testTag("btn_open_calculator")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Simular",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Chart Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = "Gráfico de Precios",
                                    tint = AgroGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Evolución de Precios (USD/Ton)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interval Selector Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                 RangoTiempo.values().forEach { rango ->
                                     val isSelected = rango == selectedRango
                                     val text = rango.etiqueta
                                     Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(12.dp))
                                             .background(
                                                 if (isSelected) AgroGreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                             )
                                             .clickable {
                                                 haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                 viewModel.seleccionarRango(rango)
                                                 selectedPointIndex = null
                                             }
                                             .padding(horizontal = 14.dp, vertical = 6.dp)
                                     ) {
                                         Text(
                                             text = text,
                                             style = MaterialTheme.typography.labelMedium,
                                             fontWeight = FontWeight.Bold,
                                             color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                         )
                                     }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "${uiState.rangoSeleccionado.granularidad.leyendaGrafico} · ${puntosGrafico.size} puntos · tocá uno para ver el detalle",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom Interactive Canvas Chart
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                if (uiState.puntosGrafico.size >= 2) {
                                    androidx.compose.runtime.key(uiState.rangoSeleccionado, uiState.puntosGrafico) {
                                        InteractiveGrainCanvasChart(
                                            points = uiState.puntosGrafico,
                                            selectedIndex = selectedPointIndex,
                                            onPointSelected = { index -> selectedPointIndex = index }
                                        )
                                    }
                                } else {
                                    // Nunca un cuadro en blanco: se explica por qué no hay curva
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(20.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        when (val estado = uiState.estado) {
                                            is EstadoSerie.Cargando -> {
                                                CircularProgressIndicator(color = AgroGreenPrimary, modifier = Modifier.size(28.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Descargando el historial de la Cámara Arbitral…", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                            }
                                            is EstadoSerie.Error -> {
                                                Text(estado.mensaje, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedButton(onClick = { viewModel.fetchGranosData(selectedCultivo, forzar = true) }) { Text("Reintentar") }
                                            }
                                            is EstadoSerie.Lista -> Text(
                                                if (uiState.puntosGrafico.isEmpty()) "No hay cotizaciones de $selectedCultivo en este período."
                                                else "Hay una sola rueda en este período: elegí un rango más largo para ver la evolución.",
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            (uiState.estado as? EstadoSerie.Lista)?.let { e ->
                                Text(
                                    text = "Fuente: Cámara Arbitral de Cereales (BCR) · ${e.ruedas} ruedas · US$ con dólar mayorista del día" +
                                        if (e.desactualizada) " · sin conexión, datos guardados" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (e.desactualizada) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            // Tooltip details display on touch
                            val activePoint = selectedPointIndex?.let { idx -> puntosGrafico.getOrNull(idx) }
                            AnimatedVisibility(visible = activePoint != null) {
                                activePoint?.let { pt ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .testTag("chart_tooltip_info"),
                                        colors = CardDefaults.cardColors(containerColor = AgroGreenPrimary.copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        // Fecha y precio lado a lado si entran; si no, el precio va debajo
                                        // (con 360 dp y letra grande quedaba "Fecha: 2…" y "Cierre d…")
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically)) {
                                                Text(
                                                    text = "Fecha: ${pt.fecha}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.6.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = AgroGreenPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = pt.puerto,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.2.sp),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.CenterVertically)) {
                                                Text(
                                                    text = String.format(Locale.getDefault(), "US$ %.2f / Ton", pt.precioUsd),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.9.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = AgroGreenPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = String.format(Locale.getDefault(), "$ %,.0f ARS", pt.precioArs),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.6.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tabla histórica: días, semanas o meses según el rango elegido
                item(key = "tabla_titulo") {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Histórico de Cotizaciones en Rosario",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (uiState.rangoSeleccionado) {
                                RangoTiempo.SIETE_DIAS -> "Cierre de cada rueda y variación contra la anterior"
                                RangoTiempo.TREINTA_DIAS -> "Resumen semanal: cierre, mínimo–máximo y variación de la semana"
                                RangoTiempo.TRES_MESES -> "Resumen mensual: cierre, mínimo–máximo, promedio y variación del mes"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item(key = "tabla") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.padding(14.dp)) {
                            val diaria = uiState.rangoSeleccionado == RangoTiempo.SIETE_DIAS
                            // Las cuatro columnas necesitan unos 330 dp; si no entran, cada período va en
                            // dos renglones en vez de partir los números
                            val compacta = maxWidth < 330.dp
                            Column {
                                if (!compacta) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (diaria) "RUEDA" else "PERÍODO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                                        Text("CIERRE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                                        Text(if (diaria) "ARS/TON" else "MÍN–MÁX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                                        Text("VAR %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (filasTabla.isEmpty()) {
                                    Text(
                                        "Sin cotizaciones para este período.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                filasTabla.forEachIndexed { i, fila ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    if (compacta) {
                                        FilaHistorialCompacta(fila, diaria)
                                    } else Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = if (diaria) 7.dp else 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.4f)) {
                                            Text(fila.titulo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            fila.detalle?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1.1f)) {
                                            Text(String.format(Locale.getDefault(), "US$ %.1f", fila.cierreUsd), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            if (!diaria) {
                                                Text(String.format(Locale.getDefault(), "prom. %.1f", fila.promedioUsd), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(
                                            text = if (diaria) String.format(Locale.getDefault(), "$ %,.0f", fila.cierreArs)
                                            else String.format(Locale.getDefault(), "%.1f–%.1f", fila.minUsd, fila.maxUsd),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        TextoVariacion(fila.variacionPct, modifier = Modifier.weight(0.8f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Translucent Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) {}, // block touches
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AgroGreenPrimary, modifier = Modifier.size(48.dp))
                        Text(
                            text = "Conectando con granos.ar...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AgroGreenPrimary
                        )
                        Text(
                            text = "Obteniendo precios de pizarra en pesos, dólares y variaciones oficiales para $selectedCultivo...",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showCalculatorSheet) {
            val ultimo = puntosGrafico.lastOrNull()
            val currentPrice = ultimo?.precioUsd ?: 0.0
            val selectedDolarValue by viewModel.selectedDolarValue.collectAsStateWithLifecycle()

            // Clave por grano: al cambiar de cultivo la hoja arranca con los valores de ese grano
            key(selectedCultivo) {
                com.example.ui.components.CalculadoraRendimientoBottomSheet(
                    cultivo = selectedCultivo,
                    precioInicialUsd = currentPrice,
                    tipoCambio = selectedDolarValue,
                    onDismissRequest = { showCalculatorSheet = false },
                    valoresGuardados = viewModel.valoresCalculadora(selectedCultivo),
                    onValorCambiado = { clave, valor -> viewModel.guardarCampoCalculadora(selectedCultivo, clave, valor) },
                    onLimpiar = { viewModel.limpiarCalculadora(selectedCultivo) },
                    costosAgenda = costosAgenda
                )
            }
        }
    }
}

/** "yyyy-MM-dd" → "dd/MM/yyyy"; devuelve la fecha original si no tiene ese formato. */
private fun formatFechaPizarra(fecha: String): String = try {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US)
    parser.parse(fecha)?.let { formatter.format(it) } ?: fecha
} catch (e: Exception) {
    fecha
}

@Composable
fun InteractiveGrainCanvasChart(
    points: List<com.example.data.remote.model.CotizacionHistorica>,
    selectedIndex: Int?,
    onPointSelected: (Int) -> Unit
) {
    val greenColor = AgroGreenPrimary
    val goldColor = AgroGoldAccent
    val chartDescription = remember(points) {
        "Precio histórico en dólares: " + points.filter { it.precioUsd > 0.0 }.joinToString(", ") {
            String.format(Locale.getDefault(), "%s US$ %.1f", it.fecha, it.precioUsd)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = chartDescription }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val width = size.width
                    if (points.size > 1) {
                        val stepX = width / (points.size - 1)
                        val index = ((offset.x + (stepX / 2)) / stepX).toInt().coerceIn(0, points.size - 1)
                        onPointSelected(index)
                    }
                }
            }
    ) {
        val validPoints = points.filter { it.precioUsd > 0.0 }
        if (validPoints.size < 2) return@Canvas

        val baseMin = validPoints.minOf { it.precioUsd }
        val baseMax = validPoints.maxOf { it.precioUsd }
        val minY = if (baseMin == baseMax) baseMin - 10.0 else baseMin * 0.97
        val maxY = if (baseMin == baseMax) baseMax + 10.0 else baseMax * 1.03
        val rangeY = maxY - minY

        val width = size.width
        val height = size.height

        val stepX = width / (points.size - 1)

        // Los puntos sin precio (0) no se dibujan: antes quedaban fuera del área del gráfico y la
        // curva caía por debajo. La x sigue al índice original para que coincida con el toque.
        val pathPoints: List<Offset?> = points.mapIndexed { idx, p ->
            if (p.precioUsd <= 0.0) return@mapIndexed null
            val x = stepX * idx
            val y = height - (((p.precioUsd - minY) / rangeY) * height).toFloat()
            Offset(x, y)
        }
        val validOffsets = pathPoints.filterNotNull()

        // Draw background gradient fill under curve
        val strokePath = Path().apply {
            if (validOffsets.isNotEmpty()) {
                moveTo(validOffsets.first().x, validOffsets.first().y)
                for (i in 1 until validOffsets.size) {
                    val prev = validOffsets[i - 1]
                    val curr = validOffsets[i]
                    val cp1X = prev.x + (curr.x - prev.x) / 3f
                    val cp1Y = prev.y
                    val cp2X = prev.x + 2f * (curr.x - prev.x) / 3f
                    val cp2Y = curr.y
                    cubicTo(cp1X, cp1Y, cp2X, cp2Y, curr.x, curr.y)
                }
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(validOffsets.last().x, height)
            lineTo(validOffsets.first().x, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    greenColor.copy(alpha = 0.35f),
                    Color.Transparent
                )
            )
        )

        // Draw Line Curve
        drawPath(
            path = strokePath,
            color = greenColor,
            style = Stroke(width = 4.dp.toPx())
        )

        // Draw Points
        pathPoints.forEachIndexed { index, offset ->
            if (offset == null) return@forEachIndexed
            val isSelected = selectedIndex == index
            val radius = if (isSelected) 8.dp.toPx() else 4.dp.toPx()

            drawCircle(
                color = if (isSelected) goldColor else greenColor,
                radius = radius,
                center = offset
            )

            if (isSelected) {
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = offset
                )

                // Dashed vertical guide line
                drawLine(
                    color = goldColor,
                    start = Offset(offset.x, 0f),
                    end = Offset(offset.x, height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }
        }
    }
}

/** Variación del período, en verde o rojo; "--" si no hay período anterior para comparar. */
@Composable
private fun TextoVariacion(variacion: Double?, modifier: Modifier = Modifier) {
    Text(
        text = variacion?.let { String.format(Locale.getDefault(), "%+.2f%%", it) } ?: "--",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        softWrap = false,
        color = when {
            variacion == null -> MaterialTheme.colorScheme.onSurfaceVariant
            variacion >= 0 -> Color(0xFF16A34A)
            else -> Color(0xFFDC2626)
        },
        modifier = modifier
    )
}

/**
 * Un período de la tabla en dos renglones, para pantallas angostas o con letra grande: título y cierre
 * arriba; fechas, mínimo–máximo o pesos, y variación abajo.
 */
@Composable
private fun FilaHistorialCompacta(fila: FilaHistorial, diaria: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fila.titulo,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = String.format(Locale.getDefault(), "US$ %.1f", fila.cierreUsd),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                softWrap = false
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val detalle = if (diaria) {
                String.format(Locale.getDefault(), "$ %,.0f por tonelada", fila.cierreArs)
            } else {
                listOfNotNull(
                    fila.detalle,
                    String.format(Locale.getDefault(), "mín–máx %.1f–%.1f · prom. %.1f", fila.minUsd, fila.maxUsd, fila.promedioUsd)
                ).joinToString(" · ")
            }
            Text(
                text = detalle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            TextoVariacion(fila.variacionPct)
        }
    }
}
