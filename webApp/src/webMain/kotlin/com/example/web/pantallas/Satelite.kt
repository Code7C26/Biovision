package com.example.web.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.EstadoCuenta
import com.example.web.datos.LoteWeb
import com.example.web.plataforma.CanalMarco
import com.example.web.plataforma.MarcoWeb
import com.example.web.plataforma.TaparMarcos
import com.example.web.ui.Aviso
import com.example.web.ui.BotonIcono
import com.example.web.ui.BuscadorLocalidad
import com.example.web.ui.ClaseAncho
import com.example.web.ui.Encabezado
import com.example.web.ui.EspacioPanel
import com.example.web.ui.IconosGranex
import com.example.web.ui.Insignia
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.desplazableHorizontal
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.viewmodel.DeteccionProvinciaWeb
import com.example.web.viewmodel.EscaneoWebViewModel
import com.example.web.viewmodel.EdicionWeb
import com.example.web.viewmodel.MapaBaseWeb
import com.example.web.viewmodel.SateliteWebViewModel
import com.example.utils.vra.ExportacionPrescripcion
import com.example.web.viewmodel.ComandoMapa
import com.example.web.viewmodel.PrescripcionWebViewModel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** Página Leaflet compartida con Android (app/src/main/assets/mapa), servida junto a la app web. */
private const val URL_MAPA = "mapa/mapa_gis.html"

/**
 * Satélite: el mismo mapa GIS que Android (Leaflet + mapa_gis.js) dentro de un iframe del mismo origen,
 * con los lotes del usuario: lista, selección, dibujo y edición (tocar el mapa agrega vértices,
 * arrastrar los mueve, el punto medio de un lado inserta uno, tocar el primero cierra el polígono).
 * La app le habla con postMessage → window.GRANEX.* y la página responde con sus eventos.
 * Teléfono: buscador, mapa y barra abajo. Tablet y escritorio: panel lateral + mapa.
 */
