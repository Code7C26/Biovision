package com.example.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.data.remote.model.CotizacionHistorica
import com.example.data.remote.model.NivelUv
import com.example.data.remote.model.esCodigoLluvia
import com.example.web.viewmodel.GranoWeb

/*
 * Piezas gráficas de la web, con el mismo lenguaje visual que la app Android: íconos en círculos de
 * color, tarjetas de métricas, insignias de variación, íconos de granos, el medidor de UV y gráficos
 * dibujados con Canvas (sin librerías de gráficos: el .wasm no crece).
 */

/** Ícono sobre un círculo de su color al 14 % (encabezados de sección y tarjetas de métricas, como en Android). */
@Composable
fun IconoEnCirculo(icono: ImageVector, color: Color, tamano: Int = 40, descripcion: String? = null) {
    Box(
        Modifier.size(tamano.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = descripcion, tint = color, modifier = Modifier.size((tamano * 0.55f).dp))
    }
}

/** Título de sección con su ícono ("$ Cotizaciones de divisas", "Mercado de futuros"…). */
@Composable
fun TituloSeccion(icono: ImageVector, titulo: String, subtitulo: String? = null, color: Color = LocalMarca.current.primario) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconoEnCirculo(icono, color)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tarjeta de un dato del clima: ícono, título, valor grande y lectura agronómica (AgroMetricCard en Android). */
@Composable
fun TarjetaMetrica(
    icono: ImageVector,
    titulo: String,
    valor: String,
    detalle: String?,
    modifier: Modifier = Modifier,
    color: Color = LocalMarca.current.primario
) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoEnCirculo(icono, color, tamano = 34)
                Spacer(Modifier.width(10.dp))
                Text(titulo, style = MaterialTheme.typography.labelLarge, maxLines = 2)
            }
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            if (detalle != null) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

/** Variación de precio con flecha y color: verde si sube, roja si baja, gris si no cambió. */
@Composable
fun InsigniaVariacion(pct: Double, decimales: Int = 2) {
    val marca = LocalMarca.current
    val color = when {
        pct > 0.004 -> marca.positivo
        pct < -0.004 -> marca.negativo
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (color != MaterialTheme.colorScheme.onSurfaceVariant) {
                Icon(if (pct > 0) IconosGranex.Sube else IconosGranex.Baja, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(variacion(pct, decimales), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        }
    }
}

/** Color del grano según el tema (variantes clara y oscura, como en Android). */
@Composable
fun colorDe(grano: GranoWeb): Color = if (LocalTemaOscuro.current) grano.colorOscuro else grano.colorClaro

/** Ícono del grano sobre un círculo de su color. */
@Composable
fun IconoGrano(grano: GranoWeb, tamano: Int = 40) = IconoEnCirculo(grano.icono, colorDe(grano), tamano, descripcion = grano.nombre)

/** Ícono y color del tiempo según el código WMO de Open-Meteo. */
data class AspectoClima(val icono: ImageVector, val color: Color)

@Composable
fun aspectoClima(codigo: Int?, deDia: Boolean = true): AspectoClima {
    val c = codigo ?: return AspectoClima(IconosGranex.Nube, MaterialTheme.colorScheme.onSurfaceVariant)
    return when {
        c >= 95 -> AspectoClima(IconosGranex.Rayo, Color(0xFF7C3AED))
        esCodigoLluvia(c) -> AspectoClima(IconosGranex.Gota, LocalMarca.current.info)
        c in 71..86 -> AspectoClima(IconosGranex.Nube, Color(0xFF64748B))
        c >= 2 -> AspectoClima(IconosGranex.Nube, Color(0xFF94A3B8))
        !deDia -> AspectoClima(IconosGranex.Luna, Color(0xFFFDE68A))
        else -> AspectoClima(IconosGranex.Sol, Color(0xFFF59E0B))
    }
}

/**
 * Rango de temperatura del día sobre la escala de toda la semana (de [minSemana] a [maxSemana]):
 * una barra con degradé de frío a calor, como el pronóstico de los teléfonos.
 */
@Composable
fun BarraRango(minima: Double, maxima: Double, minSemana: Double, maxSemana: Double, modifier: Modifier = Modifier) {
    val pista = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier.height(6.dp)) {
        val total = (maxSemana - minSemana).takeIf { it > 0 } ?: 1.0
        val desde = ((minima - minSemana) / total).toFloat().coerceIn(0f, 1f) * size.width
        val hasta = ((maxima - minSemana) / total).toFloat().coerceIn(0f, 1f) * size.width
        val radio = CornerRadius(size.height / 2)
        drawRoundRect(pista, cornerRadius = radio)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFFFACC15), Color(0xFFF97316)), startX = 0f, endX = size.width),
            topLeft = Offset(desde, 0f),
            size = Size((hasta - desde).coerceAtLeast(size.height), size.height),
            cornerRadius = radio
        )
    }
}

