package com.example.utils.vra

import com.example.utils.grados
import com.example.utils.radianes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Tabla leída de un archivo del campo (monitor de rinde o laboratorio de suelos): una fila por punto,
 * con su posición en WGS84 y las columnas numéricas (NaN donde la celda está vacía o no es un número).
 * Puro, probado en ArchivosCampoTest.
 */
class TablaCampo(val lat: DoubleArray, val lon: DoubleArray, val columnas: Map<String, DoubleArray>) {
    val filas: Int get() = lat.size

    /** Mediana de una columna sin contar los vacíos, para mostrarla y sugerir la unidad. */
    fun mediana(columna: String): Double? {
        val v = columnas[columna]?.filter { !it.isNaN() }?.sorted() ?: return null
        return if (v.isEmpty()) null else v[v.size / 2]
    }

    /**
     * La primera columna cuyo nombre (normalizado) empieza con alguna de las [claves], en orden. Una
     * clave de una letra ("p", "n") tiene que coincidir entera: si no, "p" encontraría "ph".
     */
    fun buscar(vararg claves: String): String? =
        claves.firstNotNullOfOrNull { clave ->
            columnas.keys.firstOrNull { nombre ->
                normalizarNombre(nombre).let { if (clave.length == 1) it == clave else it.startsWith(clave) }
            }
        }
}

class ErrorArchivoCampo(mensaje: String) : Exception(mensaje)

/** "Latitud (°)" → "latitud": minúsculas, sin tildes ni símbolos. */
internal fun normalizarNombre(nombre: String): String = buildString {
    for (c in nombre.lowercase()) {
        val base = when (c) {
            'á', 'à', 'ä' -> 'a'
            'é', 'è', 'ë' -> 'e'
            'í', 'ì', 'ï' -> 'i'
            'ó', 'ò', 'ö' -> 'o'
            'ú', 'ù', 'ü' -> 'u'
            'ñ' -> 'n'
            else -> c
        }
        if (base in 'a'..'z' || base in '0'..'9' || base == '_') append(base)
    }
}

private fun esLatitud(nombre: String): Boolean = normalizarNombre(nombre).let { it == "y" || it.startsWith("lat") }

private fun esLongitud(nombre: String): Boolean =
    normalizarNombre(nombre).let { it == "x" || it.startsWith("lon") || it.startsWith("lng") }

/** Columnas en las que al menos la mitad de las filas tiene un número. */
private fun soloNumericas(columnas: Map<String, DoubleArray>): Map<String, DoubleArray> =
    columnas.filterValues { v -> v.isNotEmpty() && v.count { !it.isNaN() } * 2 >= v.size }

/**
 * CSV o TXT exportado de una planilla o del laboratorio. Detecta el separador (coma, punto y coma o
 * tabulación) y la coma decimal, y busca la latitud y la longitud por el nombre de la columna.
 */
object LectorCsv {

    fun leer(texto: String): TablaCampo {
        val lineas = texto.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lineas.size < 2) throw ErrorArchivoCampo("El archivo no tiene datos.")
        val encabezado = lineas.first()
        val separador = listOf(';', '\t', ',').maxBy { c -> encabezado.count { it == c } }
        if (encabezado.none { it == separador }) throw ErrorArchivoCampo("No reconozco el separador de columnas del archivo.")
        // Con punto y coma o tabulación, la coma es el separador decimal (planillas en castellano)
        val comaDecimal = separador != ','
        val nombres = partir(encabezado, separador).map { it.trim() }
        val iLat = nombres.indexOfFirst(::esLatitud)
        val iLon = nombres.indexOfFirst(::esLongitud)
        if (iLat < 0 || iLon < 0) throw ErrorArchivoCampo("No encontré las columnas de latitud y longitud (lat/latitud y lon/longitud).")

