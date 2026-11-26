package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.MapaRindeLote
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.Carga
import com.example.ui.viewmodel.EstadoPrescripcion
import com.example.ui.viewmodel.FuenteZonas
import com.example.ui.viewmodel.ImportacionRinde
import com.example.ui.viewmodel.ImportacionSuelo
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.utils.vra.ColumnasSuelo
import com.example.utils.vra.CultivoVra
import com.example.utils.vra.EstrategiaFosforo
import com.example.utils.vra.MapaRinde
import com.example.utils.vra.MuestraSuelo
import com.example.utils.vra.ProductoFertilizante
import com.example.utils.vra.RindeZona
import com.example.utils.vra.UnidadRinde
import java.util.Locale

private val ar = Locale("es", "AR")

private fun num(v: Double, decimales: Int = 1): String = String.format(ar, "%.${decimales}f", v)

/** Campo decimal (acepta coma) que se actualiza cuando el valor cambia desde afuera. */
@Composable
internal fun CampoDecimal(
    clave: String,
    valor: Double?,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    onCambio: (Double?) -> Unit
) {
    var texto by rememberSaveable(clave) { mutableStateOf(valor?.let { num(it, 2).trimEnd('0').trimEnd(',') }.orEmpty()) }
    LaunchedEffect(valor) {
        if (texto.replace(',', '.').toDoubleOrNull() != valor) texto = valor?.let { num(it, 2).trimEnd('0').trimEnd(',') }.orEmpty()
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { t ->
            val limpio = t.filter { it.isDigit() || it == ',' || it == '.' }.take(8)
            texto = limpio
            onCambio(limpio.replace(',', '.').toDoubleOrNull())
        },
        label = { Text(etiqueta, maxLines = 1) },
        supportingText = ayuda?.let { a -> { Text(a, maxLines = 1) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

// ---------- Mapas de rinde ----------

/** Mapas de rinde del lote: importar, quitar y si se suman a las campañas. */
@Composable
internal fun SeccionMapasRinde(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel, onImportar: () -> Unit) {
    Text(
        "Mapa de rinde del monitor de la cosechadora: shapefile en ZIP (.shp, .dbf y .prj) o CSV con latitud, longitud y rinde. " +
            "Se descartan los puntos fuera del lote, los ceros y los valores atípicos.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    estado.rindes.forEach { mapa -> FilaMapaRinde(mapa) { viewModel.borrarRinde(mapa.id) } }
    if (estado.fuenteZonas == FuenteZonas.CAMPANAS && estado.rindes.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = estado.sumarRindes,
                onCheckedChange = { viewModel.alternarSumarRindes() },
                modifier = Modifier.testTag("sumar_rindes")
            )
            Spacer(Modifier.width(8.dp))
            Text("Sumar los mapas de rinde a las campañas (cada mapa cuenta como una campaña más)", style = MaterialTheme.typography.bodySmall)
        }
    }
    OutlinedButton(onClick = onImportar, modifier = Modifier.fillMaxWidth().testTag("importar_rinde")) {
        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Importar mapa de rinde")
    }
}

@Composable
private fun FilaMapaRinde(mapa: MapaRindeLote, onBorrar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(mapa.etiqueta.ifBlank { mapa.archivo }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                String.format(ar, "%,d puntos · mediana %.2f t/ha · %s", mapa.puntos.size, mapa.medianaTHa, mapa.archivo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onBorrar) { Icon(Icons.Default.Delete, contentDescription = "Quitar ${mapa.etiqueta}") }
    }
}

/** Base "Rinde": estado del cálculo con los mapas importados. */
@Composable
internal fun BaseRinde(estado: EstadoPrescripcion) {
    when (val p = estado.potencialRinde) {
        null -> Text("Importá al menos un mapa de rinde (más abajo) para usarlo como base.", style = MaterialTheme.typography.bodySmall)
        is Carga.Cargando -> Text("Calculando el rinde relativo del lote…", style = MaterialTheme.typography.bodySmall)
        is Carga.Fallo -> Text(p.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is Carga.Lista -> Text(
            "${p.valor.descripcion}. Con varios años las zonas muestran lo que se repite; con uno solo, puede pesar una campaña con sequía o granizo.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogoImportacionRinde(imp: ImportacionRinde, viewModel: PrescripcionViewModel) {
    val mediana = imp.columna?.let(imp.tabla::mediana)
    AlertDialog(
        onDismissRequest = viewModel::cancelarImportacion,
        title = { Text("Mapa de rinde") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(ar, "%s · %,d puntos", imp.archivo, imp.tabla.filas), style = MaterialTheme.typography.bodySmall)
                Text("Columna con el rinde", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    imp.tabla.columnas.keys.forEach { c ->
                        FilterChip(
                            selected = imp.columna == c,
                            onClick = {
                                val unidad = imp.tabla.mediana(c)?.let(UnidadRinde::sugerida) ?: imp.unidad
                                viewModel.cambiarImportacionRinde { it.copy(columna = c, unidad = unidad) }
                            },
                            label = { Text(c) }
                        )
                    }
                }
                Text("Unidad", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnidadRinde.entries.forEach { u ->
                        FilterChip(selected = imp.unidad == u, onClick = { viewModel.cambiarImportacionRinde { it.copy(unidad = u) } }, label = { Text(u.nombre) })
                    }
                }
                mediana?.let {
                    Text(
                        String.format(ar, "Mediana: %s %s = %.2f t/ha", num(it, 2), imp.unidad.nombre, it * imp.unidad.aTHa),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text("Cultivo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CultivoVra.entries.forEach { c ->
                        FilterChip(selected = imp.cultivo == c, onClick = { viewModel.cambiarImportacionRinde { it.copy(cultivo = c) } }, label = { Text(c.nombre) })
                    }
                }
                var campana by rememberSaveable { mutableStateOf(imp.campana) }
                OutlinedTextField(
                    value = campana,
                    onValueChange = { t -> campana = t.take(12); viewModel.cambiarImportacionRinde { it.copy(campana = campana) } },
                    label = { Text("Campaña (ej. 2024/25)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirmarImportacionRinde,
                enabled = imp.columna != null,
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                modifier = Modifier.testTag("confirmar_rinde")
            ) { Text("Usar") }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelarImportacion) { Text("Cancelar") } }
    )
}

/** Rinde medido en cada zona, por mapa, con un diagnóstico: ¿las zonas acompañan al rinde? */
@Composable
internal fun RindePorZona(estado: EstadoPrescripcion) {
    if (estado.rindes.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rinde por zona", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            estado.rindes.forEach { mapa ->
                val rz = estado.rindePorZona[mapa.id].orEmpty()
                if (rz.isEmpty()) return@forEach
                Text(mapa.etiqueta, style = MaterialTheme.typography.labelLarge)
                Text(
                    rz.joinToString(" · ") { "Z${it.zona} ${num(it.rindeTHa, 2)} t/ha" },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    diagnosticoRinde(rz, estado.fuenteZonas),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("diagnostico_rinde")
                )
            }
        }
    }
}

private fun diagnosticoRinde(rz: List<RindeZona>, fuente: FuenteZonas): String {
    if (rz.size < 2) return "Hay rinde en una sola zona: el mapa cubre poco del lote."
    val primera = rz.first()
    val ultima = rz.last()
    val dif = (ultima.rindeTHa / primera.rindeTHa - 1) * 100
    return when {
        fuente == FuenteZonas.RELIEVE ->
            if (dif >= 0) "Las lomas (zona ${ultima.zona}) rindieron ${num(dif, 0)} % más que los bajos (zona 1) esa campaña."
            else "Los bajos (zona 1) rindieron ${num(-dif, 0)} % más que las lomas (zona ${ultima.zona}) esa campaña."
        MapaRinde.zonasOrdenadas(rz) ->
            "El rinde acompaña a las zonas: la zona ${ultima.zona} rindió ${num(dif, 0)} % más que la 1. Las zonas tienen sentido para dosificar."
        else ->
            "El rinde no sigue el orden de las zonas. Probá con otra base (más campañas o el rinde) o con menos zonas antes de prescribir."
    }
}

// ---------- Análisis de suelo ----------

/** Resultado del laboratorio por zona (a mano) o por puntos de muestreo importados. */
@Composable
internal fun SeccionSuelo(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel, onImportar: () -> Unit) {
    val zon = estado.zonas ?: return
    Text(
        "Cargá el resultado del laboratorio de la muestra compuesta de cada zona, o importá una planilla (CSV) con los puntos de muestreo: " +
            "latitud, longitud y los análisis. Lo cargado a mano tiene prioridad sobre el promedio de los puntos.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val firma = estado.firmaZonas.orEmpty()
    zon.zonas.forEach { zona ->
        val manual = estado.sueloManual[zona.zona] ?: MuestraSuelo()
        val puntos = estado.sueloPorZona[zona.zona]
        Text(
            "Zona ${zona.zona}" + (puntos?.let { " · ${it.puntos} punto${if (it.puntos == 1) "" else "s"} de muestreo" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        fun ayuda(v: Double?) = v?.let { "puntos: ${num(it)}" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDecimal("suelo_${firma}_${zona.zona}_p", manual.pBrayPpm, "P Bray (ppm)", Modifier.weight(1f), ayuda(puntos?.muestra?.pBrayPpm)) {
                viewModel.cambiarSueloManual(zona.zona, manual.copy(pBrayPpm = it))
            }
            CampoDecimal("suelo_${firma}_${zona.zona}_n", manual.nNitratosKgHa, "N-NO₃ (kg/ha)", Modifier.weight(1f), ayuda(puntos?.muestra?.nNitratosKgHa)) {
                viewModel.cambiarSueloManual(zona.zona, manual.copy(nNitratosKgHa = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDecimal("suelo_${firma}_${zona.zona}_mo", manual.materiaOrganicaPct, "MO (%)", Modifier.weight(1f), ayuda(puntos?.muestra?.materiaOrganicaPct)) {
                viewModel.cambiarSueloManual(zona.zona, manual.copy(materiaOrganicaPct = it))
            }
            CampoDecimal("suelo_${firma}_${zona.zona}_ph", manual.ph, "pH", Modifier.weight(1f), ayuda(puntos?.muestra?.ph)) {
                viewModel.cambiarSueloManual(zona.zona, manual.copy(ph = it))
            }
        }
    }
    Text(
        "N-nitratos de 0 a 60 cm en kg N/ha. Si el laboratorio informa ppm por capa de 20 cm: kg/ha ≈ ppm × 2,6 en cada capa, sumando las tres.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (estado.suelo.puntos.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${estado.suelo.puntos.size} puntos de muestreo importados", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::borrarPuntosSuelo) { Text("Quitar") }
        }
    }
    OutlinedButton(onClick = onImportar, modifier = Modifier.fillMaxWidth().testTag("importar_suelo")) {
        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Importar puntos de muestreo (CSV)")
    }
}

/** Elegir qué columna de la planilla tiene un análisis ("No vino" si el laboratorio no lo midió). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElegirColumna(titulo: String, columnas: Set<String>, actual: String?, onElegir: (String?) -> Unit) {
    Text(titulo, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = actual == null, onClick = { onElegir(null) }, label = { Text("No vino") })
        columnas.forEach { c -> FilterChip(selected = actual == c, onClick = { onElegir(c) }, label = { Text(c) }) }
    }
}

@Composable
internal fun DialogoImportacionSuelo(imp: ImportacionSuelo, viewModel: PrescripcionViewModel) {
    val c = imp.columnas
    val columnas = imp.tabla.columnas.keys
    AlertDialog(
        onDismissRequest = viewModel::cancelarImportacion,
        title = { Text("Puntos de muestreo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(ar, "%s · %,d puntos", imp.archivo, imp.tabla.filas), style = MaterialTheme.typography.bodySmall)
                ElegirColumna("P Bray (ppm)", columnas, c.pBray) { viewModel.cambiarImportacionSuelo(c.copy(pBray = it)) }
                ElegirColumna("N-nitratos 0-60 cm (kg/ha)", columnas, c.nNitratos) { viewModel.cambiarImportacionSuelo(c.copy(nNitratos = it)) }
                ElegirColumna("Materia orgánica (%)", columnas, c.materiaOrganica) { viewModel.cambiarImportacionSuelo(c.copy(materiaOrganica = it)) }
                ElegirColumna("pH", columnas, c.ph) { viewModel.cambiarImportacionSuelo(c.copy(ph = it)) }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirmarImportacionSuelo,
                enabled = !c.ninguna,
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                modifier = Modifier.testTag("confirmar_suelo")
            ) { Text("Usar") }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelarImportacion) { Text("Cancelar") } }
    )
}

// ---------- Dosis sugerida ----------

/** Dosis de fertilizante sugerida por zona a partir del análisis de suelo; se puede pasar a las dosis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SugerenciaDosis(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel) {
    val p = estado.parametrosDosis
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }
    var verParametros by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dosis sugerida según el análisis de suelo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Valores de referencia de la región pampeana: verificalos con el ingeniero agrónomo. Cada zona ajusta el rinde objetivo por su productividad relativa.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Cultivo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CultivoVra.entries.forEach { c ->
                    FilterChip(selected = p.cultivo == c, onClick = { viewModel.cambiarParametros { it.conCultivo(c) } }, label = { Text(c.nombre) })
                }
            }
            Text("Producto", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProductoFertilizante.entries.forEach { pr ->
                    FilterChip(selected = p.producto == pr, onClick = { viewModel.cambiarParametros { it.conProducto(pr) } }, label = { Text(pr.nombre) })
                }
            }
            if (p.producto == ProductoFertilizante.OTRO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoDecimal("prod_n", p.nProductoPct, "N (%)", Modifier.weight(1f)) { v -> viewModel.cambiarParametros { it.copy(nProductoPct = v ?: 0.0) } }
                    CampoDecimal("prod_p", p.p2o5ProductoPct, "P₂O₅ (%)", Modifier.weight(1f)) { v -> viewModel.cambiarParametros { it.copy(p2o5ProductoPct = v ?: 0.0) } }
                }
            }
            CampoDecimal("rinde_obj_${p.cultivo}", p.rindeObjetivoTHa, "Rinde objetivo del lote (t/ha)", Modifier.fillMaxWidth()) { v ->
                v?.let { viewModel.cambiarParametros { it.copy(rindeObjetivoTHa = v) } }
            }
            Text("Fósforo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EstrategiaFosforo.entries.forEach { e ->
                    FilterChip(selected = p.estrategiaP == e, onClick = { viewModel.cambiarParametros { it.copy(estrategiaP = e) } }, label = { Text(e.nombre) })
                }
            }
            Text(p.estrategiaP.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { verParametros = !verParametros }) {
                Text(if (verParametros) "Ocultar parámetros" else "Ver y cambiar los parámetros")
            }
            if (verParametros) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoDecimal("n_por_t_${p.cultivo}", p.nPorToneladaKg, "N por t (kg)", Modifier.weight(1f), "suelo + fertilizante") { v ->
                        v?.let { viewModel.cambiarParametros { it.copy(nPorToneladaKg = v) } }
                    }
                    CampoDecimal("umbral_p_${p.cultivo}", p.umbralPBrayPpm, "Umbral Bray (ppm)", Modifier.weight(1f)) { v ->
                        v?.let { viewModel.cambiarParametros { it.copy(umbralPBrayPpm = v) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoDecimal("kg_p_ppm", p.kgPPorPpm, "kg P por ppm", Modifier.weight(1f), "para subir el Bray") { v ->
                        v?.let { viewModel.cambiarParametros { it.copy(kgPPorPpm = v) } }
                    }
                    CampoDecimal("extr_p_${p.cultivo}", p.extraccionPKgT, "Extracción P (kg/t)", Modifier.weight(1f)) { v ->
                        v?.let { viewModel.cambiarParametros { it.copy(extraccionPKgT = v) } }
                    }
                }
            }
            estado.sugerencias.forEach { s ->
                val partes = buildList {
                    add("obj. ${num(s.rindeObjetivoTHa)} t/ha")
                    s.nKgHa?.let { add("N ${num(it, 0)} kg") }
                    s.pKgHa?.let { add("P ${num(it, 0)} kg") }
                }
                Text(
                    "Zona ${s.zona}: ${partes.joinToString(" · ")}" +
                        (s.productoKgHa?.let { " → $it kg/ha" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (s.productoKgHa != null) FontWeight.SemiBold else FontWeight.Normal
                )
                s.faltante?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            Button(
                onClick = {
                    val n = viewModel.aplicarSugerencia()
                    aviso = if (n == 0) "Ninguna zona tiene los análisis que hacen falta." else "Se completaron $n zonas. Podés ajustar cada dosis abajo."
                },
                enabled = estado.sugerencias.any { it.productoKgHa != null },
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                modifier = Modifier.fillMaxWidth().testTag("aplicar_sugerencia")
            ) { Text("Completar las dosis con la sugerencia") }
            aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}
