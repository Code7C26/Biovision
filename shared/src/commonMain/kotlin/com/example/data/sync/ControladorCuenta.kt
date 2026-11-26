package com.example.data.sync

import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.ValidacionPerfil
import com.example.data.remote.supabase.ResultadoRegistro
import com.example.data.remote.supabase.ServicioCuenta
import com.example.data.remote.supabase.ValidacionCuenta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que la cuenta está haciendo ahora (la UI deshabilita los botones mientras no sea NINGUNA). */
enum class OperacionCuenta { NINGUNA, INGRESANDO, REGISTRANDO, SINCRONIZANDO, CERRANDO_SESION, ELIMINANDO, CAMBIANDO_CLAVE }

enum class TipoAviso { EXITO, INFO, ERROR, CIERRE_BLOQUEADO }

/** Mensaje para el usuario sobre la última operación. [TipoAviso.CIERRE_BLOQUEADO] se muestra como alerta. */
data class AvisoCuenta(val mensaje: String, val tipo: TipoAviso)

/**
 * Lógica de la pantalla de cuenta, compartida por Android y la web (cada ViewModel le pasa su scope):
 * - al ingresar (o crear la cuenta con sesión inmediata) sincroniza enseguida, así los lotes de la nube
 *   aparecen en el dispositivo y los creados sin cuenta se suben;
 * - al cerrar sesión sube primero lo pendiente y, si algo no se pudo subir, no cierra (ver
 *   [SincronizadorLotes.cerrarSesion]).
 *
 * @param sincronizador null sin backend.
 * @param programarReintento se llama si una sincronización falla (Android: WorkManager espera la red).
 * @param borrarDatosLocales al cerrar sesión o eliminar la cuenta, borra lo que el dispositivo guarda
 *   aparte de los lotes (Android: suelo, rinde, dosis y credenciales de servicios de terceros).
 */
