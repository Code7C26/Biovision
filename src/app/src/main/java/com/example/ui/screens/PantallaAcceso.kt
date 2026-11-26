package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.PROVINCIAS_ARGENTINAS
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.ValidacionCuenta
import com.example.data.remote.supabase.ValidacionPerfil
import com.example.data.repository.AccesoGoogle
import com.example.data.repository.ResultadoGoogle
import com.example.data.sync.AvisoCuenta
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.TipoAviso
import com.example.ui.components.CampoCorreo
import com.example.ui.components.CampoSecreto
import com.example.ui.components.EnlaceSoporte
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.CuentaViewModel
import com.example.ui.viewmodel.EstadoPerfil
import com.example.ui.viewmodel.PerfilViewModel
import com.example.ui.viewmodel.cuentaViewModelDeLaApp
import com.example.ui.viewmodel.perfilViewModelDeLaApp
import kotlinx.coroutines.launch

/**
 * Control de acceso: con backend hace falta una cuenta. Sin sesión se muestra [PantallaAcceso];
 * con sesión pero sin perfil (entró con Google o es una cuenta anterior), [PantallaCompletarPerfil].
 * Sin backend configurado la app se usa sin cuenta, como antes. Sin red, con una sesión guardada, se
 * entra igual (los datos del teléfono siguen disponibles).
 */
@Composable
fun AccesoGranex(contenido: @Composable () -> Unit) {
    val cuenta = cuentaViewModelDeLaApp()
    val perfilVm = perfilViewModelDeLaApp()
    val estado by cuenta.estado.collectAsStateWithLifecycle()
    val perfil by perfilVm.estado.collectAsStateWithLifecycle()
    // Al cerrar sesión (o eliminar la cuenta) la Activity se reinicia: los ViewModels de las pantallas
    // (lote elegido, escaneo, prescripción) no conservan nada de la cuenta anterior en memoria
    val contexto = LocalContext.current
    var habiaSesion by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(estado) {
        val conSesion = estado is EstadoCuenta.Conectado || estado is EstadoCuenta.SinConexion
        if (habiaSesion && estado is EstadoCuenta.SinSesion) {
            val actividad = generateSequence(contexto) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>().firstOrNull()
            actividad?.let {
                it.startActivity(
                    android.content.Intent(it, it::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                it.finish()
            }
        }
        habiaSesion = conSesion
    }
    when {
        estado is EstadoCuenta.SinBackend -> contenido()
        estado is EstadoCuenta.Verificando -> PantallaCarga()
        estado is EstadoCuenta.SinSesion -> PantallaAcceso(cuenta)
        perfil is EstadoPerfil.Cargando || perfil is EstadoPerfil.SinSesion -> PantallaCarga()
        perfil is EstadoPerfil.Incompleto -> PantallaCompletarPerfil(perfilVm, perfil as EstadoPerfil.Incompleto, cuenta)
        else -> contenido()
    }
}

@Composable
private fun FondoAcceso(contenido: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to AgroGreenPrimary,
                    0.38f to AgroGreenPrimary.copy(alpha = 0.55f),
                    0.62f to MaterialTheme.colorScheme.background
                )
            )
    ) { contenido() }
}

