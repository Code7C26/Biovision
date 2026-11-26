package com.example.utils

import kotlin.math.max

/**
 * Derechos de exportación (DEX) del grano, Decreto 423/2026 (vigente desde el 5/6/2026; consultado
 * el 22/10/2026). Soja baja 0,25 pp por mes desde enero de 2027: el usuario siempre puede editarlo.
 */
object DerechosExportacion {
    const val FUENTE = "Decreto 423/2026 (vigente desde 05/06/2026)"

    private val ALICUOTAS = mapOf(
        "soja" to 24.0,
        "maiz" to 8.5,
        "sorgo" to 8.5,
        "trigo" to 5.5,
        "girasol" to 4.5
    )

    fun alicuota(cultivo: String): Double =
        ALICUOTAS[cultivo.trim().lowercase().replace("í", "i")] ?: 0.0
}

/**
 * Referencias provinciales para producción primaria. Son valores de partida editables, no una
 * liquidación: [confirmado] = false marca los que no se pudieron verificar en una fuente oficial.
 */
data class ImpuestosProvincia(
    val nombre: String,
    /** Ingresos Brutos sobre la venta de la producción primaria (%). */
    val iibbPct: Double,
    /** Sellos sobre el monto del contrato de arrendamiento rural (%). */
    val sellosArrendamientoPct: Double,
    val nota: String,
    val confirmado: Boolean
)

object ImpuestosProvinciales {
    val PROVINCIAS = listOf(
        ImpuestosProvincia("Córdoba", 0.0, 0.75,
            "IIBB: exenta la producción primaria con explotación en la provincia. Sellos 0,75 % (Ley Impositiva 2026).", true),
        ImpuestosProvincia("Santa Fe", 0.0, 1.2,
            "IIBB: exenta la producción primaria santafesina. Sellos: verificá la alícuota vigente con API.", false),
        ImpuestosProvincia("Buenos Aires", 0.75, 1.2,
            "IIBB: exenciones con topes de facturación; verificá tu situación con ARBA. Sellos 1,2 % (1,5 % si el arrendatario es una empresa).", false),
        ImpuestosProvincia("Entre Ríos", 0.0, 1.0,
            "IIBB: exenta hasta \$1.040 millones de facturación anual, 0,75 % por encima. Sellos: verificá con ATER.", false),
        ImpuestosProvincia("La Pampa", 0.0, 0.5,
            "IIBB: exenta la actividad primaria. Sellos 0,5 % según la ley impositiva 2026; verificá con la DGR.", false),
        ImpuestosProvincia("Otra / personalizada", 0.0, 0.0, "Cargá las alícuotas de tu jurisdicción.", false)
    )

    fun de(nombre: String) = PROVINCIAS.firstOrNull { it.nombre == nombre } ?: PROVINCIAS.last()
}

/** Sobre qué precio se calcula: la pizarra ya trae descontado el DEX; el FOB todavía no. */
enum class PrecioBase { PIZARRA, FOB }

/** Datos de un cultivo en una campaña. Montos en US$ salvo que el nombre diga Ars. */
data class EntradaMargen(
    val superficieHa: Double,
    val rindeTnHa: Double,
    val precioBase: PrecioBase,
    val precioUsdTn: Double,
    val dexPct: Double,
    /** Solo con precio FOB: gastos de embarque (elevación, puerto) para llegar al FAS. */
    val fobbingUsdTn: Double = 0.0,
    // Comercialización
    val comisionPct: Double,
    val secadoUsdTn: Double = 0.0,
    val zarandaUsdTn: Double = 0.0,
    val fumigacionUsdTn: Double = 0.0,
    val almacenajeUsdTn: Double = 0.0,
    // Fletes
    val fleteCortoUsdTn: Double = 0.0,
    val fleteLargoUsdTn: Double = 0.0,
    // Producción
    val costosDirectosUsdHa: Double = 0.0,
    val cosechaPct: Double = 0.0,
    // Tierra, estructura e impuestos locales
    val arrendamientoUsdHa: Double = 0.0,
    val estructuraUsdHa: Double = 0.0,
    val iibbPct: Double = 0.0,
    val sellosPct: Double = 0.0,
    /** Parte de los sellos del arrendamiento que paga el productor (suele ser 50 %). */
    val sellosAcargoPct: Double = 50.0,
    val tasaVialArsHa: Double = 0.0,
    val tipoCambio: Double
)

