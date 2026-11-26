package com.example.web.pantallas

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.agenda.Agenda
import com.example.data.agenda.CatalogoProductos
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.CostoLote
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.IconoProducto
import com.example.data.agenda.InsumoEnStock
import com.example.data.agenda.LoteAgenda
import com.example.data.agenda.Moneda
import com.example.data.agenda.ProductoSugerido
import com.example.data.agenda.TrazosIconos
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import com.example.utils.FechasIso
import com.example.web.plataforma.Navegador
import com.example.web.ui.BotonIcono
import com.example.web.ui.ClaseAncho
import com.example.web.ui.Encabezado
import com.example.web.ui.EspacioPanel
import com.example.web.ui.IconoEnCirculo
import com.example.web.ui.IconoGrano
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.dolares
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.ui.pesos
import com.example.web.viewmodel.AgendaWebViewModel
import com.example.web.viewmodel.EstadoAgendaWeb
import com.example.web.viewmodel.FiltroAgendaWeb
import com.example.web.viewmodel.GranoWeb

/** Color de cada categoría (los mismos que en Android). */
private fun colorCategoria(c: CategoriaGasto): Color = when (c) {
    CategoriaGasto.SEMILLA -> Color(0xFF047857)
    CategoriaGasto.FERTILIZANTE -> Color(0xFF0284C7)
    CategoriaGasto.FITOSANITARIO -> Color(0xFFD97706)
    CategoriaGasto.LABOR -> Color(0xFF7C3AED)
    CategoriaGasto.COMBUSTIBLE -> Color(0xFF475569)
    CategoriaGasto.OTRO -> Color(0xFF94A3B8)
}

private fun cantidadTexto(v: Double) = if (v == kotlin.math.floor(v)) numero(v) else numero(v, 2)

private enum class VistaAgenda(val nombre: String) { GASTOS("Gastos"), INSUMOS("Insumos"), LOTES("Por lote") }

/**
 * La Agenda de la campaña en la web: gastos con lo que queda por pagar, stock de insumos y costo por
 * lote. Es la misma Agenda que la app Android (se copia a la cuenta). En el teléfono va en pestañas; en
 * pantallas anchas, los gastos a la izquierda y los insumos y lotes a la derecha.
 */
