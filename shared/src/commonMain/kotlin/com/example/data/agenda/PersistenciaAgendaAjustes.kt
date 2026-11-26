package com.example.data.agenda

import com.example.data.remote.supabase.ArchivosLotes
import com.russhwolf.settings.Settings

/**
 * La Agenda en los ajustes del dispositivo (en la web, localStorage), bajo una sola clave. Es la copia
 * local para usarla sin conexión; la de la cuenta está en Storage (SincronizadorAgenda).
 */
class PersistenciaAgendaAjustes(private val ajustes: Settings, private val clave: String = CLAVE) : PersistenciaAgenda {
    override suspend fun leer(): String? = ajustes.getStringOrNull(clave)
    override suspend fun escribir(texto: String) = ajustes.putString(clave, texto)
    override suspend fun borrar() = ajustes.remove(clave)

    companion object {
        const val CLAVE = "agenda_v1"
    }
}

/** La carpeta del usuario en Storage vista como [ArchivosAgenda]. */
fun ArchivosLotes.comoArchivosAgenda(): ArchivosAgenda {
    val archivos = this
    return object : ArchivosAgenda {
        override suspend fun listar(prefijo: String) = archivos.listar(prefijo)
        override suspend fun subir(nombre: String, contenido: ByteArray) { archivos.subir(nombre, contenido) }
        override suspend fun bajar(nombre: String) = archivos.descargar(nombre)
        override suspend fun borrar(nombre: String) = archivos.borrar(nombre)
    }
}
