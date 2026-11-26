package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AgroGreenPrimary

/**
 * Dato breve del encabezado (estado del mercado, última actualización, ubicación…).
 * [punto] dibuja un indicador de color en lugar del ícono.
 */
@Immutable
data class ChipEncabezado(
    val texto: String,
    val icono: ImageVector? = null,
    val punto: Color? = null
)

/**
 * Encabezado compartido de las pantallas principales: una sola fila con logo, "GRANEX" + sección,
 * una línea de chips de contexto y las acciones. Pinta su fondo detrás de la barra de estado y se
 * corre solo lo necesario ([statusBarsPadding]): el Scaffold principal ya no reserva ese margen.
 */
@Composable
fun GranexHeader(
    titulo: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    chips: List<ChipEncabezado> = emptyList(),
    onRefreshClick: (() -> Unit)? = null,
    refreshButtonTestTag: String = "btn_refresh",
    onConfigurarAlertasClick: (() -> Unit)? = null,
    rightContent: (@Composable () -> Unit)? = null
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 52.dp)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_granex),
                contentDescription = "GRANEX Logo",
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // Si "GRANEX" + sección no entra junto a las acciones (en Mercado, el selector de grano),
                // queda la sección: el logo ya está al lado
                val conMarca = maxWidth >= 150.dp
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conMarca) {
                            Text(
                                text = "GRANEX  ",
                                color = AgroGreenPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = titulo,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (chips.isEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chips.forEach { ChipInfo(it) }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                rightContent?.invoke()
                if (onRefreshClick != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefreshClick()
                        },
                        modifier = Modifier.size(40.dp).testTag(refreshButtonTestTag)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualizar", tint = AgroGreenPrimary)
                    }
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSettings = true
                    },
                    modifier = Modifier.size(40.dp).testTag("btn_user_profile")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Perfil de Usuario",
                        tint = AgroGreenPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    if (showSettings) {
        UserSettingsDialog(
            onDismissRequest = { showSettings = false },
            onConfigurarAlertasClick = {
                onConfigurarAlertasClick?.invoke()
            }
        )
    }
}

@Composable
private fun ChipInfo(chip: ChipEncabezado) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                chip.punto != null -> Box(Modifier.size(6.dp).clip(CircleShape).background(chip.punto))
                chip.icono != null -> Icon(chip.icono, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chip.punto != null || chip.icono != null) Spacer(Modifier.width(4.dp))
            Text(
                text = chip.texto,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
