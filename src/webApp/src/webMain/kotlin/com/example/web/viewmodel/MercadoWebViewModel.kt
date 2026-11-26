package com.example.web.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.web.ui.IconosGranos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ktor.PizarraHoy
import com.example.data.remote.ktor.ServicioPizarra
import com.example.data.remote.model.CotizacionHistorica
import com.example.utils.Granularidad
import com.example.utils.HistorialPizarra
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.utils.DerechosExportacion
import com.example.utils.EntradaMargen
import com.example.utils.ImpuestosProvinciales
import com.example.utils.PrecioBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EstadoPizarra {
    data object Cargando : EstadoPizarra
    data class Ok(val pizarra: PizarraHoy) : EstadoPizarra
    data class Error(val mensaje: String) : EstadoPizarra
}

/** Serie histórica de cada grano en US$/t, ya convertida con el dólar mayorista de cada rueda. */
sealed interface EstadoHistorial {
    data object Cargando : EstadoHistorial
    data class Ok(val series: Map<GranoWeb, List<CotizacionHistorica>>) : EstadoHistorial
    data class Error(val mensaje: String) : EstadoHistorial
}

/** Rangos del historial (los mismos de Android más "1 año", que granos.ar permite). */
enum class RangoHistorial(val etiqueta: String, val granularidad: Granularidad) {
    SIETE_DIAS("7 días", Granularidad.DIARIA),
    TREINTA_DIAS("30 días", Granularidad.TRES_DIAS),
    TRES_MESES("3 meses", Granularidad.SEMANAL),
    UN_ANIO("1 año", Granularidad.ANUAL)
}

/**
 * Granos de la pizarra de Rosario: clave de granos.ar, nombre, ícono y color propio en tema claro y
 * oscuro (los mismos que Grano/IconoGrano en Android).
 */
enum class GranoWeb(val clave: String, val nombre: String, val icono: ImageVector, val colorClaro: Color, val colorOscuro: Color) {
    SOJA("soja", "Soja", IconosGranos.Soja, Color(0xFF2E7D32), Color(0xFF81C784)),
    MAIZ("maiz", "Maíz", IconosGranos.Maiz, Color(0xFFA16207), Color(0xFFFFD54F)),
    TRIGO("trigo", "Trigo", IconosGranos.Trigo, Color(0xFF8A6A00), Color(0xFFE6C36A)),
    GIRASOL("girasol", "Girasol", IconosGranos.Girasol, Color(0xFFB45309), Color(0xFFFFB74D)),
    SORGO("sorgo", "Sorgo", IconosGranos.Sorgo, Color(0xFF9C3D1A), Color(0xFFFF8A65))
}

/** Campos de la calculadora de margen (versión web simplificada de CalculadoraBottomSheet). */
object CamposMargen {
    const val SUPERFICIE = "superficie"
    const val RINDE = "rinde"
    const val PRECIO = "precio"
    const val COMISION = "comision"
    const val FLETE_CORTO = "fleteCorto"
    const val FLETE_LARGO = "fleteLargo"
    const val DIRECTOS = "directos"
    const val COSECHA = "cosecha"
    const val ARRENDAMIENTO = "arrendamiento"
    const val ESTRUCTURA = "estructura"
    const val IIBB = "iibb"
    const val SELLOS = "sellos"

    fun iniciales(): Map<String, String> {
        val provincia = ImpuestosProvinciales.PROVINCIAS.first()
        return mapOf(
            COMISION to "1,5",
            IIBB to provincia.iibbPct.toString().replace('.', ','),
            SELLOS to provincia.sellosArrendamientoPct.toString().replace('.', ',')
        )
    }
}

/** "1.234,5" o "1234.5" → 1234.5; vacío o inválido → 0. */
fun leerNumero(texto: String?): Double {
    val t = texto?.trim().orEmpty()
    if (t.isEmpty()) return 0.0
    val normalizado = if (',' in t) t.replace(".", "").replace(',', '.') else t
    return normalizado.toDoubleOrNull() ?: 0.0
}

/**
 * Entrada del modelo común [com.example.utils.MargenAgricola] a partir de los campos. El precio es el
 * de la pizarra (DEX ya descontado), salvo que el usuario lo haya escrito.
 */
