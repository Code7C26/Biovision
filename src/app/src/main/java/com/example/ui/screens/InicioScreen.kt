package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.model.AgroClimateDashboard
import com.example.data.repository.LoteSatelital
import com.example.data.repository.NewsArticle
import com.example.ui.components.Grano
import com.example.ui.components.IconoGrano
import com.example.ui.components.abrirNoticia
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.EstadoPerfil
import com.example.ui.viewmodel.EstadoPreciosInicio
import com.example.ui.viewmodel.FinancialUiState
import com.example.ui.viewmodel.FinancialViewModel
import com.example.ui.viewmodel.InicioViewModel
import com.example.ui.viewmodel.NovedadesUiState
import com.example.ui.viewmodel.NovedadesViewModel
import com.example.ui.viewmodel.PerfilViewModel
import com.example.ui.viewmodel.SateliteUiState
import com.example.ui.viewmodel.SateliteViewModel
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import com.example.utils.NivelHelada
import com.example.utils.PronosticoAgro
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Rediseño: la pantalla de Inicio responde lo que el productor quiere saber al abrir la app (si
 * puede pulverizar, si llueve o hiela, los precios, sus lotes y las noticias) y lleva a cada sección.
 * No carga nada propio salvo la pizarra del día ([InicioViewModel]): usa los ViewModels de cada pestaña.
 */
@Composable
fun InicioScreen(
    inicio: InicioViewModel,
    clima: WeatherViewModel,
    finanzas: FinancialViewModel,
    satelite: SateliteViewModel,
    agenda: com.example.ui.viewmodel.AgendaViewModel,
    novedades: NovedadesViewModel,
    perfil: PerfilViewModel,
    onAbrirClima: () -> Unit,
    onAbrirMercado: () -> Unit,
    onAbrirCampo: () -> Unit,
    onAbrirLote: (LoteSatelital) -> Unit,
    onAbrirAgenda: () -> Unit,
    onAnotarGasto: () -> Unit,
    onAbrirNoticias: () -> Unit,
    onAbrirPerfil: () -> Unit
) {
    val estadoClima by clima.uiState.collectAsStateWithLifecycle()
    val estadoFinanzas by finanzas.uiState.collectAsStateWithLifecycle()
    val estadoLotes by satelite.uiState.collectAsStateWithLifecycle()
    val estadoNoticias by novedades.uiState.collectAsStateWithLifecycle()
    val estadoPerfil by perfil.estado.collectAsStateWithLifecycle()
    val precios by inicio.precios.collectAsStateWithLifecycle()
    val estadoAgenda by agenda.estado.collectAsStateWithLifecycle()

    // El dólar se carga con su caché (no repite la consulta si está vigente)
    LaunchedEffect(Unit) { finanzas.cargarDatosFinancieros() }

    val nombre = when (val p = estadoPerfil) {
        is EstadoPerfil.Listo -> p.perfil.nombre
        is EstadoPerfil.Local -> p.perfil.nombre
        is EstadoPerfil.SinConexion -> p.guardado?.nombre
        else -> null
    }?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    val establecimiento = when (val p = estadoPerfil) {
        is EstadoPerfil.Listo -> p.perfil.establecimiento
        is EstadoPerfil.Local -> p.perfil.establecimiento
        is EstadoPerfil.SinConexion -> p.guardado?.establecimiento
        else -> null
    }?.takeIf { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pantalla_inicio"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Encabezado(nombre, establecimiento, onAbrirPerfil) }
        item {
            when (val c = estadoClima) {
                is WeatherUiState.Success -> TarjetaHoy(c.dashboard, onAbrirClima)
                is WeatherUiState.Error -> TarjetaAviso("Clima", "No se pudo cargar el pronóstico.", "Ver clima", onAbrirClima)
                WeatherUiState.Loading -> TarjetaCargando(170)
            }
        }
        item { TarjetaPrecios(precios, estadoFinanzas, onAbrirMercado, onReintentar = inicio::cargar) }
        item {
            val lotes = (estadoLotes as? SateliteUiState.Success)?.lotes
            SeccionLotes(lotes, onAbrirCampo, onAbrirLote)
        }
        item { TarjetaAgenda(estadoAgenda, onAbrirAgenda, onAnotarGasto) }
        item {
            val notas = (estadoNoticias as? NovedadesUiState.Success)?.listaNovedades.orEmpty()
            TarjetaNoticias(notas.take(3), cargando = estadoNoticias is NovedadesUiState.LoadingSkeleton, onAbrirNoticias)
        }
    }
}

