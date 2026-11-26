package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.supabase.LoteRemoto
import com.example.data.remote.supabase.RemotoLotes
import com.example.data.repository.AgroSatRepository
import com.example.data.sync.AlmacenLotesRoom
import com.example.data.sync.MarcasSincronizacion
import com.example.data.sync.ResultadoSincronizacion
import com.example.data.sync.SincronizadorLotes
import com.example.data.sync.aRemoto
import com.example.data.sync.RegistroLote
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Migración de Room para sincronizar lotes y almacén Room del sincronizador. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SincronizacionLotesAndroidTest {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val nombreBase = "migracion-lotes-test.db"
    private var base: AppDatabase? = null

    @After
    fun cerrar() {
        base?.close()
        contexto.deleteDatabase(nombreBase)
    }

    /** Base en versión 5 con el SQL exacto que generaba Room (AppDatabase_Impl de esa versión). */
    private fun crearBaseVersion5() {
        contexto.deleteDatabase(nombreBase)
        val configuracion = SupportSQLiteOpenHelper.Configuration.builder(contexto)
            .name(nombreBase)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `precios_rosario` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cultivo` TEXT NOT NULL, `fecha` TEXT NOT NULL, `precioUSD` REAL NOT NULL, `precioARS` REAL NOT NULL, `variacionPorcentaje` REAL NOT NULL, `puerto` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `usuarios` (`id` INTEGER NOT NULL, `nombreProductor` TEXT NOT NULL, `establecimiento` TEXT NOT NULL, `ubicacionPrincipal` TEXT NOT NULL, `hectareasTotales` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_cities` (`name` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `dateAdded` INTEGER NOT NULL, PRIMARY KEY(`name`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `lotes_satelitales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL, `geoJson` TEXT NOT NULL, `superficieHa` REAL NOT NULL, `fechaCreacion` INTEGER NOT NULL, `provincia` TEXT NOT NULL DEFAULT '')")
                    db.execSQL("INSERT INTO lotes_satelitales (nombre, geoJson, superficieHa, fechaCreacion, provincia) VALUES ('Lote 4', '$GEOJSON', 71.52, 1761000000000, 'Córdoba')")
                    db.execSQL("INSERT INTO lotes_satelitales (nombre, geoJson, superficieHa, fechaCreacion, provincia) VALUES ('Lote 5', '$GEOJSON', 12.3, 1761100000000, 'Santa Fe')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuracion).writableDatabase.close()
    }

    private fun abrirConRoom(): AppDatabase =
        Room.databaseBuilder(contexto, AppDatabase::class.java, nombreBase)
            .addMigrations(AppDatabase.MIGRACION_4_5, AppDatabase.MIGRACION_5_6)
            .allowMainThreadQueries()
            .build()
            .also { base = it }

    @Test
    fun laMigracion5a6ConservaLosLotesYLosDejaListosParaSubir() = runBlocking {
        crearBaseVersion5()
        // Si la migración no coincide con LoteEntity (columnas, DEFAULT, índice), Room no abre la base
        val dao = abrirConRoom().agroDao()
        val lotes = dao.observarLotes().first()
        assertEquals(listOf("Lote 5", "Lote 4"), lotes.map { it.nombre })
        assertEquals(2, lotes.map { it.uuid }.toSet().size)
        lotes.forEach { lote ->
            assertTrue("UUID válido: ${lote.uuid}", Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(lote.uuid))
            assertEquals(lote.fechaCreacion, lote.modificadoEn)
            assertTrue(lote.pendienteSync)
            assertFalse(lote.eliminado)
        }
        assertEquals(2, dao.lotesPendientes().size)
    }

    /** Remoto en memoria: gana el mayor modificado_en, igual que el trigger de supabase/migrations. */
    private class RemotoEnMemoria : RemotoLotes {
        val filas = mutableMapOf<String, LoteRemoto>()
        private var reloj = 0

        override suspend fun subir(lotes: List<LoteRemoto>) = lotes.forEach { nuevo ->
            val viejo = filas[nuevo.id]
            if (viejo == null || nuevo.modificadoEn >= viejo.modificadoEn) {
                filas[nuevo.id] = nuevo.copy(actualizadoEn = "2026-10-26T12:00:%02dZ".format(reloj++))
            }
        }

        override suspend fun cambiosDesde(desde: String?) =
            filas.values.filter { desde == null || it.actualizadoEn!! >= desde }.sortedBy { it.actualizadoEn }
    }

    @Test
    fun repositorioYSincronizadorSobreRoom() = runBlocking {
        crearBaseVersion5()
        val db = abrirConRoom()
        var cambios = 0
        val repositorio = AgroSatRepository(db.agroDao(), sincronizaConNube = true, alCambiar = { cambios++ })
        val remoto = RemotoEnMemoria()
        val sincronizador = SincronizadorLotes(AlmacenLotesRoom(db), remoto, MarcasSincronizacion(MapSettings()), { "usuario-1" })

        // Dibujar un lote nuevo programa la sincronización
        val nuevo = repositorio.guardarLote(null, "Lote 6", "Buenos Aires", listOf(-33.0 to -61.0, -33.0 to -60.99, -33.01 to -60.99))
        assertEquals(1, cambios)

        // Los dos lotes migrados y el nuevo suben a la cuenta
        val r = sincronizador.sincronizar()
        assertEquals(ResultadoSincronizacion.Lista(subidos = 3, recibidos = 0, borrados = 0), r)
        assertTrue(remoto.filas.values.all { it.usuarioId == "usuario-1" })
        assertTrue(db.agroDao().lotesPendientes().isEmpty())

        // Un lote de otro dispositivo aparece en la lista de la app
        val otro = RegistroLote(
            id = "0b8f2c9e-3a51-4d7e-9c2b-5e6f7a8b9c0d", nombre = "Lote de la web", provincia = "La Pampa",
            superficieHa = 5.0, geojson = GEOJSON, creadoEn = 1_761_200_000_000, modificadoEn = 1_761_200_000_000
        )
        remoto.subir(listOf(otro.aRemoto("usuario-1")))
        sincronizador.sincronizar()
        assertTrue(repositorio.lotes.first().any { it.nombre == "Lote de la web" })

        // Borrar es lógico: desaparece de la app, viaja a la nube y después se purga del teléfono
        repositorio.eliminarLote(nuevo)
        assertEquals(2, cambios)
        assertFalse(repositorio.lotes.first().any { it.nombre == "Lote 6" })
        sincronizador.sincronizar()
        assertTrue(remoto.filas.values.first { it.nombre == "Lote 6" }.eliminado)
        val uuidNuevo = remoto.filas.values.first { it.nombre == "Lote 6" }.id
        assertNull(db.agroDao().lotePorUuid(uuidNuevo))
    }

    @Test
    fun sinBackendElBorradoEsDirecto() = runBlocking {
        crearBaseVersion5()
        val db = abrirConRoom()
        val repositorio = AgroSatRepository(db.agroDao())
        val lote = repositorio.lotes.first().first()
        repositorio.eliminarLote(lote)
        assertEquals(1, repositorio.lotes.first().size)
        assertEquals(1, db.agroDao().lotesPendientes().size) // el que queda, para cuando haya cuenta
    }

    private companion object {
        const val GEOJSON = """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[-61.0,-33.0],[-60.99,-33.0],[-60.99,-33.01],[-61.0,-33.0]]]}}"""
    }
}
