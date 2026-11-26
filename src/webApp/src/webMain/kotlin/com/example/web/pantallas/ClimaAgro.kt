package com.example.web.pantallas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.utils.BalanceHidrico
import com.example.utils.EstacionMeteo
import com.example.utils.EstadoHidrico
import com.example.utils.HoraPulverizacion
import com.example.utils.NivelHelada
import com.example.utils.PronosticoAgro
import com.example.utils.PronosticoHelada
import com.example.utils.PronosticoPulverizacion
import com.example.utils.VentanaPulverizacion
import com.example.utils.formatoDecimal
import com.example.web.ui.IconoEnCirculo
import com.example.web.ui.IconosGranex
import com.example.web.ui.Panel
import com.example.web.ui.mano

// Herramientas agronómicas sobre el pronóstico, como ClimaAgronomico.kt en Android (lógica en
// :shared utils/PronosticoAgro): ventana de pulverización, helada con hora y balance hídrico.

private val COLOR_BUENA = Color(0xFF2E7D32)
private val COLOR_PRECAUCION = Color(0xFFE09B00)
private val COLOR_NO = Color(0xFFC62828)
private val COLOR_ET0 = Color(0xFFE65100)
private val COLOR_LLUVIA = Color(0xFF0EA5E9)

private fun colorAptitud(a: EstacionMeteo.Aptitud) = when (a) {
    EstacionMeteo.Aptitud.BUENA -> COLOR_BUENA
    EstacionMeteo.Aptitud.PRECAUCION -> COLOR_PRECAUCION
    EstacionMeteo.Aptitud.NO_RECOMENDADA -> COLOR_NO
}

private fun textoAptitud(a: EstacionMeteo.Aptitud) = when (a) {
    EstacionMeteo.Aptitud.BUENA -> "Buena"
    EstacionMeteo.Aptitud.PRECAUCION -> "Con precaución"
    EstacionMeteo.Aptitud.NO_RECOMENDADA -> "No conviene"
}

/** "hoy", "mañana" o "el jueves", para usar dentro de una frase. */
private fun cuando(dia: Int, hoy: Long): String = when (dia) {
    0 -> "hoy"
    1 -> "mañana"
    else -> "el " + PronosticoAgro.nombreDia(dia, hoy + dia).lowercase()
}

private fun mm(v: Double) = formatoDecimal(v, 0) + " mm"

private fun signo(v: Double) = (if (v > 0.5) "+" else "") + mm(v)

@Composable
private fun TarjetaAgro(icono: ImageVector, color: Color, titulo: String, subtitulo: String, contenido: @Composable () -> Unit) {
    Panel(titulo, subtitulo = subtitulo) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { contenido() }
            Spacer(Modifier.width(8.dp))
            IconoEnCirculo(icono, color, tamano = 36)
        }
    }
}

@Composable
private fun NotaModelo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

