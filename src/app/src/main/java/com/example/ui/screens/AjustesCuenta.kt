package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.ValidacionCuenta
import com.example.data.sync.AvisoCuenta
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.TipoAviso
import com.example.ui.components.CampoCorreo
import com.example.ui.components.CampoSecreto
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.CuentaViewModel

/** Acciones de la tarjeta de cuenta (separadas del ViewModel para poder probar la tarjeta sola). */
class AccionesCuenta(
    val ingresar: (correo: String, clave: String) -> Unit,
    val registrarse: (correo: String, clave: String, confirmacion: String) -> Unit,
    val sincronizar: () -> Unit,
    val cerrarSesion: () -> Unit,
    val descartarAviso: () -> Unit,
    val eliminarCuenta: () -> Unit = {}
)

/** Sección "Cuenta y sincronización" de Ajustes. */
@Composable
fun SeccionCuenta(viewModel: CuentaViewModel) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val operacion by viewModel.operacion.collectAsStateWithLifecycle()
    val aviso by viewModel.aviso.collectAsStateWithLifecycle()
    val pendientes by viewModel.pendientes.collectAsStateWithLifecycle()
    TarjetaCuenta(
        estado = estado,
        operacion = operacion,
        aviso = aviso,
        pendientes = pendientes,
        acciones = remember(viewModel) {
            AccionesCuenta(
                ingresar = viewModel::ingresar,
                registrarse = viewModel::registrarse,
                sincronizar = viewModel::sincronizarAhora,
                cerrarSesion = viewModel::cerrarSesion,
                descartarAviso = viewModel::descartarAviso,
                eliminarCuenta = viewModel::eliminarCuenta
            )
        }
    )
}