@Composable
fun PantallaAgenda(vm: AgendaWebViewModel, nuevoAlAbrir: Boolean = false, onNuevoAbierto: () -> Unit = {}) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<String?>(null) }   // id del gasto, o "nuevo"
    // "Anotar gasto" desde el Inicio: abre el formulario de un gasto nuevo una sola vez
    LaunchedEffect(nuevoAlAbrir, estado.disponible) {
        if (nuevoAlAbrir) {
            if (estado.disponible) editando = "nuevo"
            onNuevoAbierto()
        }
    }
    var usando by remember { mutableStateOf<InsumoEnStock?>(null) }
    var menuCampana by remember { mutableStateOf(false) }
    var vista by remember { mutableStateOf(VistaAgenda.GASTOS) }
    val compacta = LocalClaseAncho.current == ClaseAncho.COMPACTA

    editando?.let { id ->
        DialogoGasto(vm, estado, if (id == "nuevo") null else vm.gasto(id)) { editando = null }
    }
    usando?.let { insumo ->
        DialogoUso(insumo, estado.lotes, onCancelar = { usando = null }) { cantidad, lote ->
            vm.registrarAplicacion(insumo, cantidad, lote)
            usando = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Agenda",
            chips = listOfNotNull(
                "Campaña ${estado.campana}",
                dolares(estado.resumen.totalUsd).takeIf { estado.resumen.totalUsd > 0 },
                "Sin subir".takeIf { estado.pendiente }
            )
        ) {
            Box {
                BotonIcono(Icons.Filled.DateRange, "Elegir campaña") { menuCampana = true }
                DropdownMenu(expanded = menuCampana, onDismissRequest = { menuCampana = false }) {
                    estado.campanas.forEach { c ->
                        DropdownMenuItem(text = { Text("Campaña $c") }, onClick = { vm.elegirCampana(c); menuCampana = false }, modifier = Modifier.mano())
                    }
                }
            }
            BotonIcono(IconosGranex.Subir, "Descargar planilla (CSV)", estado.dias.isNotEmpty()) { descargarPlanilla(estado) }
            BotonIcono(Icons.Filled.Refresh, "Sincronizar con tu cuenta", vm::sincronizarAhora)
        }
        if (!estado.disponible) {
            Box(Modifier.padding(EspacioPanel)) {
                Panel("Agenda") { Text("El navegador no deja guardar datos en esta página (modo privado estricto). Abrí GRANEX en una ventana normal para usar la Agenda.") }
            }
            return@Column
        }
        Box(Modifier.weight(1f)) {
            if (compacta) {
                LazyColumn(
                    contentPadding = PaddingValues(start = EspacioPanel, end = EspacioPanel, top = EspacioPanel, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VistaAgenda.entries.forEach { v ->
                                FilterChip(selected = vista == v, onClick = { vista = v }, label = { Text(v.nombre) }, modifier = Modifier.mano())
                            }
                        }
                    }
                    when (vista) {
                        VistaAgenda.GASTOS -> {
                            item { PanelResumen(estado) }
                            item { Filtros(estado, vm::filtrar) }
                            itemsGastos(estado) { editando = it }
                        }
                        VistaAgenda.INSUMOS -> item { PanelInsumos(estado, vm) { usando = it } }
                        VistaAgenda.LOTES -> item { PanelLotes(estado) }
                    }
                    item { NotaCuenta(estado) }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = EspacioPanel), horizontalArrangement = Arrangement.spacedBy(EspacioPanel)) {
                    LazyColumn(
                        Modifier.weight(1.3f),
                        contentPadding = PaddingValues(top = EspacioPanel, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { PanelResumen(estado) }
                        item { Filtros(estado, vm::filtrar) }
                        itemsGastos(estado) { editando = it }
                        item { NotaCuenta(estado) }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = EspacioPanel, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PanelInsumos(estado, vm) { usando = it }
                        PanelLotes(estado)
                    }
                }
            }
            Button(
                onClick = { editando = "nuevo" },
                colors = ButtonDefaults.buttonColors(containerColor = LocalMarca.current.primario),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(EspacioPanel).heightIn(min = 52.dp).mano()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Anotar gasto", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsGastos(estado: EstadoAgendaWeb, onAbrir: (String) -> Unit) {
    if (estado.cargado && estado.dias.isEmpty()) {
        item {
            Panel("Gastos") {
                Text(
                    if (estado.filtro.activo) "No hay gastos con este filtro."
                    else "Todavía no anotaste gastos de la campaña ${estado.campana}. Tocá \"Anotar gasto\": semillas, fertilizantes, labores, gasoil y todo lo que pagues."
                )
            }
        }
    }
    estado.dias.forEach { dia ->
        item(key = "dia_${dia.fecha}") {
            Text(
                Agenda.fechaCorta(dia.fecha) + " · " + dia.fecha.take(4),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        items(dia.gastos, key = { it.id }) { g -> FilaGasto(g) { onAbrir(g.id) } }
    }
}

@Composable
private fun NotaCuenta(estado: EstadoAgendaWeb) {
    Text(
        if (estado.pendiente) "Hay cambios sin subir a tu cuenta: se suben solos apenas haya conexión."
        else "Se guarda en tu cuenta: la ves igual en la app Android.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
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
private fun PanelResumen(estado: EstadoAgendaWeb) {
    val r = estado.resumen
    Panel("Gastado en la campaña ${estado.campana}") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(dolares(r.totalUsd), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (r.totalPesos > 0) Text("≈ ${pesos(r.totalPesos)} al dólar de cada día", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            estado.usdPorHa?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${dolares(it)}/ha", fontWeight = FontWeight.Bold, color = LocalMarca.current.primario, fontSize = 17.sp)
                    Text("${numero(estado.hectareas)} ha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        BarraCategorias(r.porCategoria)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.porCategoria.forEach { (c, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colorCategoria(c)))
                    Text("${c.nombre} ", style = MaterialTheme.typography.bodySmall)
                    Text(numero(v), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (r.facturasAPagar > 0) {
            Text(
                "A pagar ${dolares(r.aPagarUsd)} en ${r.facturasAPagar} " + (if (r.facturasAPagar == 1) "factura" else "facturas") +
                    (r.proximoVencimiento?.let { " · vence el ${Agenda.fechaCorta(it)}" } ?: ""),
                color = LocalMarca.current.alerta, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Filtros(estado: EstadoAgendaWeb, onFiltro: (FiltroAgendaWeb) -> Unit) {
    val f = estado.filtro
    var menuCategoria by remember { mutableStateOf(false) }
    var menuLote by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = !f.activo, onClick = { onFiltro(FiltroAgendaWeb()) }, label = { Text("Todos") }, modifier = Modifier.mano())
        Box {
            FilterChip(
                selected = f.categoria != null, onClick = { menuCategoria = true }, modifier = Modifier.mano(),
                label = { Text(f.categoria?.nombre ?: "Categoría") }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
            )
            DropdownMenu(expanded = menuCategoria, onDismissRequest = { menuCategoria = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { onFiltro(f.copy(categoria = null)); menuCategoria = false })
                CategoriaGasto.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.nombre) }, onClick = { onFiltro(f.copy(categoria = c)); menuCategoria = false })
                }
            }
        }
        Box {
            val nombre = when (f.loteId) {
                null -> "Lote"
                FiltroAgendaWeb.GENERAL -> "General"
                else -> estado.lotes.firstOrNull { it.id == f.loteId }?.nombre ?: "Lote"
            }
            FilterChip(
                selected = f.loteId != null, onClick = { menuLote = true }, modifier = Modifier.mano(),
                label = { Text(nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
            )
            DropdownMenu(expanded = menuLote, onDismissRequest = { menuLote = false }) {
                DropdownMenuItem(text = { Text("Todos los lotes") }, onClick = { onFiltro(f.copy(loteId = null)); menuLote = false })
                estado.lotes.forEach { l -> DropdownMenuItem(text = { Text(l.nombre) }, onClick = { onFiltro(f.copy(loteId = l.id)); menuLote = false }) }
                DropdownMenuItem(text = { Text("General (sin lote)") }, onClick = { onFiltro(f.copy(loteId = FiltroAgendaWeb.GENERAL)); menuLote = false })
            }
        }
        FilterChip(selected = f.soloAPagar, onClick = { onFiltro(f.copy(soloAPagar = !f.soloAPagar)) }, label = { Text("A pagar") }, modifier = Modifier.mano())
    }
}

@Composable
private fun FilaGasto(g: Gasto, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).mano(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoProducto(iconoDe(g), g.categoria, 40)
            Column(Modifier.weight(1f)) {
                Text(g.producto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "${cantidadTexto(g.cantidad)} ${g.unidad}",
                        g.loteNombre ?: "general",
                        g.proveedor.takeIf { it.isNotBlank() },
                        if (g.moneda == Moneda.ARS) pesos(g.total) else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dolares(g.totalUsd), fontWeight = FontWeight.Bold)
                val (texto, color) = when (g.estadoPago) {
                    EstadoPago.PAGADO -> "Pagado" to LocalMarca.current.positivo
                    EstadoPago.CANJE -> "Canje" to MaterialTheme.colorScheme.onSurfaceVariant
                    EstadoPago.A_PAGAR -> (g.vencimiento?.let { "Vence ${Agenda.fechaCorta(it)}" } ?: "A pagar") to LocalMarca.current.alerta
                }
                Text(texto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

/** El ícono del catálogo si el producto está en él; si no, el de la categoría. */
private fun iconoDe(g: Gasto): IconoProducto =
    CatalogoProductos.porCategoria[g.categoria].orEmpty()
        .firstOrNull { CatalogoProductos.normalizar(it.nombre) == CatalogoProductos.normalizar(g.producto) }?.icono
        ?: when (g.categoria) {
            CategoriaGasto.SEMILLA -> IconoProducto.SEMILLA
            CategoriaGasto.FERTILIZANTE -> IconoProducto.FERTILIZANTE
            CategoriaGasto.FITOSANITARIO -> IconoProducto.HERBICIDA
            CategoriaGasto.LABOR -> IconoProducto.SEMBRADORA
            CategoriaGasto.COMBUSTIBLE -> IconoProducto.COMBUSTIBLE
            CategoriaGasto.OTRO -> IconoProducto.OTRO
        }

@Composable
private fun PanelInsumos(estado: EstadoAgendaWeb, vm: AgendaWebViewModel, onUsar: (InsumoEnStock) -> Unit) {
    Panel("Stock de insumos", subtitulo = "Lo comprado menos lo que ya usaste") {
        if (estado.stock.isEmpty()) {
            Text("Acá aparece lo que compraste de semillas, fertilizantes, fitosanitarios y combustible.", style = MaterialTheme.typography.bodyMedium)
        }
        estado.stock.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorCategoria(i.categoria)))
                    Column(Modifier.weight(1f)) {
                        Text(i.producto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Comprado ${cantidadTexto(i.comprado)} · usado ${cantidadTexto(i.aplicado)} · ${dolares(i.usdPorUnidad, if (i.usdPorUnidad < 100) 2 else 0)} por ${i.unidad.removeSuffix("s")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${cantidadTexto(i.restante)} ${i.unidad}", fontWeight = FontWeight.Bold,
                        color = if (i.poco) LocalMarca.current.alerta else Color.Unspecified
                    )
                    TextButton(onClick = { onUsar(i) }, modifier = Modifier.mano()) { Text("Registrar uso") }
                }
                LinearProgressIndicator(
                    progress = { i.fraccionUsada.toFloat() }, color = colorCategoria(i.categoria),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                if (i.poco) Text("Queda poco: menos del 15 % de lo comprado.", style = MaterialTheme.typography.bodySmall, color = LocalMarca.current.alerta)
            }
        }
        if (estado.aplicaciones.isNotEmpty()) {
            HorizontalDivider()
            Text("Últimos usos", fontWeight = FontWeight.SemiBold)
            estado.aplicaciones.take(15).forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Agenda.fechaCorta(a.fecha)} · ${a.producto} · ${cantidadTexto(a.cantidad)} ${a.unidad} · ${a.loteNombre ?: "sin lote"}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                    )
                    BotonIcono(Icons.Filled.Delete, "Quitar este uso") { vm.borrarAplicacion(a.id) }
                }
            }
        }
    }
}

@Composable
private fun PanelLotes(estado: EstadoAgendaWeb) {
    Panel("Costo por lote", subtitulo = "Lo gastado en cada lote por hectárea") {
        if (estado.lotes.isEmpty()) Text("Cargá tus lotes en Campo para ver cuánto llevás gastado por hectárea en cada uno.")
        estado.costos.lotes.forEach { c -> FilaCostoLote(c, estado.costos.generalesPorHa) }
        if (estado.costos.generalesUsd > 0) {
            HorizontalDivider()
            Text(
                "Gastos generales (sin lote): ${dolares(estado.costos.generalesUsd)}, se reparten a ${dolares(estado.costos.generalesPorHa, 1)}/ha",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilaCostoLote(c: CostoLote, generalesPorHa: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.lote.nombre, fontWeight = FontWeight.Bold)
                Text("${numero(c.lote.hectareas, 1)} ha · ${dolares(c.totalUsd)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${dolares(c.usdPorHa)}/ha", fontWeight = FontWeight.Bold, color = LocalMarca.current.primario)
                if (generalesPorHa > 0) Text("con generales ${dolares(c.usdPorHa + generalesPorHa)}/ha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BarraCategorias(c.porCategoria, alto = 8)
    }
}

// ---------- Ícono de un producto ----------

@Composable
private fun IconoProducto(icono: IconoProducto, categoria: CategoriaGasto, tamano: Int = 32) {
    val grano = when (icono) {
        IconoProducto.SOJA -> GranoWeb.SOJA
        IconoProducto.MAIZ -> GranoWeb.MAIZ
        IconoProducto.TRIGO -> GranoWeb.TRIGO
        IconoProducto.GIRASOL -> GranoWeb.GIRASOL
        IconoProducto.SORGO -> GranoWeb.SORGO
        else -> null
    }
    if (grano != null) IconoGrano(grano, tamano) else IconoEnCirculo(vectorProducto(icono), colorCategoria(categoria), tamano)
}

private val dibujosPropios = mutableMapOf<IconoProducto, ImageVector>()

/** La maquinaria de las labores con su dibujo propio (TrazosIconos, el mismo de Android); el resto con IconosGranex. */
private fun vectorProducto(icono: IconoProducto): ImageVector =
    TrazosIconos.de(icono)?.let { trazos ->
        dibujosPropios.getOrPut(icono) {
            ImageVector.Builder(icono.name, 24.dp, 24.dp, 24f, 24f).apply {
                trazos.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
            }.build()
        }
    } ?: vectorDe(icono)

private fun vectorDe(icono: IconoProducto): ImageVector = when (icono) {
    IconoProducto.SOJA, IconoProducto.MAIZ, IconoProducto.TRIGO, IconoProducto.GIRASOL, IconoProducto.SORGO,
    IconoProducto.SEMILLA -> IconosGranex.Brote
    IconoProducto.FERTILIZANTE, IconoProducto.ANALISIS -> IconosGranex.Matraz
    IconoProducto.HERBICIDA -> IconosGranex.Hoja
    IconoProducto.INSECTICIDA -> IconosGranex.Insecto
    IconoProducto.FUNGICIDA, IconoProducto.SEGURO -> IconosGranex.Escudo
    IconoProducto.COADYUVANTE, IconoProducto.PULVERIZACION, IconoProducto.LUBRICANTE -> IconosGranex.Gota
    IconoProducto.SIEMBRA, IconoProducto.COSECHA, IconoProducto.LABRANZA -> IconosGranex.Tractor
    IconoProducto.FLETE -> IconosGranex.Camion
    IconoProducto.COMBUSTIBLE -> IconosGranex.Surtidor
    IconoProducto.SERVICIO -> Icons.Filled.Settings
    IconoProducto.ARRENDAMIENTO -> Icons.Filled.Home
    IconoProducto.MANO_DE_OBRA -> Icons.Filled.Person
    IconoProducto.REPARACION -> Icons.Filled.Build
    IconoProducto.OTRO -> Icons.Filled.ShoppingCart
    IconoProducto.USADO -> Icons.Filled.Refresh
    IconoProducto.PULVERIZADORA, IconoProducto.AVION, IconoProducto.COSECHADORA, IconoProducto.EMBOLSADORA,
    IconoProducto.EXTRACTORA, IconoProducto.SEMBRADORA, IconoProducto.FERTILIZADORA, IconoProducto.RASTRA,
    IconoProducto.ROLO, IconoProducto.CAMION, IconoProducto.MONITOREO -> IconosGranex.Tractor
}

// ---------- Formulario ----------

/**
 * Campo del producto: se escribe libre o se elige de la lista de la categoría (con su ícono). La lista
 * se abre al tocar el campo vacío o la flecha y se filtra mientras se escribe, sin sacar el foco.
 */
@Composable
private fun CampoProducto(
    valor: String,
    categoria: CategoriaGasto,
    usados: List<Pair<CategoriaGasto, String>>,
    onCambio: (String) -> Unit,
    onElegir: (ProductoSugerido) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = remember(categoria, valor, usados) { CatalogoProductos.sugerencias(categoria, valor, usados) }
    val delCatalogo = remember(categoria, valor) {
        CatalogoProductos.porCategoria[categoria].orEmpty().firstOrNull { CatalogoProductos.normalizar(it.nombre) == CatalogoProductos.normalizar(valor) }
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = { onCambio(it); abierto = true },
            label = { Text(if (categoria == CategoriaGasto.LABOR) "Labor" else "Producto") },
            placeholder = { Text("Escribí o elegí de la lista") },
            leadingIcon = delCatalogo?.let { p -> { IconoProducto(p.icono, categoria, 28) } },
            trailingIcon = {
                IconButton(onClick = { abierto = !abierto }, modifier = Modifier.mano()) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = if (abierto) "Cerrar la lista" else "Elegir de la lista")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused && valor.isBlank()) abierto = true }
        )
        DropdownMenu(
            expanded = abierto && opciones.isNotEmpty(),
            onDismissRequest = { abierto = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 320.dp).widthIn(min = 280.dp)
        ) {
            opciones.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(o.nombre)
                            if (o.icono == IconoProducto.USADO) Text("Cargado antes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { IconoProducto(o.icono, categoria, 28) },
                    onClick = { onElegir(o); abierto = false },
                    modifier = Modifier.mano()
                )
            }
        }
    }
}

@Composable
private fun <T> Selector(etiqueta: String, valor: String, opciones: List<T>, texto: (T) -> String, modifier: Modifier = Modifier, onElegir: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { abierto = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).mano()) {
                Text(valor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { o -> DropdownMenuItem(text = { Text(texto(o)) }, onClick = { onElegir(o); abierto = false }, modifier = Modifier.mano()) }
            }
        }
    }
}

/** yyyy-MM-dd ↔ milisegundos UTC (lo que usa el selector de fecha). */
private fun isoAMillis(iso: String): Long? = runCatching { FechasIso.diasEntre("1970-01-01", iso).toLong() * 86_400_000L }.getOrNull()
private fun millisAIso(ms: Long): String = FechasIso.deArgentina(ms + 3 * 3_600_000L)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DialogoGasto(vm: AgendaWebViewModel, estado: EstadoAgendaWeb, existente: Gasto?, onCerrar: () -> Unit) {
    val dolarHoy by vm.tipoCambio.collectAsStateWithLifecycle()
    fun texto(v: Double?) = v?.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString().replace('.', ',') } ?: ""
    var categoria by remember { mutableStateOf(existente?.categoria ?: CategoriaGasto.SEMILLA) }
    var producto by remember { mutableStateOf(existente?.producto ?: "") }
    var cantidad by remember { mutableStateOf(texto(existente?.cantidad)) }
    var unidad by remember { mutableStateOf(existente?.unidad ?: CategoriaGasto.SEMILLA.unidadSugerida) }
    var unidadTocada by remember { mutableStateOf(existente != null) }
    var precio by remember { mutableStateOf(texto(existente?.precioUnitario)) }
    var moneda by remember { mutableStateOf(existente?.moneda ?: Moneda.USD) }
    var tipoCambio by remember { mutableStateOf(texto(existente?.tipoCambio?.takeIf { it > 0 })) }
    var loteId by remember { mutableStateOf(existente?.loteId) }
    var fecha by remember { mutableStateOf(existente?.fecha ?: vm.fechaDeHoy()) }
    var proveedor by remember { mutableStateOf(existente?.proveedor ?: "") }
    var pago by remember { mutableStateOf(existente?.estadoPago ?: EstadoPago.PAGADO) }
    var vence by remember { mutableStateOf(existente?.vencimiento) }
    var alStock by remember { mutableStateOf(existente?.alStock ?: true) }
    var stockTocado by remember { mutableStateOf(existente != null) }
    var notas by remember { mutableStateOf(existente?.notas ?: "") }
    var eligiendoFecha by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf(false) }

    LaunchedEffect(dolarHoy) { if (tipoCambio.isBlank()) dolarHoy?.let { tipoCambio = texto(it) } }
    val lotes = estado.lotes
    val vCantidad = Agenda.leerNumero(cantidad)
    val vPrecio = Agenda.leerNumero(precio)
    val vTc = Agenda.leerNumero(tipoCambio)
    val total = if (vCantidad != null && vPrecio != null) vCantidad * vPrecio else null
    val totalUsd = total?.let { if (moneda == Moneda.USD) it else vTc?.takeIf { tc -> tc > 0 }?.let { tc -> it / tc } }
    val valido = producto.isNotBlank() && vCantidad != null && vCantidad > 0 && vPrecio != null && vPrecio > 0 &&
        (moneda == Moneda.USD || (vTc != null && vTc > 0))

    if (confirmarBorrar && existente != null) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            title = { Text("¿Borrar este gasto?") },
            text = { Text("${existente.producto} · ${dolares(existente.totalUsd)}. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { vm.borrar(existente.id); onCerrar() }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } }
        )
    }
    eligiendoFecha?.let { campo ->
        val estadoFecha = rememberDatePickerState(initialSelectedDateMillis = isoAMillis(if (campo == "vence") vence ?: fecha else fecha))
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

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 560.dp).padding(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (existente == null) "Anotar gasto" else "Editar gasto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (existente != null) BotonIcono(Icons.Filled.Delete, "Borrar gasto") { confirmarBorrar = true }
                }
                Text("Categoría", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoriaGasto.entries.forEach { c ->
                        val elegida = c == categoria
                        Surface(
                            onClick = {
                                categoria = c
                                if (!stockTocado) alStock = c.alStock
                                if (!unidadTocada) unidad = c.unidadSugerida
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (elegida) colorCategoria(c).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) colorCategoria(c) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.mano()
                        ) {
                            Text(c.nombre, fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                CampoProducto(producto, categoria, estado.productosUsados, onCambio = { producto = it }) { opcion ->
                    producto = opcion.nombre
                    if (!unidadTocada && opcion.unidad.isNotEmpty()) unidad = opcion.unidad
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = cantidad, onValueChange = { cantidad = it }, label = { Text("Cantidad") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                    Selector("Unidad", unidad, Agenda.UNIDADES, { it }, Modifier.weight(1f)) { unidad = it; unidadTocada = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = precio, onValueChange = { precio = it }, label = { Text("Precio por ${unidad.removeSuffix("s")}") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                    Selector("Moneda", moneda.simbolo, Moneda.entries, { it.simbolo }, Modifier.weight(1f)) { moneda = it }
                }
                OutlinedTextField(
                    value = tipoCambio, onValueChange = { tipoCambio = it }, singleLine = true,
                    label = { Text("Dólar del día ($ por US$)") },
                    supportingText = { Text(if (moneda == Moneda.ARS) "Para pasar el gasto a dólares" else "Para mostrar el gasto en pesos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.labelMedium)
                        Text(totalUsd?.let { dolares(it) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (total != null && moneda == Moneda.ARS) Text(pesos(total), style = MaterialTheme.typography.bodySmall)
                    if (total != null && moneda == Moneda.USD && vTc != null && vTc > 0) Text("≈ ${pesos(total * vTc)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    val opcionesLote: List<LoteAgenda?> = lotes + null
                    Selector(
                        "Lote", lotes.firstOrNull { it.id == loteId }?.nombre ?: "General (sin lote)", opcionesLote,
                        { it?.let { l -> "${l.nombre} (${numero(l.hectareas, 1)} ha)" } ?: "General (sin lote)" }, Modifier.weight(1.3f)
                    ) { loteId = it?.id }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { eligiendoFecha = "fecha" }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).mano()) {
                            Text(Agenda.fechaCorta(fecha) + " " + fecha.take(4), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                OutlinedTextField(value = proveedor, onValueChange = { proveedor = it }, label = { Text("Proveedor (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Pago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstadoPago.entries.forEach { e ->
                        FilterChip(selected = pago == e, onClick = { pago = e }, label = { Text(e.nombre) }, modifier = Modifier.mano())
                    }
                }
                if (pago == EstadoPago.A_PAGAR) {
                    OutlinedButton(onClick = { eligiendoFecha = "vence" }, shape = RoundedCornerShape(12.dp), modifier = Modifier.mano()) {
                        Text("Vence: " + (vence?.let { Agenda.fechaCorta(it) + " " + it.take(4) } ?: "elegir fecha"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alStock = !alStock; stockTocado = true }.mano()) {
                    Checkbox(checked = alStock, onCheckedChange = { alStock = it; stockTocado = true })
                    Text("Sumar al stock de insumos")
                }
                OutlinedTextField(value = notas, onValueChange = { notas = it }, label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCerrar, modifier = Modifier.weight(1f).heightIn(min = 48.dp).mano()) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val lote = lotes.firstOrNull { it.id == loteId }
                            vm.guardar(
                                Gasto(
                                    id = existente?.id ?: vm.nuevoId(), fecha = fecha, categoria = categoria, producto = producto,
                                    cantidad = vCantidad ?: 0.0, unidad = unidad, precioUnitario = vPrecio ?: 0.0,
                                    moneda = moneda, tipoCambio = vTc ?: 0.0, loteId = lote?.id, loteNombre = lote?.nombre,
                                    proveedor = proveedor.trim(), estadoPago = pago,
                                    vencimiento = if (pago == EstadoPago.A_PAGAR) vence else null,
                                    alStock = alStock, notas = notas.trim()
                                )
                            )
                            onCerrar()
                        },
                        enabled = valido,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalMarca.current.primario),
                        modifier = Modifier.weight(1.5f).heightIn(min = 48.dp).mano()
                    ) { Text("Guardar gasto", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun DialogoUso(insumo: InsumoEnStock, lotes: List<LoteAgenda>, onCancelar: () -> Unit, onGuardar: (Double, LoteAgenda?) -> Unit) {
    var cantidad by remember { mutableStateOf("") }
    var lote by remember { mutableStateOf(lotes.firstOrNull()) }
    val valor = Agenda.leerNumero(cantidad)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Registrar uso de ${insumo.producto}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quedan ${cantidadTexto(insumo.restante)} ${insumo.unidad}.")
                OutlinedTextField(
                    value = cantidad, onValueChange = { cantidad = it }, label = { Text("Cantidad usada (${insumo.unidad})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Selector("Lote", lote?.nombre ?: "Sin lote", lotes + null, { it?.nombre ?: "Sin lote" }) { lote = it }
            }
        },
        confirmButton = {
            Button(onClick = { valor?.let { onGuardar(it, lote) } }, enabled = valor != null && valor > 0, modifier = Modifier.mano()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar, modifier = Modifier.mano()) { Text("Cancelar") } }
    )
}

/** La planilla de la campaña en CSV (separada por ";", se abre en Excel o Google Sheets). */
private fun descargarPlanilla(estado: EstadoAgendaWeb) {
    val filas = buildString {
        appendLine("Fecha;Categoría;Producto;Cantidad;Unidad;Precio unitario;Moneda;Dólar del día;Total US$;Lote;Proveedor;Pago;Vence")
        estado.dias.flatMap { it.gastos }.sortedBy { it.fecha }.forEach { g ->
            appendLine(
                listOf(
                    g.fecha, g.categoria.nombre, g.producto, cantidadTexto(g.cantidad), g.unidad, numero(g.precioUnitario, 2),
                    g.moneda.simbolo, numero(g.tipoCambio, 2), numero(g.totalUsd, 2),
                    g.loteNombre ?: "General", g.proveedor, g.estadoPago.nombre, g.vencimiento ?: ""
                ).joinToString(";") { it.replace(";", ",").replace("\n", " ") }
            )
        }
    }
    // Con BOM, para que Excel lea bien los acentos
    Navegador.descargarArchivo("granex_agenda_${estado.campana.replace('/', '-')}.csv", ("﻿" + filas).encodeToByteArray(), "text/csv")
}
