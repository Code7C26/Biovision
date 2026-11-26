package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.ValidacionPerfil
import com.example.ui.screens.CamposPerfil
import com.example.ui.screens.formatoHectareas
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.EstadoPerfil
import com.example.ui.viewmodel.perfilViewModelDeLaApp

/**
 * "Ajustes de Perfil": los datos reales del productor (tabla `perfiles`, o solo el teléfono sin
 * backend). El correo es el de la cuenta y no se edita acá.
 */
@Composable
fun UserSettingsDialog(
    onDismissRequest: () -> Unit,
    onConfigurarAlertasClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val perfilVm = perfilViewModelDeLaApp()
    val estado by perfilVm.estado.collectAsStateWithLifecycle()
    val guardando by perfilVm.guardando.collectAsStateWithLifecycle()
    val error by perfilVm.error.collectAsStateWithLifecycle()

    val (actual, correo) = when (val e = estado) {
        is EstadoPerfil.Listo -> e.perfil to e.correo
        is EstadoPerfil.Incompleto -> e.sugerido to e.correo
        is EstadoPerfil.SinConexion -> e.guardado to e.correo
        is EstadoPerfil.Local -> e.perfil to null
        else -> null to null
    }
    val sinConexion = estado is EstadoPerfil.SinConexion

    // Se inicializa con el perfil actual; la clave hace que se recargue cuando llega de la nube
    val clave = actual?.toString()
    var nombre by rememberSaveable(clave) { mutableStateOf(actual?.nombre.orEmpty()) }
    var establecimiento by rememberSaveable(clave) { mutableStateOf(actual?.establecimiento.orEmpty()) }
    var provincia by rememberSaveable(clave) { mutableStateOf(actual?.provincia.orEmpty()) }
    var localidad by rememberSaveable(clave) { mutableStateOf(actual?.localidad.orEmpty()) }
    var hectareas by rememberSaveable(clave) { mutableStateOf(actual?.hectareas?.let(::formatoHectareas).orEmpty()) }
    var intento by remember { mutableStateOf(false) }
    val errores = if (intento) ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas) else ValidacionPerfil.Errores()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ajustes de Perfil",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = AgroGreenPrimary
                        )
                    }
                    IconButton(onClick = onDismissRequest, modifier = Modifier.testTag("btn_close_settings")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text("Datos de la cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (correo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("perfil_correo")) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(correo, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (estado is EstadoPerfil.Cargando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Cargando tu perfil…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (sinConexion) {
                    Text(
                        "Sin conexión: se muestran los datos guardados en el teléfono. Para cambiarlos hace falta internet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CamposPerfil(
                    nombre, { nombre = it }, establecimiento, { establecimiento = it }, provincia, { provincia = it },
                    localidad, { localidad = it }, hectareas, { hectareas = it }, errores
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismissRequest()
                            onConfigurarAlertasClick()
                        }
                        .testTag("btn_configurar_alertas"),
                    colors = CardDefaults.cardColors(containerColor = AgroGreenPrimary.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Configuración", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                                Text(
                                    "Cuenta, notificaciones de Clima, Dólar, Granos y más",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AgroGreenPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        intento = true
                        if (!ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas).hayErrores) {
                            perfilVm.guardar(
                                PerfilNuevo(nombre.trim(), establecimiento.trim(), provincia, localidad.trim(), ValidacionPerfil.hectareas(hectareas)),
                                alGuardar = onDismissRequest
                            )
                        }
                    },
                    enabled = !guardando && !sinConexion && estado !is EstadoPerfil.Cargando,
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_save_settings")
                ) {
                    if (guardando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("Guardar cambios", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