@Composable
fun TarjetaCuenta(
    estado: EstadoCuenta,
    operacion: OperacionCuenta,
    aviso: AvisoCuenta?,
    pendientes: Int,
    acciones: AccionesCuenta,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Cuenta y sincronización",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AgroGreenPrimary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().testTag("tarjeta_cuenta"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (estado) {
                    EstadoCuenta.SinBackend -> FilaEstado(
                        Icons.Default.CloudOff,
                        "La sincronización en la nube no está disponible en esta versión. Tus lotes se guardan solo en este teléfono."
                    )
                    EstadoCuenta.Verificando -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Verificando sesión…", style = MaterialTheme.typography.bodyMedium)
                    }
                    EstadoCuenta.SinSesion -> FormularioCuenta(operacion, aviso, pendientes, acciones)
                    is EstadoCuenta.Conectado -> PanelSesion(estado.correo, sinConexion = false, operacion, aviso, pendientes, acciones)
                    EstadoCuenta.SinConexion -> PanelSesion(null, sinConexion = true, operacion, aviso, pendientes, acciones)
                }
            }
        }
    }

    val cierreBloqueado = aviso?.takeIf { it.tipo == TipoAviso.CIERRE_BLOQUEADO }
    if (cierreBloqueado != null) {
        AlertDialog(
            onDismissRequest = acciones.descartarAviso,
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("No se cerró la sesión") },
            text = { Text(cierreBloqueado.mensaje) },
            confirmButton = {
                TextButton(onClick = acciones.cerrarSesion, modifier = Modifier.testTag("btn_reintentar_cierre")) {
                    Text("Reintentar")
                }
            },
            dismissButton = { TextButton(onClick = acciones.descartarAviso) { Text("Entendido") } },
            modifier = Modifier.testTag("dialogo_cierre_bloqueado")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioCuenta(operacion: OperacionCuenta, aviso: AvisoCuenta?, pendientes: Int, acciones: AccionesCuenta) {
    var creandoCuenta by rememberSaveable { mutableStateOf(false) }
    var correo by rememberSaveable { mutableStateOf("") }
    // Las contraseñas nunca van a rememberSaveable (quedarían en el estado guardado de la Activity)
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    // Los errores por campo aparecen recién después del primer intento de enviar
    var intentado by rememberSaveable { mutableStateOf(false) }
    val errores = if (intentado) {
        ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta)
    } else ValidacionCuenta.Errores()
    val ocupado = operacion != OperacionCuenta.NINGUNA

    // Cuenta creada que hay que confirmar por correo: el siguiente paso es ingresar
    LaunchedEffect(aviso) {
        if (aviso?.tipo == TipoAviso.INFO && creandoCuenta) {
            creandoCuenta = false
            confirmacion = ""
            intentado = false
        }
    }

    fun enviar() {
        intentado = true
        if (ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta).hayErrores) return
        if (creandoCuenta) acciones.registrarse(correo, clave, confirmacion) else acciones.ingresar(correo, clave)
    }

    Text(
        text = "Con una cuenta tus lotes se guardan en la nube y se sincronizan entre tus dispositivos. " +
            "Sin cuenta quedan solo en este teléfono.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (pendientes > 0) {
        FilaEstado(
            Icons.Default.CloudUpload,
            if (pendientes == 1) "Tenés 1 cambio de lotes en este teléfono: se sube a la cuenta con la que ingreses."
            else "Tenés $pendientes cambios de lotes en este teléfono: se suben a la cuenta con la que ingreses."
        )
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to "Iniciar sesión", true to "Crear cuenta").forEachIndexed { i, (modo, texto) ->
            SegmentedButton(
                selected = creandoCuenta == modo,
                onClick = {
                    if (creandoCuenta != modo) {
                        creandoCuenta = modo
                        confirmacion = ""
                        intentado = false
                        acciones.descartarAviso()
                    }
                },
                enabled = !ocupado,
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AgroGreenPrimary.copy(alpha = 0.16f),
                    activeContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag(if (modo) "tab_crear_cuenta" else "tab_iniciar_sesion")
            ) { Text(texto) }
        }
    }

    CampoCorreo(
        valor = correo,
        onValorChange = { correo = it },
        error = errores.correo,
        modifier = Modifier.fillMaxWidth(),
        testTag = "input_cuenta_correo"
    )
    CampoSecreto(
        valor = clave,
        onValorChange = { clave = it },
        etiqueta = "Contraseña",
        error = errores.clave,
        imeAction = if (creandoCuenta) ImeAction.Next else ImeAction.Done,
        alConfirmar = if (creandoCuenta) null else { { enviar() } },
        modifier = Modifier.fillMaxWidth(),
        testTag = "input_cuenta_clave"
    )
    if (creandoCuenta) {
        CampoSecreto(
            valor = confirmacion,
            onValorChange = { confirmacion = it },
            etiqueta = "Repetir contraseña",
            error = errores.confirmacion,
            alConfirmar = { enviar() },
            modifier = Modifier.fillMaxWidth(),
            testTag = "input_cuenta_confirmacion"
        )
        Text(
            text = "Mínimo ${ValidacionCuenta.LARGO_MINIMO_CLAVE} caracteres, combinando letras y números.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoEnLinea(it, acciones.descartarAviso) }

    Button(
        onClick = { enviar() },
        enabled = !ocupado,
        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().testTag("btn_cuenta_enviar")
    ) {
        if (ocupado) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            when (operacion) {
                OperacionCuenta.INGRESANDO -> "Ingresando…"
                OperacionCuenta.REGISTRANDO -> "Creando cuenta…"
                OperacionCuenta.SINCRONIZANDO -> "Sincronizando lotes…"
                else -> if (creandoCuenta) "Crear cuenta" else "Iniciar sesión"
            }
        )
    }
}

