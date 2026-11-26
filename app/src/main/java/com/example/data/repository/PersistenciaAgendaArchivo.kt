package com.example.data.repository

import com.example.data.agenda.PersistenciaAgenda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La Agenda en `filesDir/agenda/agenda.json` (excluida de las copias de seguridad, como los datos
 * agronómicos, y borrada al cerrar sesión). Escribe en un temporal y lo renombra: un corte de energía
 * a mitad de camino deja la versión anterior, no un archivo a medias.
 */
class PersistenciaAgendaArchivo(private val carpeta: File) : PersistenciaAgenda {
    private val archivo get() = File(carpeta, "agenda.json")

    override suspend fun leer(): String? = withContext(Dispatchers.IO) {
        archivo.takeIf { it.exists() }?.readText()
    }

    override suspend fun escribir(texto: String) = withContext(Dispatchers.IO) {
        carpeta.mkdirs()
        val temporal = File(carpeta, "agenda.json.tmp")
        temporal.writeText(texto)
        if (!temporal.renameTo(archivo)) {
            archivo.delete()
            check(temporal.renameTo(archivo)) { "No se pudo guardar la agenda" }
        }
    }

    override suspend fun borrar() {
        withContext(Dispatchers.IO) { carpeta.deleteRecursively() }
    }
}
