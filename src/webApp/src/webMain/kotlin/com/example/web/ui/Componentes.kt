package com.example.web.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.web.plataforma.Navegador
import com.example.web.plataforma.TaparMarcos
import com.example.web.recursos.Res
import com.example.web.recursos.logo_granex
import org.jetbrains.compose.resources.painterResource

/** Cursor de mano sobre lo que se puede tocar (en la web Compose no lo pone solo). */
fun Modifier.mano(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/** Acciones globales que el encabezado ofrece en el teléfono (en pantallas anchas están en el menú). */
@Immutable
data class AccionesApp(
    val modoTema: ModoTema = ModoTema.SISTEMA,
    val cambiarModoTema: (ModoTema) -> Unit = {},
    val modoMenu: ModoMenu = ModoMenu.FIJO,
    val cambiarModoMenu: (ModoMenu) -> Unit = {},
    val abrirAcercaDe: () -> Unit = {},
    val abrirCuenta: () -> Unit = {},
    /** "Ingresar", el correo de la sesión, o null sin backend (entonces no se ofrece la cuenta). */
    val etiquetaCuenta: String? = null
)

val LocalAccionesApp = staticCompositionLocalOf { AccionesApp() }

/** false cuando el navegador avisa que no hay red (los datos en pantalla pueden ser los guardados). */
val LocalConectado = compositionLocalOf { true }

/** Logo cuadrado (recorte del emblema de la app). */
@Composable
fun LogoGranex(tamano: Int = 36) {
    Image(
        painter = painterResource(Res.drawable.logo_granex),
        contentDescription = "Logo de GRANEX",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(tamano.dp).clip(RoundedCornerShape((tamano * 0.28f).dp))
    )
}

/**
 * Encabezado de sección con el diseño de la app Android (EncabezadoR): título grande sobre el fondo, el
 * contexto ([chips]: ciudad, campaña, hora de actualización) en una línea debajo, y las acciones a la
 * derecha. [onVolver] pone la flecha para volver (Noticias vuelve al Inicio). En el teléfono suma el
 * menú ⋮ con el tema, la cuenta y "Acerca de".
 */
@Composable
fun Encabezado(
    titulo: String,
    chips: List<String> = emptyList(),
    onVolver: (() -> Unit)? = null,
    acciones: @Composable () -> Unit = {}
) {
    val compacta = LocalClaseAncho.current == ClaseAncho.COMPACTA
    Row(
        Modifier.fillMaxWidth().padding(
            start = if (onVolver != null) 4.dp else EspacioPanel, end = if (compacta) 4.dp else 12.dp, top = 12.dp, bottom = 4.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onVolver != null) BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onVolver)
        Column(Modifier.weight(1f)) {
            Text(
                titulo, fontSize = if (onVolver != null) 20.sp else 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (chips.isNotEmpty()) {
                Text(
                    chips.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!LocalConectado.current) ChipEstadoR("Sin conexión", Tono.ALERTA)
        Row(verticalAlignment = Alignment.CenterVertically) {
            acciones()
            if (compacta) MenuApp()
        }
    }
}

/** Fila con scroll horizontal que desvanece el borde por donde sigue (chips de filtros, capas…). */
@Composable
fun Modifier.desplazableHorizontal(): Modifier {
    val estado = rememberScrollState()
    return desvanecerBordes(estado).horizontalScroll(estado)
}

/** Desvanece el borde por donde la fila sigue (en lugar de cortar un chip a la mitad). */
private fun Modifier.desvanecerBordes(estado: ScrollState): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val ancho = 24.dp.toPx().coerceAtMost(size.width / 3)
        if (estado.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - ancho, endX = size.width),
                blendMode = BlendMode.DstIn
            )
        }
        if (estado.canScrollBackward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = ancho),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Menú ⋮ del teléfono: tema, cuenta y "Acerca de" (lo usan el Encabezado y el saludo del Inicio). */
@Composable
fun MenuApp() {
    val acciones = LocalAccionesApp.current
    var abierto by remember { mutableStateOf(false) }
    Box {
        BotonIcono(Icons.Filled.MoreVert, "Más opciones") { abierto = true }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            TaparMarcos()
            Text(
                "Tema",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModoTema.entries.forEach { modo ->
                DropdownMenuItem(
                    text = { Text(modo.etiqueta) },
                    onClick = {
                        acciones.cambiarModoTema(modo)
                        abierto = false
                    },
                    modifier = Modifier.mano(),
                    leadingIcon = {
                        if (modo == acciones.modoTema) Icon(Icons.Filled.Check, contentDescription = "Elegido")
                        else Spacer(Modifier.size(24.dp))
                    }
                )
            }
            HorizontalDivider()
            acciones.etiquetaCuenta?.let { etiqueta ->
                DropdownMenuItem(
                    text = { Text(etiqueta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        abierto = false
                        acciones.abrirCuenta()
                    },
                    modifier = Modifier.mano(),
                    leadingIcon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text("Acerca de GRANEX web") },
                onClick = {
                    abierto = false
                    acciones.abrirAcercaDe()
                },
                modifier = Modifier.mano(),
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
            )
        }
    }
}

@Composable
fun ChipEncabezado(texto: String, color: Color? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            texto,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (color != null) FontWeight.SemiBold else null,
            color = color ?: MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

/** Botón de ícono con su descripción como tooltip al pasar el mouse (y para lectores de pantalla). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, alTocar: () -> Unit) = BotonIcono(icono, descripcion, true, alTocar)

/** Igual, deshabilitable (deshacer sin historial, por ejemplo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, habilitado: Boolean, alTocar: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(descripcion) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = alTocar, enabled = habilitado, modifier = if (habilitado) Modifier.mano() else Modifier) {
            Icon(icono, contentDescription = descripcion)
        }
    }
}

/** Panel con título: la unidad que se reparte en columnas en escritorio. */
@Composable
fun Panel(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    // Tarjeta del rediseño: 20 dp, sin sombra, sobre el fondo (como TarjetaR en Android)
    TarjetaR(modifier, espacio = 10.dp) {
        Column {
            Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitulo != null) {
                Text(subtitulo, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        contenido()
    }
}

/**
 * Panel "fantasma" mientras llegan los datos: la página ya muestra su estructura en lugar de un
 * spinner solo (el texto de [descripcion] lo leen los lectores de pantalla).
 */
@Composable
fun PanelEsqueleto(descripcion: String, lineas: Int = 3, modifier: Modifier = Modifier) {
    val pulso by rememberInfiniteTransition(label = "esqueleto").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulso"
    )
    val relleno = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    Panel(descripcion, modifier) {
        Column(Modifier.alpha(pulso), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(lineas) { i ->
                Box(
                    Modifier.fillMaxWidth(if (i % 2 == 0) 0.85f else 0.6f).height(14.dp)
                        .clip(RoundedCornerShape(7.dp)).background(relleno)
                )
            }
        }
    }
}

@Composable
fun Cargando(texto: String = "Cargando…") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorConReintento(mensaje: String, alReintentar: () -> Unit) {
    val conectado = LocalConectado.current
    Panel(if (conectado) "No se pudieron cargar los datos" else "Sin conexión") {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = LocalMarca.current.alerta, modifier = Modifier.size(20.dp))
            Text(
                if (conectado) mensaje else "No hay internet y todavía no hay datos guardados de esta sección. Se reintenta al volver la conexión.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(onClick = alReintentar, modifier = Modifier.mano()) { Text("Reintentar") }
    }
}

/** Aviso informativo (por qué algo no está disponible en la web, de dónde salen los datos…). */
@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier, color: Color = LocalMarca.current.info) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.10f)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(texto, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Etiqueta chica de color (brecha, variación, estado). */
@Composable
fun Insignia(texto: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            texto,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

/** Atribución de la fuente de datos (obligatoria para Open-Meteo y granos.ar), con enlace. */
@Composable
fun Fuente(texto: String, url: String? = null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (url == null) {
            Text(texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = { Navegador.abrirEnPestana(url) }, modifier = Modifier.mano()) {
                Text(texto, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(IconosGranex.AbrirAfuera, contentDescription = "(abre otra pestaña)", modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Fila etiqueta / valor de las tarjetas de datos. */
@Composable
fun Dato(etiqueta: String, valor: String, colorValor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colorValor)
    }
}
