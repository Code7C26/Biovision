package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.Autenticacion
import com.example.data.remote.supabase.EstadoCuenta
import com.example.data.remote.supabase.PerfilNuevo
import com.example.data.remote.supabase.PerfilRemoto
import com.example.utils.WeatherNotificationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Perfil del productor según la sesión. */
sealed interface EstadoPerfil {
    data object Cargando : EstadoPerfil
    data object SinSesion : EstadoPerfil
    data class Listo(val perfil: PerfilNuevo, val correo: String?) : EstadoPerfil
    /** Con sesión pero sin los datos mínimos (entró con Google o es una cuenta anterior): se piden una vez. */
    data class Incompleto(val sugerido: PerfilNuevo, val correo: String?) : EstadoPerfil
    /** Sin red para leer el perfil: la app sigue con lo guardado en el teléfono. */
    data class SinConexion(val guardado: PerfilNuevo?, val correo: String?) : EstadoPerfil
    /** Sin backend configurado: perfil solo en el teléfono. */
    data class Local(val perfil: PerfilNuevo) : EstadoPerfil
}

/** Tabla `perfiles` (interfaz: las pruebas usan una en memoria). */
interface PerfilesRemotos {
    suspend fun miPerfil(): PerfilRemoto?
    suspend fun guardar(perfil: PerfilRemoto)
}

/**
 * Datos del productor: los que cargó al crear la cuenta (o completó después) en la tabla `perfiles`.
 * Si al ingresar la fila no existe pero la cuenta trae los datos del registro (se crearon antes de
 * confirmar el correo), se crea sola. Guarda una copia en el teléfono para mostrarla sin red.
 */