        val filas = lineas.drop(1).map { partir(it, separador) }
            .filter { f -> numero(f.getOrNull(iLat), comaDecimal) != null && numero(f.getOrNull(iLon), comaDecimal) != null }
        if (filas.isEmpty()) throw ErrorArchivoCampo("Ninguna fila tiene latitud y longitud válidas.")
        val lat = DoubleArray(filas.size) { numero(filas[it][iLat], comaDecimal)!! }
        val lon = DoubleArray(filas.size) { numero(filas[it][iLon], comaDecimal)!! }
        if (lat.any { abs(it) > 90 } || lon.any { abs(it) > 180 }) {
            throw ErrorArchivoCampo("Las coordenadas no son latitud y longitud en grados. Exportá el archivo en WGS84.")
        }
        val columnas = nombres.indices.filter { it != iLat && it != iLon }.associate { c ->
            nombres[c] to DoubleArray(filas.size) { numero(filas[it].getOrNull(c), comaDecimal) ?: Double.NaN }
        }
        return TablaCampo(lat, lon, soloNumericas(columnas))
    }

    private fun numero(texto: String?, comaDecimal: Boolean): Double? {
        val t = texto?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        // "1.234,5" → 1234,5. Sin coma se lee tal cual: muchos monitores separan con ";" pero usan
        // punto decimal en las coordenadas (-31.5886)
        if (comaDecimal && ',' in t) return t.replace(".", "").replace(',', '.').toDoubleOrNull()
        return t.toDoubleOrNull() ?: t.replace(',', '.').toDoubleOrNull()
    }

    /** Parte una línea respetando los campos entre comillas. */
    private fun partir(linea: String, separador: Char): List<String> {
        val campos = mutableListOf<String>()
        val actual = StringBuilder()
        var entreComillas = false
        for (c in linea) {
            when {
                c == '"' -> entreComillas = !entreComillas
                c == separador && !entreComillas -> {
                    campos += actual.toString()
                    actual.clear()
                }
                else -> actual.append(c)
            }
        }
        campos += actual.toString()
        return campos
    }
}

/**
 * Shapefile de puntos o de polígonos (pasadas de la cosechadora) con su tabla de atributos (.dbf).
 * Cada polígono se toma por el centro de su recuadro. Coordenadas en WGS84 o en UTM (se reconoce la
 * zona en el .prj); otras proyecciones piden exportar en WGS84.
 */
object LectorShapefile {

    fun leer(shp: ByteArray, dbf: ByteArray?, prj: String?): TablaCampo {
        if (shp.size < 100 || enteroBigEndian(shp, 0) != 9994) throw ErrorArchivoCampo("El archivo .shp no es un shapefile válido.")
        val posiciones = mutableListOf<Pair<Double, Double>?>()
        var p = 100
        while (p + 8 <= shp.size) {
            val largo = enteroBigEndian(shp, p + 4) * 2
            val contenido = p + 8
            if (contenido + largo > shp.size || largo < 4) break
            posiciones += when (enteroLittleEndian(shp, contenido)) {
                1, 11, 21 -> realLittleEndian(shp, contenido + 4) to realLittleEndian(shp, contenido + 12)
                3, 5, 8, 13, 15, 18, 23, 25, 28 -> {
                    // Recuadro: xmin, ymin, xmax, ymax
                    val xMin = realLittleEndian(shp, contenido + 4)
                    val yMin = realLittleEndian(shp, contenido + 12)
                    val xMax = realLittleEndian(shp, contenido + 20)
                    val yMax = realLittleEndian(shp, contenido + 28)
                    (xMin + xMax) / 2 to (yMin + yMax) / 2
                }
                else -> null
            }
            p = contenido + largo
        }
        if (posiciones.none { it != null }) throw ErrorArchivoCampo("El shapefile no tiene puntos.")
        val atributos = dbf?.let(::leerDbf) ?: emptyMap()

        val proyectadas = posiciones.filterNotNull().any { (x, y) -> abs(x) > 180 || abs(y) > 90 }
        val utm = if (proyectadas) zonaUtm(prj) ?: throw ErrorArchivoCampo(
            "El archivo está en una proyección que no reconozco. Exportalo en WGS84 (latitud y longitud) o en UTM."
        ) else null

        val validas = posiciones.indices.filter { posiciones[it] != null }
        val lat = DoubleArray(validas.size)
        val lon = DoubleArray(validas.size)
        validas.forEachIndexed { k, i ->
            val (x, y) = posiciones[i]!!
            if (utm == null) {
                lat[k] = y
                lon[k] = x
            } else {
                val (la, lo) = Utm.aLatLon(x, y, utm.first, utm.second)
                lat[k] = la
                lon[k] = lo
            }
        }
        val columnas = atributos.mapValues { (_, v) -> DoubleArray(validas.size) { k -> v.getOrElse(validas[k]) { Double.NaN } } }
        return TablaCampo(lat, lon, soloNumericas(columnas))
    }

