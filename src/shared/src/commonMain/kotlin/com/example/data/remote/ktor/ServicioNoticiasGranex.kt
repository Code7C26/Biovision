package com.example.data.remote.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.Serializable

/*
 * Noticias de los medios de FUENTES_NOTICIAS ya leídas y filtradas por la función "publico" de GRANEX
 * (supabase/functions/publico), con las mismas reglas que LectorRss en Android: los RSS no responden
 * con CORS, así que el navegador no puede leerlos directo.
 */

@Serializable
data class NotaPublica(
    val titulo: String,
    val resumen: String = "",
    val enlace: String,
    val imagen: String? = null,
    val categorias: List<String> = emptyList(),
    /** Milisegundos desde 1970; 0 si el medio no informó la fecha. */
    val fecha: Long = 0,
    val fuente: String,
    /** "AGRO" o "ECONOMIA", como RubroFuente. */
    val rubro: String = "AGRO"
)

@Serializable
data class NoticiasPublicas(
    val generado: String = "",
    val notas: List<NotaPublica> = emptyList(),
    /** Medios que no respondieron en esta lectura. */
    val fallidas: List<String> = emptyList()
)

class ServicioNoticiasGranex(
    private val http: HttpClient,
    /** URL de la función: "https://<proyecto>.supabase.co/functions/v1/publico". */
    private val base: String,
    private val cabeceras: Map<String, String>
) {
    suspend fun noticias(): NoticiasPublicas =
        http.get("$base/noticias") { cabeceras.forEach { (nombre, valor) -> header(nombre, valor) } }.body()
}