@Composable
private fun Marca(subtitulo: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.logo_granex),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(12.dp))
        Text("GRANEX", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 3.sp)
        Text(
            subtitulo,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PantallaCarga() {
    FondoAcceso {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Marca("Preparando tu campo…")
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private enum class ModoAcceso { INGRESAR, CREAR, RECUPERAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAcceso(cuenta: CuentaViewModel) {
    val operacion by cuenta.operacion.collectAsStateWithLifecycle()
    val aviso by cuenta.aviso.collectAsStateWithLifecycle()
    val porConfirmar by cuenta.correoPorConfirmar.collectAsStateWithLifecycle()
    var modo by rememberSaveable { mutableStateOf(ModoAcceso.INGRESAR) }
    val ocupado = operacion != OperacionCuenta.NINGUNA
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    val conGoogle: () -> Unit = {
        alcance.launch {
            when (val r = AccesoGoogle.pedirToken(contexto)) {
                is ResultadoGoogle.Token -> cuenta.ingresarConGoogle(r.idToken, r.nonce)
                is ResultadoGoogle.Error -> cuenta.avisarError(r.mensaje)
                ResultadoGoogle.Cancelado -> Unit
            }
        }
    }

    FondoAcceso {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Marca("Mercado, clima y monitoreo satelital de tus lotes")
            Spacer(Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val correoConfirmar = porConfirmar
                    if (correoConfirmar != null) {
                        FormularioConfirmar(cuenta, correoConfirmar, ocupado)
                        aviso?.let { AvisoAcceso(it) }
                    } else {
                    if (modo != ModoAcceso.RECUPERAR) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(ModoAcceso.INGRESAR to "Ingresar", ModoAcceso.CREAR to "Crear cuenta").forEachIndexed { i, (m, texto) ->
                                SegmentedButton(
                                    selected = modo == m,
                                    onClick = {
                                        modo = m
                                        cuenta.descartarAviso()
                                    },
                                    enabled = !ocupado,
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    modifier = Modifier.testTag(if (m == ModoAcceso.INGRESAR) "tab_ingresar" else "tab_crear")
                                ) { Text(texto) }
                            }
                        }
                    }
                    AnimatedContent(targetState = modo, label = "modo") { m ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (m) {
                                ModoAcceso.INGRESAR -> FormularioIngreso(cuenta, ocupado, operacion) { modo = ModoAcceso.RECUPERAR }
                                ModoAcceso.CREAR -> FormularioRegistro(cuenta, ocupado, operacion)
                                ModoAcceso.RECUPERAR -> FormularioRecuperar(cuenta, ocupado) { modo = ModoAcceso.INGRESAR }
                            }
                        }
                    }
                    aviso?.let { AvisoAcceso(it) }
                    if (modo != ModoAcceso.RECUPERAR) {
                        Separador()
                        BotonGoogle(enabled = !ocupado, onClick = conGoogle)
                    }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "¿Problemas para ingresar? Escribinos:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EnlaceSoporte(asunto = "GRANEX: problema para ingresar")
        }
    }
}

@Composable
private fun FormularioIngreso(cuenta: CuentaViewModel, ocupado: Boolean, operacion: OperacionCuenta, alOlvidar: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    // Las contraseñas nunca van en rememberSaveable (quedarían en el estado guardado de la Activity)
    var clave by remember { mutableStateOf("") }
    var intento by remember { mutableStateOf(false) }
    val errores = if (intento) ValidacionCuenta.validar(correo, clave, null, creandoCuenta = false) else ValidacionCuenta.Errores()
    val enviar = {
        intento = true
        cuenta.ingresar(correo, clave)
    }
    Text("Ingresá a tu cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    CampoCorreo(correo, { correo = it }, Modifier.fillMaxWidth(), error = errores.correo, testTag = "acceso_correo")
    CampoSecreto(
        valor = clave,
        onValorChange = { clave = it },
        etiqueta = "Contraseña",
        error = errores.clave,
        alConfirmar = { enviar() },
        modifier = Modifier.fillMaxWidth(),
        testTag = "acceso_clave"
    )
    TextButton(onClick = alOlvidar, enabled = !ocupado, modifier = Modifier.testTag("btn_olvide_clave")) {
        Text("¿Olvidaste tu contraseña?")
    }
    BotonPrincipal(
        texto = if (operacion == OperacionCuenta.INGRESANDO || operacion == OperacionCuenta.SINCRONIZANDO) "Ingresando…" else "Ingresar",
        cargando = operacion == OperacionCuenta.INGRESANDO || operacion == OperacionCuenta.SINCRONIZANDO,
        enabled = !ocupado,
        testTag = "btn_ingresar",
        onClick = { enviar() }
    )
}

@Composable
private fun FormularioRegistro(cuenta: CuentaViewModel, ocupado: Boolean, operacion: OperacionCuenta) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var establecimiento by rememberSaveable { mutableStateOf("") }
    var provincia by rememberSaveable { mutableStateOf("") }
    var localidad by rememberSaveable { mutableStateOf("") }
    var hectareas by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var acepta by rememberSaveable { mutableStateOf(false) }
    var intento by remember { mutableStateOf(false) }

    val erroresPerfil = if (intento) ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas) else ValidacionPerfil.Errores()
    val erroresCuenta = if (intento) ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta = true) else ValidacionCuenta.Errores()
    val enviar = {
        intento = true
        val validos = !ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas).hayErrores &&
            !ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta = true).hayErrores
        if (validos && acepta) {
            cuenta.registrarse(
                correo, clave, confirmacion,
                PerfilNuevo(nombre.trim(), establecimiento.trim(), provincia, localidad.trim(), ValidacionPerfil.hectareas(hectareas))
            )
        }
    }

    Text("Tus datos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    CamposPerfil(
        nombre = nombre, onNombre = { nombre = it },
        establecimiento = establecimiento, onEstablecimiento = { establecimiento = it },
        provincia = provincia, onProvincia = { provincia = it },
        localidad = localidad, onLocalidad = { localidad = it },
        hectareas = hectareas, onHectareas = { hectareas = it },
        errores = erroresPerfil
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Tu acceso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    CampoCorreo(correo, { correo = it }, Modifier.fillMaxWidth(), error = erroresCuenta.correo, testTag = "registro_correo")
    CampoSecreto(
        valor = clave, onValorChange = { clave = it }, etiqueta = "Contraseña", error = erroresCuenta.clave,
        imeAction = ImeAction.Next, modifier = Modifier.fillMaxWidth(), testTag = "registro_clave"
    )
    CampoSecreto(
        valor = confirmacion, onValorChange = { confirmacion = it }, etiqueta = "Repetir contraseña",
        error = erroresCuenta.confirmacion, alConfirmar = { enviar() }, modifier = Modifier.fillMaxWidth(),
        testTag = "registro_confirmacion"
    )
    Text(
        "Mínimo ${ValidacionCuenta.LARGO_MINIMO_CLAVE} caracteres, combinando letras y números.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = acepta, onCheckedChange = { acepta = it }, modifier = Modifier.testTag("registro_acepta"))
        Text(
            "Acepto los Términos de Servicio y la Política de Privacidad de GRANEX.",
            style = MaterialTheme.typography.bodySmall,
            color = if (intento && !acepta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
    BotonPrincipal(
        texto = if (operacion == OperacionCuenta.REGISTRANDO) "Creando cuenta…" else "Crear cuenta",
        cargando = operacion == OperacionCuenta.REGISTRANDO || operacion == OperacionCuenta.SINCRONIZANDO,
        enabled = !ocupado,
        testTag = "btn_crear_cuenta",
        onClick = { enviar() }
    )
}

@Composable
private fun FormularioRecuperar(cuenta: CuentaViewModel, ocupado: Boolean, alVolver: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var codigoEnviado by rememberSaveable { mutableStateOf(false) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }

    Text("Recuperar la contraseña", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        if (!codigoEnviado) "Te mandamos un código de 6 dígitos al correo de tu cuenta."
        else "Ingresá el código que te llegó y elegí una contraseña nueva.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CampoCorreo(correo, { correo = it }, Modifier.fillMaxWidth(), testTag = "recuperar_correo")
    if (codigoEnviado) {
        OutlinedTextField(
            value = codigo,
            onValueChange = { v -> codigo = v.filter { it.isDigit() }.take(10) },
            label = { Text("Código del correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("recuperar_codigo")
        )
        CampoSecreto(clave, { clave = it }, "Contraseña nueva", imeAction = ImeAction.Next, modifier = Modifier.fillMaxWidth())
        CampoSecreto(confirmacion, { confirmacion = it }, "Repetir contraseña nueva", modifier = Modifier.fillMaxWidth())
        BotonPrincipal("Cambiar contraseña e ingresar", cargando = ocupado, enabled = !ocupado, testTag = "btn_cambiar_clave") {
            cuenta.cambiarClave(correo, codigo, clave, confirmacion)
        }
        TextButton(onClick = { cuenta.pedirCodigoClave(correo) {} }, enabled = !ocupado) { Text("Reenviar el código") }
    } else {
        BotonPrincipal("Enviar código", cargando = ocupado, enabled = !ocupado, testTag = "btn_enviar_codigo") {
            cuenta.pedirCodigoClave(correo) { codigoEnviado = true }
        }
    }
    TextButton(onClick = {
        cuenta.descartarAviso()
        alVolver()
    }, enabled = !ocupado) { Text("Volver a ingresar") }
}

/** Cuenta recién creada: se activa con el código de 6 dígitos del correo de GRANEX. */
@Composable
private fun FormularioConfirmar(cuenta: CuentaViewModel, correo: String, ocupado: Boolean) {
    var codigo by rememberSaveable { mutableStateOf("") }
    Text("Activá tu cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Ingresá el código de 6 dígitos que te mandamos a $correo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = codigo,
        onValueChange = { v -> codigo = v.filter { it.isDigit() }.take(10) },
        label = { Text("Código del correo") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("confirmar_codigo")
    )
    BotonPrincipal("Activar cuenta", cargando = ocupado, enabled = !ocupado, testTag = "btn_confirmar_correo") {
        cuenta.confirmarCorreo(codigo)
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = cuenta::cancelarConfirmacion, enabled = !ocupado) { Text("Volver") }
        TextButton(onClick = cuenta::reenviarConfirmacion, enabled = !ocupado) { Text("Reenviar el código") }
    }
}

/** Datos del productor: los mismos en el registro, al completar el perfil y en "Ajustes de Perfil". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamposPerfil(
    nombre: String, onNombre: (String) -> Unit,
    establecimiento: String, onEstablecimiento: (String) -> Unit,
    provincia: String, onProvincia: (String) -> Unit,
    localidad: String, onLocalidad: (String) -> Unit,
    hectareas: String, onHectareas: (String) -> Unit,
    errores: ValidacionPerfil.Errores
) {
    CampoTexto(nombre, onNombre, "Nombre y apellido", errores.nombre, "perfil_nombre", KeyboardCapitalization.Words)
    CampoTexto(establecimiento, onEstablecimiento, "Establecimiento o empresa", errores.establecimiento, "perfil_establecimiento", KeyboardCapitalization.Words)
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = provincia,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provincia") },
            isError = errores.provincia != null,
            supportingText = errores.provincia?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("perfil_provincia")
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            PROVINCIAS_ARGENTINAS.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = {
                    onProvincia(p)
                    abierto = false
                })
            }
        }
    }
    CampoTexto(localidad, onLocalidad, "Localidad (opcional)", null, "perfil_localidad", KeyboardCapitalization.Words)
    CampoTexto(
        hectareas, { v -> onHectareas(v.filter { it.isDigit() || it == ',' || it == '.' }.take(12)) },
        "Hectáreas que trabajás (opcional)", errores.hectareas, "perfil_hectareas", KeyboardCapitalization.None,
        KeyboardType.Decimal
    )
}

@Composable
private fun CampoTexto(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    error: String?,
    testTag: String,
    mayusculas: KeyboardCapitalization,
    teclado: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onValor(it.take(120)) },
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = mayusculas, keyboardType = teclado, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    )
}

@Composable
private fun BotonPrincipal(texto: String, cargando: Boolean, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(testTag)
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Separador() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text("  o  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BotonGoogle(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_google")
    ) {
        // "G" con los colores de Google, sin imagen de marca empaquetada
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White)
        ) {
            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text("Continuar con Google", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvisoAcceso(aviso: AvisoCuenta) {
    val esquema = MaterialTheme.colorScheme
    val (fondo, texto, icono) = when (aviso.tipo) {
        TipoAviso.EXITO -> Triple(AgroGreenPrimary.copy(alpha = 0.12f), esquema.onSurface, Icons.Default.CheckCircle)
        TipoAviso.INFO -> Triple(esquema.secondaryContainer, esquema.onSecondaryContainer, Icons.Default.Info)
        TipoAviso.ERROR, TipoAviso.CIERRE_BLOQUEADO -> Triple(esquema.errorContainer, esquema.onErrorContainer, Icons.Default.ErrorOutline)
    }
    Surface(color = fondo, contentColor = texto, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("aviso_acceso")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(aviso.mensaje, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Después de entrar con Google (o con una cuenta sin perfil): los datos del productor, una sola vez. */
@Composable
fun PantallaCompletarPerfil(perfilVm: PerfilViewModel, estado: EstadoPerfil.Incompleto, cuenta: CuentaViewModel) {
    val guardando by perfilVm.guardando.collectAsStateWithLifecycle()
    val error by perfilVm.error.collectAsStateWithLifecycle()
    val s = estado.sugerido
    var nombre by rememberSaveable { mutableStateOf(s.nombre) }
    var establecimiento by rememberSaveable { mutableStateOf(s.establecimiento) }
    var provincia by rememberSaveable { mutableStateOf(s.provincia) }
    var localidad by rememberSaveable { mutableStateOf(s.localidad) }
    var hectareas by rememberSaveable { mutableStateOf(s.hectareas?.let { formatoHectareas(it) }.orEmpty()) }
    var intento by remember { mutableStateOf(false) }
    val errores = if (intento) ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas) else ValidacionPerfil.Errores()

    FondoAcceso {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Marca("Un último paso: contanos de tu campo")
            Spacer(Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Completá tu perfil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    estado.correo?.let {
                        Text("Cuenta: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CamposPerfil(
                        nombre, { nombre = it }, establecimiento, { establecimiento = it }, provincia, { provincia = it },
                        localidad, { localidad = it }, hectareas, { hectareas = it }, errores
                    )
                    error?.let { AvisoAcceso(AvisoCuenta(it, TipoAviso.ERROR)) }
                    BotonPrincipal("Guardar y continuar", cargando = guardando, enabled = !guardando, testTag = "btn_guardar_perfil") {
                        intento = true
                        if (!ValidacionPerfil.validar(nombre, establecimiento, provincia, hectareas).hayErrores) {
                            perfilVm.guardar(PerfilNuevo(nombre.trim(), establecimiento.trim(), provincia, localidad.trim(), ValidacionPerfil.hectareas(hectareas)))
                        }
                    }
                    TextButton(onClick = cuenta::cerrarSesion, enabled = !guardando, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Usar otra cuenta")
                    }
                }
            }
        }
    }
}

/** 1234.5 → "1234,5" (sin separador de miles: el campo los quitaría al validar). */
fun formatoHectareas(h: Double): String =
    if (h % 1.0 == 0.0) h.toLong().toString() else h.toString().replace('.', ',')
