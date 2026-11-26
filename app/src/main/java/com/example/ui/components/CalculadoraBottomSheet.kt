package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AgroGreenPrimary
import com.example.utils.DerechosExportacion
import com.example.utils.EntradaMargen
import com.example.utils.ImpuestosProvinciales
import com.example.utils.MargenAgricola
import com.example.utils.PrecioBase
import com.example.utils.ResultadoMargen
import com.example.utils.RubroCosto
import com.example.utils.UnidadPeso
import java.util.Locale
import kotlin.reflect.KProperty

private val localeAr = Locale.forLanguageTag("es-AR")

/** Texto de un campo numérico → número; acepta coma decimal. */
private fun num(texto: String): Double = texto.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

private fun fmt(v: Double, decimales: Int = 0) = String.format(localeAr, "%,.${decimales}f", v)

/** Valor para un campo editable: hasta 4 decimales con coma, sin miles ni ceros finales. */
internal fun textoCampo(v: Double): String =
    String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.').replace('.', ',')

/** Campos que son cantidades de grano (se multiplican al pasar de t a qq). */
private val CAMPOS_CANTIDAD = listOf("rinde")

/** Campos expresados por unidad de peso (US$/t o $/t: se dividen al pasar de t a qq). */
private val CAMPOS_POR_PESO = listOf(
    "precio", "fobbing", "secado", "zaranda", "fumigacion", "almacenaje", "fleteCorto", "fleteLargoUsd", "tarifaArsTn"
)

/**
 * Cambia la unidad convirtiendo una sola vez lo que el usuario ya cargó, así no se pierde nada:
 * cantidades × factor y valores por peso ÷ factor (factor = 10 de t a qq, 0,1 de qq a t).
 */
