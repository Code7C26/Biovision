package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.Carga
import com.example.ui.viewmodel.EscaneoViewModel
import com.example.ui.viewmodel.EstadoEscaneo
import com.example.ui.viewmodel.VistaEscaneo
import com.example.utils.AnalisisRelieve
import com.example.utils.ClaseVigor
import com.example.utils.CoberturaLote
import com.example.utils.EscaneoLote
import com.example.utils.FechasIso
import com.example.utils.ModoEscala
import com.example.utils.PosicionRelieve
import com.example.utils.ResumenRelieve
import com.example.utils.formatoDecimal
import com.example.utils.vra.IndiceVegetacion

/** Paleta continua del mapa (igual que RAMPAS.indice en mapa_gis.js): rojo = bajo, verde = alto. */
private val RAMPA_INDICE = listOf(
    0xFFA50026, 0xFFD73027, 0xFFF46D43, 0xFFFDAE61, 0xFFFEE08B, 0xFFD9EF8B, 0xFFA6D96A, 0xFF66BD63, 0xFF1A9850, 0xFF006837
).map { Color(it) }
private val RAMPA_CAMBIO = listOf(0xFFB2182B, 0xFFEF8A62, 0xFFFDDBC7, 0xFFF7F7F7, 0xFFD9F0D3, 0xFF7FBF7B, 0xFF1B7837).map { Color(it) }
/** Relieve (RAMPAS.relieve): azul = bajo, blanco = altura típica, marrón = loma. */
private val RAMPA_RELIEVE = listOf(
    0xFF2166AC, 0xFF4393C3, 0xFF92C5DE, 0xFFD1E5F0, 0xFFF7F7F7, 0xFFF6E8C3, 0xFFDFC27D, 0xFFBF812D, 0xFF8C510A
).map { Color(it) }
private val COLOR_POSICION = mapOf(
    PosicionRelieve.BAJO to Color(0xFF2166AC),
    PosicionRelieve.MEDIA_LOMA to Color(0xFFBDBDBD),
    PosicionRelieve.LOMA to Color(0xFF8C510A)
)

/** Escala fija del mapa de cambio entre fechas. */
private const val ESCALA_CAMBIO = 0.3

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "2026-10-05" → "5 oct". */
fun fechaCorta(fecha: String): String {
    val partes = fecha.split("-")
    val mes = partes.getOrNull(1)?.toIntOrNull()?.let { MESES.getOrNull(it - 1) } ?: return fecha
    return "${partes.getOrNull(2)?.toIntOrNull() ?: ""} $mes"
}

private fun fechaLarga(fecha: String) = "${fechaCorta(fecha)} ${fecha.take(4)}"

private fun num(v: Double, decimales: Int = 2) = formatoDecimal(v, decimales)

/**
 * Escala del mapa de relieve, simétrica alrededor de la altura típica: llega a la más lejana de las
 * puntas sin ruido (p2 / p98) y nunca a menos de medio metro, para que un lote plano no parezca
 * lleno de lomas y bajos por diferencias de centímetros.
 */
fun escalaRelieve(r: ResumenRelieve): Double = maxOf(r.mediana - r.p2, r.p98 - r.mediana, 0.5)

/**
 * Lo que dibuja el mapa según la vista: la imagen de 10 m del índice elegido (o el cambio entre dos
 * fechas) con la escala del modo, la foto en color real de la fecha o el relieve. null si todavía
 * no hay nada que mostrar.
 */
fun capaSentinelJson(estado: EstadoEscaneo?): String? {
    val e = estado ?: return null
    return when (e.vista) {
        VistaEscaneo.INDICE -> capaIndiceJson(e)
        VistaEscaneo.FOTO -> capaFotoJson(e)
        VistaEscaneo.RELIEVE -> capaRelieveJson(e)
    }
}

/** La foto del día va como PNG (ya trae su transparencia fuera de la imagen) con sus límites. */
private fun capaFotoJson(e: EstadoEscaneo): String? = (e.foto as? Carga.Lista)?.valor?.let(::fotoJson)