    /** Zona UTM y hemisferio (true = sur) a partir del .prj, p. ej. "WGS_1984_UTM_Zone_20S". */
    internal fun zonaUtm(prj: String?): Pair<Int, Boolean>? {
        val texto = prj ?: return null
        val m = Regex("UTM[ _]?zone[ _]?(\\d{1,2})\\s*([NS])?", RegexOption.IGNORE_CASE).find(texto) ?: return null
        val zona = m.groupValues[1].toInt().takeIf { it in 1..60 } ?: return null
        val sur = when {
            m.groupValues[2].isNotEmpty() -> m.groupValues[2].equals("S", ignoreCase = true)
            else -> texto.contains("south", ignoreCase = true) || Regex("False_Northing\"?,\\s*10000000").containsMatchIn(texto)
        }
        return zona to sur
    }

    /** Tabla dBase: valores numéricos por columna (NaN si la celda no es un número). */
    internal fun leerDbf(dbf: ByteArray): Map<String, DoubleArray> {
        if (dbf.size < 32) return emptyMap()
        val registros = enteroLittleEndian(dbf, 4)
        val largoEncabezado = corto(dbf, 8)
        val largoRegistro = corto(dbf, 10)
        data class Campo(val nombre: String, val desde: Int, val largo: Int)
        val campos = mutableListOf<Campo>()
        var p = 32
        var desde = 1 // el primer byte de cada registro marca si está borrado
        while (p + 32 <= largoEncabezado && dbf[p] != 0x0D.toByte()) {
            val nombre = latin1(dbf, p, 11).trimEnd('\u0000').trim()
            val largo = dbf[p + 16].toInt() and 0xFF
            campos += Campo(nombre, desde, largo)
            desde += largo
            p += 32
        }
        val valores = campos.associate { it.nombre to DoubleArray(registros) { Double.NaN } }
        for (r in 0 until registros) {
            val inicio = largoEncabezado + r * largoRegistro
            if (inicio + largoRegistro > dbf.size) break
            for (c in campos) {
                val texto = latin1(dbf, inicio + c.desde, c.largo).trim()
                texto.replace(',', '.').toDoubleOrNull()?.let { valores.getValue(c.nombre)[r] = it }
            }
        }
        return valores
    }

    private fun latin1(b: ByteArray, desde: Int, largo: Int): String =
        buildString { for (i in desde until minOf(desde + largo, b.size)) append((b[i].toInt() and 0xFF).toChar()) }

    private fun corto(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun enteroBigEndian(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun enteroLittleEndian(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)

    private fun realLittleEndian(b: ByteArray, i: Int): Double {
        var bits = 0L
        for (k in 7 downTo 0) bits = (bits shl 8) or (b[i + k].toLong() and 0xFF)
        return Double.fromBits(bits)
    }
}

/** Conversión UTM (WGS84) → latitud/longitud, fórmulas de Snyder (precisión submétrica). */
object Utm {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    fun aLatLon(este: Double, norte: Double, zona: Int, sur: Boolean): Pair<Double, Double> {
        val x = este - 500_000.0
        val y = if (sur) norte - 10_000_000.0 else norte
        val lon0 = radianes((zona - 1) * 6 - 180 + 3.0)
        val m = y / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2.pow(3) / 256))
        val e1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
        val phi1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)
        val sinPhi = sin(phi1)
        val cosPhi = cos(phi1)
        val tanPhi = tan(phi1)
        val c1 = EP2 * cosPhi * cosPhi
        val t1 = tanPhi * tanPhi
        val n1 = A / sqrt(1 - E2 * sinPhi * sinPhi)
        val r1 = A * (1 - E2) / (1 - E2 * sinPhi * sinPhi).pow(1.5)
        val d = x / (n1 * K0)
        val lat = phi1 - (n1 * tanPhi / r1) * (
            d * d / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lon = lon0 + (
            d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120
            ) / cosPhi
        return grados(lat) to grados(lon)
    }
}