internal fun convertirCampos(campos: Map<String, String>, de: UnidadPeso, a: UnidadPeso): Map<String, String> {
    if (de == a) return campos
    val factor = UnidadPeso.factorCantidad(de, a)
    return campos.mapValues { (clave, valor) ->
        val n = valor.trim().replace(',', '.').toDoubleOrNull()
        when {
            n == null || valor.isBlank() -> valor
            clave in CAMPOS_CANTIDAD -> textoCampo(n * factor)
            clave in CAMPOS_POR_PESO -> textoCampo(n / factor)
            else -> valor
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraRendimientoBottomSheet(
    cultivo: String,
    precioInicialUsd: Double,
    tipoCambio: Double,
    onDismissRequest: () -> Unit,
    valoresGuardados: Map<String, String> = emptyMap(),
    onValorCambiado: (clave: String, valor: String) -> Unit = { _, _ -> },
    onLimpiar: () -> Unit = {},
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    costosAgenda: List<CostosAgendaLote> = emptyList()
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("calculadora_rendimiento_bottom_sheet")
    ) {
        CalculadoraMargen(cultivo, precioInicialUsd, tipoCambio, onDismissRequest, valoresGuardados, onValorCambiado, onLimpiar, costosAgenda)
    }
}

/**
 * Calculadora de margen bruto y neto por hectárea. La matemática vive en [MargenAgricola]
 * (testeada); acá solo se juntan los datos y se muestra el desglose en tiempo real.
 */
@Composable
internal fun CalculadoraMargen(
    cultivo: String,
    precioInicialUsd: Double,
    tipoCambio: Double,
    onCerrar: () -> Unit,
    valoresGuardados: Map<String, String> = emptyMap(),
    onValorCambiado: (clave: String, valor: String) -> Unit = { _, _ -> },
    onLimpiar: () -> Unit = {},
    costosAgenda: List<CostosAgendaLote> = emptyList()
) {
    // Se edita sobre un estado local (sin perder letras al tipear rápido) y cada cambio se copia
    // al ViewModel de Mercado: cerrar la hoja con atrás, tocando afuera o con "Listo" no borra nada
    val campos = remember(cultivo) { mutableStateMapOf<String, String>().apply { putAll(valoresGuardados) } }
    val alCambiar: (String, String) -> Unit = { clave, valor ->
        campos[clave] = valor
        onValorCambiado(clave, valor)
    }

    // Unidad de carga y lectura: el cálculo siempre es en toneladas
    var unidadTxt by Campo(campos, alCambiar, UnidadPeso.TONELADA.name)
    val unidad = UnidadPeso.valueOf(unidadTxt)
    val u = unidad.abreviatura
    val cambiarUnidad: (UnidadPeso) -> Unit = { nueva ->
        convertirCampos(campos.toMap(), unidad, nueva).forEach { (clave, valor) ->
            if (campos[clave] != valor) alCambiar(clave, valor)
        }
        unidadTxt = nueva.name
    }

    // Precio y tipo de cambio
    var precioBaseTxt by Campo(campos, alCambiar, PrecioBase.PIZARRA.name)
    val precioBase = PrecioBase.valueOf(precioBaseTxt)
    // Si la pizarra llega con la hoja abierta, el precio se actualiza
    var precio by Campo(campos, alCambiar, textoCampo(unidad.precioDesdeTn(precioInicialUsd)))
    var dex by Campo(campos, alCambiar, DerechosExportacion.alicuota(cultivo).toString().replace('.', ','))
    var fobbing by Campo(campos, alCambiar, "")
    var tcPropioTxt by Campo(campos, alCambiar, "false")
    val tcPropio = tcPropioTxt == "true"
    var tcTexto by Campo(campos, alCambiar, fmt(tipoCambio, 2).replace(".", ""))
    // Lote
    var superficie by Campo(campos, alCambiar, "")
    var rinde by Campo(campos, alCambiar, "")
    // Producción
    var semilla by Campo(campos, alCambiar, "")
    var fertilizantes by Campo(campos, alCambiar, "")
    var fitosanitarios by Campo(campos, alCambiar, "")
    var labores by Campo(campos, alCambiar, "")
    var cosechaPct by Campo(campos, alCambiar, "")
    // Comercialización y logística
    var comision by Campo(campos, alCambiar, "1,5")
    var secado by Campo(campos, alCambiar, "")
    var zaranda by Campo(campos, alCambiar, "")
    var fumigacion by Campo(campos, alCambiar, "")
    var almacenaje by Campo(campos, alCambiar, "")
    var fleteCorto by Campo(campos, alCambiar, "")
    var fletePorDistanciaTxt by Campo(campos, alCambiar, "false")
    val fletePorDistancia = fletePorDistanciaTxt == "true"
    var fleteLargoUsd by Campo(campos, alCambiar, "")
    var distanciaKm by Campo(campos, alCambiar, "")
    var tarifaArsTn by Campo(campos, alCambiar, "")
    // Tierra, impuestos y tasas
    var provincia by Campo(campos, alCambiar, ImpuestosProvinciales.PROVINCIAS.first().nombre)
    var iibb by Campo(campos, alCambiar, ImpuestosProvinciales.PROVINCIAS.first().iibbPct.toString().replace('.', ','))
    var sellos by Campo(campos, alCambiar, ImpuestosProvinciales.PROVINCIAS.first().sellosArrendamientoPct.toString().replace('.', ','))
    var sellosAcargo by Campo(campos, alCambiar, "50")
    var tasaVial by Campo(campos, alCambiar, "")
    var arrendamiento by Campo(campos, alCambiar, "")
    var estructura by Campo(campos, alCambiar, "")

    val tc = if (tcPropio) num(tcTexto) else tipoCambio
    // US$/t para el cálculo (la tarifa se carga en $ por la unidad elegida)
    val fleteLargo = unidad.precioATn(if (fletePorDistancia) (if (tc > 0) num(tarifaArsTn) / tc else 0.0) else num(fleteLargoUsd))

    val entrada = EntradaMargen(
        superficieHa = num(superficie),
        rindeTnHa = unidad.cantidadATn(num(rinde)),
        precioBase = precioBase,
        precioUsdTn = unidad.precioATn(num(precio)),
        dexPct = num(dex),
        fobbingUsdTn = unidad.precioATn(num(fobbing)),
        comisionPct = num(comision),
        secadoUsdTn = unidad.precioATn(num(secado)),
        zarandaUsdTn = unidad.precioATn(num(zaranda)),
        fumigacionUsdTn = unidad.precioATn(num(fumigacion)),
        almacenajeUsdTn = unidad.precioATn(num(almacenaje)),
        fleteCortoUsdTn = unidad.precioATn(num(fleteCorto)),
        fleteLargoUsdTn = fleteLargo,
        costosDirectosUsdHa = num(semilla) + num(fertilizantes) + num(fitosanitarios) + num(labores),
        cosechaPct = num(cosechaPct),
        arrendamientoUsdHa = num(arrendamiento),
        estructuraUsdHa = num(estructura),
        iibbPct = num(iibb),
        sellosPct = num(sellos),
        sellosAcargoPct = num(sellosAcargo),
        tasaVialArsHa = num(tasaVial),
        tipoCambio = tc
    )
    val r = remember(entrada) { MargenAgricola.calcular(entrada) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Calculate, contentDescription = null, tint = AgroGreenPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Margen bruto y neto · $cultivo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    campos.clear()
                    onLimpiar()
                },
                modifier = Modifier.testTag("btn_limpiar_calculadora")
            ) { Text("Limpiar") }
            IconButton(onClick = onCerrar, modifier = Modifier.testTag("btn_close_calculator")) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("selector_unidad")) {
            UnidadPeso.entries.forEachIndexed { i, opcion ->
                SegmentedButton(
                    selected = unidad == opcion,
                    onClick = { cambiarUnidad(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(i, UnidadPeso.entries.size),
                    modifier = Modifier.testTag("unidad_${opcion.name.lowercase()}")
                ) { Text(opcion.nombre) }
            }
        }
        Nota("1 tonelada = 10 quintales (1 qq = 100 kg). Rinde y precios se muestran en ${if (unidad == UnidadPeso.QUINTAL) "qq/ha y US\$/qq" else "t/ha y US\$/t"}.")

        // 1. Precio, derechos de exportación y dólar
        Seccion("Precio y retenciones", abierta = true) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PrecioBase.entries.forEachIndexed { i, base ->
                    SegmentedButton(
                        selected = precioBase == base,
                        onClick = { precioBaseTxt = base.name },
                        shape = SegmentedButtonDefaults.itemShape(i, PrecioBase.entries.size),
                        modifier = Modifier.testTag("base_${base.name.lowercase()}")
                    ) { Text(if (base == PrecioBase.PIZARRA) "Pizarra Rosario" else "Precio FOB") }
                }
            }
            CampoNumero(precio, { precio = it }, if (precioBase == PrecioBase.PIZARRA) "Precio pizarra" else "Precio FOB", "US$/$u", tag = "input_precio_usd")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(dex, { dex = it }, "Derechos de exportación", "%", Modifier.weight(1f), "input_dex")
                if (precioBase == PrecioBase.FOB) {
                    CampoNumero(fobbing, { fobbing = it }, "Gastos fobbing", "US$/$u", Modifier.weight(1f))
                }
            }
            Nota(
                if (precioBase == PrecioBase.PIZARRA)
                    "La pizarra ya viene con el DEX descontado (el exportador paga FOB × (1 − DEX)): no se resta otra vez. " +
                        "Equivale a ≈ US$ ${fmt(unidad.precioDesdeTn(r.dexImplicitoUsdTn), 2)}/$u de retención incluida en el precio."
                else "Con precio FOB se resta el DEX y los gastos de embarque para llegar al precio FAS."
            )
            Nota("Alícuota de ${DerechosExportacion.FUENTE}. Si cambia la norma, editá el porcentaje.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (tcPropio) "Dólar propio" else "Dólar oficial BNA de hoy: \$ ${fmt(tipoCambio, 2)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (tcPropio) "Usar oficial" else "Cambiar",
                    color = AgroGreenPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tcPropioTxt = (!tcPropio).toString() }.padding(8.dp)
                )
            }
            if (tcPropio) CampoNumero(tcTexto, { tcTexto = it }, "Tipo de cambio", "\$/US$", tag = "input_tipo_cambio")
        }

        // 2. Lote y producción
        Seccion("Lote y costos de producción", abierta = true) {
            // Rediseño: los costos por hectárea de cada lote salen de lo anotado en la Agenda
            if (costosAgenda.isNotEmpty()) {
                Text("Traer los costos de la Agenda:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    costosAgenda.forEach { c ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                superficie = textoCampo(c.hectareas)
                                semilla = textoCampo(c.semillaHa)
                                fertilizantes = textoCampo(c.fertilizantesHa)
                                fitosanitarios = textoCampo(c.fitosanitariosHa)
                                labores = textoCampo(c.laboresHa)
                                if (c.generalesHa > 0) estructura = textoCampo(c.generalesHa)
                            },
                            label = { Text("${c.nombre} · US$ ${fmt(c.semillaHa + c.fertilizantesHa + c.fitosanitariosHa + c.laboresHa, 0)}/ha") },
                            modifier = Modifier.testTag("agenda_lote_${c.nombre}")
                        )
                    }
                }
                Nota("Combustible y otros gastos del lote entran en labores; los gastos sin lote, en estructura.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(superficie, { superficie = it }, "Superficie", "ha", Modifier.weight(1f), "input_hectareas")
                CampoNumero(rinde, { rinde = it }, "Rinde esperado", "$u/ha", Modifier.weight(1f), "input_rendimiento")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(semilla, { semilla = it }, "Semilla", "US$/ha", Modifier.weight(1f))
                CampoNumero(fertilizantes, { fertilizantes = it }, "Fertilizantes", "US$/ha", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(fitosanitarios, { fitosanitarios = it }, "Fitosanitarios", "US$/ha", Modifier.weight(1f))
                CampoNumero(labores, { labores = it }, "Labores", "US$/ha", Modifier.weight(1f))
            }
            CampoNumero(cosechaPct, { cosechaPct = it }, "Cosecha (contratista)", "% del ingreso", tag = "input_cosecha")
        }

        // 3. Comercialización y logística
        Seccion("Comercialización y fletes") {
            CampoNumero(comision, { comision = it }, "Comisión de acopio / corretaje (1–2 %)", "%")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(secado, { secado = it }, "Secado", "US$/$u", Modifier.weight(1f))
                CampoNumero(zaranda, { zaranda = it }, "Zaranda", "US$/$u", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(fumigacion, { fumigacion = it }, "Fumigación", "US$/$u", Modifier.weight(1f))
                CampoNumero(almacenaje, { almacenaje = it }, "Almacenaje", "US$/$u", Modifier.weight(1f))
            }
            CampoNumero(fleteCorto, { fleteCorto = it }, "Flete corto (chacra → acopio)", "US$/$u")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Flete largo en US$/$u", true to "Por tarifa CATAC").forEachIndexed { i, (porDistancia, etiqueta) ->
                    SegmentedButton(
                        selected = fletePorDistancia == porDistancia,
                        onClick = { fletePorDistanciaTxt = porDistancia.toString() },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(etiqueta, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (fletePorDistancia) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoNumero(distanciaKm, { distanciaKm = it }, "Distancia a puerto", "km", Modifier.weight(1f))
                    CampoNumero(tarifaArsTn, { tarifaArsTn = it }, "Tarifa para esa distancia", "\$/$u", Modifier.weight(1f))
                }
                Nota(
                    "Buscá la tarifa de referencia para ${distanciaKm.ifBlank { "tu" }} km en la tabla vigente de CATAC/FETAC" +
                        (if (unidad == UnidadPeso.QUINTAL) " (está en \$/t: dividila por 10)" else "") +
                        ". Equivale a US$ ${fmt(unidad.precioDesdeTn(fleteLargo), 2)}/$u."
                )
            } else {
                CampoNumero(fleteLargoUsd, { fleteLargoUsd = it }, "Flete largo (acopio → puerto)", "US$/$u")
            }
        }

        // 4. Tierra, impuestos y tasas locales
        Seccion("Arrendamiento e impuestos provinciales") {
            SelectorProvincia(provincia) { elegida ->
                provincia = elegida.nombre
                iibb = elegida.iibbPct.toString().replace('.', ',')
                sellos = elegida.sellosArrendamientoPct.toString().replace('.', ',')
            }
            val datos = ImpuestosProvinciales.de(provincia)
            Nota((if (datos.confirmado) "" else "Referencia a verificar. ") + datos.nota)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(iibb, { iibb = it }, "Ingresos Brutos", "%", Modifier.weight(1f), "input_iibb")
                CampoNumero(tasaVial, { tasaVial = it }, "Tasa vial / comunal", "\$/ha", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(arrendamiento, { arrendamiento = it }, "Arrendamiento", "US$/ha", Modifier.weight(1f), "input_arrendamiento")
                CampoNumero(estructura, { estructura = it }, "Estructura / adm.", "US$/ha", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoNumero(sellos, { sellos = it }, "Sellos del arrendamiento", "%", Modifier.weight(1f))
                CampoNumero(sellosAcargo, { sellosAcargo = it }, "A tu cargo", "%", Modifier.weight(1f))
            }
        }

        ResultadoCalculadora(r, num(rinde), unidad)

        Button(
            onClick = onCerrar,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_close_calculator_action"),
            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Listo", fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

@Composable
private fun Seccion(titulo: String, abierta: Boolean = false, contenido: @Composable () -> Unit) {
    var expandida by rememberSaveable(titulo) { mutableStateOf(abierta) }
    OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expandida = !expandida }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (expandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expandida) "Contraer" else "Expandir")
        }
        AnimatedVisibility(expandida) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { contenido() }
        }
    }
}

