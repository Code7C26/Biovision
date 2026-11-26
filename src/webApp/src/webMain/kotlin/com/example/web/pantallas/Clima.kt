package com.example.web.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.ktor.PronosticoOpenMeteo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.remote.ktor.UvHoy
import com.example.data.remote.model.riesgoIncendio
import com.example.web.ui.IconoAlerta
import com.example.web.ui.LeyendaHoraria
import com.example.web.ui.GraficoHorario
import com.example.web.ui.PuntoHorario
import com.example.web.ui.TarjetaMetrica
import com.example.web.ui.TarjetaUv
import com.example.web.ui.BarraRango
import com.example.web.ui.aspectoClima
import com.example.data.remote.model.describirCodigoClima
import com.example.web.plataforma.MarcoWeb
import com.example.web.plataforma.Navegador
import com.example.web.ui.Aviso
import com.example.web.ui.BotonIcono
import com.example.web.ui.BuscadorLocalidad
import com.example.web.ui.ClaseAncho
import com.example.web.ui.Dato
import com.example.web.ui.Encabezado
import com.example.web.ui.ErrorConReintento
import com.example.web.ui.EspacioPanel
import com.example.web.ui.Fuente
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.diaCorto
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.ui.desplazableHorizontal
import com.example.web.viewmodel.CapaWindy
import com.example.web.viewmodel.CiudadClima
import com.example.web.viewmodel.ClimaWebViewModel
import com.example.web.viewmodel.EstacionesWebViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.web.viewmodel.EstadoPronostico
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.remember

/**
 * Clima: condiciones actuales, indicadores agro y pronóstico de 7 días (Open-Meteo) + mapa de Windy.
 * Teléfono: todo en una columna. Tablet: datos | mapa. Escritorio: actual + agro | pronóstico | mapa.
 */
@Composable
fun PantallaClima(vm: ClimaWebViewModel, estaciones: EstacionesWebViewModel, urlServidor: String?) {
    var administrando by rememberSaveable { mutableStateOf(false) }
    if (administrando) {
        PantallaEstaciones(estaciones, urlServidor) { administrando = false }
        return
    }
    val ciudad by vm.ciudad.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val capa by vm.capa.collectAsStateWithLifecycle()
    val agro by vm.agro.collectAsStateWithLifecycle()
    val texto by vm.buscador.texto.collectAsStateWithLifecycle()
    val sugerencias by vm.buscador.sugerencias.collectAsStateWithLifecycle()
    val clase = LocalClaseAncho.current

    val buscador: @Composable () -> Unit = {
        BuscadorLocalidad(
            texto = texto,
            alCambiar = { vm.buscador.texto.value = it },
            sugerencias = sugerencias,
            alElegir = vm::elegir,
            placeholder = "Buscar localidad (p. ej. Pergamino)"
        )
    }
    val datos: @Composable ColumnScope.(Boolean) -> Unit = { conPronostico ->
        TarjetaEstacionPropia(estaciones) { administrando = true }
        when (val e = estado) {
            EstadoPronostico.Cargando -> {
                PanelEsqueleto("Cargando pronóstico de ${ciudad.nombre}…", lineas = 5)
                PanelEsqueleto("Indicadores agro · 7 días", lineas = 4)
            }
            is EstadoPronostico.Error -> ErrorConReintento(e.mensaje, vm::cargar)
            is EstadoPronostico.Ok -> {
                PanelActual(ciudad, e.datos)
                MetricasClima(e.datos, e.uv)
                if (conPronostico) PanelHorario(e.datos)
                PanelAgro(e.datos)
                PanelesHerramientasAgro(agro)
                if (conPronostico) PanelPronostico(e.datos)
            }
        }
    }
    val atribucion: @Composable () -> Unit = {
        Fuente("Datos: Open-Meteo.com · Mapa: Windy.com", "https://open-meteo.com/")
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Clima",
            chips = listOfNotNull(ciudad.nombre, (estado as? EstadoPronostico.Ok)?.let { "Actualizado ${it.hora}" })
        ) {
            BotonIcono(Icons.Filled.Refresh, "Actualizar pronóstico", vm::cargar)
        }
        when (clase) {
            ClaseAncho.COMPACTA -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                buscador()
                datos(true)
                PanelMapaWindy(ciudad, capa, vm::elegirCapa, Modifier.fillMaxWidth().height(440.dp))
                atribucion()
            }
            ClaseAncho.MEDIANA -> Row(
                Modifier.fillMaxSize().padding(EspacioPanel),
                horizontalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EspacioPanel)
                ) {
                    buscador()
                    datos(true)
                    atribucion()
                }
                PanelMapaWindy(ciudad, capa, vm::elegirCapa, Modifier.weight(1.1f).fillMaxHeight())
            }
            ClaseAncho.EXPANDIDA -> Row(
                Modifier.fillMaxSize().padding(EspacioPanel),
                horizontalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EspacioPanel)
                ) {
                    buscador()
                    datos(false)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EspacioPanel)
                ) {
                    (estado as? EstadoPronostico.Ok)?.let {
                        PanelHorario(it.datos)
                        PanelPronostico(it.datos)
                    }
                    atribucion()
                }
                PanelMapaWindy(ciudad, capa, vm::elegirCapa, Modifier.weight(1.4f).fillMaxHeight())
            }
        }
    }
}

