package com.example.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.web.pantallas.PantallaClima
import com.example.web.pantallas.PantallaFinanzas
import com.example.web.pantallas.PantallaInicio
import com.example.web.pantallas.SelectorMercado
import com.example.web.pantallas.PantallaMercado
import com.example.web.pantallas.PantallaNovedades
import com.example.web.pantallas.PantallaAgenda
import com.example.web.viewmodel.AgendaWebViewModel
import kotlinx.coroutines.flow.map
import com.example.web.pantallas.PantallaSatelite
import com.example.web.plataforma.Navegador
import com.example.web.ui.AccionesApp
import com.example.web.ui.AnchoMaximoContenido
import com.example.web.ui.BotonIcono
import com.example.web.ui.ClaseAncho
import com.example.web.ui.DialogoAcercaDe
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalAccionesApp
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalConectado
import com.example.web.ui.LogoGranex
import com.example.web.ui.ModoTema
import com.example.web.ui.TemaGranexWeb
import com.example.web.ui.fuentesListas
import com.example.web.ui.mano
import com.example.data.remote.supabase.EstadoCuenta
import com.example.web.pantallas.PantallaCuenta
import com.example.web.viewmodel.ClimaWebViewModel
import com.example.web.viewmodel.EstadoCotizaciones
import com.example.web.viewmodel.EstadoPizarra
import com.example.web.viewmodel.EstadoPronostico
import com.example.web.viewmodel.CuentaWebViewModel
import com.example.web.viewmodel.EscaneoWebViewModel
import com.example.web.viewmodel.EstacionesWebViewModel
import com.example.web.viewmodel.FinanzasWebViewModel
import com.example.web.viewmodel.NovedadesWebViewModel
import com.example.web.viewmodel.PrescripcionWebViewModel
import com.example.web.viewmodel.EstadoNoticias
import com.example.web.viewmodel.MercadoWebViewModel
import com.example.web.viewmodel.SateliteWebViewModel
import kotlinx.coroutines.launch

/**
 * Secciones con su ruta en el fragmento de la URL. Las [navegable] son las cinco pestañas del rediseño
 * de Android (Inicio · Clima · Campo · Mercado · Agenda). Dólar y futuros (FINANZAS) se abre con el
 * selector de Mercado, Noticias desde el Inicio y Cuenta desde el círculo del Inicio o el menú; cada una
 * deja marcada su [pestana].
 */
enum class Seccion(val ruta: String, val titulo: String, val icono: ImageVector, val navegable: Boolean = true) {
    INICIO("inicio", "Inicio", Icons.Filled.Home),
    CLIMA("clima", "Clima", IconosGranex.Nube),
    SATELITE("satelital", "Campo", IconosGranex.Mapa),
    MERCADO("mercado", "Mercado", IconosGranex.Grafico),
    AGENDA("agenda", "Agenda", IconosGranex.Agenda),
    FINANZAS("finanzas", "Dólar y futuros", IconosGranex.Dolar, navegable = false),
    NOVEDADES("novedades", "Noticias", IconosGranex.Noticia, navegable = false),
    CUENTA("cuenta", "Cuenta", Icons.Filled.AccountCircle, navegable = false);

    /** La pestaña que queda marcada en la barra, el riel o el menú mientras se ve esta sección. */
    val pestana: Seccion
        get() = when (this) {
            FINANZAS -> MERCADO
            NOVEDADES -> INICIO
            else -> this
        }

    companion object {
        /** Las de la barra, el riel y el menú lateral. */
        val navegables: List<Seccion> = entries.filter { it.navegable }

        /** Sin ruta (o desconocida) arranca en el Inicio, como la app Android. */
        fun deRuta(ruta: String): Seccion = entries.firstOrNull { it.ruta == ruta } ?: INICIO
    }
}

