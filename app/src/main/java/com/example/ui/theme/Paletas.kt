package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Colores de marca que las pantallas usan directamente (AgroGreenPrimary, AgroGoldAccent…).
 * Roles, para elegir tonos con contraste suficiente en cada tema:
 * - [primario]: texto e íconos sobre la superficie, y fondo de botones/chips con contenido blanco.
 * - [primarioOscuro]: extremo oscuro de degradados con texto blanco.
 * - [primarioClaro]: íconos secundarios sobre la superficie.
 * - [acento]: fondo de botones destacados con texto negro, insignias.
 * - [acentoClaro]: íconos sobre fondos de color [primario]/[primarioOscuro].
 * - [info]: íconos informativos (lluvia, heladas) sobre la superficie.
 */
@Immutable
data class ColoresMarca(
    val primario: Color,
    val primarioOscuro: Color,
    val primarioClaro: Color,
    val acento: Color,
    val acentoClaro: Color,
    val info: Color
)

enum class ModoTema { SEGUN_AJUSTE, SIEMPRE_OSCURO, SIEMPRE_CLARO }

enum class TemaGranex(
    val nombre: String,
    val descripcion: String,
    val modo: ModoTema,
    private val esquemaClaro: ColorScheme,
    private val esquemaOscuro: ColorScheme,
    private val marcaClara: ColoresMarca,
    private val marcaOscura: ColoresMarca
) {
    VERDE_CAMPO(
        nombre = "Verde Campo",
        descripcion = "Verdes agrícolas y esmeralda. Tema original de GRANEX.",
        modo = ModoTema.SEGUN_AJUSTE,
        esquemaClaro = lightColorScheme(
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
        ).completar(Color(0xFFD1FAE5), Color(0xFF064E3B)),
        esquemaOscuro = darkColorScheme(
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
        ).completar(Color(0xFF065F46), Color(0xFFD1FAE5)),
        marcaClara = ColoresMarca(
            primario = Color(0xFF047857),
            primarioOscuro = Color(0xFF064E3B),
            primarioClaro = Color(0xFF10B981),
            acento = Color(0xFFD97706),
            acentoClaro = Color(0xFFFBBF24),
            info = Color(0xFF0284C7)
        ),
        marcaOscura = ColoresMarca(
            primario = Color(0xFF059669),
            primarioOscuro = Color(0xFF064E3B),
            primarioClaro = Color(0xFF34D399),
            acento = Color(0xFFF59E0B),
            acentoClaro = Color(0xFFFCD34D),
            info = Color(0xFF38BDF8)
        )
    ),

    TIERRA(
        nombre = "Tierra / Otoño",
        descripcion = "Terracota, ocre y beige: tonos de suelo y grano.",
        modo = ModoTema.SEGUN_AJUSTE,
        esquemaClaro = lightColorScheme(
            primary = Color(0xFFA0522D),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF6E3D3),
            onPrimaryContainer = Color(0xFF5A2A12),
            secondary = Color(0xFFB7791F),
            onSecondary = Color.White,
            tertiary = Color(0xFF6B7A2A),
            background = Color(0xFFF7F1E8),
            onBackground = Color(0xFF2B1F16),
            surface = Color(0xFFFFFDF9),
            onSurface = Color(0xFF2B1F16),
            surfaceVariant = Color(0xFFEFE4D6),
            onSurfaceVariant = Color(0xFF6F5B4B),
            outline = Color(0xFFD8C3A5)
        ).completar(Color(0xFFF3DFC4), Color(0xFF5A2A12)),
        esquemaOscuro = darkColorScheme(
            primary = Color(0xFFB8643A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF5C2E16),
            onPrimaryContainer = Color(0xFFF6E3D3),
            secondary = Color(0xFFE3B25A),
            onSecondary = Color(0xFF2B1F16),
            tertiary = Color(0xFFB5C26A),
            background = Color(0xFF1C1410),
            onBackground = Color(0xFFF3E7DA),
            surface = Color(0xFF271C16),
            onSurface = Color(0xFFF3E7DA),
            surfaceVariant = Color(0xFF3A2A20),
            onSurfaceVariant = Color(0xFFC9B5A2),
            outline = Color(0xFF5E4636)
        ).completar(Color(0xFF5C3A1C), Color(0xFFF6E3D3)),
        marcaClara = ColoresMarca(
            primario = Color(0xFFA0522D),
            primarioOscuro = Color(0xFF5C2E16),
            primarioClaro = Color(0xFFC97B4A),
            acento = Color(0xFFC9861A),
            acentoClaro = Color(0xFFF2C14E),
            info = Color(0xFF3D7EA6)
        ),
        marcaOscura = ColoresMarca(
            primario = Color(0xFFB8643A),
            primarioOscuro = Color(0xFF5C2E16),
            primarioClaro = Color(0xFFE0A07A),
            acento = Color(0xFFE0A43A),
            acentoClaro = Color(0xFFF6D27A),
            info = Color(0xFF7FB3D5)
        )
    ),

    AZUL_TECNICO(
        nombre = "Azul Técnico",
        descripcion = "Azul marino y cian. Siempre oscuro, para uso nocturno.",
        modo = ModoTema.SIEMPRE_OSCURO,
        esquemaClaro = AZUL_OSCURO,
        esquemaOscuro = AZUL_OSCURO,
        marcaClara = MARCA_AZUL,
        marcaOscura = MARCA_AZUL
    ),

    ALTO_CONTRASTE(
        nombre = "Sol Radiante",
        descripcion = "Negro y amarillo, legible a pleno sol. Siempre claro.",
        modo = ModoTema.SIEMPRE_CLARO,
        esquemaClaro = CONTRASTE_CLARO,
        esquemaOscuro = CONTRASTE_CLARO,
        marcaClara = MARCA_CONTRASTE,
        marcaOscura = MARCA_CONTRASTE
    );

    fun usaOscuro(preferenciaOscuro: Boolean): Boolean = when (modo) {
        ModoTema.SEGUN_AJUSTE -> preferenciaOscuro
        ModoTema.SIEMPRE_OSCURO -> true
        ModoTema.SIEMPRE_CLARO -> false
    }

    fun esquema(oscuro: Boolean): ColorScheme = if (oscuro) esquemaOscuro else esquemaClaro

    fun marca(oscuro: Boolean): ColoresMarca = if (oscuro) marcaOscura else marcaClara
}

