package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.remote.api.RetrofitNetworkClient
import com.example.data.remote.supabase.ErrorSentinelGranex
import com.example.data.remote.supabase.ImagenSentinelRemota
import com.example.data.remote.supabase.SentinelGranex
import com.example.utils.GeometriaLote
import com.example.utils.RelieveLote
import com.example.utils.WeatherNotificationService
import com.example.utils.vra.Campana
import com.example.utils.vra.CampanaNdvi
import com.example.utils.vra.EscenaSentinel
import com.example.utils.vra.GrillaGeografica
import com.example.utils.vra.ImagenIndices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.cos

/**
 * Origen de las imágenes Sentinel-2 para prescripciones. Hoy lo implementa [SentinelHubCdse] con la
 * cuenta de Copernicus del usuario; a futuro puede reemplazarse por un backend propio (las
 * credenciales quedarían en el servidor) sin cambiar el ViewModel ni la pantalla.
 */
interface FuenteSentinel {
    fun configurada(): Boolean

    /** true si las imágenes vienen del backend de GRANEX (hace falta sesión, no cuenta de Copernicus). */
    val usaCuentaGranex: Boolean get() = false

    /** Qué tiene que hacer el usuario cuando [configurada] es false. */
    val mensajeSinConfigurar: String get() = SentinelHubCdse.SIN_CREDENCIALES

    /** Fechas con imagen sobre el lote en los últimos [dias] días, con sus promedios sin nubes. */
    suspend fun escenas(lote: LoteSatelital, dias: Int = 120): Result<List<EscenaSentinel>>

    /** NDVI, NDRE y GNDVI por píxel (10 m) de la fecha [fecha] (yyyy-MM-dd), recortados al lote. */
    suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices>

    /** Pico de vigor (NDVI) de cada píxel en la [campana], para las zonas de varias campañas. */
    suspend fun campana(lote: LoteSatelital, campana: Campana): Result<CampanaNdvi> =
        Result.failure(ErrorSentinel("Las zonas de varias campañas necesitan el servidor de GRANEX: iniciá sesión en Ajustes."))

    /** Foto en color real de la [fecha] (PNG con transparencia), con un margen alrededor del lote. */
    suspend fun colorReal(lote: LoteSatelital, fecha: String): Result<ImagenSentinelRemota> =
        Result.failure(ErrorSentinel("La foto del día necesita el servidor de GRANEX: iniciá sesión en Ajustes."))

    /** Altura del terreno del lote (Copernicus DEM, 10 m). */
    suspend fun relieve(lote: LoteSatelital): Result<RelieveLote> =
        Result.failure(ErrorSentinel("El relieve necesita el servidor de GRANEX: iniciá sesión en Ajustes."))
}

class ErrorSentinel(mensaje: String) : Exception(mensaje)

/** Sentinel Hub en Copernicus Data Space Ecosystem, con el cliente OAuth que el usuario carga en Ajustes. */
class SentinelHubCdse(private val context: Context) : FuenteSentinel {

    // Sin el interceptor de logging: en debug registraría el client_secret del pedido de token
    private val http: OkHttpClient = RetrofitNetworkClient.okHttpClient.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .build()

    private val mutexToken = Mutex()
    private var token: String? = null
    private var tokenVence = 0L
    private var tokenCliente: String? = null

    override fun configurada(): Boolean = credenciales() != null

    private fun credenciales(): Pair<String, String>? {
        val id = WeatherNotificationService.getCopernicusClientId(context).trim()
        val secreto = WeatherNotificationService.getCopernicusClientSecret(context).trim()
        return if (id.isNotEmpty() && secreto.isNotEmpty()) id to secreto else null
    }

    private suspend fun obtenerToken(): String = mutexToken.withLock {
        val (id, secreto) = credenciales() ?: throw ErrorSentinel(SIN_CREDENCIALES)
        val vigente = token
        if (vigente != null && tokenCliente == id && System.currentTimeMillis() < tokenVence) return vigente
        val pedido = Request.Builder()
            .url(URL_TOKEN)
            .post(FormBody.Builder().add("grant_type", "client_credentials").add("client_id", id).add("client_secret", secreto).build())
            .build()
        http.newCall(pedido).execute().use { r ->
            val cuerpo = r.body?.string().orEmpty()
            if (r.code == 400 || r.code == 401) throw ErrorSentinel("Copernicus rechazó las credenciales. Revisa el client ID y el secret en Ajustes.")
            if (!r.isSuccessful) throw ErrorSentinel("El servicio de Copernicus no respondió (código ${r.code}).")
            val json = JSONObject(cuerpo)
            val nuevo = json.getString("access_token")
            token = nuevo
            tokenCliente = id
            tokenVence = System.currentTimeMillis() + (json.optLong("expires_in", 300) - 60).coerceAtLeast(30) * 1000
            nuevo
        }
    }

