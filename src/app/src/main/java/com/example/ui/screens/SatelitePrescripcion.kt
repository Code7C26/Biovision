package com.example.ui.screens

import com.example.ui.viewmodel.FuenteZonas
import com.example.ui.viewmodel.SentidoSiembra
import com.example.utils.AnalisisRelieve
import com.example.utils.ResumenRelieve
import com.example.utils.formatoDecimal
import com.example.utils.vra.RecomendacionZonas
import com.example.utils.vra.Temporada
import com.example.utils.vra.Zonificador
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.EnlaceExterno
import com.example.ui.components.EnlacesRegistro
import com.example.ui.components.detachFromParent
import com.example.ui.components.mapa.GisMapController
import com.example.ui.components.mapa.jsString
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.Carga
import com.example.ui.viewmodel.EstadoPrescripcion
import com.example.ui.viewmodel.PrescripcionViewModel
import com.example.utils.vra.ConversionesDosis
import com.example.utils.vra.ExportacionPrescripcion
import com.example.utils.vra.GuardadoArchivos
import com.example.utils.vra.IndiceVegetacion
import com.example.utils.vra.Labor
import com.example.utils.vra.MetodoZonificacion
import com.example.utils.vra.PerfilMonitor
import com.example.utils.vra.UbicacionFertilizante
import com.example.utils.vra.ImportadorCampo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val localeAr = Locale.forLanguageTag("es-AR")

