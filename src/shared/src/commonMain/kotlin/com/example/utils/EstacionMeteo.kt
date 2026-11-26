package com.example.utils

import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estaciones meteorológicas propias del productor (ver supabase/functions/estaciones): marcas
 * soportadas, qué datos pide cada una y cálculos agronómicos sobre las lecturas.
 */
enum class MarcaEstacion(
    /** Valor de la columna `marca` en Supabase. */
    val clave: String,
    val nombre: String,
    val descripcion: String,
    /** Claves de la cuenta de la marca (vacío en el envío directo). */
    val campos: List<CampoCredencial>,
    /** Dónde encuentra el productor esas claves. */
    val ayuda: String
) {
    ECOWITT(
        "ecowitt", "Ecowitt / Misol",
        "Estaciones conectadas a ecowitt.net",
        listOf(
            CampoCredencial("applicationKey", "Application Key", secreto = true),
            CampoCredencial("apiKey", "API Key", secreto = true)
        ),
        "En ecowitt.net, entrá a tu perfil → Private Center → API Keys. Creá una Application Key y una API Key y copialas acá."
    ),
    DAVIS(
        "davis", "Davis (WeatherLink)",
        "Vantage Pro2, Vantage Vue o WeatherLink Live",
        listOf(
            CampoCredencial("apiKey", "API Key v2", secreto = false),
            CampoCredencial("apiSecret", "API Secret", secreto = true)
        ),
        "En weatherlink.com, entrá a Account → Generate v2 Key. Copiá la API Key y el API Secret."
    ),
    METOS(
        "metos", "Pessl Metos (FieldClimate)",
        "Estaciones iMetos en ng.fieldclimate.com",
        listOf(
            CampoCredencial("clavePublica", "Clave pública (Public key)", secreto = false),
            CampoCredencial("clavePrivada", "Clave privada (Private key)", secreto = true)
        ),
        "En ng.fieldclimate.com, entrá a User menu → API services → generá un par de claves HMAC y copialas acá."
    ),
    ENVIO(
        "envio", "Envío directo a GRANEX",
        "Cualquier estación que permita un \"servidor propio\" (Customized upload)",
        emptyList(),
        "GRANEX te da un servidor y una ruta para cargar en la configuración de la estación (por ejemplo, en la app WS View Plus de Ecowitt: Weather Services → Customized)."
    );

    companion object {
        fun deClave(clave: String): MarcaEstacion? = entries.firstOrNull { it.clave == clave }
    }
}

data class CampoCredencial(val clave: String, val etiqueta: String, val secreto: Boolean)

/** Qué cargar en la estación para el envío directo. */
data class ConfiguracionEnvio(val servidor: String, val ruta: String, val puerto: Int)

object EstacionMeteo {

    /** Servidor y ruta para el "servidor propio" de la estación, a partir de la URL de Supabase. */
    fun configuracionEnvio(urlSupabase: String, token: String): ConfiguracionEnvio {
        val servidor = urlSupabase.substringAfter("://").substringBefore('/').substringBefore(':')
        return ConfiguracionEnvio(servidor, "/functions/v1/estaciones/subir/$token", 443)
    }

    /**
     * Temperatura de bulbo húmedo (°C) con la fórmula de Stull (2011), válida entre 5 y 99 % de humedad
     * y −20 a 50 °C, con un error de menos de 1 °C.
     */
    fun bulboHumedo(temperaturaC: Double, humedadPct: Double): Double {
        val t = temperaturaC
        val h = humedadPct.coerceIn(5.0, 99.0)
        return t * atan(0.151977 * sqrt(h + 8.313659)) + atan(t + h) - atan(h - 1.676331) +
            0.00391838 * h.pow(1.5) * atan(0.023101 * h) - 4.686035
    }

    /** Delta T = temperatura − bulbo húmedo: la referencia para pulverizar (ideal entre 2 y 8). */
    fun deltaT(temperaturaC: Double, humedadPct: Double): Double = temperaturaC - bulboHumedo(temperaturaC, humedadPct)

    /** Punto de rocío (Magnus) cuando la estación no lo informa. */
    fun puntoRocio(temperaturaC: Double, humedadPct: Double): Double {
        val a = 17.62
        val b = 243.12
        val g = kotlin.math.ln(humedadPct.coerceIn(1.0, 100.0) / 100.0) + a * temperaturaC / (b + temperaturaC)
        return b * g / (a - g)
    }

    /** "N", "NE", … (de dónde viene el viento). */
    fun puntoCardinal(grados: Double): String {
        val nombres = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val i = (((grados % 360) + 360) % 360 / 45.0).roundToInt() % 8
        return nombres[i]
    }

    enum class Aptitud { BUENA, PRECAUCION, NO_RECOMENDADA }

    data class CondicionPulverizacion(
        val aptitud: Aptitud,
        val deltaT: Double?,
        /** Motivos, del más grave al más leve. Vacío si las condiciones son buenas. */
        val motivos: List<String>
    ) {
        val titulo: String
            get() = when (aptitud) {
                Aptitud.BUENA -> "Buenas condiciones para pulverizar"
                Aptitud.PRECAUCION -> "Pulverizar con precaución"
                Aptitud.NO_RECOMENDADA -> "No conviene pulverizar ahora"
            }
    }

