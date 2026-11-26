package com.example.web.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.model.describirCodigoClima
import com.example.data.remote.supabase.PerfilRemoto
import com.example.utils.FechasIso
import com.example.utils.NivelHelada
import com.example.utils.PronosticoAgro
import com.example.web.Seccion
import com.example.web.plataforma.Navegador
import com.example.web.ui.BotonIcono
import com.example.web.ui.ClaseAncho
import com.example.web.ui.EspacioPanel
import com.example.web.ui.LocalAccionesApp
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalConectado
import com.example.web.ui.LocalMarca
import com.example.web.ui.LogoGranex
import com.example.web.ui.MenuApp
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.Tablero
import com.example.web.ui.TarjetaR
import com.example.web.ui.TituloTarjetaR
import com.example.web.ui.ChipEstadoR
import com.example.web.ui.Tono
import com.example.web.ui.colorDe
import com.example.web.ui.colorSecundario
import com.example.web.ui.colores
import com.example.web.ui.desplazableHorizontal
import com.example.web.ui.dolares
import com.example.web.ui.mano
import com.example.web.ui.numero
import com.example.web.ui.pesos
import com.example.web.viewmodel.AgendaWebViewModel
import com.example.web.viewmodel.ClimaWebViewModel
import com.example.web.viewmodel.EstadoCotizaciones
import com.example.web.viewmodel.EstadoNoticias
import com.example.web.viewmodel.EstadoPizarra
import com.example.web.viewmodel.EstadoPronostico
import com.example.web.viewmodel.FinanzasWebViewModel
import com.example.web.viewmodel.GranoWeb
import com.example.web.viewmodel.MercadoWebViewModel
import com.example.web.viewmodel.NovedadesWebViewModel
import com.example.web.viewmodel.SateliteWebViewModel
import com.example.utils.HerramientasAgro
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Inicio, como InicioScreen en Android: lo que el productor mira primero (si hoy puede pulverizar, si
 * llueve o hiela, los precios, sus lotes, la Agenda y las noticias) y lleva a cada sección. No carga
 * nada propio: usa los ViewModels de cada sección. En pantallas anchas va en dos o tres columnas.
 */
