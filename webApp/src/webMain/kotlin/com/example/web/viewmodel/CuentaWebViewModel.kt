package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.ServicioCuenta
import com.example.data.sync.ControladorCuenta
import com.example.data.sync.SincronizadorLotes

/**
 * Cuenta GRANEX en la web, como CuentaViewModel en Android: toda la lógica (validación, una operación
 * a la vez, sincronizar al ingresar, cierre de sesión que no pierde cambios) está en el
 * [ControladorCuenta] compartido. Sin red no hay reintento programado (no hay WorkManager): la web
 * sincroniza sola al volver la conexión (GranexWeb).
 */
class CuentaWebViewModel(
    cuenta: ServicioCuenta,
    sincronizador: SincronizadorLotes?,
    /** Al cerrar sesión o eliminar la cuenta: lo que el navegador guarda de la cuenta (la Agenda). */
    borrarDatosLocales: suspend () -> Unit = {},
    /** null si se puede cerrar sesión; si no, por qué (gastos de la Agenda sin subir). */
    antesDeCerrarSesion: suspend () -> String? = { null }
) : ViewModel() {
    private val controlador = ControladorCuenta(
        cuenta, sincronizador, viewModelScope,
        borrarDatosLocales = borrarDatosLocales,
        antesDeCerrarSesion = antesDeCerrarSesion
    )

    val estado = controlador.estado
    val operacion = controlador.operacion
    val aviso = controlador.aviso
    val ultimaSincronizacion = controlador.ultimaSincronizacion

    /** Correo de una cuenta creada que falta activar con el código que llegó por correo; null si no hay. */
    val correoPorConfirmar = controlador.correoPorConfirmar

    fun ingresar(correo: String, clave: String) = controlador.ingresar(correo, clave)
    fun registrarse(correo: String, clave: String, confirmacion: String) = controlador.registrarse(correo, clave, confirmacion)
    fun registrarse(correo: String, clave: String, confirmacion: String, perfil: PerfilNuevo) =
        controlador.registrarse(correo, clave, confirmacion, perfil)
    fun confirmarCorreo(codigo: String) = controlador.confirmarCorreo(codigo)
    fun reenviarConfirmacion() = controlador.reenviarConfirmacion()
    fun cancelarConfirmacion() = controlador.cancelarConfirmacion()
    fun pedirCodigoClave(correo: String, alEnviar: () -> Unit) = controlador.pedirCodigoClave(correo, alEnviar)
    fun cambiarClave(correo: String, codigo: String, clave: String, confirmacion: String) =
        controlador.cambiarClave(correo, codigo, clave, confirmacion)
    fun sincronizarAhora() = controlador.sincronizarAhora()
    fun cerrarSesion() = controlador.cerrarSesion()
    fun eliminarCuenta() = controlador.eliminarCuenta()
    fun descartarAviso() = controlador.descartarAviso()
}