/** Colores de la escala UV de la OMS (los mismos que colorUv en Android). */
fun colorUv(nivel: NivelUv): Color = when (nivel) {
    NivelUv.BAJO -> Color(0xFF2E7D32)
    NivelUv.MODERADO -> Color(0xFFB58900)
    NivelUv.ALTO -> Color(0xFFE65100)
    NivelUv.MUY_ALTO -> Color(0xFFC62828)
    NivelUv.EXTREMO -> Color(0xFF6A1B9A)
}

private const val UV_ESCALA_MAX = 14.0

/** Radiación UV a todo el ancho: valor, categoría, escala de la OMS y recomendaciones (TarjetaRadiacionUv). */
@Composable
fun TarjetaUv(actual: Double?, maximoHoy: Double?, modifier: Modifier = Modifier) {
    val valor = actual ?: maximoHoy ?: return
    val nivel = NivelUv.de(valor)
    val color = colorUv(nivel)
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconoEnCirculo(IconosGranex.Sol, color, tamano = 52)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Radiación UV", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${numero(valor, 1)} UV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(50), color = color) {
                        Text(
                            "Riesgo ${nivel.etiqueta.lowercase()}",
                            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                Text(
                    listOfNotNull(if (actual != null) "Ahora" else null, maximoHoy?.let { "máximo de hoy ${numero(it, 1)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MedidorUv(valor)
                Text(nivel.proteccion, style = MaterialTheme.typography.bodySmall)
                Text(nivel.pulverizacion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Escala segmentada de la OMS con la marca del valor actual. */
@Composable
private fun MedidorUv(valor: Double) {
    val niveles = NivelUv.entries
    val marcaColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val alto = 8.dp.toPx()
            val arriba = (size.height - alto) / 2
            niveles.forEachIndexed { i, n ->
                val desde = (n.desde / UV_ESCALA_MAX).toFloat() * size.width
                val hasta = ((niveles.getOrNull(i + 1)?.desde ?: UV_ESCALA_MAX) / UV_ESCALA_MAX).toFloat() * size.width
                drawRect(colorUv(n), topLeft = Offset(desde, arriba), size = Size(hasta - desde, alto))
            }
            val x = (valor.coerceIn(0.0, UV_ESCALA_MAX) / UV_ESCALA_MAX).toFloat() * size.width
            drawLine(marcaColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            niveles.forEach { n ->
                Text(
                    n.etiqueta,
                    Modifier.offset(x = maxWidth * (n.desde / UV_ESCALA_MAX).toFloat()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Un punto del gráfico horario: etiqueta de la hora, temperatura y lluvia de esa hora. */
data class PuntoHorario(val hora: String, val temperatura: Double?, val lluviaMm: Double)

/**
 * Próximas horas: línea de temperatura con área degradé, mínimo y máximo marcados, y barras de lluvia
 * abajo (escala propia, mínimo 2 mm para que una llovizna no parezca un diluvio).
 */
@Composable
fun GraficoHorario(puntos: List<PuntoHorario>, modifier: Modifier = Modifier) {
    if (puntos.size < 2) return
    val medidor = rememberTextMeasurer()
    val linea = LocalMarca.current.acento
    val lluvia = LocalMarca.current.info
    val textoColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fuerte = MaterialTheme.colorScheme.onSurface
    val estiloEje = MaterialTheme.typography.labelSmall.copy(color = textoColor)
    val estiloValor = MaterialTheme.typography.labelMedium.copy(color = fuerte, fontWeight = FontWeight.SemiBold)
    val temps = remember(puntos) { puntos.mapNotNull { it.temperatura } }
    if (temps.isEmpty()) return
    val minimo = temps.min()
    val maximo = temps.max()
    val maxLluvia = maxOf(2.0, puntos.maxOf { it.lluviaMm })

    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val margenX = 8.dp.toPx()
        val altoEje = 18.dp.toPx()
        val altoLluvia = 34.dp.toPx()
        val arriba = 20.dp.toPx()
        val abajoTemp = size.height - altoEje - altoLluvia - 10.dp.toPx()
        val ancho = size.width - 2 * margenX
        val paso = ancho / (puntos.size - 1)
        val rango = (maximo - minimo).takeIf { it > 0.5 } ?: 1.0
        fun x(i: Int) = margenX + i * paso
        fun y(t: Double) = (abajoTemp - ((t - minimo) / rango).toFloat() * (abajoTemp - arriba))

        // Lluvia: barras desde la base del área de lluvia
        val baseLluvia = size.height - altoEje
        puntos.forEachIndexed { i, p ->
            if (p.lluviaMm > 0.05) {
                val alto = (p.lluviaMm / maxLluvia).toFloat() * altoLluvia
                drawRoundRect(
                    lluvia.copy(alpha = 0.75f),
                    topLeft = Offset(x(i) - paso * 0.32f, baseLluvia - alto),
                    size = Size(paso * 0.64f, alto),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }

        // Temperatura: área y línea
        val camino = Path()
        var empezado = false
        puntos.forEachIndexed { i, p ->
            val t = p.temperatura ?: return@forEachIndexed
            if (!empezado) camino.moveTo(x(i), y(t)) else camino.lineTo(x(i), y(t))
            empezado = true
        }
        val area = Path().apply {
            addPath(camino)
            lineTo(x(puntos.lastIndex), abajoTemp)
            lineTo(x(0), abajoTemp)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(linea.copy(alpha = 0.28f), linea.copy(alpha = 0f)), startY = arriba, endY = abajoTemp))
        drawPath(camino, linea, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Máximo y mínimo marcados con su valor
        val iMax = puntos.indexOfFirst { it.temperatura == maximo }
        val iMin = puntos.indexOfFirst { it.temperatura == minimo }
        listOf(iMax to true, iMin to false).distinctBy { it.first }.forEach { (i, esMax) ->
            if (i < 0) return@forEach
            val c = Offset(x(i), y(puntos[i].temperatura!!))
            drawCircle(linea, radius = 4.dp.toPx(), center = c)
            val texto = medidor.measure("${numero(puntos[i].temperatura!!)}°", estiloValor)
            val tx = (c.x - texto.size.width / 2).coerceIn(0f, size.width - texto.size.width)
            val ty = if (esMax) c.y - texto.size.height - 4.dp.toPx() else c.y + 6.dp.toPx()
            drawText(texto, topLeft = Offset(tx, ty.coerceIn(0f, abajoTemp)))
        }

        // Horas: una etiqueta cada 3 h
        puntos.forEachIndexed { i, p ->
            if (i % 3 != 0) return@forEachIndexed
            val texto = medidor.measure(p.hora, estiloEje)
            val tx = (x(i) - texto.size.width / 2).coerceIn(0f, size.width - texto.size.width)
            drawText(texto, topLeft = Offset(tx, size.height - texto.size.height))
        }
    }
}

/** Leyenda chica del gráfico horario. */
@Composable
fun LeyendaHoraria() {
    val marca = LocalMarca.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 3.dp).background(marca.acento, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text("Temperatura (°C)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 8.dp, height = 10.dp).background(marca.info, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text("Lluvia (mm por hora)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ícono de alerta en círculo (riesgo de incendio). */
val IconoAlerta: ImageVector get() = Icons.Filled.Warning

/** Barra apilada de proporciones (zonas de vigor, posiciones del relieve): cada parte con su color. */
@Composable
fun BarraProporciones(partes: List<Pair<Double, Color>>, modifier: Modifier = Modifier) {
    val total = partes.sumOf { it.first }.takeIf { it > 0 } ?: return
    val pista = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        drawRect(pista)
        var x = 0f
        partes.forEach { (valor, color) ->
            val ancho = (valor / total).toFloat() * size.width
            drawRect(color, topLeft = Offset(x, 0f), size = Size(ancho, size.height))
            x += ancho
        }
    }
}

/**
 * Precio de un grano en el tiempo (US$/t): línea con área degradé del color del grano, máximo y mínimo
 * marcados con su valor, y la primera y la última fecha abajo. Los puntos ya vienen agregados
 * (AgregacionPrecios): diarios, cada 3 días o semanales según el rango.
 */
@Composable
fun GraficoPrecios(puntos: List<CotizacionHistorica>, color: Color, modifier: Modifier = Modifier) {
    if (puntos.size < 2) return
    val medidor = rememberTextMeasurer()
    val textoColor = MaterialTheme.colorScheme.onSurfaceVariant
    val guia = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val estiloEje = MaterialTheme.typography.labelSmall.copy(color = textoColor)
    val estiloValor = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val minimo = puntos.minOf { it.precioUsd }
    val maximo = puntos.maxOf { it.precioUsd }

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val margenX = 8.dp.toPx()
        val altoEje = 18.dp.toPx()
        val arriba = 22.dp.toPx()
        val abajo = size.height - altoEje - 18.dp.toPx()
        val paso = (size.width - 2 * margenX) / (puntos.size - 1)
        // Un rango mínimo de 1 % del precio: una serie casi plana no se dibuja como un serrucho
        val rango = maxOf(maximo - minimo, maximo * 0.01, 0.01)
        val base = minimo - (rango - (maximo - minimo)) / 2
        fun x(i: Int) = margenX + i * paso
        fun y(v: Double) = (abajo - ((v - base) / rango).toFloat() * (abajo - arriba))

        listOf(arriba, (arriba + abajo) / 2, abajo).forEach { gy ->
            drawLine(guia, Offset(margenX, gy), Offset(size.width - margenX, gy), strokeWidth = 1.dp.toPx())
        }
        val camino = Path().apply {
            puntos.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.precioUsd)) else lineTo(x(i), y(p.precioUsd)) }
        }
        val area = Path().apply {
            addPath(camino)
            lineTo(x(puntos.lastIndex), abajo)
            lineTo(x(0), abajo)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f)), startY = arriba, endY = abajo))
        drawPath(camino, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val iMax = puntos.indexOfFirst { it.precioUsd == maximo }
        val iMin = puntos.indexOfFirst { it.precioUsd == minimo }
        listOf(iMax to true, iMin to false).distinctBy { it.first }.forEach { (i, esMax) ->
            val c = Offset(x(i), y(puntos[i].precioUsd))
            drawCircle(color, radius = 4.dp.toPx(), center = c)
            val texto = medidor.measure(numero(puntos[i].precioUsd, 1), estiloValor)
            val tx = (c.x - texto.size.width / 2).coerceIn(0f, size.width - texto.size.width)
            val ty = if (esMax) c.y - texto.size.height - 4.dp.toPx() else c.y + 6.dp.toPx()
            drawText(texto, topLeft = Offset(tx, ty.coerceIn(0f, size.height - altoEje - texto.size.height)))
        }
        // Último punto resaltado (el precio de referencia de hoy)
        drawCircle(color, radius = 5.dp.toPx(), center = Offset(x(puntos.lastIndex), y(puntos.last().precioUsd)))

        fun fecha(c: CotizacionHistorica) = "${c.fecha.substring(8, 10)}/${c.fecha.substring(5, 7)}/${c.fecha.substring(2, 4)}"
        val desde = medidor.measure(fecha(puntos.first()), estiloEje)
        val hasta = medidor.measure(fecha(puntos.last()), estiloEje)
        drawText(desde, topLeft = Offset(0f, size.height - desde.size.height))
        drawText(hasta, topLeft = Offset(size.width - hasta.size.width, size.height - hasta.size.height))
    }
}