class ControladorCuenta(
    private val cuenta: Autenticacion,
    private val sincronizador: SincronizadorLotes?,
    private val alcance: CoroutineScope,
    private val programarReintento: () -> Unit = {},
    private val borrarDatosLocales: suspend () -> Unit = {},
    /** Antes de cerrar sesión: null si se puede salir; si no, el motivo (p. ej. datos sin subir). */
    private val antesDeCerrarSesion: suspend () -> String? = { null }
) {
    val estado: StateFlow<EstadoCuenta> = cuenta.estado.stateIn(
        alcance,
        SharingStarted.Eagerly,
        if (cuenta.configurada) EstadoCuenta.Verificando else EstadoCuenta.SinBackend
    )

    private val _operacion = MutableStateFlow(OperacionCuenta.NINGUNA)
    val operacion: StateFlow<OperacionCuenta> = _operacion.asStateFlow()

    private val _aviso = MutableStateFlow<AvisoCuenta?>(null)
    val aviso: StateFlow<AvisoCuenta?> = _aviso.asStateFlow()

    /** Correo de una cuenta creada que espera el código de confirmación (la pantalla lo pide). */
    private val _correoPorConfirmar = MutableStateFlow<String?>(null)
    val correoPorConfirmar: StateFlow<String?> = _correoPorConfirmar.asStateFlow()

    private val _ultimaSincronizacion = MutableStateFlow<ResultadoSincronizacion?>(null)
    val ultimaSincronizacion: StateFlow<ResultadoSincronizacion?> = _ultimaSincronizacion.asStateFlow()

    fun ingresar(correo: String, clave: String) {
        if (ValidacionCuenta.validar(correo, clave, null, creandoCuenta = false).hayErrores) return
        operar(OperacionCuenta.INGRESANDO) {
            cuenta.ingresar(correo, clave).fold(
                onSuccess = { sincronizarTrasIngresar() },
                onFailure = {
                    // Cuenta creada pero sin activar: se pide el código en lugar de mostrar solo el error
                    if (it.message == ServicioCuenta.MENSAJE_SIN_CONFIRMAR) _correoPorConfirmar.value = correo.trim()
                    _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR)
                }
            )
        }
    }

    private fun pedirCodigoConfirmacion(correo: String) {
        _correoPorConfirmar.value = correo.trim()
        _aviso.value = AvisoCuenta(
            "Te enviamos un código de 6 dígitos a ${correo.trim()}. Ingresalo para activar tu cuenta " +
                "(revisá también el correo no deseado).",
            TipoAviso.INFO
        )
    }

    /** Activa la cuenta con el código del correo; la sesión empieza enseguida. */
    fun confirmarCorreo(codigo: String) {
        val correo = _correoPorConfirmar.value ?: return
        if (codigo.trim().length < 6) {
            _aviso.value = AvisoCuenta("Ingresá el código que te llegó por correo.", TipoAviso.ERROR)
            return
        }
        operar(OperacionCuenta.INGRESANDO) {
            cuenta.confirmarCorreo(correo, codigo).fold(
                onSuccess = {
                    _correoPorConfirmar.value = null
                    sincronizarTrasIngresar()
                },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    fun reenviarConfirmacion() {
        val correo = _correoPorConfirmar.value ?: return
        operar(OperacionCuenta.REGISTRANDO) {
            cuenta.reenviarConfirmacion(correo).fold(
                onSuccess = { _aviso.value = AvisoCuenta("Te mandamos un código nuevo a $correo.", TipoAviso.INFO) },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    /** Volver al ingreso sin confirmar (otro correo). */
    fun cancelarConfirmacion() {
        _correoPorConfirmar.value = null
        _aviso.value = null
    }

    fun registrarse(correo: String, clave: String, confirmacion: String) {
        if (ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta = true).hayErrores) return
        operar(OperacionCuenta.REGISTRANDO) {
            cuenta.registrarse(correo, clave).fold(
                onSuccess = { resultado ->
                    when (resultado) {
                        ResultadoRegistro.SESION_INICIADA -> sincronizarTrasIngresar()
                        ResultadoRegistro.CONFIRMAR_CORREO -> pedirCodigoConfirmacion(correo)
                    }
                },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    /** Crear la cuenta con los datos del productor (pantalla de acceso). */
    fun registrarse(correo: String, clave: String, confirmacion: String, perfil: PerfilNuevo) {
        if (ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta = true).hayErrores) return
        val h = perfil.hectareas?.toString().orEmpty()
        if (ValidacionPerfil.validar(perfil.nombre, perfil.establecimiento, perfil.provincia, h).hayErrores) return
        operar(OperacionCuenta.REGISTRANDO) {
            cuenta.registrarse(correo, clave, perfil).fold(
                onSuccess = { resultado ->
                    when (resultado) {
                        ResultadoRegistro.SESION_INICIADA -> sincronizarTrasIngresar()
                        ResultadoRegistro.CONFIRMAR_CORREO -> pedirCodigoConfirmacion(correo)
                    }
                },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    /** [idToken] lo da el selector de cuentas de Google del teléfono. */
    fun ingresarConGoogle(idToken: String, nonce: String?) {
        operar(OperacionCuenta.INGRESANDO) {
            cuenta.ingresarConGoogle(idToken, nonce).fold(
                onSuccess = { sincronizarTrasIngresar() },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    /** Muestra un error que ocurrió fuera del controlador (p. ej. el selector de Google). */
    fun avisarError(mensaje: String) {
        _aviso.value = AvisoCuenta(mensaje, TipoAviso.ERROR)
    }

    /** Primer paso de "Olvidé mi contraseña": el correo trae un código de 6 dígitos. */
    fun pedirCodigoClave(correo: String, alEnviar: () -> Unit = {}) {
        if (ValidacionCuenta.validar(correo, "x", null, creandoCuenta = false).correo != null) {
            _aviso.value = AvisoCuenta("Ingresá el correo de tu cuenta.", TipoAviso.ERROR)
            return
        }
        operar(OperacionCuenta.CAMBIANDO_CLAVE) {
            cuenta.pedirCodigoClave(correo).fold(
                onSuccess = {
                    _aviso.value = AvisoCuenta("Te enviamos un código a ${correo.trim()}. Revisá también el correo no deseado.", TipoAviso.INFO)
                    alEnviar()
                },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    /** Segundo paso: con el código, la sesión empieza y la contraseña cambia. */
    fun cambiarClave(correo: String, codigo: String, clave: String, confirmacion: String) {
        val errores = ValidacionCuenta.validar(correo, clave, confirmacion, creandoCuenta = true)
        if (errores.hayErrores) {
            _aviso.value = AvisoCuenta(errores.clave ?: errores.confirmacion ?: errores.correo ?: ERROR_GENERICO, TipoAviso.ERROR)
            return
        }
        if (codigo.trim().length < 6) {
            _aviso.value = AvisoCuenta("Ingresá el código que te llegó por correo.", TipoAviso.ERROR)
            return
        }
        operar(OperacionCuenta.CAMBIANDO_CLAVE) {
            cuenta.cambiarClaveConCodigo(correo, codigo, clave).fold(
                onSuccess = { sincronizarTrasIngresar() },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    fun sincronizarAhora() {
        val sincronizador = sincronizador ?: return
        operar(OperacionCuenta.SINCRONIZANDO) {
            val resultado = sincronizador.sincronizar()
            registrar(resultado)
            _aviso.value = when (resultado) {
                is ResultadoSincronizacion.Lista -> AvisoCuenta(describir(resultado), TipoAviso.EXITO)
                else -> avisoDeFallo(resultado)
            }
        }
    }

    fun cerrarSesion() {
        operar(OperacionCuenta.CERRANDO_SESION) {
            // Lo que el dispositivo guarda aparte de los lotes (suelo, rinde) se sube antes de salir
            antesDeCerrarSesion()?.let { motivo ->
                programarReintento()
                _aviso.value = AvisoCuenta("No se puede cerrar sesión: $motivo", TipoAviso.CIERRE_BLOQUEADO)
                return@operar
            }
            // El próximo que use el dispositivo no ve nada de esta cuenta. Se borra antes de salir: al
            // quedar sin sesión, Android reinicia la pantalla y cancela lo que siga corriendo
            val salir: suspend () -> Unit = {
                borrarDatosLocales()
                cuenta.salir()
            }
            val resultado = sincronizador?.cerrarSesion(salir) ?: ResultadoCierreSesion.Cerrada.also { salir() }
            _aviso.value = when (resultado) {
                ResultadoCierreSesion.Cerrada -> {
                    _ultimaSincronizacion.value = null
                    AvisoCuenta(
                        "Sesión cerrada. Tus lotes siguen guardados en la nube y vuelven a aparecer al ingresar.",
                        TipoAviso.EXITO
                    )
                }
                is ResultadoCierreSesion.Bloqueada -> {
                    programarReintento()
                    AvisoCuenta(mensajeCierreBloqueado(resultado), TipoAviso.CIERRE_BLOQUEADO)
                }
            }
        }
    }

    /**
     * Borra la cuenta y todos sus datos (nube y dispositivo). No se puede deshacer: la pantalla pide
     * confirmarlo antes. Si el servidor falla, no se borra nada local y la sesión sigue.
     */
    fun eliminarCuenta() {
        operar(OperacionCuenta.ELIMINANDO) {
            cuenta.eliminarCuenta().fold(
                onSuccess = {
                    sincronizador?.olvidarCuenta()
                    borrarDatosLocales()
                    // Recién ahora se descarta la sesión (el usuario ya no existe en el servidor)
                    cuenta.salir()
                    _ultimaSincronizacion.value = null
                    _aviso.value = AvisoCuenta("Tu cuenta y sus datos fueron eliminados.", TipoAviso.EXITO)
                },
                onFailure = { _aviso.value = AvisoCuenta(it.message ?: ERROR_GENERICO, TipoAviso.ERROR) }
            )
        }
    }

    fun descartarAviso() {
        _aviso.value = null
    }

    private suspend fun sincronizarTrasIngresar() {
        val sincronizador = sincronizador ?: return
        _operacion.value = OperacionCuenta.SINCRONIZANDO
        val resultado = sincronizador.sincronizar()
        registrar(resultado)
        _aviso.value = when (resultado) {
            is ResultadoSincronizacion.Lista -> AvisoCuenta("Sesión iniciada. ${describir(resultado)}", TipoAviso.EXITO)
            is ResultadoSincronizacion.Error -> AvisoCuenta(
                "Sesión iniciada, pero los lotes no se pudieron sincronizar: ${resultado.mensaje} " +
                    "Se reintenta solo cuando vuelva la conexión.",
                TipoAviso.ERROR
            )
            else -> avisoDeFallo(resultado)
        }
    }

    private fun registrar(resultado: ResultadoSincronizacion) {
        _ultimaSincronizacion.value = resultado
        if (resultado is ResultadoSincronizacion.Error) programarReintento()
    }

    /** Una operación a la vez: un segundo toque mientras se ingresa o se cierra sesión no hace nada. */
    private fun operar(operacion: OperacionCuenta, bloque: suspend () -> Unit) {
        if (_operacion.value != OperacionCuenta.NINGUNA) return
        _operacion.value = operacion
        _aviso.value = null
        alcance.launch {
            try {
                bloque()
            } finally {
                _operacion.value = OperacionCuenta.NINGUNA
            }
        }
    }

    companion object {
        private const val ERROR_GENERICO = "No se pudo completar la operación."

        fun describir(r: ResultadoSincronizacion.Lista): String {
            val partes = buildList {
                if (r.recibidos > 0) add(plural(r.recibidos, "lote recibido", "lotes recibidos"))
                if (r.subidos > 0) add(plural(r.subidos, "cambio subido", "cambios subidos"))
                if (r.borrados > 0) add(plural(r.borrados, "lote borrado", "lotes borrados") + " en otro dispositivo")
            }
            return if (partes.isEmpty()) "Tus lotes están al día." else "Lotes sincronizados: ${partes.joinToString(", ")}."
        }

        fun mensajeCierreBloqueado(r: ResultadoCierreSesion.Bloqueada): String =
            "No se puede cerrar sesión: hay ${plural(r.pendientes, "cambio local pendiente", "cambios locales pendientes")} " +
                "de sincronizar. Verificá tu conexión a internet e intentá de nuevo." +
                (r.causa?.let { "\n\nDetalle: $it" } ?: "")

        private fun avisoDeFallo(r: ResultadoSincronizacion): AvisoCuenta = when (r) {
            is ResultadoSincronizacion.Error -> AvisoCuenta(
                "No se pudo sincronizar: ${r.mensaje} " +
                    "Quedan ${plural(r.pendientes, "cambio pendiente", "cambios pendientes")}; se reintenta solo cuando vuelva la conexión.",
                TipoAviso.ERROR
            )
            ResultadoSincronizacion.OtraCuenta -> AvisoCuenta(
                "Este dispositivo tiene lotes sin subir de otra cuenta GRANEX. No se mezclan con los tuyos: " +
                    "cerrá sesión e ingresá con esa cuenta para subirlos.",
                TipoAviso.ERROR
            )
            ResultadoSincronizacion.SinSesion -> AvisoCuenta("La sesión venció. Ingresá de nuevo.", TipoAviso.ERROR)
            is ResultadoSincronizacion.Lista -> AvisoCuenta(describir(r), TipoAviso.EXITO)
        }

        private fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"
    }
}