/**
 * Condición actual destacada: ícono del tiempo, temperatura grande y máxima/mínima de hoy, sobre un
 * degradé de la marca (como el encabezado del clima en la app Android).
 */
@Composable
private fun PanelActual(ciudad: CiudadClima, p: PronosticoOpenMeteo) {
    val a = p.current ?: return
    val marca = LocalMarca.current
    val aspecto = aspectoClima(a.codigo, deDia = a.esDeDia != 0)
    val hoyMax = p.daily?.maxima?.firstOrNull()
    val hoyMin = p.daily?.minima?.firstOrNull()
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(marca.primarioOscuro, marca.primario)))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ciudad.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(aspecto.icono, contentDescription = null, tint = when (aspecto.icono) {
                            IconosGranex.Sol -> Color(0xFFFFD54F)
                            IconosGranex.Luna -> Color(0xFFFDE68A)
                            else -> Color.White
                        }, modifier = Modifier.size(38.dp))
                    }
                    Column {
                        Text(
                            a.temperatura?.let { "${numero(it, 1)}\u00A0°C" } ?: "—",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            a.codigo?.let(::describirCodigoClima) ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        a.sensacion?.let { "Sensación ${numero(it, 1)}\u00A0°C" },
                        if (hoyMax != null && hoyMin != null) "Hoy ${numero(hoyMin)}° / ${numero(hoyMax)}°" else null
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/** Tarjetas de datos con su lectura agronómica y el UV, con los mismos criterios que la app Android. */
@Composable
private fun MetricasClima(p: PronosticoOpenMeteo, uv: UvHoy?) {
    val a = p.current ?: return
    val marca = LocalMarca.current
    val lluviaHoy = p.daily?.lluvia?.firstOrNull()
    val probHoy = p.daily?.probabilidadLluvia?.firstOrNull()
    val tarjetas = buildList<@Composable (Modifier) -> Unit> {
        a.humedad?.let { h ->
            add { m -> TarjetaMetrica(IconosGranex.Gota, "Humedad relativa", "${numero(h)} %", if (h in 50.0..80.0) "Óptima aplicación" else "Extrema para aplicar", m) }
        }
        a.viento?.let { v ->
            val dir = a.direccionViento?.let { "${puntoCardinal(it)} (${numero(it)}°)" }
            val raf = a.rafagas?.let { "Ráf. ${numero(it)}" }
            add { m -> TarjetaMetrica(IconosGranex.Viento, "Viento", "${numero(v, 1)} km/h", listOfNotNull(dir, raf).joinToString(" · "), m) }
        }
        a.sensacion?.let { st ->
            add { m -> TarjetaMetrica(IconosGranex.Termometro, "Sensación térmica", "${numero(st, 1)}\u00A0°C", a.nubosidad?.let { "Nubosidad ${numero(it)} %" }, m) }
        }
        add { m ->
            TarjetaMetrica(
                IconosGranex.Gota,
                "Lluvia hoy",
                "${numero(lluviaHoy ?: a.precipitation ?: 0.0, 1)} mm",
                probHoy?.let { "Probabilidad ${numero(it)} %" } ?: "Sin probabilidad",
                m,
                color = marca.info
            )
        }
        a.presion?.let { pr ->
            add { m ->
                TarjetaMetrica(
                    IconosGranex.Presion,
                    "Presión atmosférica",
                    "${numero(pr)} hPa",
                    when {
                        pr < 1005 -> "Baja: inestabilidad"
                        pr > 1020 -> "Alta: tiempo estable"
                        else -> "Normal"
                    },
                    m
                )
            }
        }
        if (a.temperatura != null && a.humedad != null && a.viento != null) {
            val riesgo = riesgoIncendio(a.temperatura!!, a.humedad!!, a.viento!!)
            add { m ->
                TarjetaMetrica(
                    IconoAlerta,
                    "Riesgo de incendio",
                    riesgo,
                    if (riesgo == "Bajo") "Baja alerta" else "Monitorear rastrojo",
                    m,
                    color = if (riesgo == "Bajo") marca.primario else marca.alerta
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tarjetas.chunked(2).forEach { fila ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        uv?.let { TarjetaUv(it.actual, it.maximo) }
    }
}

/** Próximas 24 horas: temperatura y lluvia por hora (desde la hora actual del lugar). */
@Composable
private fun PanelHorario(p: PronosticoOpenMeteo) {
    val h = p.hourly ?: return
    val ahora = p.current?.time?.take(13) ?: return // "yyyy-MM-ddTHH"
    val desde = h.time.indexOfFirst { it.take(13) >= ahora }.takeIf { it >= 0 } ?: return
    val puntos = (desde until minOf(desde + 25, h.time.size)).map { i ->
        PuntoHorario(
            hora = h.time[i].substring(11, 13) + " h",
            temperatura = h.temperatura.getOrNull(i),
            lluviaMm = h.precipitation.getOrNull(i) ?: 0.0
        )
    }
    if (puntos.size < 2) return
    val lluvia = puntos.sumOf { it.lluviaMm }
    Panel(
        "Próximas 24 horas",
        subtitulo = if (lluvia > 0.05) "Lluvia esperada: ${numero(lluvia, 1)} mm" else "Sin lluvia prevista"
    ) {
        GraficoHorario(puntos)
        LeyendaHoraria()
    }
}

/** Resumen de la semana para decisiones de campo: agua (lluvia − ET0) y heladas. */
@Composable
private fun PanelAgro(p: PronosticoOpenMeteo) {
    val d = p.daily ?: return
    val lluvia = d.lluvia.filterNotNull().sum()
    val et0 = d.evapotranspiracion.filterNotNull().sum()
    val heladas = d.minima.count { it != null && it <= 0.0 }
    val riesgoHelada = d.minima.count { it != null && it > 0.0 && it <= 3.0 }
    val balance = lluvia - et0
    val marca = LocalMarca.current
    Panel("Indicadores agro · 7 días") {
        Dato("Lluvia acumulada", "${numero(lluvia, 1)} mm")
        Dato("Evapotranspiración (ET0)", "${numero(et0, 1)} mm")
        Dato(
            "Balance hídrico",
            "${if (balance > 0) "+" else ""}${numero(balance, 1)} mm",
            if (balance >= 0) marca.positivo else marca.alerta
        )
        Dato("Días con helada (mín. ≤ 0 °C)", heladas.toString(), if (heladas > 0) marca.negativo else marca.positivo)
        Dato("Días con riesgo (0–3 °C)", riesgoHelada.toString(), if (riesgoHelada > 0) marca.alerta else marca.positivo)
    }
}

@Composable
private fun PanelPronostico(p: PronosticoOpenMeteo) {
    val d = p.daily ?: return
    // Escala de toda la semana para las barras de temperatura
    val minSemana = d.minima.filterNotNull().minOrNull() ?: 0.0
    val maxSemana = d.maxima.filterNotNull().maxOrNull() ?: 1.0
    // Un día abierto a la vez; tocarlo de nuevo lo cierra
    var abierto by rememberSaveable { mutableStateOf<String?>(null) }
    val menosMovimiento = remember { Navegador.prefiereMenosMovimiento() }
    Panel("Pronóstico extendido", subtitulo = "7 días · ECMWF · tocá un día para ver el detalle") {
        d.time.forEachIndexed { i, fecha ->
            if (i > 0) HorizontalDivider()
            val aspecto = aspectoClima(d.codigo.getOrNull(i))
            val minima = d.minima.getOrNull(i)
            val maxima = d.maxima.getOrNull(i)
            val esteAbierto = abierto == fecha
            val nombreDia = if (i == 0) "Hoy" else diaCorto(fecha)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).mano()
                    .clickable(onClickLabel = if (esteAbierto) "Cerrar el detalle de $nombreDia" else "Ver el detalle de $nombreDia") {
                        abierto = if (esteAbierto) null else fecha
                    }
                    .background(if (esteAbierto) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent)
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.width(92.dp)) {
                        Text(nombreDia, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(
                            d.codigo.getOrNull(i)?.let(::describirCodigoClima) ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (esteAbierto) 3 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(aspecto.icono, contentDescription = null, tint = aspecto.color, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(minima?.let { "${numero(it)}°" } ?: "—", Modifier.width(30.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (minima != null && maxima != null) BarraRango(minima, maxima, minSemana, maxSemana, Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                            Text(maxima?.let { "${numero(it)}°" } ?: "—", Modifier.width(34.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                        val mm = d.lluvia.getOrNull(i) ?: 0.0
                        val prob = d.probabilidadLluvia.getOrNull(i)
                        if (mm > 0 || (prob ?: 0.0) >= 20) {
                            Text(
                                "${numero(mm, 1)} mm${prob?.let { " · ${numero(it)} %" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalMarca.current.info
                            )
                        }
                    }
                    Icon(
                        if (esteAbierto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(
                    visible = esteAbierto,
                    enter = if (menosMovimiento) EnterTransition.None else expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = if (menosMovimiento) ExitTransition.None else shrinkVertically(tween(160)) + fadeOut(tween(160))
                ) {
                    DetalleDiaPronostico(p, i, fecha)
                }
            }
        }
    }
}

/** Lo que se ve al abrir un día: el estado completo, temperaturas por momento, lluvia, viento y ET0. */
@Composable
private fun DetalleDiaPronostico(p: PronosticoOpenMeteo, i: Int, fecha: String) {
    val d = p.daily ?: return
    val horas = horasDelDia(p, fecha)
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        d.codigo.getOrNull(i)?.let(::describirCodigoClima)?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        val minima = d.minima.getOrNull(i)
        val maxima = d.maxima.getOrNull(i)
        if (minima != null || maxima != null) {
            Dato("Mínima / máxima", "${minima?.let { numero(it, 1) } ?: "—"}° / ${maxima?.let { numero(it, 1) } ?: "—"}°")
        }
        if (horas.temperaturas.isNotEmpty()) {
            // En columnas: en una sola línea no entra en un teléfono y aplastaba la etiqueta
            Text("Temperatura durante el día", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) {
                horas.temperaturas.forEach { (h, t) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$h h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${numero(t)}°", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        val mm = d.lluvia.getOrNull(i) ?: 0.0
        val prob = d.probabilidadLluvia.getOrNull(i)
        Dato(
            "Lluvia",
            buildString {
                append("${numero(mm, 1)} mm")
                prob?.let { append(" · ${numero(it)} % de probabilidad") }
            },
            if (mm > 0) LocalMarca.current.info else Color.Unspecified
        )
        horas.tramoLluvia?.let { (desde, hasta) ->
            Dato("Cuándo llueve", "entre las $desde y las $hasta h (${horas.horasConLluvia} h con lluvia)")
        }
        horas.vientoMaximo?.let { Dato("Viento máximo", "${numero(it)} km/h") }
        d.evapotranspiracion.getOrNull(i)?.let { Dato("Evapotranspiración (ET0)", "${numero(it, 1)} mm") }
    }
}

/** Resumen de las horas de un día del pronóstico (hora local "yyyy-MM-ddTHH:mm"). */
private class HorasDia(
    val temperaturas: List<Pair<String, Double>>,
    val vientoMaximo: Double?,
    val tramoLluvia: Pair<String, String>?,
    val horasConLluvia: Int
)

private fun horasDelDia(p: PronosticoOpenMeteo, fecha: String): HorasDia {
    val h = p.hourly ?: return HorasDia(emptyList(), null, null, 0)
    val indices = h.time.indices.filter { h.time[it].startsWith(fecha) }
    fun hora(i: Int) = h.time[i].substring(11, 13).toIntOrNull()
    val temperaturas = listOf(6, 12, 15, 21).mapNotNull { objetivo ->
        val i = indices.firstOrNull { hora(it) == objetivo } ?: return@mapNotNull null
        h.temperatura.getOrNull(i)?.let { objetivo.toString().padStart(2, '0') to it }
    }
    val viento = indices.mapNotNull { h.viento.getOrNull(it) }.maxOrNull()
    // La lluvia de cada hora es la acumulada en la hora anterior: la de las 15 h cayó entre las 14 y las 15
    val conLluvia = indices.filter { (h.precipitation.getOrNull(it) ?: 0.0) >= 0.1 }
    val tramo = if (conLluvia.isEmpty()) null else {
        val desde = ((hora(conLluvia.first()) ?: 1) - 1).coerceAtLeast(0)
        val hasta = hora(conLluvia.last()) ?: 24
        desde.toString().padStart(2, '0') to hasta.toString().padStart(2, '0')
    }
    return HorasDia(temperaturas, viento, tramo, conLluvia.size)
}

@Composable
private fun PanelMapaWindy(ciudad: CiudadClima, capa: CapaWindy, alElegir: (CapaWindy) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().desplazableHorizontal(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapaWindy.entries.forEach { c ->
                FilterChip(selected = c == capa, onClick = { alElegir(c) }, label = { Text(c.etiqueta) }, modifier = Modifier.mano())
            }
            TextButton(
                onClick = { Navegador.abrirEnPestana(ClimaWebViewModel.urlWindyCompleto(ciudad, capa)) },
                modifier = Modifier.mano()
            ) {
                Text("Abrir en Windy")
                Icon(IconosGranex.AbrirAfuera, contentDescription = "(abre otra pestaña)", Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
        // Cambiar de capa o de localidad recarga el embed: desde otro origen no se puede mover su mapa
        MarcoWeb(
            url = ClimaWebViewModel.urlWindy(ciudad, capa),
            titulo = "Mapa de Windy: ${capa.etiqueta}",
            modifier = Modifier.fillMaxWidth().weight(1f),
            externo = true
        )
        Aviso("Toca el mapa para ver el pronóstico de cualquier punto. El mapa de Windy es un servicio externo.")
    }
}

private fun puntoCardinal(grados: Double): String {
    val puntos = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return puntos[(((grados % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
}
