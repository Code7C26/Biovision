package com.example.web.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.ktor.CotizacionDolar
import com.example.data.remote.ktor.ServicioFuturosA3
import com.example.utils.ContratoFuturo
import com.example.web.ui.AnchoMaximoContenido
import com.example.web.ui.Aviso
import com.example.web.ui.BotonIcono
import com.example.web.ui.Encabezado
import com.example.web.ui.ErrorConReintento
import com.example.web.ui.EspacioPanel
import com.example.web.ui.Fuente
import com.example.web.ui.IconoGrano
import com.example.web.ui.IconosGranex
import com.example.web.ui.Insignia
import com.example.web.ui.InsigniaVariacion
import com.example.web.ui.LocalMarca
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.TituloSeccion
import com.example.web.ui.dolares
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.ui.pesos
import com.example.web.ui.variacion
import com.example.web.viewmodel.EstadoCotizaciones
import com.example.web.viewmodel.EstadoFuturos
import com.example.web.viewmodel.FinanzasWebViewModel
import com.example.web.viewmodel.GranoWeb
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Ancho mínimo de una tarjeta de dólar: dos por fila en el teléfono (como Android) y las 7 en fila en un monitor. */
private val AnchoDolar = 150.dp

/** Por debajo de este ancho la tarjeta apila compra y venta en lugar de ponerlas lado a lado. */
private val AnchoDolarComodo = 240.dp

/** Ancho mínimo de la tarjeta de un grano: las tres lado a lado desde una tablet. */
private val AnchoGrano = 240.dp

/** Primero los que usa el productor (el mayorista es el de la pizarra y la exportación); la tarjeta al final. */
private val ORDEN_DOLARES = listOf("oficial", "mayorista", "bolsa", "contadoconliqui", "blue", "cripto", "tarjeta")

@Composable
fun PantallaFinanzas(vm: FinanzasWebViewModel, selector: @Composable () -> Unit = {}) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val futuros by vm.estadoFuturos.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Mercado",
            chips = listOfNotNull("Dólar y futuros", (estado as? EstadoCotizaciones.Ok)?.let { "Actualizado ${it.hora}" })
        ) {
            BotonIcono(Icons.Filled.Refresh, "Actualizar cotizaciones", vm::cargar)
        }
        selector()
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = AnchoMaximoContenido).fillMaxWidth().padding(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                // Cada sección con su estado: si falla el dólar, los futuros se siguen viendo
                SeccionDolares(estado, vm::cargar)
                SeccionFuturos(futuros, vm::cargar)
            }
        }
    }
}

@Composable
private fun SeccionDolares(estado: EstadoCotizaciones, alReintentar: () -> Unit) {
    // Se guarda la casa, no la cotización: si se actualiza con la calculadora abierta, usa el valor nuevo
    var elegida by rememberSaveable { mutableStateOf<String?>(null) }
    TituloSeccion(IconosGranex.Dolar, "Cotizaciones de divisas (Argentina)", "Compra y venta · brecha con el oficial · tocá uno para convertir")
    (estado as? EstadoCotizaciones.Ok)?.dolares?.firstOrNull { it.casa == elegida }?.let { d ->
        CalculadoraDolar(d) { elegida = null }
    }
    when (estado) {
        EstadoCotizaciones.Cargando -> GrillaPareja((1..7).toList(), AnchoDolar) { _, _, modifier ->
            PanelEsqueleto("Consultando…", lineas = 2, modifier = modifier)
        }
        is EstadoCotizaciones.Error -> ErrorConReintento(estado.mensaje, alReintentar)
        is EstadoCotizaciones.Ok -> {
            val oficial = estado.dolares.firstOrNull { it.casa == "oficial" }?.venta
            val orden = estado.dolares.sortedBy { d -> ORDEN_DOLARES.indexOf(d.casa).let { if (it < 0) ORDEN_DOLARES.size else it } }
            GrillaPareja(orden, AnchoDolar) { d, ancho, modifier ->
                TarjetaDolar(d, oficial, comoda = ancho >= AnchoDolarComodo, modifier = modifier) { elegida = d.casa }
            }
            Fuente("Fuente: dolarapi.com", "https://dolarapi.com")
        }
    }
}