// Los dos temas de modo fijo comparten esquema en ambos modos
private val AZUL_OSCURO = darkColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0C3550),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF00222A),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0A1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111B2E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2E4263)
).completar(Color(0xFF0C3550), Color(0xFFBAE6FD))

private val MARCA_AZUL = ColoresMarca(
    primario = Color(0xFF0284C7),
    primarioOscuro = Color(0xFF0C2A4A),
    primarioClaro = Color(0xFF38BDF8),
    acento = Color(0xFFF59E0B),
    acentoClaro = Color(0xFFFCD34D),
    info = Color(0xFF22D3EE)
)

private val CONTRASTE_CLARO = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFD600),
    primaryContainer = Color(0xFFFFD600),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFFFD600),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF0047AB),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFFF4B8),
    onSurfaceVariant = Color(0xFF1F1F1F),
    outline = Color(0xFF000000)
).completar(Color(0xFFFFD600), Color(0xFF000000))

private val MARCA_CONTRASTE = ColoresMarca(
    primario = Color(0xFF111111),
    primarioOscuro = Color(0xFF000000),
    primarioClaro = Color(0xFF000000),
    acento = Color(0xFFFFD600),
    acentoClaro = Color(0xFFFFD600),
    info = Color(0xFF0047AB)
)

/**
 * Completa los roles que lightColorScheme/darkColorScheme dejan con los valores violetas de base de
 * Material 3 (chips seleccionados, superficies elevadas, bordes suaves), derivándolos del propio tema.
 */
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