    private suspend fun post(url: String, cuerpo: JSONObject, acepta: String): ByteArray {
        repeat(2) { intento ->
            val pedido = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${obtenerToken()}")
                .header("Accept", acepta)
                .post(cuerpo.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(pedido).execute().use { r ->
                val bytes = r.body?.bytes() ?: ByteArray(0)
                when {
                    r.isSuccessful -> return bytes
                    r.code == 401 && intento == 0 -> mutexToken.withLock { token = null } // token vencido: se pide otro
                    r.code == 401 -> throw ErrorSentinel("Copernicus rechazó el acceso. Revisa las credenciales en Ajustes.")
                    r.code == 403 -> throw ErrorSentinel("Tu cuenta de Copernicus no tiene acceso a Sentinel Hub o se agotó la cuota mensual.")
                    r.code == 429 -> throw ErrorSentinel("Demasiados pedidos a Copernicus: espera un minuto e intenta de nuevo.")
                    else -> throw ErrorSentinel("Sentinel Hub respondió con un error (${r.code}): ${String(bytes).take(160)}")
                }
            }
        }
        throw ErrorSentinel("No se pudo autenticar con Copernicus.")
    }

    private fun limites(lote: LoteSatelital): Limites {
        val anillo = GeometriaLote.anilloAbierto(lote.coordenadas)
        return Limites(anillo.minOf { it.second }, anillo.minOf { it.first }, anillo.maxOf { it.second }, anillo.maxOf { it.first }, anillo)
    }

    private class Limites(val oeste: Double, val sur: Double, val este: Double, val norte: Double, val anillo: List<Pair<Double, Double>>) {
        val latMedia: Double get() = (norte + sur) / 2
        val metrosLon: Double get() = 111_412.84 * cos(Math.toRadians(latMedia))
    }

    private fun geometria(l: Limites): JSONObject {
        val ring = JSONArray().apply { (l.anillo + l.anillo.first()).forEach { (lat, lon) -> put(JSONArray().put(lon).put(lat)) } }
        return JSONObject()
            .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring)))
            .put("properties", JSONObject().put("crs", CRS84))
    }

    override suspend fun escenas(lote: LoteSatelital, dias: Int): Result<List<EscenaSentinel>> = withContext(Dispatchers.IO) {
        try {
            val l = limites(lote)
            val formato = SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val ahora = System.currentTimeMillis()
            val cuerpo = JSONObject()
                .put("input", JSONObject()
                    .put("bounds", geometria(l))
                    .put("data", JSONArray().put(JSONObject().put("type", "sentinel-2-l2a").put("dataFilter", JSONObject().put("maxCloudCoverage", 90)))))
                .put("aggregation", JSONObject()
                    .put("timeRange", JSONObject().put("from", formato.format(Date(ahora - dias * 86_400_000L))).put("to", formato.format(Date(ahora + 86_400_000L))))
                    .put("aggregationInterval", JSONObject().put("of", "P1D"))
                    .put("evalscript", EVALSCRIPT_ESTADISTICAS)
                    .put("resx", 20.0 / l.metrosLon)
                    .put("resy", 20.0 / 110_950.0))
                .put("calculations", JSONObject().put("default", JSONObject()))
            val json = JSONObject(String(post(URL_ESTADISTICAS, cuerpo, "application/json")))
            val datos = json.optJSONArray("data") ?: JSONArray()
            val crudas = (0 until datos.length()).mapNotNull { i ->
                val d = datos.getJSONObject(i)
                val fecha = d.getJSONObject("interval").getString("from").take(10)
                val bandas = d.optJSONObject("outputs")?.optJSONObject("indices")?.optJSONObject("bands") ?: return@mapNotNull null
                fun stats(b: String) = bandas.optJSONObject(b)?.optJSONObject("stats")
                val s0 = stats("B0") ?: return@mapNotNull null
                val validos = s0.optInt("sampleCount") - s0.optInt("noDataCount")
                if (validos <= 0) return@mapNotNull null
                fun media(b: String) = stats(b)?.optDouble("mean")?.takeIf { it.isFinite() }
                EscenaSentinel(fecha, 0.0, media("B0"), media("B1"), media("B2"), validos)
            }
            // % sin dato respecto del día más despejado (el conteo incluye los píxeles fuera del polígono)
            val referencia = crudas.maxOfOrNull { it.pixelesValidos } ?: 0
            Result.success(
                crudas.map { it.copy(nubesPct = if (referencia == 0) 100.0 else 100.0 * (1 - it.pixelesValidos.toDouble() / referencia)) }
                    .sortedByDescending { it.fecha }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(traducir(e))
        }
    }

    override suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices> = withContext(Dispatchers.IO) {
        try {
            val l = limites(lote)
            val ancho = ceil((l.este - l.oeste) * l.metrosLon / 10.0).toInt().coerceIn(1, 2500)
            val alto = ceil((l.norte - l.sur) * 110_950.0 / 10.0).toInt().coerceIn(1, 2500)
            val cuerpo = JSONObject()
                .put("input", JSONObject()
                    .put("bounds", geometria(l))
                    .put("data", JSONArray().put(JSONObject()
                        .put("type", "sentinel-2-l2a")
                        .put("dataFilter", JSONObject()
                            .put("timeRange", JSONObject().put("from", "${fecha}T00:00:00Z").put("to", "${fecha}T23:59:59Z"))
                            .put("mosaickingOrder", "leastCC")))))
                .put("output", JSONObject()
                    .put("width", ancho)
                    .put("height", alto)
                    .put("responses", JSONArray().put(JSONObject().put("identifier", "default").put("format", JSONObject().put("type", "image/png")))))
                .put("evalscript", EVALSCRIPT_IMAGEN)
            val png = post(URL_PROCESO, cuerpo, "image/png")
            Result.success(decodificarImagen(png, ancho, alto, l.oeste, l.sur, l.este, l.norte, fecha))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(traducir(e))
        }
    }

    private fun traducir(e: Exception): Exception = when (e) {
        is ErrorSentinel -> e
        is IOException -> ErrorSentinel("Sin conexión con Copernicus. Revisa tu conexión e intenta de nuevo.")
        is JSONException -> ErrorSentinel("No se pudo interpretar la respuesta de Sentinel Hub.")
        else -> ErrorSentinel(e.message ?: "Error al consultar Sentinel-2.")
    }

    companion object {
        const val SIN_CREDENCIALES = "Carga tu client ID y secret de Copernicus en Ajustes para usar imágenes Sentinel-2."
        private const val URL_TOKEN = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token"
        private const val URL_PROCESO = "https://sh.dataspace.copernicus.eu/process/v1"
        private const val URL_ESTADISTICAS = "https://sh.dataspace.copernicus.eu/api/v1/statistics"
        private const val CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84"

        // Píxel válido: vegetación (4) o suelo desnudo (5) según la clasificación de escena (SCL);
        // se descartan nubes, sombras, cirros, agua, nieve y píxeles saturados
        private const val VALIDEZ = "s.dataMask === 1 && (s.SCL === 4 || s.SCL === 5)"
        private const val BANDAS = "[\"B03\", \"B04\", \"B05\", \"B08\", \"B8A\", \"SCL\", \"dataMask\"]"
        private const val INDICES = """
  var ndvi = (s.B08 - s.B04) / (s.B08 + s.B04);
  var ndre = (s.B8A - s.B05) / (s.B8A + s.B05);
  var gndvi = (s.B08 - s.B03) / (s.B08 + s.B03);
  var ok = $VALIDEZ && isFinite(ndvi) && isFinite(ndre) && isFinite(gndvi);"""

        private const val EVALSCRIPT_ESTADISTICAS = """//VERSION=3
function setup() {
  return {
    input: [{ bands: $BANDAS }],
    output: [{ id: "indices", bands: 3, sampleType: "FLOAT32" }, { id: "dataMask", bands: 1 }]
  };
}
function evaluatePixel(s) {$INDICES
  return { indices: [ndvi, ndre, gndvi], dataMask: [ok ? 1 : 0] };
}"""

        // PNG de 8 bits: 0 = sin dato; 1..255 = índice de -1 a 1 (R = NDVI, G = NDRE, B = GNDVI)
        private const val EVALSCRIPT_IMAGEN = """//VERSION=3
function setup() {
  return { input: [{ bands: $BANDAS }], output: { bands: 3, sampleType: "UINT8" } };
}
function codificar(v) {
  return Math.round((Math.max(-1, Math.min(1, v)) + 1) / 2 * 254) + 1;
}
function evaluatePixel(s) {$INDICES
  if (!ok) return [0, 0, 0];
  return [codificar(ndvi), codificar(ndre), codificar(gndvi)];
}"""
    }
}

