package com.example.web.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.web.plataforma.Navegador
import com.example.web.recursos.Res
import com.example.web.recursos.inter_bold
import com.example.web.recursos.inter_medium
import com.example.web.recursos.inter_regular
import com.example.web.recursos.inter_semibold
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.preloadFont

/*
 * Tema "Verde Campo" de la app Android (app/.../ui/theme/Paletas.kt), copiado para la web.
 * Es temporal: cuando la UI pase a :shared (fase 2 de docs/web/MIGRACION_KMP.md) ambas apps usarán
 * el mismo archivo con los cuatro temas.
 */

/** Colores de marca (mismos roles que ColoresMarca en Android). */
@Immutable
data class MarcaWeb(
    val primario: Color,
    val primarioOscuro: Color,
    val primarioClaro: Color,
    val acento: Color,
    val acentoClaro: Color,
    val info: Color,
    val alerta: Color,
    val positivo: Color,
    val negativo: Color
)

val LocalMarca = staticCompositionLocalOf { MARCA_CLARA }

/** true con el tema oscuro en uso (para los colores con variante clara y oscura, como los granos). */
val LocalTemaOscuro = staticCompositionLocalOf { false }

private val MARCA_CLARA = MarcaWeb(
    primario = Color(0xFF047857),
    primarioOscuro = Color(0xFF064E3B),
    primarioClaro = Color(0xFF10B981),
    acento = Color(0xFFD97706),
    acentoClaro = Color(0xFFFBBF24),
    info = Color(0xFF0284C7),
    alerta = Color(0xFFB45309),
    positivo = Color(0xFF047857),
    negativo = Color(0xFFB91C1C)
)

private val MARCA_OSCURA = MarcaWeb(
    primario = Color(0xFF059669),
    primarioOscuro = Color(0xFF064E3B),
    primarioClaro = Color(0xFF34D399),
    acento = Color(0xFFF59E0B),
    acentoClaro = Color(0xFFFCD34D),
    info = Color(0xFF38BDF8),
    alerta = Color(0xFFFBBF24),
    positivo = Color(0xFF34D399),
    negativo = Color(0xFFF87171)
)

private val ESQUEMA_CLARO = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECFDF5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF7F9F2),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF5EB),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFA7F3D0)
).completar(Color(0xFFD1FAE5), Color(0xFF064E3B))

private val ESQUEMA_OSCURO = darkColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFECFDF5),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color.Black,
    tertiary = Color(0xFF2DD4BF),
    background = Color(0xFF061A14),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF0F2920),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF133B2E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF235846)
).completar(Color(0xFF065F46), Color(0xFFD1FAE5))

/** Igual que en Android: completa los roles que Material 3 deja con su violeta de base. */
private fun ColorScheme.completar(contenedorSecundario: Color, enContenedorSecundario: Color): ColorScheme = copy(
    secondaryContainer = contenedorSecundario,
    onSecondaryContainer = enContenedorSecundario,
    tertiaryContainer = lerp(surface, tertiary, 0.2f),
    onTertiaryContainer = onSurface,
    surfaceTint = primary,
    surfaceBright = surface,
    surfaceDim = lerp(surface, onSurface, 0.06f),
    surfaceContainerLowest = surface,
    surfaceContainerLow = lerp(surface, primary, 0.03f),
    surfaceContainer = lerp(surface, primary, 0.05f),
    surfaceContainerHigh = lerp(surface, primary, 0.08f),
    surfaceContainerHighest = lerp(surface, primary, 0.11f),
    outlineVariant = lerp(surface, outline, 0.55f),
    inverseSurface = onSurface,
    inverseOnSurface = surface,
    inversePrimary = lerp(primary, surface, 0.4f)
)

/** Modo de color elegido por el usuario (se guarda en el navegador). */
enum class ModoTema(val clave: String, val etiqueta: String) {
    SISTEMA("sistema", "Automático"),
    CLARO("claro", "Claro"),
    OSCURO("oscuro", "Oscuro");

    companion object {
        fun deClave(clave: String?): ModoTema = entries.firstOrNull { it.clave == clave } ?: SISTEMA
    }
}

/**
 * Inter (SIL OFL 1.1, licencia en resources/licencias): recortada a latín y con todas sus funciones
 * tipográficas. Las cifras tabulares ("tnum") alinean importes y temperaturas en columnas.
 */
@Composable
private fun familiaInter(): FontFamily {
    val regular = Font(Res.font.inter_regular, FontWeight.Normal)
    val medium = Font(Res.font.inter_medium, FontWeight.Medium)
    val semibold = Font(Res.font.inter_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.inter_bold, FontWeight.Bold)
    return remember(regular, medium, semibold, bold) { FontFamily(regular, medium, semibold, bold) }
}

/**
 * true cuando las cuatro variantes de Inter ya se descargaron. En la web las fuentes de recursos se
 * cargan en segundo plano: GranexWeb mantiene la pantalla de carga hasta entonces, así el primer
 * cuadro no aparece con otra letra y "salta" al cambiar.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun fuentesListas(): Boolean {
    val estados = listOf(
        preloadFont(Res.font.inter_regular, FontWeight.Normal),
        preloadFont(Res.font.inter_medium, FontWeight.Medium),
        preloadFont(Res.font.inter_semibold, FontWeight.SemiBold),
        preloadFont(Res.font.inter_bold, FontWeight.Bold)
    )
    return estados.all { it.value != null }
}

@Composable
private fun tipografiaGranex(): Typography {
    val inter = familiaInter()
    return remember(inter) {
        val base = Typography()
        fun TextStyle.conInter() = copy(fontFamily = inter, fontFeatureSettings = "tnum")
        Typography(
            displayLarge = base.displayLarge.conInter(),
            displayMedium = base.displayMedium.conInter(),
            displaySmall = base.displaySmall.conInter(),
            headlineLarge = base.headlineLarge.conInter(),
            headlineMedium = base.headlineMedium.conInter(),
            headlineSmall = base.headlineSmall.conInter(),
            titleLarge = base.titleLarge.conInter().copy(letterSpacing = (-0.2).sp),
            titleMedium = base.titleMedium.conInter(),
            titleSmall = base.titleSmall.conInter(),
            bodyLarge = base.bodyLarge.conInter(),
            bodyMedium = base.bodyMedium.conInter(),
            bodySmall = base.bodySmall.conInter(),
            labelLarge = base.labelLarge.conInter(),
            labelMedium = base.labelMedium.conInter(),
            labelSmall = base.labelSmall.conInter()
        )
    }
}

/** Tema de la app: [modo] decide claro/oscuro (SISTEMA sigue a prefers-color-scheme). */
@Composable
fun TemaGranexWeb(modo: ModoTema, contenido: @Composable () -> Unit) {
    val oscuro = when (modo) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.OSCURO -> true
    }
    // La barra del sistema (o la ventana de la app instalada) toma el color del encabezado, y los
    // avisos HTML de index.html (versión nueva) siguen el mismo tema que la app
    LaunchedEffect(oscuro) { Navegador.ponerTemaDocumento(oscuro, if (oscuro) "#0F2920" else "#FFFFFF") }
    CompositionLocalProvider(LocalMarca provides if (oscuro) MARCA_OSCURA else MARCA_CLARA, LocalTemaOscuro provides oscuro) {
        MaterialTheme(
            colorScheme = if (oscuro) ESQUEMA_OSCURO else ESQUEMA_CLARO,
            typography = tipografiaGranex(),
            content = contenido
        )
    }
}