class PerfilViewModel(
    private val cuenta: Autenticacion,
    private val remoto: PerfilesRemotos?,
    estadoCuenta: StateFlow<EstadoCuenta>,
    private val contexto: Context
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoPerfil>(EstadoPerfil.Cargando)
    val estado: StateFlow<EstadoPerfil> = _estado.asStateFlow()

    private val _guardando = MutableStateFlow(false)
    val guardando: StateFlow<Boolean> = _guardando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var carga: Job? = null
    private var ultimoUsuario: String? = null

    init {
        viewModelScope.launch {
            estadoCuenta.collect { e ->
                when (e) {
                    EstadoCuenta.SinBackend -> _estado.value = EstadoPerfil.Local(guardadoLocal() ?: PerfilNuevo("", "", ""))
                    EstadoCuenta.Verificando -> Unit
                    EstadoCuenta.SinSesion -> {
                        // Al salir (o eliminar la cuenta) el teléfono no muestra los datos de esa cuenta
                        if (ultimoUsuario != null) {
                            guardarLocal(PerfilNuevo("", "", ""))
                            WeatherNotificationService.setUserEmail(contexto, "")
                        }
                        ultimoUsuario = null
                        _estado.value = EstadoPerfil.SinSesion
                    }
                    EstadoCuenta.SinConexion -> _estado.value = EstadoPerfil.SinConexion(guardadoLocal(), correoLocal())
                    is EstadoCuenta.Conectado -> if (e.usuarioId != ultimoUsuario || _estado.value !is EstadoPerfil.Listo) {
                        ultimoUsuario = e.usuarioId
                        cargar(e.usuarioId, e.correo)
                    }
                }
            }
        }
    }

    private fun cargar(usuarioId: String, correo: String?) {
        carga?.cancel()
        _estado.value = EstadoPerfil.Cargando
        carga = viewModelScope.launch {
            val leido = withTimeoutOrNull(TIEMPO_MAXIMO_MS) { runCatching { remoto?.miPerfil() } }
            if (leido == null || leido.isFailure) {
                _estado.value = EstadoPerfil.SinConexion(guardadoLocal(), correo)
                return@launch
            }
            val enTabla = leido.getOrNull()?.let(PerfilNuevo::desdeRemoto)
            if (enTabla != null && completo(enTabla)) {
                listo(enTabla, correo)
                return@launch
            }
            // Datos del registro guardados en la cuenta (o el nombre que trae Google)
            val deLaCuenta = cuenta.perfilDeLaCuenta()
            val sugerido = combinar(enTabla, deLaCuenta)
            if (completo(sugerido)) {
                val guardado = runCatching { remoto?.guardar(sugerido.aRemoto(usuarioId)) }
                if (guardado.isSuccess) {
                    listo(sugerido, correo)
                    return@launch
                }
            }
            _estado.value = EstadoPerfil.Incompleto(sugerido, correo)
        }
    }

    /** Guarda el perfil (completar después de Google o editar desde "Ajustes de Perfil"). */
    fun guardar(perfil: PerfilNuevo, alGuardar: () -> Unit = {}) {
        val actual = _estado.value
        _error.value = null
        if (actual is EstadoPerfil.Local) {
            guardarLocal(perfil)
            _estado.value = EstadoPerfil.Local(perfil)
            alGuardar()
            return
        }
        val usuario = ultimoUsuario ?: return
        val correo = when (actual) {
            is EstadoPerfil.Listo -> actual.correo
            is EstadoPerfil.Incompleto -> actual.correo
            is EstadoPerfil.SinConexion -> actual.correo
            else -> null
        }
        viewModelScope.launch {
            _guardando.value = true
            runCatching { remoto?.guardar(perfil.aRemoto(usuario)) }
                .onSuccess {
                    listo(perfil, correo)
                    alGuardar()
                }
                .onFailure { _error.value = "No se pudo guardar el perfil. Revisá tu conexión e intentá de nuevo." }
            _guardando.value = false
        }
    }

    fun descartarError() {
        _error.value = null
    }

    private fun listo(perfil: PerfilNuevo, correo: String?) {
        guardarLocal(perfil)
        correo?.let { WeatherNotificationService.setUserEmail(contexto, it) }
        _estado.value = EstadoPerfil.Listo(perfil, correo)
    }

    private fun completo(p: PerfilNuevo) = p.nombre.isNotBlank() && p.establecimiento.isNotBlank() && p.provincia.isNotBlank()

    private fun combinar(a: PerfilNuevo?, b: PerfilNuevo?): PerfilNuevo = PerfilNuevo(
        nombre = a?.nombre?.ifBlank { null } ?: b?.nombre.orEmpty(),
        establecimiento = a?.establecimiento?.ifBlank { null } ?: b?.establecimiento.orEmpty(),
        provincia = a?.provincia?.ifBlank { null } ?: b?.provincia.orEmpty(),
        localidad = a?.localidad?.ifBlank { null } ?: b?.localidad.orEmpty(),
        hectareas = a?.hectareas ?: b?.hectareas
    )

    // Copia en el teléfono: el nombre y el establecimiento se muestran también sin red
    private fun guardadoLocal(): PerfilNuevo? {
        val nombre = WeatherNotificationService.getUserName(contexto)
        val establecimiento = WeatherNotificationService.getUserFarm(contexto)
        if (nombre.isBlank() && establecimiento.isBlank()) return null
        val ubicacion = WeatherNotificationService.getUserLocation(contexto)
        val corte = ubicacion.lastIndexOf(',')
        return PerfilNuevo(
            nombre = nombre,
            establecimiento = establecimiento,
            provincia = if (corte >= 0) ubicacion.substring(corte + 1).trim() else ubicacion.trim(),
            localidad = if (corte >= 0) ubicacion.substring(0, corte).trim() else "",
            hectareas = WeatherNotificationService.getUserHectares(contexto)
        )
    }

    private fun correoLocal(): String? = WeatherNotificationService.getUserEmail(contexto).ifBlank { null }

    private fun guardarLocal(p: PerfilNuevo) {
        WeatherNotificationService.setUserName(contexto, p.nombre)
        WeatherNotificationService.setUserFarm(contexto, p.establecimiento)
        WeatherNotificationService.setUserLocation(contexto, p.ubicacion)
        WeatherNotificationService.setUserHectares(contexto, p.hectareas)
    }

    private companion object {
        const val TIEMPO_MAXIMO_MS = 12_000L
    }
}
