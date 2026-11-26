package com.example

import com.example.utils.vra.ArchivoExportado
import com.example.utils.vra.CampoDbf
import com.example.utils.vra.EscritorShapefile
import com.example.utils.vra.ExportacionPrescripcion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exportación en Kotlin común (la usan Android y la web): bytes del shapefile y ZIP legible. */
class ExportacionComunTest {

    private fun u32Little(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)

    private fun u32Big(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    @Test
    fun shapefileConEncabezadosYTablaCorrectos() {
        val cuadrado = listOf(-33.0 to -61.0, -33.0 to -60.99, -33.01 to -60.99, -33.01 to -61.0, -33.0 to -61.0)
        val archivos = EscritorShapefile.escribir(
            listOf(listOf(cuadrado)),
            listOf(CampoDbf("ZONA", 'N', 3), CampoDbf("DOSIS", 'N', 7, 2), CampoDbf("PRODUCTO", 'C', 10)),
            listOf(listOf(1, 120.5, "Urea Ñandú"))
        )
        val shp = archivos.getValue("shp")
        assertEquals(9994, u32Big(shp, 0), "código de archivo shapefile")
        assertEquals(shp.size / 2, u32Big(shp, 24), "largo en palabras de 16 bits")
        assertEquals(1000, u32Little(shp, 28), "versión")
        assertEquals(5, u32Little(shp, 32), "tipo polígono")
        assertEquals(-61.0, Double.fromBits(shp.copyOfRange(36, 44).foldIndexed(0L) { k, acc, b -> acc or ((b.toLong() and 0xFF) shl (8 * k)) }), "xmin = longitud oeste")
        val dbf = archivos.getValue("dbf")
        assertEquals(0x03, dbf[0].toInt(), "dBase III")
        assertEquals(1, u32Little(dbf, 4), "un registro")
        val registro = dbf.decodeToString(dbf.size - 1 - 21, dbf.size - 1)
        assertEquals("   1 120.50Urea Nandu", registro, "números a la derecha con punto decimal, texto sin tildes")
        assertTrue(archivos.getValue("prj").decodeToString().startsWith("GEOGCS[\"GCS_WGS_1984\""), "proyección WGS84")
    }

    @Test
    fun zipConCrcYDirectorioCentral() {
        val datos = "123456789".encodeToByteArray()
        val zip = ExportacionPrescripcion.zip(listOf(ArchivoExportado("A/uno.txt", datos), ArchivoExportado("dos.bin", byteArrayOf(1, 2, 3))))
        assertEquals(0x04034b50, u32Little(zip, 0), "encabezado local del primer archivo")
        assertEquals(0xCBF43926.toInt(), u32Little(zip, 14), "CRC-32 de \"123456789\"")
        val fin = zip.size - 22
        assertEquals(0x06054b50, u32Little(zip, fin), "fin del directorio central")
        assertEquals(2, (zip[fin + 10].toInt() and 0xFF), "dos archivos")
        val inicioCentral = u32Little(zip, fin + 16)
        assertEquals(0x02014b50, u32Little(zip, inicioCentral), "el directorio central empieza donde dice el final")
    }

    @Test
    fun nombreCortoParaCuffia() {
        assertEquals("RXESTEAL", ExportacionPrescripcion.nombreCorto("Lote Este Álamo"), "8 letras, sin la palabra lote ni tildes")
    }
}
