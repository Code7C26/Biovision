package com.example.data.agenda

import kotlinx.serialization.Serializable
import kotlin.math.max

/** Categorías de gasto. [alStock]: por defecto lo comprado se suma al stock de insumos. */
@Serializable
enum class CategoriaGasto(val nombre: String, val alStock: Boolean, val unidadSugerida: String) {
    SEMILLA("Semilla", true, "bolsas"),
    FERTILIZANTE("Fertilizante", true, "kg"),
    FITOSANITARIO("Fitosanitario", true, "L"),
    LABOR("Labor", false, "ha"),
    COMBUSTIBLE("Combustible", true, "L"),
    OTRO("Otro", false, "unidades")
}

@Serializable
enum class EstadoPago(val nombre: String) { PAGADO("Pagado"), A_PAGAR("A pagar"), CANJE("Canje") }

@Serializable
enum class Moneda(val simbolo: String) { USD("US$"), ARS("$") }

/**
 * Un gasto de la campaña. Guarda la moneda en que se pagó y el dólar de ese día ([tipoCambio], pesos
 * por dólar), así los totales en dólares no cambian cuando se mueve el dólar.
 */
@Serializable
data class Gasto(
    val id: String,
    /** yyyy-MM-dd */
    val fecha: String,
    val categoria: CategoriaGasto,
    val producto: String,
    val cantidad: Double,
    val unidad: String,
    val precioUnitario: Double,
    val moneda: Moneda,
    val tipoCambio: Double,
    /** uuid del lote (el mismo en todos los dispositivos); null = gasto general del campo. */
    val loteId: String? = null,
    val loteNombre: String? = null,
    val proveedor: String = "",
    val estadoPago: EstadoPago = EstadoPago.PAGADO,
    /** yyyy-MM-dd; solo para los que quedan a pagar. */
    val vencimiento: String? = null,
    val alStock: Boolean = categoria.alStock,
    val notas: String = "",
    /** Milisegundos de la última edición: al juntar dos dispositivos gana la más nueva. */
    val modificadoEn: Long = 0
) {
    val total: Double get() = cantidad * precioUnitario
    val totalUsd: Double get() = when {
        moneda == Moneda.USD -> total
        tipoCambio > 0 -> total / tipoCambio
        else -> 0.0
    }
    val totalPesos: Double get() = if (moneda == Moneda.ARS) total else total * tipoCambio
}

/** Lo que se usó de un insumo del stock (siembra, pulverización, fertilización). */
@Serializable
data class Aplicacion(
    val id: String,
    val fecha: String,
    val producto: String,
    val unidad: String,
    val cantidad: Double,
    val loteId: String? = null,
    val loteNombre: String? = null,
    val modificadoEn: Long = 0
)

/**
 * Todo lo anotado. [borrados] guarda los ids de lo que se borró, para que al juntarse con otro
 * dispositivo un gasto borrado acá no vuelva desde allá.
 */
@Serializable
data class DatosAgenda(
    val gastos: List<Gasto> = emptyList(),
    val aplicaciones: List<Aplicacion> = emptyList(),
    val borrados: Set<String> = emptySet()
)

/** Un lote, con lo que la Agenda necesita de él ([id] es el uuid). */
data class LoteAgenda(val id: String, val nombre: String, val hectareas: Double)

data class ResumenGastos(
    val totalUsd: Double,
    val totalPesos: Double,
    val porCategoria: Map<CategoriaGasto, Double>,
    val aPagarUsd: Double,
    val facturasAPagar: Int,
    /** yyyy-MM-dd del vencimiento más cercano de lo que queda a pagar. */
    val proximoVencimiento: String?
)

data class InsumoEnStock(
    val producto: String,
    val unidad: String,
    val categoria: CategoriaGasto,
    val comprado: Double,
    val aplicado: Double,
    /** Precio promedio pagado, en US$ por unidad. */
    val usdPorUnidad: Double
) {
    val restante: Double get() = max(0.0, comprado - aplicado)
    val fraccionUsada: Double get() = if (comprado <= 0) 0.0 else (aplicado / comprado).coerceIn(0.0, 1.0)
    /** Queda menos del 15 % de lo comprado. */
    val poco: Boolean get() = comprado > 0 && restante < comprado * 0.15
}

data class CostoLote(
    val lote: LoteAgenda,
    val totalUsd: Double,
    val porCategoria: Map<CategoriaGasto, Double>
) {
    val usdPorHa: Double get() = if (lote.hectareas > 0) totalUsd / lote.hectareas else 0.0
}

data class CostosPorLote(
    val lotes: List<CostoLote>,
    /** Gastos sin lote (gasoil general, estructura) o de un lote borrado: se reparten por hectárea. */
    val generalesUsd: Double,
    val hectareasTotales: Double
) {
    val generalesPorHa: Double get() = if (hectareasTotales > 0) generalesUsd / hectareasTotales else 0.0
}