/** El fondo del mapa (la foto de [fotoFondo]) para `GRANEX.setFondo`. */
fun fondoSentinelJson(foto: com.example.data.remote.supabase.ImagenSentinelRemota): String = fotoJson(foto)

/**
 * Fondo del mapa alrededor del lote: la foto en color real de la fecha elegida (cubre la zona, no
 * solo el lote). Con el relieve, que no tiene fecha, queda el mapa base.
 */
fun fotoFondo(estado: EstadoEscaneo?): com.example.data.remote.supabase.ImagenSentinelRemota? {
    val e = estado ?: return null
    if (e.vista == VistaEscaneo.RELIEVE) return null
    return (e.foto as? Carga.Lista)?.valor
}

private fun fotoJson(foto: com.example.data.remote.supabase.ImagenSentinelRemota): String {
    val sb = StringBuilder(foto.png.size * 4 / 3 + 400)
    sb.append("{\"capa\":\"s2foto\",\"id\":").append(org.json.JSONObject.quote("foto|${foto.fecha}"))
    sb.append(",\"etiqueta\":\"Color real\"")
    sb.append(",\"fecha\":").append(org.json.JSONObject.quote("Sentinel-2 · ${fechaLarga(foto.fecha)}"))
    sb.append(",\"imagen\":\"data:image/png;base64,").append(android.util.Base64.encodeToString(foto.png, android.util.Base64.NO_WRAP)).append('"')
    sb.append(",\"limites\":{\"oeste\":").append(foto.oeste).append(",\"sur\":").append(foto.sur)
        .append(",\"este\":").append(foto.este).append(",\"norte\":").append(foto.norte).append("}}")
    return sb.toString()
}

/** Altura de cada píxel respecto de la típica del lote, en metros. */
private fun capaRelieveJson(e: EstadoEscaneo): String? {
    val relieve = (e.relieve as? Carga.Lista)?.valor ?: return null
    val r = e.resumenRelieve ?: return null
    val escala = escalaRelieve(r)
    val adentro = AnalisisRelieve.adentro(relieve.grilla, e.lote.coordenadas)
    val valores = FloatArray(relieve.altura.size) { (relieve.altura[it] - r.mediana).toFloat() }
    return jsonGrilla(
        id = "relieve|${relieve.grilla.oeste}|${relieve.grilla.norte}",
        rampa = "relieve",
        etiqueta = "Altura",
        pie = "respecto de la típica del lote (${num(r.mediana, 0)} m s. n. m.)",
        min = -escala,
        max = escala,
        grilla = relieve.grilla,
        valores = valores,
        adentro = adentro,
        extra = ",\"signo\":true,\"unidad\":\"m\""
    )
}

private fun capaIndiceJson(e: EstadoEscaneo): String? {
    val imagen = e.imagenLista ?: return null
    val comparacion = e.comparacion
    val g = imagen.grilla
    val valores: FloatArray
    val min: Double
    val max: Double
    val id: String
    val etiqueta: String
    val fecha: String
    if (comparacion != null) {
        valores = comparacion.diferencia
        min = -ESCALA_CAMBIO
        max = ESCALA_CAMBIO
        id = "cambio|${comparacion.fechaAnterior}|${comparacion.fechaNueva}|${e.indice}"
        etiqueta = "Cambio de ${e.indice.nombre}"
        fecha = "${fechaLarga(comparacion.fechaAnterior)} → ${fechaLarga(comparacion.fechaNueva)}"
    } else {
        val escala = e.resumen?.escala(e.modo) ?: EscaneoLote.escalaAbsoluta(e.indice).let { if (e.modo == ModoEscala.ABSOLUTA) it else return null }
        valores = imagen.valores(e.indice)
        min = escala.minimo
        max = escala.maximo
        id = "${imagen.fecha}|${e.indice}|${e.modo}"
        etiqueta = e.indice.nombre
        fecha = "Sentinel-2 · ${fechaLarga(imagen.fecha)}"
    }
    // Igual que en las hectáreas: solo los píxeles con el centro dentro del lote
    val adentro = EscaneoLote.dentroDelLote(imagen, e.lote.coordenadas)
    return jsonGrilla(
        id = id,
        rampa = if (comparacion != null) "cambio" else "indice",
        etiqueta = etiqueta,
        pie = fecha,
        min = min,
        max = max,
        grilla = g,
        valores = valores,
        adentro = adentro,
        extra = if (comparacion != null) ",\"signo\":true" else ""
    )
}

