package com.example.web.datos

import com.example.data.sync.AlmacenLotesAjustes
import com.example.data.sync.AlmacenLotesLocal
import com.example.data.sync.RegistroLote
import com.example.utils.GeoJsonLote
import com.example.utils.GeometriaLote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Lote listo para mostrar: el registro guardado más sus vértices (lat, lon) ya leídos del GeoJSON. */
data class LoteWeb(val registro: RegistroLote, val vertices: List<Pair<Double, Double>>) {
    val id: String get() = registro.id
    val nombre: String get() = registro.nombre
}

/**
 * Lotes de la web sobre localStorage ([AlmacenLotesAjustes]), observables: cada escritura —del
 * usuario o de la sincronización (que recibe esta misma instancia como [AlmacenLotesLocal])—
 * actualiza [lotes] y [pendientes], así la lista y el mapa siguen solos a lo que bajó de la nube o
 * a lo que borró el cierre de sesión.
 */
class LotesWeb(private val almacen: AlmacenLotesAjustes) : AlmacenLotesLocal {

    private val _lotes = MutableStateFlow(leerActivos())
    /** Lotes visibles (no borrados), los más nuevos primero. Los de GeoJSON ilegible se omiten. */
    val lotes: StateFlow<List<LoteWeb>> = _lotes.asStateFlow()

    private val _pendientes = MutableStateFlow(almacen.cantidadPendientes())
    /** Cambios hechos en este navegador que todavía no se subieron. */
    val pendientes: StateFlow<Int> = _pendientes.asStateFlow()

    private fun leerActivos() = almacen.activos().mapNotNull { r -> GeoJsonLote.leer(r.geojson)?.let { LoteWeb(r, it) } }

    private fun avisar() {
        _lotes.value = leerActivos()
        _pendientes.value = almacen.cantidadPendientes()
    }

    /**
     * Alta ([idExistente] null) o edición de un lote dibujado por el usuario. Queda pendiente de subir;
     * una edición conserva el id (el de Supabase) y la fecha de creación.
     */
    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    fun guardar(idExistente: String?, nombre: String, provincia: String, vertices: List<Pair<Double, Double>>): RegistroLote {
        val anillo = GeometriaLote.anilloAbierto(vertices)
        val ahora = Clock.System.now().toEpochMilliseconds()
        val existente = idExistente?.let { id -> almacen.activos().firstOrNull { it.id == id } }
        val registro = RegistroLote(
            id = existente?.id ?: Uuid.random().toString(),
            nombre = nombre.trim(),
            provincia = provincia.trim(),
            superficieHa = GeometriaLote.areaHectareas(anillo),
            geojson = GeoJsonLote.generar(anillo),
            creadoEn = existente?.creadoEn ?: ahora,
            modificadoEn = ahora
        )
        almacen.guardarCambio(registro)
        avisar()
        return registro
    }

    /** Borrado lógico: viaja a la nube en la próxima sincronización. */
    @OptIn(ExperimentalTime::class)
    fun eliminar(id: String) {
        almacen.marcarEliminado(id, Clock.System.now().toEpochMilliseconds())
        avisar()
    }

    // --- AlmacenLotesLocal (lo usa SincronizadorLotes) ---
    override suspend fun pendientes() = almacen.pendientes()
    override suspend fun porId(id: String) = almacen.porId(id)
    override suspend fun guardarDesdeRemoto(registro: RegistroLote) {
        almacen.guardarDesdeRemoto(registro)
        avisar()
    }
    override suspend fun borrar(id: String) {
        almacen.borrar(id)
        avisar()
    }
    override suspend fun marcarSincronizado(id: String, modificadoEn: Long) {
        almacen.marcarSincronizado(id, modificadoEn)
        avisar()
    }
    override suspend fun purgarEliminadosSincronizados() {
        almacen.purgarEliminadosSincronizados()
        avisar()
    }
    override suspend fun vaciar() {
        almacen.vaciar()
        avisar()
    }
}
