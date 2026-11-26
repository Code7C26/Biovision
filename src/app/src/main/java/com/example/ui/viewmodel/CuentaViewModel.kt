package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.sync.AvisoCuenta
import com.example.data.sync.ControladorCuenta
import com.example.data.sync.OperacionCuenta
import com.example.data.sync.ResultadoSincronizacion
import com.example.data.sync.SincronizadorLotes
import com.example.data.sync.TipoAviso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cuenta GRANEX en Ajustes. La lógica (ingresar → primera sincronización, cierre de sesión que no
 * pierde cambios) está en [ControladorCuenta], en :shared, para que la web use la misma.
 *
 * @param pendientes cantidad de cambios de lotes sin subir (Room).
 * @param programarReintento WorkManager: reintenta la sincronización cuando vuelva la red.
 * @param borrarDatosLocales al eliminar la cuenta: análisis de suelo, rindes y dosis guardados en el teléfono.
 */
class CuentaViewModel(
    cuenta: Autenticacion,
    sincronizador: SincronizadorLotes?,
    pendientes: Flow<Int>,
    programarReintento: () -> Unit,
    borrarDatosLocales: suspend () -> Unit = {},
    antesDeCerrarSesion: suspend () -> String? = { null }
) : ViewModel() {
    private val controlador =
        ControladorCuenta(cuenta, sincronizador, viewModelScope, programarReintento, borrarDatosLocales, antesDeCerrarSesion)

    val estado: StateFlow<EstadoCuenta> = controlador.estado
    val operacion: StateFlow<OperacionCuenta> = controlador.operacion
    val aviso: StateFlow<AvisoCuenta?> = controlador.aviso
    val ultimaSincronizacion: StateFlow<ResultadoSincronizacion?> = controlador.ultimaSincronizacion
    val pendientes: StateFlow<Int> = pendientes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Al ingresar, el worker baja también el suelo, los rindes y las dosis guardados en la cuenta
        viewModelScope.launch {
            var antes: EstadoCuenta? = null
            estado.collect { actual ->
                if (actual is EstadoCuenta.Conectado && antes != null && antes !is EstadoCuenta.Conectado) programarReintento()
                antes = actual
            }
        }
        // Un cambio nuevo sin subir deja viejo el último "Tus lotes están al día" o "Sesión cerrada…"
        viewModelScope.launch {
            this@CuentaViewModel.pendientes.collect { cantidad ->
                if (cantidad > 0 && aviso.value?.tipo == TipoAviso.EXITO) controlador.descartarAviso()
            }
        }
    }

    fun ingresar(correo: String, clave: String) = controlador.ingresar(correo, clave)
    fun registrarse(correo: String, clave: String, confirmacion: String) = controlador.registrarse(correo, clave, confirmacion)
    fun sincronizarAhora() = controlador.sincronizarAhora()
    fun cerrarSesion() = controlador.cerrarSesion()
    fun eliminarCuenta() = controlador.eliminarCuenta()
    fun registrarse(correo: String, clave: String, confirmacion: String, perfil: com.example.data.remote.supabase.PerfilNuevo) =
        controlador.registrarse(correo, clave, confirmacion, perfil)
    fun ingresarConGoogle(idToken: String, nonce: String?) = controlador.ingresarConGoogle(idToken, nonce)
    fun pedirCodigoClave(correo: String, alEnviar: () -> Unit) = controlador.pedirCodigoClave(correo, alEnviar)
    fun cambiarClave(correo: String, codigo: String, clave: String, confirmacion: String) =
        controlador.cambiarClave(correo, codigo, clave, confirmacion)
    fun avisarError(mensaje: String) = controlador.avisarError(mensaje)
    val correoPorConfirmar: StateFlow<String?> = controlador.correoPorConfirmar
    fun confirmarCorreo(codigo: String) = controlador.confirmarCorreo(codigo)
    fun reenviarConfirmacion() = controlador.reenviarConfirmacion()
    fun cancelarConfirmacion() = controlador.cancelarConfirmacion()
    fun descartarAviso() = controlador.descartarAviso()
}
