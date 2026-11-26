package com.example.utils.vra

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cultivos con los valores por defecto de la dosis sugerida: referencias para la región pampeana, a
 * verificar con el agrónomo. Todos se pueden cambiar en la pantalla.
 */
enum class CultivoVra(
    val nombre: String,
    val rindeTipicoTHa: Double,
    /** N disponible (suelo 0–60 cm + fertilizante) por tonelada de grano: método del umbral. */
    val nPorToneladaKg: Double,
    /** Bray-1 por debajo del cual el cultivo responde al fósforo (ppm). */
    val umbralPBrayPpm: Double,
    /** Fósforo que se lleva cada tonelada de grano (kg P/t), para reponer. */
    val extraccionPKgT: Double
) {
    MAIZ("Maíz", 9.0, 17.0, 16.0, 3.0),
    TRIGO("Trigo", 4.5, 30.0, 18.0, 3.7),
    SOJA("Soja", 3.5, 0.0, 12.0, 5.6),
    GIRASOL("Girasol", 2.5, 40.0, 14.0, 5.0),
    SORGO("Sorgo", 7.0, 20.0, 14.0, 2.9)
}

/** Fertilizantes comunes con su grado (N y P₂O₅ en %); "Otro" se completa a mano. */
enum class ProductoFertilizante(val nombre: String, val nPct: Double, val p2o5Pct: Double) {
    UREA("Urea (46-0-0)", 46.0, 0.0),
    UAN("UAN / Solmix (32-0-0)", 32.0, 0.0),
    MAP("Fosfato monoamónico (11-52-0)", 11.0, 52.0),
    DAP("Fosfato diamónico (18-46-0)", 18.0, 46.0),
    SPT("Superfosfato triple (0-46-0)", 0.0, 46.0),
    OTRO("Otro", 0.0, 0.0)
}

enum class EstrategiaFosforo(val nombre: String, val descripcion: String) {
    SUFICIENCIA(
        "Suficiencia",
        "Solo donde el Bray está por debajo del umbral: (umbral − Bray) × kg de P por ppm."
    ),
    REPOSICION(
        "Reposición",
        "Repone en todas las zonas el P que se lleva el grano y corrige además donde el Bray está bajo el umbral."
    )
}

data class ParametrosDosis(
    val cultivo: CultivoVra = CultivoVra.MAIZ,
    /** Rinde objetivo del lote; cada zona lo ajusta por su productividad relativa. */
    val rindeObjetivoTHa: Double = cultivo.rindeTipicoTHa,
    val nPorToneladaKg: Double = cultivo.nPorToneladaKg,
    val umbralPBrayPpm: Double = cultivo.umbralPBrayPpm,
    /** kg de P que suben 1 ppm el Bray (3 a 5 en suelos pampeanos). */
    val kgPPorPpm: Double = 3.0,
    val extraccionPKgT: Double = cultivo.extraccionPKgT,
    val estrategiaP: EstrategiaFosforo = EstrategiaFosforo.SUFICIENCIA,
    val producto: ProductoFertilizante = ProductoFertilizante.UREA,
    val nProductoPct: Double = producto.nPct,
    val p2o5ProductoPct: Double = producto.p2o5Pct
) {
    /** Al cambiar el cultivo vuelven sus valores por defecto. */
    fun conCultivo(c: CultivoVra): ParametrosDosis = copy(
        cultivo = c,
        rindeObjetivoTHa = c.rindeTipicoTHa,
        nPorToneladaKg = c.nPorToneladaKg,
        umbralPBrayPpm = c.umbralPBrayPpm,
        extraccionPKgT = c.extraccionPKgT
    )

    fun conProducto(p: ProductoFertilizante): ParametrosDosis =
        if (p == ProductoFertilizante.OTRO) copy(producto = p) else copy(producto = p, nProductoPct = p.nPct, p2o5ProductoPct = p.p2o5Pct)

    /** Fracción de P elemental del producto (P₂O₅ × 0,4364). */
    val pProducto: Double get() = p2o5ProductoPct / 100 * 0.4364
    val nProducto: Double get() = nProductoPct / 100
}

data class DosisSugeridaZona(
    val zona: Int,
    val rindeObjetivoTHa: Double,
    /** N a aplicar (kg N/ha); null si falta el análisis de nitratos. */
    val nKgHa: Double?,
    /** P a aplicar (kg P/ha); null si falta el Bray (con la estrategia de suficiencia). */
    val pKgHa: Double?,
    /** Dosis del producto elegido (kg/ha); null si falta el dato del nutriente que aporta. */
    val productoKgHa: Int?,
    /** Qué falta para calcularla; null si está completa. */
    val faltante: String?
)

/**
 * Dosis sugerida por zona a partir del análisis de suelo. Puro, probado en DosisSugeridaTest.
 *
 * - Nitrógeno, método del umbral: N a aplicar = rinde objetivo × N por tonelada − N-nitratos 0–60 cm.
 * - Fósforo: por suficiencia, (umbral − Bray) × kg de P por ppm donde el Bray está bajo; con
 *   reposición, además el P que se lleva el grano.
 * - El producto se dosifica por su nutriente principal: el P si tiene fósforo, si no el N.
 * - El rinde objetivo de cada zona es el del lote por su productividad relativa (1,00 = lo típico).
 */
object DosisSugerida {

    fun nitrogeno(p: ParametrosDosis, rindeTHa: Double, nNitratosKgHa: Double?): Double? =
        if (p.nPorToneladaKg <= 0) 0.0 else nNitratosKgHa?.let { max(0.0, rindeTHa * p.nPorToneladaKg - it) }

    fun fosforo(p: ParametrosDosis, rindeTHa: Double, pBrayPpm: Double?): Double? {
        val correccion = pBrayPpm?.let { max(0.0, p.umbralPBrayPpm - it) * p.kgPPorPpm }
        return when (p.estrategiaP) {
            EstrategiaFosforo.SUFICIENCIA -> correccion
            EstrategiaFosforo.REPOSICION -> rindeTHa * p.extraccionPKgT + (correccion ?: 0.0)
        }
    }

    fun calcular(
        p: ParametrosDosis,
        zonas: List<Int>,
        /** Productividad relativa de cada zona; las que faltan cuentan como 1,00. */
        relativos: Map<Int, Double>,
        suelo: (zona: Int) -> MuestraSuelo?
    ): List<DosisSugeridaZona> = zonas.map { zona ->
        val rinde = p.rindeObjetivoTHa * (relativos[zona] ?: 1.0)
        val muestra = suelo(zona)
        val n = nitrogeno(p, rinde, muestra?.nNitratosKgHa)
        val fosforo = fosforo(p, rinde, muestra?.pBrayPpm)
        val porFosforo = p.pProducto > 0
        val necesidad = if (porFosforo) fosforo else n
        val fraccion = if (porFosforo) p.pProducto else p.nProducto
        val faltante = when {
            fraccion <= 0 -> "Cargá el contenido de N y P₂O₅ del producto."
            necesidad == null && porFosforo -> "Falta el P Bray de la zona $zona."
            necesidad == null -> "Faltan los nitratos de la zona $zona."
            else -> null
        }
        DosisSugeridaZona(
            zona = zona,
            rindeObjetivoTHa = rinde,
            nKgHa = n,
            pKgHa = fosforo,
            productoKgHa = if (faltante == null) (necesidad!! / fraccion).roundToInt() else null,
            faltante = faltante
        )
    }
}
