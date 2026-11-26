package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ktor.PronosticoOpenMeteo
import com.example.data.remote.ktor.ServicioLocalidades
import com.example.data.remote.ktor.ServicioPronostico
import com.example.data.remote.ktor.UvHoy
import com.example.utils.HerramientasAgro
import com.example.utils.HerramientasClima
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.data.repository.PreferenciasUsuario
import com.example.data.repository.SugerenciaLocalidad
import com.example.web.ui.coordenadaUrl
import com.example.web.ui.horaActual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Localidad en pantalla; [zoom] es el del mapa de Windy (máx. 11 en el embed). */
data class CiudadClima(val nombre: String, val lat: Double, val lon: Double, val zoom: Int)

sealed interface EstadoPronostico {
    data object Cargando : EstadoPronostico
    /** [uv] null si ese pedido falló: el resto del pronóstico se muestra igual. */
    data class Ok(val datos: PronosticoOpenMeteo, val hora: String, val uv: UvHoy? = null) : EstadoPronostico
    data class Error(val mensaje: String) : EstadoPronostico
}

/** Capas del mapa de Windy (parámetro overlay del embed). */
enum class CapaWindy(val etiqueta: String, val id: String) {
    VIENTO("Viento", "wind"),
    LLUVIA("Lluvia", "rain"),
    TEMPERATURA("Temperatura", "temp"),
    NUBES("Nubes", "clouds"),
    HUMEDAD("Humedad", "rh")
}

class ClimaWebViewModel(
    private val servicio: ServicioPronostico,
    localidades: ServicioLocalidades,
    private val preferencias: PreferenciasUsuario?
) : ViewModel() {
    private val _ciudad = MutableStateFlow(ciudadInicial())
    val ciudad: StateFlow<CiudadClima> = _ciudad.asStateFlow()

    private val _estado = MutableStateFlow<EstadoPronostico>(EstadoPronostico.Cargando)
    val estado: StateFlow<EstadoPronostico> = _estado.asStateFlow()

    /** Pulverización, helada y balance hídrico (como en Android); null mientras carga o si falló. */
    private val _agro = MutableStateFlow<HerramientasAgro?>(null)
    val agro: StateFlow<HerramientasAgro?> = _agro.asStateFlow()

    private val _capa = MutableStateFlow(CapaWindy.VIENTO)
    val capa: StateFlow<CapaWindy> = _capa.asStateFlow()

    /** Sesgo por cercanía: la localidad en pantalla (la web no pide la ubicación del dispositivo). */
    val buscador = BuscadorLocalidades(localidades, viewModelScope) { _ciudad.value.let { it.lat to it.lon } }

    private var carga: Job? = null

    init {
        cargar()
    }

    fun elegir(s: SugerenciaLocalidad) {
        val zoom = s.tipo.zoom(maximo = 11)
        _ciudad.value = CiudadClima(s.nombre, s.lat, s.lon, zoom)
        preferencias?.guardarUltimaCiudad(s.nombre, s.lat, s.lon)
        preferencias?.guardarZoomLocalidad(s.nombre, zoom)
        buscador.limpiar()
        cargar()
    }

    fun elegirCapa(capa: CapaWindy) {
        _capa.value = capa
    }

    fun cargar() {
        carga?.cancel()
        carga = viewModelScope.launch {
            _estado.value = EstadoPronostico.Cargando
            val c = _ciudad.value
            _agro.value = null
            // Aparte del pronóstico: si estos pedidos fallan, el resto de la pantalla se muestra igual
            launch { _agro.value = cargarAgro(c) }
            _estado.value = try {
                val uv = async { runCatching { servicio.uv(c.lat, c.lon) }.getOrNull() }
                EstadoPronostico.Ok(conReintentos { servicio.pronostico(c.lat, c.lon) }, horaActual(), uv.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                EstadoPronostico.Error("No se pudo obtener el pronóstico de ${c.nombre}. Revisá la conexión.")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun cargarAgro(c: CiudadClima): HerramientasAgro? = coroutineScope {
        val horas = async { runCatching { conReintentos { servicio.horasAgro(c.lat, c.lon) } }.getOrNull() }
        val balance = async { runCatching { conReintentos { servicio.balanceHidrico(c.lat, c.lon) } }.getOrNull() }
        val h = horas.await()
        val b = balance.await()
        if (h == null && b == null) null
        else HerramientasClima.calcular(h, b, Clock.System.now().toEpochMilliseconds() / 1000)
    }

    private fun ciudadInicial(): CiudadClima {
        val guardada = preferencias?.ultimaCiudad()
            ?: return CiudadClima("Rosario", -32.9468, -60.6393, 9)
        return CiudadClima(guardada.name, guardada.lat, guardada.lon, preferencias.zoomLocalidad(guardada.name) ?: 9)
    }

    companion object {
        /** Mapa de Windy embebido (mismos parámetros que WindyEmbed en Android: modelo ECMWF, km/h, °C). */
        fun urlWindy(c: CiudadClima, capa: CapaWindy): String {
            val lat = coordenadaUrl(c.lat)
            val lon = coordenadaUrl(c.lon)
            return "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&zoom=${c.zoom}&level=surface&overlay=${capa.id}" +
                "&menu=&message=&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail=" +
                "&detailLat=$lat&detailLon=$lon&metricWind=km%2Fh&metricTemp=%C2%B0C&metricRain=mm&radarRange=-1&product=ecmwf"
        }

        fun urlWindyCompleto(c: CiudadClima, capa: CapaWindy): String =
            "https://www.windy.com/?${capa.id},${coordenadaUrl(c.lat)},${coordenadaUrl(c.lon)},${c.zoom + 1}"
    }
}
