package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.data.agenda.CatalogoProductos
import com.example.data.agenda.CategoriaGasto
import com.example.data.agenda.IconoProducto
import com.example.data.agenda.ProductoSugerido
import com.example.data.agenda.TrazosIconos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import com.example.ui.components.Grano
import com.example.ui.components.IconoGrano

/**
 * Campo del producto (o la labor) de un gasto: se escribe libre o se elige del menú de la categoría,
 * que se abre al tocar el campo vacío o la flecha, y se filtra mientras se escribe. El menú no toma el
 * foco, así el teclado sigue abierto. Elegir una opción completa el nombre (y la unidad, en [onElegir]).
 */
@Composable
fun CampoProductoAgenda(
    valor: String,
    categoria: CategoriaGasto,
    usados: List<Pair<CategoriaGasto, String>>,
    onCambio: (String) -> Unit,
    onElegir: (ProductoSugerido) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = remember(categoria, valor, usados) { CatalogoProductos.sugerencias(categoria, valor, usados) }
    // Si lo escrito es un producto del catálogo, el campo muestra su ícono
    val delCatalogo = remember(categoria, valor) {
        CatalogoProductos.porCategoria[categoria].orEmpty()
            .firstOrNull { CatalogoProductos.normalizar(it.nombre) == CatalogoProductos.normalizar(valor) }
    }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {
                onCambio(it)
                abierto = true
            },
            label = { Text(if (categoria == CategoriaGasto.LABOR) "Labor" else "Producto") },
            placeholder = { Text("Escribí o elegí de la lista") },
            leadingIcon = delCatalogo?.let { p -> { IconoProductoAgenda(p.icono, categoria) } },
            trailingIcon = {
                IconButton(onClick = { abierto = !abierto }, modifier = Modifier.testTag("btn_lista_productos")) {
                    Icon(
                        if (abierto) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (abierto) "Cerrar la lista" else "Elegir de la lista"
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("campo_producto")
                .onFocusChanged { if (it.isFocused && valor.isBlank()) abierto = true }
        )
        DropdownMenu(
            expanded = abierto && opciones.isNotEmpty(),
            onDismissRequest = { abierto = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 320.dp).testTag("menu_productos")
        ) {
            opciones.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(o.nombre, style = MaterialTheme.typography.bodyLarge)
                            if (o.icono == IconoProducto.USADO) {
                                Text("Cargado antes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    leadingIcon = { IconoProductoAgenda(o.icono, categoria) },
                    onClick = {
                        onElegir(o)
                        abierto = false
                    },
                    modifier = Modifier.testTag("opcion_producto")
                )
            }
        }
    }
}

/** El ícono de una opción: los granos con su dibujo propio, el resto en un círculo del color de la categoría. */
@Composable
fun IconoProductoAgenda(icono: IconoProducto, categoria: CategoriaGasto) {
    val grano = when (icono) {
        IconoProducto.SOJA -> "Soja"
        IconoProducto.MAIZ -> "Maíz"
        IconoProducto.TRIGO -> "Trigo"
        IconoProducto.GIRASOL -> "Girasol"
        IconoProducto.SORGO -> "Sorgo"
        else -> null
    }?.let { Grano.de(it) }
    if (grano != null) {
        IconoGrano(grano, tamano = 28.dp)
        return
    }
    val color = colorCategoria(categoria)
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(vectorProducto(icono), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

private val dibujosPropios = mutableMapOf<IconoProducto, ImageVector>()

/**
 * El ícono de una opción: la maquinaria de las labores con su dibujo propio (TrazosIconos, el mismo
 * de la web), el resto con íconos de Material.
 */
internal fun vectorProducto(icono: IconoProducto): ImageVector =
    TrazosIconos.de(icono)?.let { trazos ->
        dibujosPropios.getOrPut(icono) {
            ImageVector.Builder(icono.name, 24.dp, 24.dp, 24f, 24f).apply {
                trazos.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
            }.build()
        }
    } ?: vectorDe(icono)

private fun vectorDe(icono: IconoProducto): ImageVector = when (icono) {
    IconoProducto.SOJA, IconoProducto.MAIZ, IconoProducto.TRIGO, IconoProducto.GIRASOL, IconoProducto.SORGO,
    IconoProducto.SEMILLA -> Icons.Default.Spa
    IconoProducto.FERTILIZANTE -> Icons.Default.Science
    IconoProducto.HERBICIDA -> Icons.Default.Grass
    IconoProducto.INSECTICIDA -> Icons.Default.BugReport
    IconoProducto.FUNGICIDA -> Icons.Default.Healing
    IconoProducto.COADYUVANTE -> Icons.Default.Opacity
    IconoProducto.SIEMBRA -> Icons.Default.Agriculture
    IconoProducto.PULVERIZACION -> Icons.Default.WaterDrop
    IconoProducto.COSECHA -> Icons.Default.Inventory2
    IconoProducto.FLETE -> Icons.Default.LocalShipping
    IconoProducto.LABRANZA -> Icons.Default.Landscape
    IconoProducto.SERVICIO -> Icons.Default.Engineering
    IconoProducto.COMBUSTIBLE -> Icons.Default.LocalGasStation
    IconoProducto.LUBRICANTE -> Icons.Default.OilBarrel
    IconoProducto.SEGURO -> Icons.Default.Shield
    IconoProducto.ANALISIS -> Icons.Default.Biotech
    IconoProducto.ARRENDAMIENTO -> Icons.Default.Fence
    IconoProducto.MANO_DE_OBRA -> Icons.Default.Groups
    IconoProducto.REPARACION -> Icons.Default.Build
    IconoProducto.OTRO -> Icons.Default.Receipt
    IconoProducto.USADO -> Icons.Default.History
    // Tienen dibujo propio (vectorProducto); esto es solo por si faltara
    IconoProducto.PULVERIZADORA, IconoProducto.AVION, IconoProducto.COSECHADORA, IconoProducto.EMBOLSADORA,
    IconoProducto.EXTRACTORA, IconoProducto.SEMBRADORA, IconoProducto.FERTILIZADORA, IconoProducto.RASTRA,
    IconoProducto.ROLO, IconoProducto.CAMION, IconoProducto.MONITOREO -> Icons.Default.Agriculture
}
