package com.example.ui.rediseno

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AgroGreenPrimary

/*
 * Rediseño: las piezas que se repiten en todas las pantallas del diseño (tarjetas blancas de 20 dp,
 * recuadros de dato sobre el fondo, etiquetas de estado, selectores segmentados). Usan el esquema de
 * colores del tema, así funcionan con los cuatro temas y en modo oscuro.
 */

@Composable
@ReadOnlyComposable
fun esOscuro(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Texto secundario (etiquetas, aclaraciones). */
val colorSecundario: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Fondo de los recuadros de dato dentro de una tarjeta. */
val fondoTile: Color
    @Composable @ReadOnlyComposable get() =
        if (esOscuro()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background

enum class Tono { OK, ATENCION, ALERTA, INFO, NEUTRO }

data class ColoresTono(val fondo: Color, val texto: Color)

@Composable
@ReadOnlyComposable
fun colores(t: Tono): ColoresTono {
    val oscuro = esOscuro()
    return when (t) {
        Tono.OK -> ColoresTono(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Tono.ATENCION -> if (oscuro) ColoresTono(Color(0xFF422006), Color(0xFFFDE68A)) else ColoresTono(Color(0xFFFEF3C7), Color(0xFF78350F))
        Tono.ALERTA -> if (oscuro) ColoresTono(Color(0xFF450A0A), Color(0xFFFECACA)) else ColoresTono(Color(0xFFFEE2E2), Color(0xFF991B1B))
        Tono.INFO -> if (oscuro) ColoresTono(Color(0xFF082F49), Color(0xFFBAE6FD)) else ColoresTono(Color(0xFFE0F2FE), Color(0xFF075985))
        Tono.NEUTRO -> ColoresTono(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Título grande de una pestaña, con acciones a la derecha; aplica el margen de la barra de estado. */
@Composable
fun EncabezadoR(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    onVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = if (onVolver != null) 4.dp else 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onVolver != null) {
            IconButton(onClick = onVolver, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                titulo, fontSize = if (onVolver != null) 20.sp else 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitulo != null) Text(subtitulo, fontSize = 13.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        acciones()
    }
}

/** Tarjeta blanca del diseño (20 dp de radio, 16 dp de margen interno). */
@Composable
fun TarjetaR(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    borde: BorderStroke? = null,
    padding: Dp = 16.dp,
    espacio: Dp = 12.dp,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, shape = forma, color = color, border = borde, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(espacio), content = contenido)
        }
    } else {
        Surface(shape = forma, color = color, border = borde, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(espacio), content = contenido)
        }
    }
}

/** Título de tarjeta con una acción opcional a la derecha ("Ver todo"). */
@Composable
fun TituloTarjetaR(titulo: String, accion: String? = null, onAccion: (() -> Unit)? = null, extra: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (extra != null) Text(extra, fontSize = 12.sp, color = colorSecundario)
        if (accion != null && onAccion != null) {
            TextButton(onClick = onAccion, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(accion, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/** Título de sección entre tarjetas ("¿Qué puedo hacer hoy?"). */
@Composable
fun SeccionR(texto: String, modifier: Modifier = Modifier) {
    Text(texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(start = 4.dp, top = 4.dp))
}

/** Recuadro de un dato: etiqueta, valor y una aclaración. */
@Composable
fun TileDato(etiqueta: String, valor: String, modifier: Modifier = Modifier, extra: String? = null, colorValor: Color = Color.Unspecified) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(fondoTile).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(etiqueta, fontSize = 12.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colorValor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (extra != null) Text(extra, fontSize = 12.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Recuadros de dato en filas de [columnas], todos del mismo alto por fila. */
@Composable
fun GrillaTiles(datos: List<Triple<String, String, String?>>, columnas: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        datos.chunked(columnas).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                fila.forEach { (e, v, x) -> TileDato(e, v, Modifier.weight(1f).fillMaxSize(), x) }
                repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Etiqueta de estado ("Hoy sí", "Sin riesgo", "Déficit moderado"). */
@Composable
fun ChipEstadoR(texto: String, tono: Tono, modifier: Modifier = Modifier) {
    val c = colores(tono)
    Text(
        texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.texto, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(c.fondo).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Aviso dentro de una tarjeta o entre tarjetas. */
@Composable
fun AvisoR(texto: String, tono: Tono, modifier: Modifier = Modifier, icono: ImageVector? = null) {
    val c = colores(tono)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.fondo).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icono != null) Icon(icono, contentDescription = null, tint = c.texto, modifier = Modifier.size(18.dp))
        Text(texto, fontSize = 13.sp, color = c.texto)
    }
}

/** Selector segmentado (Granos / Dólar, Vigor / Foto / Relieve…). */
@Composable
fun SegmentadoR(opciones: List<String>, elegida: Int, modifier: Modifier = Modifier, alto: Dp = 42.dp, onElegir: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        opciones.forEachIndexed { i, o ->
            val sel = i == elegida
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (sel) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (sel) 1.dp else 0.dp,
                modifier = Modifier.weight(1f).height(alto).selectable(selected = sel, role = Role.Tab, onClick = { onElegir(i) })
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        o, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) AgroGreenPrimary else colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Chip que se elige (ciudades guardadas, fechas, rangos). */
@Composable
fun ChipElegibleR(texto: String, elegido: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (elegido) Color(0xFF064E3B) else MaterialTheme.colorScheme.surface,
        border = if (elegido) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 36.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                texto, fontSize = 13.sp, fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Medium,
                color = if (elegido) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
        }
    }
}

@Composable
fun BotonPrimarioR(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, habilitado: Boolean = true) {
    Button(
        onClick = onClick, enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary, contentColor = Color.White),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        if (icono != null) { Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BotonSecundarioR(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = habilitado,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp)
    ) {
        if (icono != null) { Icon(icono, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AgroGreenPrimary)
    }
}

/** Fila que abre otra pantalla: ícono en círculo, título, detalle y flecha. */
@Composable
fun FilaNavegableR(
    titulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    icono: ImageVector? = null,
    tonoIcono: Tono = Tono.INFO,
    final: String? = null
) {
    TarjetaR(modifier = modifier, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icono != null) {
                val c = colores(tonoIcono)
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.fondo), contentAlignment = Alignment.Center) {
                    Icon(icono, contentDescription = null, tint = c.texto, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (detalle != null) Text(detalle, fontSize = 13.sp, color = colorSecundario)
            }
            if (final != null) Text(final, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AgroGreenPrimary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colorSecundario)
        }
    }
}

/** Leyenda de un color ("■ Apta"). */
@Composable
fun LeyendaR(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(texto, fontSize = 12.sp)
    }
}