@Composable
fun PantallaSatelite(
    vm: SateliteWebViewModel,
    escaneo: EscaneoWebViewModel,
    prescripcion: PrescripcionWebViewModel,
    cuenta: EstadoCuenta,
    irACuenta: () -> Unit
) {
    val estadoEscaneo by escaneo.estado.collectAsStateWithLifecycle()
    val estadoPrescripcion by prescripcion.estado.collectAsStateWithLifecycle()
    val sinAccesoEscaneo by escaneo.sinAcceso.collectAsStateWithLifecycle()
    var escaneoAbierto by remember { mutableStateOf(false) }
    val base by vm.mapaBase.collectAsStateWithLifecycle()
    val listo by vm.listo.collectAsStateWithLifecycle()
    val destino by vm.destino.collectAsStateWithLifecycle()
    val texto by vm.buscador.texto.collectAsStateWithLifecycle()
    val sugerencias by vm.buscador.sugerencias.collectAsStateWithLifecycle()
    val lotes by vm.listaLotes.collectAsStateWithLifecycle()
    val seleccionado by vm.seleccionado.collectAsStateWithLifecycle()
    val edicion by vm.edicion.collectAsStateWithLifecycle()
    val areaEnVivo by vm.areaEnVivo.collectAsStateWithLifecycle()
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val dialogoGuardar by vm.dialogoGuardar.collectAsStateWithLifecycle()
    var canal by remember { mutableStateOf<CanalMarco?>(null) }
    var aEliminar by remember { mutableStateOf<LoteWeb?>(null) }
    var listaAbierta by remember { mutableStateOf(false) }

    val loteElegido = lotes.firstOrNull { it.id == seleccionado }

    // Estado → página, cuando la página ya inicializó Leaflet (como GisMapEffects en Android)
    LaunchedEffect(canal, listo, base) {
        if (listo > 0) canal?.enviar(SateliteWebViewModel.comandoMapaBase(base).json())
    }
    LaunchedEffect(canal, listo, destino) {
        val d = destino ?: return@LaunchedEffect
        val c = canal ?: return@LaunchedEffect
        if (listo == 0) return@LaunchedEffect
        c.enviar(SateliteWebViewModel.comandoIrA(d.lat, d.lon).json())
        vm.destinoEnviado()
    }
    // Solo los cambios confirmados (revision) reenvían los vértices: los arrastres no redibujan
    LaunchedEffect(canal, listo, edicion?.estado?.revision, edicion != null) {
        if (listo > 0) canal?.enviar(SateliteWebViewModel.comandoEdicion(edicion?.estado).json())
    }
    LaunchedEffect(canal, listo, lotes, seleccionado, edicion?.origen?.id, edicion != null) {
        if (listo > 0) canal?.enviar(SateliteWebViewModel.comandoLotes(lotes, seleccionado, edicion?.origen?.id).json())
    }

    val buscador: @Composable () -> Unit = {
        BuscadorLocalidad(
            texto = texto,
            alCambiar = { vm.buscador.texto.value = it },
            sugerencias = sugerencias,
            alElegir = vm::irA,
            placeholder = "Ir a una localidad"
        )
    }
    val selectorBase: @Composable () -> Unit = {
        Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapaBaseWeb.entries.forEach { b ->
                FilterChip(selected = b == base, onClick = { vm.elegirMapaBase(b) }, label = { Text(b.etiqueta) }, modifier = Modifier.mano())
            }
        }
    }
    val mapa: @Composable (Modifier) -> Unit = { modifier ->
        MarcoWeb(
            url = URL_MAPA,
            titulo = if (edicion != null) "Mapa satelital: dibujo del lote" else "Mapa satelital",
            modifier = modifier,
            alMensaje = vm::alMensajeMapa,
            alCanal = {
                canal = it
                if (it == null) vm.mapaDescartado()
            }
        )
    }
    // El escaneo sigue al lote elegido (y vuelve a intentar al iniciar o cerrar sesión); al dibujar se oculta
    LaunchedEffect(loteElegido, edicion == null, cuenta) { escaneo.abrir(loteElegido.takeIf { edicion == null }) }
    // El JSON de la grilla tiene miles de valores: se arma una vez por estado, no en cada recomposición
    val prescribiendo = estadoPrescripcion != null
    val capa = remember(estadoEscaneo, prescribiendo) { if (prescribiendo) null else estadoEscaneo?.capaJson() }
    // Las zonas se reenvían solo cuando cambia la zonificación, no al tipear dosis
    val zonasJson = remember(estadoPrescripcion?.zonas) {
        estadoPrescripcion?.prescripcion()?.let { ExportacionPrescripcion.geojson(it, conColor = true) }
    }
    LaunchedEffect(canal, listo, zonasJson) {
        if (listo > 0) canal?.enviar(ComandoMapa("setZonas", buildJsonArray { add(zonasJson?.let(::JsonPrimitive) ?: JsonNull) }).json())
    }
    // Otro lote o empezar a dibujar cierra la prescripción abierta
    LaunchedEffect(loteElegido?.id, loteElegido?.registro?.modificadoEn, edicion != null) {
        val abierta = prescripcion.estado.value ?: return@LaunchedEffect
        if (edicion != null || loteElegido == null || loteElegido.id != abierta.lote.id || loteElegido.registro.modificadoEn != abierta.lote.registro.modificadoEn) {
            prescripcion.cerrar()
        }
    }
    LaunchedEffect(canal, listo, capa) {
        if (listo > 0) canal?.enviar(SateliteWebViewModel.comandoCapa(capa).json())
    }
    val chips = listOfNotNull(
        if (edicion != null) "Dibujando lote" else if (lotes.isEmpty()) "Sin lotes" else
            if (lotes.size == 1) "1 lote" else "${lotes.size} lotes",
        if (pendientes > 0) "$pendientes sin subir" else null,
        loteElegido?.takeIf { edicion == null }?.nombre
    )

    Column(Modifier.fillMaxSize()) {
        Encabezado(titulo = "Campo", chips = chips)
        if (LocalClaseAncho.current == ClaseAncho.COMPACTA) {
            Column(
                Modifier.fillMaxSize().padding(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (edicion == null && !escaneoAbierto) buscador()
                Box(Modifier.fillMaxWidth().weight(1f)) { mapa(Modifier.fillMaxSize()) }
                val e = edicion
                if (e != null) {
                    BarraEdicion(e, areaEnVivo, vm, compacta = true)
                } else if (estadoPrescripcion != null) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Prescripción · ${estadoPrescripcion!!.lote.nombre}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = prescripcion::cerrar, modifier = Modifier.mano()) { Text("Cerrar") }
                        }
                        ContenidoPrescripcion(estadoPrescripcion!!, prescripcion, irACuenta)
                    }
                } else if (escaneoAbierto && loteElegido != null) {
                    // En el teléfono el escaneo comparte la pantalla con el mapa, que se achica
                    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Escaneo · ${loteElegido.nombre}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { escaneoAbierto = false }, modifier = Modifier.mano()) { Text("Cerrar") }
                        }
                        ContenidoEscaneo(estadoEscaneo, sinAccesoEscaneo, escaneo, irACuenta)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = vm::nuevoLote, enabled = vm.puedeGuardarLotes, modifier = Modifier.mano()) {
                            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nuevo lote")
                        }
                        OutlinedButton(onClick = { listaAbierta = true }, modifier = Modifier.mano()) {
                            Icon(Icons.Filled.List, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mis lotes (${lotes.size})")
                        }
                        if (loteElegido != null) {
                            TextButton(onClick = { escaneoAbierto = true }, modifier = Modifier.mano()) { Text("Escaneo") }
                            TextButton(onClick = { prescripcion.abrir(loteElegido) }, modifier = Modifier.mano()) { Text("Prescripción") }
                        }
                    }
                    selectorBase()
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(EspacioPanel), horizontalArrangement = Arrangement.spacedBy(EspacioPanel)) {
                Column(
                    Modifier.width(if (estadoPrescripcion != null) 440.dp else 360.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EspacioPanel)
                ) {
                    val e = edicion
                    val ep = estadoPrescripcion
                    if (e != null) {
                        Panel(if (e.origen == null) "Nuevo lote" else "Editar ${e.origen.nombre}") {
                            BarraEdicion(e, areaEnVivo, vm, compacta = false)
                        }
                    } else if (ep != null) {
                        Panel("Prescripción de dosis variable", subtitulo = ep.lote.nombre) {
                            TextButton(onClick = prescripcion::cerrar, modifier = Modifier.mano()) { Text("← Volver a mis lotes") }
                            ContenidoPrescripcion(ep, prescripcion, irACuenta)
                        }
                    } else {
                        buscador()
                        Panel("Mis lotes", subtitulo = subtituloLotes(lotes.size, pendientes)) {
                            ContenidoLotes(lotes, seleccionado, cuenta, vm, irACuenta, alEliminar = { aEliminar = it })
                        }
                        Panel("Mapa base") {
                            selectorBase()
                            Text(
                                "Imágenes de Esri (mosaico de fecha variable) u OpenStreetMap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Panel("Escaneo satelital", subtitulo = loteElegido?.nombre) {
                            ContenidoEscaneo(estadoEscaneo, sinAccesoEscaneo, escaneo, irACuenta)
                        }
                        Panel("Prescripción de dosis variable", subtitulo = loteElegido?.nombre) {
                            Text(
                                "Zonas de manejo por campañas, una fecha o el relieve, dosis por zona y los archivos para " +
                                    "Cuffia, FGS, Altina o ISOBUS, que se descargan directo a la computadora.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (loteElegido != null) {
                                Button(onClick = { prescripcion.abrir(loteElegido) }, modifier = Modifier.fillMaxWidth().mano()) {
                                    Text("Crear prescripción para ${loteElegido.nombre}")
                                }
                            } else {
                                Aviso("Elegí un lote de la lista para crear su prescripción.")
                            }
                        }
                    }
                }
                mapa(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }

    if (listaAbierta) {
        TaparMarcos()
        AlertDialog(
            onDismissRequest = { listaAbierta = false },
            title = { Text("Mis lotes") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        subtituloLotes(lotes.size, pendientes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(8.dp))
                    ContenidoLotes(
                        lotes, seleccionado, cuenta, vm,
                        irACuenta = { listaAbierta = false; irACuenta() },
                        alEliminar = { aEliminar = it },
                        alElegir = { listaAbierta = false },
                        conBotonNuevo = false
                    )
                }
            },
            confirmButton = { TextButton(onClick = { listaAbierta = false }, modifier = Modifier.mano()) { Text("Cerrar") } }
        )
    }

    val e = edicion
    if (dialogoGuardar && e != null) DialogoGuardarLote(e, vm)

    aEliminar?.let { lote ->
        TaparMarcos()
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("¿Eliminar ${lote.nombre}?") },
            text = {
                Text(
                    "Se borra de este navegador y, con cuenta, también de la nube y de tus otros dispositivos " +
                        "(incluida la app Android)."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.eliminarLote(lote.id); aEliminar = null }, modifier = Modifier.mano()) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }, modifier = Modifier.mano()) { Text("Cancelar") } }
        )
    }
}