/** Ajustes de A3: una tarjeta por grano con sus 3 posiciones más cercanas, como la tabla de Android. */
@Composable
private fun SeccionFuturos(estado: EstadoFuturos, alReintentar: () -> Unit) {
    val contratos = (estado as? EstadoFuturos.Ok)?.contratos.orEmpty()
    val rueda = contratos.maxOfOrNull { it.fecha }?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
    TituloSeccion(
        IconosGranex.Grafico,
        "Mercado de futuros A3 (Matba Rofex)",
        "Precios de ajuste en Rosario, en US$/t" + (rueda?.let { " · rueda del $it" } ?: ""),
        color = LocalMarca.current.acento
    )
    when (estado) {
        EstadoFuturos.Cargando -> GrillaPareja(listOf(GranoWeb.SOJA, GranoWeb.MAIZ, GranoWeb.TRIGO), AnchoGrano) { _, _, modifier ->
            PanelEsqueleto("Leyendo los ajustes de A3…", lineas = 4, modifier = modifier)
        }
        EstadoFuturos.SinServidor -> Aviso(
            "Los ajustes de A3 Mercados llegan por el servidor de GRANEX (su API no admite pedidos desde el " +
                "navegador), y esta versión no tiene servidor configurado. Están en la app Android."
        )
        is EstadoFuturos.Error -> ErrorConReintento(estado.mensaje, alReintentar)
        is EstadoFuturos.Ok -> {
            if (contratos.isEmpty()) {
                Aviso("A3 Mercados no publicó ajustes de soja, maíz ni trigo en los últimos días.")
            } else {
                GrillaPareja(contratos.groupBy { it.cultivo }.entries.toList(), AnchoGrano) { (cultivo, lista), _, modifier ->
                    TarjetaGrano(cultivo, lista, modifier)
                }
                Fuente("Fuente: ${ServicioFuturosA3.FUENTE}. Precios de cierre, no en tiempo real.", "https://a3mercados.com.ar")
            }
        }
    }
}

/**
 * Grilla sin huecos: tantas columnas como entren de [anchoMinimo], pero repartidas parejo entre las
 * filas (7 dólares en 6 columnas quedarían 6 + 1; así quedan 4 + 3). [contenido] recibe el ancho de
 * cada columna para que la tarjeta elija su forma sin medir otra vez.
 */
@Composable
private fun <T> GrillaPareja(
    elementos: List<T>,
    anchoMinimo: Dp,
    contenido: @Composable (elemento: T, anchoColumna: Dp, modifier: Modifier) -> Unit
) {
    if (elementos.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val entran = ((maxWidth + EspacioPanel) / (anchoMinimo + EspacioPanel)).toInt().coerceIn(1, elementos.size)
        val filas = (elementos.size + entran - 1) / entran
        val columnas = (elementos.size + filas - 1) / filas
        val anchoColumna = (maxWidth - EspacioPanel * (columnas - 1)) / columnas
        Column(verticalArrangement = Arrangement.spacedBy(EspacioPanel)) {
            elementos.chunked(columnas).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EspacioPanel)) {
                    fila.forEach { contenido(it, anchoColumna, Modifier.weight(1f)) }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaGrano(cultivo: String, contratos: List<ContratoFuturo>, modifier: Modifier) {
    val grano = when (cultivo) {
        "Soja" -> GranoWeb.SOJA
        "Maíz" -> GranoWeb.MAIZ
        else -> GranoWeb.TRIGO
    }
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(EspacioPanel)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoGrano(grano, tamano = 36)
                Column {
                    Text(cultivo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Entrega en Rosario · US$/t", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            contratos.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider()
                FilaFuturo(c)
            }
        }
    }
}

@Composable
private fun FilaFuturo(c: ContratoFuturo) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.posicion, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${c.simbolo} · ${numero(c.volumenLotes.toDouble())} lotes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dolares(c.ajusteUsd, 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            c.variacionPct?.let { InsigniaVariacion(it) }
        }
    }
}

/**
 * Tarjeta de un dólar, como en Android: nombre y brecha arriba, venta (el precio que importa)
 * destacada y la compra. Angosta ([comoda] = false, dos por fila en el teléfono) apila los datos.
 * Al tocarla se abre la calculadora de ese dólar, como en Android.
 */
@Composable
private fun TarjetaDolar(d: CotizacionDolar, ventaOficial: Double?, comoda: Boolean, modifier: Modifier, alTocar: () -> Unit) {
    val marca = LocalMarca.current
    val venta = d.venta
    val insignia: (@Composable () -> Unit)? = when {
        d.casa == "oficial" -> { { Insignia("Referencia", marca.primario) } }
        venta != null && ventaOficial != null && ventaOficial > 0 -> {
            val brecha = (venta / ventaOficial - 1) * 100
            { Insignia("Brecha ${variacion(brecha, 1)}", if (brecha > 0) marca.alerta else marca.positivo) }
        }
        else -> null
    }
    val hora = horaActualizacion(d.fechaActualizacion)
    Card(
        onClick = alTocar,
        modifier = modifier.mano(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(if (comoda) EspacioPanel else 12.dp), verticalArrangement = Arrangement.spacedBy(if (comoda) 10.dp else 6.dp)) {
            if (comoda) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nombreCorto(d), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    insignia?.invoke()
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Etiqueta("Compra")
                        ValorCotizacion(d.compra, destacado = false)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Etiqueta("Venta")
                        ValorCotizacion(venta, destacado = true)
                    }
                }
                hora?.let { Etiqueta("Act: $it hs", Modifier.fillMaxWidth(), alineadoAlFinal = true) }
            } else {
                Text(
                    nombreCorto(d),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                insignia?.invoke()
                Column {
                    Etiqueta("Venta")
                    ValorCotizacion(venta, destacado = true)
                }
                Etiqueta("Compra ${d.compra?.let { pesos(it, 2) } ?: "—"}")
                hora?.let { Etiqueta("Act: $it hs") }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String, modifier: Modifier = Modifier, alineadoAlFinal: Boolean = false) {
    Text(
        texto,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alineadoAlFinal) TextAlign.End else null
    )
}

