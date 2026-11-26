package com.example.data.remote.supabase

import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.resumable.MemoryResumableCache
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * JSON de las tablas de Supabase:
 * - encodeDefaults: `eliminado = false` también se envía;
 * - explicitNulls = false: un null no se envía, así la base completa sus valores por defecto
 *   (`creado_en`, `actualizado_en`).
 */
val JsonSupabase = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Motor HTTP de cada plataforma: HttpURLConnection en Android, fetch en el navegador. */
internal expect fun motorHttpSupabase(): HttpClientEngine

/**
 * Crea el cliente de Supabase de GRANEX. Uno solo por proceso (Android) o por pestaña (web):
 * guardarlo en AppContainer / ContenedorWeb.
 *
 * @param sesion dónde se guarda la sesión del usuario. En Android, [SesionEnAjustes] cifrada con el
 * Android Keystore; en la web, localStorage (como supabase-js).
 * @param motor motor HTTP; los tests pasan uno simulado.
 * @param cicloDeVida en Android pausa la renovación del token con la app en segundo plano (usa
 * Dispatchers.Main: los tests en la JVM lo apagan).
 */
fun crearClienteSupabase(
    configuracion: ConfiguracionSupabase,
    sesion: SessionManager,
    motor: HttpClientEngine = motorHttpSupabase(),
    cicloDeVida: Boolean = true
): SupabaseClient =
    createSupabaseClient(configuracion.url, configuracion.clavePublica) {
        httpEngine = motor
        // La consulta de fechas de Sentinel-2 (Edge Function `sentinel`) tarda ~10 s: los 10 s por
        // defecto de supabase-kt la cortaban justo antes de la respuesta
        requestTimeout = 45.seconds
        defaultSerializer = KotlinXSerializer(JsonSupabase)
        install(Auth) {
            sessionManager = sesion
            // PKCE: los enlaces de confirmación o recuperación de cuenta no exponen tokens en la URL
            flowType = FlowType.PKCE
            // El verificador PKCE solo sirve si el enlace del correo vuelve a la app, y hoy no vuelve:
            // la cuenta se confirma en el navegador y después se ingresa con la contraseña. En memoria
            // evita además los ajustes "sin argumentos" de multiplatform-settings.
            codeVerifierCache = MemoryCodeVerifierCache()
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            enableLifecycleCallbacks = cicloDeVida
        }
        install(Postgrest)
        // Edge Functions (imágenes Sentinel-2 con la cuenta de GRANEX): llevan el token del usuario
        install(Functions)
        install(Storage) {
            // Las subidas reanudables (TUS) no se usan: en memoria, sin los ajustes "sin argumentos"
            resumable { cache = MemoryResumableCache() }
        }
    }

/**
 * Guarda la sesión (tokens de acceso y renovación) en un [Settings], pasando el JSON por [cifrar] y
 * [descifrar]. Android usa SharedPreferences excluidas del backup y CredentialCipher (AES-GCM con
 * clave del Keystore); la web, localStorage sin cifrar, igual que supabase-js. Ahí lo que protege el
 * token es la CSP estricta de index.html.
 */
class SesionEnAjustes(
    private val ajustes: Settings,
    private val cifrar: (String) -> String = { it },
    private val descifrar: (String) -> String? = { it }
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        ajustes.putString(CLAVE, cifrar(JsonSupabase.encodeToString(UserSession.serializer(), session)))
    }

    override suspend fun loadSession(): UserSession? {
        val guardada = ajustes.getStringOrNull(CLAVE) ?: return null
        val json = descifrar(guardada) ?: return null.also { deleteSession() } // clave del Keystore perdida
        return try {
            JsonSupabase.decodeFromString(UserSession.serializer(), json)
        } catch (e: SerializationException) {
            deleteSession()
            null
        } catch (e: IllegalArgumentException) {
            deleteSession()
            null
        }
    }

    override suspend fun deleteSession() {
        ajustes.remove(CLAVE)
    }

    companion object {
        const val CLAVE = "supabase_sesion"
        /** SharedPreferences propias en Android (excluidas del backup en res/xml). */
        const val ARCHIVO_ANDROID = "granex_sesion"
    }
}
