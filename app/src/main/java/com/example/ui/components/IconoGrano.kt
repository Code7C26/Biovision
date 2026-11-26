package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AppThemeState

/**
 * Granos de la pizarra de Rosario con su ícono (res/drawable/ic_grano_*.xml, vectores de un solo
 * color) y un color propio en variante clara y oscura, para que se distingan y se lean en todos los temas.
 */
enum class Grano(
    val nombre: String,
    @DrawableRes val icono: Int,
    private val colorClaro: Color,
    private val colorOscuro: Color
) {
    SOJA("Soja", R.drawable.ic_grano_soja, Color(0xFF2E7D32), Color(0xFF81C784)),
    MAIZ("Maíz", R.drawable.ic_grano_maiz, Color(0xFFA16207), Color(0xFFFFD54F)),
    TRIGO("Trigo", R.drawable.ic_grano_trigo, Color(0xFF8A6A00), Color(0xFFE6C36A)),
    SORGO("Sorgo", R.drawable.ic_grano_sorgo, Color(0xFF9C3D1A), Color(0xFFFF8A65)),
    GIRASOL("Girasol", R.drawable.ic_grano_girasol, Color(0xFFB45309), Color(0xFFFFB74D));

    /** Se lee en cada composición: cambia con el tema claro/oscuro. */
    val color: Color get() = if (AppThemeState.oscuroEfectivo) colorOscuro else colorClaro

    companion object {
        fun de(nombre: String): Grano? = entries.firstOrNull { it.nombre.equals(nombre.trim(), ignoreCase = true) }
            ?: entries.firstOrNull { it.name.equals(nombre.trim().replace("í", "i"), ignoreCase = true) }
    }
}

/** Ícono del grano dentro de un círculo suave de su color. Decorativo: el nombre siempre va al lado. */
@Composable
fun IconoGrano(grano: Grano?, modifier: Modifier = Modifier, tamano: Dp = 24.dp) {
    if (grano == null) return
    val color = grano.color
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(grano.icono),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(tamano * 0.12f).size(tamano)
        )
    }
}