private fun subtituloLotes(cantidad: Int, pendientes: Int): String {
    val base = when (cantidad) {
        0 -> "Todavía no dibujaste lotes"
        1 -> "1 lote"
        else -> "$cantidad lotes"
    }
    return when (pendientes) {
        0 -> base
        1 -> "$base · 1 cambio sin subir"
        else -> "$base · $pendientes cambios sin subir"
    }
}

/** Lista de lotes con su acción principal (nuevo), las del elegido (editar, eliminar) y el estado de la cuenta. */
@Composable
private fun ContenidoLotes(
    lotes: List<LoteWeb>,
    seleccionado: String?,
    cuenta: EstadoCuenta,
    vm: SateliteWebViewModel,
    irACuenta: () -> Unit,
    alEliminar: (LoteWeb) -> Unit,
    alElegir: () -> Unit = {},
    conBotonNuevo: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!vm.puedeGuardarLotes) {
            Aviso(
                "Este navegador no permite guardar datos (modo privado estricto o almacenamiento bloqueado): " +
                    "no se pueden guardar lotes.",
                color = LocalMarca.current.alerta
            )
        }
        if (conBotonNuevo) {
            Button(onClick = vm::nuevoLote, enabled = vm.puedeGuardarLotes, modifier = Modifier.fillMaxWidth().mano()) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nuevo lote")
            }
        }
        if (lotes.isEmpty() && vm.puedeGuardarLotes) {
            Text(
                "Tocá “Nuevo lote” y marcá las esquinas del campo sobre el mapa. La superficie se calcula sola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            lotes.forEachIndexed { i, lote ->
                if (i > 0) HorizontalDivider()
                FilaLote(
                    lote = lote,
                    elegido = lote.id == seleccionado,
                    alTocar = {
                        vm.seleccionarLote(if (lote.id == seleccionado) null else lote.id)
                        alElegir()
                    },
                    alEditar = { vm.editarLote(lote); alElegir() },
                    alEliminar = { alEliminar(lote) }
                )
            }
        }
        LineaCuenta(cuenta, irACuenta)
    }
}

