package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FavoriteCityEntity
import com.example.data.local.entity.LoteEntity
import com.example.data.local.entity.PrecioRosarioEntity
import com.example.data.local.entity.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgroDao {

    // --- Favorite Cities ---
    @Query("SELECT * FROM favorite_cities ORDER BY dateAdded DESC")
    fun getFavoriteCities(): Flow<List<FavoriteCityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCity(city: FavoriteCityEntity)

    @Query("DELETE FROM favorite_cities WHERE name = :name")
    suspend fun deleteFavoriteCity(name: String)

    // --- Precios Rosario Queries ---
    @Query("SELECT * FROM precios_rosario WHERE cultivo = :cultivo ORDER BY id ASC")
    fun getPreciosPorCultivo(cultivo: String): Flow<List<PrecioRosarioEntity>>

    @Query("SELECT * FROM precios_rosario WHERE cultivo = :cultivo ORDER BY id ASC")
    suspend fun getPreciosPorCultivoList(cultivo: String): List<PrecioRosarioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreciosRosario(list: List<PrecioRosarioEntity>)

    // --- Usuario ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsuario(usuario: UsuarioEntity)

    // --- Lotes satelitales ---
    // Los borrados lógicos (pendientes de subir a la nube) no se muestran
    @Query("SELECT * FROM lotes_satelitales WHERE eliminado = 0 ORDER BY fechaCreacion DESC")
    fun observarLotes(): Flow<List<LoteEntity>>

    @Query("SELECT * FROM lotes_satelitales WHERE id = :id LIMIT 1")
    suspend fun lotePorId(id: Int): LoteEntity?

    @Query("SELECT * FROM lotes_satelitales WHERE eliminado = 0")
    suspend fun lotesActivos(): List<LoteEntity>

    // --- Sincronización con Supabase (AlmacenLotesRoom) ---
    @Query("SELECT * FROM lotes_satelitales WHERE pendienteSync = 1")
    suspend fun lotesPendientes(): List<LoteEntity>

    /** Cambios de lotes (altas, ediciones, borrados) que todavía no llegaron a la nube. */
    @Query("SELECT COUNT(*) FROM lotes_satelitales WHERE pendienteSync = 1")
    fun contarLotesPendientes(): Flow<Int>

    @Query("SELECT * FROM lotes_satelitales WHERE uuid = :uuid LIMIT 1")
    suspend fun lotePorUuid(uuid: String): LoteEntity?

    /** Solo si no se editó otra vez desde que se subió esa versión. */
    @Query("UPDATE lotes_satelitales SET pendienteSync = 0 WHERE uuid = :uuid AND modificadoEn = :modificadoEn")
    suspend fun marcarLoteSincronizado(uuid: String, modificadoEn: Long)

    @Query("UPDATE lotes_satelitales SET eliminado = 1, pendienteSync = 1, modificadoEn = :ahora WHERE id = :id")
    suspend fun marcarLoteEliminado(id: Int, ahora: Long)

    @Query("DELETE FROM lotes_satelitales WHERE uuid = :uuid")
    suspend fun borrarLotePorUuid(uuid: String)

    @Query("DELETE FROM lotes_satelitales WHERE eliminado = 1 AND pendienteSync = 0")
    suspend fun purgarLotesEliminados()

    @Query("DELETE FROM lotes_satelitales")
    suspend fun vaciarLotes()

    @Insert
    suspend fun insertarLote(lote: LoteEntity): Long

    @Update
    suspend fun actualizarLote(lote: LoteEntity)

    @Query("DELETE FROM lotes_satelitales WHERE id = :id")
    suspend fun eliminarLote(id: Int)
}
