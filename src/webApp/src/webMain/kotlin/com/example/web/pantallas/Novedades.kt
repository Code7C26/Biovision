package com.example.web.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.web.plataforma.Navegador
import com.example.web.ui.Aviso
import com.example.web.ui.ClaseAncho
import com.example.web.ui.Encabezado
import com.example.web.ui.EspacioPanel
import com.example.web.ui.IconosGranex
import com.example.web.ui.LocalClaseAncho
import com.example.web.ui.LocalMarca
import com.example.web.ui.Panel
import com.example.web.ui.Tablero
import com.example.web.ui.mano
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.FUENTES_NOTICIAS
import com.example.data.repository.TemaNoticia
import com.example.web.ui.BotonIcono
import com.example.web.ui.ErrorConReintento
import com.example.web.ui.Fuente
import com.example.web.ui.Insignia
import com.example.web.ui.PanelEsqueleto
import com.example.web.ui.desplazableHorizontal
import com.example.web.viewmodel.EstadoNoticias
import com.example.web.viewmodel.NotaWeb
import com.example.web.viewmodel.NovedadesWebViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Medios de FUENTES_NOTICIAS (app Android) con la portada de su sección. */
private data class Medio(val nombre: String, val tema: String, val url: String) {
    /** "www.clarin.com/rural/" → "clarin.com/rural" (lo que se muestra debajo del nombre). */
    val direccion: String get() = url.removePrefix("https://").removePrefix("www.").trimEnd('/')
}

private val MEDIOS = listOf(
    Medio("Clarín Rural", "Agro", "https://www.clarin.com/rural/"),
    Medio("La Nación Campo", "Agro", "https://www.lanacion.com.ar/economia/campo/"),
    Medio("La Voz Agro", "Agro", "https://www.lavoz.com.ar/agro/"),
    Medio("Agrofy News", "Agro", "https://news.agrofy.com.ar/"),
    Medio("Agrositio", "Agro", "https://www.agrositio.com.ar/"),
    Medio("Bichos de Campo", "Agro", "https://bichosdecampo.com/"),
    Medio("Infocampo", "Agro", "https://www.infocampo.com.ar/"),
    Medio("El Cronista", "Economía", "https://www.cronista.com/economia-politica/")
)

/**
 * Novedades: las notas de los medios de FUENTES_NOTICIAS, leídas y filtradas por la función "publico"
 * de GRANEX (los RSS no admiten lectura desde el navegador) y clasificadas con los mismos temas que
 * Android. Sin portadas: los sitios de imágenes de los medios no dejan leerlas desde otra página. Sin
 * servidor o si falla, se ofrecen los medios para abrirlos directo; nunca notas de ejemplo.
 */
@Composable
fun PantallaNovedades(vm: NovedadesWebViewModel, onVolver: (() -> Unit)? = null) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val tema by vm.tema.collectAsStateWithLifecycle()
    val ok = estado as? EstadoNoticias.Ok
    val visibles = remember(ok, tema) { ok?.notas.orEmpty().filter { tema == null || tema in it.temas } }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            titulo = "Noticias",
            chips = listOfNotNull("Agro y economía", ok?.let { "${it.notas.size} notas" }, ok?.let { "Actualizado ${it.hora}" }),
            onVolver = onVolver
        ) {
            if (estado !is EstadoNoticias.SinServidor) BotonIcono(Icons.Filled.Refresh, "Actualizar noticias", vm::cargar)
        }
        when (val e = estado) {
            EstadoNoticias.SinServidor -> MediosDirectos(
                "El resumen de noticias lee los RSS oficiales de cada medio por el servidor de GRANEX, porque los " +
                    "navegadores no permiten leerlos desde otra página. Esta versión no tiene servidor configurado: " +
                    "abrí cada medio directamente."
            )
            is EstadoNoticias.Error -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.padding(start = EspacioPanel, end = EspacioPanel, top = EspacioPanel)) { ErrorConReintento(e.mensaje, vm::cargar) }
                MediosDirectos(null)
            }
            EstadoNoticias.Cargando -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(EspacioPanel),
                horizontalArrangement = Arrangement.spacedBy(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel),
                userScrollEnabled = false
            ) { items(6) { PanelEsqueleto("Leyendo los medios…", lineas = 3) } }
            is EstadoNoticias.Ok -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(EspacioPanel),
                horizontalArrangement = Arrangement.spacedBy(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.desplazableHorizontal(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = tema == null, onClick = { vm.elegirTema(null) }, label = { Text("Todas") }, modifier = Modifier.mano())
                        TemaNoticia.entries.forEach { t ->
                            FilterChip(selected = tema == t, onClick = { vm.elegirTema(t) }, label = { Text(t.etiqueta) }, modifier = Modifier.mano())
                        }
                    }
                }
                if (e.fallidas.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Aviso("Sin respuesta en esta lectura: ${e.fallidas.joinToString(", ")}.")
                    }
                }
                if (visibles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Aviso("No hay notas de este tema en la lectura de hoy.") }
                }
                items(visibles, key = { it.nota.enlace }) { TarjetaNota(it) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Fuente("Notas de ${FUENTES_NOTICIAS.size} medios, leídas de sus RSS oficiales. Cada nota abre en el sitio del medio.")
                }
            }
        }
    }
}

