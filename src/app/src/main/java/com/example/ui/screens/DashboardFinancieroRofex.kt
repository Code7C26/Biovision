package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import com.example.data.remote.model.DolarRateDto
import com.example.data.remote.model.FinancialDashboardData
import com.example.data.remote.model.FuturoRofexContract
import com.example.data.remote.ktor.ServicioFuturosA3
import com.example.data.remote.model.EstadoRofex
import androidx.compose.material3.TextButton
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.components.GrillaAdaptable
import com.example.ui.components.LadoALadoOApilados
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroGreenDark
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.FinancialUiState
import com.example.ui.viewmodel.FinancialViewModel
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone

@Composable
fun DashboardFinancieroRofexScreen(
    viewModel: FinancialViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    /** Rediseño: selector Granos / Dólar y futuros debajo del encabezado. */
    selectorSeccion: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Al entrar (o volver de Ajustes): usa el caché si está vigente y toma credenciales ROFEX nuevas
    LaunchedEffect(Unit) { viewModel.cargarDatosFinancieros() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Rediseño: el mismo encabezado que la vista Granos
        com.example.ui.rediseno.EncabezadoR(titulo = "Mercado", subtitulo = "Pizarra Rosario, futuros y dólar") {
            IconButton(
                onClick = { viewModel.cargarDatosFinancieros(forceRefresh = true) },
                modifier = Modifier.testTag("btn_refresh_finanzas")
            ) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
        }
        selectorSeccion?.invoke()

        when (val state = uiState) {
            is FinancialUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AgroGreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Obteniendo divisas y futuros ROFEX en tiempo real...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is FinancialUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Error de Conexión", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            is FinancialUiState.Success -> {
                FinancialContent(
                    data = state.data,
                    onRefresh = { viewModel.cargarDatosFinancieros(forceRefresh = true) },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
internal fun FinancialContent(
    data: FinancialDashboardData,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var selectedDolar by remember { mutableStateOf<DolarRateDto?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Rediseño: el dólar en una tabla (compra, venta y variación); tocar una fila abre el conversor
        item(key = "dolar") {
            com.example.ui.rediseno.TarjetaR {
                val hora = data.divisas.mapNotNull { it.fechaActualizacion.takeIf { f -> f.length >= 16 }?.substring(11, 16) }.maxOrNull()
                com.example.ui.rediseno.TituloTarjetaR("Dólar", extra = "dolarapi.com" + (hora?.let { " · $it" } ?: ""))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Tipo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = com.example.ui.rediseno.colorSecundario, modifier = Modifier.weight(1.3f))
                    Text("Compra", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = com.example.ui.rediseno.colorSecundario, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("Venta", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = com.example.ui.rediseno.colorSecundario, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("Var.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = com.example.ui.rediseno.colorSecundario, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                }
                data.divisas.forEach { dolar ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = "Convertir con ${dolar.nombre}") { selectedDolar = dolar; showCalculatorDialog = true }
                            .padding(horizontal = 4.dp).testTag("dolar_${dolar.casa}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dolar.nombre.removePrefix("Dólar "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.3f))
                        Text(
                            if (dolar.compra > 0) "$ " + String.format(java.util.Locale("es", "AR"), "%,.0f", dolar.compra) else "—",
                            fontSize = 14.sp, color = com.example.ui.rediseno.colorSecundario, modifier = Modifier.weight(1f), textAlign = TextAlign.End
                        )
                        Text(
                            "$ " + String.format(java.util.Locale("es", "AR"), "%,.0f", dolar.venta),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End
                        )
                        val v = dolar.variacion
                        Text(
                            when {
                                kotlin.math.abs(v) < 0.05 -> "="
                                v > 0 -> "▲ " + String.format(java.util.Locale("es", "AR"), "%.1f %%", v)
                                else -> "▼ " + String.format(java.util.Locale("es", "AR"), "%.1f %%", -v)
                            },
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = when {
                                kotlin.math.abs(v) < 0.05 -> com.example.ui.rediseno.colorSecundario
                                v > 0 -> AgroGreenPrimary
                                else -> Color(0xFFB45309)
                            },
                            modifier = Modifier.weight(0.9f), textAlign = TextAlign.End
                        )
                    }
                }
                Text("Variación contra el día hábil anterior. Tocá un dólar para convertir montos.", fontSize = 12.sp, color = com.example.ui.rediseno.colorSecundario)
            }
        }

        // Futuros de A3 Mercados (ex Matba ROFEX)
        item(key = "futuros") {
            com.example.ui.rediseno.TarjetaR {
                com.example.ui.rediseno.TituloTarjetaR("Futuros A3 Mercados")
                Text("Precio de ajuste de soja, maíz y trigo en Rosario", fontSize = 13.sp, color = com.example.ui.rediseno.colorSecundario)
                when (val rofex = data.rofex) {
                    is EstadoRofex.Error -> RofexAviso(mensaje = rofex.mensaje, accion = "Reintentar", onAccion = onRefresh)
                    is EstadoRofex.Ok -> {
                        RofexTabla(rofex.contratos)
                        FuenteRofex(rofex.contratos.maxOfOrNull { it.fechaAjuste.orEmpty() })
                    }
                }
            }
        }
    }

    if (showCalculatorDialog && selectedDolar != null) {
        CalculatorDialog(
            dolar = selectedDolar!!,
            onDismiss = {
                showCalculatorDialog = false
                selectedDolar = null
            }
        )
    }
}

@Composable
private fun RofexAviso(mensaje: String, accion: String, onAccion: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("rofex_aviso"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAccion, modifier = Modifier.align(Alignment.End)) { Text(accion) }
        }
    }
}

@Composable
private fun RofexTabla(contratos: List<FuturoRofexContract>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.padding(14.dp)) {
            // Las cuatro columnas necesitan unos 320 dp; si no entran, cada contrato va en dos
            // renglones en vez de partir el símbolo y el precio
            if (maxWidth < 320.dp) {
                Column {
                    contratos.forEachIndexed { index, contrato ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        RofexContratoCompacto(contrato)
                    }
                }
            } else {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CONTRATO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                        Text("AJUSTE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("VAR. %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                        Text("LOTES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    contratos.forEachIndexed { index, contrato ->
                        RofexContractRow(contrato = contrato)
                        if (index < contratos.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Rueda del ajuste y fuente: son precios de cierre, no tiempo real. */
@Composable
private fun FuenteRofex(fecha: String?) {
    val rueda = fecha?.takeIf { it.length >= 10 }?.let { "Ajuste del ${it.substring(8, 10)}/${it.substring(5, 7)} · " }.orEmpty()
    Text(
        text = "${rueda}Fuente: ${ServicioFuturosA3.FUENTE}. Precios de cierre, no en tiempo real.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).testTag("rofex_fuente")
    )
}

/** Variación contra el ajuste anterior; "—" si A3 no la informó. */
@Composable
private fun VariacionRofex(variacion: Double?, modifier: Modifier = Modifier) {
    val isPositive = (variacion ?: 0.0) >= 0
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (variacion == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = String.format(Locale.getDefault(), "%.2f%%", variacion),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                softWrap = false
            )
        }
    }
}

/** Un contrato en dos renglones (símbolo y precio; posición, lotes y variación), para pantallas angostas. */
@Composable
private fun RofexContratoCompacto(contrato: FuturoRofexContract) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = contrato.simbolo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.getDefault(), "US$ %.2f", contrato.precioAjusteUSD),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${contrato.cultivo} • ${contrato.posVencimiento} · ${contrato.volumenLotes} lotes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            VariacionRofex(contrato.variacionPorcentaje)
        }
    }
}

@Composable
fun DolarCardItem(
    dolar: DolarRateDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = dolar.variacion >= 0

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("card_dolar_${dolar.casa}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Nombre y variación en un renglón si entran; si no, la variación pasa abajo (antes se
            // apretaba en una tira vertical: "0. / 0 / 0 / %")
            LadoALadoOApilados(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 6.dp) {
                Text(
                    text = dolar.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPositive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f%%", dolar.variacion),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compra y venta lado a lado; si no entran, la venta va debajo. Un precio nunca se parte.
            LadoALadoOApilados(modifier = Modifier.fillMaxWidth(), espacioVertical = 6.dp) {
                Column {
                    Text(
                        text = "Compra",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "$%.1f", dolar.compra),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Venta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "$%.1f", dolar.venta),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgroGreenPrimary,
                        softWrap = false
                    )
                }
            }

            if (dolar.fechaActualizacion.isNotEmpty()) {
                // Los cuadros de una fila tienen la misma altura: la hora queda abajo en todos
                Spacer(modifier = Modifier.weight(1f).height(6.dp))
                Text(
                    text = "Act: ${formatFechaActualizacion(dolar.fechaActualizacion)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun RofexContractRow(
    contrato: FuturoRofexContract
) {
    val variacion = contrato.variacionPorcentaje

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = contrato.simbolo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${contrato.cultivo} • ${contrato.posVencimiento}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = String.format(Locale.getDefault(), "US$ %.2f", contrato.precioAjusteUSD),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        VariacionRofex(variacion, modifier = Modifier.weight(0.9f))

        Text(
            text = "${contrato.volumenLotes}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
fun CalculatorDialog(
    dolar: DolarRateDto,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    // direction: true means "USD to ARS", false means "ARS to USD"
    var usdToArs by rememberSaveable { mutableStateOf(true) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val rate = if (usdToArs) dolar.compra else dolar.venta
    val result = if (usdToArs) {
        amount * rate
    } else {
        if (rate > 0) amount / rate else 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = AgroGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Calculadora - ${dolar.nombre}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Direction Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // USD to ARS Button
                    Button(
                        onClick = { usdToArs = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_conv_usd_ars"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (usdToArs) AgroGreenPrimary else Color.Transparent,
                            contentColor = if (usdToArs) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        elevation = null
                    ) {
                        Text(
                            text = "USD a ARS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // ARS to USD Button
                    Button(
                        onClick = { usdToArs = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_conv_ars_usd"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!usdToArs) AgroGreenPrimary else Color.Transparent,
                            contentColor = if (!usdToArs) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        elevation = null
                    ) {
                        Text(
                            text = "ARS a USD",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Input Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val sanitized = input.replace(",", ".").filter { it.isDigit() || it == '.' }
                        if (sanitized.count { it == '.' } <= 1) {
                            amountText = sanitized
                        }
                    },
                    label = {
                        Text(
                            text = if (usdToArs) "Monto en USD (Dólares)" else "Monto en ARS (Pesos)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    placeholder = {
                        Text(
                            text = "0.00",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = AgroGreenPrimary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_conv_amount")
                )

                // Tasa: al lado del texto si entra; si no, debajo (antes se partía en "$151 / 0.00 / ARS")
                LadoALadoOApilados(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = AgroGreenPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    espacioVertical = 2.dp
                ) {
                    Text(
                        text = if (usdToArs) "Tasa Aplicada (Compra):" else "Tasa Aplicada (Venta):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "$%.2f ARS", rate),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary,
                        softWrap = false
                    )
                }

                // Live Result
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RESULTADO ESTIMADO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (usdToArs) {
                            String.format(Locale.getDefault(), "$ %.2f ARS", result)
                        } else {
                            String.format(Locale.getDefault(), "U\$S %.2f", result)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgroGreenPrimary,
                        modifier = Modifier.testTag("txt_conv_result")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_conv_close")
            ) {
                Text(
                    text = "Cerrar",
                    fontWeight = FontWeight.Bold,
                    color = AgroGreenPrimary
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/** Estado del mercado y frescura de los datos, para la línea de chips del encabezado. */
private fun chipsFinanzas(estado: FinancialUiState): List<ChipEncabezado> {
    val abierto = checkMercadoAbierto()
    val mercado = ChipEncabezado(
        texto = if (abierto) "Mercado abierto" else "Mercado cerrado",
        punto = if (abierto) AgroGreenPrimary else Color(0xFFD32F2F)
    )
    val datos = (estado as? FinancialUiState.Success)?.data ?: return listOf(mercado)
    val hora = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(datos.timestampCache))
    return listOf(
        mercado,
        ChipEncabezado(
            texto = if (datos.isFromCache) "Caché · $hora" else "En vivo · $hora",
            icono = if (datos.isFromCache) Icons.Default.Cached else Icons.Default.Sync
        )
    )
}

fun checkMercadoAbierto(): Boolean {
    return try {
        val tz = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
        val calendar = Calendar.getInstance(tz)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val isBusinessDay = dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
        val isWorkingHours = (hour in 10..16) || (hour == 17 && minute == 0)
        
        isBusinessDay && isWorkingHours
    } catch (e: Exception) {
        true
    }
}

fun formatFechaActualizacion(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        if (isoString.contains("T")) {
            val parts = isoString.split("T")
            if (parts.size > 1) {
                val timePart = parts[1] // "17:15:00.000Z"
                val timeSubparts = timePart.split(":")
                if (timeSubparts.size >= 2) {
                    val hh = timeSubparts[0].toIntOrNull()
                    val mm = timeSubparts[1]
                    if (hh != null) {
                        // Adjust to Argentina timezone (UTC-3)
                        val argHh = (hh - 3 + 24) % 24
                        val hhStr = if (argHh < 10) "0$argHh" else "$argHh"
                        return "$hhStr:$mm hs"
                    }
                }
            }
        }
        isoString
    } catch (e: Exception) {
        isoString
    }
}
