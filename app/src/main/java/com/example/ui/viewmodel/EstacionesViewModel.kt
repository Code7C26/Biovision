package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.supabase.DatosEstacion
import com.example.data.remote.supabase.ErrorEstaciones
import com.example.data.remote.supabase.EstacionDeCuenta
import com.example.data.remote.supabase.EstacionMeteoRemota
import com.example.data.remote.supabase.ResultadoAgregarEstacion
import com.example.data.remote.supabase.ServicioEstaciones
import com.example.utils.MarcaEstacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EstadoEstaciones {
    /** La app no tiene backend configurado: las estaciones viven en la cuenta. */
    data object SinBackend : EstadoEstaciones
    data object Cargando : EstadoEstaciones
    data class Lista(val estaciones: List<EstacionMeteoRemota>) : EstadoEstaciones
    data class Error(val mensaje: String) : EstadoEstaciones
}

sealed interface EstadoDatosEstacion {
    data object Ninguno : EstadoDatosEstacion
    data object Cargando : EstadoDatosEstacion
    /** [actualizando]: se están pidiendo datos nuevos y se siguen mostrando estos. */
    data class Listo(val datos: DatosEstacion, val cargadoEnMs: Long, val actualizando: Boolean = false) : EstadoDatosEstacion
    data class Error(val mensaje: String, val anteriores: DatosEstacion? = null) : EstadoDatosEstacion
}

sealed interface EstadoAgregarEstacion {
    data object Inactivo : EstadoAgregarEstacion
    data object Enviando : EstadoAgregarEstacion
    data class Elegir(val opciones: List<EstacionDeCuenta>) : EstadoAgregarEstacion
    /** [token]: el código del envío directo, que se muestra una sola vez. */
    data class Listo(val estacion: EstacionMeteoRemota, val token: String?) : EstadoAgregarEstacion
    data class Error(val mensaje: String) : EstadoAgregarEstacion
}

/**
 * Estaciones meteorológicas propias del productor (Edge Function `estaciones`). Las claves de la
 * marca solo pasan por acá mientras se conecta la estación: el servidor las guarda cifradas y nunca
 * las devuelve.
 */
