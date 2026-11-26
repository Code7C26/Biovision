package com.example.web.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.AnalisisRelieve
import com.example.utils.CapaMapaEscaneo
import com.example.utils.ClaseVigor
import com.example.utils.ModoEscala
import com.example.utils.ResumenEscaneo
import com.example.utils.ResumenRelieve
import com.example.utils.vra.IndiceVegetacion
import com.example.web.ui.Aviso
import com.example.web.ui.BarraProporciones
import com.example.utils.PosicionRelieve
import androidx.compose.ui.graphics.Color
import com.example.web.ui.Dato
import com.example.web.ui.Insignia
import com.example.web.ui.LocalMarca
import com.example.web.ui.desplazableHorizontal
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.viewmodel.CargaWeb
import com.example.web.viewmodel.EscaneoWebViewModel
import com.example.web.viewmodel.EstadoEscaneoWeb
import com.example.web.viewmodel.VistaEscaneoWeb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.utils.ComparacionEscaneo
import com.example.utils.EscaneoLote
import com.example.utils.FechasIso
import kotlin.math.abs

/**
 * Escaneo Sentinel-2 del lote elegido (lo que la tarjeta TarjetaEscaneoSentinel hace en Android):
 * fecha despejada, índice (NDVI / NDRE / GNDVI) con zonas de vigor, foto del día y relieve.
 * Cada valor dice de dónde sale; nunca se muestran índices inventados: sin sesión o sin imagen se
 * dice por qué.
 */
@Composable
fun ContenidoEscaneo(e: EstadoEscaneoWeb?, sinAcceso: String?, vm: EscaneoWebViewModel, irACuenta: () -> Unit) {
    if (e == null) {
        Text(
            "Elegí un lote de la lista para ver sus imágenes Sentinel-2 de 10 m.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sinAcceso != null) {
            Aviso(sinAcceso)
            if ("Ingresá" in sinAcceso) OutlinedButton(onClick = irACuenta, modifier = Modifier.mano()) { Text("Ingresar") }
            return@Column
        }
        Text(
            if (e.vista == VistaEscaneoWeb.RELIEVE) "Copernicus DEM GLO-30 · 10 m" else "Copernicus Sentinel-2 · 10 m · imágenes reales",
            style = MaterialTheme.typography.labelSmall,
            color = LocalMarca.current.primario
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            VistaEscaneoWeb.entries.forEachIndexed { i, vista ->
                SegmentedButton(
                    selected = e.vista == vista,
                    onClick = { vm.elegirVista(vista) },
                    shape = SegmentedButtonDefaults.itemShape(i, VistaEscaneoWeb.entries.size),
                    modifier = Modifier.mano(),
                    icon = {},
                    label = { Text(vista.nombre, fontSize = 12.sp, maxLines = 1) }
                )
            }
        }
        if (e.vista != VistaEscaneoWeb.RELIEVE) Fechas(e, vm)
        when (e.vista) {
            VistaEscaneoWeb.INDICE -> VistaIndice(e, vm)
            VistaEscaneoWeb.FOTO -> VistaFoto(e, vm)
            VistaEscaneoWeb.RELIEVE -> VistaRelieve(e, vm)
        }
    }
}