private fun colorDe(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** Flujo de prescripción de dosis variable a pantalla completa: imagen → zonas → dosis → exportar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaPrescripcion(
    estado: EstadoPrescripcion,
    viewModel: PrescripcionViewModel,
    gisMap: GisMapController?,
    onCerrar: () -> Unit,
    onIrAAjustes: () -> Unit
) {
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    // Rediseño: la prescripción en 3 pasos (zonas → dosis → exportar), una parte por vez
    var pasoElegido by rememberSaveable { mutableStateOf(0) }
    val paso = when {
        pasoElegido >= 2 && estado.prescripcion() == null -> if (estado.zonas != null) 1 else 0
        pasoElegido >= 1 && estado.zonas == null -> 0
        else -> pasoElegido
    }

    // Al volver de Ajustes con credenciales nuevas se cargan las escenas
    LaunchedEffect(Unit) { if (!estado.configurada) viewModel.reintentar() }

    // Zonas en el mapa: solo se reenvían cuando cambia la zonificación, no al tipear dosis
    val zonasJson = remember(estado.zonas) { estado.prescripcion()?.let { ExportacionPrescripcion.geojson(it, conColor = true) } }
    LaunchedEffect(gisMap, zonasJson) {
        gisMap?.runScript("GRANEX.setZonas(${zonasJson?.let(::jsString) ?: "null"});")
    }
    DisposableEffect(gisMap) {
        onDispose { gisMap?.runScript("GRANEX.setZonas(null);") }
    }

    val elegirCarpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            mensaje = viewModel.archivosMonitor().fold(
                onSuccess = { archivos ->
                    GuardadoArchivos.enCarpeta(context, uri, archivos).fold(
                        { n -> "Se guardaron $n archivos. Llevá el pendrive al monitor." },
                        { "No se pudo guardar: ${it.message}" }
                    )
                },
                onFailure = { it.message }
            )
        }
    }
    // Mapas de rinde y puntos de muestreo: CSV, TXT o shapefile en ZIP (cada administrador de
    // archivos les pone otro tipo, así que se acepta cualquiera y el importador decide)
    fun importar(alLeer: (String, com.example.utils.vra.TablaCampo) -> Unit): (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            alcance.launch {
                withContext(Dispatchers.IO) { ImportadorCampo.leer(context, uri) }
                    .fold({ (nombre, tabla) -> alLeer(nombre, tabla) }, { viewModel.avisoArchivo(it.message) })
            }
        }
    }
    val abrirRinde = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), importar(viewModel::importarRinde))
    val abrirSuelo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), importar(viewModel::importarSuelo))
    LaunchedEffect(estado.mensajeArchivo) {
        estado.mensajeArchivo?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.avisoArchivo(null)
        }
    }
    estado.importacionRinde?.let { DialogoImportacionRinde(it, viewModel) }
    estado.importacionSuelo?.let { DialogoImportacionSuelo(it, viewModel) }

    var zipPendiente by remember { mutableStateOf<ByteArray?>(null) }
    val crearZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = zipPendiente
        zipPendiente = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        alcance.launch {
            mensaje = GuardadoArchivos.enDocumento(context, uri, bytes).fold({ "ZIP guardado." }, { "No se pudo guardar: ${it.message}" })
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCerrar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Column(Modifier.weight(1f)) {
                Text("Prescripción de dosis variable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(estado.lote.nombre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        Box(Modifier.fillMaxWidth().weight(0.4f)) {
            if (gisMap != null) {
                key(gisMap) { AndroidView(factory = { gisMap.webView.detachFromParent() }, modifier = Modifier.fillMaxSize()) }
            }
            estado.zonas?.let { z ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                ) {
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        z.zonas.forEach { zona ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(colorDe(ExportacionPrescripcion.colorZona(zona.zona, z.zonas.size))))
                                Spacer(Modifier.width(4.dp))
                                Text("Zona ${zona.zona}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(0.6f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EtiquetaOrigen(
                        OrigenDato.HISTORICO_REAL,
                        when (estado.fuenteZonas) {
                            FuenteZonas.RELIEVE -> "Copernicus DEM GLO-30 · 10 m"
                            FuenteZonas.RINDE -> "Monitor de rinde · grilla de 10 m"
                            else -> "Reflectancia Sentinel-2 L2A · 10 m (NDRE 20 m)"
                        }
                    )
                    Text(
                        "Las zonas son relativas dentro del lote. Las dosis las define un ingeniero agrónomo con análisis de suelo e historial: la imagen no reemplaza el muestreo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!estado.configurada && viewModel.usaCuentaGranex) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Iniciá sesión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Las imágenes Sentinel-2 de 10 m vienen del servidor de GRANEX: solo hace falta tu cuenta GRANEX.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onIrAAjustes, colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)) { Text("Ir a Ajustes") }
                                OutlinedButton(onClick = viewModel::reintentar) { Text("Ya ingresé") }
                            }
                        }
                    }
                }
            } else if (!estado.configurada) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Conectá Sentinel-2", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "1. Creá una cuenta gratuita en Copernicus.\n" +
                                    "2. En el panel de Sentinel Hub (User settings → OAuth clients), creá un cliente.\n" +
                                    "3. Copiá el client ID y el secret en Ajustes → Imágenes Sentinel-2.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            EnlaceExterno(
                                texto = "Registrate gratis en Copernicus",
                                url = EnlacesRegistro.COPERNICUS_REGISTRO,
                                testTag = "link_registro_copernicus_vra"
                            )
                            EnlaceExterno(
                                texto = "Abrir el panel para crear el cliente",
                                url = EnlacesRegistro.COPERNICUS_CLIENTES,
                                testTag = "link_clientes_copernicus_vra"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onIrAAjustes, colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)) { Text("Ir a Ajustes") }
                                OutlinedButton(onClick = viewModel::reintentar) { Text("Ya las cargué") }
                            }
                        }
                    }
                }
            }

            item {
                PasosPrescripcion(
                    paso = paso,
                    habilitado = listOf(true, estado.zonas != null, estado.prescripcion() != null),
                    onPaso = { pasoElegido = it }
                )
            }

            if (paso == 0) {
            // 1. Base de las zonas
            item {
                Seccion("1. Base de las zonas") {
                    // Cuatro bases: en chips, que pasan a otro renglón si no entran (en botones segmentados
                    // de igual ancho, "Campañas" se partía en dos)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FuenteZonas.entries.forEach { f ->
                            FilterChip(
                                selected = estado.fuenteZonas == f,
                                onClick = { viewModel.elegirFuenteZonas(f) },
                                label = { Text(f.nombre) },
                                modifier = Modifier.testTag("fuente_${f.name.lowercase()}")
                            )
                        }
                    }
                    Text(estado.fuenteZonas.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (estado.fuenteZonas == FuenteZonas.CAMPANAS) {
                        BaseCampanas(estado, viewModel)
                    } else if (estado.fuenteZonas == FuenteZonas.RELIEVE) {
                        BaseRelieve(estado, viewModel)
                    } else if (estado.fuenteZonas == FuenteZonas.RINDE) {
                        BaseRinde(estado)
                    } else {
                        when (val escenas = estado.escenas) {
                            is Carga.Cargando -> if (estado.configurada) Cargando("Buscando imágenes de los últimos 4 meses…")
                            is Carga.Fallo -> if (estado.configurada) {
                                Text(escenas.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = viewModel::reintentar) { Text("Reintentar") }
                            }
                            is Carga.Lista -> if (escenas.valor.isEmpty()) {
                                Text("No hay imágenes en los últimos 4 meses.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Elegí una fecha con el lote despejado:", style = MaterialTheme.typography.bodySmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(escenas.valor, key = { it.fecha }) { e ->
                                        val elegida = estado.escena == e
                                        val icono: (@Composable () -> Unit)? = if (e.nubesPct > 30) {
                                            { Icon(Icons.Default.Cloud, contentDescription = "Nublado", modifier = Modifier.size(16.dp)) }
                                        } else null
                                        FilterChip(
                                            selected = elegida,
                                            onClick = { viewModel.elegirEscena(e) },
                                            label = {
                                                Column {
                                                    Text(formatearFechaCorta(e.fecha), fontWeight = FontWeight.Bold)
                                                    Text(
                                                        String.format(localeAr, "%.0f %% nubes", e.nubesPct) +
                                                            (e.valor(estado.indice)?.let { String.format(localeAr, " · %s %.2f", estado.indice.nombre, it) } ?: ""),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            },
                                            leadingIcon = icono
                                        )
                                    }
                                }
                            }
                        }
                        Text("Índice para zonificar", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IndiceVegetacion.entries.forEach { i ->
                                FilterChip(selected = estado.indice == i, onClick = { viewModel.elegirIndice(i) }, label = { Text("${i.nombre} · ${i.resolucionM} m") })
                            }
                        }
                        Text(estado.indice.descripcion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (val img = estado.imagen) {
                            is Carga.Cargando -> Cargando("Descargando la imagen a 10 m…")
                            is Carga.Fallo -> Text(img.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                        if (estado.imagenSinCultivo) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.testTag("aviso_sin_cultivo")
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Esta imagen es de suelo sin cultivo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Barbecho o rastrojo: las zonas mostrarían diferencias de suelo y rastrojo, no el potencial del lote. " +
                                            "Para sembrar o fertilizar usá varias campañas.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(onClick = { viewModel.elegirFuenteZonas(FuenteZonas.CAMPANAS) }) { Text("Usar varias campañas") }
                                }
                            }
                        }
                    }
                }
            }

            // Mapas de rinde: base de zonas, suma a las campañas y validación de las zonas
            item {
                Seccion("Mapas de rinde") {
                    SeccionMapasRinde(estado, viewModel) { abrirRinde.launch(arrayOf("*/*")) }
                }
            }

            // 2. Zonas
            item {
                Seccion("2. Zonas de manejo") {
                    SelectorPerfil(estado.perfil, viewModel::elegirPerfil)
                    Text(estado.perfil.detalle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val recomendada = estado.recomendacion?.zonas
                    Opciones(
                        "Cantidad de zonas",
                        (Zonificador.MIN_ZONAS..Zonificador.MAX_ZONAS).toList(),
                        estado.config.zonas,
                        { if (it == recomendada) "$it · recomendada" else "$it" }
                    ) { n -> viewModel.cambiarConfig { it.copy(zonas = n) } }
                    estado.recomendacion?.let { rec ->
                        TextoRecomendacion(rec, relieve = estado.resumenRelieve.takeIf { estado.fuenteZonas == FuenteZonas.RELIEVE })
                    }
                    Opciones("Método", MetodoZonificacion.entries, estado.config.metodo, { it.nombre }) { m -> viewModel.cambiarConfig { it.copy(metodo = m) } }
                    AjusteMaquina(estado, viewModel)
                    Opciones(
                        if (estado.config.anchoLaborM != null) "Largo de celda (a lo largo de la pasada)" else "Tamaño de celda",
                        listOf(10.0, 20.0, 30.0),
                        estado.config.celdaM,
                        { "${it.toInt()} m" }
                    ) { c -> viewModel.cambiarConfig { it.copy(celdaM = c) } }
                    if (estado.config.anchoLaborM != null) AvisoRespuesta(estado, viewModel)
                    Opciones("Área mínima de cada parche", listOf(0.25, 0.5, 1.0, 2.0), estado.config.areaMinimaHa, { String.format(localeAr, "%s ha", it.toString().replace('.', ',')) }) { a ->
                        viewModel.cambiarConfig { it.copy(areaMinimaHa = a) }
                    }
                    when (val z = estado.zonificacion) {
                        null -> Text(
                            when (estado.fuenteZonas) {
                                FuenteZonas.CAMPANAS -> "Las zonas se calculan cuando terminan de bajar las campañas."
                                FuenteZonas.RELIEVE -> "Las zonas se calculan cuando llega el relieve del lote."
                                FuenteZonas.FECHA -> "Elegí una imagen para calcular las zonas."
                                FuenteZonas.RINDE -> "Las zonas se calculan con los mapas de rinde importados."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        is Carga.Cargando -> Cargando("Calculando zonas…")
                        is Carga.Fallo -> Text(z.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        is Carga.Lista -> {
                            val zon = z.valor
                            val limite = estado.perfil.maxPoligonos
                            Text(
                                buildString {
                                    append("${zon.poligonos.size} polígonos")
                                    if (limite != null) append(" (el monitor lee hasta $limite)")
                                    if (zon.areaSubioPorLimite) {
                                        append(String.format(localeAr, ". Para no pasar el límite, el área mínima subió a %.2f ha.", zon.areaMinimaAplicadaHa))
                                    }
                                    if (zon.zonas.size < estado.config.zonas) append(". Quedaron ${zon.zonas.size} zonas porque las demás eran muy chicas.")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            zon.zonas.forEach { zona ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorDe(ExportacionPrescripcion.colorZona(zona.zona, zon.zonas.size))))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        String.format(localeAr, "Zona %d · %.2f ha · %s %.2f (%.2f a %.2f)", zona.zona, zona.areaHa, estado.nombreValor, zona.indicePromedio, zona.indiceMin, zona.indiceMax),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            RindePorZona(estado)
                        }
                    }
                }
            }

            item {
                BotonSiguientePaso("Siguiente: dosis", habilitado = estado.zonas != null) { pasoElegido = 1 }
            }
            }

            if (paso == 1) {
            // 3. Análisis de suelo
            if (estado.zonas != null) {
                item {
                    Seccion("3. Análisis de suelo") {
                        SeccionSuelo(estado, viewModel) { abrirSuelo.launch(arrayOf("*/*")) }
                    }
                }
            }

            // 3. Dosis
            estado.zonas?.let { zon ->
                item {
                    Seccion("4. Dosis por zona") {
                        val labores = if (estado.perfil == PerfilMonitor.CUFFIA_ALTINA) listOf(Labor.FERTILIZACION, Labor.SIEMBRA) else Labor.entries
                        Opciones("Labor", labores, estado.labor, { it.nombre }, viewModel::cambiarLabor)
                        val altina = estado.perfil == PerfilMonitor.CUFFIA_ALTINA
                        if (estado.labor.usaFertilizante) SugerenciaDosis(estado, viewModel)
                        if (estado.perfil.semillasCada10m && estado.labor.usaFertilizante) {
                            Opciones("Fertilizante", UbicacionFertilizante.entries, estado.ubicacionFertilizante, { it.nombre }, viewModel::cambiarUbicacion)
                        }
                        if (estado.perfil.semillasCada10m && estado.labor.usaSemillas) {
                            var distancia by rememberSaveable { mutableStateOf(estado.distanciaSurcosM.toString().replace('.', ',')) }
                            OutlinedTextField(
                                value = distancia,
                                onValueChange = { t ->
                                    distancia = t
                                    t.replace(',', '.').toDoubleOrNull()?.let(viewModel::cambiarDistanciaSurcos)
                                },
                                label = { Text("Distancia entre surcos (m)") },
                                supportingText = { Text("El monitor lee semillas cada 10 m lineales.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        var producto by rememberSaveable { mutableStateOf(estado.producto) }
                        // La sugerencia de dosis completa el producto (urea, MAP…) si estaba vacío
                        LaunchedEffect(estado.producto) { if (producto != estado.producto) producto = estado.producto }
                        OutlinedTextField(
                            value = producto,
                            onValueChange = { producto = it; viewModel.cambiarProducto(it) },
                            label = { Text(if (estado.labor.usaFertilizante) "Fertilizante (ej. urea, MAP)" else "Híbrido o variedad") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        zon.zonas.forEach { zona ->
                            val dosis = estado.dosis[zona.zona]
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorDe(ExportacionPrescripcion.colorZona(zona.zona, zon.zonas.size))))
                                Text(String.format(localeAr, "Zona %d\n%.1f ha", zona.zona, zona.areaHa), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                                if (estado.labor.usaFertilizante || altina) {
                                    CampoEntero(
                                        clave = "fert_${zona.zona}",
                                        valor = dosis?.fertilizanteKgHa,
                                        etiqueta = if (altina) "kg/ha" else "Fert. kg/ha",
                                        modifier = Modifier.weight(1f)
                                    ) { viewModel.cambiarDosis(zona.zona, it, dosis?.semillasHa) }
                                }
                                if (estado.labor.usaSemillas && !altina) {
                                    CampoEntero(
                                        clave = "sem_${zona.zona}",
                                        valor = dosis?.semillasHa,
                                        etiqueta = "Sem/ha",
                                        ayuda = dosis?.semillasHa?.takeIf { estado.perfil.semillasCada10m }?.let {
                                            "≈ ${ConversionesDosis.semillasCada10m(it, estado.distanciaSurcosM)} sem/10 m"
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { viewModel.cambiarDosis(zona.zona, dosis?.fertilizanteKgHa, it) }
                                }
                            }
                        }
                        estado.prescripcion()?.let { p ->
                            Text(
                                buildString {
                                    if (p.labor.usaFertilizante || altina) append(String.format(localeAr, "Total: %,.0f kg", p.totalFertilizanteKg()))
                                    if (p.labor.usaSemillas && !altina) {
                                        if (isNotEmpty()) append(" · ")
                                        append(String.format(localeAr, "%,.0f semillas", p.totalSemillas()))
                                    }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                BotonSiguientePaso("Siguiente: exportar", habilitado = estado.prescripcion() != null) { pasoElegido = 2 }
            }
            }

            if (paso == 2) {
            // 4. Exportar
            estado.prescripcion()?.let { p ->
                item {
                    Seccion("5. Exportar al monitor") {
                        val problemas = p.problemas(estado.perfil)
                        problemas.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        val listo = problemas.isEmpty()
                        if (estado.perfil.esCuffia) {
                            Text(
                                "Cuffia lee los 4 archivos (.shp .shx .dbf .prj) sueltos en la raíz del pendrive, sin carpetas. Conectá el pendrive al teléfono con un adaptador OTG y elegí su carpeta principal.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { elegirCarpeta.launch(null) },
                            enabled = listo,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar en el pendrive")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.zipMonitor().fold(
                                    { zipPendiente = it; crearZip.launch(viewModel.nombreArchivo(".zip")) },
                                    { mensaje = it.message }
                                )
                            },
                            enabled = listo,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Guardar ZIP para ${estado.perfil.nombre}") }
                        OutlinedButton(
                            onClick = {
                                viewModel.zipTodos().fold(
                                    { zipPendiente = it; crearZip.launch(viewModel.nombreArchivo("_todos.zip")) },
                                    { mensaje = it.message }
                                )
                            },
                            enabled = listo,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Todos los formatos (Shapefile, ISO-XML, GeoJSON)") }
                        mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold) }
                        Text(
                            "Antes de aplicar en todo el lote, probá la prescripción en una franja y verificá en el monitor que cada zona muestre su dosis.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
            contenido()
        }
    }
}

@Composable
private fun Cargando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Opciones(titulo: String, opciones: List<T>, actual: T, texto: (T) -> String, onElegir: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { o -> FilterChip(selected = o == actual, onClick = { onElegir(o) }, label = { Text(texto(o)) }) }
        }
    }
}

@Composable
private fun SelectorPerfil(actual: PerfilMonitor, onElegir: (PerfilMonitor) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Agriculture, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monitor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(actual.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            PerfilMonitor.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.nombre) }, onClick = { onElegir(p); abierto = false })
            }
        }
    }
}

@Composable
private fun CampoEntero(
    clave: String,
    valor: Int?,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    onCambio: (Int?) -> Unit
) {
    var texto by rememberSaveable(clave) { mutableStateOf(valor?.toString().orEmpty()) }
    // Si la dosis cambia desde afuera (la sugerencia), el campo la muestra
    LaunchedEffect(valor) { if (texto.toIntOrNull() != valor) texto = valor?.toString().orEmpty() }
    val textoAyuda: (@Composable () -> Unit)? = ayuda?.let { a -> { Text(a) } }
    OutlinedTextField(
        value = texto,
        onValueChange = { t ->
            val limpio = t.filter { it.isDigit() }.take(7)
            texto = limpio
            onCambio(limpio.toIntOrNull())
        },
        label = { Text(etiqueta) },
        supportingText = textoAyuda,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

/** Relieve como base: estado de la descarga y el resumen de bajos y lomas. */
@Composable
private fun BaseRelieve(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel) {
    when (val r = estado.relieve) {
        null, is Carga.Cargando -> if (estado.configurada) Cargando("Calculando el relieve del lote…")
        is Carga.Fallo -> {
            Text(r.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = viewModel::reintentarRelieve) { Text("Reintentar") }
        }
        is Carga.Lista -> estado.resumenRelieve?.let { res ->
            Text(res.diagnostico, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("relieve_listo"))
            Text(
                "Las zonas van del punto más bajo (zona 1) al más alto. En años húmedos los bajos pueden anegarse; " +
                    "en años secos son los que más rinden: ajustá las dosis según la campaña.\n${AnalisisRelieve.AVISO_DEM}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Campañas para el potencial: temporada, cuáles usar y cómo quedó cada una. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BaseCampanas(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Temporada.entries.forEachIndexed { i, t ->
            SegmentedButton(
                selected = estado.temporada == t,
                onClick = { viewModel.elegirTemporada(t) },
                shape = SegmentedButtonDefaults.itemShape(i, Temporada.entries.size)
            ) { Text(t.nombre) }
        }
    }
    Text(
        "Se toma el pico de vigor de cada campaña (${estado.temporada.meses}). Sacá las campañas que no representen al lote, " +
            "por ejemplo una sequía extrema o un cultivo distinto.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (estado.campanasDisponibles.isEmpty()) {
        Text("Todavía no terminó ninguna campaña con imágenes Sentinel-2.", style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        estado.campanasDisponibles.forEach { c ->
            FilterChip(
                selected = c.etiqueta in estado.campanasElegidas,
                onClick = { viewModel.alternarCampana(c.etiqueta) },
                label = { Text(c.etiqueta) },
                modifier = Modifier.testTag("campana_${c.etiqueta}")
            )
        }
    }
    // Índice del pico: el NDVI se satura en cultivos densos; el NDRE (borde rojo) mucho menos
    val usado = estado.potencialListo?.indiceVegetacion
    Text("Índice del pico de vigor", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, IndiceVegetacion.NDVI, IndiceVegetacion.NDRE).forEach { i ->
            FilterChip(
                selected = estado.indiceCampanas == i,
                onClick = { viewModel.elegirIndiceCampanas(i) },
                label = {
                    Text(
                        when (i) {
                            null -> "Automático" + (if (estado.indiceCampanas == null && usado != null) " (${usado.nombre})" else "")
                            else -> "${i.nombre} · ${i.resolucionM} m"
                        }
                    )
                },
                modifier = Modifier.testTag("indice_campanas_${i?.name?.lowercase() ?: "auto"}")
            )
        }
    }
    Text(
        "El NDVI se satura en cultivos densos (un lote muy bueno y uno bueno dan casi lo mismo). El NDRE se satura mucho menos: " +
            "en automático se usa cuando el NDVI pico típico del lote pasa de 0,85.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val resumenes = estado.potencialListo?.campanas?.associateBy { it.campana.etiqueta }.orEmpty()
    estado.campanasDisponibles.filter { it.etiqueta in estado.campanasElegidas }.forEach { c ->
        val r = resumenes[c.etiqueta]
        val (texto, color) = when (val carga = estado.campanas[c.etiqueta]) {
            null, Carga.Cargando -> "Procesando las imágenes de la campaña…" to MaterialTheme.colorScheme.onSurfaceVariant
            is Carga.Fallo -> carga.mensaje to MaterialTheme.colorScheme.error
            is Carga.Lista -> when {
                r == null -> "Lista" to MaterialTheme.colorScheme.onSurfaceVariant
                r.usada -> String.format(localeAr, "NDVI pico típico %.2f · %.0f %% del lote con dato", r.medianaNdvi ?: 0.0, r.coberturaPct) to AgroGreenPrimary
                else -> "No se usa: ${r.motivo}" to MaterialTheme.colorScheme.error
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (estado.campanas[c.etiqueta] == Carga.Cargando) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("${c.etiqueta}: $texto", style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
    when (val p = estado.potencial) {
        is Carga.Cargando -> Cargando("Combinando las campañas…")
        is Carga.Fallo -> Text(p.mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is Carga.Lista -> {
            val inestable = p.valor.inestablePct()
            Text(
                "Potencial productivo listo con ${p.valor.usadas.size} campañas: 1,00 es lo típico del lote; 1,10, un 10 % por encima." +
                    if (inestable >= 15) String.format(localeAr, " El %.0f %% del lote cambia mucho de un año a otro: esas zonas son menos confiables.", inestable) else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("potencial_listo")
            )
        }
        null -> Unit
    }
}

/** "Recomendado: 3 zonas (explican el 84 %)" y la variación que explica cada opción. */
@Composable
private fun TextoRecomendacion(rec: RecomendacionZonas, relieve: ResumenRelieve? = null) {
    val detalle = rec.explicada.entries.joinToString(" · ") { (k, v) -> String.format(localeAr, "%d: %.0f %%", k, v * 100) }
    Text(
        if (relieve != null && rec.homogeneo) {
            "El lote es plano (${formatoDecimal(relieve.desnivel, 1)} m de desnivel): las zonas de relieve serían de pocos " +
                "centímetros y no explican el rinde. Usá varias campañas. Si igual querés zonas, 2 alcanzan."
        } else if (rec.homogeneo) {
            String.format(
                localeAr,
                "El lote es bastante parejo (varía %.0f %%): entre zonas habría pocos puntos de diferencia de potencial, así que una " +
                    "dosis variable probablemente rinda poco acá. Si igual querés zonas, 2 alcanzan.",
                rec.coeficienteVariacion * 100
            )
        } else {
            String.format(localeAr, "Recomendado: %d zonas, explican el %.0f %% de la variación del lote.", rec.zonas, rec.explicada.getValue(rec.zonas) * 100)
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("recomendacion_zonas")
    )
    Text("Variación explicada por cantidad de zonas: $detalle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (rec.atipicosPct >= 0.5) {
        Text(
            String.format(
                localeAr,
                "El %.0f %% del lote tiene valores atípicos (agua, caminos, bordes con otro uso): no se usó para definir las zonas y quedó en la zona más parecida.",
                rec.atipicosPct
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Ancho de labor y sentido de siembra: cada pasada es una columna de celdas alineada al rumbo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AjusteMaquina(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel) {
    Text("Ajuste a la máquina", style = MaterialTheme.typography.labelLarge)
    val ancho = estado.config.anchoLaborM
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Double?>(null) + ANCHOS_LABOR).forEach { a ->
            FilterChip(
                selected = ancho == a,
                onClick = { viewModel.cambiarAnchoLabor(a) },
                label = { Text(a?.let { String.format(localeAr, "%s m", formatoAncho(it)) } ?: "Sin ajustar") },
                modifier = Modifier.testTag("ancho_${a?.toInt() ?: 0}")
            )
        }
    }
    if (ancho == null) {
        Text(
            "Cargá el ancho de labor para que las zonas sigan las pasadas: ningún parche queda más angosto que la máquina.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Text("Sentido de siembra", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SentidoSiembra.entries.forEach { s ->
            FilterChip(
                selected = estado.sentido == s,
                onClick = { viewModel.cambiarSentido(s) },
                label = {
                    Text(
                        if (s == SentidoSiembra.AUTOMATICO) String.format(localeAr, "%s (%.0f°)", s.nombre, s.rumbo(estado.lote.coordenadas))
                        else s.nombre
                    )
                }
            )
        }
    }
    Text(
        String.format(
            localeAr,
            "Cada columna es una pasada de %s m; las celdas miden %s × %.0f m, alineadas a %.0f° del norte.",
            formatoAncho(ancho), formatoAncho(ancho), estado.config.celdaM, estado.config.rumboGrados ?: 0.0
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Velocidad y respuesta del dosificador: si la celda es corta, la máquina no llega a aplicar su dosis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvisoRespuesta(estado: EstadoPrescripcion, viewModel: PrescripcionViewModel) {
    Text("Velocidad de trabajo y respuesta del dosificador", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(6, 8, 10, 12).forEach { v ->
            FilterChip(selected = estado.velocidadKmH == v, onClick = { viewModel.cambiarVelocidad(v) }, label = { Text("$v km/h") })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 5).forEach { t ->
            FilterChip(selected = estado.respuestaS == t, onClick = { viewModel.cambiarRespuesta(t) }, label = { Text("$t s") })
        }
    }
    val transicion = estado.metrosTransicion
    val corta = estado.config.celdaM < 2 * transicion
    Text(
        String.format(
            localeAr,
            "A %d km/h con %d s de respuesta, la dosis tarda %.0f m en cambiar.",
            estado.velocidadKmH, estado.respuestaS, transicion
        ) + if (corta) {
            String.format(localeAr, " Con celdas de %.0f m la máquina no llega a aplicar la dosis de los parches cortos: usá celdas más largas.", estado.config.celdaM)
        } else " Con este largo de celda hay margen.",
        style = MaterialTheme.typography.bodySmall,
        color = if (corta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("aviso_respuesta")
    )
}

/** Anchos de labor comunes: sembradoras (6 a 18 m) y fertilizadoras o pulverizadoras (24 y 36 m). */
private val ANCHOS_LABOR = listOf(6.0, 9.0, 12.0, 15.0, 18.0, 24.0, 36.0)

private fun formatoAncho(m: Double) = if (m % 1.0 == 0.0) m.toInt().toString() else String.format(localeAr, "%.1f", m)

/** Rediseño: indicador de los 3 pasos de la prescripción; se puede volver a uno anterior tocándolo. */
@Composable
private fun PasosPrescripcion(paso: Int, habilitado: List<Boolean>, onPaso: (Int) -> Unit) {
    val nombres = listOf("1 · Zonas", "2 · Dosis", "3 · Exportar")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("pasos_prescripcion")) {
        nombres.forEachIndexed { i, nombre ->
            val hecho = i <= paso
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = habilitado[i], role = androidx.compose.ui.semantics.Role.Tab) { onPaso(i) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (hecho) AgroGreenPrimary else MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    nombre + if (i < paso) " ✓" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (i == paso) FontWeight.Bold else FontWeight.Normal,
                    color = if (habilitado[i]) (if (hecho) AgroGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun BotonSiguientePaso(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("btn_siguiente_paso")
    ) { Text(texto, fontWeight = FontWeight.SemiBold) }
}
