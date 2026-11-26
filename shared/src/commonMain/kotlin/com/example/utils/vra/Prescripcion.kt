package com.example.utils.vra

import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Labor(val nombre: String) {
    FERTILIZACION("Fertilización"),
    SIEMBRA("Siembra"),
    SIEMBRA_Y_FERTILIZACION("Siembra + fertilización");

    val usaFertilizante: Boolean get() = this != SIEMBRA
    val usaSemillas: Boolean get() = this != FERTILIZACION
}

enum class UbicacionFertilizante(val nombre: String) {
    LINEA("En la línea de siembra"),
    COSTADO("Al costado de la línea")
}

/**
 * Formato de salida por monitor. Requisitos de Cuffia según sus guías de carga de prescripciones:
 * 4 archivos (.shp .shx .dbf .prj) sueltos en la raíz del pendrive, nombre de hasta 8 caracteres
 * sin guiones, dosis en números enteros.
 */
enum class PerfilMonitor(val nombre: String, val detalle: String, val maxPoligonos: Int?) {
    CUFFIA_AG9500(
        "Abelardo Cuffia AG 9500",
        "3 columnas enteras: fertilizante en la línea, al costado (kg/ha) y semillas cada 10 m. Hasta 50 polígonos.",
        50
    ),
    CUFFIA_AG_FUSION(
        "Abelardo Cuffia AG Fusion",
        "3 columnas enteras: fertilizante en la línea, al costado (kg/ha) y semillas cada 10 m. Hasta 100 polígonos.",
        100
    ),
    CUFFIA_FGS(
        "Abelardo Cuffia FGS Black/Gris o Fusion Pro",
        "Semillas en sem/ha y fertilizante en kg/ha, enteros. El factor de conversión se carga en el monitor.",
        null
    ),
    CUFFIA_ALTINA(
        "Abelardo Cuffia Altina",
        "Primera columna en kg/ha (semilla o fertilizante) y las otras en 0. Hasta 50 polígonos.",
        50
    ),
    GENERICO("Shapefile genérico", "Para monitores de otras marcas que importan shapefile.", null),
    ISOBUS("ISOBUS (ISO-XML)", "TASKDATA para terminales ISOBUS (Task Controller).", null);

    val esCuffia: Boolean get() = name.startsWith("CUFFIA")
    /** Los Cuffia AG piden semillas cada 10 m lineales, así que necesitan la distancia entre surcos. */
    val semillasCada10m: Boolean get() = this == CUFFIA_AG9500 || this == CUFFIA_AG_FUSION
}

data class DosisZona(val zona: Int, val fertilizanteKgHa: Int? = null, val semillasHa: Int? = null)

data class Prescripcion(
    val nombreLote: String,
    val lote: List<Pair<Double, Double>>,
    val fechaImagen: String,
    val indice: IndiceVegetacion,
    val zonificacion: Zonificacion,
    /** De dónde salen las zonas, para el LEEME y las exportaciones. */
    val fuente: String = "Sentinel-2 L2A del $fechaImagen, índice ${indice.nombre}",
    /** Nombre del valor de cada zona: el índice o el potencial relativo. */
    val nombreValor: String = indice.nombre,
    val labor: Labor,
    val ubicacionFertilizante: UbicacionFertilizante,
    val distanciaSurcosM: Double,
    val dosis: List<DosisZona>,
    val producto: String,
    /** Análisis de suelo, rinde por zona y criterio de la dosis, para el LEEME. */
    val notas: List<String> = emptyList()
) {
    fun dosisDe(zona: Int): DosisZona = dosis.firstOrNull { it.zona == zona } ?: DosisZona(zona)

    /** Problemas que impiden exportar para [perfil]; vacía si está lista. */
    fun problemas(perfil: PerfilMonitor): List<String> = buildList {
        zonificacion.zonas.forEach { z ->
            val d = dosisDe(z.zona)
            if (perfil == PerfilMonitor.CUFFIA_ALTINA) {
                // Altina usa un único valor en kg/ha (semilla o fertilizante), guardado en fertilizanteKgHa
                if (d.fertilizanteKgHa == null || d.fertilizanteKgHa < 0) add("Falta la dosis en kg/ha de la zona ${z.zona}.")
            } else {
                if (labor.usaFertilizante && (d.fertilizanteKgHa == null || d.fertilizanteKgHa < 0)) add("Falta la dosis de fertilizante de la zona ${z.zona}.")
                if (labor.usaSemillas && (d.semillasHa == null || d.semillasHa <= 0)) add("Falta la densidad de siembra de la zona ${z.zona}.")
            }
        }
        if (perfil.semillasCada10m && labor.usaSemillas && distanciaSurcosM !in 0.1..2.0) add("La distancia entre surcos debe estar entre 0,10 y 2,00 m.")
        if (perfil == PerfilMonitor.CUFFIA_ALTINA && labor == Labor.SIEMBRA_Y_FERTILIZACION) add("Altina dosifica un solo producto en kg/ha: elige fertilización o siembra.")
        perfil.maxPoligonos?.let { max ->
            if (zonificacion.poligonos.size > max) add("Hay ${zonificacion.poligonos.size} polígonos y el monitor lee hasta $max: sube el área mínima o usa menos zonas.")
        }
    }

    fun totalFertilizanteKg(): Double = zonificacion.zonas.sumOf { z -> (dosisDe(z.zona).fertilizanteKgHa ?: 0) * z.areaHa }

    fun totalSemillas(): Double = zonificacion.zonas.sumOf { z -> (dosisDe(z.zona).semillasHa ?: 0).toDouble() * z.areaHa }

    /** Dosis promedio ponderada por superficie, para el modo "sin GPS" de ISOBUS. */
    fun promedioFertilizante(): Int = (totalFertilizanteKg() / zonificacion.zonas.sumOf { it.areaHa }).roundToInt()

    fun promedioSemillas(): Int = (totalSemillas() / zonificacion.zonas.sumOf { it.areaHa }).roundToInt()
}

object ConversionesDosis {
    /**
     * sem/ha → semillas cada 10 m lineales (Cuffia AG 9500 / AG Fusion):
     * sem/ha × distancia entre surcos (m) / 10 000 m² × 10 m. Ej.: 80 000 sem/ha a 0,525 m → 42.
     */
    fun semillasCada10m(semillasHa: Int, distanciaSurcosM: Double): Int =
        (semillasHa * distanciaSurcosM / 1000.0).roundToInt()

    /** kg/ha → mg/m² (DDI 6 de ISOBUS, resolución 1). */
    fun kgHaAMgM2(kgHa: Int): Long = kgHa * 100L

    /** sem/ha → DDI 11 de ISOBUS (cantidad por m² con resolución 0,001): sem/ha ÷ 10. */
    fun semillasHaADdi11(semillasHa: Int): Long = (semillasHa / 10.0).roundToLong()
}
