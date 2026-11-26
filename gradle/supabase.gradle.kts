// Configuración pública de Supabase para :app y :webApp. Se aplica con
//   apply(from = rootProject.file("gradle/supabase.gradle.kts"))
// y deja en el proyecto los extras "supabaseUrl" y "supabaseClave" ("" si no hay proyecto configurado).
//
// Orden de búsqueda:
//   1. Variables de entorno SUPABASE_URL y SUPABASE_ANON_KEY (CI, hosting web).
//   2. local.properties en la raíz (no versionado): supabase.url y supabase.anonKey.
// Sin valores la app funciona como antes: solo con datos locales y sin sincronizar.
//
// La clave pública ("anon" o "sb_publishable_…") viaja dentro del APK y de la web por diseño de
// Supabase: lo que protege los datos son las políticas RLS (supabase/migrations). La clave secreta
// ("service_role" o "sb_secret_…") saltea RLS y da acceso total a la base: si se configura por error,
// el build se corta acá.
import java.util.Base64
import java.util.Properties

val propiedadesLocales = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun valorSupabase(variable: String, propiedad: String): String =
    (System.getenv(variable)?.takeIf { it.isNotBlank() } ?: propiedadesLocales.getProperty(propiedad).orEmpty()).trim()

/** Rol que declara una clave JWT de Supabase ("anon", "service_role"); null si no es un JWT. */
fun rolDeClaveJwt(clave: String): String? = runCatching {
    val partes = clave.split('.')
    if (partes.size != 3) return@runCatching null
    val contenido = String(Base64.getUrlDecoder().decode(partes[1]), Charsets.UTF_8)
    Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(contenido)?.groupValues?.get(1)
}.getOrNull()

// -Pgranex.sinBackend=true: build de prueba en modo local (sin backend ni pedido de cuenta), para ver
// las pantallas sin ingresar. Nunca para publicar: el release de Android lo rechaza por no tener backend.
val sinBackend = (findProperty("granex.sinBackend") as String?) == "true"
val urlSupabase = if (sinBackend) "" else valorSupabase("SUPABASE_URL", "supabase.url").trimEnd('/')
val claveSupabase = if (sinBackend) "" else valorSupabase("SUPABASE_ANON_KEY", "supabase.anonKey")

require(!claveSupabase.startsWith("sb_secret_") && rolDeClaveJwt(claveSupabase) != "service_role") {
    "La clave de Supabase configurada es la SECRETA del proyecto (service_role / sb_secret_). " +
        "Esa clave saltea las políticas RLS y no puede ir en la app ni en la web: usá la clave pública " +
        "(\"anon\" o \"sb_publishable_\") de Project Settings → API Keys."
}
require(urlSupabase.isEmpty() || (urlSupabase.startsWith("https://") && claveSupabase.isNotEmpty())) {
    "supabase.url tiene que empezar con https:// y necesita también supabase.anonKey."
}

extra["supabaseUrl"] = urlSupabase
extra["supabaseClave"] = if (urlSupabase.isEmpty()) "" else claveSupabase