/** PNG de índices del evalscript (0 = sin dato, 1..255 = -1..1) → [ImagenIndices]. */
private fun decodificarImagen(
    png: ByteArray,
    ancho: Int,
    alto: Int,
    oeste: Double,
    sur: Double,
    este: Double,
    norte: Double,
    fecha: String
): ImagenIndices {
    val opciones = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPremultiplied = false
        inScaled = false
    }
    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, opciones)
        ?: throw ErrorSentinel("El servicio de imágenes no devolvió una imagen válida.")
    if (bitmap.width != ancho || bitmap.height != alto) {
        bitmap.recycle()
        throw ErrorSentinel("El servicio de imágenes devolvió una imagen de otro tamaño.")
    }
    val pixeles = IntArray(ancho * alto)
    bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
    bitmap.recycle()
    val imagen = ImagenIndices.desdePixeles(pixeles, ancho, alto, oeste, sur, este, norte, fecha)
    if (imagen.pixelesValidos == 0) throw ErrorSentinel("Ese día el lote estaba cubierto por nubes o sombras: elige otra fecha.")
    return imagen
}

/**
 * Imágenes por el backend de GRANEX (Edge Function `sentinel`): la cuenta de Copernicus queda en el
 * servidor. Hace falta sesión iniciada; cada pedido cuenta para el límite diario del usuario.
 */