fun entradaMargen(campos: Map<String, String>, grano: GranoWeb, precioPizarra: Double?, tipoCambio: Double?): EntradaMargen =
    EntradaMargen(
        superficieHa = leerNumero(campos[CamposMargen.SUPERFICIE]),
        rindeTnHa = leerNumero(campos[CamposMargen.RINDE]),
        precioBase = PrecioBase.PIZARRA,
        precioUsdTn = campos[CamposMargen.PRECIO]?.takeIf { it.isNotBlank() }?.let(::leerNumero) ?: (precioPizarra ?: 0.0),
        dexPct = DerechosExportacion.alicuota(grano.clave),
        comisionPct = leerNumero(campos[CamposMargen.COMISION]),
        fleteCortoUsdTn = leerNumero(campos[CamposMargen.FLETE_CORTO]),
        fleteLargoUsdTn = leerNumero(campos[CamposMargen.FLETE_LARGO]),
        costosDirectosUsdHa = leerNumero(campos[CamposMargen.DIRECTOS]),
        cosechaPct = leerNumero(campos[CamposMargen.COSECHA]),
        arrendamientoUsdHa = leerNumero(campos[CamposMargen.ARRENDAMIENTO]),
        estructuraUsdHa = leerNumero(campos[CamposMargen.ESTRUCTURA]),
        iibbPct = leerNumero(campos[CamposMargen.IIBB]),
        sellosPct = leerNumero(campos[CamposMargen.SELLOS]),
        tipoCambio = tipoCambio ?: 0.0
    )

class MercadoWebViewModel(private val servicio: ServicioPizarra) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoPizarra>(EstadoPizarra.Cargando)
    val estado: StateFlow<EstadoPizarra> = _estado.asStateFlow()

    private val _grano = MutableStateFlow(GranoWeb.SOJA)
    val grano: StateFlow<GranoWeb> = _grano.asStateFlow()

    /** Valores escritos, por grano (como valoresCalculadora en Android): cambiar de grano no los pierde. */
    private val _campos = MutableStateFlow(GranoWeb.entries.associateWith { CamposMargen.iniciales() })
    val campos: StateFlow<Map<GranoWeb, Map<String, String>>> = _campos.asStateFlow()

    private val _historial = MutableStateFlow<EstadoHistorial>(EstadoHistorial.Cargando)
    val historial: StateFlow<EstadoHistorial> = _historial.asStateFlow()

    private val _rango = MutableStateFlow(RangoHistorial.TREINTA_DIAS)
    val rango: StateFlow<RangoHistorial> = _rango.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        cargarHistorial()
        viewModelScope.launch {
            _estado.value = EstadoPizarra.Cargando
            _estado.value = try {
                EstadoPizarra.Ok(conReintentos { servicio.hoy() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                EstadoPizarra.Error("No se pudo leer la pizarra de Rosario. Revisá la conexión.")
            }
        }
    }

    /** Pizarra por rueda (granos.ar) y dólar mayorista (argentinadatos), los dos con CORS. */
    private fun cargarHistorial() {
        viewModelScope.launch {
            if (_historial.value !is EstadoHistorial.Ok) _historial.value = EstadoHistorial.Cargando
            _historial.value = try {
                val (ruedas, dolar) = coroutineScope {
                    val r = async { conReintentos { servicio.historico() } }
                    val d = async { conReintentos { servicio.dolarMayorista() } }
                    r.await() to d.await()
                }
                EstadoHistorial.Ok(GranoWeb.entries.associateWith { HistorialPizarra.serie(ruedas, it.clave, dolar) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Si ya había una serie se conserva (un reintento fallido no borra lo que se ve)
                (_historial.value as? EstadoHistorial.Ok)
                    ?: EstadoHistorial.Error("No se pudo leer el historial de la pizarra. Revisá la conexión.")
            }
        }
    }

    fun elegirRango(rango: RangoHistorial) {
        _rango.value = rango
    }

    fun elegirGrano(grano: GranoWeb) {
        _grano.value = grano
    }

    fun cambiarCampo(clave: String, valor: String) {
        _campos.update { todos -> todos + (_grano.value to (todos[_grano.value].orEmpty() + (clave to valor))) }
    }

    fun elegirProvincia(nombre: String) {
        val p = ImpuestosProvinciales.de(nombre)
        cambiarCampo(CamposMargen.IIBB, p.iibbPct.toString().replace('.', ','))
        cambiarCampo(CamposMargen.SELLOS, p.sellosArrendamientoPct.toString().replace('.', ','))
    }

    fun limpiar() {
        _campos.update { it + (_grano.value to CamposMargen.iniciales()) }
    }
}
