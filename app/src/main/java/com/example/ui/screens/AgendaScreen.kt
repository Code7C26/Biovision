package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.agenda.Agenda
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.CostoLote
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.InsumoEnStock
import com.example.data.agenda.LoteAgenda
import com.example.data.agenda.Moneda
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.AgendaViewModel
import com.example.ui.viewmodel.EstadoAgenda
import com.example.ui.viewmodel.FiltroAgenda
import java.util.Locale

// ---------- Formatos y colores compartidos por las pantallas de la Agenda ----------

private val AR = Locale("es", "AR")

internal fun usdAgenda(v: Double) = "US$ " + String.format(AR, "%,.0f", v)
internal fun pesosAgenda(v: Double) = "$ " + String.format(AR, "%,.0f", v)
internal fun numero(v: Double): String =
    if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e9) String.format(AR, "%,.0f", v) else String.format(AR, "%,.2f", v)

internal fun colorCategoria(c: CategoriaGasto): Color = when (c) {
    CategoriaGasto.SEMILLA -> Color(0xFF047857)
    CategoriaGasto.FERTILIZANTE -> Color(0xFF0284C7)
    CategoriaGasto.FITOSANITARIO -> Color(0xFFD97706)
    CategoriaGasto.LABOR -> Color(0xFF7C3AED)
    CategoriaGasto.COMBUSTIBLE -> Color(0xFF475569)
    CategoriaGasto.OTRO -> Color(0xFF94A3B8)
}


/** "2026-11-21" → "21 nov". */
internal fun fechaAgenda(iso: String): String = Agenda.fechaCorta(iso)

private val AMBAR_FONDO = Color(0xFFFFFBEB)
private val AMBAR_TEXTO = Color(0xFF78350F)

// ---------- Pantalla principal ----------

private enum class VistaAgenda(val nombre: String) { GASTOS("Gastos"), INSUMOS("Insumos"), LOTES("Por lote") }

/**
 * La Agenda de la campaña: gastos (con lo que queda por pagar), stock de insumos y costo por lote.
 * Se guarda en el teléfono y se copia a la cuenta (la misma Agenda en la web y en otros teléfonos).
 */
