package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.agenda.Agenda
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.Moneda
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.AgendaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Anotar o editar un gasto de la Agenda ([gastoId] null = nuevo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnotarGastoScreen(viewModel: AgendaViewModel, gastoId: String?, onListo: () -> Unit) {
    val existente = remember(gastoId) { gastoId?.let { viewModel.gasto(it) } }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val dolarHoy by viewModel.tipoCambio.collectAsStateWithLifecycle()

    var categoria by rememberSaveable { mutableStateOf(existente?.categoria ?: CategoriaGasto.SEMILLA) }
    var producto by rememberSaveable { mutableStateOf(existente?.producto ?: "") }
    var cantidad by rememberSaveable { mutableStateOf(existente?.cantidad?.let(::numero)?.replace(".", "") ?: "") }
    var unidad by rememberSaveable { mutableStateOf(existente?.unidad ?: CategoriaGasto.SEMILLA.unidadSugerida) }
    // Si el usuario eligió la unidad a mano, elegir un producto de la lista no se la cambia
    var unidadTocada by rememberSaveable { mutableStateOf(existente != null) }
    var precio by rememberSaveable { mutableStateOf(existente?.precioUnitario?.let { String.format(Locale("es", "AR"), "%.2f", it).removeSuffix(",00") } ?: "") }
    var moneda by rememberSaveable { mutableStateOf(existente?.moneda ?: Moneda.USD) }
    var tipoCambio by rememberSaveable { mutableStateOf(existente?.tipoCambio?.takeIf { it > 0 }?.let { String.format(Locale("es", "AR"), "%.2f", it).removeSuffix(",00") } ?: "") }
    var loteId by rememberSaveable { mutableStateOf(existente?.loteId) }
    var fecha by rememberSaveable { mutableStateOf(existente?.fecha ?: viewModel.fechaDeHoy()) }
    var proveedor by rememberSaveable { mutableStateOf(existente?.proveedor ?: "") }
    var pago by rememberSaveable { mutableStateOf(existente?.estadoPago ?: EstadoPago.PAGADO) }
    var vence by rememberSaveable { mutableStateOf(existente?.vencimiento) }
    var alStock by rememberSaveable { mutableStateOf(existente?.alStock ?: true) }
    var stockTocado by rememberSaveable { mutableStateOf(existente != null) }
    var notas by rememberSaveable { mutableStateOf(existente?.notas ?: "") }
    var eligiendoFecha by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmarBorrar by rememberSaveable { mutableStateOf(false) }

    // El dólar del día llega después de abrir: completa el campo si el usuario no escribió otro
    LaunchedEffect(dolarHoy) {
        if (tipoCambio.isBlank()) dolarHoy?.let { tipoCambio = String.format(Locale("es", "AR"), "%.2f", it).removeSuffix(",00") }
    }
    // Si el lote del gasto se borró, queda como general
    val lotes = estado.lotes
    if (loteId != null && lotes.isNotEmpty() && lotes.none { it.id == loteId }) loteId = null

    val vCantidad = leerNumero(cantidad)
    val vPrecio = leerNumero(precio)
    val vTc = leerNumero(tipoCambio)
    val total = if (vCantidad != null && vPrecio != null) vCantidad * vPrecio else null
    val totalUsd = total?.let { if (moneda == Moneda.USD) it else vTc?.takeIf { tc -> tc > 0 }?.let { tc -> it / tc } }
    val valido = producto.isNotBlank() && vCantidad != null && vCantidad > 0 && vPrecio != null && vPrecio > 0 &&
        (moneda == Moneda.USD || (vTc != null && vTc > 0))

    if (confirmarBorrar && existente != null) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            title = { Text("¿Borrar este gasto?") },
            text = { Text("${existente.producto} · ${usdAgenda(existente.totalUsd)}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.borrar(existente.id); confirmarBorrar = false; onListo() }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } }
        )
    }
    eligiendoFecha?.let { campo ->
        val inicial = if (campo == "vence") vence ?: fecha else fecha
        val estadoFecha = rememberDatePickerState(initialSelectedDateMillis = isoAMillis(inicial))
        DatePickerDialog(
            onDismissRequest = { eligiendoFecha = null },
            confirmButton = {
                TextButton(onClick = {
                    estadoFecha.selectedDateMillis?.let { ms -> if (campo == "vence") vence = millisAIso(ms) else fecha = millisAIso(ms) }
                    eligiendoFecha = null
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { eligiendoFecha = null }) { Text("Cancelar") } }
        ) { DatePicker(state = estadoFecha) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onListo, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            Text(if (existente == null) "Anotar gasto" else "Editar gasto", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (existente != null) {
                IconButton(onClick = { confirmarBorrar = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar gasto", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Etiqueta("Categoría")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoriaGasto.entries.chunked(3).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { c ->
                            val elegida = c == categoria
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (elegida) colorCategoria(c).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) colorCategoria(c) else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .selectable(selected = elegida, role = Role.RadioButton, onClick = {
                                        categoria = c
                                        if (!stockTocado) alStock = c.alStock
                                        if (!unidadTocada) unidad = c.unidadSugerida
                                    })
                                    .testTag("categoria_${c.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text(c.nombre, fontSize = 13.sp, fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Se escribe libre o se elige de la lista de la categoría (con su ícono)
            CampoProductoAgenda(
                valor = producto,
                categoria = categoria,
                usados = estado.productosUsados,
                onCambio = { producto = it },
                onElegir = { opcion ->
                    producto = opcion.nombre
                    if (!unidadTocada && opcion.unidad.isNotEmpty()) unidad = opcion.unidad
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = cantidad, onValueChange = { cantidad = it }, label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f).testTag("campo_cantidad")
                )
                Selector(unidad, Agenda.UNIDADES, Modifier.weight(1f).padding(top = 8.dp)) { unidad = it; unidadTocada = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = precio, onValueChange = { precio = it },
                    label = { Text("Precio por ${unidad.removeSuffix("s")}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f).testTag("campo_precio")
                )
                Segmentos(
                    opciones = Moneda.entries.map { it.simbolo }, elegida = moneda.ordinal,
                    modifier = Modifier.weight(1f).padding(top = 8.dp)
                ) { moneda = Moneda.entries[it] }
            }
            OutlinedTextField(
                value = tipoCambio, onValueChange = { tipoCambio = it },
                label = { Text("Dólar del día ($ por US$)") },
                supportingText = { Text(if (moneda == Moneda.ARS) "Para pasar el gasto a dólares" else "Para mostrar el gasto en pesos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(totalUsd?.let(::usdAgenda) ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                val loteHa = lotes.firstOrNull { it.id == loteId }?.hectareas
                Column(horizontalAlignment = Alignment.End) {
                    if (total != null && moneda == Moneda.ARS) Text(pesosAgenda(total), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (total != null && moneda == Moneda.USD && vTc != null && vTc > 0) Text("≈ ${pesosAgenda(total * vTc)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (totalUsd != null && loteHa != null && loteHa > 0) Text("${String.format(Locale("es", "AR"), "%,.1f", totalUsd / loteHa)} US$/ha", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiqueta("Lote")
                    Selector(
                        valor = lotes.firstOrNull { it.id == loteId }?.let { "${it.nombre} (${numero(it.hectareas)} ha)" } ?: "General (sin lote)",
                        opciones = lotes.map { "${it.nombre} (${numero(it.hectareas)} ha)" } + "General (sin lote)",
                        modifier = Modifier.fillMaxWidth()
                    ) { elegido -> loteId = lotes.firstOrNull { "${it.nombre} (${numero(it.hectareas)} ha)" == elegido }?.id }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiqueta("Fecha")
                    BotonFecha(fechaAgenda(fecha) + " " + fecha.take(4)) { eligiendoFecha = "fecha" }
                }
            }

            OutlinedTextField(
                value = proveedor, onValueChange = { proveedor = it }, label = { Text("Proveedor (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Etiqueta("Pago")
            Segmentos(EstadoPago.entries.map { it.nombre }, pago.ordinal, Modifier.fillMaxWidth()) { pago = EstadoPago.entries[it] }
            if (pago == EstadoPago.A_PAGAR) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vence", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    BotonFecha(vence?.let { fechaAgenda(it) + " " + it.take(4) } ?: "Elegir fecha") { eligiendoFecha = "vence" }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sumar al stock de insumos", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Para llevar cuánto te queda", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = alStock, onCheckedChange = { alStock = it; stockTocado = true },
                    colors = SwitchDefaults.colors(checkedTrackColor = AgroGreenPrimary)
                )
            }
            OutlinedTextField(
                value = notas, onValueChange = { notas = it }, label = { Text("Notas (opcional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onListo, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancelar", color = AgroGreenPrimary) }
            Button(
                onClick = {
                    val lote = lotes.firstOrNull { it.id == loteId }
                    viewModel.guardar(
                        Gasto(
                            id = existente?.id ?: viewModel.nuevoId(),
                            fecha = fecha, categoria = categoria, producto = producto,
                            cantidad = vCantidad ?: 0.0, unidad = unidad, precioUnitario = vPrecio ?: 0.0,
                            moneda = moneda, tipoCambio = vTc ?: 0.0,
                            loteId = lote?.id, loteNombre = lote?.nombre,
                            proveedor = proveedor.trim(), estadoPago = pago,
                            vencimiento = if (pago == EstadoPago.A_PAGAR) vence else null,
                            alStock = alStock, notas = notas.trim()
                        )
                    )
                    onListo()
                },
                enabled = valido,
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                modifier = Modifier.weight(1.6f).height(52.dp).testTag("btn_guardar_gasto")
            ) { Text("Guardar gasto", fontWeight = FontWeight.SemiBold) }
        }
    }
}


private fun isoAMillis(iso: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
}.getOrNull()

private fun millisAIso(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Selector(valor: String, opciones: List<String>, modifier: Modifier = Modifier, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { abierto = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(valor, modifier = Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onElegir(o); abierto = false }) }
        }
    }
}

@Composable
private fun BotonFecha(texto: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(52.dp)) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp), tint = AgroGreenPrimary)
        Spacer(Modifier.width(6.dp))
        Text(texto, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun Segmentos(opciones: List<String>, elegida: Int, modifier: Modifier = Modifier, onElegir: (Int) -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        opciones.forEachIndexed { i, o ->
            val sel = i == elegida
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (sel) AgroGreenPrimary else Color.Transparent,
                modifier = Modifier.weight(1f).fillMaxSize().selectable(selected = sel, role = Role.RadioButton, onClick = { onElegir(i) })
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(o, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
