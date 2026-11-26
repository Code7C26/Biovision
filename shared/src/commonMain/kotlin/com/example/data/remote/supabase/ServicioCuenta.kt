package com.example.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.serialization.json.JsonPrimitive
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Estado de la cuenta GRANEX (backend Supabase). */
sealed interface EstadoCuenta {
    /** Sin proyecto Supabase configurado: la app funciona solo con datos locales. */
    data object SinBackend : EstadoCuenta
    data object Verificando : EstadoCuenta
    data object SinSesion : EstadoCuenta
    data class Conectado(val usuarioId: String, val correo: String?) : EstadoCuenta
    /** Había sesión pero no se pudo renovar (sin red): los datos locales siguen disponibles. */
    data object SinConexion : EstadoCuenta
}

/** Qué pasó al crear la cuenta. */
enum class ResultadoRegistro {
    /** El proyecto no pide confirmar el correo: la sesión ya empezó. */
    SESION_INICIADA,
    /** Hay que abrir el enlace del correo y después ingresar con la contraseña. */
    CONFIRMAR_CORREO
}

/** Operaciones de cuenta que usa [com.example.data.sync.ControladorCuenta] (interfaz: los tests usan una falsa). */
interface Autenticacion {
    /** false sin proyecto Supabase configurado. */
    val configurada: Boolean
    val estado: Flow<EstadoCuenta>
    suspend fun ingresar(correo: String, clave: String): Result<Unit>
    suspend fun registrarse(correo: String, clave: String): Result<ResultadoRegistro>
    suspend fun salir(): Result<Unit>
    /**
     * Borra la cuenta y sus datos en el servidor. La sesión local la cierra después el controlador
     * (con [salir]), una vez borrados los datos del dispositivo.
     */
    suspend fun eliminarCuenta(): Result<Unit>

    /** Crea la cuenta con los datos del productor (quedan en la cuenta hasta crear el perfil). */
    suspend fun registrarse(correo: String, clave: String, perfil: PerfilNuevo): Result<ResultadoRegistro> =
        registrarse(correo, clave)

    /** Ingreso con el token de Google ([nonce] = el valor sin cifrar que se usó al pedirlo). */
    suspend fun ingresarConGoogle(idToken: String, nonce: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("Google no está disponible."))

    /** Envía por correo un código para cambiar la contraseña. */
    suspend fun pedirCodigoClave(correo: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(SIN_BACKEND_TEXTO))

    /** Con el código del correo, inicia la sesión y cambia la contraseña. */
    suspend fun cambiarClaveConCodigo(correo: String, codigo: String, claveNueva: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(SIN_BACKEND_TEXTO))

    /** Activa la cuenta con el código de 6 dígitos del correo de confirmación (y empieza la sesión). */
    suspend fun confirmarCorreo(correo: String, codigo: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(SIN_BACKEND_TEXTO))

    /** Vuelve a mandar el correo de confirmación. */
    suspend fun reenviarConfirmacion(correo: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(SIN_BACKEND_TEXTO))

    /** Datos que el usuario cargó al registrarse o que trae Google (nombre); null sin sesión. */
    fun perfilDeLaCuenta(): PerfilNuevo? = null
}

private const val SIN_BACKEND_TEXTO = "La sincronización en la nube no está configurada en esta versión."

/** Datos del productor que se piden al crear la cuenta (tabla `perfiles`). */
data class PerfilNuevo(
    val nombre: String,
    val establecimiento: String,
    val provincia: String,
    val localidad: String = "",
    val hectareas: Double? = null
) {
    /** `ubicacion_principal` guarda "Localidad, Provincia". */
    val ubicacion: String get() = listOf(localidad.trim(), provincia.trim()).filter { it.isNotEmpty() }.joinToString(", ")

    fun aRemoto(usuarioId: String) = PerfilRemoto(
        id = usuarioId,
        nombreProductor = nombre.trim(),
        establecimiento = establecimiento.trim(),
        ubicacionPrincipal = ubicacion.ifEmpty { null },
        hectareasTotales = hectareas
    )

    companion object {
        fun desdeRemoto(p: PerfilRemoto): PerfilNuevo {
            val ubicacion = p.ubicacionPrincipal.orEmpty()
            val corte = ubicacion.lastIndexOf(',')
            return PerfilNuevo(
                nombre = p.nombreProductor.orEmpty(),
                establecimiento = p.establecimiento.orEmpty(),
                provincia = if (corte >= 0) ubicacion.substring(corte + 1).trim() else ubicacion.trim(),
                localidad = if (corte >= 0) ubicacion.substring(0, corte).trim() else "",
                hectareas = p.hectareasTotales
            )
        }
    }
}

