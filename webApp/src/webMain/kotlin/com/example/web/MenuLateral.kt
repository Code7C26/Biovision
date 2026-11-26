package com.example.web

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.web.plataforma.Navegador
import com.example.web.ui.AccionesApp
import com.example.web.ui.BotonIcono
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalMarca
import com.example.web.ui.LogoGranex
import com.example.web.ui.ModoMenu
import com.example.web.ui.ModoTema
import com.example.web.ui.VERSION_WEB
import com.example.web.ui.mano
import kotlinx.coroutines.delay

/** Ancho del menú abierto. */
private val ANCHO_ABIERTO = 248.dp

/** Ancho del riel de íconos (modo AL_PASAR cerrado). */
private val ANCHO_RIEL = 80.dp

/** Margen lateral del menú: con el riel de 80 dp deja los íconos centrados (12 + 56 / 2 = 40). */
private val MARGEN = 12.dp

/** Espera antes de abrir (cruzar el riel con el mouse no lo abre) y antes de cerrar (no parpadea). */
private const val ESPERA_ABRIR_MS = 120L
private const val ESPERA_CERRAR_MS = 180L

/**
 * Navegación de escritorio (EXPANDIDA) con el menú lateral según [AccionesApp.modoMenu]:
 * - FIJO: menú abierto de ancho completo y el contenido a su derecha.
 * - AL_PASAR: el contenido reserva solo el riel de íconos; al pasar el mouse (o al entrar con Tab)
 *   el menú se abre por encima, con sombra, sin mover el contenido, y se cierra al salir.
 * Pasar de un modo al otro también se anima (el contenido se corre suave).
 * Ojo: el menú abierto se dibuja en el canvas de Compose, así que quedaría DEBAJO de un iframe
 * (mapa, Windy) que estuviera en esos 168 dp; hoy ninguna sección los pone en el borde izquierdo.
 */
@Composable
fun NavegacionEscritorio(
    actual: Seccion,
    ir: (Seccion) -> Unit,
    acciones: AccionesApp,
    contenido: @Composable () -> Unit
) {
    val fijo = acciones.modoMenu == ModoMenu.FIJO
    val interaccion = remember { MutableInteractionSource() }
    val sobre by interaccion.collectIsHoveredAsState()
    var focoTeclado by remember { mutableStateOf(false) }
    val modoEntrada = LocalInputModeManager.current

    // Intención de abrir: mouse encima o navegación con teclado dentro del menú
    var abiertoAlPasar by remember { mutableStateOf(false) }
    LaunchedEffect(sobre, focoTeclado) {
        val quiere = sobre || focoTeclado
        delay(if (quiere) ESPERA_ABRIR_MS else ESPERA_CERRAR_MS)
        abiertoAlPasar = quiere
    }

    val animacion: AnimationSpec<Dp> = remember {
        if (Navegador.prefiereMenosMovimiento()) snap() else tween(durationMillis = 220, easing = FastOutSlowInEasing)
    }
    val reservado by animateDpAsState(if (fijo) ANCHO_ABIERTO else ANCHO_RIEL, animacion, label = "reservado")
    val ancho by animateDpAsState(if (fijo || abiertoAlPasar) ANCHO_ABIERTO else ANCHO_RIEL, animacion, label = "ancho")
    val progreso = ((ancho - ANCHO_RIEL) / (ANCHO_ABIERTO - ANCHO_RIEL)).coerceIn(0f, 1f)
    // Flota sobre el contenido cuando ocupa más de lo que el contenido le reservó
    val flotando = ancho > reservado + 1.dp

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(reservado))
            VerticalDivider()
            Box(Modifier.weight(1f)) { contenido() }
        }
        Surface(
            modifier = Modifier.zIndex(1f).width(ancho).fillMaxHeight()
                .hoverable(interaccion)
                .onFocusChanged { focoTeclado = it.hasFocus && modoEntrada.inputMode == InputMode.Keyboard },
            color = MaterialTheme.colorScheme.background,
            shadowElevation = if (flotando) 8.dp else 0.dp
        ) {
            MenuLateral(actual, ir, acciones, progreso)
        }
    }
}