@Composable
fun PantallaInicio(
    clima: ClimaWebViewModel,
    finanzas: FinanzasWebViewModel,
    mercado: MercadoWebViewModel,
    satelite: SateliteWebViewModel,
    agenda: AgendaWebViewModel,
    novedades: NovedadesWebViewModel,
    perfil: PerfilRemoto?,
    ir: (Seccion) -> Unit,
    onAnotarGasto: () -> Unit
) {
    val estadoClima by clima.estado.collectAsStateWithLifecycle()
    val ciudad by clima.ciudad.collectAsStateWithLifecycle()
    val agro by clima.agro.collectAsStateWithLifecycle()
    val estadoDolar by finanzas.estado.collectAsStateWithLifecycle()
    val estadoPizarra by mercado.estado.collectAsStateWithLifecycle()
    val lotes by satelite.listaLotes.collectAsStateWithLifecycle()
    val estadoAgenda by agenda.estado.collectAsStateWithLifecycle()
    val estadoNoticias by novedades.estado.collectAsStateWithLifecycle()

    val hoy: @Composable () -> Unit = {
        when (val e = estadoClima) {
            is EstadoPronostico.Ok -> TarjetaHoy(e, ciudad.nombre, agro) { ir(Seccion.CLIMA) }
            is EstadoPronostico.Error -> TarjetaAviso("Clima", "No se pudo cargar el pronóstico.", "Ver clima") { ir(Seccion.CLIMA) }
            EstadoPronostico.Cargando -> PanelEsqueleto("Cargando el clima…", lineas = 4)
        }
    }
    val precios: @Composable () -> Unit = {
        TarjetaPrecios(estadoPizarra, estadoDolar, mercado::cargar) { ir(Seccion.MERCADO) }
    }
    val misLotes: @Composable () -> Unit = {
        SeccionLotes(lotes.map { Triple(it.id, it.nombre, it.registro.superficieHa to it.registro.provincia) }, { ir(Seccion.SATELITE) }) { id ->
            satelite.seleccionarLote(id)
            ir(Seccion.SATELITE)
        }
    }
    val tarjetaAgenda: @Composable () -> Unit = {
        val r = estadoAgenda.resumen
        TarjetaR {
            TituloTarjetaR("Agenda · campaña ${estadoAgenda.campana}", "Abrir", { ir(Seccion.AGENDA) })
            if (r.totalUsd <= 0) {
                Text(
                    "Anotá semillas, insumos y todos los gastos, y mirá cuánto llevás gastado por hectárea en cada lote.",
                    fontSize = 13.sp, color = colorSecundario
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Gastado", fontSize = 12.sp, color = colorSecundario)
                        Text(dolares(r.totalUsd), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        estadoAgenda.usdPorHa?.let { Text("${dolares(it)}/ha", fontSize = 12.sp, color = colorSecundario) }
                    }
                    if (r.facturasAPagar > 0) {
                        val c = colores(Tono.ATENCION)
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.fondo).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("A pagar", fontSize = 12.sp, color = c.texto)
                            Text(dolares(r.aPagarUsd), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.texto)
                            r.proximoVencimiento?.let { Text("vence el ${com.example.data.agenda.Agenda.fechaCorta(it)}", fontSize = 12.sp, color = c.texto) }
                        }
                    }
                }
            }
            if (estadoAgenda.disponible) {
                Button(
                    onClick = onAnotarGasto,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalMarca.current.primario, contentColor = Color.White),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).mano()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Anotar gasto", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    val noticias: @Composable () -> Unit = {
        TarjetaR {
            TituloTarjetaR("Noticias del campo", "Ver todas", { ir(Seccion.NOVEDADES) })
            when (val e = estadoNoticias) {
                is EstadoNoticias.Ok -> e.notas.take(3).forEachIndexed { i, n ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).mano()
                            .clickable(role = Role.Button) { Navegador.abrirEnPestana(n.nota.enlace) }
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(n.nota.fuente, fontSize = 12.sp, color = colorSecundario)
                        Text(n.nota.titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                    }
                }
                EstadoNoticias.Cargando -> PanelEsqueleto("Leyendo los medios…", lineas = 3)
                else -> Text("Las notas de los medios están en Noticias.", fontSize = 13.sp, color = colorSecundario)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Saludo(perfil)
        Tablero(
            when (LocalClaseAncho.current) {
                ClaseAncho.EXPANDIDA -> listOf(listOf(hoy), listOf(precios, misLotes), listOf(tarjetaAgenda, noticias))
                ClaseAncho.MEDIANA -> listOf(listOf(hoy, precios), listOf(misLotes, tarjetaAgenda, noticias))
                ClaseAncho.COMPACTA -> listOf(listOf(hoy, precios, misLotes, tarjetaAgenda, noticias))
            }
        )
    }
}

private val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