/** Validación del perfil (mensajes por campo, null = válido). */
object ValidacionPerfil {
    data class Errores(
        val nombre: String? = null,
        val establecimiento: String? = null,
        val provincia: String? = null,
        val hectareas: String? = null
    ) {
        val hayErrores: Boolean get() = nombre != null || establecimiento != null || provincia != null || hectareas != null
    }

    /** [hectareas] es el texto del campo: vacío = sin dato. */
    fun validar(nombre: String, establecimiento: String, provincia: String, hectareas: String): Errores = Errores(
        nombre = when {
            nombre.isBlank() -> "Ingresá tu nombre y apellido."
            nombre.trim().length < 3 -> "Ingresá tu nombre completo."
            nombre.length > 120 -> "Máximo 120 caracteres."
            else -> null
        },
        establecimiento = when {
            establecimiento.isBlank() -> "Ingresá el establecimiento o la empresa."
            establecimiento.length > 120 -> "Máximo 120 caracteres."
            else -> null
        },
        provincia = if (provincia.isBlank()) "Elegí la provincia." else null,
        hectareas = hectareas.trim().takeIf { it.isNotEmpty() }?.let { t ->
            val v = t.replace(".", "").replace(',', '.').toDoubleOrNull()
            if (v == null || v < 0 || v > 10_000_000) "Ingresá las hectáreas en números." else null
        }
    )

    fun hectareas(texto: String): Double? = texto.trim().replace(".", "").replace(',', '.').toDoubleOrNull()
}

/** Provincias argentinas, para el selector del perfil. */
val PROVINCIAS_ARGENTINAS = listOf(
    "Buenos Aires", "Catamarca", "Chaco", "Chubut", "Ciudad Autónoma de Buenos Aires", "Córdoba", "Corrientes",
    "Entre Ríos", "Formosa", "Jujuy", "La Pampa", "La Rioja", "Mendoza", "Misiones", "Neuquén", "Río Negro",
    "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe", "Santiago del Estero", "Tierra del Fuego", "Tucumán"
)

/**
 * Cuentas con correo y contraseña (Supabase Auth). Con [supabase] null (sin backend configurado)
 * todas las operaciones responden [EstadoCuenta.SinBackend] o fallan con un mensaje claro.
 *
 * Postgrest y Storage toman el token de la sesión de este mismo cliente: con sesión, cada consulta va
 * como el usuario (y RLS la limita a sus filas); sin sesión, va con la clave pública y no ve nada.
 */
class ServicioCuenta(private val supabase: SupabaseClient?) : Autenticacion {

    override val configurada: Boolean get() = supabase != null

    override val estado: Flow<EstadoCuenta> = supabase?.auth?.sessionStatus?.map { estado ->
        when (estado) {
            is SessionStatus.Authenticated -> EstadoCuenta.Conectado(estado.session.user?.id.orEmpty(), estado.session.user?.email)
            is SessionStatus.NotAuthenticated -> EstadoCuenta.SinSesion
            is SessionStatus.Initializing -> EstadoCuenta.Verificando
            is SessionStatus.RefreshFailure -> EstadoCuenta.SinConexion
        }
    } ?: flowOf(EstadoCuenta.SinBackend)

    /** Id del usuario con sesión (también sin red, con la sesión guardada); null si no hay. */
    fun usuarioId(): String? = supabase?.auth?.currentUserOrNull()?.id

    /** Como [usuarioId], pero primero espera a que termine de cargarse la sesión guardada (al arrancar). */
    suspend fun usuarioIdCargado(): String? {
        val auth = supabase?.auth ?: return null
        auth.awaitInitialization()
        return auth.currentUserOrNull()?.id
    }

    override suspend fun ingresar(correo: String, clave: String): Result<Unit> = operar { cliente ->
        // Con correo y contraseña no hay redirección (la de por defecto lee window, que en Node no existe)
        cliente.auth.signInWith(Email, redirectUrl = null) {
            email = correo.trim()
            password = clave
        }
    }

