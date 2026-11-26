package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.agenda.Agenda
import com.example.data.agenda.AlmacenAgenda
import com.example.data.agenda.Aplicacion
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.CostosPorLote
import com.example.data.agenda.EstadoPago
import com.example.data.agenda.Gasto
import com.example.data.agenda.InsumoEnStock
import com.example.data.agenda.LoteAgenda
import com.example.data.agenda.ResumenGastos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Filtros de la lista de gastos. [loteId] = [GENERAL] para los gastos sin lote. */
data class FiltroAgenda(
    val categoria: CategoriaGasto? = null,
    val loteId: String? = null,
    val soloAPagar: Boolean = false
) {
    val activo: Boolean get() = categoria != null || loteId != null || soloAPagar

    companion object {
        const val GENERAL = "__general__"
    }
}

data class DiaGastos(val fecha: String, val gastos: List<Gasto>)

data class EstadoAgenda(
    val cargado: Boolean = false,
    val campana: String = "",
    val campanas: List<String> = emptyList(),
    val resumen: ResumenGastos = Agenda.resumen(emptyList()),
    val dias: List<DiaGastos> = emptyList(),
    val stock: List<InsumoEnStock> = emptyList(),
    val aplicaciones: List<Aplicacion> = emptyList(),
    val costos: CostosPorLote = CostosPorLote(emptyList(), 0.0, 0.0),
    val lotes: List<LoteAgenda> = emptyList(),
    val filtro: FiltroAgenda = FiltroAgenda(),
    /** Productos ya cargados con su categoría: el menú de productos los ofrece primero. */
    val productosUsados: List<Pair<CategoriaGasto, String>> = emptyList(),
    /** Hay cambios que todavía no se subieron a la nube. */
    val pendiente: Boolean = false
) {
    val hectareas: Double get() = lotes.sumOf { it.hectareas }
    val usdPorHa: Double? get() = if (hectareas > 0) resumen.totalUsd / hectareas else null
}

/**
 * La Agenda de la campaña: gastos, stock de insumos y costo por lote. Los datos viven en el
 * [AlmacenAgenda] compartido con la web; [lotes] trae los lotes del teléfono por uuid (el id que
 * comparten todos los dispositivos), y el dólar oficial del día pasa a dólares lo pagado en pesos.
 */
class AgendaViewModel(
    private val almacen: AlmacenAgenda,
    lotes: Flow<List<LoteAgenda>>,
    private val dolarOficial: suspend () -> Double?,
    private val hoy: () -> String = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
) : ViewModel() {

    private val campanaElegida = MutableStateFlow<String?>(null)
    private val filtro = MutableStateFlow(FiltroAgenda())
    private val _tipoCambio = MutableStateFlow<Double?>(null)
    /** Dólar oficial (venta) de hoy; null si no se pudo leer. */
    val tipoCambio: StateFlow<Double?> = _tipoCambio.asStateFlow()

    private val datos = combine(almacen.datos, almacen.pendiente) { d, p -> d to p }

    val estado: StateFlow<EstadoAgenda> = combine(datos, lotes, campanaElegida, filtro) { (d, pendiente), lotes, elegida, f ->
        val campanas = Agenda.campanas(d, hoy())
        val campana = elegida?.takeIf { it in campanas } ?: Agenda.campanaDe(hoy())
        val deCampana = Agenda.deCampana(d, campana)
        val filtrados = deCampana.gastos.filter { g ->
            (f.categoria == null || g.categoria == f.categoria) &&
                (f.loteId == null || (if (f.loteId == FiltroAgenda.GENERAL) g.loteId == null else g.loteId == f.loteId)) &&
                (!f.soloAPagar || g.estadoPago == EstadoPago.A_PAGAR)
        }
        EstadoAgenda(
            cargado = true,
            campana = campana,
            campanas = campanas,
            resumen = Agenda.resumen(deCampana.gastos),
            dias = filtrados.sortedWith(compareByDescending<Gasto> { it.fecha }.thenByDescending { it.modificadoEn })
                .groupBy { it.fecha }.map { (fecha, g) -> DiaGastos(fecha, g) },
            stock = Agenda.stock(deCampana),
            aplicaciones = deCampana.aplicaciones.sortedByDescending { it.fecha },
            costos = Agenda.costosPorLote(deCampana.gastos, lotes),
            lotes = lotes,
            filtro = f,
            productosUsados = d.gastos.sortedByDescending { it.modificadoEn }.map { it.categoria to it.producto },
            pendiente = pendiente
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAgenda())

    init {
        viewModelScope.launch { almacen.cargar() }
        actualizarDolar()
    }

    fun actualizarDolar() {
        viewModelScope.launch { runCatching { dolarOficial() }.getOrNull()?.let { _tipoCambio.value = it } }
    }

    fun elegirCampana(c: String) { campanaElegida.value = c }
    fun filtrar(f: FiltroAgenda) { filtro.value = f }

    fun gasto(id: String): Gasto? = almacen.datos.value.gastos.firstOrNull { it.id == id }
    fun nuevoId(): String = UUID.randomUUID().toString()
    fun fechaDeHoy(): String = hoy()

    fun guardar(gasto: Gasto) {
        viewModelScope.launch { almacen.guardarGasto(gasto) }
    }

    fun borrar(id: String) {
        viewModelScope.launch { almacen.borrarGasto(id) }
    }

    fun registrarAplicacion(insumo: InsumoEnStock, cantidad: Double, lote: LoteAgenda?, fecha: String = hoy()) {
        if (cantidad <= 0) return
        viewModelScope.launch {
            almacen.guardarAplicacion(Aplicacion(nuevoId(), fecha, insumo.producto, insumo.unidad, cantidad, lote?.id, lote?.nombre))
        }
    }

    fun borrarAplicacion(id: String) {
        viewModelScope.launch { almacen.borrarAplicacion(id) }
    }
}
