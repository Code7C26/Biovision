package com.example.utils.vra

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escritura con el Storage Access Framework: el usuario elige la carpeta (la raíz de un pendrive
 * conectado por OTG, Descargas, Drive…) y la app no necesita permisos de almacenamiento.
 */
object GuardadoArchivos {

    /** Escribe [archivos] dentro de la carpeta [arbol] (OpenDocumentTree), reemplazando los que tengan el mismo nombre. */
    suspend fun enCarpeta(context: Context, arbol: Uri, archivos: List<ArchivoExportado>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val raiz = DocumentsContract.buildDocumentUriUsingTree(arbol, DocumentsContract.getTreeDocumentId(arbol))
            archivos.forEach { archivo ->
                val partes = archivo.ruta.split('/')
                var carpeta = raiz
                partes.dropLast(1).forEach { nombre ->
                    carpeta = hijos(context, arbol, carpeta)[nombre]
                        ?: DocumentsContract.createDocument(resolver, carpeta, DocumentsContract.Document.MIME_TYPE_DIR, nombre)
                        ?: error("No se pudo crear la carpeta $nombre")
                }
                val nombre = partes.last()
                // Si ya existe se borra: si no, el sistema crearía "nombre (1)" y el monitor no lo reconocería
                hijos(context, arbol, carpeta)[nombre]?.let { DocumentsContract.deleteDocument(resolver, it) }
                val destino = DocumentsContract.createDocument(resolver, carpeta, "application/octet-stream", nombre)
                    ?: error("No se pudo crear $nombre")
                resolver.openOutputStream(destino, "w")?.use { it.write(archivo.bytes) } ?: error("No se pudo escribir $nombre")
            }
            archivos.size
        }
    }

    /** Escribe [bytes] en el documento elegido con CreateDocument. */
    suspend fun enDocumento(context: Context, documento: Uri, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(documento, "wt")?.use { it.write(bytes) } ?: error("No se pudo escribir el archivo")
        }
    }

    private fun hijos(context: Context, arbol: Uri, carpeta: Uri): Map<String, Uri> {
        val lista = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, DocumentsContract.getDocumentId(carpeta))
        val columnas = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val resultado = HashMap<String, Uri>()
        context.contentResolver.query(lista, columnas, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                resultado[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(arbol, c.getString(0))
            }
        }
        return resultado
    }
}
