package com.example.data.repository

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

/** Nota leída de un feed, antes de formatear la fecha para la pantalla. */
data class ItemRss(
    val titulo: String,
    val descripcion: String,
    val enlace: String,
    val imagen: String?,
    val categorias: List<String>,
    val timestamp: Long
)

/**
 * Lector de RSS 2.0 de los medios de Novedades. Usa el parser XML de Jsoup: tolera feeds algo
 * malformados y no resuelve entidades externas (sin XXE). Puro: testeado en NoticiasTest.
 */
object LectorRss {

    /** Notas patrocinadas o publirreportajes: no son información periodística. */
    private val MARCAS_PUBLICIDAD = listOf(
        "patrocinado", "contenido de marca", "espacio de marca", "publinota", "advertorial",
        "sponsored", "brandstudio", "brand-studio", "contenido-patrocinado", "/espacio-publicitario"
    )

    private val FORMATOS_FECHA = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun leer(xml: String, fuente: FuenteNoticias): List<ItemRss> =
        leer(Jsoup.parse(xml, "", Parser.xmlParser()), fuente)

    /** Desde los bytes: Jsoup toma el charset de la declaración XML (algunos medios usan ISO-8859-1). */
    fun leer(bytes: java.io.InputStream, fuente: FuenteNoticias): List<ItemRss> =
        leer(Jsoup.parse(bytes, null, "", Parser.xmlParser()), fuente)

    private fun leer(doc: Document, fuente: FuenteNoticias): List<ItemRss> =
        doc.select("item").mapNotNull { leerItem(it, fuente) }

    private fun leerItem(item: Element, fuente: FuenteNoticias): ItemRss? {
        val titulo = textoPlano(hijo(item, "title")).takeIf { it.length >= 8 } ?: return null
        val enlace = enlaceSeguro(hijo(item, "link").ifBlank { hijo(item, "guid") }, fuente.dominio) ?: return null
        val descripcionHtml = hijo(item, "description")
        val contenidoHtml = item.getElementsByTag("content:encoded").firstOrNull()?.text().orEmpty()
        val categorias = item.getElementsByTag("category").map { it.text().trim() }.filter { it.isNotEmpty() }
        val descripcion = textoPlano(descripcionHtml).let { if (it.length > 400) it.take(397).trimEnd() + "…" else it }

        val textoPublicidad = (titulo + " " + enlace + " " + categorias.joinToString(" ")).lowercase()
        if (MARCAS_PUBLICIDAD.any { it in textoPublicidad }) return null

        return ItemRss(
            titulo = titulo,
            descripcion = descripcion,
            enlace = enlace,
            imagen = imagen(item, descripcionHtml, contenidoHtml),
            categorias = categorias,
            timestamp = fecha(hijo(item, "pubDate").ifBlank { hijo(item, "dc:date") })
        )
    }

    private fun hijo(item: Element, tag: String): String =
        item.getElementsByTag(tag).firstOrNull()?.text()?.trim().orEmpty()

    /** Portada: <enclosure> de imagen, <media:content>/<media:thumbnail> o la primera <img> del HTML. */
    private fun imagen(item: Element, descripcionHtml: String, contenidoHtml: String): String? {
        val enclosure = item.getElementsByTag("enclosure")
            .firstOrNull { it.attr("type").startsWith("image") || it.attr("url").let(::pareceImagen) }
            ?.attr("url")
        val media = (item.getElementsByTag("media:content") + item.getElementsByTag("media:thumbnail"))
            .firstOrNull { it.attr("medium").let { m -> m.isEmpty() || m == "image" } && it.attr("url").isNotBlank() }
            ?.attr("url")
        val enHtml = sequenceOf(contenidoHtml, descripcionHtml)
            .mapNotNull { html -> Jsoup.parse(html).selectFirst("img[src]")?.attr("src") }
            .firstOrNull()
        return sequenceOf(enclosure, media, enHtml)
            .filterNotNull()
            .map { urlImagenSegura(it) }
            .firstOrNull { it != null }
    }

    private fun pareceImagen(url: String) = Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** HTML de la bajada → texto (sin etiquetas, entidades resueltas, espacios colapsados). */
    fun textoPlano(html: String): String =
        if (html.isBlank()) "" else Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()

    /**
     * Enlace de la nota: https y del dominio del medio (o un subdominio). Cualquier otro host
     * (acortadores, redirecciones, publicidad) se descarta.
     */
    fun enlaceSeguro(url: String, dominio: String): String? {
        val limpia = NewsArticle.cleanUrl(url.trim())
        val uri = try { URI(limpia) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme != "https" || !(host == dominio || host.endsWith(".$dominio"))) return null
        return limpia
    }

    /** Imagen de portada: cualquier CDN, pero siempre https (la app bloquea HTTP en claro). */
    fun urlImagenSegura(url: String): String? {
        if (url.isBlank() || url.startsWith("data:")) return null
        val limpia = NewsArticle.cleanUrl(url.trim().replace("&amp;", "&"))
        val host = try { URI(limpia.replace(" ", "%20")).host } catch (e: Exception) { null }
        return limpia.takeIf { !host.isNullOrBlank() }
    }

    fun fecha(texto: String): Long {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return 0L
        for (formato in FORMATOS_FECHA) {
            try {
                val sdf = SimpleDateFormat(formato, Locale.ENGLISH).apply { isLenient = false }
                sdf.parse(limpio)?.let { return it.time }
            } catch (e: Exception) {
                // siguiente formato
            }
        }
        return 0L
    }
}