/**
 * Raíz de la app web. Como GRANEXMainApp en Android crea los ViewModels (alcance de la página) a
 * partir del contenedor, y adapta la navegación al ancho:
 * - teléfono: barra inferior (igual que la app Android) y menú ⋮ en el encabezado;
 * - tablet: riel lateral con íconos;
 * - escritorio: menú lateral con logo, nombres, tema y "Acerca de", fijo o que se abre al pasar el
 *   mouse (NavegacionEscritorio, en MenuLateral.kt).
 */
@Composable
fun GranexWeb(contenedor: ContenedorWeb) {
    // Claves explícitas: la clave por defecto usa KClass.qualifiedName, que Kotlin/JS no soporta
    val finanzas = viewModel(key = "finanzas") { FinanzasWebViewModel(contenedor.cotizaciones, contenedor.futuros) }
    val clima = viewModel(key = "clima") { ClimaWebViewModel(contenedor.pronostico, contenedor.localidades, contenedor.preferencias) }
    val estaciones = viewModel(key = "estaciones") { EstacionesWebViewModel(contenedor.estaciones) }
    val satelite = viewModel(key = "satelite") {
        SateliteWebViewModel(contenedor.localidades, contenedor.provincias, contenedor.lotes) { contenedor.sincronizarLotes() }
    }
    val escaneo = viewModel(key = "escaneo") { EscaneoWebViewModel(contenedor.sentinel) { contenedor.cuenta.usuarioId() != null } }
    val prescripcion = viewModel(key = "prescripcion") { PrescripcionWebViewModel(contenedor.sentinel, haySesion = { contenedor.cuenta.usuarioId() != null }) }
    val cuenta = viewModel(key = "cuenta") {
        CuentaWebViewModel(
            contenedor.cuenta, contenedor.sincronizador,
            borrarDatosLocales = { contenedor.agenda?.vaciar() },
            antesDeCerrarSesion = contenedor::agendaAntesDeCerrarSesion
        )
    }
    val agenda = viewModel(key = "agenda") {
        AgendaWebViewModel(
            almacen = contenedor.agenda,
            // Por uuid: el id de los lotes que comparten la web, la app Android y la nube
            lotes = (contenedor.lotes?.lotes ?: kotlinx.coroutines.flow.flowOf(emptyList())).map { lista ->
                lista.map { com.example.data.agenda.LoteAgenda(it.id, it.nombre, it.registro.superficieHa) }
            },
            dolarOficial = { contenedor.cotizaciones.dolares().firstOrNull { it.casa == "oficial" }?.venta },
            sincronizar = contenedor::sincronizarAgenda
        )
    }
    val estadoCuenta by cuenta.estado.collectAsStateWithLifecycle()
    val cantidadLotes by (contenedor.lotes?.lotes ?: satelite.listaLotes).collectAsStateWithLifecycle()
    val pendientesLotes by satelite.pendientes.collectAsStateWithLifecycle()
    val mercado = viewModel(key = "mercado") { MercadoWebViewModel(contenedor.pizarra) }
    val novedades = viewModel(key = "novedades") { NovedadesWebViewModel(contenedor.noticias) }

    var seccion by remember { mutableStateOf(Seccion.deRuta(Navegador.rutaActual())) }
    var modoTema by remember { mutableStateOf(contenedor.modoTema) }
    var modoMenu by remember { mutableStateOf(contenedor.modoMenu) }
    var conectado by remember { mutableStateOf(Navegador.conectado()) }
    var acercaDeAbierto by remember { mutableStateOf(false) }
    // Perfil del productor (nombre y establecimiento) para el saludo del Inicio
    var perfil by remember { mutableStateOf<com.example.data.remote.supabase.PerfilRemoto?>(null) }
    // "Anotar gasto" del Inicio: la Agenda abre el formulario de un gasto nuevo al mostrarse
    var anotarAlAbrirAgenda by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    // Como AccesoGranex en Android: al cerrar sesión, borrar la cuenta o pasar a otra, la página se
    // recarga y ningún ViewModel conserva datos de la cuenta anterior. Al ingresar se piden las
    // estaciones (su ViewModel nace antes de la sesión y su primer pedido falla).
    var usuarioAnterior by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(estadoCuenta) {
        when (val e = estadoCuenta) {
            is EstadoCuenta.Conectado -> {
                val antes = usuarioAnterior
                if (antes != null && antes != e.usuarioId) Navegador.recargar()
                else if (antes == null) {
                    estaciones.cargarLista()
                    agenda.sincronizarAhora()
                    perfil = runCatching { contenedor.perfil?.miPerfil() }.getOrNull()
                }
                usuarioAnterior = e.usuarioId
            }
            EstadoCuenta.SinSesion -> if (usuarioAnterior != null) Navegador.recargar()
            else -> {}
        }
    }
    LaunchedEffect(Unit) {
        Navegador.escucharRuta { seccion = Seccion.deRuta(it) }
        Navegador.escucharConexion { ahora ->
            // Al volver la red se recargan los datos: reemplaza lo que vino de la caché sin conexión
            if (ahora && !conectado) {
                finanzas.cargar()
                clima.cargar()
                mercado.cargar()
                // y se sincronizan los lotes editados sin señal (sin cuenta o sin backend no hace nada)
                alcance.launch { contenedor.sincronizarLotes() }
                agenda.sincronizarAhora()
            }
            conectado = ahora
        }
        // Al volver a la pestaña (el teléfono sale del reposo) se recarga lo que había quedado en error
        Navegador.escucharRegreso {
            if (finanzas.estado.value is EstadoCotizaciones.Error) finanzas.cargar()
            if (clima.estado.value is EstadoPronostico.Error) clima.cargar()
            if (mercado.estado.value is EstadoPizarra.Error) mercado.cargar()
            if (novedades.estado.value is EstadoNoticias.Error) novedades.cargar()
        }
        if (Navegador.conectado()) contenedor.sincronizarLotes()
    }
    LaunchedEffect(seccion) { Navegador.ponerTitulo("${seccion.titulo} · GRANEX") }
    val ir: (Seccion) -> Unit = { if (it != seccion) Navegador.irARuta(it.ruta) }
    val acciones = AccionesApp(
        modoTema = modoTema,
        cambiarModoTema = {
            modoTema = it
            contenedor.modoTema = it
        },
        modoMenu = modoMenu,
        cambiarModoMenu = {
            modoMenu = it
            contenedor.modoMenu = it
        },
        abrirAcercaDe = { acercaDeAbierto = true },
        abrirCuenta = { ir(Seccion.CUENTA) },
        etiquetaCuenta = when (val e = estadoCuenta) {
            is EstadoCuenta.Conectado -> e.correo ?: "Mi cuenta"
            EstadoCuenta.SinBackend -> null
            else -> "Ingresar"
        }
    )

    TemaGranexWeb(modoTema) {
        // Hasta que llega la tipografía queda visible la pantalla de carga de index.html
        if (!fuentesListas()) return@TemaGranexWeb
        LaunchedEffect(Unit) { Navegador.ocultarPantallaDeCarga() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val clase = ClaseAncho.de(maxWidth)
                CompositionLocalProvider(
                    LocalClaseAncho provides clase,
                    LocalConectado provides conectado,
                    LocalAccionesApp provides acciones
                ) {
                    // movableContentOf: al cruzar un corte de ancho (achicar la ventana, girar la tablet) la
                    // sección se muda de diseño sin perder su estado (formularios a medio llenar, scroll, mapa)
                    val contenido = remember {
                        movableContentOf {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = AnchoMaximoContenido).fillMaxSize()) {
                                when (seccion) {
                                    Seccion.INICIO -> PantallaInicio(
                                        clima, finanzas, mercado, satelite, agenda, novedades, perfil, ir,
                                        onAnotarGasto = {
                                            anotarAlAbrirAgenda = true
                                            ir(Seccion.AGENDA)
                                        }
                                    )
                                    Seccion.FINANZAS -> PantallaFinanzas(finanzas) { SelectorMercado(enGranos = false, ir) }
                                    Seccion.CLIMA -> PantallaClima(clima, estaciones, contenedor.urlServidor)
                                    Seccion.SATELITE -> PantallaSatelite(satelite, escaneo, prescripcion, estadoCuenta) { ir(Seccion.CUENTA) }
                                    Seccion.MERCADO -> PantallaMercado(mercado) { SelectorMercado(enGranos = true, ir) }
                                    Seccion.NOVEDADES -> PantallaNovedades(novedades) { ir(Seccion.INICIO) }
                                    Seccion.AGENDA -> PantallaAgenda(agenda, nuevoAlAbrir = anotarAlAbrirAgenda) { anotarAlAbrirAgenda = false }
                                    Seccion.CUENTA -> PantallaCuenta(cuenta, cantidadLotes.size, pendientesLotes)
                                }
                            }
                        }
                        }
                    }
                    // Con backend hace falta una cuenta: sin sesión solo se ve la pantalla para ingresar
                    when (estadoCuenta) {
                        EstadoCuenta.Verificando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                        EstadoCuenta.SinSesion -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = AnchoMaximoContenido).fillMaxSize()) {
                                PantallaCuenta(cuenta, 0, 0)
                            }
                        }
                        else -> when (clase) {
                            ClaseAncho.COMPACTA -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f)) { contenido() }
                                BarraInferior(seccion, ir)
                            }
                            ClaseAncho.MEDIANA -> Row(Modifier.fillMaxSize()) {
                                RielLateral(seccion, ir, acciones)
                                Box(Modifier.weight(1f)) { contenido() }
                            }
                            ClaseAncho.EXPANDIDA -> NavegacionEscritorio(seccion, ir, acciones) { contenido() }
                        }
                    }
                    if (acercaDeAbierto) DialogoAcercaDe { acercaDeAbierto = false }
                }
            }
        }
    }
}

