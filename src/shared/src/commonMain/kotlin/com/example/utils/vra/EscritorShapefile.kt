package com.example.utils.vra

import com.example.utils.FechasIso
import com.example.utils.formatoDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Campo de la tabla .dbf (dBase III): 'N' numérico o 'C' texto. Nombre de hasta 10 caracteres. */
data class CampoDbf(val nombre: String, val tipo: Char, val largo: Int, val decimales: Int = 0) {
    init {
        require(nombre.length in 1..10 && nombre.all { it.isLetterOrDigit() || it == '_' }) { "Nombre de campo inválido: $nombre" }
        require(tipo == 'N' || tipo == 'C')
    }
}

/**
 * Escribe shapefiles de polígonos (tipo 5) en WGS84 según la especificación técnica de ESRI (1998),
 * sin librerías: .shp y .shx con encabezados big/little endian, .dbf dBase III y .prj en formato ESRI.
 * En :shared (sin java.nio) para que la web genere los mismos archivos que Android.
 */
object EscritorShapefile {

    const val PRJ_WGS84 = "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]]," +
        "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"

    /**
     * [poligonos]: por registro, sus anillos en (lat, lon), cerrados, exterior horario y huecos antihorarios.
     * Devuelve los bytes por extensión: "shp", "shx", "dbf", "prj".
     */
    fun escribir(
        poligonos: List<List<List<Pair<Double, Double>>>>,
        campos: List<CampoDbf>,
        registros: List<List<Any?>>
    ): Map<String, ByteArray> {
        require(poligonos.size == registros.size) { "Cada polígono necesita su registro de atributos" }
        require(registros.all { it.size == campos.size }) { "Los registros no coinciden con los campos" }

        val contenidos = poligonos.map(::contenidoPoligono)
        val todos = poligonos.flatten().flatten()
        val caja = doubleArrayOf(
            todos.minOf { it.second }, todos.minOf { it.first },
            todos.maxOf { it.second }, todos.maxOf { it.first }
        )

        val largoShp = 100 + contenidos.sumOf { 8 + it.size }
        val shp = Bytes(largoShp)
        encabezado(shp, largoShp, caja)
        val shx = Bytes(100 + 8 * contenidos.size)
        encabezado(shx, 100 + 8 * contenidos.size, caja)
        var desplazamiento = 100
        contenidos.forEachIndexed { i, c ->
            shp.intBig(i + 1).intBig(c.size / 2)
            shp.bytes(c)
            shx.intBig(desplazamiento / 2).intBig(c.size / 2)
            desplazamiento += 8 + c.size
        }

        return mapOf(
            "shp" to shp.datos,
            "shx" to shx.datos,
            "dbf" to dbf(campos, registros),
            "prj" to PRJ_WGS84.encodeToByteArray()
        )
    }

    private fun encabezado(b: Bytes, largoBytes: Int, caja: DoubleArray) {
        b.intBig(9994)
        repeat(5) { b.intBig(0) }
        b.intBig(largoBytes / 2) // en palabras de 16 bits
        b.intLittle(1000).intLittle(5)
        caja.forEach { b.doubleLittle(it) }
        repeat(4) { b.doubleLittle(0.0) } // Z y M sin usar
    }

    private fun contenidoPoligono(anillos: List<List<Pair<Double, Double>>>): ByteArray {
        val puntos = anillos.sumOf { it.size }
        val b = Bytes(44 + 4 * anillos.size + 16 * puntos)
        val todos = anillos.flatten()
        b.intLittle(5)
        b.doubleLittle(todos.minOf { it.second }).doubleLittle(todos.minOf { it.first })
        b.doubleLittle(todos.maxOf { it.second }).doubleLittle(todos.maxOf { it.first })
        b.intLittle(anillos.size).intLittle(puntos)
        var inicio = 0
        anillos.forEach { b.intLittle(inicio); inicio += it.size }
        todos.forEach { (lat, lon) -> b.doubleLittle(lon).doubleLittle(lat) } // X = longitud, Y = latitud
        return b.datos
    }