/** Color de la etiqueta de categoría (las mismas categorías que las tarjetas de Android). */
@Composable
private fun colorCategoria(categoria: String): Color {
    val marca = LocalMarca.current
    return when (categoria) {
        ClasificadorNoticias.CATEGORIA_MAQUINARIA -> marca.info
        ClasificadorNoticias.CATEGORIA_INSUMOS -> marca.acento
        ClasificadorNoticias.CATEGORIA_CLIMA -> Color(0xFF0EA5E9)
        ClasificadorNoticias.CATEGORIA_GANADERIA -> Color(0xFFC2410C)
        ClasificadorNoticias.CATEGORIA_ECONOMIA -> marca.alerta
        else -> marca.primario
    }
}

/** "hace 5 min", "hace 3 h", "ayer" o la fecha, como la lista de Android. */
@OptIn(ExperimentalTime::class)
private fun haceCuanto(fecha: Long): String? {
    if (fecha <= 0) return null
    val minutos = (Clock.System.now().toEpochMilliseconds() - fecha) / 60_000
    return when {
        minutos < 1 -> "recién"
        minutos < 60 -> "hace $minutos min"
        minutos < 24 * 60 -> "hace ${minutos / 60} h"
        minutos < 48 * 60 -> "ayer"
        else -> {
            val d = Instant.fromEpochMilliseconds(fecha).toLocalDateTime(TimeZone.currentSystemDefault()).date
            "${d.day.toString().padStart(2, '0')}/${d.month.number.toString().padStart(2, '0')}"
        }
    }
}

@Composable
private fun TarjetaNota(n: NotaWeb) {
    val color = colorCategoria(n.categoria)
    val marca = LocalMarca.current
    Card(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).mano()
            .clickable(onClickLabel = "Abrir la nota en ${n.nota.fuente}") { Navegador.abrirEnPestana(n.nota.enlace) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(EspacioPanel), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Insignia(n.categoria, color)
                Text(
                    listOfNotNull(n.nota.fuente, haceCuanto(n.nota.fecha)).joinToString(" · "),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(n.nota.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.nota.resumen.isNotBlank()) {
                Text(
                    n.nota.resumen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Leer en ${n.nota.fuente}", style = MaterialTheme.typography.labelMedium, color = marca.primario, fontWeight = FontWeight.SemiBold)
                Icon(IconosGranex.AbrirAfuera, contentDescription = null, Modifier.size(14.dp), tint = marca.primario)
            }
        }
    }
}

/** Los medios para abrir directo (sin servidor o si la lectura falló). */
@Composable
private fun MediosDirectos(texto: String?) {
    val aviso: @Composable () -> Unit = { texto?.let { Aviso(it) } }
    val paneles = MEDIOS.groupBy { it.tema }.map { (tema, medios) -> @Composable { PanelMedios(tema, medios) } }
    if (LocalClaseAncho.current == ClaseAncho.COMPACTA) {
        Tablero(listOf(listOf(aviso) + paneles))
    } else {
        // Ancho cómodo de lectura: las dos listas lado a lado, sin estirarse de borde a borde
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1080.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) {
                aviso()
                Row(horizontalArrangement = Arrangement.spacedBy(EspacioPanel)) {
                    paneles.forEach { Box(Modifier.weight(1f)) { it() } }
                }
            }
        }
    }
}

@Composable
private fun PanelMedios(tema: String, medios: List<Medio>) {
    Panel(tema, subtitulo = if (tema == "Agro") "Campo, granos y producción" else "Economía y finanzas") {
        Column {
            medios.forEachIndexed { i, medio ->
                if (i > 0) HorizontalDivider()
                FilaMedio(medio)
            }
        }
    }
}

@Composable
private fun FilaMedio(medio: Medio) {
    val marca = LocalMarca.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).mano()
            .clickable(onClickLabel = "Abrir ${medio.nombre} en otra pestaña") { Navegador.abrirEnPestana(medio.url) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Inicial del medio como "avatar" (sin pedir logos a otros sitios)
        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = marca.primario.copy(alpha = 0.12f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(medio.nombre.take(1), fontWeight = FontWeight.Bold, color = marca.primario)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(medio.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(medio.direccion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(
            IconosGranex.AbrirAfuera,
            contentDescription = null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