@Composable
private fun BarraInferior(actual: Seccion, ir: (Seccion) -> Unit) {
    NavigationBar {
        Seccion.navegables.forEach { s ->
            NavigationBarItem(
                selected = s == actual.pestana,
                onClick = { ir(s) },
                icon = { Icon(s.icono, contentDescription = null) },
                label = { Text(s.titulo, maxLines = 1) },
                modifier = Modifier.mano()
            )
        }
    }
}

@Composable
private fun RielLateral(actual: Seccion, ir: (Seccion) -> Unit, acciones: AccionesApp) {
    NavigationRail(header = { LogoGranex(40) }) {
        Spacer(Modifier.height(12.dp))
        Seccion.navegables.forEach { s ->
            NavigationRailItem(
                selected = s == actual.pestana,
                onClick = { ir(s) },
                icon = { Icon(s.icono, contentDescription = null) },
                label = { Text(s.titulo) },
                modifier = Modifier.mano()
            )
        }
        Spacer(Modifier.weight(1f))
        // Un toque pasa al modo siguiente: Automático → Claro → Oscuro
        val siguiente = ModoTema.entries[(acciones.modoTema.ordinal + 1) % ModoTema.entries.size]
        BotonIcono(IconosGranex.Tema, "Tema: ${acciones.modoTema.etiqueta} (cambiar a ${siguiente.etiqueta})") {
            acciones.cambiarModoTema(siguiente)
        }
        acciones.etiquetaCuenta?.let { BotonIcono(Icons.Filled.AccountCircle, "Cuenta: $it", acciones.abrirCuenta) }
        BotonIcono(Icons.Filled.Info, "Acerca de GRANEX web", acciones.abrirAcercaDe)
        Spacer(Modifier.height(12.dp))
    }
}