/** Cálculos de la Agenda (puros, probados en AgendaTest). */
object Agenda {
    val UNIDADES = listOf("bolsas", "kg", "t", "L", "ha", "unidades", "horas")

    /** Campaña agrícola de julio a junio: 2026-11-21 → "2026/27". */
    fun campanaDe(fecha: String): String {
        val anio = fecha.take(4).toIntOrNull() ?: return ""
        val mes = fecha.drop(5).take(2).toIntOrNull() ?: 1
        val inicio = if (mes >= 7) anio else anio - 1
        return "$inicio/" + ((inicio + 1) % 100).toString().padStart(2, '0')
    }

    /** Campañas con datos más la actual, de la más nueva a la más vieja. */
    fun campanas(datos: DatosAgenda, hoy: String): List<String> =
        (datos.gastos.map { campanaDe(it.fecha) } + datos.aplicaciones.map { campanaDe(it.fecha) } + campanaDe(hoy))
            .filter { it.isNotEmpty() }.distinct().sortedDescending()

    fun deCampana(datos: DatosAgenda, campana: String) = datos.copy(
        gastos = datos.gastos.filter { campanaDe(it.fecha) == campana },
        aplicaciones = datos.aplicaciones.filter { campanaDe(it.fecha) == campana }
    )

    fun resumen(gastos: List<Gasto>): ResumenGastos {
        val aPagar = gastos.filter { it.estadoPago == EstadoPago.A_PAGAR }
        return ResumenGastos(
            totalUsd = gastos.sumOf { it.totalUsd },
            totalPesos = gastos.sumOf { it.totalPesos },
            porCategoria = CategoriaGasto.entries.associateWith { c -> gastos.filter { it.categoria == c }.sumOf { it.totalUsd } }
                .filterValues { it > 0 },
            aPagarUsd = aPagar.sumOf { it.totalUsd },
            facturasAPagar = aPagar.size,
            proximoVencimiento = aPagar.mapNotNull { it.vencimiento }.minOrNull()
        )
    }

    private fun clave(producto: String, unidad: String) = producto.trim().lowercase() + "|" + unidad.trim().lowercase()

    /** Stock por producto y unidad: lo comprado que va al stock menos lo aplicado. */
    fun stock(datos: DatosAgenda): List<InsumoEnStock> {
        val compras = datos.gastos.filter { it.alStock && it.cantidad > 0 }.groupBy { clave(it.producto, it.unidad) }
        val usos = datos.aplicaciones.groupBy { clave(it.producto, it.unidad) }
        return compras.map { (k, lista) ->
            val ultimo = lista.maxBy { it.fecha }
            val comprado = lista.sumOf { it.cantidad }
            InsumoEnStock(
                producto = ultimo.producto.trim(),
                unidad = ultimo.unidad,
                categoria = ultimo.categoria,
                comprado = comprado,
                aplicado = usos[k].orEmpty().sumOf { it.cantidad },
                usdPorUnidad = lista.sumOf { it.totalUsd } / comprado
            )
        }.sortedWith(compareBy<InsumoEnStock> { it.categoria.ordinal }.thenBy { it.producto.lowercase() })
    }

    fun costosPorLote(gastos: List<Gasto>, lotes: List<LoteAgenda>): CostosPorLote {
        val ids = lotes.map { it.id }.toSet()
        val porLote = gastos.filter { it.loteId != null && it.loteId in ids }.groupBy { it.loteId }
        return CostosPorLote(
            lotes = lotes.map { lote ->
                val propios = porLote[lote.id].orEmpty()
                CostoLote(
                    lote = lote,
                    totalUsd = propios.sumOf { it.totalUsd },
                    porCategoria = CategoriaGasto.entries.associateWith { c -> propios.filter { it.categoria == c }.sumOf { it.totalUsd } }
                        .filterValues { it > 0 }
                )
            }.sortedByDescending { it.usdPorHa },
            generalesUsd = gastos.filter { it.loteId == null || it.loteId !in ids }.sumOf { it.totalUsd },
            hectareasTotales = lotes.sumOf { it.hectareas }
        )
    }

    /** Costo total del lote por hectárea, con su parte de los gastos generales. */
    fun usdPorHaConGenerales(costos: CostosPorLote, loteId: String): Double? =
        costos.lotes.firstOrNull { it.lote.id == loteId }?.let { it.usdPorHa + costos.generalesPorHa }

    /** Acepta coma o punto decimal ("1,5" o "1.5"); null si no es un número. */
    fun leerNumero(texto: String): Double? = texto.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()

    /** "2026-11-21" → "21 nov". */
    fun fechaCorta(iso: String): String {
        val p = iso.split("-")
        if (p.size != 3) return iso
        val mes = p[1].toIntOrNull()?.let { MESES.getOrNull(it - 1) } ?: p[1]
        return "${p[2].trimStart('0')} $mes"
    }

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
}
