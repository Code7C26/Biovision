package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.remote.api.ApiKeys
import com.example.data.remote.supabase.DatosEstacion
import com.example.data.remote.supabase.DiaEstacion
import com.example.data.remote.supabase.EstacionMeteoRemota
import com.example.data.remote.supabase.LecturaEstacion
import com.example.data.remote.supabase.PuntoEstacion
import com.example.ui.components.CampoIdentificador
import com.example.ui.components.CampoSecreto
import com.example.ui.components.GrillaAdaptable
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.theme.AgroSkyBlue
import com.example.ui.viewmodel.EstadoAgregarEstacion
import com.example.ui.viewmodel.EstadoDatosEstacion
import com.example.ui.viewmodel.EstadoEstaciones
import com.example.ui.viewmodel.EstacionesViewModel
import com.example.utils.EstacionMeteo
import com.example.utils.MarcaEstacion
import com.example.utils.formatoDecimal
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Tarjeta "Mi estación" en Clima
// ---------------------------------------------------------------------------------------------

/**
 * Datos de la estación meteorológica propia del productor, arriba del pronóstico. Sin estaciones
 * muestra una invitación a conectar una; sin backend no muestra nada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaEstacionPropia(viewModel: EstacionesViewModel, onAdministrar: () -> Unit) {
    val estado by viewModel.estaciones.collectAsStateWithLifecycle()
    val seleccionada by viewModel.seleccionada.collectAsStateWithLifecycle()
    val datos by viewModel.datos.collectAsStateWithLifecycle()

    // Refresco cada 5 min solo con la pantalla visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.mantenerActualizado() }
    }

    when (val e = estado) {
        EstadoEstaciones.SinBackend, EstadoEstaciones.Cargando -> return
        is EstadoEstaciones.Error -> {
            TarjetaBase(Modifier.testTag("estacion_error_lista")) {
                Text(e.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::cargarLista) { Text("Reintentar") }
            }
            return
        }
        is EstadoEstaciones.Lista -> if (e.estaciones.isEmpty()) {
            InvitacionEstacion(onAdministrar)
            return
        }
    }
    val lista = (estado as EstadoEstaciones.Lista).estaciones
    val estacion = lista.firstOrNull { it.id == seleccionada } ?: lista.first()

    TarjetaBase(Modifier.testTag("tarjeta_estacion_propia")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoCirculo(Icons.Default.Sensors, AgroGreenPrimary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Mi estación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    estacion.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = viewModel::actualizar, modifier = Modifier.testTag("btn_actualizar_estacion")) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar estación", tint = AgroGreenPrimary)
            }
            IconButton(onClick = onAdministrar, modifier = Modifier.testTag("btn_administrar_estaciones")) {
                Icon(Icons.Default.Settings, contentDescription = "Administrar estaciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (lista.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                lista.forEach { otra ->
                    FilterChip(
                        selected = otra.id == estacion.id,
                        onClick = { viewModel.seleccionar(otra.id) },
                        label = { Text(otra.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when (val d = datos) {
            EstadoDatosEstacion.Ninguno, EstadoDatosEstacion.Cargando -> Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AgroGreenPrimary) }
            is EstadoDatosEstacion.Error -> {
                AvisoEstacion(d.mensaje)
                TextButton(onClick = viewModel::actualizar) { Text("Reintentar") }
                d.anteriores?.let { CuerpoEstacion(it, estacion, actualizando = false) }
            }
            is EstadoDatosEstacion.Listo -> CuerpoEstacion(d.datos, estacion, d.actualizando)
        }
    }
}

@Composable
private fun TarjetaBase(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) { contenido() }
    }
}

@Composable
private fun IconoCirculo(icono: ImageVector, color: Color, tamano: Int = 36) {
    Box(
        Modifier.size(tamano.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size((tamano * 0.6f).dp))
    }
}

@Composable
private fun InvitacionEstacion(onConectar: () -> Unit) {
    TarjetaBase(Modifier.testTag("invitacion_estacion")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoCirculo(Icons.Default.Sensors, AgroGreenPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("¿Tenés una estación meteorológica en el campo?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Conectá tu Davis, Ecowitt, Pessl u otra y mirá sus datos acá: lluvia, viento y si conviene pulverizar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onConectar, modifier = Modifier.fillMaxWidth().testTag("btn_conectar_estacion")) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Conectar estación")
        }
    }
}

@Composable
private fun AvisoEstacion(texto: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

private fun mm(v: Double) = formatoDecimal(v, 1) + " mm"
private fun grados(v: Double) = formatoDecimal(v, 1) + " °C"

@Composable
private fun CuerpoEstacion(datos: DatosEstacion, estacion: EstacionMeteoRemota, actualizando: Boolean) {
    val ahora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    if (actualizando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp), color = AgroGreenPrimary)
    datos.avisos.forEach { AvisoEstacion(it) }
    val a = datos.actual
    if (a == null) {
        if (datos.avisos.isEmpty()) AvisoEstacion("La estación todavía no tiene datos.")
        return
    }

    val momento = EstacionMeteo.epochMs(a.momento)
    Text(
        text = buildString {
            append("Medido en el campo")
            if (momento != null) append(" · ${EstacionMeteo.antiguedad(momento, ahora)} (${EstacionMeteo.horaArgentina(momento)})")
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))

    // Temperatura grande y humedad
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            a.temperaturaC?.let { formatoDecimal(it, 1) + "°" } ?: "—",
            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.testTag("estacion_temperatura")
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = 6.dp)) {
            a.humedadPct?.let { Text("Humedad ${formatoDecimal(it, 0)} %", style = MaterialTheme.typography.bodyMedium) }
            val t = a.temperaturaC
            val h = a.humedadPct
            val rocio = a.puntoRocioC ?: if (t != null && h != null) EstacionMeteo.puntoRocio(t, h) else null
            rocio?.let {
                Text("Punto de rocío ${grados(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    EstacionMeteo.avisoHelada(a.temperaturaC)?.let {
        Spacer(Modifier.height(6.dp))
        AvisoEstacion("❄️ $it")
    }

    // Condiciones para pulverizar
    EstacionMeteo.pulverizacion(a.temperaturaC, a.humedadPct, a.vientoKmh, a.rafagaKmh, a.lluviaTasaMmH)?.let { c ->
        Spacer(Modifier.height(8.dp))
        TarjetaPulverizacion(c)
    }

    Spacer(Modifier.height(10.dp))
    val datosGrilla = datosDeGrilla(a)
    if (datosGrilla.isNotEmpty()) {
        GrillaAdaptable(elementos = datosGrilla, anchoMinimo = 140.dp, espacio = 8.dp) { dato, modifier ->
            DatoEstacion(dato, modifier)
        }
    }

    // Lluvia acumulada
    val acumulados = listOfNotNull(
        a.lluviaHoyMm?.let { "Hoy" to it },
        a.lluviaSemanaMm?.let { "7 días" to it },
        a.lluviaMesMm?.let { "Mes" to it },
        a.lluviaAnioMm?.let { "Año" to it }
    )
    if (acumulados.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Lluvia acumulada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            acumulados.forEach { (etiqueta, valor) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AgroSkyBlue.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(mm(valor), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }

    if (datos.serie.count { it.temperaturaC != null } >= 2) {
        Spacer(Modifier.height(14.dp))
        Text("Últimas 24 horas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        GraficoUltimas24h(datos.serie)
    }

    if (datos.diario.any { it.lluviaMm != null }) {
        Spacer(Modifier.height(14.dp))
        Text("Lluvia por día (últimos 30 días)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        GraficoLluviaDiaria(datos.diario.takeLast(30))
    }

    Spacer(Modifier.height(10.dp))
    Text(
        "Datos: tu estación (${estacion.marcaEstacion?.nombre ?: estacion.marca})",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class DatoGrilla(val icono: ImageVector, val titulo: String, val valor: String, val detalle: String?)

private fun datosDeGrilla(a: LecturaEstacion): List<DatoGrilla> = buildList {
    a.vientoKmh?.let { v ->
        val dir = a.direccionGrados?.let { " del ${EstacionMeteo.puntoCardinal(it)}" }.orEmpty()
        add(DatoGrilla(Icons.Default.Air, "Viento", "${formatoDecimal(v, 0)} km/h$dir", a.rafagaKmh?.let { "Ráfagas ${formatoDecimal(it, 0)} km/h" }))
    }
    if (a.lluviaHoyMm != null || a.lluviaTasaMmH != null) {
        add(
            DatoGrilla(
                Icons.Default.WaterDrop, "Lluvia hoy", mm(a.lluviaHoyMm ?: 0.0),
                a.lluviaTasaMmH?.let { if (it > 0) "Lloviendo: ${formatoDecimal(it, 1)} mm/h" else "Sin lluvia ahora" }
            )
        )
    }
    a.presionHpa?.let { add(DatoGrilla(Icons.Default.Speed, "Presión", "${formatoDecimal(it, 0)} hPa", null)) }
    if (a.radiacionWm2 != null || a.uv != null) {
        add(
            DatoGrilla(
                Icons.Default.WbSunny, "Radiación",
                a.radiacionWm2?.let { "${formatoDecimal(it, 0)} W/m²" } ?: "UV ${formatoDecimal(a.uv!!, 1)}",
                if (a.radiacionWm2 != null) a.uv?.let { "Índice UV ${formatoDecimal(it, 1)}" } else null
            )
        )
    }
    if (a.sueloHumedadPct != null || a.sueloTemperaturaC != null) {
        add(
            DatoGrilla(
                Icons.Default.Grass, "Suelo",
                a.sueloHumedadPct?.let { "${formatoDecimal(it, 0)} % humedad" } ?: grados(a.sueloTemperaturaC!!),
                if (a.sueloHumedadPct != null) a.sueloTemperaturaC?.let { "Temperatura ${grados(it)}" } else null
            )
        )
    }
    a.temperaturaC?.let { t ->
        a.humedadPct?.let { h ->
            add(DatoGrilla(Icons.Default.Thermostat, "Delta T", formatoDecimal(EstacionMeteo.deltaT(t, h), 1), "Ideal para pulverizar: 2 a 8"))
        }
    }
}

@Composable
private fun DatoEstacion(dato: DatoGrilla, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(dato.icono, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(dato.titulo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(dato.valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            dato.detalle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TarjetaPulverizacion(c: EstacionMeteo.CondicionPulverizacion) {
    val color = when (c.aptitud) {
        EstacionMeteo.Aptitud.BUENA -> Color(0xFF2E7D32)
        EstacionMeteo.Aptitud.PRECAUCION -> Color(0xFFB26A00)
        EstacionMeteo.Aptitud.NO_RECOMENDADA -> Color(0xFFC62828)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("estacion_pulverizacion")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (c.aptitud == EstacionMeteo.Aptitud.BUENA) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null, tint = color, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(c.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (c.motivos.isEmpty()) {
                Text(
                    "Delta T ${c.deltaT?.let { formatoDecimal(it, 1) }}, viento moderado y sin lluvia.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 28.dp)
                )
            }
            c.motivos.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, start = 28.dp))
            }
        }
    }
}

/** Temperatura (línea) y lluvia (barras) de las últimas 24 h. */
@Composable
private fun GraficoUltimas24h(serie: List<PuntoEstacion>) {
    val puntos = serie.mapNotNull { p -> EstacionMeteo.epochMs(p.momento)?.let { it to p } }.sortedBy { it.first }
    if (puntos.size < 2) return
    val temps = puntos.mapNotNull { it.second.temperaturaC }
    val tMin = temps.min()
    val tMax = temps.max()
    val lluviaMax = puntos.maxOf { it.second.lluviaMm ?: 0.0 }
    val lluviaTotal = puntos.sumOf { it.second.lluviaMm ?: 0.0 }
    val colorTemp = Color(0xFFE65100)
    val colorLluvia = AgroSkyBlue
    val colorGuia = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val t0 = puntos.first().first
    val t1 = puntos.last().first
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Temperatura de ${formatoDecimal(tMin, 1)} a ${formatoDecimal(tMax, 1)} grados y ${formatoDecimal(lluviaTotal, 1)} milímetros de lluvia en las últimas 24 horas"
        }
    ) {
        val ancho = size.width
        val alto = size.height
        fun x(t: Long) = if (t1 == t0) 0f else ((t - t0).toFloat() / (t1 - t0)) * ancho
        for (i in 0..3) drawLine(colorGuia, Offset(0f, alto * i / 3f), Offset(ancho, alto * i / 3f), 1f)
        if (lluviaMax > 0) {
            val barra = (ancho / puntos.size * 0.7f).coerceAtLeast(2f)
            puntos.forEach { (t, p) ->
                val ll = p.lluviaMm ?: 0.0
                if (ll > 0) {
                    val h = (ll / lluviaMax).toFloat() * alto * 0.6f
                    drawRect(colorLluvia.copy(alpha = 0.7f), Offset(x(t) - barra / 2, alto - h), Size(barra, h))
                }
            }
        }
        val rango = (tMax - tMin).coerceAtLeast(1.0)
        val path = Path()
        var empezado = false
        puntos.forEach { (t, p) ->
            val temp = p.temperaturaC ?: return@forEach
            val y = alto * 0.08f + (1 - ((temp - tMin) / rango).toFloat()) * alto * 0.84f
            if (!empezado) path.moveTo(x(t), y) else path.lineTo(x(t), y)
            empezado = true
        }
        drawPath(path, colorTemp, style = Stroke(width = 2.5.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(EstacionMeteo.horaArgentina(t0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Temp. ${formatoDecimal(tMin, 1)}–${formatoDecimal(tMax, 1)} °C · Lluvia ${mm(lluviaTotal)}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(EstacionMeteo.horaArgentina(t1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Barras de lluvia de cada día. */
@Composable
private fun GraficoLluviaDiaria(dias: List<DiaEstacion>) {
    val maximo = dias.maxOf { it.lluviaMm ?: 0.0 }
    val total = dias.sumOf { it.lluviaMm ?: 0.0 }
    val color = AgroSkyBlue
    val colorBase = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(
        Modifier.fillMaxWidth().height(80.dp).semantics {
            contentDescription = "${formatoDecimal(total, 1)} milímetros en ${dias.size} días"
        }
    ) {
        val paso = size.width / dias.size
        drawLine(colorBase, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (maximo <= 0) return@Canvas
        dias.forEachIndexed { i, d ->
            val ll = d.lluviaMm ?: 0.0
            if (ll > 0) {
                val h = (ll / maximo).toFloat() * size.height
                drawRect(color, Offset(i * paso + paso * 0.15f, size.height - h), Size(paso * 0.7f, h))
            }
        }
    }
    val lluviosos = dias.filter { (it.lluviaMm ?: 0.0) > 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fechaDiaMes(dias.first().dia), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Total ${mm(total)} en ${lluviosos.size} días con lluvia",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(fechaDiaMes(dias.last().dia), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Lista de los días con lluvia (lo que el productor anota en el pluviómetro)
    if (lluviosos.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            lluviosos.takeLast(8).reversed().joinToString("  ·  ") { "${fechaDiaMes(it.dia)}: ${mm(it.lluviaMm!!)}" },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** "2026-11-16" → "16/11". */
private fun fechaDiaMes(dia: String): String =
    if (dia.length >= 10) "${dia.substring(8, 10)}/${dia.substring(5, 7)}" else dia

// ---------------------------------------------------------------------------------------------
// Pantalla para conectar y administrar estaciones
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEstaciones(viewModel: EstacionesViewModel, onBack: () -> Unit) {
    val estado by viewModel.estaciones.collectAsStateWithLifecycle()
    val agregar by viewModel.agregar.collectAsStateWithLifecycle()
    val eliminando by viewModel.eliminando.collectAsStateWithLifecycle()
    val errorEliminar by viewModel.errorEliminar.collectAsStateWithLifecycle()
    var marca by rememberSaveable { mutableStateOf<MarcaEstacion?>(null) }
    var aEliminar by remember { mutableStateOf<EstacionMeteoRemota?>(null) }

    val volver = {
        if (marca != null) {
            marca = null
            viewModel.terminarAgregar()
        } else onBack()
    }
    androidx.activity.compose.BackHandler(enabled = marca != null) { volver() }

    Scaffold(
        topBar = {
            // Rediseño: el mismo encabezado que el resto de las pantallas
            com.example.ui.rediseno.EncabezadoR(
                titulo = if (marca == null) "Mis estaciones" else marca!!.nombre,
                subtitulo = if (marca == null) "Datos medidos en tu campo" else "Conectar estación",
                onVolver = volver
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val m = marca
        if (m == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val e = estado) {
                    EstadoEstaciones.SinBackend -> item {
                        Text("Las estaciones se guardan en tu cuenta de GRANEX, y esta versión de la app no tiene servidor configurado.")
                    }
                    EstadoEstaciones.Cargando -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    is EstadoEstaciones.Error -> item {
                        AvisoEstacion(e.mensaje)
                        TextButton(onClick = viewModel::cargarLista) { Text("Reintentar") }
                    }
                    is EstadoEstaciones.Lista -> {
                        if (e.estaciones.isNotEmpty()) {
                            item { Text("Conectadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                            items(e.estaciones, key = { it.id }) { est ->
                                FilaEstacion(est, eliminando = eliminando == est.id, onEliminar = { aEliminar = est })
                            }
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        }
                        item {
                            Text("Conectar una estación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Elegí la marca. Las claves se guardan cifradas en el servidor de GRANEX y solo se usan para leer los datos de tu estación.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(MarcaEstacion.entries.toList(), key = { it.clave }) { opcion ->
                            OpcionMarca(opcion) {
                                viewModel.terminarAgregar()
                                marca = opcion
                            }
                        }
                    }
                }
            }
        } else {
            FormularioEstacion(
                marca = m,
                estado = agregar,
                modifier = Modifier.padding(padding),
                onConectar = { nombre, credenciales -> viewModel.conectar(m, nombre, credenciales) },
                onElegir = viewModel::elegir,
                onDescartarError = viewModel::descartarError,
                onTerminar = {
                    viewModel.terminarAgregar()
                    marca = null
                    onBack()
                }
            )
        }
    }

    aEliminar?.let { est ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("¿Desconectar ${est.nombre}?") },
            text = {
                Text(
                    if (est.marca == MarcaEstacion.ENVIO.clave) "Se borran los datos que la estación mandó a GRANEX. La estación va a seguir enviando hasta que borres el servidor propio en su configuración."
                    else "GRANEX borra las claves guardadas. Los datos siguen en la cuenta de la marca."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminar(est.id)
                    aEliminar = null
                }) { Text("Desconectar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } }
        )
    }
    errorEliminar?.let { mensaje ->
        AlertDialog(
            onDismissRequest = viewModel::descartarErrorEliminar,
            title = { Text("No se pudo desconectar") },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = viewModel::descartarErrorEliminar) { Text("Aceptar") } }
        )
    }
}

@Composable
private fun FilaEstacion(est: EstacionMeteoRemota, eliminando: Boolean, onEliminar: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoCirculo(Icons.Default.Sensors, AgroGreenPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(est.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(est.marcaEstacion?.nombre ?: est.marca, est.identificador).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (est.marca == MarcaEstacion.ENVIO.clave) {
                    val ultimo = est.ultimoEnvio?.let(EstacionMeteo::epochMs)
                    Text(
                        if (ultimo == null) "Todavía no recibió datos" else "Último dato ${EstacionMeteo.antiguedad(ultimo, System.currentTimeMillis())}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (eliminando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, contentDescription = "Desconectar ${est.nombre}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OpcionMarca(marca: MarcaEstacion, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("marca_${marca.clave}")
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoCirculo(if (marca == MarcaEstacion.ENVIO) Icons.Default.Sensors else Icons.Default.Thermostat, AgroSkyBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(marca.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(marca.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = AgroGreenPrimary)
        }
    }
}

@Composable
private fun FormularioEstacion(
    marca: MarcaEstacion,
    estado: EstadoAgregarEstacion,
    modifier: Modifier,
    onConectar: (nombre: String, credenciales: Map<String, String>) -> Unit,
    onElegir: (com.example.data.remote.supabase.EstacionDeCuenta) -> Unit,
    onDescartarError: () -> Unit,
    onTerminar: () -> Unit
) {
    // Las claves en remember (no rememberSaveable): no deben quedar en el estado guardado
    var nombre by rememberSaveable(marca) { mutableStateOf("") }
    val credenciales = remember(marca) { mutableStateMapOf<String, String>() }
    val enviando = estado == EstadoAgregarEstacion.Enviando

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (estado is EstadoAgregarEstacion.Listo) {
            item { EstacionConectada(estado, onTerminar) }
            return@LazyColumn
        }
        if (estado is EstadoAgregarEstacion.Elegir) {
            item {
                Text("Tu cuenta tiene varias estaciones. ¿Cuál querés conectar?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(estado.opciones, key = { it.identificador }) { opcion ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onElegir(opcion) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(opcion.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(opcion.identificador, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Text(marca.ayuda, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it.take(60) },
                label = { Text("Nombre (por ejemplo, \"Campo La Esperanza\")") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("campo_nombre_estacion")
            )
        }
        items(marca.campos, key = { it.clave }) { campo ->
            val valor = credenciales[campo.clave].orEmpty()
            if (campo.secreto) {
                CampoSecreto(
                    valor = valor,
                    onValorChange = { credenciales[campo.clave] = it.trim() },
                    etiqueta = campo.etiqueta,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "campo_${campo.clave}",
                    imeAction = ImeAction.Next
                )
            } else {
                CampoIdentificador(
                    valor = valor,
                    onValorChange = { credenciales[campo.clave] = it.trim() },
                    etiqueta = campo.etiqueta,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "campo_${campo.clave}"
                )
            }
        }
        if (estado is EstadoAgregarEstacion.Error) {
            item { AvisoEstacion(estado.mensaje) }
        }
        item {
            val completo = nombre.isNotBlank() && marca.campos.all { !credenciales[it.clave].isNullOrBlank() }
            Button(
                onClick = {
                    onDescartarError()
                    onConectar(nombre, credenciales.toMap())
                },
                enabled = completo && !enviando,
                modifier = Modifier.fillMaxWidth().testTag("btn_guardar_estacion")
            ) {
                if (enviando) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (marca == MarcaEstacion.ENVIO) "Creando…" else "Comprobando las claves…")
                } else {
                    Text(if (marca == MarcaEstacion.ENVIO) "Crear código de envío" else "Conectar")
                }
            }
        }
    }
}

@Composable
private fun EstacionConectada(estado: EstadoAgregarEstacion.Listo, onTerminar: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
            Spacer(Modifier.width(8.dp))
            Text("${estado.estacion.nombre} quedó conectada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val token = estado.token
        val url = ApiKeys.supabase?.url
        if (token != null && url != null) {
            val conf = EstacionMeteo.configuracionEnvio(url, token)
            Text(
                "Cargá estos datos en la configuración de la estación, en \"servidor propio\" o \"Customized\" (en Ecowitt: app WS View Plus → Weather Services → Customized). " +
                    "Guardalos ahora: por seguridad el código no se vuelve a mostrar.",
                style = MaterialTheme.typography.bodyMedium
            )
            DatoCopiable(context, "Protocolo", "Ecowitt (o Wunderground)")
            DatoCopiable(context, "Servidor", conf.servidor)
            DatoCopiable(context, "Ruta (Path)", conf.ruta)
            DatoCopiable(context, "Puerto", conf.puerto.toString())
            DatoCopiable(context, "Intervalo", "60 segundos")
            Text(
                "La conexión es segura (HTTPS, puerto 443). Si tu estación solo permite enviar por el puerto 80, escribinos a supportgranex@gmail.com con el modelo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Ya podés ver sus datos en Clima.", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onTerminar, modifier = Modifier.fillMaxWidth().testTag("btn_listo_estacion")) { Text("Listo") }
    }
}

@Composable
private fun DatoCopiable(context: Context, etiqueta: String, valor: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valor, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = {
                val portapapeles = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                portapapeles.setPrimaryClip(ClipData.newPlainText(etiqueta, valor))
                Toast.makeText(context, "$etiqueta copiado", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar $etiqueta")
            }
        }
    }
}
