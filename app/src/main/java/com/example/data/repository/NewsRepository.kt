package com.example.data.repository

import android.util.Log
import com.example.data.remote.api.RetrofitNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class NewsArticle(
    val id: String,
    val title: String,
    val description: String,
    val sourceName: String,
    val articleUrl: String,
    /** Etiqueta visible ("Maquinaria", "Economía", "Clima"…); también elige la imagen de respaldo. */
    val category: String,
    val formattedTime: String,
    val timestamp: Long = 0L,
    val imageUrl: String? = null,
    val temas: Set<TemaNoticia> = emptySet()
) {
    companion object {
        fun cleanUrl(url: String): String {
            var cleanUrl = url.trim()
            if (cleanUrl.startsWith("http://")) {
                // La app bloquea tráfico HTTP sin cifrar (network_security_config.xml)
                cleanUrl = "https://" + cleanUrl.removePrefix("http://")
            } else if (cleanUrl.startsWith("//")) {
                cleanUrl = "https:$cleanUrl"
            } else if (!cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            return cleanUrl
        }
    }
}

/** Noticias reales más qué medios respondieron. Nunca contiene notas inventadas. */
data class ResultadoNoticias(
    val articulos: List<NewsArticle>,
    val fuentesOk: List<String>,
    val fuentesConError: List<String>
)

/**
 * Noticias de los medios de [FUENTES_NOTICIAS] (RSS oficiales), descargadas en paralelo. Si un medio
 * falla se sigue con el resto; si fallan todos se lanza IOException (la pantalla muestra el error y
 * "Reintentar"): no hay datos de ejemplo, para que todo lo que se lee sea verídico.
 */
class NewsRepository {

    // Sin el interceptor de logging del cliente común: los feeds pesan hasta 3 MB y no aportan al log
    private val cliente: OkHttpClient by lazy {
        RetrofitNetworkClient.okHttpClient.newBuilder()
            .apply { interceptors().clear() }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** og:image ya buscadas (url de la nota → imagen, "" si la página no tiene). */
    private val imagenesOg = ConcurrentHashMap<String, String>()

    suspend fun obtenerNoticias(): ResultadoNoticias = coroutineScope {
        val resultados = FUENTES_NOTICIAS.map { fuente ->
            async(Dispatchers.IO) { fuente to runCatching { leerFuente(fuente) } }
        }.awaitAll()

        resultados.filter { it.second.isFailure }.forEach { (fuente, r) ->
            Log.w(TAG, "${fuente.nombre} no respondió: ${r.exceptionOrNull()?.message}")
        }
        val fuentesOk = resultados.filter { it.second.isSuccess }.map { it.first.nombre }
        if (fuentesOk.isEmpty()) throw IOException("No se pudo conectar con ningún medio. Revisá la conexión a internet.")

        val ahora = System.currentTimeMillis()
        val articulos = resultados
            .flatMap { (fuente, r) -> r.getOrDefault(emptyList()).map { aArticulo(it, fuente, ahora) } }
            .filter { it.timestamp == 0L || ahora - it.timestamp <= ANTIGUEDAD_MAXIMA_MS }
            .distinctBy { it.id }
            // La misma nota levantada por dos medios (o repetida en un feed) se muestra una vez
            .distinctBy { ClasificadorNoticias.normalizar(it.title).filter(Char::isLetterOrDigit) }
            .sortedByDescending { it.timestamp }

        ResultadoNoticias(
            articulos = completarImagenes(articulos),
            fuentesOk = fuentesOk,
            fuentesConError = resultados.filter { it.second.isFailure }.map { it.first.nombre }
        )
    }

    /** Lo usa el aviso de noticias nuevas en segundo plano (AlertasWorker). */
    suspend fun getRealTimeNews(): List<NewsArticle> = obtenerNoticias().articulos

    private fun leerFuente(fuente: FuenteNoticias): List<ItemRss> {
        val request = Request.Builder()
            .url(fuente.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()
        cliente.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Respuesta vacía")
            return LectorRss.leer(body.byteStream(), fuente)
                .sortedByDescending { it.timestamp }
                .take(fuente.maxNotas)
        }
    }

    private fun aArticulo(item: ItemRss, fuente: FuenteNoticias, ahora: Long): NewsArticle {
        // Fechas en el futuro (zona horaria mal declarada en el feed) se muestran como "recién"
        val ts = item.timestamp.coerceAtMost(ahora)
        return NewsArticle(
            id = item.enlace,
            title = item.titulo,
            description = item.descripcion,
            sourceName = fuente.nombre,
            articleUrl = item.enlace,
            category = ClasificadorNoticias.categoria(item.titulo, item.descripcion, fuente.rubro),
            formattedTime = getRelativeTimeSpanString(ts),
            timestamp = ts,
            imageUrl = item.imagen,
            temas = ClasificadorNoticias.temas(item.titulo, item.descripcion, item.categorias, fuente.rubro)
        )
    }

    /** Las notas sin imagen en el feed toman la og:image de su página (con tope, en paralelo). */
    private suspend fun completarImagenes(articulos: List<NewsArticle>): List<NewsArticle> = coroutineScope {
        val sinImagen = articulos.filter { it.imageUrl == null }.take(MAX_BUSQUEDAS_OG).map { it.id }.toSet()
        val encontradas = sinImagen.map { url -> async(Dispatchers.IO) { url to imagenOg(url) } }.awaitAll().toMap()
        articulos.map { a -> encontradas[a.id]?.let { a.copy(imageUrl = it) } ?: a }
    }

    private suspend fun imagenOg(urlNota: String): String? = withContext(Dispatchers.IO) {
        imagenesOg[urlNota]?.let { return@withContext it.ifEmpty { null } }
        val imagen = try {
            Jsoup.connect(urlNota)
                .userAgent(USER_AGENT)
                .timeout(6_000)
                .maxBodySize(600 * 1024)
                .get()
                .selectFirst("meta[property=og:image], meta[name=twitter:image]")
                ?.attr("content")
                ?.let(LectorRss::urlImagenSegura)
        } catch (e: Exception) {
            null
        }
        imagenesOg[urlNota] = imagen.orEmpty()
        imagen
    }

    fun getRelativeTimeSpanString(timestamp: Long): String {
        if (timestamp <= 0L) return "Fecha desconocida"
        val now = System.currentTimeMillis()
        if (now - timestamp < 60_000L) return "Hace unos instantes"
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    private companion object {
        const val TAG = "GranexNoticias"
        // Varios medios rechazan clientes que no se identifican como navegador
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        const val ANTIGUEDAD_MAXIMA_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_BUSQUEDAS_OG = 15
    }
}
