package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.repository.ClasificadorNoticias
import com.example.data.repository.TemaNoticia
import com.example.ui.components.abrirNoticia
import com.example.ui.theme.AgroGoldAccent
import com.example.ui.theme.AgroSkyBlue
import com.example.ui.viewmodel.ResumenNovedades
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.NewsArticle
import com.example.ui.components.ChipEncabezado
import com.example.ui.components.GranexHeader
import com.example.ui.theme.AgroGreenPrimary
import com.example.ui.viewmodel.NovedadesUiState
import com.example.ui.viewmodel.NovedadesViewModel

@Composable
fun NovedadesScreen(
    viewModel: NovedadesViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    /** Rediseño: Noticias se abre desde el Inicio y vuelve a él. */
    onVolver: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val temaSeleccionado by viewModel.tema.collectAsStateWithLifecycle()
    val resumen by viewModel.resumen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colorBarra = AgroGreenPrimary
    val abrir: (NewsArticle) -> Unit = { abrirNoticia(context, it.articleUrl, colorBarra) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar Header
        // Rediseño: "Noticias del campo", con cuántas notas y medios respondieron
        com.example.ui.rediseno.EncabezadoR(
            titulo = "Noticias del campo",
            subtitulo = resumen.takeIf { it.fuentesOk.isNotEmpty() }?.let { r ->
                "${r.total} notas de ${r.fuentesOk.size} de ${r.fuentesOk.size + r.fuentesConError.size} medios"
            },
            onVolver = onVolver
        ) {
            IconButton(onClick = { viewModel.cargarNovedades() }, modifier = Modifier.testTag("btn_refresh_novedades")) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }

        // Buscador de Noticias
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                .testTag("search_input_novedades"),
            placeholder = { Text("Buscar noticias, fuentes o temas") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AgroGreenPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                cursorColor = AgroGreenPrimary
            )
        )

        FiltrosTema(
            seleccionado = temaSeleccionado,
            resumen = resumen,
            onSeleccionar = viewModel::seleccionarTema
        )

        when (val state = uiState) {
            is NovedadesUiState.LoadingSkeleton -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(4) {
                        NovedadSkeletonCard()
                    }
                }
            }

            is NovedadesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "No se pudieron cargar las novedades",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (!state.message.isNullOrBlank()) state.message else "Por favor, verifica tu conexión a internet e inténtalo de nuevo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { viewModel.cargarNovedades() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reintentar", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }

            is NovedadesUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("empty_state_novedades"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val filtrando = searchQuery.isNotEmpty() || temaSeleccionado != null
                        Text(
                            text = if (filtrando) "Sin resultados" else "No hay novedades disponibles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (filtrando)
                                "No hay notas recientes que coincidan con la búsqueda o el tema elegido."
                                else "Los medios no publicaron notas en los últimos 30 días.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (filtrando) {
                                    viewModel.onSearchQueryChanged("")
                                    viewModel.seleccionarTema(null)
                                } else {
                                    viewModel.cargarNovedades()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (filtrando) "Ver todas las noticias" else "Reintentar")
                        }
                    }
                }
            }

            is NovedadesUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.listaNovedades, key = { it.id }) { novedad ->
                        NovedadAgroCard(novedad = novedad, onAbrir = abrir)
                    }
                    item(key = "pie_fuentes") { PieFuentes(resumen) }
                }
            }
        }
    }
}

/** Chips de tema: "Todas" y los tres filtros, con la cantidad de notas de cada uno. */
@Composable
internal fun FiltrosTema(
    seleccionado: TemaNoticia?,
    resumen: ResumenNovedades,
    onSeleccionar: (TemaNoticia?) -> Unit
) {
    val opciones: List<Pair<TemaNoticia?, String>> =
        listOf(null to "Todas") + TemaNoticia.entries.map { it to it.etiqueta }
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag("filtros_tema_novedades"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(opciones, key = { it.first?.name ?: "todas" }) { (tema, etiqueta) ->
            val cantidad = if (tema == null) resumen.total else resumen.porTema[tema] ?: 0
            FilterChip(
                selected = seleccionado == tema,
                onClick = { onSeleccionar(tema) },
                label = {
                    Text(
                        if (resumen.total > 0) "$etiqueta · $cantidad" else etiqueta,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AgroGreenPrimary,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("chip_tema_${tema?.name?.lowercase() ?: "todas"}")
            )
        }
    }
}

/** Ícono y color de la portada de respaldo según la categoría de la nota. */
private fun estiloPortada(categoria: String): Pair<ImageVector, Color> = when (categoria) {
    ClasificadorNoticias.CATEGORIA_MAQUINARIA -> Icons.Default.Agriculture to AgroGreenPrimary
    ClasificadorNoticias.CATEGORIA_INSUMOS -> Icons.Default.Science to AgroGoldAccent
    ClasificadorNoticias.CATEGORIA_ECONOMIA -> Icons.AutoMirrored.Filled.TrendingUp to AgroGoldAccent
    ClasificadorNoticias.CATEGORIA_CLIMA -> Icons.Default.WbSunny to AgroSkyBlue
    ClasificadorNoticias.CATEGORIA_GANADERIA -> Icons.Default.Grass to AgroGreenPrimary
    else -> Icons.Default.Spa to AgroGreenPrimary
}

/**
 * Portada de respaldo: degradé del color de la categoría con su ícono y el nombre del medio. Queda
 * debajo de la foto: se ve mientras carga, si falla o si la nota no tiene imagen, así todas las
 * tarjetas miden lo mismo.
 */
@Composable
internal fun PortadaRespaldo(categoria: String, fuente: String, modifier: Modifier = Modifier) {
    val (icono, color) = estiloPortada(categoria)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.45f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icono, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fuente,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun NovedadAgroCard(novedad: NewsArticle, onAbrir: (NewsArticle) -> Unit) {
    // Rediseño: tarjeta compacta (foto a la izquierda) para ver más notas sin desplazar tanto
    val context = LocalContext.current
    val imagenUrl = novedad.imageUrl?.takeIf { it.isNotBlank() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir la nota en ${novedad.sourceName}", role = Role.Button) { onAbrir(novedad) }
            .testTag("card_novedad_${novedad.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp))) {
                PortadaRespaldo(novedad.category, novedad.sourceName, Modifier.fillMaxSize())
                if (imagenUrl != null) {
                    // Decorativa: el título ya se lee en la tarjeta. Si falla, queda la portada de respaldo
                    AsyncImage(model = imagenUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${novedad.sourceName} · ${novedad.formattedTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = novedad.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                if (novedad.description.isNotBlank()) {
                    Text(
                        text = novedad.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = novedad.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, novedad.title)
                                    putExtra(Intent.EXTRA_TEXT, "${novedad.title}\n\nLeé la nota completa en ${novedad.sourceName}: ${novedad.articleUrl}")
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir noticia"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "No se pudo compartir la noticia", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Al final de la lista: qué medios se consultaron y cuáles no respondieron. */
@Composable
private fun PieFuentes(resumen: ResumenNovedades) {
    if (resumen.fuentesOk.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Medios consultados: " + resumen.fuentesOk.joinToString(", "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (resumen.fuentesConError.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sin respuesta ahora: " + resumen.fuentesConError.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun NovedadSkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(Color.Gray.copy(alpha = alpha)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(Color.Gray.copy(alpha = alpha)) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(Color.Gray.copy(alpha = alpha)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind { drawRect(Color.Gray.copy(alpha = alpha)) }
            )
        }
    }
}