@Composable
private fun FilaLote(lote: LoteWeb, elegido: Boolean, alTocar: () -> Unit, alEditar: () -> Unit, alEliminar: () -> Unit) {
    val colores = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (elegido) colores.secondaryContainer else colores.surface)
            .mano()
            .selectable(selected = elegido, role = Role.RadioButton, onClick = alTocar)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(lote.nombre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf("${numero(lote.registro.superficieHa, 1)} ha", lote.registro.provincia).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.onSurfaceVariant
                )
            }
            if (lote.registro.pendiente) Insignia("Sin subir", LocalMarca.current.alerta)
        }
        if (elegido) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AccionLote(Icons.Filled.Edit, "Editar contorno", alEditar)
                AccionLote(Icons.Filled.Delete, "Eliminar", alEliminar)
            }
        }
    }
}

@Composable
private fun AccionLote(icono: ImageVector, texto: String, alTocar: () -> Unit) {
    TextButton(onClick = alTocar, modifier = Modifier.mano()) {
        Icon(icono, contentDescription = null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Dónde quedan los lotes según la cuenta, con acceso a ingresar. */
@Composable
private fun LineaCuenta(cuenta: EstadoCuenta, irACuenta: () -> Unit) {
    val texto = when (cuenta) {
        is EstadoCuenta.Conectado -> "Se sincronizan con tu cuenta${cuenta.correo?.let { " ($it)" } ?: ""}."
        EstadoCuenta.SinSesion -> "Sin cuenta, los lotes quedan solo en este navegador."
        EstadoCuenta.SinConexion -> "Sin conexión: los cambios se suben al volver la red."
        EstadoCuenta.SinBackend -> "Los lotes quedan guardados en este navegador."
        EstadoCuenta.Verificando -> null
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            texto,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (cuenta == EstadoCuenta.SinSesion) {
            TextButton(onClick = irACuenta, modifier = Modifier.mano()) { Text("Ingresar") }
        }
    }
}

/** Herramientas del dibujo: medidas, instrucciones, deshacer/rehacer, borrar vértice, cancelar y guardar. */
@Composable
private fun BarraEdicion(e: EdicionWeb, areaEnVivo: Double?, vm: SateliteWebViewModel, compacta: Boolean) {
    val s = e.estado
    val marca = LocalMarca.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${numero(areaEnVivo ?: s.areaHa, 2)} ha",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = marca.primario
                )
                Text(
                    "${s.vertices.size} vértices · perímetro ${numero(s.perimetroM)} m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BotonIcono(IconosGranex.Deshacer, "Deshacer", s.puedeDeshacer, vm::deshacer)
            BotonIcono(IconosGranex.Rehacer, "Rehacer", s.puedeRehacer, vm::rehacer)
            if (s.seleccionado != null) BotonIcono(Icons.Filled.Delete, "Borrar el vértice elegido", vm::eliminarVerticeSeleccionado)
        }
        when {
            s.autointerseccion -> Aviso("Los lados se cruzan: mové un vértice o deshacé el último cambio.", color = marca.negativo)
            !compacta || s.vertices.size < 3 -> Text(
                when (s.vertices.size) {
                    0 -> "Tocá el mapa en cada esquina del lote."
                    1, 2 -> "Seguí marcando esquinas: hacen falta al menos 3."
                    else -> "Arrastrá un vértice para moverlo o el punto medio de un lado para agregar uno. " +
                        "Tocá un vértice para elegirlo y borrarlo; tocá el primero para cerrar el lote."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val guardar: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = vm::abrirDialogoGuardar, enabled = s.puedeGuardar, modifier = modifier.mano()) { Text("Guardar lote") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::cancelarEdicion, modifier = Modifier.mano()) {
                Icon(Icons.Filled.Clear, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancelar")
            }
            if (compacta) {
                Spacer(Modifier.weight(1f))
                guardar(Modifier)
            } else if (s.vertices.isNotEmpty()) {
                TextButton(onClick = vm::limpiarVertices, modifier = Modifier.mano()) { Text("Empezar de nuevo") }
            }
        }
        if (!compacta) guardar(Modifier.fillMaxWidth())
    }
}

@Composable
private fun DialogoGuardarLote(e: EdicionWeb, vm: SateliteWebViewModel) {
    TaparMarcos()
    val deteccion by vm.deteccionProvincia.collectAsStateWithLifecycle()
    var nombre by remember { mutableStateOf(e.origen?.nombre.orEmpty()) }
    var provincia by remember { mutableStateOf(e.origen?.registro?.provincia.orEmpty()) }
    // Lo detectado completa el campo solo si el usuario todavía no escribió nada
    var escritaPorUsuario by remember { mutableStateOf(provincia.isNotBlank()) }
    LaunchedEffect(deteccion) {
        val d = deteccion
        if (d is DeteccionProvinciaWeb.Detectada && !escritaPorUsuario) provincia = d.provincia
    }
    var intentado by remember { mutableStateOf(false) }
    val faltaNombre = intentado && nombre.isBlank()
    fun guardar() {
        intentado = true
        vm.guardarLote(nombre, provincia)
    }
    AlertDialog(
        onDismissRequest = vm::cerrarDialogoGuardar,
        title = { Text(if (e.origen == null) "Guardar lote" else "Guardar cambios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${numero(e.estado.areaHa, 2)} ha · ${e.estado.vertices.size} vértices",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalMarca.current.primario
                )
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(60) },
                    label = { Text("Nombre del lote") },
                    singleLine = true,
                    isError = faltaNombre,
                    supportingText = if (faltaNombre) ({ Text("Poné un nombre para reconocerlo.") }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = provincia,
                    onValueChange = {
                        provincia = it.take(40)
                        escritaPorUsuario = true
                    },
                    label = { Text("Provincia (opcional)") },
                    singleLine = true,
                    trailingIcon = if (deteccion == DeteccionProvinciaWeb.Buscando) {
                        { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                    supportingText = when (val d = deteccion) {
                        DeteccionProvinciaWeb.Buscando -> ({ Text("Buscando la provincia por la ubicación del lote…") })
                        is DeteccionProvinciaWeb.Detectada -> if (!escritaPorUsuario || provincia == d.provincia) ({ Text("Detectada por la ubicación del lote") }) else null
                        is DeteccionProvinciaWeb.NoDisponible -> ({ Text("${d.mensaje}: escribila si querés.") })
                        DeteccionProvinciaWeb.Inactiva -> null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = ::guardar, modifier = Modifier.mano()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = vm::cerrarDialogoGuardar, modifier = Modifier.mano()) { Text("Seguir dibujando") } }
    )
}