class EstacionesViewModel(
    private val servicio: ServicioEstaciones?,
    private val leerSeleccion: () -> String? = { null },
    private val guardarSeleccion: (String?) -> Unit = {},
    private val ahora: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _estaciones = MutableStateFlow<EstadoEstaciones>(
        if (servicio == null) EstadoEstaciones.SinBackend else EstadoEstaciones.Cargando
    )
    val estaciones: StateFlow<EstadoEstaciones> = _estaciones.asStateFlow()

    private val _seleccionada = MutableStateFlow<String?>(null)
    val seleccionada: StateFlow<String?> = _seleccionada.asStateFlow()

    private val _datos = MutableStateFlow<EstadoDatosEstacion>(EstadoDatosEstacion.Ninguno)
    val datos: StateFlow<EstadoDatosEstacion> = _datos.asStateFlow()

    private val _agregar = MutableStateFlow<EstadoAgregarEstacion>(EstadoAgregarEstacion.Inactivo)
    val agregar: StateFlow<EstadoAgregarEstacion> = _agregar.asStateFlow()

    private val _eliminando = MutableStateFlow<String?>(null)
    val eliminando: StateFlow<String?> = _eliminando.asStateFlow()

    /** Datos ya cargados por estación: cambiar de estación no vuelve a pedir lo que ya está. */
    private val cache = mutableMapOf<String, Pair<DatosEstacion, Long>>()
    private var trabajoDatos: Job? = null
    private var trabajoLista: Job? = null

    /** Lo que se mandó al agregar, para repetirlo con la estación elegida. */
    private var pendiente: Triple<MarcaEstacion, String, Map<String, String>>? = null

    init {
        cargarLista()
    }

    fun cargarLista() {
        val s = servicio ?: return
        if (trabajoLista?.isActive == true) return
        trabajoLista = viewModelScope.launch {
            if (_estaciones.value !is EstadoEstaciones.Lista) _estaciones.value = EstadoEstaciones.Cargando
            try {
                val lista = s.listar()
                _estaciones.value = EstadoEstaciones.Lista(lista)
                val guardada = _seleccionada.value ?: leerSeleccion()
                val elegida = lista.firstOrNull { it.id == guardada } ?: lista.firstOrNull()
                seleccionarInterno(elegida?.id, forzar = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_estaciones.value !is EstadoEstaciones.Lista) _estaciones.value = EstadoEstaciones.Error(mensaje(e))
            }
        }
    }

    fun seleccionar(id: String) = seleccionarInterno(id, forzar = false)

    private fun seleccionarInterno(id: String?, forzar: Boolean) {
        val cambio = id != _seleccionada.value
        _seleccionada.value = id
        guardarSeleccion(id)
        if (id == null) {
            trabajoDatos?.cancel()
            _datos.value = EstadoDatosEstacion.Ninguno
            return
        }
        val enCache = cache[id]
        if (cambio && enCache != null) _datos.value = EstadoDatosEstacion.Listo(enCache.first, enCache.second)
        val vencido = enCache == null || ahora() - enCache.second > VIGENCIA_MS
        if (forzar || vencido || _datos.value !is EstadoDatosEstacion.Listo) cargarDatos(id)
    }

    /** Pide los datos de nuevo (botón actualizar). */
    fun actualizar() {
        if (_estaciones.value !is EstadoEstaciones.Lista) cargarLista()
        _seleccionada.value?.let { seleccionarInterno(it, forzar = true) }
    }

    /** Refresca mientras la pantalla está visible (se cancela al salir). */
    suspend fun mantenerActualizado() {
        while (true) {
            _seleccionada.value?.let { id ->
                val enCache = cache[id]
                if (enCache == null || ahora() - enCache.second > VIGENCIA_MS) cargarDatos(id)
            }
            delay(VIGENCIA_MS)
        }
    }

    private fun cargarDatos(id: String) {
        val s = servicio ?: return
        trabajoDatos?.cancel()
        trabajoDatos = viewModelScope.launch {
            val previos = (_datos.value as? EstadoDatosEstacion.Listo)?.takeIf { _seleccionada.value == id }
            _datos.value = previos?.copy(actualizando = true) ?: EstadoDatosEstacion.Cargando
            try {
                val d = s.datos(id)
                val momento = ahora()
                cache[id] = d to momento
                if (_seleccionada.value == id) _datos.value = EstadoDatosEstacion.Listo(d, momento)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_seleccionada.value == id) _datos.value = EstadoDatosEstacion.Error(mensaje(e), previos?.datos)
            }
        }
    }

    fun conectar(marca: MarcaEstacion, nombre: String, credenciales: Map<String, String>) {
        pendiente = Triple(marca, nombre, credenciales)
        enviar(identificador = null)
    }

    /** La cuenta tenía varias estaciones: conecta la elegida. */
    fun elegir(opcion: EstacionDeCuenta) = enviar(opcion.identificador)

    private fun enviar(identificador: String?) {
        val s = servicio ?: return
        val (marca, nombre, credenciales) = pendiente ?: return
        if (_agregar.value == EstadoAgregarEstacion.Enviando) return
        _agregar.value = EstadoAgregarEstacion.Enviando
        viewModelScope.launch {
            _agregar.value = try {
                when (val r = s.agregar(marca, nombre, credenciales, identificador)) {
                    is ResultadoAgregarEstacion.Elegir -> EstadoAgregarEstacion.Elegir(r.opciones)
                    is ResultadoAgregarEstacion.Agregada -> {
                        pendiente = null
                        val lista = (_estaciones.value as? EstadoEstaciones.Lista)?.estaciones.orEmpty()
                        _estaciones.value = EstadoEstaciones.Lista(lista + r.estacion)
                        seleccionarInterno(r.estacion.id, forzar = true)
                        EstadoAgregarEstacion.Listo(r.estacion, r.token)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoAgregarEstacion.Error(mensaje(e))
            }
        }
    }

    /** Cierra el formulario y olvida las claves que se habían escrito. */
    fun terminarAgregar() {
        pendiente = null
        _agregar.value = EstadoAgregarEstacion.Inactivo
    }

    /** Vuelve al formulario después de un error, sin perder lo escrito en pantalla. */
    fun descartarError() {
        if (_agregar.value is EstadoAgregarEstacion.Error) _agregar.value = EstadoAgregarEstacion.Inactivo
    }

    fun eliminar(id: String) {
        val s = servicio ?: return
        if (_eliminando.value != null) return
        _eliminando.value = id
        viewModelScope.launch {
            try {
                s.eliminar(id)
                cache.remove(id)
                val lista = (_estaciones.value as? EstadoEstaciones.Lista)?.estaciones.orEmpty().filterNot { it.id == id }
                _estaciones.value = EstadoEstaciones.Lista(lista)
                if (_seleccionada.value == id) seleccionarInterno(lista.firstOrNull()?.id, forzar = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estaciones.value = (_estaciones.value as? EstadoEstaciones.Lista) ?: EstadoEstaciones.Error(mensaje(e))
                _errorEliminar.value = mensaje(e)
            } finally {
                _eliminando.value = null
            }
        }
    }

    private val _errorEliminar = MutableStateFlow<String?>(null)
    val errorEliminar: StateFlow<String?> = _errorEliminar.asStateFlow()
    fun descartarErrorEliminar() {
        _errorEliminar.value = null
    }

    private fun mensaje(e: Exception): String =
        (e as? ErrorEstaciones)?.message ?: "No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo."

    companion object {
        /** Las estaciones envían cada 1 a 15 minutos: más seguido no trae nada nuevo. */
        const val VIGENCIA_MS = 5 * 60_000L
    }
}