/** Contenido del menú. [progreso] 0 = riel de íconos, 1 = abierto con nombres. */
@Composable
private fun MenuLateral(actual: Seccion, ir: (Seccion) -> Unit, acciones: AccionesApp, progreso: Float) {
    Column(
        Modifier.fillMaxSize().clipToBounds().padding(horizontal = MARGEN, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LogoGranex(44)
            Column(Modifier.alpha(progreso)) {
                Text(
                    "GRANEX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = LocalMarca.current.primario,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "Agro · Mercados · Clima",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Seccion.navegables.forEach { s -> ItemMenu(s, s == actual.pestana, progreso) { ir(s) } }
        Spacer(Modifier.weight(1f))
        // El pie completo necesita el ancho abierto; mientras se abre o en el riel van solo íconos
        if (progreso > 0.9f) {
            PieCompleto(acciones, Modifier.alpha((progreso - 0.9f) * 10f))
        } else {
            PieRiel(acciones)
        }
    }
}

/**
 * Opción del menú. El ícono ocupa siempre los mismos 56 dp (centrado en el riel), así al abrirse el
 * menú no se mueve: solo aparece el nombre a su derecha.
 */
@Composable
private fun ItemMenu(seccion: Seccion, seleccionado: Boolean, progreso: Float, alTocar: () -> Unit) {
    val colores = MaterialTheme.colorScheme
    val colorContenido = if (seleccionado) colores.onSecondaryContainer else colores.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (seleccionado) colores.secondaryContainer else colores.background)
            .mano()
            .selectable(selected = seleccionado, role = Role.Tab, onClick = alTocar),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            Icon(seccion.icono, contentDescription = null, tint = colorContenido)
        }
        Text(
            seccion.titulo,
            Modifier.alpha(progreso),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Medium,
            color = colorContenido,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Pie con el menú abierto: tema, modo del menú y "Acerca de". */
@Composable
private fun PieCompleto(acciones: AccionesApp, modifier: Modifier) {
    val fijo = acciones.modoMenu == ModoMenu.FIJO
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        acciones.etiquetaCuenta?.let { etiqueta ->
            TextButton(onClick = acciones.abrirCuenta, modifier = Modifier.fillMaxWidth().mano()) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(
                    etiqueta,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "Tema",
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            ModoTema.entries.forEachIndexed { i, modo ->
                SegmentedButton(
                    selected = modo == acciones.modoTema,
                    onClick = { acciones.cambiarModoTema(modo) },
                    shape = SegmentedButtonDefaults.itemShape(i, ModoTema.entries.size),
                    modifier = Modifier.mano(),
                    icon = {},
                    label = { Text(if (modo == ModoTema.SISTEMA) "Auto" else modo.etiqueta, maxLines = 1, fontSize = 12.sp) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Menú fijo", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    if (fijo) "Siempre abierto" else "Se abre al pasar el mouse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Switch(
                checked = fijo,
                onCheckedChange = { acciones.cambiarModoMenu(if (it) ModoMenu.FIJO else ModoMenu.AL_PASAR) },
                modifier = Modifier.mano()
            )
        }
        TextButton(onClick = acciones.abrirAcercaDe, modifier = Modifier.mano()) {
            Icon(Icons.Filled.Info, contentDescription = null, Modifier.padding(end = 8.dp))
            Text("Acerca de · v$VERSION_WEB", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/**
 * Pie del riel cerrado: los mismos controles en íconos. En pantallas táctiles grandes no hay
 * "pasar el mouse", así que desde acá se puede volver a fijar el menú con un toque.
 */
@Composable
private fun PieRiel(acciones: AccionesApp) {
    val siguiente = ModoTema.entries[(acciones.modoTema.ordinal + 1) % ModoTema.entries.size]
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        acciones.etiquetaCuenta?.let { BotonIcono(Icons.Filled.AccountCircle, "Cuenta: $it", acciones.abrirCuenta) }
        BotonIcono(IconosGranex.Fijar, "Fijar el menú abierto") { acciones.cambiarModoMenu(ModoMenu.FIJO) }
        BotonIcono(IconosGranex.Tema, "Tema: ${acciones.modoTema.etiqueta} (cambiar a ${siguiente.etiqueta})") {
            acciones.cambiarModoTema(siguiente)
        }
        BotonIcono(Icons.Filled.Info, "Acerca de GRANEX web", acciones.abrirAcercaDe)
        Spacer(Modifier.size(4.dp))
    }
}