    /** Crea la cuenta. Si el proyecto pide confirmar el correo, la sesión empieza al confirmarlo e ingresar. */
    override suspend fun registrarse(correo: String, clave: String): Result<ResultadoRegistro> = operar { cliente ->
        cliente.auth.signUpWith(Email) {
            email = correo.trim()
            password = clave
        }
        if (cliente.auth.currentSessionOrNull() != null) ResultadoRegistro.SESION_INICIADA else ResultadoRegistro.CONFIRMAR_CORREO
    }

    /**
     * Los datos del productor viajan como metadatos de la cuenta: si hay que confirmar el correo, la
     * fila de `perfiles` se crea recién al primer ingreso (en cualquier dispositivo) a partir de ellos.
     */
    override suspend fun registrarse(correo: String, clave: String, perfil: PerfilNuevo): Result<ResultadoRegistro> = operar { cliente ->
        cliente.auth.signUpWith(Email) {
            email = correo.trim()
            password = clave
            data = buildJsonObject {
                put(META_NOMBRE, perfil.nombre.trim())
                put(META_ESTABLECIMIENTO, perfil.establecimiento.trim())
                put(META_PROVINCIA, perfil.provincia.trim())
                put(META_LOCALIDAD, perfil.localidad.trim())
                perfil.hectareas?.let { put(META_HECTAREAS, it) }
            }
        }
        if (cliente.auth.currentSessionOrNull() != null) ResultadoRegistro.SESION_INICIADA else ResultadoRegistro.CONFIRMAR_CORREO
    }