@Composable
private fun Encabezado(nombre: String?, establecimiento: String?, onAbrirPerfil: () -> Unit) {
    val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val saludo = when (hora) {
        in 5..11 -> "Buen día"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val fecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "AR")).format(Date())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.logo_granex),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (nombre != null) "$saludo, $nombre" else saludo,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(establecimiento, fecha.replaceFirstChar { it.uppercase() }).joinToString(" · "),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            onClick = onAbrirPerfil,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp).testTag("btn_perfil")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    (nombre?.firstOrNull() ?: 'G').uppercase(),
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** El clima de ahora y las tres respuestas del día: pulverizar, lluvia y helada. */
@Composable
private fun TarjetaHoy(d: AgroClimateDashboard, onAbrirClima: () -> Unit) {
    val fondo = Color(0xFF064E3B)
    Surface(
        onClick = onAbrirClima,
        shape = RoundedCornerShape(24.dp),
        color = fondo,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().testTag("tarjeta_hoy")
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${d.ciudad} · ahora", fontSize = 13.sp, color = Color(0xFFA7F3D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.tempCelsius.toInt()}°", fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp)
                Text(
                    "${d.condicionTexto} · máx ${d.tempMax.toInt()}° mín ${d.tempMin.toInt()}°",
                    fontSize = 14.sp, color = Color(0xFFD1FAE5)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fraseSpray(d)?.let { (texto, color) -> FilaDecision(texto, color) }
                fraseLluvia(d)?.let { (texto, color) -> FilaDecision(texto, color) }
                fraseHelada(d)?.let { (texto, color) -> FilaDecision(texto, color) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ver todo el clima", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val VERDE_OK = Color(0xFF34D399)
private val AMBAR = Color(0xFFFCD34D)
private val ROJO_SUAVE = Color(0xFFFCA5A5)

private fun fraseSpray(d: AgroClimateDashboard): Pair<androidx.compose.ui.text.AnnotatedString, Color>? {
    val p = d.pulverizacion ?: return null
    val hoy = p.ventanas.firstOrNull { it.dia == 0 }
    val manana = p.ventanas.firstOrNull { it.dia == 1 }
    return when {
        hoy != null -> negrita("Se puede pulverizar", " de ${hoy.horaDesde} a ${hoy.horaHasta} h") to VERDE_OK
        manana != null -> negrita("Hoy no conviene pulverizar", "; mañana de ${manana.horaDesde} a ${manana.horaHasta} h") to AMBAR
        else -> negrita("No conviene pulverizar", " en las próximas 48 h") to ROJO_SUAVE
    }
}

private fun fraseLluvia(d: AgroClimateDashboard): Pair<androidx.compose.ui.text.AnnotatedString, Color>? {
    val dias = d.forecast?.dailyList ?: return null
    val i = dias.take(4).indexOfFirst { it.precipitacionMm >= 1.0 }
    if (i < 0) return negrita("Sin lluvias", " en los próximos 4 días") to VERDE_OK
    val cuando = when (i) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "el " + dias[i].dayName.lowercase()
    }
    val mm = dias[i].precipitacionMm
    return negrita("Lluvia $cuando:", " ${if (mm >= 10) mm.toInt() else String.format(Locale("es", "AR"), "%.1f", mm)} mm") to AMBAR
}

private fun fraseHelada(d: AgroClimateDashboard): Pair<androidx.compose.ui.text.AnnotatedString, Color>? {
    val h = d.helada ?: return null
    val cuando = PronosticoAgro.nombreDia(h.diaMinima, h.hoy + h.diaMinima).lowercase()
    val minima = String.format(Locale("es", "AR"), "%.1f°", h.minimaC)
    return when (h.nivel) {
        NivelHelada.SIN_RIESGO -> negrita("Sin helada", " en los próximos 3 días") to VERDE_OK
        NivelHelada.RIESGO -> negrita("Riesgo de helada:", " mínima $minima $cuando a las ${h.horaMinima} h") to AMBAR
        NivelHelada.HELADA -> negrita("Helada:", " mínima $minima $cuando a las ${h.horaMinima} h") to ROJO_SUAVE
    }
}

private fun negrita(fuerte: String, resto: String) = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fuerte) }
    append(resto)
}

@Composable
private fun FilaDecision(texto: androidx.compose.ui.text.AnnotatedString, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(texto, fontSize = 14.sp)
    }
}

@Composable
private fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() }
    }
}

