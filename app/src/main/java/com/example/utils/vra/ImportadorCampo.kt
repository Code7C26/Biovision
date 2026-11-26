package com.example.utils.vra

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Lee archivos del campo elegidos con el selector del sistema: CSV/TXT (planilla o laboratorio) o
 * un shapefile en ZIP (.shp + .dbf + .prj, como lo exportan los monitores de rinde).
 */
object ImportadorCampo {

    /** Un ZIP o un CSV más grande que esto no es un mapa de un lote (y podría trabar el teléfono). */
    private const val MAX_BYTES = 150L * 1024 * 1024

    fun leer(context: Context, uri: Uri): Result<Pair<String, TablaCampo>> = runCatching {
        val nombre = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val bytes = context.contentResolver.openInputStream(uri)?.use { leerAcotado(it) }
            ?: throw ErrorArchivoCampo("No se pudo abrir el archivo.")
        nombre to leer(nombre, bytes)
    }.recoverCatching { e ->
        throw if (e is ErrorArchivoCampo) e else ErrorArchivoCampo("No se pudo leer el archivo: ${e.message}")
    }

    fun leer(nombre: String, bytes: ByteArray): TablaCampo {
        val esZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!esZip) {
            if (nombre.lowercase().endsWith(".shp")) {
                throw ErrorArchivoCampo("Un shapefile son varios archivos: comprimí el .shp, el .dbf y el .prj en un ZIP y elegí el ZIP.")
            }
            return LectorCsv.leer(texto(bytes))
        }
        val archivos = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            while (true) {
                val entrada = zip.nextEntry ?: break
                val nombreEntrada = entrada.name.substringAfterLast('/').lowercase()
                val ext = nombreEntrada.substringAfterLast('.', "")
                if (!entrada.isDirectory && ext in setOf("shp", "dbf", "prj", "csv", "txt") && !nombreEntrada.startsWith("._")) {
                    val contenido = leerAcotado(zip, MAX_BYTES - total)
                    total += contenido.size
                    // Si hay varios, el primero de cada tipo (los monitores exportan un mapa por ZIP)
                    archivos.putIfAbsent(ext, contenido)
                }
            }
        }
        archivos["shp"]?.let { shp ->
            if (archivos["dbf"] == null) throw ErrorArchivoCampo("Al ZIP le falta el .dbf con los valores de cada punto.")
            return LectorShapefile.leer(shp, archivos["dbf"], archivos["prj"]?.let(::texto))
        }
        val csv = archivos["csv"] ?: archivos["txt"] ?: throw ErrorArchivoCampo("El ZIP no tiene un shapefile (.shp) ni un CSV.")
        return LectorCsv.leer(texto(csv))
    }

    /** UTF-8; si no lo es (planillas viejas o del laboratorio), Latin-1. */
    private fun texto(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if ('�' in utf8) bytes.toString(Charsets.ISO_8859_1) else utf8
    }

    private fun leerAcotado(entrada: java.io.InputStream, maximo: Long = MAX_BYTES): ByteArray {
        val salida = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = entrada.read(buffer)
            if (n < 0) break
            total += n
            if (total > maximo) throw ErrorArchivoCampo("El archivo es demasiado grande para un lote.")
            salida.write(buffer, 0, n)
        }
        return salida.toByteArray()
    }
}