@Composable
private fun CampoNumero(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    unidad: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    tag: String? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> onCambio(nuevo.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(etiqueta, maxLines = 1) },
        suffix = { Text(unidad, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgroGreenPrimary, focusedLabelColor = AgroGreenPrimary),
        modifier = modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

@Composable
private fun Nota(texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectorProvincia(actual: String, onElegir: (com.example.utils.ImpuestosProvincia) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().testTag("selector_provincia")) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Provincia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(actual, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir provincia")
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            ImpuestosProvinciales.PROVINCIAS.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.nombre) },
                    onClick = { onElegir(p); abierto = false }
                )
            }
        }
    }
}

private fun colorRubro(rubro: RubroCosto): Color = when (rubro) {
    RubroCosto.PRODUCCION -> Color(0xFF5C6BC0)
    RubroCosto.FLETE -> Color(0xFFE5A93C)
    RubroCosto.COMERCIALIZACION -> Color(0xFFF27E2B)
    RubroCosto.IMPUESTOS -> Color(0xFFDC4141)
    RubroCosto.ARRENDAMIENTO -> Color(0xFF8D6E63)
    RubroCosto.MARGEN -> Color(0xFF2E7D32)
}

@Composable
private fun ResultadoCalculadora(r: ResultadoMargen, rinde: Double, unidad: UnidadPeso) {
    val u = unidad.abreviatura
    // Por unidad de peso: con quintales los valores son 10 veces menores, se muestran con 2 decimales
    val dec = if (unidad == UnidadPeso.QUINTAL) 2 else 1
    fun porUnidad(usdTn: Double) = unidad.precioDesdeTn(usdTn)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("resultado_margen"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AgroGreenPrimary.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RESULTADO POR HECTÁREA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)

            // Del precio base al precio en chacra
            Text("Del precio al neto en chacra (US$/$u)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (r.dexUsdTn > 0) FilaValor("− Derechos de exportación", -porUnidad(r.dexUsdTn), dec)
            FilaValor("Precio FAS / pizarra", porUnidad(r.precioFasUsdTn), dec, destacado = true)
            FilaValor("− Comisión acopio / corretaje", -porUnidad(r.comisionUsdTn), dec)
            FilaValor("− Acondicionamiento", -porUnidad(r.acondicionamientoUsdTn), dec)
            FilaValor("− Fletes corto y largo", -porUnidad(r.fletesUsdTn), dec)
            FilaValor("Precio neto en chacra", porUnidad(r.precioChacraUsdTn), dec, destacado = true, tag = "valor_precio_chacra")
            Text(
                "En pesos: \$ ${fmt(porUnidad(r.precioChacraUsdTn) * r.tipoCambio)}/$u",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("valor_precio_chacra_ars")
            )

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica("Margen bruto", r.margenBrutoUsdHa, r.margenBrutoArsHa, "margen_bruto")
                Metrica("Margen neto", r.margenNetoUsdHa, r.margenNetoArsHa, "margen_neto")
            }
            Nota("Margen bruto = ingreso neto − costos de producción. Margen neto = bruto − arrendamiento, estructura, IIBB, sellos y tasa vial.")
            if (r.superficieHa > 0) {
                Text(
                    "Total ${fmt(r.superficieHa)} ha: producción ${fmt(rinde * r.superficieHa)} $u · " +
                        "margen bruto US$ ${fmt(r.margenBrutoTotalUsd)} · neto US$ ${fmt(r.margenNetoTotalUsd)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            Text("Rinde de indiferencia", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            FilaRinde("Cubre costos de producción", r.rindeIndiferenciaDirectosTnHa?.let(unidad::cantidadDesdeTn), rinde, u, "rinde_indiferencia_directos")
            FilaRinde("Cubre todo (con arrendamiento e impuestos)", r.rindeIndiferenciaTotalTnHa?.let(unidad::cantidadDesdeTn), rinde, u, "rinde_indiferencia_total")

            if (r.composicion.isNotEmpty()) {
                HorizontalDivider()
                Composicion(r)
            }
        }
    }
}