private fun jsonGrilla(
    id: String,
    rampa: String,
    etiqueta: String,
    pie: String,
    min: Double,
    max: Double,
    grilla: com.example.utils.vra.GrillaGeografica,
    valores: FloatArray,
    adentro: BooleanArray,
    extra: String
): String {
    val g = grilla
    val sb = StringBuilder(valores.size * 6 + 400)
    sb.append("{\"capa\":\"s2\",\"id\":").append(org.json.JSONObject.quote(id))
    sb.append(",\"rampa\":\"").append(rampa).append('"')
    sb.append(",\"etiqueta\":").append(org.json.JSONObject.quote(etiqueta))
    sb.append(",\"fecha\":").append(org.json.JSONObject.quote(pie))
    sb.append(extra)
    sb.append(",\"escala\":{\"min\":").append(min).append(",\"max\":").append(max).append('}')
    sb.append(",\"geo\":{\"oeste\":").append(g.oeste).append(",\"norte\":").append(g.norte)
        .append(",\"pasoLon\":").append(g.pasoLon).append(",\"pasoLat\":").append(g.pasoLat)
        .append(",\"columnas\":").append(g.columnas).append(",\"filas\":").append(g.filas).append('}')
    sb.append(",\"valores\":[")
    valores.forEachIndexed { i, v ->
        if (i > 0) sb.append(',')
        if (v.isNaN() || !adentro[i]) sb.append("null") else sb.append(Math.round(v * 1000) / 1000.0)
    }
    sb.append("]}")
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarjetaEscaneoSentinel(
    estado: EstadoEscaneo?,
    viewModel: EscaneoViewModel,
    onIrAAjustes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("tarjeta_escaneo_s2"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Escaneo satelital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
            EtiquetaOrigen(
                OrigenDato.HISTORICO_REAL,
                if (estado?.vista == VistaEscaneo.RELIEVE) "Copernicus DEM GLO-30 · 10 m"
                else "Sentinel-2 L2A · píxeles de 10 m · sin nubes ni sombras"
            )

            when {
                estado == null -> Text(
                    "Elegí o creá un lote para ver sus imágenes satelitales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                !estado.disponible -> {
                    Text(estado.mensajeSinConfigurar, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onIrAAjustes, colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)) { Text("Ir a Ajustes") }
                        OutlinedButton(onClick = viewModel::reintentar) { Text("Ya ingresé") }
                    }
                }
                else -> ContenidoEscaneo(estado, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContenidoEscaneo(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    // Qué muestra el mapa: el índice, la foto en color real de la fecha o el relieve del lote
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("vista_escaneo")) {
        VistaEscaneo.entries.forEachIndexed { i, vista ->
            SegmentedButton(
                selected = e.vista == vista,
                onClick = { viewModel.elegirVista(vista) },
                shape = SegmentedButtonDefaults.itemShape(i, VistaEscaneo.entries.size)
            ) { Text(vista.nombre, maxLines = 1) }
        }
    }
    if (e.vista == VistaEscaneo.RELIEVE) {
        SeccionRelieve(e, viewModel)
        return
    }

    when (val escenas = e.escenas) {
        Carga.Cargando -> Cargando("Buscando imágenes de los últimos 6 meses…")
        is Carga.Fallo -> Fallo(escenas.mensaje, viewModel::reintentar)
        is Carga.Lista -> {
            val utiles = e.escenasUtiles
            if (utiles.isEmpty()) {
                Text(
                    "No hay imágenes con el lote despejado en los últimos 6 meses (${escenas.valor.size} pasadas, todas con nubes).",
                    style = MaterialTheme.typography.bodySmall
                )
                return
            }
            val dudosas = e.fechasDudosas

            // Fechas
            Text("Fecha (${utiles.size} sin nubes)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(utiles, key = { it.fecha }) { escena ->
                    val dudosa = escena.fecha in dudosas
                    FilterChip(
                        selected = escena.fecha == e.fecha,
                        onClick = { viewModel.elegirFecha(escena.fecha) },
                        label = {
                            Text(
                                if (dudosa) "${fechaCorta(escena.fecha)} · posible neblina"
                                else "${fechaCorta(escena.fecha)} · ${num(escena.nubesPct, 0)} % nubes"
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AgroGreenPrimary.copy(alpha = 0.18f),
                            labelColor = if (dudosa) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
            if (e.fecha != null && e.fecha in dudosas) {
                Text(
                    "Esta fecha parece tener neblina o nubes finas que el filtro no detectó: el índice bajó y volvió a subir " +
                        "en pocos días. Revisá la foto del día antes de sacar conclusiones.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("aviso_neblina")
                )
            }

            if (e.vista == VistaEscaneo.FOTO) SeccionFoto(e, viewModel) else SeccionIndice(e, viewModel)

            Serie(e, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeccionIndice(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        IndiceVegetacion.entries.forEachIndexed { i, indice ->
            SegmentedButton(
                selected = e.indice == indice,
                onClick = { viewModel.elegirIndice(indice) },
                shape = SegmentedButtonDefaults.itemShape(i, IndiceVegetacion.entries.size)
            ) { Text(indice.nombre) }
        }
    }
    Text(e.indice.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    when (val imagen = e.imagen) {
        null, Carga.Cargando -> Cargando("Descargando la imagen del ${e.fecha?.let(::fechaCorta) ?: ""}…")
        is Carga.Fallo -> Fallo(imagen.mensaje) { e.fecha?.let(viewModel::elegirFecha) }
        is Carga.Lista -> {
            val comparacion = e.comparacion
            if (comparacion == null) {
                // Escala de colores
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ModoEscala.entries.forEachIndexed { i, modo ->
                        SegmentedButton(
                            selected = e.modo == modo,
                            onClick = { viewModel.elegirModo(modo) },
                            shape = SegmentedButtonDefaults.itemShape(i, ModoEscala.entries.size)
                        ) { Text(modo.nombre) }
                    }
                }
                Text(e.modo.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.resumen?.let { r ->
                    val escala = r.escala(e.modo)
                    Leyenda(
                        RAMPA_INDICE, num(escala.minimo), num(escala.maximo), "${e.indice.nombre} bajo", "${e.indice.nombre} alto",
                        medio = if (e.modo == ModoEscala.RELATIVA) "típico ${num(r.mediana)}" else null
                    )
                }
            } else {
                Leyenda(RAMPA_CAMBIO, "−" + num(ESCALA_CAMBIO), "+" + num(ESCALA_CAMBIO), "Bajó", "Subió", medio = "sin cambio")
            }
            Text(
                "Tocá el lote en el mapa para ver el valor de cada punto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            e.resumen?.let { r ->
                Resumen(r)
            }

            Comparar(e, viewModel)
        }
    }
}

/** Foto en color real de la fecha: para ver lo que el índice no explica. */
@Composable
private fun SeccionFoto(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    when (val foto = e.foto) {
        null, Carga.Cargando -> Cargando("Descargando la foto del ${e.fecha?.let(::fechaCorta) ?: ""}…")
        is Carga.Fallo -> Fallo(foto.mensaje) { e.fecha?.let(viewModel::elegirFecha) }
        is Carga.Lista -> Text(
            "Color real del ${fechaLarga(foto.valor.fecha)} (Sentinel-2, 10 m), con un margen alrededor del lote. " +
                "Sirve para ver lo que el índice no explica: agua, cosecha, rastrojo, caminos o nubes finas.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("foto_escaneo")
        )
    }
}

/** Relieve del lote (Copernicus DEM): bajos, media loma y lomas en hectáreas. */
@Composable
private fun SeccionRelieve(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    when (val relieve = e.relieve) {
        null, Carga.Cargando -> Cargando("Calculando el relieve del lote…")
        is Carga.Fallo -> Fallo(relieve.mensaje) { viewModel.elegirVista(VistaEscaneo.RELIEVE) }
        is Carga.Lista -> {
            val r = e.resumenRelieve
            if (r == null) {
                Text("El modelo de elevación no tiene datos dentro del lote.", style = MaterialTheme.typography.bodySmall)
                return
            }
            val escala = escalaRelieve(r)
            Leyenda(
                RAMPA_RELIEVE, "−${num(escala)} m", "+${num(escala)} m", "Bajo", "Loma",
                medio = "típica ${num(r.mediana, 0)} m"
            )
            Text(
                "Tocá el lote en el mapa para ver la altura de cada punto respecto de la típica.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(r.diagnostico, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diagnostico_relieve"))
            Text(
                "Altura típica ${num(r.mediana, 1)} m s. n. m. · desnivel ${num(r.desnivel, 1)} m · pendiente media ${num(r.pendienteMediaPct, 1)} %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            r.clases.forEach { clase ->
                val color = COLOR_POSICION.getValue(clase.posicion)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(clase.posicion.nombre, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(Modifier.fillMaxWidth((clase.porcentaje / 100).toFloat().coerceIn(0f, 1f)).height(8.dp).background(color))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${num(clase.hectareas, 1)} ha · ${num(clase.porcentaje, 0)} %",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(112.dp)
                    )
                }
            }
            Text(
                "Bajo y loma: más de ${num(r.umbral)} m por debajo o por encima de la altura típica. Copernicus DEM (30 m) " +
                    "remuestreado a 10 m: muestra las formas del terreno, no diferencias de pocos centímetros. Es la altura " +
                    "de la superficie: montes, árboles y galpones suman la suya y aparecen como lomas. " +
                    "Para zonificar por relieve, elegí «Relieve» como base en la prescripción.\n${AnalisisRelieve.AVISO_DEM}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Resumen(r: com.example.utils.ResumenEscaneo) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.cobertura?.let { cobertura ->
            val color = when (cobertura) {
                CoberturaLote.SUELO_DESNUDO -> Color(0xFF8D6E63)
                CoberturaLote.CULTIVO_INICIAL -> Color(0xFFF9A825)
                CoberturaLote.CULTIVO_ACTIVO -> AgroGreenPrimary
            }
            Text(
                cobertura.nombre,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(r.diagnostico, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diagnostico_escaneo"))
        Text(
            "${r.indice.nombre} típico ${num(r.mediana)} · rango ${num(r.p2)} a ${num(r.p98)} · ${num(r.hectareasConDato, 1)} ha con dato",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Zonas en hectáreas
        r.zonas.forEach { zona ->
            val color = when (zona.clase) {
                ClaseVigor.BAJO -> RAMPA_INDICE[1]
                ClaseVigor.MEDIO -> RAMPA_INDICE[4]
                ClaseVigor.ALTO -> RAMPA_INDICE[8]
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(zona.clase.nombre, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.fillMaxWidth((zona.porcentaje / 100).toFloat().coerceIn(0f, 1f)).height(8.dp).background(color))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${num(zona.hectareas, 1)} ha · ${num(zona.porcentaje, 0)} %",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(112.dp)
                )
            }
        }
        Text(
            "Bajo y alto: más de ${num(EscaneoLote.UMBRAL_ZONA)} por debajo o por encima del valor típico del lote.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Comparar(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    val otras = e.escenasUtiles.filter { it.fecha != e.fecha }
    if (otras.isEmpty()) return
    Text("Comparar con otra fecha", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = e.fechaComparar == null,
                onClick = { viewModel.compararCon(null) },
                label = { Text("Sin comparar") }
            )
        }
        items(otras, key = { "c" + it.fecha }) { escena ->
            FilterChip(
                selected = e.fechaComparar == escena.fecha,
                onClick = { viewModel.compararCon(escena.fecha) },
                label = { Text(fechaCorta(escena.fecha)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AgroGreenPrimary.copy(alpha = 0.18f))
            )
        }
    }
    when (val img = e.imagenComparar) {
        Carga.Cargando -> Cargando("Descargando la imagen para comparar…")
        is Carga.Fallo -> Text(img.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        else -> e.comparacion?.let { c ->
            val signo = if (c.cambioMedio > 0) "+" else ""
            Text(
                "De ${fechaCorta(c.fechaAnterior)} a ${fechaCorta(c.fechaNueva)}: ${c.indice.nombre} $signo${num(c.cambioMedio)} en promedio. " +
                    "Subió en ${num(c.hectareasSubio, 1)} ha, bajó en ${num(c.hectareasBajo, 1)} ha y se mantuvo en ${num(c.hectareasEstable, 1)} ha.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("comparacion_escaneo")
            )
        }
    }
}

/** Promedio del índice en el lote en cada fecha despejada; tocar un punto abre esa fecha. */
@Composable
private fun Serie(e: EstadoEscaneo, viewModel: EscaneoViewModel) {
    val puntos = e.escenasUtiles.mapNotNull { escena -> escena.valor(e.indice)?.let { escena.fecha to it } }.sortedBy { it.first }
    if (puntos.size < 2) return
    Text("Evolución del ${e.indice.nombre} promedio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    val primera = puntos.first().first
    val totalDias = FechasIso.diasEntre(primera, puntos.last().first).coerceAtLeast(1)
    val minimo = (puntos.minOf { it.second } - 0.05).coerceAtLeast(-0.2)
    val maximo = (puntos.maxOf { it.second } + 0.05).coerceAtMost(1.0)
    val colorLinea = AgroGreenPrimary
    val colorSeleccion = MaterialTheme.colorScheme.onSurface
    val colorGuia = MaterialTheme.colorScheme.outlineVariant
    val colorDudosa = MaterialTheme.colorScheme.outline
    // Las fechas con posible neblina quedan fuera de la línea: no son una caída real del cultivo
    val dudosas = e.fechasDudosas
    val linea = puntos.filter { it.first !in dudosas }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag("serie_escaneo")
            .pointerInput(puntos) {
                detectTapGestures { toque ->
                    val x = toque.x / size.width
                    puntos.minByOrNull { kotlin.math.abs(FechasIso.diasEntre(primera, it.first).toFloat() / totalDias - x) }
                        ?.let { viewModel.elegirFecha(it.first) }
                }
            }
    ) {
        fun pos(fecha: String, valor: Double) = Offset(
            x = FechasIso.diasEntre(primera, fecha).toFloat() / totalDias * (size.width - 16f) + 8f,
            y = ((maximo - valor) / (maximo - minimo)).toFloat() * (size.height - 16f) + 8f
        )
        drawLine(colorGuia, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1f)
        for (i in 1 until linea.size) {
            drawLine(colorLinea, pos(linea[i - 1].first, linea[i - 1].second), pos(linea[i].first, linea[i].second), strokeWidth = 4f)
        }
        puntos.forEach { (fecha, valor) ->
            val p = pos(fecha, valor)
            if (fecha in dudosas) drawCircle(colorDudosa, radius = 6f, center = p, style = Stroke(width = 2.5f))
            else drawCircle(colorLinea, radius = 7f, center = p)
            if (fecha == e.fecha) drawCircle(colorSeleccion, radius = 12f, center = p, style = Stroke(width = 3f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fechaCorta(puntos.first().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${e.indice.nombre} ${num(minimo)} – ${num(maximo)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fechaCorta(puntos.last().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (dudosas.isNotEmpty()) {
        Text(
            "○ Posible neblina (${dudosas.size} ${if (dudosas.size == 1) "fecha" else "fechas"}): fuera de la línea.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("leyenda_neblina")
        )
    }
}

@Composable
private fun Leyenda(colores: List<Color>, minimo: String, maximo: String, textoMin: String, textoMax: String, medio: String? = null) {
    Column(Modifier.fillMaxWidth().testTag("leyenda_escaneo")) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(colores)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$minimo · $textoMin", style = MaterialTheme.typography.labelSmall)
            // El centro de la paleta: el valor típico del lote o "sin cambio"
            medio?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("$textoMax · $maximo", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Cargando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
        Spacer(Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Fallo(mensaje: String, reintentar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = reintentar) { Text("Reintentar") }
    }
}
