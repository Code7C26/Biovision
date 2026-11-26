package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AgroGreenPrimary

/**
 * Rediseño: la pestaña Mercado junta la pizarra y el historial de granos con el dólar y los futuros
 * (lo que antes era la pestaña Finanzas). Va debajo del encabezado de cada una de las dos vistas.
 */
@Composable
fun SelectorSeccionMercado(enGranos: Boolean, onGranos: () -> Unit, onDolar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(4.dp)
            .selectableGroup()
            .testTag("selector_mercado"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Opcion("Granos", elegida = enGranos, onClick = onGranos, modifier = Modifier.weight(1f).testTag("seccion_granos"))
        Opcion("Dólar y futuros", elegida = !enGranos, onClick = onDolar, modifier = Modifier.weight(1f).testTag("seccion_dolar"))
    }
}

@Composable
private fun Opcion(texto: String, elegida: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (elegida) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        shadowElevation = if (elegida) 1.dp else 0.dp,
        modifier = modifier
            .height(42.dp)
            .selectable(selected = elegida, onClick = onClick, role = Role.Tab)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                texto,
                fontSize = 14.sp,
                fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium,
                color = if (elegida) AgroGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