/** Nombres cortos de la app Android ("Contado con liquidación" no entra en la tarjeta). */
private fun nombreCorto(d: CotizacionDolar): String = when (d.casa) {
    "bolsa" -> "Dólar MEP"
    "contadoconliqui" -> "Dólar CCL"
    else -> if (d.nombre.startsWith("Dólar")) d.nombre else "Dólar ${d.nombre}"
}

/** "2026-11-03T17:00:00.000Z" → hora local "14:00" (dolarapi informa en UTC). */
@OptIn(ExperimentalTime::class)
private fun horaActualizacion(iso: String): String? = runCatching {
    val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}.getOrNull()

/**
 * Calculadora de un dólar, como la de Android: de dólares a pesos usa la compra (lo que te pagan por
 * cada dólar que vendés) y de pesos a dólares la venta (lo que cuesta cada dólar que comprás).
 */
@Composable
private fun CalculadoraDolar(d: CotizacionDolar, alCerrar: () -> Unit) {
    var aPesos by rememberSaveable { mutableStateOf(true) }
    var texto by rememberSaveable { mutableStateOf("") }
    val marca = LocalMarca.current
    val tasa = if (aPesos) d.compra else d.venta
    val monto = leerMonto(texto)
    val resultado = if (monto != null && tasa != null && tasa > 0) (if (aPesos) monto * tasa else monto / tasa) else null
    val invalido = texto.isNotBlank() && monto == null
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Convertir con el ${nombreCorto(d)}") },
        text = {
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true to "Dólares a pesos", false to "Pesos a dólares").forEachIndexed { i, (valor, etiqueta) ->
                        SegmentedButton(
                            selected = aPesos == valor,
                            onClick = { aPesos = valor },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            modifier = Modifier.mano(),
                            icon = {}
                        ) { Text(etiqueta, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = texto,
                    onValueChange = { nuevo -> texto = nuevo.filter { it.isDigit() || it == '.' || it == ',' }.take(18) },
                    label = { Text(if (aPesos) "Monto en dólares" else "Monto en pesos") },
                    prefix = { Text(if (aPesos) "US$ " else "$ ") },
                    placeholder = { Text("Ej.: 1.500") },
                    isError = invalido,
                    supportingText = if (invalido) {
                        { Text("Usá punto para los miles y coma para los decimales: 1.500,50") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    // El campo con foco se queda con Escape y el diálogo no se cerraba
                    modifier = Modifier.fillMaxWidth().focusRequester(foco).onPreviewKeyEvent {
                        if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                            alCerrar()
                            true
                        } else false
                    }
                )
                if (tasa == null || tasa <= 0) {
                    Aviso("dolarapi no informa la ${if (aPesos) "compra" else "venta"} de este dólar.")
                } else {
                    Text(
                        if (aPesos) "Se usa la compra, ${pesos(tasa, 2)}: lo que te pagan por cada dólar que vendés."
                        else "Se usa la venta, ${pesos(tasa, 2)}: lo que cuesta cada dólar que comprás.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = marca.primario.copy(alpha = 0.10f)) {
                    Column(Modifier.padding(EspacioPanel), horizontalAlignment = Alignment.CenterHorizontally) {
                        Etiqueta(if (aPesos) "Recibís" else "Comprás")
                        BasicText(
                            resultado?.let { if (aPesos) pesos(it, 2) else dolares(it, 2) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = marca.primario),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp, stepSize = 0.5.sp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar, modifier = Modifier.mano()) { Text("Cerrar") } }
    )
}

/** "150.000" o "150000" → 150000; "1.500,50" → 1500.5; "1.5" → 1.5 (punto suelto = decimal). Null si no se entiende. */
internal fun leerMonto(texto: String): Double? {
    val t = texto.trim()
    if (t.isEmpty()) return null
    val normalizado = when {
        ',' in t -> t.replace(".", "").replace(',', '.')
        MILES.matches(t) -> t.replace(".", "")
        else -> t
    }
    return normalizado.toDoubleOrNull()?.takeIf { it >= 0 }
}

private val MILES = Regex("""\d{1,3}(\.\d{3})+""")

@Composable
private fun ValorCotizacion(valor: Double?, destacado: Boolean) {
    // Un renglón siempre: en tarjetas angostas la letra baja hasta 14 sp en lugar de cortar el número
    BasicText(
        valor?.let { pesos(it, 2) } ?: "—",
        style = (if (destacado) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Medium,
            color = if (destacado) LocalMarca.current.primario else MaterialTheme.colorScheme.onSurface
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = if (destacado) 22.sp else 18.sp, stepSize = 0.5.sp)
    )
}
