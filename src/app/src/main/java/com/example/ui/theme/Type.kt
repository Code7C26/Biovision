package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R

/**
 * Rediseño: la misma tipografía que la web (Inter, licencia SIL OFL en assets/licencias), con números
 * tabulares para que las cifras de precios y tablas queden alineadas. Los caracteres que no trae el
 * subconjunto (▲ ▼) los dibuja la fuente del sistema.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun TextStyle.conInter() = copy(fontFamily = Inter, fontFeatureSettings = "tnum")

val Typography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.conInter(),
        displayMedium = t.displayMedium.conInter(),
        displaySmall = t.displaySmall.conInter(),
        headlineLarge = t.headlineLarge.conInter(),
        headlineMedium = t.headlineMedium.conInter(),
        headlineSmall = t.headlineSmall.conInter(),
        titleLarge = t.titleLarge.conInter(),
        titleMedium = t.titleMedium.conInter(),
        titleSmall = t.titleSmall.conInter(),
        bodyLarge = t.bodyLarge.conInter(),
        bodyMedium = t.bodyMedium.conInter(),
        bodySmall = t.bodySmall.conInter(),
        labelLarge = t.labelLarge.conInter(),
        labelMedium = t.labelMedium.conInter(),
        labelSmall = t.labelSmall.conInter()
    )
}