// --- Ventana de pulverización ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelPulverizacion(p: PronosticoPulverizacion) {
    var elegida by rememberSaveable { mutableStateOf<Long?>(null) }
    val seleccion = p.horas.firstOrNull { it.epochS == elegida } ?: p.horas.first()
    Panel("Ventana de pulverización", subtitulo = "Próximas 48 horas, hora por hora") {
        Text(resumenVentana(p), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Column {
            // Una fila por día con las 24 horas; las que ya pasaron o quedan fuera del pronóstico, vacías
            p.horas.groupBy { it.dia }.forEach { (dia, horasDia) ->
                val porHora = horasDia.associateBy { it.hora }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PronosticoAgro.nombreDia(dia, p.hoy + dia),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(72.dp),
                        maxLines = 1
                    )
                    Row(Modifier.weight(1f)) {
                        for (h in 0 until 24) {
                            val celda = porHora[h]
                            val activa = celda != null && celda.epochS == seleccion.epochS
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .padding(horizontal = 0.5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(celda?.let { colorAptitud(it.aptitud) } ?: Color.Transparent)
                                    .then(
                                        if (activa) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                                        else Modifier
                                    )
                                    .then(
                                        if (celda != null) {
                                            Modifier
                                                .mano()
                                                .clickable(onClickLabel = "Ver detalle") { elegida = celda.epochS }
                                                .semantics {
                                                    contentDescription = "${PronosticoAgro.nombreDia(dia, p.hoy + dia)} " +
                                                        "${PronosticoAgro.hora(h)}: ${textoAptitud(celda.aptitud)}"
                                                }
                                        } else Modifier
                                    )
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(72.dp))
                Row(Modifier.weight(1f)) {
                    listOf("00", "06", "12", "18").forEach {
                        Text(
                            it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EstacionMeteo.Aptitud.entries.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorAptitud(a)))
                    Spacer(Modifier.width(4.dp))
                    Text(textoAptitud(a), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        DetalleHora(seleccion, p.hoy)
        val proximas = p.ventanas.take(4)
        if (proximas.size > 1) {
            Column {
                Text("Ventanas buenas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                proximas.forEach { v ->
                    Text("${tramoVentana(v, p.hoy, enFrase = false)} · ${v.horas} h", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        NotaModelo(
            "Delta T, viento y lluvia estimados por el pronóstico ECMWF para la localidad. " +
                "Antes de aplicar, confirmá las condiciones en el lote."
        )
    }
}

private fun resumenVentana(p: PronosticoPulverizacion): String {
    val ahora = p.horas.first()
    val enCurso = p.ventanas.firstOrNull { it.desdeS == ahora.epochS }
    if (enCurso != null) return "Ahora hay buenas condiciones, hasta las ${PronosticoAgro.hora(enCurso.horaHasta)}."
    val proxima = p.ventanas.firstOrNull()
        ?: return "No hay ventanas buenas de al menos ${PronosticoAgro.HORAS_MINIMAS_VENTANA} horas en las próximas 48 horas."
    return "Próxima ventana: ${tramoVentana(proxima, p.hoy, enFrase = true)} (${proxima.horas} h)."
}

/** "hoy de 08:00 a 12:00", o "hoy 19:00 a mañana 02:00" si pasa la medianoche. */
private fun tramoVentana(v: VentanaPulverizacion, hoy: Long, enFrase: Boolean): String {
    fun nombre(dia: Int) = if (enFrase) cuando(dia, hoy) else PronosticoAgro.nombreDia(dia, hoy + dia)
    val diaFin = v.dia + ((v.hastaS - 1 - v.desdeS) / 3600 + v.horaDesde).toInt() / 24
    return if (diaFin == v.dia) {
        "${nombre(v.dia)} de ${PronosticoAgro.hora(v.horaDesde)} a ${PronosticoAgro.hora(v.horaHasta)}"
    } else {
        val fin = PronosticoAgro.nombreDia(diaFin, hoy + diaFin).lowercase()
        "${nombre(v.dia)} ${PronosticoAgro.hora(v.horaDesde)} a $fin ${PronosticoAgro.hora(v.horaHasta)}"
    }
}

@Composable
private fun DetalleHora(h: HoraPulverizacion, hoy: Long) {
    val color = colorAptitud(h.aptitud)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "${PronosticoAgro.nombreDia(h.dia, hoy + h.dia)} ${PronosticoAgro.hora(h.hora)} · ${textoAptitud(h.aptitud)}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            val datos = buildList {
                h.deltaT?.let { add("Delta T ${formatoDecimal(it, 1)}") }
                h.vientoKmh?.let { v ->
                    add("viento ${formatoDecimal(v, 0)} km/h" + (h.rafagaKmh?.let { " (ráfagas ${formatoDecimal(it, 0)})" } ?: ""))
                }
                if (h.lluviaMm >= PronosticoAgro.LLUVIA_HORA_MM) add("lluvia ${formatoDecimal(h.lluviaMm, 1)} mm")
            }
            Text(datos.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            h.motivos.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Helada ---

@Composable
fun PanelHelada(h: PronosticoHelada) {
    val color = when (h.nivel) {
        NivelHelada.HELADA -> MaterialTheme.colorScheme.error
        NivelHelada.RIESGO -> Color(0xFF1E88E5)
        NivelHelada.SIN_RIESGO -> COLOR_LLUVIA
    }
    val dias = (h.horasAnalizadas + 23) / 24
    TarjetaAgro(
        IconosGranex.Termometro, color,
        when (h.nivel) {
            NivelHelada.HELADA -> "Helada pronosticada"
            NivelHelada.RIESGO -> "Riesgo de helada"
            NivelHelada.SIN_RIESGO -> "Sin riesgo de helada"
        },
        "Próximos $dias días"
    ) {
        Text(
            "Mínima de ${formatoDecimal(h.minimaC, 1)} °C ${cuando(h.diaMinima, h.hoy)} a las ${PronosticoAgro.hora(h.horaMinima)}.",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
        )
        val desde = h.desdeHora
        val hasta = h.hastaHora
        if (h.nivel != NivelHelada.SIN_RIESGO && desde != null && hasta != null) {
            val bajoCero = if (h.horasBajoCero > 0) ", ${h.horasBajoCero} h bajo cero" else ""
            Text(
                "A 3 °C o menos de ${PronosticoAgro.hora(desde)} a ${PronosticoAgro.hora(hasta)} (${h.horasRiesgo} h$bajoCero).",
                style = MaterialTheme.typography.bodySmall
            )
            if (h.nochesConRiesgo > 1) {
                Text("Hay ${h.nochesConRiesgo} noches con riesgo en este período.", style = MaterialTheme.typography.bodySmall)
            }
            NotaModelo(
                "Es la temperatura del aire a 2 m. Con cielo despejado y sin viento, a ras del suelo y en " +
                    "los bajos puede hacer de 2 a 4 °C menos."
            )
        }
    }
}

// --- Balance hídrico ---

@Composable
fun PanelBalanceHidrico(b: BalanceHidrico) {
    val (textoEstado, colorEstado) = when (b.estado) {
        EstadoHidrico.SIN_DEFICIT -> "Las lluvias cubrieron la demanda" to COLOR_BUENA
        EstadoHidrico.DEFICIT_MODERADO -> "Déficit moderado" to COLOR_PRECAUCION
        EstadoHidrico.DEFICIT_MARCADO -> "Déficit marcado" to COLOR_NO
    }
    TarjetaAgro(IconosGranex.Gota, COLOR_LLUVIA, "Balance hídrico", "Lluvia frente a evapotranspiración (ET0)") {
        Surface(shape = RoundedCornerShape(20.dp), color = colorEstado.copy(alpha = 0.14f)) {
            Text(
                "Últimos ${b.diasPasados} días: $textoEstado",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    Panel("Lluvia y ET0 por día", subtitulo = "Últimos ${b.diasPasados} días y próximos ${b.diasProximos}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BloqueBalance(
                "Últimos ${b.diasPasados} días",
                listOf(
                    "Lluvia" to mm(b.lluviaPasadaMm), "ET0" to mm(b.et0PasadaMm), "Balance" to signo(b.balancePasadoMm),
                    "Últ. 7 días" to mm(b.lluviaUltimos7Mm)
                ),
                Modifier.weight(1f)
            )
            BloqueBalance(
                "Próximos ${b.diasProximos} días",
                listOf("Lluvia" to mm(b.lluviaProximaMm), "ET0" to mm(b.et0ProximaMm), "Balance" to signo(b.balanceProximoMm)),
                Modifier.weight(1f)
            )
        }
        GraficoBalance(b)
        NotaModelo(
            "Lluvia estimada por el modelo ECMWF, no medida: en lluvias de tormenta puede diferir mucho de tu " +
                "pluviómetro. ET0 es la demanda de un pasto de referencia: un cultivo cerrado consume algo más y " +
                "uno recién emergido bastante menos. No cuenta el agua guardada en el perfil."
        )
    }
}

@Composable
private fun BloqueBalance(titulo: String, filas: List<Pair<String, String>>, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        filas.forEach { (etiqueta, valor) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(etiqueta, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(valor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false)
            }
        }
    }
}

/** Barras de lluvia por día (las del pronóstico más claras) y la ET0 de cada día como línea. */
@Composable
private fun GraficoBalance(b: BalanceHidrico) {
    val dias = b.dias
    if (dias.isEmpty()) return
    val maximo = maxOf(dias.maxOf { it.lluviaMm ?: 0.0 }, dias.maxOf { it.et0Mm ?: 0.0 }, 1.0)
    val colorGuia = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val colorHoy = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(90.dp).semantics {
                contentDescription = "Lluvia diaria de los últimos ${b.diasPasados} días y los próximos ${b.diasProximos}: " +
                    "${mm(b.lluviaPasadaMm)} caídos frente a ${mm(b.et0PasadaMm)} de evapotranspiración"
            }
        ) {
            val paso = size.width / dias.size
            val alto = size.height
            drawLine(colorGuia, Offset(0f, alto), Offset(size.width, alto), 1.dp.toPx())
            dias.forEachIndexed { i, d ->
                val ll = d.lluviaMm ?: 0.0
                if (ll > 0) {
                    val h = (ll / maximo).toFloat() * alto
                    val color = if (i >= b.indiceHoy) COLOR_LLUVIA.copy(alpha = 0.45f) else COLOR_LLUVIA
                    drawRect(color, Offset(i * paso + paso * 0.15f, alto - h), Size(paso * 0.7f, h))
                }
            }
            var anterior: Offset? = null
            dias.forEachIndexed { i, d ->
                val e = d.et0Mm ?: return@forEachIndexed
                val p = Offset(i * paso + paso / 2, alto - (e / maximo).toFloat() * alto)
                anterior?.let { drawLine(COLOR_ET0, it, p, 2.dp.toPx()) }
                anterior = p
            }
            val xHoy = b.indiceHoy * paso
            drawLine(colorHoy, Offset(xHoy, 0f), Offset(xHoy, alto), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val inicio = b.hoy - b.indiceHoy
            Text(PronosticoAgro.fechaDiaMes(inicio), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(COLOR_LLUVIA))
                Text(" Lluvia   ", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.width(12.dp).height(2.dp).background(COLOR_ET0))
                Text(" ET0", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                PronosticoAgro.fechaDiaMes(inicio + dias.size - 1),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Las herramientas que se pudieron calcular, en el orden de Android: pulverización, helada, balance. */
@Composable
fun PanelesHerramientasAgro(agro: com.example.utils.HerramientasAgro?) {
    agro ?: return
    agro.pulverizacion?.let { PanelPulverizacion(it) }
    agro.helada?.let { PanelHelada(it) }
    agro.balance?.let { PanelBalanceHidrico(it) }
}
