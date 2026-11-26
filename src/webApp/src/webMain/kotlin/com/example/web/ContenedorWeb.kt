package com.example.web

import com.example.data.agenda.AlmacenAgenda
import com.example.data.agenda.PersistenciaAgendaAjustes
import com.example.data.agenda.SincronizadorAgenda
import com.example.data.agenda.comoArchivosAgenda
import com.example.data.remote.supabase.ArchivosLotes
import kotlin.time.Clock
import com.example.data.remote.ktor.ServicioCotizaciones
import com.example.data.remote.ktor.ServicioFuturosA3
import com.example.data.remote.ktor.ServicioNoticiasGranex
import com.example.data.remote.ktor.ServicioLocalidades
import com.example.data.remote.ktor.ServicioPizarra
import com.example.data.remote.ktor.ServicioPronostico
import com.example.data.remote.ktor.ServicioProvincias
import com.example.data.remote.ktor.crearClienteHttp
import com.example.data.remote.supabase.ConfiguracionSupabase
import com.example.data.remote.supabase.RemotoLotesSupabase
import com.example.data.remote.supabase.EstacionesGranex
import com.example.data.remote.supabase.SentinelGranex
import com.example.data.remote.supabase.ServicioCuenta
import com.example.data.remote.supabase.SesionEnAjustes
import com.example.data.remote.supabase.crearClienteSupabase
import com.example.data.repository.PreferenciasUsuario
import com.example.data.sync.AlmacenLotesAjustes
import com.example.data.sync.MarcasSincronizacion
import com.example.data.sync.ResultadoSincronizacion
import com.example.data.sync.SincronizadorLotes
import com.example.web.datos.LotesWeb
import com.example.web.plataforma.crearAjustesNavegador
import com.example.web.ui.ModoMenu
import com.example.web.ui.ModoTema

/** Dependencias de la app web, como AppContainer en Android (sin framework de DI): un solo cliente HTTP. */
@OptIn(kotlin.time.ExperimentalTime::class)
class ContenedorWeb {
    private val http = crearClienteHttp()
    val cotizaciones = ServicioCotizaciones(http)
    val pronostico = ServicioPronostico(http)
    val pizarra = ServicioPizarra(http)
    val localidades = ServicioLocalidades(http)
    val provincias = ServicioProvincias(http)

    /** localStorage; null si el navegador lo bloquea (entonces nada se recuerda entre visitas). */
    private val ajustes = crearAjustesNavegador()

    /** Mismas preferencias que Android, guardadas en localStorage. */
    val preferencias: PreferenciasUsuario? = ajustes?.let(::PreferenciasUsuario)

    // --- Backend Supabase (null si el build no tiene proyecto configurado o el navegador bloquea localStorage) ---
    /** URL y clave pública generadas al compilar (gradle/supabase.gradle.kts → ConfiguracionSupabaseWeb). */
    private val configuracionSupabase =
        ConfiguracionSupabase.desde(ConfiguracionSupabaseWeb.URL, ConfiguracionSupabaseWeb.CLAVE_PUBLICA)

    /** La sesión va a localStorage, como en supabase-js: la protege la CSP estricta de index.html. */
    private val supabase = if (configuracionSupabase != null && ajustes != null) {
        crearClienteSupabase(configuracionSupabase, SesionEnAjustes(ajustes))
    } else null

    val cuenta = ServicioCuenta(supabase)

    /** Perfil del productor (tabla perfiles), para el saludo del Inicio; null sin backend. */
    val perfil: com.example.data.remote.supabase.RepositorioPerfil? = supabase?.let { com.example.data.remote.supabase.RepositorioPerfil(it) }

    /**
     * Función "publico" de GRANEX: ajustes de A3 y noticias, que no responden con CORS. No pide sesión,
     * solo la clave pública (la exige la puerta de entrada de Supabase). null sin backend.
     */
    private val cabecerasPublicas = configuracionSupabase?.let { mapOf("apikey" to it.clavePublica, "Authorization" to "Bearer ${it.clavePublica}") }
    val futuros: ServicioFuturosA3? = configuracionSupabase?.let {
        ServicioFuturosA3(http, "${it.url}/functions/v1/publico/a3", cabecerasPublicas.orEmpty())
    }
    val noticias: ServicioNoticiasGranex? = configuracionSupabase?.let {
        ServicioNoticiasGranex(http, "${it.url}/functions/v1/publico", cabecerasPublicas.orEmpty())
    }