@Composable
private fun FilaValor(etiqueta: String, valor: Double, decimales: Int = 1, destacado: Boolean = false, tag: String? = null) {
    Row(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal,
            color = if (destacado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            // Sin "-0,0" cuando el descuento es cero
            fmt(if (kotlin.math.abs(valor) < 0.005) 0.0 else valor, decimales),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RowScope.Metrica(titulo: String, usdHa: Double, arsHa: Double, tag: String) {
    val color = if (usdHa >= 0) AgroGreenPrimary else MaterialTheme.colorScheme.error
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).padding(12.dp).testTag(tag)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("US$ ${fmt(usdHa)}/ha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text("\$ ${fmt(arsHa)}/ha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilaRinde(etiqueta: String, indiferencia: Double?, rinde: Double, u: String, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        val (texto, color) = when {
            indiferencia == null -> "Sin margen por tonelada" to MaterialTheme.colorScheme.error
            indiferencia <= 0.0 -> "Cargá costos" to MaterialTheme.colorScheme.onSurfaceVariant
            rinde <= 0 -> "${fmt(indiferencia, 2)} $u/ha" to MaterialTheme.colorScheme.onSurface
            indiferencia <= rinde -> "${fmt(indiferencia, 2)} $u/ha ✓" to AgroGreenPrimary
            else -> "${fmt(indiferencia, 2)} $u/ha" to MaterialTheme.colorScheme.error
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Barra y tabla desplegable: en qué se va cada dólar del ingreso bruto. */
@Composable
private fun Composicion(r: ResultadoMargen) {
    var detalle by rememberSaveable { mutableStateOf(false) }
    Text("¿A dónde va el ingreso bruto? (US$ ${fmt(r.ingresoBrutoUsdHa)}/ha)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).testTag("barra_composicion")
    ) {
        r.composicion.filter { it.pct > 0.05 }.forEach { p ->
            Box(Modifier.fillMaxHeight().weight(p.pct.toFloat()).background(colorRubro(p.rubro)))
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { detalle = !detalle }.padding(vertical = 4.dp).testTag("btn_detalle_composicion"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (detalle) "Ocultar desglose" else "Ver desglose por rubro", color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Icon(if (detalle) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = AgroGreenPrimary)
    }
    AnimatedVisibility(detalle) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.composicion.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorRubro(p.rubro)))
                    Spacer(Modifier.width(8.dp))
                    Text(p.rubro.etiqueta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("US$ ${fmt(p.usdHa)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${fmt(p.pct, 1)} %",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (p.pct < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(64.dp).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Campo de texto de la calculadora guardado en [campos] con el nombre de la variable como clave.
 * Mientras el usuario no lo toque vale [defecto] (así el precio sigue a la pizarra del día).
 */
private class Campo(
    private val campos: SnapshotStateMap<String, String>,
    private val alCambiar: (String, String) -> Unit,
    private val defecto: String
) {
    operator fun getValue(dueno: Any?, propiedad: KProperty<*>): String = campos[propiedad.name] ?: defecto
    operator fun setValue(dueno: Any?, propiedad: KProperty<*>, valor: String) = alCambiar(propiedad.name, valor)
}

/** Rediseño: costos por hectárea de un lote según la Agenda, para cargarlos en la calculadora. */
data class CostosAgendaLote(
    val nombre: String,
    val hectareas: Double,
    val semillaHa: Double,
    val fertilizantesHa: Double,
    val fitosanitariosHa: Double,
    /** Labores más combustible y otros gastos del lote. */
    val laboresHa: Double,
    /** Gastos sin lote repartidos por hectárea. */
    val generalesHa: Double
)
