package com.example

import android.app.Application
import android.content.Context
import android.webkit.WebView
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AgroDao
import com.example.data.repository.AgroSatRepository
import com.example.data.repository.FuenteSentinel
import com.example.data.repository.SentinelBackendGranex
import com.example.data.repository.SentinelHubCdse
import com.example.data.repository.FinancialRepository
import com.example.data.repository.NewsRepository
import com.example.data.repository.RofexRepository
import com.example.data.repository.RosarioMarketRepository
import com.example.data.repository.WeatherRepository
import com.example.data.remote.api.ApiKeys
import com.example.data.remote.supabase.RemotoLotesSupabase
import com.example.data.remote.supabase.SentinelGranex
import com.example.data.remote.supabase.ServicioCuenta
import com.example.data.remote.supabase.SesionEnAjustes
import com.example.data.remote.supabase.crearClienteSupabase
import com.example.data.sync.AlmacenLotesRoom
import com.example.data.sync.MarcasSincronizacion
import com.example.data.sync.SincronizacionLotesWorker
import com.example.data.sync.SincronizadorLotes
import com.example.utils.CredentialCipher
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient

class GranexApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Solo en debug: permite inspeccionar los mapas (Windy, GIS) desde chrome://inspect
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        // La sincronización de lotes se programa en MainActivity.onCreate, junto con las alertas: acá
        // correría también en los tests de Robolectric, donde WorkManager no está inicializado
    }
}

/**
 * Dependencias con alcance de proceso. Los repositorios guardan caché y estado en memoria
 * (p. ej. FinancialRepository se comparte entre Finanzas y Mercado), así que debe existir
 * una sola instancia de cada uno, independiente de recreaciones de la Activity.
 */
class AppContainer(context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    val agroDao: AgroDao by lazy { database.agroDao() }

    val rofexRepository by lazy { RofexRepository(context) }
    val financialRepository by lazy { FinancialRepository(context, rofexRepository) }
    val weatherRepository by lazy { WeatherRepository() }

    /** Mapas de rinde, análisis de suelo y parámetros de dosis de cada lote (solo en el teléfono). */
    val almacenAgronomia by lazy {
        com.example.data.repository.AlmacenAgronomia(java.io.File(context.filesDir, "agronomia")).apply {
            // Con cuenta, cada cambio se sube (el worker espera la red y no hace nada sin sesión)
            if (configuracionSupabase != null) alCambiar = { SincronizacionLotesWorker.programar(context) }
        }
    }
    val rosarioMarketRepository by lazy { RosarioMarketRepository(context) }
    val newsRepository by lazy { NewsRepository() }
    // --- Backend Supabase (null sin configurar: la app funciona solo con datos locales) ---
    private val configuracionSupabase = ApiKeys.supabase

    /** Un solo cliente por proceso. La sesión se guarda cifrada con el Android Keystore. */
    val supabase: SupabaseClient? by lazy {
        configuracionSupabase?.let { configuracion ->
            val ajustesSesion = SharedPreferencesSettings(
                context.getSharedPreferences(SesionEnAjustes.ARCHIVO_ANDROID, Context.MODE_PRIVATE)
            )
            crearClienteSupabase(
                configuracion,
                SesionEnAjustes(ajustesSesion, cifrar = CredentialCipher::encrypt, descifrar = CredentialCipher::decrypt)
            )
        }
    }
    val cuenta by lazy { ServicioCuenta(supabase) }
    val sincronizadorLotes: SincronizadorLotes? by lazy {
        supabase?.let { cliente ->
            SincronizadorLotes(
                local = AlmacenLotesRoom(database),
                remoto = RemotoLotesSupabase(cliente),
                marcas = MarcasSincronizacion(
                    SharedPreferencesSettings(context.getSharedPreferences(ARCHIVO_SINCRONIZACION, Context.MODE_PRIVATE))
                ),
                usuarioActual = cuenta::usuarioIdCargado
            )
        }
    }

    val agroSatRepository by lazy {
        AgroSatRepository(
            agroDao,
            sincronizaConNube = configuracionSupabase != null,
            alCambiar = { SincronizacionLotesWorker.programar(context) },
            alEliminar = { id, uuid -> almacenAgronomia.borrarLote(id, uuid) }
        )
    }

    /** Suelo, rinde y dosis de cada lote en la carpeta del usuario en Storage (ZIP por lote). */
    val sincronizadorAgronomia: com.example.data.repository.SincronizadorAgronomia? by lazy {
        supabase?.let { cliente ->
            val archivos = com.example.data.remote.supabase.ArchivosLotes(cliente)
            com.example.data.repository.SincronizadorAgronomia(
                almacen = almacenAgronomia,
                remoto = object : com.example.data.repository.ArchivosRemotos {
                    override suspend fun listar(prefijo: String) = archivos.listar(prefijo)
                    override suspend fun subir(nombre: String, contenido: ByteArray) { archivos.subir(nombre, contenido) }
                    override suspend fun bajar(nombre: String) = archivos.descargar(nombre)
                    override suspend fun borrar(nombre: String) = archivos.borrar(nombre)
                },
                lotes = { agroDao.lotesActivos().associate { AgroSatRepository.PREFIJO_DB + it.id to it.uuid } }
            )
        }
    }
    /**
     * La Agenda de la campaña (gastos, insumos y aplicaciones) en el teléfono. Cada cambio programa la
     * sincronización, que sube una copia a la carpeta del usuario en Storage.
     */
    val almacenAgenda by lazy {
        com.example.data.agenda.AlmacenAgenda(
            com.example.data.repository.PersistenciaAgendaArchivo(java.io.File(context.filesDir, "agenda")),
            ahora = System::currentTimeMillis,
            alCambiar = { if (configuracionSupabase != null) runCatching { SincronizacionLotesWorker.programar(context) } }
        )
    }

    /** Copia de la Agenda en Storage, compartida con la web y los otros teléfonos. Null sin backend. */
    val sincronizadorAgenda: com.example.data.agenda.SincronizadorAgenda? by lazy {
        supabase?.let { cliente ->
            val archivos = com.example.data.remote.supabase.ArchivosLotes(cliente)
            com.example.data.agenda.SincronizadorAgenda(
                almacen = almacenAgenda,
                remoto = object : com.example.data.agenda.ArchivosAgenda {
                    override suspend fun listar(prefijo: String) = archivos.listar(prefijo)
                    override suspend fun subir(nombre: String, contenido: ByteArray) { archivos.subir(nombre, contenido) }
                    override suspend fun bajar(nombre: String) = archivos.descargar(nombre)
                    override suspend fun borrar(nombre: String) = archivos.borrar(nombre)
                },
                ahora = System::currentTimeMillis
            )
        }
    }

    /** Estaciones meteorológicas propias (Edge Function `estaciones`). Null sin backend. */
    val estaciones: com.example.data.remote.supabase.ServicioEstaciones? by lazy {
        supabase?.let { com.example.data.remote.supabase.EstacionesGranex(it) }
    }

    /**
     * Con backend: la Edge Function `sentinel` con la cuenta de Copernicus de GRANEX (hace falta sesión).
     * Sin backend configurado: la cuenta de Copernicus que el usuario carga en Ajustes.
     */
    val fuenteSentinel: FuenteSentinel by lazy {
        supabase?.let { cliente ->
            SentinelBackendGranex(SentinelGranex(cliente), carpetaCampanas = java.io.File(context.cacheDir, "campanas")) {
                cuenta.usuarioId() != null
            }
        }
            ?: SentinelHubCdse(context)
    }

    private companion object {
        const val ARCHIVO_SINCRONIZACION = "granex_sync"
    }
}
