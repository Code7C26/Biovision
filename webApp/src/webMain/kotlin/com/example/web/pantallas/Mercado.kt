package com.example.web.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.ktor.PizarraHoy
import com.example.utils.DerechosExportacion
import com.example.utils.ImpuestosProvinciales
import com.example.utils.MargenAgricola
import com.example.utils.ResultadoMargen
import com.example.web.ui.Aviso
import com.example.web.ui.IconoGrano
import com.example.web.ui.InsigniaVariacion
import com.example.web.ui.colorDe
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.web.ui.ClaseAncho
import com.example.web.ui.Dato
import com.example.web.ui.Encabezado
import com.example.web.ui.ErrorConReintento
import com.example.web.ui.Fuente
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.Tablero
import com.example.web.ui.dolares
import com.example.web.ui.numero
import com.example.web.ui.pesos
import com.example.web.ui.variacion
import com.example.web.ui.BotonIcono
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.mano
import com.example.web.ui.desplazableHorizontal
import com.example.web.viewmodel.CamposMargen
import com.example.web.viewmodel.EstadoHistorial
import com.example.web.viewmodel.EstadoPizarra
import com.example.web.viewmodel.RangoHistorial
import com.example.utils.AgregacionPrecios
import com.example.utils.FilaHistorial
import com.example.web.ui.GraficoPrecios
import com.example.web.viewmodel.GranoWeb
import com.example.web.viewmodel.MercadoWebViewModel
import com.example.web.viewmodel.entradaMargen

/**
 * Mercado: pizarra de Rosario del día (granos.ar) y calculadora de margen con el modelo común
 * MargenAgricola (el mismo de Android). Teléfono: una columna. Tablet: pizarra | calculadora.
 * Escritorio: pizarra | datos | resultado.
 */
@Composable
fun PantallaMercado(vm: MercadoWebViewModel, selector: @Composable () -> Unit = {}) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val grano by vm.grano.collectAsStateWithLifecycle()
    val todos by vm.campos.collectAsStateWithLifecycle()
    val campos = todos[grano].orEmpty()
    val pizarra = (estado as? EstadoPizarra.Ok)?.pizarra
    val historial by vm.historial.collectAsStateWithLifecycle()
    val rango by vm.rango.collectAsStateWithLifecycle()
    val referencias = remember(pizarra, historial) { preciosDeReferencia(pizarra, historial) }
    val resultado = remember(campos, grano, referencias, pizarra) {
        MargenAgricola.calcular(entradaMargen(campos, grano, referencias[grano]?.usdTn, pizarra?.tipoCambio))
    }

    val panelPizarra: @Composable () -> Unit = {
        Panel("Pizarra Rosario", subtitulo = pizarra?.let { "Rueda del ${it.fecha.substring(8, 10)}/${it.fecha.substring(5, 7)}" }) {
            when (val e = estado) {
                EstadoPizarra.Cargando -> PanelEsqueleto("Leyendo la pizarra…", lineas = 5)
                is EstadoPizarra.Error -> ErrorConReintento(e.mensaje, vm::cargar)
                is EstadoPizarra.Ok -> TablaPizarra(e.pizarra, grano, referencias, vm::elegirGrano)
            }
            Fuente("Fuente: granos.ar (${pizarra?.fuentePrecios ?: "Consiagro / BCR Rosario"})", "https://granos.ar")
        }
    }
    val panelHistorial: @Composable () -> Unit = { PanelHistorial(historial, grano, rango, vm::elegirRango, vm::cargar) }
    val panelDatos: @Composable () -> Unit = { PanelDatosMargen(vm, grano, campos, referencias[grano]) }
    val panelResultado: @Composable () -> Unit = { PanelResultadoMargen(grano, resultado) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Mercado",
            chips = listOfNotNull("Rosario", pizarra?.tipoCambio?.let { "BNA divisas ${pesos(it, 2)}" })
        ) {
            BotonIcono(Icons.Filled.Refresh, "Actualizar pizarra", vm::cargar)
        }
        selector()
        Tablero(
            when (LocalClaseAncho.current) {
                ClaseAncho.EXPANDIDA -> listOf(listOf(panelPizarra, panelHistorial), listOf(panelDatos), listOf(panelResultado))
                else -> listOf(listOf(panelPizarra, panelHistorial), listOf(panelDatos, panelResultado))
            }
        )
    }
}