    /**
     * Aptitud para pulverizar con las condiciones medidas en el lote: Delta T 2–8 (bajo 2 las gotas
     * finas quedan suspendidas y derivan; sobre 10 se evaporan antes de llegar), viento 3–15 km/h
     * (con calma puede haber inversión térmica), sin lluvia y por debajo de 30 °C.
     */
    fun pulverizacion(
        temperaturaC: Double?,
        humedadPct: Double?,
        vientoKmh: Double?,
        rafagaKmh: Double? = null,
        lluviaTasaMmH: Double? = null
    ): CondicionPulverizacion? {
        if (temperaturaC == null || humedadPct == null) return null
        val dt = deltaT(temperaturaC, humedadPct)
        val graves = mutableListOf<String>()
        val leves = mutableListOf<String>()
        if (lluviaTasaMmH != null && lluviaTasaMmH > 0.0) graves += "Está lloviendo"
        when {
            dt > 10 -> graves += "Delta T ${formatoDecimal(dt, 1)}: el aire está muy seco y las gotas se evaporan"
            dt > 8 -> leves += "Delta T ${formatoDecimal(dt, 1)}: aire seco, usá gotas más gruesas"
            dt < 2 -> leves += "Delta T ${formatoDecimal(dt, 1)}: aire muy húmedo, las gotas finas quedan suspendidas"
        }
        val viento = maxOf(vientoKmh ?: 0.0, (rafagaKmh ?: 0.0) * 0.75)
        when {
            vientoKmh == null -> {}
            viento > 20 -> graves += "Viento de ${formatoDecimal(vientoKmh, 0)} km/h: mucha deriva"
            viento > 15 -> leves += "Viento de ${formatoDecimal(vientoKmh, 0)} km/h: riesgo de deriva"
            vientoKmh < 3 -> leves += "Casi sin viento: puede haber inversión térmica"
        }
        if (temperaturaC > 30) leves += "Temperatura de ${formatoDecimal(temperaturaC, 0)} °C: más evaporación"
        val aptitud = when {
            graves.isNotEmpty() -> Aptitud.NO_RECOMENDADA
            leves.isNotEmpty() -> Aptitud.PRECAUCION
            else -> Aptitud.BUENA
        }
        return CondicionPulverizacion(aptitud, dt, graves + leves)
    }

    /** Aviso de helada con la temperatura medida en el lote (en el abrigo, a 1,5 m). */
    fun avisoHelada(temperaturaC: Double?): String? = when {
        temperaturaC == null -> null
        temperaturaC <= 0.0 -> "Helada: ${formatoDecimal(temperaturaC, 1)} °C en la estación"
        temperaturaC <= 3.0 -> "Riesgo de helada a nivel del suelo: ${formatoDecimal(temperaturaC, 1)} °C en la estación"
        else -> null
    }

    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?\s*(Z|[+-]\d{2}(?::?\d{2})?)?$""")

    /**
     * Milisegundos de una fecha ISO 8601 del servidor ("2026-11-16T13:05:00.000Z", o con desfase
     * "+00:00" como las devuelve Postgres). Sin zona se toma como UTC. Null si no se entiende.
     */
    fun epochMs(iso: String): Long? {
        val m = ISO.matchEntire(iso.trim()) ?: return null
        val g = m.groupValues
        val anio = g[1].toLong()
        val mes = g[2].toInt()
        val dia = g[3].toInt()
        if (mes !in 1..12 || dia !in 1..31) return null
        // Días desde 1970-01-01 (algoritmo de Howard Hinnant)
        val y = if (mes <= 2) anio - 1 else anio
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (mes + 9) % 12
        val doy = (153 * mp + 2) / 5 + dia - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val dias = era * 146097 + doe - 719468
        val milis = g[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
        var ms = ((dias * 24 + g[4].toLong()) * 60 + g[5].toLong()) * 60_000L + (g[6].toLongOrNull() ?: 0L) * 1000L + milis
        val zona = g[8]
        if (zona.isNotEmpty() && zona != "Z") {
            val signo = if (zona[0] == '-') -1 else 1
            val digitos = zona.substring(1).replace(":", "")
            val desfaseMin = digitos.take(2).toInt() * 60 + (digitos.drop(2).toIntOrNull() ?: 0)
            ms -= signo * desfaseMin * 60_000L
        }
        return ms
    }

    /** Hora de Argentina (UTC−3) "HH:mm" de un instante. */
    fun horaArgentina(epochMs: Long): String {
        val minutos = ((epochMs - 3 * 3_600_000L).floorDiv(60_000L)).mod(24 * 60L)
        return "${(minutos / 60).toString().padStart(2, '0')}:${(minutos % 60).toString().padStart(2, '0')}"
    }

    /** "hace 5 min", "hace 3 h", "hace 2 días" desde [momentoMs] hasta [ahoraMs]. */
    fun antiguedad(momentoMs: Long, ahoraMs: Long): String {
        val min = ((ahoraMs - momentoMs) / 60_000L).coerceAtLeast(0)
        return when {
            min < 1 -> "recién"
            min < 60 -> "hace $min min"
            min < 48 * 60 -> "hace ${min / 60} h"
            else -> "hace ${min / (24 * 60)} días"
        }
    }
}