class SentinelBackendGranex(
    private val remoto: SentinelGranex,
    /** Las campañas terminadas no cambian: se guardan acá y no se vuelven a pedir. null = sin caché. */
    private val carpetaCampanas: File? = null,
    private val haySesion: () -> Boolean
) : FuenteSentinel {

    override fun configurada(): Boolean = haySesion()

    override val usaCuentaGranex: Boolean get() = true

    override val mensajeSinConfigurar: String get() = SIN_SESION

    override suspend fun escenas(lote: LoteSatelital, dias: Int): Result<List<EscenaSentinel>> = withContext(Dispatchers.IO) {
        llamar { remoto.escenas(GeometriaLote.anilloAbierto(lote.coordenadas), dias) }
    }

    override suspend fun imagen(lote: LoteSatelital, fecha: String): Result<ImagenIndices> = withContext(Dispatchers.IO) {
        llamar {
            val r = remoto.imagen(GeometriaLote.anilloAbierto(lote.coordenadas), fecha)
            decodificarImagen(r.png, r.ancho, r.alto, r.oeste, r.sur, r.este, r.norte, r.fecha)
        }
    }

    override suspend fun campana(lote: LoteSatelital, campana: Campana): Result<CampanaNdvi> = withContext(Dispatchers.IO) {
        llamar {
            val anillo = GeometriaLote.anilloAbierto(lote.coordenadas)
            val archivo = carpetaCampanas?.let { File(it, "${anillo.hashCode().toUInt()}_${campana.desde}_${campana.hasta}_v2.txt") }
            val r = archivo?.let(::leerCampanaGuardada) ?: remoto.campana(anillo, campana.desde, campana.hasta).also { r ->
                archivo?.let { guardarCampana(it, r) }
            }
            decodificarCampana(r, campana)
        }
    }

    override suspend fun colorReal(lote: LoteSatelital, fecha: String): Result<ImagenSentinelRemota> = withContext(Dispatchers.IO) {
        llamar { remoto.colorReal(GeometriaLote.anilloAbierto(lote.coordenadas), fecha) }
    }

    /** El terreno no cambia: se guarda en la misma carpeta que las campañas y no se vuelve a pedir. */
    override suspend fun relieve(lote: LoteSatelital): Result<RelieveLote> = withContext(Dispatchers.IO) {
        llamar {
            val anillo = GeometriaLote.anilloAbierto(lote.coordenadas)
            val archivo = carpetaCampanas?.let { File(it, "relieve_${anillo.hashCode().toUInt()}_v1.txt") }
            val r = archivo?.let(::leerCampanaGuardada) ?: remoto.relieve(anillo).also { r ->
                archivo?.let { guardarCampana(it, r) }
            }
            decodificarRelieve(r)
        }
    }

    private fun leerCampanaGuardada(archivo: File): ImagenSentinelRemota? = runCatching {
        if (!archivo.exists()) return null
        val (cabecera, png) = archivo.readText().split('\n', limit = 2)
        val c = cabecera.split(';')
        ImagenSentinelRemota(c[0], c[1].toDouble(), c[2].toDouble(), c[3].toDouble(), c[4].toDouble(), c[5].toInt(), c[6].toInt(),
            android.util.Base64.decode(png, android.util.Base64.NO_WRAP))
    }.getOrNull()

    private fun guardarCampana(archivo: File, r: ImagenSentinelRemota) {
        runCatching {
            archivo.parentFile?.mkdirs()
            val temporal = File(archivo.path + ".tmp")
            temporal.writeText(
                listOf(r.fecha, r.oeste, r.sur, r.este, r.norte, r.ancho, r.alto).joinToString(";") + "\n" +
                    android.util.Base64.encodeToString(r.png, android.util.Base64.NO_WRAP)
            )
            temporal.renameTo(archivo)
        }.onFailure { Log.w(TAG, "No se pudo guardar la campaña en caché", it) }
    }

    private suspend fun <T> llamar(bloque: suspend () -> T): Result<T> = try {
        Result.success(bloque())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorSentinelGranex) {
        Result.failure(ErrorSentinel(e.message ?: "Error al consultar Sentinel-2."))
    } catch (e: ErrorSentinel) {
        Result.failure(e)
    } catch (e: Exception) {
        Log.w(TAG, "Respuesta inesperada del servidor de imágenes", e)
        Result.failure(ErrorSentinel("No se pudo interpretar la respuesta del servidor de imágenes."))
    }

    companion object {
        const val SIN_SESION = "Iniciá sesión en Ajustes → Cuenta para usar las imágenes Sentinel-2 de 10 m."
        private const val TAG = "GranexSentinel"
    }
}