@Composable
private fun TituloTarjeta(titulo: String, accion: String?, onAccion: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (accion != null && onAccion != null) {
            TextButton(onClick = onAccion, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(accion, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TarjetaPrecios(
    precios: EstadoPreciosInicio,
    finanzas: FinancialUiState,
    onAbrirMercado: () -> Unit,
    onReintentar: () -> Unit
) {
    Tarjeta(Modifier.testTag("tarjeta_precios")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Precios de hoy", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val fecha = (precios as? EstadoPreciosInicio.Listo)?.fecha?.split("-")?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}" }
            Text(
                "Pizarra Rosario${fecha?.let { " $it" } ?: ""} · US$/t",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (precios) {
            is EstadoPreciosInicio.Listo -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                precios.precios.take(3).forEach { p ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconoGrano(Grano.de(p.grano), tamano = 18.dp)
                            Text(p.grano, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(p.usdTn.toInt().toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        p.variacionPct?.let { v ->
                            Text(
                                (if (v >= 0) "▲ " else "▼ ") + String.format(Locale("es", "AR"), "%.1f %%", kotlin.math.abs(v)),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (v >= 0) AgroGreenPrimary else Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
            is EstadoPreciosInicio.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(precios.mensaje, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onReintentar) { Text("Reintentar", color = AgroGreenPrimary) }
            }
            EstadoPreciosInicio.Cargando -> Box(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
        val divisas = (finanzas as? FinancialUiState.Success)?.data?.divisas.orEmpty()
        val oficial = divisas.firstOrNull { it.casa == "oficial" }
        val mep = divisas.firstOrNull { it.casa == "mep" }
        if (oficial != null || mep != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                oficial?.let { Text(negrita("Dólar oficial ", pesos(it.venta)).let { s -> s }, fontSize = 13.sp) }
                mep?.let { Text("MEP " + pesos(it.venta), fontSize = 13.sp) }
            }
        }
        Row(
            modifier = Modifier.clickable(role = Role.Button, onClick = onAbrirMercado).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ver mercado, futuros y dólar", color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun pesos(v: Double) = "$ " + String.format(Locale("es", "AR"), "%,.0f", v)

@Composable
private fun SeccionLotes(lotes: List<LoteSatelital>?, onAbrirCampo: () -> Unit, onAbrirLote: (LoteSatelital) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloTarjeta("Mis lotes", "Ver mapa", onAbrirCampo)
        when {
            lotes == null -> TarjetaCargando(96)
            lotes.isEmpty() -> Tarjeta {
                Text("Todavía no cargaste lotes.", fontSize = 14.sp)
                Text(
                    "Tocá tu lote en el mapa y GRANEX busca el contorno solo, o dibujalo a mano.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAbrirCampo,
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cargar mi primer lote")
                }
            }
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lotes, key = { it.id }) { lote ->
                    Surface(
                        onClick = { onAbrirLote(lote) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.width(160.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lote.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                String.format(Locale("es", "AR"), "%.1f ha", lote.areaHectareas) +
                                    (lote.provincia.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("Ver vigor y escaneo", fontSize = 12.sp, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaAgenda(estado: com.example.ui.viewmodel.EstadoAgenda, onAbrirAgenda: () -> Unit, onAnotarGasto: () -> Unit) {
    val r = estado.resumen
    Tarjeta(Modifier.testTag("tarjeta_agenda")) {
        TituloTarjeta("Agenda · campaña ${estado.campana}", "Abrir", onAbrirAgenda)
        if (r.totalUsd <= 0) {
            Text(
                "Anotá semillas, insumos y todos los gastos, y mirá cuánto llevás gastado por hectárea en cada lote.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Gastado", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(usdAgenda(r.totalUsd), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    estado.usdPorHa?.let { Text("${usdAgenda(it)}/ha", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (r.facturasAPagar > 0) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFBEB)).padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("A pagar", fontSize = 12.sp, color = Color(0xFF78350F))
                        Text(usdAgenda(r.aPagarUsd), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F))
                        r.proximoVencimiento?.let { Text("vence el ${fechaAgenda(it)}", fontSize = 12.sp, color = Color(0xFF78350F)) }
                    }
                }
            }
        }
        Button(
            onClick = onAnotarGasto,
            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Anotar gasto", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TarjetaNoticias(notas: List<NewsArticle>, cargando: Boolean, onAbrirNoticias: () -> Unit) {
    val context = LocalContext.current
    val colorBarra = AgroGreenPrimary
    Tarjeta(Modifier.testTag("tarjeta_noticias")) {
        TituloTarjeta("Noticias del campo", "Ver todas", onAbrirNoticias)
        when {
            notas.isNotEmpty() -> notas.forEachIndexed { i, nota ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { abrirNoticia(context, nota.articleUrl, colorBarra) }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${nota.sourceName} · ${nota.formattedTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(nota.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                }
            }
            cargando -> TarjetaCargando(80)
            else -> Text("No se pudieron cargar las noticias.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TarjetaAviso(titulo: String, texto: String, accion: String, onAccion: () -> Unit) {
    Tarjeta {
        TituloTarjeta(titulo, accion, onAccion)
        Text(texto, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TarjetaCargando(alto: Int) {
    Box(
        Modifier.fillMaxWidth().height(alto.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )
}
