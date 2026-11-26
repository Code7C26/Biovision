package com.example.web.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.utils.AnalisisRelieve
import com.example.utils.ResumenRelieve
import com.example.utils.formatoDecimal
import com.example.utils.vra.ConversionesDosis
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Labor
import com.example.utils.vra.MetodoZonificacion
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.RecomendacionZonas
import com.example.utils.vra.Temporada
import com.example.utils.vra.UbicacionFertilizante
import com.example.utils.vra.Zonificador
import com.example.web.plataforma.Navegador
import com.example.web.plataforma.TaparMarcos
import com.example.web.ui.Aviso
import com.example.web.ui.LocalMarca
import com.example.web.ui.mano
import com.example.web.viewmodel.CargaWeb
import com.example.web.viewmodel.EstadoPrescripcionWeb
import com.example.web.viewmodel.FuenteZonasWeb
import com.example.web.viewmodel.PrescripcionWebViewModel
import com.example.web.viewmodel.SentidoSiembraWeb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Prescripción de dosis variable en la web: el mismo flujo que SatelitePrescripcion.kt en Android
// (base → zonas → dosis → archivos), con la descarga directa a la computadora.

/** "#d73027" → Color. */
fun colorHex(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))

private fun num(v: Double, decimales: Int = 2) = formatoDecimal(v, decimales)

private fun formatoAncho(m: Double) = if (m % 1.0 == 0.0) m.toInt().toString() else num(m, 1)

/** Anchos de labor comunes: sembradoras (6 a 18 m) y fertilizadoras o pulverizadoras (24 y 36 m). */
private val ANCHOS_LABOR = listOf(6.0, 9.0, 12.0, 15.0, 18.0, 24.0, 36.0)

/** Todo el flujo, en secciones; [irACuenta] cuando falta la sesión. */
@Composable
fun ContenidoPrescripcion(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel, irACuenta: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            when (e.fuenteZonas) {
                FuenteZonasWeb.RELIEVE -> "Copernicus DEM GLO-30 · 10 m"
                else -> "Reflectancia Sentinel-2 L2A · 10 m (NDRE 20 m)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = LocalMarca.current.primario
        )
        Text(
            "Las zonas son relativas dentro del lote. Las dosis las define un ingeniero agrónomo con análisis de suelo e historial: la imagen no reemplaza el muestreo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val sinAcceso = e.sinAcceso
        if (sinAcceso != null) {
            Aviso(sinAcceso)
            if ("Ingresá" in sinAcceso) OutlinedButton(onClick = irACuenta, modifier = Modifier.mano()) { Text("Ingresar") }
            return@Column
        }
        Seccion("1. Base de las zonas") { BaseZonas(e, vm) }
        Seccion("2. Zonas de manejo") { Zonas(e, vm) }
        if (e.zonas != null) Seccion("3. Dosis por zona") { Dosis(e, vm) }
        if (e.prescripcion() != null) Seccion("4. Descargar para el monitor") { Descargas(e, vm) }
        Aviso(
            "Los mapas de rinde y los análisis de suelo (dosis sugerida) por ahora se cargan en la app Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LocalMarca.current.primario)
        contenido()
    }
}

@Composable
private fun Cargando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Falla(texto: String, alReintentar: (() -> Unit)? = null) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (alReintentar != null) TextButton(onClick = alReintentar, modifier = Modifier.mano()) { Text("Reintentar") }
}

@Composable
private fun Ayuda(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Opciones(titulo: String?, opciones: List<T>, actual: T, texto: (T) -> String, alElegir: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        titulo?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            opciones.forEach { o -> FilterChip(selected = o == actual, onClick = { alElegir(o) }, label = { Text(texto(o)) }, modifier = Modifier.mano()) }
        }
    }
}