/** Resultado por hectárea (US$) y por tonelada, más la composición del ingreso bruto. */
data class ResultadoMargen(
    // Por tonelada
    val dexUsdTn: Double,
    val precioFasUsdTn: Double,
    val comisionUsdTn: Double,
    val acondicionamientoUsdTn: Double,
    val fletesUsdTn: Double,
    val precioChacraUsdTn: Double,
    /** Con precio pizarra: DEX que ya viene descontado en el precio (informativo, no se resta de nuevo). */
    val dexImplicitoUsdTn: Double,
    // Por hectárea
    val ingresoBrutoUsdHa: Double,
    val ingresoNetoUsdHa: Double,
    val costosProduccionUsdHa: Double,
    val margenBrutoUsdHa: Double,
    val impuestosLocalesUsdHa: Double,
    val arrendamientoUsdHa: Double,
    val estructuraUsdHa: Double,
    val margenNetoUsdHa: Double,
    // Rindes de indiferencia (tn/ha); null si cada tonelada no deja margen
    val rindeIndiferenciaDirectosTnHa: Double?,
    val rindeIndiferenciaTotalTnHa: Double?,
    val composicion: List<Porcion>,
    val tipoCambio: Double,
    val superficieHa: Double
) {
    val margenBrutoArsHa get() = margenBrutoUsdHa * tipoCambio
    val margenNetoArsHa get() = margenNetoUsdHa * tipoCambio
    val margenNetoTotalUsd get() = margenNetoUsdHa * superficieHa
    val margenBrutoTotalUsd get() = margenBrutoUsdHa * superficieHa
}

enum class RubroCosto(val etiqueta: String) {
    PRODUCCION("Costos de producción"),
    FLETE("Fletes"),
    COMERCIALIZACION("Comercialización y acondicionamiento"),
    IMPUESTOS("Impuestos y tasas"),
    ARRENDAMIENTO("Arrendamiento y estructura"),
    MARGEN("Margen neto")
}

/** Parte del ingreso bruto: [pct] puede ser negativo en MARGEN (pérdida). */
data class Porcion(val rubro: RubroCosto, val usdHa: Double, val pct: Double)

/**
 * Margen agrícola por hectárea. Fórmulas (por tonelada salvo indicación):
 *
 * - DEX = FOB × dex% (solo con precio FOB; la pizarra ya lo trae descontado)
 * - FAS = FOB − DEX − fobbing, o la pizarra tal cual
 * - Precio en chacra = FAS − comisión% × FAS − (secado + zaranda + fumigación + almacenaje) − flete corto − flete largo
 * - Ingreso bruto/ha = rinde × precio base; ingreso neto/ha = rinde × precio en chacra
 * - Costos de producción/ha = insumos y labores + cosecha% × (rinde × FAS)
 * - Margen bruto/ha = ingreso neto − costos de producción
 * - Impuestos locales/ha = IIBB% × (rinde × FAS) + sellos% × arrendamiento × parte a cargo + tasa vial
 * - Margen neto/ha = margen bruto − impuestos locales − arrendamiento − estructura
 * - Rinde de indiferencia = costos fijos/ha ÷ aporte por tonelada, donde
 *   aporte = precio en chacra − (cosecha% + IIBB%) × FAS
 */
object MargenAgricola {