/** "Buenas tardes, Juan" con el establecimiento y la fecha, y el círculo con la inicial que abre la cuenta. */
@OptIn(ExperimentalTime::class)
@Composable
private fun Saludo(perfil: PerfilRemoto?) {
    val acciones = LocalAccionesApp.current
    val compacta = LocalClaseAncho.current == ClaseAncho.COMPACTA
    // GRANEX es para Argentina (UTC−3, sin horario de verano): como FechasIso.deArgentina
    val ahoraMs = Clock.System.now().toEpochMilliseconds()
    val hora = ((ahoraMs - 3 * 3_600_000L).mod(86_400_000L) / 3_600_000L).toInt()
    val saludo = when (hora) {
        in 5..11 -> "Buen día"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val fechaIso = FechasIso.deArgentina(ahoraMs)
    val fecha = "${DIAS[FechasIso.diaDeSemana(fechaIso) - 1]} ${fechaIso.substring(8, 10).trimStart('0')} de " +
        MESES[fechaIso.substring(5, 7).toInt() - 1]
    val nombre = perfil?.nombreProductor?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    val establecimiento = perfil?.establecimiento?.takeIf { it.isNotBlank() }
    Row(
        Modifier.fillMaxWidth().padding(start = EspacioPanel, end = if (compacta) 4.dp else 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (compacta) LogoGranex(40)
        Column(Modifier.weight(1f)) {
            Text(
                if (nombre != null) "$saludo, $nombre" else saludo,
                fontSize = if (compacta) 20.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(listOfNotNull(establecimiento, fecha).joinToString(" · "), fontSize = 13.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!LocalConectado.current) ChipEstadoR("Sin conexión", Tono.ALERTA)
        if (acciones.etiquetaCuenta != null) {
            Surface(
                onClick = acciones.abrirCuenta,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp).mano()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (nombre?.firstOrNull() ?: 'G').uppercase(),
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        if (compacta) MenuApp()
    }
}

/** El clima de ahora y las tres respuestas del día: pulverizar, lluvia y helada. */
@Composable
private fun TarjetaHoy(e: EstadoPronostico.Ok, ciudad: String, agro: HerramientasAgro?, onAbrirClima: () -> Unit) {
    val actual = e.datos.current
    val diario = e.datos.daily
    Surface(
        onClick = onAbrirClima,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF064E3B),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().mano()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$ciudad · ahora", fontSize = 13.sp, color = Color(0xFFA7F3D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(actual?.temperatura?.let { "${numero(it)}°" } ?: "—", fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp)
                val max = diario?.maxima?.firstOrNull()
                val min = diario?.minima?.firstOrNull()
                Text(
                    listOfNotNull(
                        actual?.codigo?.let(::describirCodigoClima),
                        if (max != null && min != null) "máx ${numero(max)}° mín ${numero(min)}°" else null
                    ).joinToString(" · "),
                    fontSize = 14.sp, color = Color(0xFFD1FAE5)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fraseSpray(agro)?.let { (t, c) -> FilaDecision(t, c) }
                fraseLluvia(e)?.let { (t, c) -> FilaDecision(t, c) }
                fraseHelada(agro)?.let { (t, c) -> FilaDecision(t, c) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ver todo el clima", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val VERDE_OK = Color(0xFF34D399)
private val AMBAR = Color(0xFFFCD34D)
private val ROJO_SUAVE = Color(0xFFFCA5A5)

private fun fraseSpray(agro: HerramientasAgro?): Pair<AnnotatedString, Color>? {
    val p = agro?.pulverizacion ?: return null
    val hoy = p.ventanas.firstOrNull { it.dia == 0 }
    val manana = p.ventanas.firstOrNull { it.dia == 1 }
    return when {
        hoy != null -> negrita("Se puede pulverizar", " de ${hoy.horaDesde} a ${hoy.horaHasta} h") to VERDE_OK
        manana != null -> negrita("Hoy no conviene pulverizar", "; mañana de ${manana.horaDesde} a ${manana.horaHasta} h") to AMBAR
        else -> negrita("No conviene pulverizar", " en las próximas 48 h") to ROJO_SUAVE
    }
}

private fun fraseLluvia(e: EstadoPronostico.Ok): Pair<AnnotatedString, Color>? {
    val d = e.datos.daily ?: return null
    val lluvias = d.lluvia.take(4)
    if (lluvias.isEmpty()) return null
    val i = lluvias.indexOfFirst { (it ?: 0.0) >= 1.0 }
    if (i < 0) return negrita("Sin lluvias", " en los próximos ${lluvias.size} días") to VERDE_OK
    val cuando = when (i) {
        0 -> "hoy"
        1 -> "mañana"
        else -> "el " + (d.time.getOrNull(i)?.let { DIAS[FechasIso.diaDeSemana(it) - 1].lowercase() } ?: "")
    }
    val mm = lluvias[i] ?: 0.0
    return negrita("Lluvia $cuando:", " ${numero(mm, if (mm >= 10) 0 else 1)} mm") to AMBAR
}

private fun fraseHelada(agro: HerramientasAgro?): Pair<AnnotatedString, Color>? {
    val h = agro?.helada ?: return null
    val cuando = PronosticoAgro.nombreDia(h.diaMinima, h.hoy + h.diaMinima).lowercase()
    val minima = "${numero(h.minimaC, 1)}°"
    return when (h.nivel) {
        NivelHelada.SIN_RIESGO -> negrita("Sin helada", " en los próximos 3 días") to VERDE_OK
        NivelHelada.RIESGO -> negrita("Riesgo de helada:", " mínima $minima $cuando a las ${h.horaMinima} h") to AMBAR
        NivelHelada.HELADA -> negrita("Helada:", " mínima $minima $cuando a las ${h.horaMinima} h") to ROJO_SUAVE
    }
}

private fun negrita(fuerte: String, resto: String) = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fuerte) }
    append(resto)
}

@Composable
private fun FilaDecision(texto: AnnotatedString, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(texto, fontSize = 14.sp)
    }
}

@Composable
private fun TarjetaAviso(titulo: String, texto: String, accion: String, onAccion: () -> Unit) {
    TarjetaR {
        TituloTarjetaR(titulo, accion, onAccion)
        Text(texto, fontSize = 13.sp, color = colorSecundario)
    }
}

@Composable
private fun TarjetaPrecios(pizarra: EstadoPizarra, dolar: EstadoCotizaciones, onReintentar: () -> Unit, onAbrirMercado: () -> Unit) {
    val marca = LocalMarca.current
    TarjetaR {
        val ok = pizarra as? EstadoPizarra.Ok
        val fecha = ok?.pizarra?.fecha?.takeIf { it.length >= 10 }?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" }
        TituloTarjetaR("Precios de hoy", extra = "Pizarra Rosario${fecha?.let { " $it" } ?: ""} · US$/t")
        when (pizarra) {
            is EstadoPizarra.Ok -> {
                val conPrecio = GranoWeb.entries.mapNotNull { g -> pizarra.pizarra.granos[g.clave]?.takeIf { it.usdTn != null }?.let { g to it } }.take(3)
                if (conPrecio.isEmpty()) {
                    Text("La Cámara todavía no publicó los precios de hoy.", fontSize = 13.sp, color = colorSecundario)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    conPrecio.forEach { (g, p) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(g.icono, contentDescription = null, tint = colorDe(g), modifier = Modifier.size(18.dp))
                                Text(g.nombre, fontSize = 13.sp, color = colorSecundario, maxLines = 1)
                            }
                            Text(numero(p.usdTn ?: 0.0), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            p.variacionPct?.let { v ->
                                Text(
                                    (if (v >= 0) "▲ " else "▼ ") + numero(kotlin.math.abs(v), 1) + " %",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (v >= 0) marca.primario else Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
            }
            is EstadoPizarra.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pizarra.mensaje, fontSize = 13.sp, color = colorSecundario, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onReintentar, modifier = Modifier.mano()) { Text("Reintentar", color = marca.primario) }
            }
            EstadoPizarra.Cargando -> Box(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
        val dolares = (dolar as? EstadoCotizaciones.Ok)?.dolares.orEmpty()
        val oficial = dolares.firstOrNull { it.casa == "oficial" }?.venta
        val mep = dolares.firstOrNull { it.casa == "bolsa" }?.venta
        if (oficial != null || mep != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                oficial?.let { Text(negrita("Dólar oficial ", pesos(it)), fontSize = 13.sp) }
                mep?.let { Text("MEP ${pesos(it)}", fontSize = 13.sp) }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).mano().clickable(role = Role.Button, onClick = onAbrirMercado).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ver mercado, futuros y dólar", color = marca.primario, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = marca.primario, modifier = Modifier.size(18.dp))
        }
    }
}

/** Mis lotes: tarjetas en fila (tocar una abre el lote en Campo), o cómo cargar el primero. */
@Composable
private fun SeccionLotes(
    lotes: List<Triple<String, String, Pair<Double, String>>>,
    onAbrirCampo: () -> Unit,
    onAbrirLote: (String) -> Unit
) {
    val marca = LocalMarca.current
    TarjetaR {
        TituloTarjetaR("Mis lotes", "Ver mapa", onAbrirCampo, extra = lotes.takeIf { it.isNotEmpty() }?.let { l ->
            "${l.size} " + (if (l.size == 1) "lote" else "lotes") + " · ${numero(l.sumOf { it.third.first }, 1)} ha"
        })
        if (lotes.isEmpty()) {
            Text("Todavía no cargaste lotes.", fontSize = 14.sp)
            Text("Dibujá tu lote en el mapa de Campo y GRANEX te muestra su vigor con imágenes satelitales.", fontSize = 13.sp, color = colorSecundario)
            Button(
                onClick = onAbrirCampo,
                colors = ButtonDefaults.buttonColors(containerColor = marca.primario, contentColor = Color.White),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.heightIn(min = 48.dp).mano()
            ) { Text("Cargar mi primer lote", fontWeight = FontWeight.SemiBold) }
        } else {
            Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                lotes.forEach { (id, nombre, datos) ->
                    Surface(
                        onClick = { onAbrirLote(id) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.width(170.dp).mano()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${numero(datos.first, 1)} ha" + (datos.second.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                fontSize = 12.sp, color = colorSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("Ver vigor y escaneo", fontSize = 12.sp, color = marca.primario, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Granos / Dólar y futuros: las dos vistas de la pestaña Mercado (como SelectorSeccionMercado en Android). */
@Composable
fun SelectorMercado(enGranos: Boolean, ir: (Seccion) -> Unit) {
    com.example.web.ui.SegmentadoR(
        listOf("Granos", "Dólar y futuros"),
        elegida = if (enGranos) 0 else 1,
        modifier = Modifier.padding(horizontal = EspacioPanel, vertical = 4.dp)
    ) { i -> ir(if (i == 0) Seccion.MERCADO else Seccion.FINANZAS) }
}