@Composable
fun AgendaScreen(viewModel: AgendaViewModel, onAnotar: (gastoId: String?) -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var vista by rememberSaveable { mutableStateOf(VistaAgenda.GASTOS) }
    var aplicando by remember { mutableStateOf<InsumoEnStock?>(null) }
    val context = LocalContext.current

    aplicando?.let { insumo ->
        DialogoAplicacion(
            insumo = insumo,
            lotes = estado.lotes,
            onCancelar = { aplicando = null },
            onGuardar = { cantidad, lote ->
                viewModel.registrarAplicacion(insumo, cantidad, lote)
                aplicando = null
            }
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EncabezadoAgenda(
            estado = estado,
            onCampana = viewModel::elegirCampana,
            onCompartir = { compartirPlanilla(context, estado) }
        )
    Box(Modifier.weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("pantalla_agenda"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SelectorVista(vista) { vista = it } }
            when (vista) {
                VistaAgenda.GASTOS -> {
                    item { TarjetaResumen(estado) }
                    item { FiltrosGastos(estado, viewModel::filtrar) }
                    if (estado.cargado && estado.dias.isEmpty()) {
                        item {
                            Vacio(
                                if (estado.filtro.activo) "No hay gastos con este filtro."
                                else "Todavía no anotaste gastos de la campaña ${estado.campana}. Tocá \"Anotar gasto\" para empezar: semillas, fertilizantes, labores, gasoil y todo lo que pagues."
                            )
                        }
                    }
                    estado.dias.forEach { dia ->
                        item(key = "dia_${dia.fecha}") {
                            Text(
                                fechaAgenda(dia.fecha) + " · " + dia.fecha.take(4),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                        items(dia.gastos, key = { it.id }) { g -> FilaGasto(g) { onAnotar(g.id) } }
                    }
                }
                VistaAgenda.INSUMOS -> {
                    if (estado.stock.isEmpty()) {
                        item { Vacio("Acá aparece lo que compraste de semillas, fertilizantes, fitosanitarios y combustible, con lo que ya usaste y lo que te queda.") }
                    }
                    items(estado.stock, key = { it.producto + it.unidad }) { insumo ->
                        TarjetaInsumo(insumo) { aplicando = insumo }
                    }
                    if (estado.aplicaciones.isNotEmpty()) {
                        item {
                            Text(
                                "Últimas aplicaciones", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }
                        items(estado.aplicaciones.take(20), key = { it.id }) { a ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface).padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text("${a.producto} · ${numero(a.cantidad)} ${a.unidad}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        fechaAgenda(a.fecha) + " · " + (a.loteNombre ?: "sin lote"),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.borrarAplicacion(a.id) }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                VistaAgenda.LOTES -> {
                    if (estado.lotes.isEmpty()) {
                        item { Vacio("Cargá tus lotes en Campo para ver cuánto llevás gastado por hectárea en cada uno.") }
                    }
                    items(estado.costos.lotes, key = { it.lote.id }) { c -> TarjetaCostoLote(c, estado.costos.generalesPorHa) }
                    if (estado.costos.generalesUsd > 0) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Gastos generales", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Sin lote: se reparten por hectárea (${usdAgenda(estado.costos.generalesPorHa)}/ha)",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(usdAgenda(estado.costos.generalesUsd), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    if (estado.pendiente) "Hay cambios sin subir a tu cuenta: se suben solos apenas haya conexión."
                    else "Se guarda en tu cuenta: la ves igual en la web y en tus otros teléfonos.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onAnotar(null) },
            containerColor = AgroGreenPrimary,
            contentColor = Color.White,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Anotar gasto", fontWeight = FontWeight.SemiBold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("btn_anotar_gasto")
        )
    }
    }
}

/** Encabezado del rediseño: "Agenda", la campaña (se toca para cambiarla), lo que falta subir y compartir. */
@Composable
private fun EncabezadoAgenda(estado: EstadoAgenda, onCampana: (String) -> Unit, onCompartir: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Agenda", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    Surface(
                        onClick = { menu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("btn_campana_agenda")
                    ) {
                        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Campaña ${estado.campana}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir campaña", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        estado.campanas.forEach { c ->
                            DropdownMenuItem(text = { Text("Campaña $c") }, onClick = { onCampana(c); menu = false })
                        }
                    }
                }
                if (estado.pendiente) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("Sin subir", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        IconButton(onClick = onCompartir, modifier = Modifier.size(48.dp).testTag("btn_compartir_agenda")) {
            Icon(Icons.Default.Share, contentDescription = "Compartir planilla")
        }
    }
}

@Composable
private fun SelectorVista(vista: VistaAgenda, onCambiar: (VistaAgenda) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VistaAgenda.entries.forEach { v ->
            val elegida = v == vista
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (elegida) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (elegida) 1.dp else 0.dp,
                modifier = Modifier.weight(1f).height(42.dp)
                    .selectable(selected = elegida, onClick = { onCambiar(v) }, role = Role.Tab)
                    .testTag("vista_agenda_${v.name.lowercase()}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        v.nombre, fontSize = 14.sp,
                        fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium,
                        color = if (elegida) AgroGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BarraCategorias(porCategoria: Map<CategoriaGasto, Double>, alto: Int = 12) {
    val total = porCategoria.values.sum()
    if (total <= 0) return
    Row(Modifier.fillMaxWidth().height(alto.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CategoriaGasto.entries.forEach { c ->
            val v = porCategoria[c] ?: 0.0
            if (v > 0) Box(Modifier.weight((v / total).toFloat()).fillMaxSize().background(colorCategoria(c)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaResumen(estado: EstadoAgenda) {
    val r = estado.resumen
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.testTag("resumen_agenda")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Gastado en la campaña", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(usdAgenda(r.totalUsd), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    if (r.totalPesos > 0) Text(
                        "≈ ${pesosAgenda(r.totalPesos)} al dólar de cada día",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                estado.usdPorHa?.let { porHa ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${usdAgenda(porHa)}/ha", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                        Text("${numero(estado.hectareas)} ha", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            BarraCategorias(r.porCategoria)
            if (r.porCategoria.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.porCategoria.forEach { (c, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colorCategoria(c)))
                            Text("${c.nombre} ", fontSize = 12.sp)
                            Text(String.format(AR, "%,.0f", v), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (r.facturasAPagar > 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AMBAR_FONDO).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AMBAR_TEXTO, modifier = Modifier.size(18.dp))
                    Text(
                        "A pagar ${usdAgenda(r.aPagarUsd)} en ${r.facturasAPagar} " + (if (r.facturasAPagar == 1) "factura" else "facturas") +
                            (r.proximoVencimiento?.let { " · vence el ${fechaAgenda(it)}" } ?: ""),
                        fontSize = 13.sp, color = AMBAR_TEXTO
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltrosGastos(estado: EstadoAgenda, onFiltro: (FiltroAgenda) -> Unit) {
    val f = estado.filtro
    var menuCategoria by remember { mutableStateOf(false) }
    var menuLote by remember { mutableStateOf(false) }
    val colores = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !f.activo, onClick = { onFiltro(FiltroAgenda()) }, label = { Text("Todos") }, colors = colores)
        Box {
            FilterChip(
                selected = f.categoria != null, onClick = { menuCategoria = true }, colors = colores,
                label = { Text(f.categoria?.nombre ?: "Categoría") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuCategoria, onDismissRequest = { menuCategoria = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { onFiltro(f.copy(categoria = null)); menuCategoria = false })
                CategoriaGasto.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.nombre) }, onClick = { onFiltro(f.copy(categoria = c)); menuCategoria = false })
                }
            }
        }
        Box {
            val nombreLote = when (f.loteId) {
                null -> "Lote"
                FiltroAgenda.GENERAL -> "General"
                else -> estado.lotes.firstOrNull { it.id == f.loteId }?.nombre ?: "Lote"
            }
            FilterChip(
                selected = f.loteId != null, onClick = { menuLote = true }, colors = colores,
                label = { Text(nombreLote, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuLote, onDismissRequest = { menuLote = false }) {
                DropdownMenuItem(text = { Text("Todos los lotes") }, onClick = { onFiltro(f.copy(loteId = null)); menuLote = false })
                estado.lotes.forEach { l ->
                    DropdownMenuItem(text = { Text(l.nombre) }, onClick = { onFiltro(f.copy(loteId = l.id)); menuLote = false })
                }
                DropdownMenuItem(text = { Text("General (sin lote)") }, onClick = { onFiltro(f.copy(loteId = FiltroAgenda.GENERAL)); menuLote = false })
            }
        }
        FilterChip(selected = f.soloAPagar, onClick = { onFiltro(f.copy(soloAPagar = !f.soloAPagar)) }, label = { Text("A pagar") }, colors = colores)
    }
}

@Composable
private fun FilaGasto(g: Gasto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("gasto_${g.id}")
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colorCategoria(g.categoria).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(g.categoria.nombre.take(1), fontWeight = FontWeight.Bold, color = colorCategoria(g.categoria))
            }
            Column(Modifier.weight(1f)) {
                Text(g.producto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "${numero(g.cantidad)} ${g.unidad}",
                        g.loteNombre ?: "general",
                        g.proveedor.takeIf { it.isNotBlank() },
                        if (g.moneda == Moneda.ARS) pesosAgenda(g.total) else null
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(usdAgenda(g.totalUsd), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val (texto, color) = when (g.estadoPago) {
                    EstadoPago.PAGADO -> "Pagado" to AgroGreenPrimary
                    EstadoPago.CANJE -> "Canje" to MaterialTheme.colorScheme.onSurfaceVariant
                    EstadoPago.A_PAGAR -> (g.vencimiento?.let { "Vence ${fechaAgenda(it)}" } ?: "A pagar") to Color(0xFFB45309)
                }
                Text(texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun TarjetaInsumo(i: InsumoEnStock, onAplicar: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (i.poco) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)) else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colorCategoria(i.categoria)))
                Column(Modifier.weight(1f)) {
                    Text(i.producto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${i.categoria.nombre} · US$ ${if (i.usdPorUnidad < 100) String.format(AR, "%,.2f", i.usdPorUnidad) else String.format(AR, "%,.0f", i.usdPorUnidad)} por ${i.unidad.removeSuffix("s")}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(numero(i.restante), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (i.poco) Color(0xFFB45309) else Color.Unspecified)
                    Text("${i.unidad} quedan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(
                progress = { i.fraccionUsada.toFloat() },
                color = colorCategoria(i.categoria),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Comprado ${numero(i.comprado)} · usado ${numero(i.aplicado)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAplicar) { Text("Registrar uso", color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold) }
            }
            if (i.poco) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                    Text("Queda poco: menos del 15 % de lo comprado.", fontSize = 12.sp, color = AMBAR_TEXTO)
                }
            }
        }
    }
}

@Composable
private fun TarjetaCostoLote(c: CostoLote, generalesPorHa: Double) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(c.lote.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${numero(c.lote.hectareas)} ha", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${usdAgenda(c.usdPorHa)}/ha", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                    Text("${usdAgenda(c.totalUsd)} en total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BarraCategorias(c.porCategoria, alto = 10)
            if (generalesPorHa > 0) Text(
                "Con los gastos generales: ${usdAgenda(c.usdPorHa + generalesPorHa)}/ha",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Vacio(texto: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(texto, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun DialogoAplicacion(
    insumo: InsumoEnStock,
    lotes: List<LoteAgenda>,
    onCancelar: () -> Unit,
    onGuardar: (Double, LoteAgenda?) -> Unit
) {
    var cantidad by rememberSaveable { mutableStateOf("") }
    var loteId by rememberSaveable { mutableStateOf<String?>(lotes.firstOrNull()?.id) }
    var menu by remember { mutableStateOf(false) }
    val valor = leerNumero(cantidad)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Registrar uso de ${insumo.producto}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quedan ${numero(insumo.restante)} ${insumo.unidad}.", fontSize = 14.sp)
                OutlinedTextField(
                    value = cantidad, onValueChange = { cantidad = it },
                    label = { Text("Cantidad usada (${insumo.unidad})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Lote: " + (lotes.firstOrNull { it.id == loteId }?.nombre ?: "sin lote"), modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        lotes.forEach { l -> DropdownMenuItem(text = { Text(l.nombre) }, onClick = { loteId = l.id; menu = false }) }
                        DropdownMenuItem(text = { Text("Sin lote") }, onClick = { loteId = null; menu = false })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { valor?.let { onGuardar(it, lotes.firstOrNull { l -> l.id == loteId }) } },
                enabled = valor != null && valor > 0,
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/** Acepta coma o punto decimal ("1,5" o "1.5"); null si no es un número. */
internal fun leerNumero(texto: String): Double? = Agenda.leerNumero(texto)

/** Comparte la planilla de la campaña como CSV (se abre en Excel o Google Sheets). */
private fun compartirPlanilla(context: android.content.Context, estado: EstadoAgenda) {
    val filas = buildString {
        appendLine("Fecha;Categoría;Producto;Cantidad;Unidad;Precio unitario;Moneda;Dólar del día;Total US$;Lote;Proveedor;Pago;Vence")
        estado.dias.flatMap { it.gastos }.sortedBy { it.fecha }.forEach { g ->
            appendLine(
                listOf(
                    g.fecha, g.categoria.nombre, g.producto, numero(g.cantidad), g.unidad, String.format(AR, "%.2f", g.precioUnitario),
                    g.moneda.simbolo, String.format(AR, "%.2f", g.tipoCambio), String.format(AR, "%.2f", g.totalUsd),
                    g.loteNombre ?: "General", g.proveedor, g.estadoPago.nombre, g.vencimiento ?: ""
                ).joinToString(";") { it.replace(";", ",").replace("\n", " ") }
            )
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GRANEX · Agenda campaña ${estado.campana}")
        putExtra(Intent.EXTRA_TEXT, filas)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir planilla"))
}
