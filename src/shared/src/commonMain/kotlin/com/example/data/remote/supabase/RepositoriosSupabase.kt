package com.example.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage

/** Lado remoto de la sincronización de lotes (interfaz: los tests usan una implementación en memoria). */
interface RemotoLotes {
    /** Sube versiones locales. El servidor se queda con la de mayor `modificado_en` (trigger). */
    suspend fun subir(lotes: List<LoteRemoto>)

    /** Filas del usuario con `actualizado_en` >= [desde] (todas si es null), de la más vieja a la más nueva. */
    suspend fun cambiosDesde(desde: String?): List<LoteRemoto>
}

/** Tabla `lotes` por Postgrest. RLS limita cada consulta a las filas del usuario con sesión. */
class RemotoLotesSupabase(private val supabase: SupabaseClient) : RemotoLotes {

    override suspend fun subir(lotes: List<LoteRemoto>) {
        lotes.chunked(TAMANO_LOTE).forEach { tanda ->
            supabase.from(TablasSupabase.LOTES).upsert(tanda) {
                onConflict = "id"
                // Una columna que no se envía conserva su valor por defecto, no pasa a null
                defaultToNull = false
            }
        }
    }

    override suspend fun cambiosDesde(desde: String?): List<LoteRemoto> {
        val resultado = mutableListOf<LoteRemoto>()
        var desdeFila = 0L
        while (true) {
            val pagina = supabase.from(TablasSupabase.LOTES).select {
                if (desde != null) filter { gte("actualizado_en", desde) }
                order("actualizado_en", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(desdeFila, desdeFila + TAMANO_PAGINA - 1)
            }.decodeList<LoteRemoto>()
            resultado += pagina
            if (pagina.size < TAMANO_PAGINA) return resultado
            desdeFila += TAMANO_PAGINA
        }
    }

    private companion object {
        const val TAMANO_LOTE = 200
        const val TAMANO_PAGINA = 500L
    }
}

/** Perfil del productor (tabla `perfiles`). */
class RepositorioPerfil(private val supabase: SupabaseClient) {

    /** Perfil del usuario con sesión; null si todavía no lo cargó o no hay sesión. */
    suspend fun miPerfil(): PerfilRemoto? {
        val id = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.from(TablasSupabase.PERFILES).select {
            filter { eq("id", id) }
        }.decodeSingleOrNull<PerfilRemoto>()
    }

    suspend fun guardar(perfil: PerfilRemoto) {
        supabase.from(TablasSupabase.PERFILES).upsert(perfil) {
            onConflict = "id"
            defaultToNull = false
        }
    }
}

/**
 * Archivos de lotes en Storage (bucket privado `lotes`): GeoJSON, Shapefiles o prescripciones. Cada
 * usuario solo puede leer y escribir dentro de su carpeta (`{usuario_id}/…`, política de Storage).
 */
class ArchivosLotes(private val supabase: SupabaseClient) {

    suspend fun subir(nombreArchivo: String, contenido: ByteArray): String {
        val ruta = rutaPropia(nombreArchivo)
        supabase.storage.from(TablasSupabase.BUCKET_LOTES).upload(ruta, contenido) { upsert = true }
        return ruta
    }

    suspend fun descargar(nombreArchivo: String): ByteArray =
        supabase.storage.from(TablasSupabase.BUCKET_LOTES).downloadAuthenticated(rutaPropia(nombreArchivo))

    suspend fun borrar(nombreArchivo: String) {
        supabase.storage.from(TablasSupabase.BUCKET_LOTES).delete(rutaPropia(nombreArchivo))
    }

    /** Nombres de los archivos de la carpeta propia que empiezan con [prefijo]. */
    suspend fun listar(prefijo: String): List<String> {
        val usuario = supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Sin sesión")
        val nombres = mutableListOf<String>()
        var desde = 0
        while (true) {
            val pagina = supabase.storage.from(TablasSupabase.BUCKET_LOTES).list(usuario) {
                search = prefijo
                limit = TAMANO_PAGINA
                offset = desde
            }
            nombres += pagina.map { it.name }.filter { it.startsWith(prefijo) }
            if (pagina.size < TAMANO_PAGINA) return nombres
            desde += TAMANO_PAGINA
        }
    }

    private fun rutaPropia(nombreArchivo: String): String {
        val usuario = supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Sin sesión")
        return rutaArchivo(usuario, nombreArchivo)
    }

    companion object {
        private const val TAMANO_PAGINA = 1000

        /** `{usuario}/{archivo}`, sin barras ni `..` en el nombre (no se puede salir de la carpeta propia). */
        fun rutaArchivo(usuarioId: String, nombreArchivo: String): String {
            val limpio = nombreArchivo.trim()
            require(limpio.isNotEmpty() && '/' !in limpio && '\\' !in limpio && limpio != "." && limpio != "..") {
                "Nombre de archivo inválido"
            }
            return "$usuarioId/$limpio"
        }
    }
}
