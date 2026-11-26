package com.example.web.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * El kit del rediseño de la app Android (ui/rediseno/Kit.kt) para la web: tarjetas de 20 dp sin sombra
 * sobre el fondo, recuadros de dato, etiquetas de estado y selectores segmentados. Usan el esquema de
 * colores del tema, así funcionan en claro y oscuro. Mantener el mismo aspecto que en Android.
 */

/** Texto secundario (etiquetas, aclaraciones). */
val colorSecundario: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Fondo de los recuadros de dato dentro de una tarjeta. */
val fondoTile: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalTemaOscuro.current) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background

enum class Tono { OK, ATENCION, ALERTA, INFO, NEUTRO }

data class ColoresTono(val fondo: Color, val texto: Color)

@Composable
@ReadOnlyComposable
fun colores(t: Tono): ColoresTono {
    val oscuro = LocalTemaOscuro.current
    return when (t) {
        Tono.OK -> ColoresTono(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Tono.ATENCION -> if (oscuro) ColoresTono(Color(0xFF422006), Color(0xFFFDE68A)) else ColoresTono(Color(0xFFFEF3C7), Color(0xFF78350F))
        Tono.ALERTA -> if (oscuro) ColoresTono(Color(0xFF450A0A), Color(0xFFFECACA)) else ColoresTono(Color(0xFFFEE2E2), Color(0xFF991B1B))
        Tono.INFO -> if (oscuro) ColoresTono(Color(0xFF082F49), Color(0xFFBAE6FD)) else ColoresTono(Color(0xFFE0F2FE), Color(0xFF075985))
        Tono.NEUTRO -> ColoresTono(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tarjeta del diseño (20 dp de radio, sin sombra, 16 dp de margen interno). */
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
        Surface(onClick = onClick, shape = forma, color = color, border = borde, modifier = modifier.fillMaxWidth().mano()) {
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
            TextButton(onClick = onAccion, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.mano()) {
                Text(accion, color = LocalMarca.current.primario, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
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

/** Etiqueta de estado ("Sin conexión", "Hoy sí", "Sin subir"). */
@Composable
fun ChipEstadoR(texto: String, tono: Tono, modifier: Modifier = Modifier) {
    val c = colores(tono)
    Text(
        texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.texto, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(c.fondo).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Selector segmentado (Granos / Dólar y futuros, vistas de una pantalla). */
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
                modifier = Modifier.weight(1f).height(alto).mano()
                    .selectable(selected = sel, role = Role.Tab, onClick = { onElegir(i) })
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        o, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) LocalMarca.current.primario else colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Fila que abre otra sección: ícono en círculo, título, detalle y flecha. */
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
            if (final != null) Text(final, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalMarca.current.primario)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colorSecundario)
        }
    }
}
