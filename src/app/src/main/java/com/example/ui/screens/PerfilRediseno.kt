package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.rediseno.TarjetaR
import com.example.ui.rediseno.colorSecundario
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.EstadoPerfil
import com.example.ui.viewmodel.perfilViewModelDeLaApp
import java.util.Locale

/** Rediseño: los datos del productor arriba de Perfil y ajustes (avatar, nombre, establecimiento y correo). */
@Composable
fun TarjetaPerfilR(onEditar: () -> Unit) {
    val estado by perfilViewModelDeLaApp().estado.collectAsStateWithLifecycle()
    val (perfil, correo) = when (val e = estado) {
        is EstadoPerfil.Listo -> e.perfil to e.correo
        is EstadoPerfil.Incompleto -> e.sugerido to e.correo
        is EstadoPerfil.SinConexion -> e.guardado to e.correo
        is EstadoPerfil.Local -> e.perfil to null
        else -> null to null
    }
    TarjetaR(modifier = Modifier.testTag("tarjeta_perfil")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (perfil?.nombre?.trim()?.firstOrNull() ?: 'G').uppercase(),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    perfil?.nombre?.takeIf { it.isNotBlank() } ?: "Tu perfil",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val detalle = listOfNotNull(
                    perfil?.establecimiento?.takeIf { it.isNotBlank() },
                    perfil?.provincia?.takeIf { it.isNotBlank() },
                    perfil?.hectareas?.let { String.format(Locale("es", "AR"), "%,.0f ha", it) }
                ).joinToString(" · ")
                if (detalle.isNotEmpty()) Text(detalle, fontSize = 13.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!correo.isNullOrBlank()) Text(correo, fontSize = 13.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onEditar) { Text("Editar", color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}