    fun calcular(e: EntradaMargen): ResultadoMargen {
        val rinde = max(e.rindeTnHa, 0.0)
        val tc = if (e.tipoCambio > 0) e.tipoCambio else 1.0

        val dex = if (e.precioBase == PrecioBase.FOB) e.precioUsdTn * e.dexPct / 100 else 0.0
        val fas = if (e.precioBase == PrecioBase.FOB) e.precioUsdTn - dex - e.fobbingUsdTn else e.precioUsdTn
        val dexImplicito = if (e.precioBase == PrecioBase.PIZARRA && e.dexPct in 0.0..<100.0)
            e.precioUsdTn * e.dexPct / (100 - e.dexPct) else 0.0

        val comision = fas * e.comisionPct / 100
        val acondicionamiento = e.secadoUsdTn + e.zarandaUsdTn + e.fumigacionUsdTn + e.almacenajeUsdTn
        val fletes = e.fleteCortoUsdTn + e.fleteLargoUsdTn
        val chacra = fas - comision - acondicionamiento - fletes

        val ingresoBruto = rinde * e.precioUsdTn
        val ventaFas = rinde * fas
        val ingresoNeto = rinde * chacra
        val cosecha = ventaFas * e.cosechaPct / 100
        val produccion = e.costosDirectosUsdHa + cosecha
        val margenBruto = ingresoNeto - produccion

        val iibb = ventaFas * e.iibbPct / 100
        val sellos = e.arrendamientoUsdHa * e.sellosPct / 100 * e.sellosAcargoPct / 100
        val tasaVial = e.tasaVialArsHa / tc
        val impuestosLocales = iibb + sellos + tasaVial
        val margenNeto = margenBruto - impuestosLocales - e.arrendamientoUsdHa - e.estructuraUsdHa

        // Lo que deja cada tonelada después de los costos que crecen con el rinde
        val aporte = chacra - fas * (e.cosechaPct + e.iibbPct) / 100
        val aporteDirectos = chacra - fas * e.cosechaPct / 100
        val fijosTotales = e.costosDirectosUsdHa + sellos + tasaVial + e.arrendamientoUsdHa + e.estructuraUsdHa

        val composicion = if (ingresoBruto > 0) {
            listOf(
                RubroCosto.PRODUCCION to produccion,
                RubroCosto.FLETE to rinde * fletes,
                RubroCosto.COMERCIALIZACION to rinde * (comision + acondicionamiento + if (e.precioBase == PrecioBase.FOB) e.fobbingUsdTn else 0.0),
                RubroCosto.IMPUESTOS to rinde * dex + impuestosLocales,
                RubroCosto.ARRENDAMIENTO to e.arrendamientoUsdHa + e.estructuraUsdHa,
                RubroCosto.MARGEN to margenNeto
            ).map { (rubro, usd) -> Porcion(rubro, usd, usd / ingresoBruto * 100) }
        } else emptyList()

        return ResultadoMargen(
            dexUsdTn = dex,
            precioFasUsdTn = fas,
            comisionUsdTn = comision,
            acondicionamientoUsdTn = acondicionamiento,
            fletesUsdTn = fletes,
            precioChacraUsdTn = chacra,
            dexImplicitoUsdTn = dexImplicito,
            ingresoBrutoUsdHa = ingresoBruto,
            ingresoNetoUsdHa = ingresoNeto,
            costosProduccionUsdHa = produccion,
            margenBrutoUsdHa = margenBruto,
            impuestosLocalesUsdHa = impuestosLocales,
            arrendamientoUsdHa = e.arrendamientoUsdHa,
            estructuraUsdHa = e.estructuraUsdHa,
            margenNetoUsdHa = margenNeto,
            rindeIndiferenciaDirectosTnHa = if (aporteDirectos > 0) e.costosDirectosUsdHa / aporteDirectos else null,
            rindeIndiferenciaTotalTnHa = if (aporte > 0) fijosTotales / aporte else null,
            composicion = composicion,
            tipoCambio = tc,
            superficieHa = max(e.superficieHa, 0.0)
        )
    }
}

/**
 * Unidad en que el usuario carga y lee producción y precios. El cálculo siempre trabaja en
 * toneladas: 1 t = 1.000 kg = 10 qq (1 qq = 100 kg). [porTonelada] = unidades en una tonelada.
 *
 * - Cantidades (rinde, producción): en unidad = t × porTonelada   (6,5 t/ha = 65 qq/ha)
 * - Precios y costos por peso:       en unidad = US$/t ÷ porTonelada (190 US$/t = 19 US$/qq)
 *
 * Así el ingreso no cambia con la unidad: 65 qq × 19 US$/qq = 6,5 t × 190 US$/t.
 */
enum class UnidadPeso(val nombre: String, val abreviatura: String, val porTonelada: Double) {
    TONELADA("Toneladas (Tn)", "t", 1.0),
    QUINTAL("Quintales (qq)", "qq", 10.0);

    fun cantidadDesdeTn(tn: Double) = tn * porTonelada
    fun cantidadATn(valor: Double) = valor / porTonelada
    fun precioDesdeTn(porTn: Double) = porTn / porTonelada
    fun precioATn(porUnidad: Double) = porUnidad * porTonelada

    companion object {
        /** Factor para pasar una cantidad de [de] a [a] (para precios se usa el inverso). */
        fun factorCantidad(de: UnidadPeso, a: UnidadPeso) = a.porTonelada / de.porTonelada
    }
}