    @OptIn(ExperimentalTime::class)
    private fun dbf(campos: List<CampoDbf>, registros: List<List<Any?>>): ByteArray {
        val largoEncabezado = 32 + 32 * campos.size + 1
        val largoRegistro = 1 + campos.sumOf { it.largo }
        val b = Bytes(largoEncabezado + registros.size * largoRegistro + 1)
        // Fecha de la última modificación (año desde 1900, mes, día); la de Argentina
        val hoy = FechasIso.deArgentina(Clock.System.now().toEpochMilliseconds())
        b.byte(0x03)
        b.byte(hoy.substring(0, 4).toInt() - 1900).byte(hoy.substring(5, 7).toInt()).byte(hoy.substring(8, 10).toInt())
        b.intLittle(registros.size).shortLittle(largoEncabezado).shortLittle(largoRegistro)
        repeat(20) { b.byte(0) }
        campos.forEach { campo ->
            val d = ByteArray(32)
            campo.nombre.encodeToByteArray().copyInto(d)
            d[11] = campo.tipo.code.toByte()
            d[16] = campo.largo.toByte()
            d[17] = campo.decimales.toByte()
            b.bytes(d)
        }
        b.byte(0x0D)
        registros.forEach { registro ->
            b.byte(' '.code) // registro vigente (no borrado)
            campos.forEachIndexed { i, campo -> b.bytes(valorDbf(campo, registro[i])) }
        }
        b.byte(0x1A)
        return b.datos
    }

    private fun valorDbf(campo: CampoDbf, valor: Any?): ByteArray {
        val texto = when {
            valor == null -> ""
            campo.tipo == 'N' && campo.decimales == 0 -> (valor as Number).toLong().toString()
            campo.tipo == 'N' -> decimalConPunto((valor as Number).toDouble(), campo.decimales)
            else -> soloAscii(valor.toString())
        }
        require(texto.length <= campo.largo) { "El valor $texto no entra en el campo ${campo.nombre}" }
        val relleno = if (campo.tipo == 'N') texto.padStart(campo.largo) else texto.padEnd(campo.largo)
        return relleno.encodeToByteArray()
    }

    /** Los monitores suelen leer el .dbf como ASCII: se quitan tildes y caracteres especiales. */
    fun soloAscii(texto: String): String =
        texto.map { c -> SIN_TILDE[c] ?: c }.map { if (it.code in 32..126) it else '?' }.joinToString("")

    private val SIN_TILDE: Map<Char, Char> = buildMap {
        val con = "áàâäãåéèêëíìîïóòôöõúùûüñçÁÀÂÄÃÅÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇ"
        val sin = "aaaaaaeeeeiiiiooooouuuuncAAAAAAEEEEIIIIOOOOOUUUUNC"
        con.forEachIndexed { i, c -> put(c, sin[i]) }
    }
}

/** "1234,57" de formatoDecimal → "1234.57", como String.format(Locale.US, …). */
internal fun decimalConPunto(valor: Double, decimales: Int): String = formatoDecimal(valor, decimales).replace(',', '.')

/** Bytes de tamaño fijo con enteros y doubles big/little endian (lo que hacía java.nio.ByteBuffer). */
internal class Bytes(largo: Int) {
    val datos = ByteArray(largo)
    private var pos = 0

    fun byte(v: Int): Bytes {
        datos[pos++] = v.toByte()
        return this
    }

    fun bytes(v: ByteArray): Bytes {
        v.copyInto(datos, pos)
        pos += v.size
        return this
    }

    fun intBig(v: Int): Bytes {
        for (k in 3 downTo 0) datos[pos++] = (v ushr (8 * k)).toByte()
        return this
    }

    fun intLittle(v: Int): Bytes {
        for (k in 0..3) datos[pos++] = (v ushr (8 * k)).toByte()
        return this
    }

    fun shortLittle(v: Int): Bytes {
        datos[pos++] = v.toByte()
        datos[pos++] = (v ushr 8).toByte()
        return this
    }

    fun doubleLittle(v: Double): Bytes {
        val bits = v.toRawBits()
        for (k in 0..7) datos[pos++] = (bits ushr (8 * k)).toByte()
        return this
    }
}