/** PNG del relieve: altura = (R·256 + G) / 10 − 500 m; B = 255 con dato, 0 sin dato. */
private fun decodificarRelieve(r: ImagenSentinelRemota): RelieveLote {
    val opciones = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPremultiplied = false
        inScaled = false
    }
    val bitmap = BitmapFactory.decodeByteArray(r.png, 0, r.png.size, opciones)
        ?: throw ErrorSentinel("El servicio de imágenes no devolvió un relieve válido.")
    if (bitmap.width != r.ancho || bitmap.height != r.alto) {
        bitmap.recycle()
        throw ErrorSentinel("El servicio de imágenes devolvió un relieve de otro tamaño.")
    }
    val pixeles = IntArray(r.ancho * r.alto)
    bitmap.getPixels(pixeles, 0, r.ancho, 0, 0, r.ancho, r.alto)
    bitmap.recycle()
    val grilla = GrillaGeografica(r.oeste, r.norte, (r.este - r.oeste) / r.ancho, (r.norte - r.sur) / r.alto, r.ancho, r.alto)
    val altura = FloatArray(pixeles.size) { i ->
        val p = pixeles[i]
        if ((p and 0xFF) == 0) Float.NaN else ((((p shr 16) and 0xFF) * 256 + ((p shr 8) and 0xFF)) / 10f - 500f)
    }
    return RelieveLote(grilla, altura)
}

/** PNG de una campaña: R = NDVI pico (0 = sin dato, 1..255 = -1..1), G = pasadas sin nubes, B = NDRE pico. */
private fun decodificarCampana(r: ImagenSentinelRemota, campana: Campana): CampanaNdvi {
    val opciones = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPremultiplied = false
        inScaled = false
    }
    val bitmap = BitmapFactory.decodeByteArray(r.png, 0, r.png.size, opciones)
        ?: throw ErrorSentinel("El servicio de imágenes no devolvió una imagen válida.")
    if (bitmap.width != r.ancho || bitmap.height != r.alto) {
        bitmap.recycle()
        throw ErrorSentinel("El servicio de imágenes devolvió una imagen de otro tamaño.")
    }
    val pixeles = IntArray(r.ancho * r.alto)
    bitmap.getPixels(pixeles, 0, r.ancho, 0, 0, r.ancho, r.alto)
    bitmap.recycle()
    val grilla = GrillaGeografica(r.oeste, r.norte, (r.este - r.oeste) / r.ancho, (r.norte - r.sur) / r.alto, r.ancho, r.alto)
    return CampanaNdvi(
        campana = campana,
        grilla = grilla,
        ndvi = FloatArray(pixeles.size) { ImagenIndices.decodificarCanal((pixeles[it] shr 16) and 0xFF) },
        observaciones = IntArray(pixeles.size) { (pixeles[it] shr 8) and 0xFF },
        ndre = FloatArray(pixeles.size) { ImagenIndices.decodificarCanal(pixeles[it] and 0xFF) }
    )
}
