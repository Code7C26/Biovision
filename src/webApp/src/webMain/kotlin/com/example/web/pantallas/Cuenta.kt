package com.example.web.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.ValidacionCuenta
import com.example.data.sync.AvisoCuenta
import com.example.data.sync.ControladorCuenta
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.ResultadoSincronizacion
import com.example.data.sync.TipoAviso
import com.example.web.plataforma.Navegador
import com.example.web.plataforma.PaginaPropia
import com.example.web.ui.Aviso
import com.example.web.ui.Encabezado
import com.example.web.ui.EspacioPanel
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.mano
import com.example.web.viewmodel.CuentaWebViewModel
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.example.data.remote.supabase.PROVINCIAS_ARGENTINAS
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.ValidacionPerfil

/**
 * Cuenta GRANEX (Supabase Auth). Con cuenta, los lotes dibujados en la web se guardan en la nube y
 * aparecen en la app Android (y al revés). Con backend, la web no se usa sin sesión.
 * Las contraseñas se guardan con remember (nunca persistidas) y nunca se escriben en localStorage:
 * ahí solo va la sesión de Supabase.
 */
@Composable
fun PantallaCuenta(vm: CuentaWebViewModel, lotes: Int, pendientes: Int) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val operacion by vm.operacion.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val ultima by vm.ultimaSincronizacion.collectAsStateWithLifecycle()
    val porConfirmar by vm.correoPorConfirmar.collectAsStateWithLifecycle()
    var recuperando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Cuenta",
            chips = listOfNotNull(
                when (estado) {
                    is EstadoCuenta.Conectado -> "Sesión iniciada"
                    EstadoCuenta.SinSesion -> "Sin sesión"
                    else -> null
                },
                if (pendientes > 0) "$pendientes sin subir" else null
            )
        )
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                when (val e = estado) {
                    EstadoCuenta.SinBackend -> Panel("Cuenta GRANEX") {
                        Aviso(
                            "Esta versión web no tiene configurado el servidor de GRANEX. Los lotes que dibujes " +
                                "quedan guardados solo en este navegador."
                        )
                    }
                    EstadoCuenta.Verificando -> PanelEsqueleto("Verificando sesión…", lineas = 3)
                    EstadoCuenta.SinSesion -> {
                        val correoPendiente = porConfirmar
                        when {
                            correoPendiente != null -> Panel("Activá tu cuenta") {
                                ConfirmarCorreo(correoPendiente, operacion, aviso, vm)
                            }
                            recuperando -> Panel("Cambiar la contraseña") {
                                RecuperarClave(operacion, aviso, vm) {
                                    vm.descartarAviso()
                                    recuperando = false
                                }
                            }
                            else -> Panel("Ingresar a GRANEX") {
                                FormularioCuenta(operacion, aviso, pendientes, vm) {
                                    vm.descartarAviso()
                                    recuperando = true
                                }
                            }
                        }
                    }
                    is EstadoCuenta.Conectado -> PanelSesion(e.correo, sinConexion = false, operacion, aviso, lotes, pendientes, ultima, vm)
                    EstadoCuenta.SinConexion -> PanelSesion(null, sinConexion = true, operacion, aviso, lotes, pendientes, ultima, vm)
                }
                Text(
                    "La misma cuenta sirve en la app Android: tus lotes y estaciones se sincronizan entre dispositivos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Cierre de sesión bloqueado por cambios sin subir: alerta, como en Android
    aviso?.takeIf { it.tipo == TipoAviso.CIERRE_BLOQUEADO }?.let { a ->
        AlertDialog(
            onDismissRequest = vm::descartarAviso,
            title = { Text("No se cerró la sesión") },
            text = { Text(a.mensaje) },
            confirmButton = {
                TextButton(onClick = { vm.descartarAviso(); vm.cerrarSesion() }, modifier = Modifier.mano()) { Text("Reintentar") }
            },
            dismissButton = { TextButton(onClick = vm::descartarAviso, modifier = Modifier.mano()) { Text("Entendido") } }
        )
    }
}