@Composable
private fun Fechas(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    when (val escenas = e.escenas) {
        CargaWeb.Cargando -> Cargando("Buscando fechas sin nubes…")
        is CargaWeb.Fallo -> Fallo(escenas.mensaje, vm::reintentar)
        is CargaWeb.Lista -> {
            val utiles = e.escenasUtiles
            if (utiles.isEmpty()) {
                Aviso("No hay fechas con el lote despejado en los últimos ${EscaneoWebViewModel.DIAS_HISTORIAL} días.")
                return
            }
            val dudosas = e.fechasDudosas
            Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                utiles.forEach { escena ->
                    FilterChip(
                        selected = escena.fecha == e.fecha,
                        onClick = { vm.elegirFecha(escena.fecha) },
                        label = {
                            Text(
                                CapaMapaEscaneo.fechaLarga(escena.fecha).take(5) + if (escena.fecha in dudosas) " · neblina?" else "",
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.mano()
                    )
                }
            }
            if (e.fecha in dudosas) {
                Aviso(
                    "Posible neblina o nubes finas ese día: el índice cae respecto de las fechas vecinas. " +
                        "Compará con otra fecha antes de sacar conclusiones.",
                    color = LocalMarca.current.alerta
                )
            }
        }
    }
}

@Composable
private fun VistaIndice(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IndiceVegetacion.entries.forEach { indice ->
            FilterChip(selected = e.indice == indice, onClick = { vm.elegirIndice(indice) }, label = { Text(indice.nombre) }, modifier = Modifier.mano())
        }
    }
    Text(e.indice.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (e.comparacion == null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ModoEscala.entries.forEachIndexed { i, modo ->
            SegmentedButton(
                selected = e.modo == modo,
                onClick = { vm.elegirModo(modo) },
                shape = SegmentedButtonDefaults.itemShape(i, ModoEscala.entries.size),
                modifier = Modifier.mano(),
                icon = {},
                label = { Text(modo.nombre, fontSize = 12.sp, maxLines = 1) }
            )
        }
    }
    if (e.comparacion == null) Text(e.modo.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (val imagen = e.imagen) {
        null -> {}
        CargaWeb.Cargando -> Cargando("Bajando la imagen de 10 m…")
        is CargaWeb.Fallo -> Fallo(imagen.mensaje) { e.fecha?.let(vm::elegirFecha) }
        is CargaWeb.Lista -> {
            e.comparacion?.let { ResumenComparacion(it) }
                ?: e.resumen?.let { Resumen(it) }
                ?: Aviso("La imagen no tiene píxeles despejados dentro del lote.")
            Comparar(e, vm)
        }
    }
    Serie(e, vm)
}

/** Elegir otra fecha despejada para ver el cambio píxel a píxel (como en Android). */
@Composable
private fun Comparar(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    val otras = e.escenasUtiles.filter { it.fecha != e.fecha }
    if (otras.isEmpty()) return
    HorizontalDivider()
    Text("Comparar con otra fecha", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = e.fechaComparar == null, onClick = { vm.compararCon(null) }, label = { Text("Sin comparar") }, modifier = Modifier.mano())
        otras.forEach { escena ->
            FilterChip(
                selected = e.fechaComparar == escena.fecha,
                onClick = { vm.compararCon(escena.fecha) },
                label = { Text(CapaMapaEscaneo.fechaLarga(escena.fecha).take(5), maxLines = 1) },
                modifier = Modifier.mano()
            )
        }
    }
    when (val img = e.imagenComparar) {
        CargaWeb.Cargando -> Cargando("Bajando la imagen para comparar…")
        is CargaWeb.Fallo -> Fallo(img.mensaje) { vm.compararCon(e.fechaComparar) }
        else -> {}
    }
}

@Composable
private fun ResumenComparacion(c: ComparacionEscaneo) {
    val marca = LocalMarca.current
    HorizontalDivider()
    val signo = if (c.cambioMedio > 0) "+" else ""
    Text(
        "De ${CapaMapaEscaneo.fechaLarga(c.fechaAnterior).take(5)} a ${CapaMapaEscaneo.fechaLarga(c.fechaNueva).take(5)}: " +
            "${c.indice.nombre} $signo${numero(c.cambioMedio, 2)} en promedio.",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    val total = c.hectareasSubio + c.hectareasBajo + c.hectareasEstable
    if (total > 0) {
        BarraProporciones(
            listOf(
                c.hectareasBajo / total * 100 to marca.negativo,
                c.hectareasEstable / total * 100 to MaterialTheme.colorScheme.outline,
                c.hectareasSubio / total * 100 to marca.positivo
            )
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Insignia("Bajó ${numero(c.hectareasBajo, 1)} ha", marca.negativo)
        Insignia("Igual ${numero(c.hectareasEstable, 1)} ha", MaterialTheme.colorScheme.outline)
        Insignia("Subió ${numero(c.hectareasSubio, 1)} ha", marca.positivo)
    }
    Text(
        "En el mapa: rojo bajó, verde subió (escala ±${numero(CapaMapaEscaneo.ESCALA_CAMBIO, 1)}). " +
            "\"Igual\" es un cambio menor a ${numero(EscaneoLote.UMBRAL_CAMBIO, 2)}. Tocá el lote para ver el cambio de un punto.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Promedio del índice en el lote en cada fecha despejada; tocar un punto abre esa fecha. */
@Composable
private fun Serie(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    val puntos = e.escenasUtiles.mapNotNull { escena -> escena.valor(e.indice)?.let { escena.fecha to it } }.sortedBy { it.first }
    if (puntos.size < 2) return
    HorizontalDivider()
    Text("Evolución del ${e.indice.nombre} promedio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    val primera = puntos.first().first
    val totalDias = FechasIso.diasEntre(primera, puntos.last().first).coerceAtLeast(1)
    val minimo = (puntos.minOf { it.second } - 0.05).coerceAtLeast(-0.2)
    val maximo = (puntos.maxOf { it.second } + 0.05).coerceAtMost(1.0)
    val colorLinea = LocalMarca.current.primario
    val colorSeleccion = MaterialTheme.colorScheme.onSurface
    val colorGuia = MaterialTheme.colorScheme.outlineVariant
    val colorDudosa = MaterialTheme.colorScheme.outline
    // Las fechas con posible neblina quedan fuera de la línea: no son una caída real del cultivo
    val dudosas = e.fechasDudosas
    val linea = puntos.filter { it.first !in dudosas }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .mano()
            .semantics { contentDescription = "Serie del ${e.indice.nombre} en ${puntos.size} fechas; tocá un punto para abrir esa fecha" }
            .pointerInput(puntos) {
                detectTapGestures { toque ->
                    val x = toque.x / size.width
                    puntos.minByOrNull { abs(FechasIso.diasEntre(primera, it.first).toFloat() / totalDias - x) }
                        ?.let { vm.elegirFecha(it.first) }
                }
            }
    ) {
        fun pos(fecha: String, valor: Double) = Offset(
            x = FechasIso.diasEntre(primera, fecha).toFloat() / totalDias * (size.width - 16f) + 8f,
            y = ((maximo - valor) / (maximo - minimo)).toFloat() * (size.height - 16f) + 8f
        )
        drawLine(colorGuia, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
        for (i in 1 until linea.size) {
            drawLine(colorLinea, pos(linea[i - 1].first, linea[i - 1].second), pos(linea[i].first, linea[i].second), strokeWidth = 3f)
        }
        puntos.forEach { (fecha, valor) ->
            val p = pos(fecha, valor)
            if (fecha in dudosas) drawCircle(colorDudosa, radius = 4.5f, center = p, style = Stroke(width = 2f))
            else drawCircle(colorLinea, radius = 5f, center = p)
            if (fecha == e.fecha) drawCircle(colorSeleccion, radius = 9f, center = p, style = Stroke(width = 2.5f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val estilo = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(CapaMapaEscaneo.fechaLarga(puntos.first().first).take(5), style = estilo, color = color)
        Text("${e.indice.nombre} ${numero(minimo, 2)} – ${numero(maximo, 2)}", style = estilo, color = color)
        Text(CapaMapaEscaneo.fechaLarga(puntos.last().first).take(5), style = estilo, color = color)
    }
    if (dudosas.isNotEmpty()) {
        Text(
            "○ Posible neblina (${dudosas.size} ${if (dudosas.size == 1) "fecha" else "fechas"}): fuera de la línea.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Resumen(r: ResumenEscaneo) {
    val marca = LocalMarca.current
    HorizontalDivider()
    Dato("${r.indice.nombre} típico del lote (mediana)", numero(r.mediana, 2))
    Dato("Promedio", numero(r.media, 2))
    Dato("Superficie con dato", "${numero(r.hectareasConDato, 1)} ha")
    r.cobertura?.let { Dato("Cobertura", it.nombre) }
    Text("Zonas de vigor (respecto del típico del lote)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BarraProporciones(r.zonas.map { z ->
        z.porcentaje to when (z.clase) {
            ClaseVigor.BAJO -> marca.negativo
            ClaseVigor.MEDIO -> marca.alerta
            ClaseVigor.ALTO -> marca.positivo
        }
    })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        r.zonas.forEach { z ->
            val color = when (z.clase) {
                ClaseVigor.BAJO -> marca.negativo
                ClaseVigor.MEDIO -> marca.alerta
                ClaseVigor.ALTO -> marca.positivo
            }
            Insignia("${z.clase.nombre} ${numero(z.hectareas, 1)} ha · ${numero(z.porcentaje)} %", color)
        }
    }
    Text(r.diagnostico, style = MaterialTheme.typography.bodySmall)
    Text(
        "Tocá el lote en el mapa para ver el valor de un punto.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun VistaFoto(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    when (val foto = e.foto) {
        null -> {}
        CargaWeb.Cargando -> Cargando("Bajando la foto en color real…")
        is CargaWeb.Fallo -> Fallo(foto.mensaje) { e.fecha?.let(vm::elegirFecha) }
        is CargaWeb.Lista -> Text(
            "Color real del ${CapaMapaEscaneo.fechaLarga(foto.valor.fecha)}, con un margen alrededor del lote. " +
                "Sirve para ver encharcamientos, cosecha o nubes que el índice no explica.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VistaRelieve(e: EstadoEscaneoWeb, vm: EscaneoWebViewModel) {
    when (val relieve = e.relieve) {
        null -> {}
        CargaWeb.Cargando -> Cargando("Bajando el modelo de elevación…")
        is CargaWeb.Fallo -> Fallo(relieve.mensaje) { vm.elegirVista(VistaEscaneoWeb.RELIEVE) }
        is CargaWeb.Lista -> e.resumenRelieve?.let { ResumenDelRelieve(it) } ?: Aviso("El relieve no tiene datos dentro del lote.")
    }
}

@Composable
private fun ResumenDelRelieve(r: ResumenRelieve) {
    HorizontalDivider()
    Dato("Desnivel (sin el 2 % de cada punta)", "${numero(r.desnivel, 1)} m")
    Dato("Pendiente media", "${numero(r.pendienteMediaPct, 1)} %")
    Dato("Altura típica", "${numero(r.mediana)} m s. n. m.")
    // Mismos colores que la rampa del relieve en el mapa: azul = bajo, marrón = loma
    val colores = mapOf(PosicionRelieve.BAJO to Color(0xFF2563EB), PosicionRelieve.MEDIA_LOMA to Color(0xFFD6C29A), PosicionRelieve.LOMA to Color(0xFF8B5A2B))
    BarraProporciones(r.clases.map { it.porcentaje to (colores[it.posicion] ?: Color.Gray) })
    r.clases.forEach { c -> Dato(c.posicion.nombre, "${numero(c.hectareas, 1)} ha · ${numero(c.porcentaje)} %", colores[c.posicion] ?: Color.Unspecified) }
    Text(r.diagnostico, style = MaterialTheme.typography.bodySmall)
    Text(
        "Es un modelo de superficie: montes y galpones aparecen como lomas.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Aviso que exige la licencia del GLO-30 donde se muestra el dato modificado
    Text(AnalisisRelieve.AVISO_DEM, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Cargando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Fallo(mensaje: String, alReintentar: () -> Unit) {
    Aviso(mensaje, color = LocalMarca.current.negativo)
    TextButton(onClick = alReintentar, modifier = Modifier.mano().padding(0.dp)) {
        Text("Reintentar", fontWeight = FontWeight.SemiBold)
    }
}
