package com.example.web.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.SugerenciaLocalidad
import com.example.ui.viewmodel.SugerenciasState

/**
 * Buscador de localidades con la lista de sugerencias debajo del campo (no en un popup, como
 * BarraBusqueda en Android: así el campo no pierde el foco). Enter elige la primera sugerencia.
 */
@Composable
fun BuscadorLocalidad(
    texto: String,
    alCambiar: (String) -> Unit,
    sugerencias: SugerenciasState,
    alElegir: (SugerenciaLocalidad) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = texto,
            onValueChange = alCambiar,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // Un renglón: en el teléfono el texto de ayuda partido agrandaba el campo
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (texto.isNotEmpty()) {
                    IconButton(onClick = { alCambiar("") }, modifier = Modifier.mano()) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") }
                }
            },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { sugerencias.items.firstOrNull()?.let(alElegir) })
        )
        if (sugerencias.visible) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (sugerencias.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
                sugerencias.error?.let {
                    Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
                sugerencias.items.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider()
                    FilaSugerencia(s) { alElegir(s) }
                }
            }
        }
    }
}

@Composable
private fun FilaSugerencia(s: SugerenciaLocalidad, alTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().mano().clickable(onClick = alTocar).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = LocalMarca.current.primario, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(s.nombre, fontWeight = FontWeight.SemiBold)
            if (s.detalle.isNotBlank()) {
                Text(s.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        s.distanciaKm?.let {
            Text("a ${numero(it)} km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
