package com.example.data.sync

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AgroDao
import com.example.data.local.entity.LoteEntity

/** Lotes de Room (`lotes_satelitales`) para [SincronizadorLotes]. El id de sincronización es `uuid`. */
class AlmacenLotesRoom(private val db: AppDatabase) : AlmacenLotesLocal {

    private val dao: AgroDao get() = db.agroDao()

    override suspend fun pendientes() = dao.lotesPendientes().map { it.aRegistro() }

    override suspend fun porId(id: String) = dao.lotePorUuid(id)?.aRegistro()

    override suspend fun guardarDesdeRemoto(registro: RegistroLote) = db.withTransaction {
        val existente = dao.lotePorUuid(registro.id)
        val entidad = LoteEntity(
            id = existente?.id ?: 0,
            nombre = registro.nombre,
            geoJson = registro.geojson,
            superficieHa = registro.superficieHa,
            fechaCreacion = registro.creadoEn,
            provincia = registro.provincia,
            uuid = registro.id,
            modificadoEn = registro.modificadoEn,
            pendienteSync = false,
            eliminado = registro.eliminado
        )
        if (existente == null) dao.insertarLote(entidad) else dao.actualizarLote(entidad)
        Unit
    }

    override suspend fun borrar(id: String) = dao.borrarLotePorUuid(id)

    override suspend fun marcarSincronizado(id: String, modificadoEn: Long) = dao.marcarLoteSincronizado(id, modificadoEn)

    override suspend fun purgarEliminadosSincronizados() = dao.purgarLotesEliminados()

    override suspend fun vaciar() = dao.vaciarLotes()

    private fun LoteEntity.aRegistro() = RegistroLote(
        id = uuid,
        nombre = nombre,
        provincia = provincia,
        superficieHa = superficieHa,
        geojson = geoJson,
        creadoEn = fechaCreacion,
        modificadoEn = modificadoEn,
        eliminado = eliminado,
        pendiente = pendienteSync
    )
}
