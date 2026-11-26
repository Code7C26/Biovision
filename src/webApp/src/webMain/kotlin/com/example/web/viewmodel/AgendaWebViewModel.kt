package com.example.web.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.agenda.Agenda
import com.example.data.agenda.AlmacenAgenda
import com.example.data.agenda.Aplicacion
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.CostosPorLote
import com.example.data.agenda.DatosAgenda
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.InsumoEnStock
import com.example.data.agenda.LoteAgenda
import com.example.data.agenda.ResumenGastos
import com.example.utils.FechasIso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Filtros de la lista de gastos. [loteId] = [GENERAL] para los gastos sin lote. */
data class FiltroAgendaWeb(
    val categoria: CategoriaGasto? = null,
    val loteId: String? = null,
    val soloAPagar: Boolean = false
) {
    val activo: Boolean get() = categoria != null || loteId != null || soloAPagar

    companion object {
        const val GENERAL = "__general__"
    }
}

data class DiaGastosWeb(val fecha: String, val gastos: List<Gasto>)

data class EstadoAgendaWeb(
    val cargado: Boolean = false,
    /** Sin almacenamiento en el navegador (modo privado estricto): no se puede anotar. */
    val disponible: Boolean = true,
    val campana: String = "",
    val campanas: List<String> = emptyList(),
    val resumen: ResumenGastos = Agenda.resumen(emptyList()),
    val dias: List<DiaGastosWeb> = emptyList(),
    val stock: List<InsumoEnStock> = emptyList(),
    val aplicaciones: List<Aplicacion> = emptyList(),
    val costos: CostosPorLote = CostosPorLote(emptyList(), 0.0, 0.0),
    val lotes: List<LoteAgenda> = emptyList(),
    val filtro: FiltroAgendaWeb = FiltroAgendaWeb(),
    val productosUsados: List<Pair<CategoriaGasto, String>> = emptyList(),
    val pendiente: Boolean = false
) {
    val hectareas: Double get() = lotes.sumOf { it.hectareas }
    val usdPorHa: Double? get() = if (hectareas > 0) resumen.totalUsd / hectareas else null
}

/**
 * La Agenda en la web: la misma lógica que AgendaViewModel de Android (mantenerlos iguales; :shared no
 * tiene ViewModels). Después de cada cambio sincroniza con la cuenta ([sincronizar]), como los lotes.
 */
@OptIn(ExperimentalTime::class)
class AgendaWebViewModel(
    private val almacen: AlmacenAgenda?,
    lotes: Flow<List<LoteAgenda>>,
    private val dolarOficial: suspend () -> Double?,
    private val sincronizar: suspend () -> Unit,
    private val hoy: () -> String = { FechasIso.deArgentina(Clock.System.now().toEpochMilliseconds()) }
) : ViewModel() {

    private val campanaElegida = MutableStateFlow<String?>(null)
    private val filtro = MutableStateFlow(FiltroAgendaWeb())
    private val _tipoCambio = MutableStateFlow<Double?>(null)
    val tipoCambio: StateFlow<Double?> = _tipoCambio.asStateFlow()

    private val datos: Flow<Pair<DatosAgenda, Boolean>> =
        if (almacen == null) flowOf(DatosAgenda() to false) else combine(almacen.datos, almacen.pendiente) { d, p -> d to p }

    val estado: StateFlow<EstadoAgendaWeb> = combine(datos, lotes, campanaElegida, filtro) { (d, pendiente), lotes, elegida, f ->
        val campanas = Agenda.campanas(d, hoy())
        val campana = elegida?.takeIf { it in campanas } ?: Agenda.campanaDe(hoy())
        val deCampana = Agenda.deCampana(d, campana)
        val filtrados = deCampana.gastos.filter { g ->
            (f.categoria == null || g.categoria == f.categoria) &&
                (f.loteId == null || (if (f.loteId == FiltroAgendaWeb.GENERAL) g.loteId == null else g.loteId == f.loteId)) &&
                (!f.soloAPagar || g.estadoPago == EstadoPago.A_PAGAR)
        }
        EstadoAgendaWeb(
            cargado = true,
            disponible = almacen != null,
            campana = campana,
            campanas = campanas,
            resumen = Agenda.resumen(deCampana.gastos),
            dias = filtrados.sortedWith(compareByDescending<Gasto> { it.fecha }.thenByDescending { it.modificadoEn })
                .groupBy { it.fecha }.map { (fecha, g) -> DiaGastosWeb(fecha, g) },
            stock = Agenda.stock(deCampana),
            aplicaciones = deCampana.aplicaciones.sortedByDescending { it.fecha },
            costos = Agenda.costosPorLote(deCampana.gastos, lotes),
            lotes = lotes,
            filtro = f,
            productosUsados = d.gastos.sortedByDescending { it.modificadoEn }.map { it.categoria to it.producto },
            pendiente = pendiente
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoAgendaWeb())

    init {
        viewModelScope.launch {
            almacen?.cargar()
            sincronizar()
        }
        actualizarDolar()
    }

    fun actualizarDolar() {
        viewModelScope.launch { runCatching { dolarOficial() }.getOrNull()?.let { _tipoCambio.value = it } }
    }

    /** Al volver la conexión o iniciar sesión: sube lo anotado y baja lo de otros dispositivos. */
    fun sincronizarAhora() {
        viewModelScope.launch { sincronizar() }
    }

    fun elegirCampana(c: String) { campanaElegida.value = c }
    fun filtrar(f: FiltroAgendaWeb) { filtro.value = f }

    fun gasto(id: String): Gasto? = almacen?.datos?.value?.gastos?.firstOrNull { it.id == id }

    @OptIn(ExperimentalUuidApi::class)
    fun nuevoId(): String = Uuid.random().toString()
    fun fechaDeHoy(): String = hoy()

    fun guardar(gasto: Gasto) = cambiar { almacen?.guardarGasto(gasto) }
    fun borrar(id: String) = cambiar { almacen?.borrarGasto(id) }
    fun borrarAplicacion(id: String) = cambiar { almacen?.borrarAplicacion(id) }

    fun registrarAplicacion(insumo: InsumoEnStock, cantidad: Double, lote: LoteAgenda?) {
        if (cantidad <= 0) return
        cambiar {
            almacen?.guardarAplicacion(Aplicacion(nuevoId(), hoy(), insumo.producto, insumo.unidad, cantidad, lote?.id, lote?.nombre))
        }
    }

    private fun cambiar(accion: suspend () -> Unit) {
        viewModelScope.launch {
            accion()
            sincronizar()
        }
    }
}