@Composable
private fun PanelSesion(
    correo: String?,
    sinConexion: Boolean,
    operacion: OperacionCuenta,
    aviso: AvisoCuenta?,
    pendientes: Int,
    acciones: AccionesCuenta
) {
    var confirmarCierre by rememberSaveable { mutableStateOf(false) }
    var confirmarEliminacion by rememberSaveable { mutableStateOf(false) }
    val ocupado = operacion != OperacionCuenta.NINGUNA

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = correo ?: "Cuenta GRANEX",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("cuenta_correo")
            )
            Text(
                text = if (sinConexion) "Sin conexión: la sesión se renueva cuando vuelva la señal." else "Sesión iniciada",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    when {
        operacion == OperacionCuenta.SINCRONIZANDO || operacion == OperacionCuenta.CERRANDO_SESION ||
            operacion == OperacionCuenta.ELIMINANDO ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AgroGreenPrimary)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (operacion) {
                        OperacionCuenta.CERRANDO_SESION -> "Subiendo cambios antes de cerrar sesión…"
                        OperacionCuenta.ELIMINANDO -> "Eliminando la cuenta…"
                        else -> "Sincronizando lotes…"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        pendientes == 0 -> FilaEstado(Icons.Default.CloudDone, "Lotes sincronizados con la nube.")
        else -> FilaEstado(
            Icons.Default.CloudUpload,
            (if (pendientes == 1) "1 cambio pendiente de subir." else "$pendientes cambios pendientes de subir.") +
                " Se suben solos cuando hay conexión."
        )
    }

    aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoEnLinea(it, acciones.descartarAviso) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = acciones.sincronizar,
            enabled = !ocupado,
            modifier = Modifier.weight(1f).testTag("btn_sincronizar")
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sincronizar")
        }
        Button(
            onClick = { confirmarCierre = true },
            enabled = !ocupado,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.weight(1f).testTag("btn_cerrar_sesion")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cerrar sesión")
        }
    }

    // Google Play y el App Store exigen poder borrar la cuenta desde la app
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { confirmarEliminacion = true },
            enabled = !ocupado && !sinConexion,
            modifier = Modifier.testTag("btn_eliminar_cuenta")
        ) {
            Text("Eliminar cuenta", color = if (!ocupado && !sinConexion) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
    }

    if (confirmarEliminacion) {
        AlertDialog(
            onDismissRequest = { confirmarEliminacion = false },
            title = { Text("¿Eliminar la cuenta?") },
            text = {
                Text(
                    "Se borran para siempre tu cuenta, tus lotes (en la nube y en este teléfono), los archivos " +
                        "guardados y los análisis de suelo, mapas de rinde y dosis de este teléfono. No se puede deshacer." +
                        if (pendientes > 0) "\n\nTambién se pierden los cambios que todavía no se subieron." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminacion = false
                        acciones.eliminarCuenta()
                    },
                    modifier = Modifier.testTag("btn_confirmar_eliminacion")
                ) { Text("Eliminar para siempre", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminacion = false }) { Text("Cancelar") } }
        )
    }

    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Antes de salir se suben los cambios pendientes. Después se quitan los lotes de este teléfono: " +
                        "siguen guardados en tu cuenta y vuelven a aparecer al ingresar." +
                        if (pendientes > 0) "\n\nHay cambios sin subir: hace falta conexión a internet." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarCierre = false
                        acciones.cerrarSesion()
                    },
                    modifier = Modifier.testTag("btn_confirmar_cierre")
                ) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FilaEstado(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AvisoEnLinea(aviso: AvisoCuenta, descartar: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    val (fondo, texto, icono) = when (aviso.tipo) {
        TipoAviso.EXITO -> Triple(AgroGreenPrimary.copy(alpha = 0.12f), esquema.onSurface, Icons.Default.CheckCircle)
        TipoAviso.INFO -> Triple(esquema.secondaryContainer, esquema.onSecondaryContainer, Icons.Default.Info)
        TipoAviso.ERROR, TipoAviso.CIERRE_BLOQUEADO -> Triple(esquema.errorContainer, esquema.onErrorContainer, Icons.Default.ErrorOutline)
    }
    Surface(color = fondo, contentColor = texto, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("aviso_cuenta")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
            Icon(
                icono,
                contentDescription = null,
                tint = if (aviso.tipo == TipoAviso.EXITO) AgroGreenPrimary else texto,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(aviso.mensaje, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            IconButton(onClick = descartar) { Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", modifier = Modifier.size(18.dp)) }
        }
    }
}