    /** Estaciones meteorológicas propias (Edge Function "estaciones"); null sin backend. */
    val estaciones: EstacionesGranex? = supabase?.let(::EstacionesGranex)

    /** URL del proyecto, para la configuración del envío directo de una estación. */
    val urlServidor: String? = configuracionSupabase?.url

    /** Imágenes Sentinel-2 por la Edge Function de GRANEX (la cuenta de Copernicus queda en el servidor); null sin backend. */
    val sentinel: SentinelGranex? = supabase?.let(::SentinelGranex)

    /**
     * Lotes del usuario en localStorage (también sin cuenta y sin conexión), observables para la lista y
     * el mapa. null si el navegador bloquea el almacenamiento: entonces no se pueden guardar lotes.
     */
    val lotes: LotesWeb? = ajustes?.let { LotesWeb(AlmacenLotesAjustes(it)) }

    /** Sube los cambios locales y baja los de otros dispositivos; null sin backend. Lo usa también la cuenta. */
    val sincronizador: SincronizadorLotes? = if (supabase != null && ajustes != null && lotes != null) {
        SincronizadorLotes(lotes, RemotoLotesSupabase(supabase), MarcasSincronizacion(ajustes), cuenta::usuarioIdCargado)
    } else null

    /** Sube los cambios hechos sin conexión y baja los de otros dispositivos. */
    suspend fun sincronizarLotes(): ResultadoSincronizacion? = sincronizador?.sincronizar()

    /**
     * La Agenda de la campaña: copia local en localStorage (para usarla sin conexión) y la de la cuenta
     * en Storage, la misma que usa la app Android. null si el navegador bloquea el almacenamiento.
     */
    val agenda: AlmacenAgenda? = ajustes?.let {
        AlmacenAgenda(PersistenciaAgendaAjustes(it), ahora = { Clock.System.now().toEpochMilliseconds() })
    }
    private val sincronizadorAgenda: SincronizadorAgenda? = if (supabase != null && agenda != null) {
        SincronizadorAgenda(agenda, ArchivosLotes(supabase).comoArchivosAgenda()) { Clock.System.now().toEpochMilliseconds() }
    } else null

    /** Sube los gastos anotados y baja los de otros dispositivos; sin sesión o sin backend no hace nada. */
    suspend fun sincronizarAgenda() {
        if (cuenta.usuarioId() == null) return
        runCatching { sincronizadorAgenda?.sincronizar() }
    }

    /** Antes de cerrar sesión: null si se puede salir; si no, por qué (gastos sin subir). */
    suspend fun agendaAntesDeCerrarSesion(): String? {
        val almacen = agenda ?: return null
        almacen.cargar()
        if (almacen.pendiente.value) sincronizarAgenda()
        return if (sincronizadorAgenda != null && almacen.pendiente.value) {
            "hay gastos de la Agenda sin subir. Verificá tu conexión a internet e intentá de nuevo."
        } else null
    }

    /**
     * Claro / oscuro / automático. Junto con modoMenu, la sesión de Supabase y la caché de lotes, lo
     * único que la web guarda (nunca credenciales de terceros como las de Copernicus).
     */
    var modoTema: ModoTema
        get() = ModoTema.deClave(ajustes?.getStringOrNull(CLAVE_TEMA))
        set(valor) {
            ajustes?.putString(CLAVE_TEMA, valor.clave)
        }

    /** Menú lateral de escritorio fijo o que se abre al pasar el mouse. */
    var modoMenu: ModoMenu
        get() = ModoMenu.deClave(ajustes?.getStringOrNull(CLAVE_MENU))
        set(valor) {
            ajustes?.putString(CLAVE_MENU, valor.clave)
        }

    private companion object {
        const val CLAVE_TEMA = "web_tema"
        const val CLAVE_MENU = "web_menu"
    }
}