@Composable
private fun FormularioCuenta(operacion: OperacionCuenta, aviso: AvisoCuenta?, pendientes: Int, vm: CuentaWebViewModel, alOlvidarClave: () -> Unit) {
    var creandoCuenta by remember { mutableStateOf(false) }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    // Datos del productor, como el registro de Android: van con la cuenta y crean la fila de `perfiles`
    var nombre by remember { mutableStateOf("") }
    var establecimiento by remember { mutableStateOf("") }
    var provincia by remember { mutableStateOf("") }
    var localidad by remember { mutableStateOf("") }
    var hectareas by remember { mutableStateOf("") }
    var acepta by remember { mutableStateOf(false) }
    // Los errores por campo aparecen recién después del primer intento de enviar
    var intentado by remember { mutableStateOf(false) }
    val errores = if (intentado) ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta) else ValidacionCuenta.Errores()
    val erroresPerfil = if (intentado && creandoCuenta) ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas) else ValidacionPerfil.Errores()
    val ocupado = operacion != OperacionCuenta.NINGUNA

    fun enviar() {
        intentado = true
        if (ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta).hayErrores) return
        if (!creandoCuenta) {
            vm.ingresar(correo, clave)
            return
        }
        if (ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas).hayErrores || !acepta) return
        vm.registrarse(
            correo, clave, confirmacion,
            PerfilNuevo(nombre.trim(), establecimiento.trim(), provincia, localidad.trim(), ValidacionPerfil.hectareas(hectareas))
        )
    }

    Text(
        "Ingresá con tu cuenta GRANEX, la misma de la app Android. Tus lotes, estaciones y perfil se guardan " +
            "en la nube y se sincronizan entre dispositivos.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (pendientes > 0) {
        FilaEstado(
            IconosGranex.Subir,
            if (pendientes == 1) "Tenés 1 cambio de lotes en este navegador: se sube a la cuenta con la que ingreses."
            else "Tenés $pendientes cambios de lotes en este navegador: se suben a la cuenta con la que ingreses."
        )
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false to "Iniciar sesión", true to "Crear cuenta").forEachIndexed { i, (modo, texto) ->
            SegmentedButton(
                selected = creandoCuenta == modo,
                onClick = {
                    if (creandoCuenta != modo) {
                        creandoCuenta = modo
                        confirmacion = ""
                        intentado = false
                        vm.descartarAviso()
                    }
                },
                enabled = !ocupado,
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                modifier = Modifier.mano(),
                icon = {}
            ) { Text(texto) }
        }
    }
    if (creandoCuenta) {
        Text("Tus datos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        CampoTexto(nombre, { nombre = it.take(120) }, "Nombre y apellido", erroresPerfil.nombre)
        CampoTexto(establecimiento, { establecimiento = it.take(120) }, "Establecimiento o empresa", erroresPerfil.establecimiento)
        SelectorProvincia(provincia, { provincia = it }, erroresPerfil.provincia)
        CampoTexto(localidad, { localidad = it.take(120) }, "Localidad (opcional)", null)
        CampoTexto(hectareas, { hectareas = it.take(12) }, "Hectáreas (opcional)", erroresPerfil.hectareas, KeyboardType.Decimal)
        HorizontalDivider()
        Text("Tu acceso", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
    OutlinedTextField(
        value = correo,
        onValueChange = { correo = it },
        label = { Text("Correo") },
        singleLine = true,
        isError = errores.correo != null,
        supportingText = errores.correo?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    CampoClave(
        valor = clave,
        alCambiar = { clave = it },
        etiqueta = "Contraseña",
        error = errores.clave,
        ultimo = !creandoCuenta,
        alConfirmar = ::enviar
    )
    if (creandoCuenta) {
        CampoClave(
            valor = confirmacion,
            alCambiar = { confirmacion = it },
            etiqueta = "Repetir contraseña",
            error = errores.confirmacion,
            ultimo = true,
            alConfirmar = ::enviar
        )
        Text(
            "Mínimo ${ValidacionCuenta.LARGO_MINIMO_CLAVE} caracteres, combinando letras y números.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Antes de dejar el correo, qué se hace con él (Ley 25.326); la aceptación es explícita, como en Android
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acepta, onCheckedChange = { acepta = it }, modifier = Modifier.mano())
            Text(
                "Acepto los Términos de Servicio y la Política de Privacidad de GRANEX: guardan mi correo, mi perfil y " +
                    "mis lotes para sincronizarlos, y puedo pedir que se borren cuando quiera.",
                style = MaterialTheme.typography.bodySmall,
                color = if (intentado && !acepta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Row {
            PaginaPropia.entries.forEach { pagina ->
                TextButton(onClick = { Navegador.abrirPaginaPropia(pagina) }, modifier = Modifier.mano()) {
                    Text(pagina.titulo, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (!creandoCuenta) {
        TextButton(onClick = alOlvidarClave, enabled = !ocupado, modifier = Modifier.mano()) { Text("¿Olvidaste tu contraseña?") }
    }
    aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoCuentaEnLinea(it) }
    Button(onClick = ::enviar, enabled = !ocupado, modifier = Modifier.fillMaxWidth().mano()) {
        if (ocupado) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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

/**
 * Cuenta creada que falta activar: el correo trae un código de 6 dígitos (plantilla "Confirm signup"
 * de Supabase con {{ .Token }}). Aparece después de crear la cuenta o al ingresar con una sin activar.
 */
@Composable
private fun ConfirmarCorreo(correo: String, operacion: OperacionCuenta, aviso: AvisoCuenta?, vm: CuentaWebViewModel) {
    var codigo by remember { mutableStateOf("") }
    val ocupado = operacion != OperacionCuenta.NINGUNA
    val confirmar = { if (!ocupado) vm.confirmarCorreo(codigo) }
    Text(
        "Te enviamos un código de 6 dígitos a $correo. Escribilo acá para activar tu cuenta. " +
            "Si no lo ves, revisá el correo no deseado.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CampoCodigo(codigo, { codigo = it }, ultimo = true, alConfirmar = confirmar)
    aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoCuentaEnLinea(it) }
    Button(onClick = confirmar, enabled = !ocupado && codigo.length >= 6, modifier = Modifier.fillMaxWidth().mano()) {
        if (ocupado) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            when (operacion) {
                OperacionCuenta.INGRESANDO -> "Activando…"
                OperacionCuenta.REGISTRANDO -> "Enviando código…"
                OperacionCuenta.SINCRONIZANDO -> "Sincronizando lotes…"
                else -> "Activar cuenta"
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = vm::reenviarConfirmacion, enabled = !ocupado, modifier = Modifier.mano()) { Text("Reenviar código") }
        TextButton(onClick = vm::cancelarConfirmacion, enabled = !ocupado, modifier = Modifier.mano()) { Text("Usar otro correo") }
    }
}

/**
 * "¿Olvidaste tu contraseña?": primero se pide un código al correo de la cuenta y después, con el
 * código, se elige la contraseña nueva (sin enlaces: plantilla "Reset Password" con {{ .Token }}).
 */
@Composable
private fun RecuperarClave(operacion: OperacionCuenta, aviso: AvisoCuenta?, vm: CuentaWebViewModel, alVolver: () -> Unit) {
    var correo by remember { mutableStateOf("") }
    var codigoEnviado by remember { mutableStateOf(false) }
    var codigo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    val ocupado = operacion != OperacionCuenta.NINGUNA
    val pedir = { if (!ocupado) vm.pedirCodigoClave(correo) { codigoEnviado = true } }
    val cambiar = { if (!ocupado) vm.cambiarClave(correo, codigo, clave, confirmacion) }

    Text(
        if (!codigoEnviado) "Escribí el correo de tu cuenta y te mandamos un código para elegir una contraseña nueva."
        else "Escribí el código que te llegó a ${correo.trim()} y tu contraseña nueva.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = correo,
        onValueChange = { correo = it },
        label = { Text("Correo") },
        singleLine = true,
        enabled = !codigoEnviado,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { if (!codigoEnviado) pedir() }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    if (codigoEnviado) {
        CampoCodigo(codigo, { codigo = it }, ultimo = false, alConfirmar = {})
        CampoClave(valor = clave, alCambiar = { clave = it }, etiqueta = "Contraseña nueva", error = null, ultimo = false, alConfirmar = {})
        CampoClave(valor = confirmacion, alCambiar = { confirmacion = it }, etiqueta = "Repetir contraseña", error = null, ultimo = true, alConfirmar = cambiar)
        Text(
            "Mínimo ${ValidacionCuenta.LARGO_MINIMO_CLAVE} caracteres, combinando letras y números.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoCuentaEnLinea(it) }
    Button(
        onClick = if (codigoEnviado) cambiar else pedir,
        enabled = !ocupado && (!codigoEnviado || codigo.length >= 6),
        modifier = Modifier.fillMaxWidth().mano()
    ) {
        if (ocupado) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            when {
                operacion == OperacionCuenta.SINCRONIZANDO -> "Sincronizando lotes…"
                ocupado -> if (codigoEnviado) "Cambiando la contraseña…" else "Enviando código…"
                codigoEnviado -> "Cambiar contraseña e ingresar"
                else -> "Enviarme el código"
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (codigoEnviado) {
            TextButton(onClick = pedir, enabled = !ocupado, modifier = Modifier.mano()) { Text("Reenviar código") }
        }
        TextButton(onClick = alVolver, enabled = !ocupado, modifier = Modifier.mano()) { Text("Volver a ingresar") }
    }
}

@Composable
private fun CampoTexto(valor: String, alCambiar: (String) -> Unit, etiqueta: String, error: String?, teclado: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Provincia de una lista cerrada (la misma de Android): así `ubicacion_principal` queda escrita igual. */
@Composable
private fun SelectorProvincia(valor: String, alElegir: (String) -> Unit, error: String?) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provincia") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        // El campo de solo lectura no recibe el toque: una capa encima abre la lista
        Box(Modifier.matchParentSize().mano().clickable(onClickLabel = "Elegir provincia") { abierto = true })
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            PROVINCIAS_ARGENTINAS.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { alElegir(p); abierto = false }, modifier = Modifier.mano())
            }
        }
    }
}

/** Código de 6 dígitos del correo: solo números, teclado numérico y sin autocorrección. */
@Composable
private fun CampoCodigo(valor: String, alCambiar: (String) -> Unit, ultimo: Boolean, alConfirmar: () -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> alCambiar(nuevo.filter { it.isDigit() }.take(8)) },
        label = { Text("Código del correo") },
        placeholder = { Text("6 dígitos") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { alConfirmar() }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Contraseña oculta por defecto, con el ojo para mostrarla; sin autocorrección ni sugerencias. */
@Composable
private fun CampoClave(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    error: String?,
    ultimo: Boolean,
    alConfirmar: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onDone = { alConfirmar() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.mano()) {
                Icon(
                    if (visible) IconosGranex.OjoTachado else IconosGranex.Ojo,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PanelSesion(
    correo: String?,
    sinConexion: Boolean,
    operacion: OperacionCuenta,
    aviso: AvisoCuenta?,
    lotes: Int,
    pendientes: Int,
    ultima: ResultadoSincronizacion?,
    vm: CuentaWebViewModel
) {
    var confirmarCierre by remember { mutableStateOf(false) }
    var confirmarEliminacion by remember { mutableStateOf(false) }
    val ocupado = operacion != OperacionCuenta.NINGUNA
    val marca = LocalMarca.current

    Panel("Tu cuenta") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = marca.primario, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(correo ?: "Cuenta GRANEX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (sinConexion) "Sesión guardada. Sin conexión para verificarla."
                    else if (lotes == 1) "1 lote en este navegador" else "$lotes lotes en este navegador",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FilaEstado(
            IconosGranex.Subir,
            when {
                pendientes == 0 -> "Todo sincronizado."
                pendientes == 1 -> "1 cambio sin subir: se sube solo cuando haya conexión."
                else -> "$pendientes cambios sin subir: se suben solos cuando haya conexión."
            }
        )
        (ultima as? ResultadoSincronizacion.Lista)?.let {
            Text(
                "Última sincronización: ${ControladorCuenta.describir(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        aviso?.takeIf { it.tipo != TipoAviso.CIERRE_BLOQUEADO }?.let { AvisoCuentaEnLinea(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::sincronizarAhora, enabled = !ocupado, modifier = Modifier.mano()) {
                if (operacion == OperacionCuenta.SINCRONIZANDO) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar")
            }
            TextButton(onClick = { confirmarCierre = true }, enabled = !ocupado, modifier = Modifier.mano()) {
                Text(if (operacion == OperacionCuenta.CERRANDO_SESION) "Cerrando sesión…" else "Cerrar sesión")
            }
        }
        TextButton(
            onClick = { confirmarEliminacion = true },
            enabled = !ocupado && !sinConexion,
            modifier = Modifier.mano()
        ) {
            Text(
                if (operacion == OperacionCuenta.ELIMINANDO) "Eliminando la cuenta…" else "Eliminar cuenta",
                color = if (!ocupado && !sinConexion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmarEliminacion) {
        AlertDialog(
            onDismissRequest = { confirmarEliminacion = false },
            title = { Text("¿Eliminar la cuenta?") },
            text = {
                Text(
                    "Se borran para siempre tu cuenta, tus lotes (en la nube y en este navegador) y los archivos " +
                        "guardados. No se puede deshacer." +
                        if (pendientes > 0) "\n\nTambién se pierden los cambios que todavía no se subieron." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminacion = false
                        vm.eliminarCuenta()
                    },
                    modifier = Modifier.mano()
                ) { Text("Eliminar para siempre", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminacion = false }, modifier = Modifier.mano()) { Text("Cancelar") } }
        )
    }

    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Antes de salir se suben los cambios pendientes. Después se quitan los lotes de este navegador: " +
                        "siguen guardados en tu cuenta y vuelven a aparecer al ingresar." +
                        if (pendientes > 0) "\n\nHay cambios sin subir: hace falta conexión a internet." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarCierre = false
                        vm.cerrarSesion()
                    },
                    modifier = Modifier.mano()
                ) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarCierre = false }, modifier = Modifier.mano()) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FilaEstado(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AvisoCuentaEnLinea(aviso: AvisoCuenta) {
    val marca = LocalMarca.current
    Aviso(
        aviso.mensaje,
        color = when (aviso.tipo) {
            TipoAviso.EXITO -> marca.positivo
            TipoAviso.ERROR, TipoAviso.CIERRE_BLOQUEADO -> marca.negativo
            TipoAviso.INFO -> marca.info
        }
    )
}