    override suspend fun ingresarConGoogle(idToken: String, nonce: String?): Result<Unit> = operar { cliente ->
        cliente.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            this.nonce = nonce
        }
    }

    /** El correo de "Reset Password" de Supabase tiene que incluir {{ .Token }} (ver docs/backend/SUPABASE.md). */
    override suspend fun pedirCodigoClave(correo: String): Result<Unit> = operar { cliente ->
        cliente.auth.resetPasswordForEmail(correo.trim())
    }

    override suspend fun cambiarClaveConCodigo(correo: String, codigo: String, claveNueva: String): Result<Unit> = operar { cliente ->
        cliente.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = correo.trim(), token = codigo.trim())
        cliente.auth.updateUser { password = claveNueva }
    }

    /** La plantilla "Confirm signup" de Supabase tiene que incluir {{ .Token }} (supabase/plantillas). */
    override suspend fun confirmarCorreo(correo: String, codigo: String): Result<Unit> = operar { cliente ->
        cliente.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = correo.trim(), token = codigo.trim())
    }

    override suspend fun reenviarConfirmacion(correo: String): Result<Unit> = operar { cliente ->
        cliente.auth.resendEmail(OtpType.Email.SIGNUP, correo.trim())
    }

    override fun perfilDeLaCuenta(): PerfilNuevo? {
        val usuario = supabase?.auth?.currentUserOrNull() ?: return null
        val m = usuario.userMetadata ?: return null
        fun texto(clave: String) = (m[clave] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        return PerfilNuevo(
            // Google trae "full_name" o "name"
            nombre = texto(META_NOMBRE).ifEmpty { texto("full_name") }.ifEmpty { texto("name") },
            establecimiento = texto(META_ESTABLECIMIENTO),
            provincia = texto(META_PROVINCIA),
            localidad = texto(META_LOCALIDAD),
            hectareas = (m[META_HECTAREAS] as? JsonPrimitive)?.content?.toDoubleOrNull()
        )
    }

    /**
     * Cierra la sesión en este dispositivo. Si el servidor no responde (sin red), igual borra la sesión
     * guardada: lo que importa es que el teléfono deje de actuar como el usuario. Para no perder
     * cambios, llamarlo a través de SincronizadorLotes.cerrarSesion.
     */
    override suspend fun salir(): Result<Unit> = operar { cliente ->
        try {
            cliente.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cliente.auth.clearSession()
        }
    }

    /**
     * La función `cuenta` (supabase/functions/cuenta) borra los archivos y el usuario; sus filas se
     * borran en cascada. La sesión guardada ya no sirve: [salir] la descarta (signOut falla y cae en
     * clearSession).
     */
    override suspend fun eliminarCuenta(): Result<Unit> {
        val cliente = supabase ?: return Result.failure(IllegalStateException(SIN_BACKEND))
        try {
            cliente.functions.invoke(FUNCION_CUENTA, body = buildJsonObject { put("accion", "eliminar") })
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            val mensaje = SentinelGranex.mensajeDelServidor(e.error) ?: mensajeDeError(e)
            return Result.failure(Exception(mensaje, e))
        } catch (e: Exception) {
            return Result.failure(Exception(mensajeDeError(e), e))
        }
        return Result.success(Unit)
    }

    private suspend fun <T> operar(accion: suspend (SupabaseClient) -> T): Result<T> {
        val cliente = supabase ?: return Result.failure(IllegalStateException(SIN_BACKEND))
        val resultado: T
        try {
            resultado = accion(cliente)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(Exception(mensajeDeError(e), e))
        }
        return Result.success(resultado)
    }

    companion object {
        const val SIN_BACKEND = "La sincronización en la nube no está configurada en esta versión."
        const val FUNCION_CUENTA = "cuenta"
        /** Ingreso con un correo sin confirmar: la pantalla pide el código (ControladorCuenta lo reconoce). */
        const val MENSAJE_SIN_CONFIRMAR = "Tu cuenta todavía no está activada: ingresá el código que te enviamos por correo."
        private const val META_NOMBRE = "nombre_productor"
        private const val META_ESTABLECIMIENTO = "establecimiento"
        private const val META_PROVINCIA = "provincia"
        private const val META_LOCALIDAD = "localidad"
        private const val META_HECTAREAS = "hectareas"

        /** Mensaje para mostrar al usuario según el error de Supabase. */
        fun mensajeDeError(e: Throwable): String = when {
            e is HttpRequestException || e is HttpRequestTimeoutException ->
                "Sin conexión con el servidor. Revisá tu conexión a internet e intentá de nuevo."
            e is AuthRestException -> when (e.errorCode) {
                // Supabase no distingue "usuario inexistente" de "contraseña incorrecta" (no revela qué correos tienen cuenta)
                AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound -> "Correo o contraseña incorrectos."
                AuthErrorCode.EmailNotConfirmed -> MENSAJE_SIN_CONFIRMAR
                AuthErrorCode.OtpExpired -> "El código no es correcto o venció. Pedí uno nuevo."
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Ya existe una cuenta con ese correo. Ingresá con tu contraseña."
                AuthErrorCode.WeakPassword -> "La contraseña es muy débil: usá al menos 8 caracteres combinando letras y números."
                AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "El correo no es válido."
                AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled -> "Por ahora no se pueden crear cuentas nuevas."
                AuthErrorCode.UserBanned -> "Esta cuenta está suspendida."
                AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> "Demasiados intentos. Esperá unos minutos."
                AuthErrorCode.SessionExpired, AuthErrorCode.SessionNotFound, AuthErrorCode.RefreshTokenNotFound ->
                    "La sesión venció. Ingresá de nuevo."
                else -> "No se pudo completar la operación (${e.error})."
            }
            e is RestException && e.statusCode >= 500 -> "El servidor no responde. Intentá de nuevo en unos minutos."
            e is RestException && e.statusCode == 401 -> "La sesión venció. Ingresá de nuevo."
            else -> "No se pudo completar la operación."
        }
    }
}

/** Validación de los campos de cuenta antes de enviarlos (mensajes por campo, null = válido). */
object ValidacionCuenta {
    const val LARGO_MINIMO_CLAVE = 8

    private val FORMATO_CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    data class Errores(val correo: String? = null, val clave: String? = null, val confirmacion: String? = null) {
        val hayErrores: Boolean get() = correo != null || clave != null || confirmacion != null
    }

    /** Al ingresar solo se pide la contraseña; al crear la cuenta, el largo mínimo y que coincida la repetición. */
    fun validar(correo: String, clave: String, confirmacion: String?, creandoCuenta: Boolean): Errores {
        val c = correo.trim()
        return Errores(
            correo = when {
                c.isEmpty() -> "Ingresá tu correo."
                !FORMATO_CORREO.matches(c) -> "El correo no es válido."
                else -> null
            },
            clave = when {
                clave.isEmpty() -> "Ingresá la contraseña."
                creandoCuenta && clave.length < LARGO_MINIMO_CLAVE -> "Usá al menos $LARGO_MINIMO_CLAVE caracteres."
                creandoCuenta && (clave.none { it.isLetter() } || clave.none { it.isDigit() }) -> "Combiná letras y números."
                else -> null
            },
            confirmacion = if (creandoCuenta && confirmacion != clave) "Las contraseñas no coinciden." else null
        )
    }
}