// ---------- 1. Base ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseZonas(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    Opciones(null, FuenteZonasWeb.entries, e.fuenteZonas, { it.nombre }, vm::elegirFuenteZonas)
    Ayuda(e.fuenteZonas.descripcion)
    when (e.fuenteZonas) {
        FuenteZonasWeb.CAMPANAS -> BaseCampanas(e, vm)
        FuenteZonasWeb.RELIEVE -> BaseRelieve(e, vm)
        FuenteZonasWeb.FECHA -> BaseFecha(e, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseCampanas(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Temporada.entries.forEachIndexed { i, t ->
            SegmentedButton(
                selected = e.temporada == t,
                onClick = { vm.elegirTemporada(t) },
                shape = SegmentedButtonDefaults.itemShape(i, Temporada.entries.size),
                modifier = Modifier.mano(),
                icon = {}
            ) { Text(t.nombre) }
        }
    }
    Ayuda(
        "Se toma el pico de vigor de cada campaña (${e.temporada.meses}). Sacá las campañas que no representen al lote, " +
            "por ejemplo una sequía extrema o un cultivo distinto."
    )
    if (e.campanasDisponibles.isEmpty()) {
        Text("Todavía no terminó ninguna campaña con imágenes Sentinel-2.", style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        e.campanasDisponibles.forEach { c ->
            FilterChip(selected = c.etiqueta in e.campanasElegidas, onClick = { vm.alternarCampana(c.etiqueta) }, label = { Text(c.etiqueta) }, modifier = Modifier.mano())
        }
    }
    val usado = e.potencialListo?.indiceVegetacion
    Opciones(
        "Índice del pico de vigor",
        listOf(null, IndiceVegetacion.NDVI, IndiceVegetacion.NDRE),
        e.indiceCampanas,
        { i -> i?.let { "${it.nombre} · ${it.resolucionM} m" } ?: ("Automático" + (if (e.indiceCampanas == null && usado != null) " (${usado.nombre})" else "")) },
        vm::elegirIndiceCampanas
    )
    Ayuda("El NDVI se satura en cultivos densos; el NDRE mucho menos. En automático se usa NDRE cuando el NDVI pico típico del lote pasa de 0,85.")
    val resumenes = e.potencialListo?.campanas?.associateBy { it.campana.etiqueta }.orEmpty()
    val marca = LocalMarca.current
    e.campanasDisponibles.filter { it.etiqueta in e.campanasElegidas }.forEach { c ->
        val r = resumenes[c.etiqueta]
        val (texto, color) = when (val carga = e.campanas[c.etiqueta]) {
            null, CargaWeb.Cargando -> "Procesando las imágenes de la campaña…" to MaterialTheme.colorScheme.onSurfaceVariant
            is CargaWeb.Fallo -> carga.mensaje to MaterialTheme.colorScheme.error
            is CargaWeb.Lista -> when {
                r == null -> "Lista" to MaterialTheme.colorScheme.onSurfaceVariant
                r.usada -> "NDVI pico típico ${num(r.medianaNdvi ?: 0.0)} · ${num(r.coberturaPct, 0)} % del lote con dato" to marca.primario
                else -> "No se usa: ${r.motivo}" to MaterialTheme.colorScheme.error
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.campanas[c.etiqueta] == CargaWeb.Cargando) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("${c.etiqueta}: $texto", style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
    when (val p = e.potencial) {
        CargaWeb.Cargando -> Cargando("Combinando las campañas…")
        is CargaWeb.Fallo -> Falla(p.mensaje, vm::reintentar)
        is CargaWeb.Lista -> {
            val inestable = p.valor.inestablePct()
            Text(
                "Potencial productivo listo con ${p.valor.usadas.size} campañas: 1,00 es lo típico del lote; 1,10, un 10 % por encima." +
                    if (inestable >= 15) " El ${num(inestable, 0)} % del lote cambia mucho de un año a otro: esas zonas son menos confiables." else "",
                style = MaterialTheme.typography.bodySmall
            )
        }
        null -> Unit
    }
}

@Composable
private fun BaseRelieve(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    when (val r = e.relieve) {
        null, CargaWeb.Cargando -> Cargando("Calculando el relieve del lote…")
        is CargaWeb.Fallo -> Falla(r.mensaje, vm::reintentar)
        is CargaWeb.Lista -> e.resumenRelieve?.let { res ->
            Text(res.diagnostico, style = MaterialTheme.typography.bodySmall)
            Ayuda(
                "Las zonas van del punto más bajo (zona 1) al más alto. En años húmedos los bajos pueden anegarse; " +
                    "en años secos son los que más rinden: ajustá las dosis según la campaña.\n${AnalisisRelieve.AVISO_DEM}."
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseFecha(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    when (val escenas = e.escenas) {
        null, CargaWeb.Cargando -> Cargando("Buscando imágenes de los últimos 4 meses…")
        is CargaWeb.Fallo -> Falla(escenas.mensaje, vm::reintentar)
        is CargaWeb.Lista -> if (escenas.valor.isEmpty()) {
            Text("No hay imágenes en los últimos 4 meses.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Elegí una fecha con el lote despejado:", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                escenas.valor.forEach { esc ->
                    FilterChip(
                        selected = e.escena == esc,
                        onClick = { vm.elegirEscena(esc) },
                        label = {
                            Column {
                                Text(esc.fecha.substring(8, 10) + "/" + esc.fecha.substring(5, 7), fontWeight = FontWeight.SemiBold)
                                Text("${num(esc.nubesPct, 0)} % nubes", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.mano()
                    )
                }
            }
        }
    }
    Opciones("Índice para zonificar", IndiceVegetacion.entries, e.indice, { "${it.nombre} · ${it.resolucionM} m" }, vm::elegirIndice)
    Ayuda(e.indice.descripcion)
    when (val img = e.imagen) {
        CargaWeb.Cargando -> Cargando("Descargando la imagen a 10 m…")
        is CargaWeb.Fallo -> Falla(img.mensaje, vm::reintentar)
        else -> Unit
    }
    if (e.imagenSinCultivo) {
        Aviso(
            "Esta imagen es de suelo sin cultivo (barbecho o rastrojo): las zonas mostrarían diferencias de suelo y rastrojo, " +
                "no el potencial del lote. Para sembrar o fertilizar usá varias campañas.",
            color = MaterialTheme.colorScheme.error
        )
        OutlinedButton(onClick = { vm.elegirFuenteZonas(FuenteZonasWeb.CAMPANAS) }, modifier = Modifier.mano()) { Text("Usar varias campañas") }
    }
}

// ---------- 2. Zonas ----------

@Composable
private fun Zonas(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    SelectorPerfil(e.perfil, vm::elegirPerfil)
    Ayuda(e.perfil.detalle)
    val recomendada = e.recomendacion?.zonas
    Opciones(
        "Cantidad de zonas",
        (Zonificador.MIN_ZONAS..Zonificador.MAX_ZONAS).toList(),
        e.config.zonas,
        { if (it == recomendada) "$it · recomendada" else "$it" }
    ) { n -> vm.cambiarConfig { it.copy(zonas = n) } }
    e.recomendacion?.let { TextoRecomendacion(it, e.resumenRelieve.takeIf { e.fuenteZonas == FuenteZonasWeb.RELIEVE }) }
    Opciones("Método", MetodoZonificacion.entries, e.config.metodo, { it.nombre }) { m -> vm.cambiarConfig { it.copy(metodo = m) } }
    AjusteMaquina(e, vm)
    Opciones(
        if (e.config.anchoLaborM != null) "Largo de celda (a lo largo de la pasada)" else "Tamaño de celda",
        listOf(10.0, 20.0, 30.0),
        e.config.celdaM,
        { "${it.toInt()} m" }
    ) { c -> vm.cambiarConfig { it.copy(celdaM = c) } }
    if (e.config.anchoLaborM != null) AvisoRespuesta(e, vm)
    Opciones("Área mínima de cada parche", listOf(0.25, 0.5, 1.0, 2.0), e.config.areaMinimaHa, { "${num(it, if (it < 1) 2 else 0)} ha" }) { a ->
        vm.cambiarConfig { it.copy(areaMinimaHa = a) }
    }
    when (val z = e.zonificacion) {
        null -> Text(
            when (e.fuenteZonas) {
                FuenteZonasWeb.CAMPANAS -> "Las zonas se calculan cuando terminan de bajar las campañas."
                FuenteZonasWeb.RELIEVE -> "Las zonas se calculan cuando llega el relieve del lote."
                FuenteZonasWeb.FECHA -> "Elegí una imagen para calcular las zonas."
            },
            style = MaterialTheme.typography.bodySmall
        )
        CargaWeb.Cargando -> Cargando("Calculando zonas…")
        is CargaWeb.Fallo -> Falla(z.mensaje)
        is CargaWeb.Lista -> {
            val zon = z.valor
            val limite = e.perfil.maxPoligonos
            Text(
                buildString {
                    append("${zon.poligonos.size} polígonos")
                    if (limite != null) append(" (el monitor lee hasta $limite)")
                    if (zon.areaSubioPorLimite) append(". Para no pasar el límite, el área mínima subió a ${num(zon.areaMinimaAplicadaHa)} ha.")
                    if (zon.zonas.size < e.config.zonas) append(". Quedaron ${zon.zonas.size} zonas porque las demás eran muy chicas.")
                },
                style = MaterialTheme.typography.bodySmall
            )
            zon.zonas.forEach { zona ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorHex(ExportacionPrescripcion.colorZona(zona.zona, zon.zonas.size))))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Zona ${zona.zona} · ${num(zona.areaHa)} ha · ${e.nombreValor} ${num(zona.indicePromedio)} (${num(zona.indiceMin)} a ${num(zona.indiceMax)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorPerfil(actual: PerfilMonitor, alElegir: (PerfilMonitor) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().mano(), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Monitor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(actual.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("▾")
        }
        if (abierto) TaparMarcos()
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            PerfilMonitor.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.nombre) }, onClick = { alElegir(p); abierto = false }, modifier = Modifier.mano())
            }
        }
    }
}

@Composable
private fun TextoRecomendacion(rec: RecomendacionZonas, relieve: ResumenRelieve?) {
    val detalle = rec.explicada.entries.joinToString(" · ") { (k, v) -> "$k: ${num(v * 100, 0)} %" }
    Text(
        when {
            relieve != null && rec.homogeneo ->
                "El lote es plano (${num(relieve.desnivel, 1)} m de desnivel): las zonas de relieve serían de pocos centímetros y no explican el rinde. " +
                    "Usá varias campañas. Si igual querés zonas, 2 alcanzan."
            rec.homogeneo ->
                "El lote es bastante parejo (varía ${num(rec.coeficienteVariacion * 100, 0)} %): una dosis variable probablemente rinda poco acá. " +
                    "Si igual querés zonas, 2 alcanzan."
            else -> "Recomendado: ${rec.zonas} zonas, explican el ${num(rec.explicada.getValue(rec.zonas) * 100, 0)} % de la variación del lote."
        },
        style = MaterialTheme.typography.bodySmall
    )
    Ayuda("Variación explicada por cantidad de zonas: $detalle")
    if (rec.atipicosPct >= 0.5) {
        Ayuda(
            "El ${num(rec.atipicosPct, 0)} % del lote tiene valores atípicos (agua, caminos, bordes con otro uso): no se usó para definir " +
                "las zonas y quedó en la zona más parecida."
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AjusteMaquina(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    val ancho = e.config.anchoLaborM
    Opciones("Ajuste a la máquina (ancho de labor)", listOf<Double?>(null) + ANCHOS_LABOR, ancho, { a -> a?.let { "${formatoAncho(it)} m" } ?: "Sin ajustar" }, vm::cambiarAnchoLabor)
    if (ancho == null) {
        Ayuda("Cargá el ancho de labor para que las zonas sigan las pasadas: ningún parche queda más angosto que la máquina.")
        return
    }
    Opciones(
        "Sentido de siembra",
        SentidoSiembraWeb.entries,
        e.sentido,
        { s -> if (s == SentidoSiembraWeb.AUTOMATICO) "${s.nombre} (${num(s.rumbo(e.lote.vertices), 0)}°)" else s.nombre },
        vm::cambiarSentido
    )
    Ayuda(
        "Cada columna es una pasada de ${formatoAncho(ancho)} m; las celdas miden ${formatoAncho(ancho)} × ${num(e.config.celdaM, 0)} m, " +
            "alineadas a ${num(e.config.rumboGrados ?: 0.0, 0)}° del norte."
    )
}

@Composable
private fun AvisoRespuesta(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    Opciones("Velocidad de trabajo", listOf(6, 8, 10, 12), e.velocidadKmH, { "$it km/h" }, vm::cambiarVelocidad)
    Opciones("Respuesta del dosificador", listOf(1, 2, 3, 5), e.respuestaS, { "$it s" }, vm::cambiarRespuesta)
    val transicion = e.metrosTransicion
    val corta = e.config.celdaM < 2 * transicion
    Text(
        "A ${e.velocidadKmH} km/h con ${e.respuestaS} s de respuesta, la dosis tarda ${num(transicion, 0)} m en cambiar." +
            if (corta) " Con celdas de ${num(e.config.celdaM, 0)} m la máquina no llega a aplicar la dosis de los parches cortos: usá celdas más largas."
            else " Con este largo de celda hay margen.",
        style = MaterialTheme.typography.bodySmall,
        color = if (corta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------- 3. Dosis ----------

@Composable
private fun Dosis(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    val zon = e.zonas ?: return
    val altina = e.perfil == PerfilMonitor.CUFFIA_ALTINA
    val labores = if (altina) listOf(Labor.FERTILIZACION, Labor.SIEMBRA) else Labor.entries
    Opciones("Labor", labores, e.labor, { it.nombre }, vm::cambiarLabor)
    if (e.perfil.semillasCada10m && e.labor.usaFertilizante) {
        Opciones("Fertilizante", UbicacionFertilizante.entries, e.ubicacionFertilizante, { it.nombre }, vm::cambiarUbicacion)
    }
    if (e.perfil.semillasCada10m && e.labor.usaSemillas) {
        var distancia by remember { mutableStateOf(e.distanciaSurcosM.toString().replace('.', ',')) }
        OutlinedTextField(
            value = distancia,
            onValueChange = { t ->
                distancia = t
                t.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(vm::cambiarDistanciaSurcos)
            },
            label = { Text("Distancia entre surcos (m)") },
            supportingText = { Text("El monitor lee semillas cada 10 m lineales.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
    var producto by remember { mutableStateOf(e.producto) }
    OutlinedTextField(
        value = producto,
        onValueChange = { producto = it.take(40); vm.cambiarProducto(producto) },
        label = { Text(if (e.labor.usaFertilizante) "Fertilizante (ej. urea, MAP)" else "Híbrido o variedad") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    zon.zonas.forEach { zona ->
        val dosis = e.dosis[zona.zona]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorHex(ExportacionPrescripcion.colorZona(zona.zona, zon.zonas.size))))
            Column(Modifier.width(64.dp)) {
                Text("Zona ${zona.zona}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("${num(zona.areaHa, 1)} ha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.labor.usaFertilizante || altina) {
                CampoEntero("fert_${zona.zona}", dosis?.fertilizanteKgHa, if (altina) "kg/ha" else "Fert. kg/ha", Modifier.weight(1f)) {
                    vm.cambiarDosis(zona.zona, it, dosis?.semillasHa)
                }
            }
            if (e.labor.usaSemillas && !altina) {
                CampoEntero(
                    "sem_${zona.zona}", dosis?.semillasHa, "Sem/ha", Modifier.weight(1f),
                    ayuda = dosis?.semillasHa?.takeIf { e.perfil.semillasCada10m }?.let { "≈ ${ConversionesDosis.semillasCada10m(it, e.distanciaSurcosM)} sem/10 m" }
                ) { vm.cambiarDosis(zona.zona, dosis?.fertilizanteKgHa, it) }
            }
        }
    }
    e.prescripcion()?.let { p ->
        Text(
            buildString {
                if (p.labor.usaFertilizante || altina) append("Total: ${num(p.totalFertilizanteKg(), 0)} kg")
                if (p.labor.usaSemillas && !altina) {
                    if (isNotEmpty()) append(" · ")
                    append("${num(p.totalSemillas(), 0)} semillas")
                }
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CampoEntero(clave: String, valor: Int?, etiqueta: String, modifier: Modifier, ayuda: String? = null, alCambiar: (Int?) -> Unit) {
    var texto by remember(clave) { mutableStateOf(valor?.toString().orEmpty()) }
    LaunchedEffect(valor) { if (texto.toIntOrNull() != valor) texto = valor?.toString().orEmpty() }
    OutlinedTextField(
        value = texto,
        onValueChange = { t ->
            val limpio = t.filter { it.isDigit() }.take(7)
            texto = limpio
            alCambiar(limpio.toIntOrNull())
        },
        label = { Text(etiqueta) },
        supportingText = ayuda?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

// ---------- 4. Descargas ----------

@Composable
private fun Descargas(e: EstadoPrescripcionWeb, vm: PrescripcionWebViewModel) {
    val p = e.prescripcion() ?: return
    val problemas = p.problemas(e.perfil)
    problemas.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    val listo = problemas.isEmpty()
    var mensaje by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    if (e.perfil.esCuffia) {
        Ayuda("Cuffia lee los 4 archivos (.shp .shx .dbf .prj) sueltos en la raíz del pendrive o tarjeta SD, sin carpetas.")
        Button(
            onClick = {
                vm.archivosMonitor().fold(
                    { archivos ->
                        // De a uno, con una pausa: algunos navegadores frenan varias descargas juntas
                        alcance.launch {
                            archivos.forEach { a ->
                                Navegador.descargarArchivo(a.ruta, a.bytes, "application/octet-stream")
                                delay(400)
                            }
                        }
                        mensaje = "Se descargaron ${archivos.size} archivos. Copialos sueltos a la raíz del pendrive. " +
                            "Si el navegador pregunta si permite descargar varios archivos, aceptá."
                    },
                    { mensaje = it.message }
                )
            },
            enabled = listo,
            modifier = Modifier.fillMaxWidth().mano()
        ) { Text("Descargar los 4 archivos para el pendrive") }
    }
    val zip = {
        vm.zipMonitor().fold(
            { bytes ->
                Navegador.descargarArchivo(vm.nombreArchivo(".zip"), bytes, "application/zip")
                mensaje = "ZIP descargado: descomprimilo y copiá los archivos al pendrive como dice el LEEME."
            },
            { mensaje = it.message }
        )
    }
    if (e.perfil.esCuffia) {
        OutlinedButton(onClick = zip, enabled = listo, modifier = Modifier.fillMaxWidth().mano()) { Text("Descargar ZIP con instrucciones") }
    } else {
        Button(onClick = zip, enabled = listo, modifier = Modifier.fillMaxWidth().mano()) { Text("Descargar para ${e.perfil.nombre} (ZIP)") }
    }
    OutlinedButton(
        onClick = {
            vm.zipTodos().fold(
                { bytes ->
                    Navegador.descargarArchivo(vm.nombreArchivo("_todos.zip"), bytes, "application/zip")
                    mensaje = "ZIP con todos los formatos descargado."
                },
                { mensaje = it.message }
            )
        },
        enabled = listo,
        modifier = Modifier.fillMaxWidth().mano()
    ) { Text("Todos los formatos (Shapefile, ISO-XML, GeoJSON)") }
    mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalMarca.current.primario, fontWeight = FontWeight.SemiBold) }
    Ayuda("Antes de aplicar en todo el lote, probá la prescripción en una franja y verificá en el monitor que cada zona muestre su dosis.")
}
