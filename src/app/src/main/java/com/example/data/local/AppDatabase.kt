package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AgroDao
import com.example.data.local.entity.FavoriteCityEntity
import com.example.data.local.entity.LoteEntity
import com.example.data.local.entity.PrecioRosarioEntity
import com.example.data.local.entity.UsuarioEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PrecioRosarioEntity::class,
        UsuarioEntity::class,
        FavoriteCityEntity::class,
        LoteEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun agroDao(): AgroDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migración explícita: con fallbackToDestructiveMigration se perderían ciudades favoritas y lotes
        internal val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lotes_satelitales ADD COLUMN provincia TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Columnas para sincronizar lotes con Supabase. Cada lote existente recibe su UUID (generado
         * acá, uno por fila: SQLite no tiene uno por fila en un UPDATE masivo), toma su fecha de
         * creación como última modificación y queda pendiente de subir a la primera cuenta que ingrese.
         */
        internal val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lotes_satelitales ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lotes_satelitales ADD COLUMN modificadoEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lotes_satelitales ADD COLUMN pendienteSync INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE lotes_satelitales ADD COLUMN eliminado INTEGER NOT NULL DEFAULT 0")
                val ids = mutableListOf<Long>()
                db.query("SELECT id FROM lotes_satelitales").use { cursor ->
                    while (cursor.moveToNext()) ids += cursor.getLong(0)
                }
                ids.forEach { id ->
                    db.execSQL(
                        "UPDATE lotes_satelitales SET uuid = ?, modificadoEn = fechaCreacion WHERE id = ?",
                        arrayOf<Any>(java.util.UUID.randomUUID().toString(), id)
                    )
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lotes_satelitales_uuid` ON `lotes_satelitales` (`uuid`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "granex_agtech_db"
                )
                    .addMigrations(MIGRACION_4_5, MIGRACION_5_6)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    populateInitialData(database.agroDao())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateInitialData(dao: AgroDao) {
            // Seed Rosario Grain Prices (Real historical series in Puerto de Rosario)
            val rosarioSeed = listOf(
                // Soja
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-03-01", precioUSD = 310.0, precioARS = 310000.0, variacionPorcentaje = 0.5),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-04-01", precioUSD = 318.0, precioARS = 318000.0, variacionPorcentaje = 2.5),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-05-01", precioUSD = 325.0, precioARS = 325000.0, variacionPorcentaje = 2.2),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-06-01", precioUSD = 320.0, precioARS = 320000.0, variacionPorcentaje = -1.5),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-01", precioUSD = 332.0, precioARS = 332000.0, variacionPorcentaje = 3.75),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-10", precioUSD = 334.0, precioARS = 334000.0, variacionPorcentaje = 0.6),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-18", precioUSD = 335.5, precioARS = 335500.0, variacionPorcentaje = 0.45),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-22", precioUSD = 337.0, precioARS = 337000.0, variacionPorcentaje = 0.4),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-24", precioUSD = 338.5, precioARS = 338500.0, variacionPorcentaje = 0.44),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-26", precioUSD = 340.0, precioARS = 340000.0, variacionPorcentaje = 0.44),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-27", precioUSD = 341.5, precioARS = 341500.0, variacionPorcentaje = 0.44),
                PrecioRosarioEntity(cultivo = "Soja", fecha = "2026-07-28", precioUSD = 342.0, precioARS = 342000.0, variacionPorcentaje = 0.15),

                // Maíz
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-03-01", precioUSD = 175.0, precioARS = 175000.0, variacionPorcentaje = 0.2),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-04-01", precioUSD = 180.0, precioARS = 180000.0, variacionPorcentaje = 2.8),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-05-01", precioUSD = 185.0, precioARS = 185000.0, variacionPorcentaje = 2.7),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-06-01", precioUSD = 182.0, precioARS = 182000.0, variacionPorcentaje = -1.6),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-01", precioUSD = 190.0, precioARS = 190000.0, variacionPorcentaje = 4.3),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-10", precioUSD = 191.0, precioARS = 191000.0, variacionPorcentaje = 0.5),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-18", precioUSD = 192.5, precioARS = 192500.0, variacionPorcentaje = 0.78),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-22", precioUSD = 193.0, precioARS = 193000.0, variacionPorcentaje = 0.26),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-24", precioUSD = 194.0, precioARS = 194000.0, variacionPorcentaje = 0.5),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-26", precioUSD = 195.5, precioARS = 195500.0, variacionPorcentaje = 0.77),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-27", precioUSD = 196.0, precioARS = 196000.0, variacionPorcentaje = 0.25),
                PrecioRosarioEntity(cultivo = "Maíz", fecha = "2026-07-28", precioUSD = 197.5, precioARS = 197500.0, variacionPorcentaje = 0.76),

                // Trigo
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-03-01", precioUSD = 210.0, precioARS = 210000.0, variacionPorcentaje = -0.5),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-04-01", precioUSD = 215.0, precioARS = 215000.0, variacionPorcentaje = 2.3),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-05-01", precioUSD = 222.0, precioARS = 222000.0, variacionPorcentaje = 3.2),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-06-01", precioUSD = 228.0, precioARS = 228000.0, variacionPorcentaje = 2.7),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-01", precioUSD = 235.0, precioARS = 235000.0, variacionPorcentaje = 3.0),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-10", precioUSD = 236.0, precioARS = 236000.0, variacionPorcentaje = 0.4),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-18", precioUSD = 237.5, precioARS = 237500.0, variacionPorcentaje = 0.63),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-22", precioUSD = 238.0, precioARS = 238000.0, variacionPorcentaje = 0.21),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-24", precioUSD = 239.5, precioARS = 239500.0, variacionPorcentaje = 0.63),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-26", precioUSD = 241.0, precioARS = 241000.0, variacionPorcentaje = 0.62),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-27", precioUSD = 241.5, precioARS = 241500.0, variacionPorcentaje = 0.21),
                PrecioRosarioEntity(cultivo = "Trigo", fecha = "2026-07-28", precioUSD = 242.0, precioARS = 242000.0, variacionPorcentaje = 0.21),

                // Girasol
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-03-01", precioUSD = 340.0, precioARS = 340000.0, variacionPorcentaje = 1.1),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-04-01", precioUSD = 350.0, precioARS = 350000.0, variacionPorcentaje = 2.9),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-05-01", precioUSD = 360.0, precioARS = 360000.0, variacionPorcentaje = 2.8),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-06-01", precioUSD = 358.0, precioARS = 358000.0, variacionPorcentaje = -0.5),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-01", precioUSD = 368.0, precioARS = 368000.0, variacionPorcentaje = 2.7),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-10", precioUSD = 369.0, precioARS = 369000.0, variacionPorcentaje = 0.27),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-18", precioUSD = 371.5, precioARS = 371500.0, variacionPorcentaje = 0.67),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-22", precioUSD = 373.0, precioARS = 373000.0, variacionPorcentaje = 0.4),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-24", precioUSD = 374.0, precioARS = 374000.0, variacionPorcentaje = 0.27),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-26", precioUSD = 375.5, precioARS = 375500.0, variacionPorcentaje = 0.4),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-27", precioUSD = 377.0, precioARS = 377000.0, variacionPorcentaje = 0.4),
                PrecioRosarioEntity(cultivo = "Girasol", fecha = "2026-07-28", precioUSD = 378.0, precioARS = 378000.0, variacionPorcentaje = 0.26)
            )
            dao.insertPreciosRosario(rosarioSeed)

            // Seed Default User
            val userSeed = UsuarioEntity(
                id = 1,
                nombreProductor = "Ing. Agr. Mateo Rossi",
                establecimiento = "Establecimiento Don Pedro",
                ubicacionPrincipal = "Marcos Juárez, Córdoba / Rosario, Santa Fe",
                hectareasTotales = 1450
            )
            dao.insertUsuario(userSeed)

            // Seed Default Favorite Cities
            val defaultFavorites = listOf(
                FavoriteCityEntity(name = "Marcos Juárez", lat = -32.6975, lon = -62.1022),
                FavoriteCityEntity(name = "Rosario", lat = -32.9468, lon = -60.6393),
                FavoriteCityEntity(name = "Pergamino", lat = -33.8902, lon = -60.5736)
            )
            defaultFavorites.forEach { dao.insertFavoriteCity(it) }
        }
    }
}