/** Precio de un grano para mostrar y calcular; [fechaAnterior] no es null si no es el de la rueda del día. */
private data class PrecioReferencia(val usdTn: Double, val fechaAnterior: String?)

/**
 * La pizarra del día o, si ese día un grano no tiene precio (la Cámara todavía no lo publicó o no hubo
 * rueda), la última rueda con precio del historial. Así la calculadora no queda con US$ 0.
 */
private fun preciosDeReferencia(pizarra: PizarraHoy?, historial: EstadoHistorial): Map<GranoWeb, PrecioReferencia> =
    GranoWeb.entries.mapNotNull { g ->
        val hoy = pizarra?.granos?.get(g.clave)?.usdTn?.let { PrecioReferencia(it, null) }
        val anterior = (historial as? EstadoHistorial.Ok)?.series?.get(g)?.lastOrNull()?.let { PrecioReferencia(it.precioUsd, it.fecha) }
        (hoy ?: anterior)?.let { g to it }
    }.toMap()

private fun fechaCorta(fecha: String) = "${fecha.substring(8, 10)}/${fecha.substring(5, 7)}"

@Composable
private fun TablaPizarra(p: PizarraHoy, elegido: GranoWeb, referencias: Map<GranoWeb, PrecioReferencia>, alElegir: (GranoWeb) -> Unit) {
    val marca = LocalMarca.current
    Column {
        GranoWeb.entries.forEachIndexed { i, g ->
            val precio = p.granos[g.clave]
            if (i > 0) HorizontalDivider()
            val elegidoAhora = g == elegido
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (elegidoAhora) colorDe(g).copy(alpha = 0.08f) else Color.Transparent)
                    .mano().clickable { alElegir(g) }.padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconoGrano(g, tamano = 42)
                Column(Modifier.weight(1f)) {
                    Text(
                        g.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (elegidoAhora) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (elegidoAhora) colorDe(g) else MaterialTheme.colorScheme.onSurface
                    )
                    val anterior = referencias[g]?.fechaAnterior
                    val ars = precio?.arsTn
                    when {
                        ars != null -> Text(
                            pesos(ars) + "/t",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        anterior != null -> Text(
                            "Sin precio hoy · rueda del ${fechaCorta(anterior)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        referencias[g]?.let { dolares(it.usdTn, 2) + "/t" } ?: "Sin precio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    precio?.variacionPct?.let { InsigniaVariacion(it) }
                }
            }
        }
    }
}

/**
 * Historial del grano elegido en la pizarra: gráfico y tabla con AgregacionPrecios (la misma lógica que
 * Mercado en Android), sobre la serie de granos.ar pasada a US$ con el dólar mayorista de cada rueda.
 */
@Composable
private fun PanelHistorial(
    estado: EstadoHistorial,
    grano: GranoWeb,
    rango: RangoHistorial,
    alElegirRango: (RangoHistorial) -> Unit,
    alReintentar: () -> Unit
) {
    val serie = (estado as? EstadoHistorial.Ok)?.series?.get(grano).orEmpty()
    val agregada = remember(serie, rango) { AgregacionPrecios.agregar(serie, rango.granularidad) }
    Panel("Historial · ${grano.nombre}", subtitulo = "Pizarra de Rosario en US$/t") {
        Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RangoHistorial.entries.forEach { r ->
                FilterChip(selected = r == rango, onClick = { alElegirRango(r) }, label = { Text(r.etiqueta) }, modifier = Modifier.mano())
            }
        }
        when {
            estado is EstadoHistorial.Cargando -> PanelEsqueleto("Leyendo el historial…", lineas = 4)
            estado is EstadoHistorial.Error -> ErrorConReintento(estado.mensaje, alReintentar)
            agregada.puntos.size < 2 -> Aviso("No hay suficientes ruedas de ${grano.nombre} en este rango para dibujar la serie.")
            else -> {
                val primero = agregada.puntos.first().precioUsd
                val ultimo = agregada.puntos.last()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${dolares(ultimo.precioUsd, 1)}/t", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Rueda del ${ultimo.fecha.substring(8, 10)}/${ultimo.fecha.substring(5, 7)} · ${pesos(ultimo.precioArs)}/t",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        InsigniaVariacion((ultimo.precioUsd - primero) / primero * 100.0)
                        Text("en ${rango.etiqueta}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GraficoPrecios(agregada.puntos, colorDe(grano))
                Text(rango.granularidad.leyendaGrafico, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                agregada.filas.take(12).forEachIndexed { i, fila ->
                    if (i > 0) HorizontalDivider()
                    FilaTablaHistorial(fila)
                }
            }
        }
        Fuente("Fuente: granos.ar (Cámara Arbitral de Rosario) · dólar mayorista: ArgentinaDatos", "https://granos.ar")
    }
}

@Composable
private fun FilaTablaHistorial(fila: FilaHistorial) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(fila.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            fila.detalle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (fila.ruedas > 1) {
                Text(
                    "Mín ${numero(fila.minUsd, 1)} · Máx ${numero(fila.maxUsd, 1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dolares(fila.cierreUsd, 1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            fila.variacionPct?.let { InsigniaVariacion(it) }
        }
    }
}

@Composable
private fun PanelDatosMargen(vm: MercadoWebViewModel, grano: GranoWeb, campos: Map<String, String>, referencia: PrecioReferencia?) {
    Panel("Calculadora de margen", subtitulo = "${grano.nombre} · valores por hectárea en US$") {
        Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GranoWeb.entries.forEach { g ->
                FilterChip(
                    selected = g == grano,
                    onClick = { vm.elegirGrano(g) },
                    label = { Text(g.nombre) },
                    leadingIcon = { Icon(g.icono, contentDescription = null, tint = colorDe(g), modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.mano()
                )
            }
        }
        CampoNumero("Superficie (ha)", campos[CamposMargen.SUPERFICIE]) { vm.cambiarCampo(CamposMargen.SUPERFICIE, it) }
        CampoNumero("Rinde esperado (t/ha)", campos[CamposMargen.RINDE]) { vm.cambiarCampo(CamposMargen.RINDE, it) }
        CampoNumero(
            "Precio (US$/t)",
            campos[CamposMargen.PRECIO],
            ayuda = referencia?.let { r ->
                val cual = r.fechaAnterior?.let { "última pizarra con precio, del ${fechaCorta(it)}" } ?: "pizarra de hoy"
                "Vacío = $cual (${dolares(r.usdTn, 2)}). El DEX ya viene descontado."
            }
        ) { vm.cambiarCampo(CamposMargen.PRECIO, it) }
        CampoNumero("Insumos y labores (US$/ha)", campos[CamposMargen.DIRECTOS]) { vm.cambiarCampo(CamposMargen.DIRECTOS, it) }
        CampoNumero("Cosecha (% del ingreso)", campos[CamposMargen.COSECHA]) { vm.cambiarCampo(CamposMargen.COSECHA, it) }
        CampoNumero("Comisión (%)", campos[CamposMargen.COMISION]) { vm.cambiarCampo(CamposMargen.COMISION, it) }
        CampoNumero("Flete corto (US$/t)", campos[CamposMargen.FLETE_CORTO]) { vm.cambiarCampo(CamposMargen.FLETE_CORTO, it) }
        CampoNumero("Flete largo (US$/t)", campos[CamposMargen.FLETE_LARGO]) { vm.cambiarCampo(CamposMargen.FLETE_LARGO, it) }
        CampoNumero("Arrendamiento (US$/ha)", campos[CamposMargen.ARRENDAMIENTO]) { vm.cambiarCampo(CamposMargen.ARRENDAMIENTO, it) }
        CampoNumero("Estructura (US$/ha)", campos[CamposMargen.ESTRUCTURA]) { vm.cambiarCampo(CamposMargen.ESTRUCTURA, it) }
        Text("Provincia (IIBB y sellos de referencia)", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ImpuestosProvinciales.PROVINCIAS.forEach { p ->
                val elegida = campos[CamposMargen.IIBB] == p.iibbPct.toString().replace('.', ',') &&
                    campos[CamposMargen.SELLOS] == p.sellosArrendamientoPct.toString().replace('.', ',')
                FilterChip(selected = elegida, onClick = { vm.elegirProvincia(p.nombre) }, label = { Text(p.nombre) }, modifier = Modifier.mano())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("IIBB (%)", campos[CamposMargen.IIBB], Modifier.weight(1f)) { vm.cambiarCampo(CamposMargen.IIBB, it) }
            CampoNumero("Sellos (%)", campos[CamposMargen.SELLOS], Modifier.weight(1f)) { vm.cambiarCampo(CamposMargen.SELLOS, it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DEX ${grano.nombre}: ${numero(DerechosExportacion.alicuota(grano.clave), 1)} % (${DerechosExportacion.FUENTE})",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = vm::limpiar, modifier = Modifier.mano()) { Text("Limpiar") }
        }
    }
}

@Composable
private fun CampoNumero(
    etiqueta: String,
    valor: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    ayuda: String? = null,
    alCambiar: (String) -> Unit
) {
    OutlinedTextField(
        value = valor.orEmpty(),
        onValueChange = { nuevo -> alCambiar(nuevo.filter { it.isDigit() || it == ',' || it == '.' }) },
        modifier = modifier,
        label = { Text(etiqueta) },
        singleLine = true,
        supportingText = ayuda?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun PanelResultadoMargen(grano: GranoWeb, r: ResultadoMargen) {
    val marca = LocalMarca.current
    fun color(v: Double) = if (v >= 0) marca.positivo else marca.negativo
    // Sin rinde no hay ingreso: en vez de una tabla de US$ 0 se dice qué falta completar
    val sinRinde = r.ingresoBrutoUsdHa <= 0.0
    Panel("Resultado · ${grano.nombre}", subtitulo = "Con el modelo común MargenAgricola (igual que en Android)") {
        val fondo = if (sinRinde) MaterialTheme.colorScheme.onSurface else color(r.margenNetoUsdHa)
        Surface(shape = RoundedCornerShape(12.dp), color = fondo.copy(alpha = if (sinRinde) 0.05f else 0.10f)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Margen neto", style = MaterialTheme.typography.labelLarge)
                if (sinRinde) {
                    Text("—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Completá el rinde esperado y los costos para calcular el margen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${dolares(r.margenNetoUsdHa)}/ha",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color(r.margenNetoUsdHa)
                    )
                    if (r.superficieHa > 0) Text("Total: ${dolares(r.margenNetoTotalUsd)} en ${numero(r.superficieHa)} ha")
                    if (r.tipoCambio > 1) Text("≈ ${pesos(r.margenNetoArsHa)}/ha", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!sinRinde) {
            Dato("Ingreso bruto", "${dolares(r.ingresoBrutoUsdHa)}/ha")
        }
        Dato("Precio en chacra", "${dolares(r.precioChacraUsdTn, 1)}/t")
        if (!sinRinde) {
            Dato("Ingreso neto", "${dolares(r.ingresoNetoUsdHa)}/ha")
            Dato("Costos de producción", "${dolares(r.costosProduccionUsdHa)}/ha")
            Dato("Margen bruto", "${dolares(r.margenBrutoUsdHa)}/ha", color(r.margenBrutoUsdHa))
            Dato("Impuestos locales", "${dolares(r.impuestosLocalesUsdHa)}/ha")
            Dato("Arrendamiento + estructura", "${dolares(r.arrendamientoUsdHa + r.estructuraUsdHa)}/ha")
            HorizontalDivider()
            Dato("Rinde de indiferencia (directos)", r.rindeIndiferenciaDirectosTnHa?.let { "${numero(it, 2)} t/ha" } ?: "Sin margen por tonelada")
            Dato("Rinde de indiferencia (total)", r.rindeIndiferenciaTotalTnHa?.let { "${numero(it, 2)} t/ha" } ?: "Sin margen por tonelada")
        }
        if (r.dexImplicitoUsdTn > 0) {
            Dato("DEX implícito en la pizarra (informativo)", "${dolares(r.dexImplicitoUsdTn, 1)}/t")
        }
        Aviso(
            "Versión web simplificada. La calculadora completa (precio FOB, secado y acondicionamiento, quintales, " +
                "flete por distancia, tasa vial) está en la app Android y pasa a la web junto con la UI común."
        )
    }
}
