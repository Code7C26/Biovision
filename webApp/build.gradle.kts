import java.util.Properties
import java.security.MessageDigest
import javax.inject.Inject
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// GRANEX web: PWA con Compose Multiplatform. Wasm (navegadores con WasmGC: Chrome/Edge 119+,
// Firefox 120+, Safari 18.2+) y JS como respaldo para navegadores viejos (modo compatibilidad).
//
// Desarrollo:  ./gradlew :webApp:wasmJsBrowserDevelopmentRun   (abre http://localhost:8080)
// Publicación: ./gradlew :webApp:distribucionPwa       → webApp/build/dist/pwa (Wasm + JS)
//              ./gradlew :webApp:distribucionPwaWasm   → webApp/build/dist/pwa-wasm (solo Wasm, compila más rápido)
// Despliegue en Firebase Hosting, Vercel o GitHub Pages: docs/web/DESPLIEGUE.md
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

// Supabase: URL y clave pública desde variables de entorno (SUPABASE_URL / SUPABASE_ANON_KEY, p. ej. en
// el hosting) o local.properties. Corta el build si se configura la clave secreta. Ver gradle/supabase.gradle.kts
apply(from = rootProject.file("gradle/supabase.gradle.kts"))
val supabaseUrl: String by extra
val supabaseClave: String by extra

/**
 * Genera ConfiguracionSupabaseWeb.kt con la URL y la clave pública del proyecto. Vacías = sin backend
 * (la web funciona igual, sin cuentas ni sincronización). La clave pública es visible en cualquier
 * cliente por diseño de Supabase: los datos los protegen las políticas RLS.
 */
abstract class GenerarConfiguracionSupabase : DefaultTask() {
  @get:Input abstract val url: Property<String>
  @get:Input abstract val clave: Property<String>
  @get:OutputDirectory abstract val salida: DirectoryProperty

  @TaskAction
  fun generar() {
    fun literal(valor: String) = "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    val archivo = salida.get().asFile.resolve("com/example/web/ConfiguracionSupabaseWeb.kt")
    archivo.parentFile.mkdirs()
    archivo.writeText(
      """
      |// Generado por :webApp:generarConfiguracionSupabase (gradle/supabase.gradle.kts). No editar.
      |package com.example.web
      |
      |internal object ConfiguracionSupabaseWeb {
      |    const val URL = ${literal(url.get())}
      |    const val CLAVE_PUBLICA = ${literal(clave.get())}
      |}
      |""".trimMargin()
    )
  }
}

val generarConfiguracionSupabase = tasks.register<GenerarConfiguracionSupabase>("generarConfiguracionSupabase") {
  url.set(supabaseUrl)
  clave.set(supabaseClave)
  salida.set(layout.buildDirectory.dir("generated/supabase/kotlin"))
}

/*
 * Datos de publicación (variables de entorno en CI o local.properties):
 * - GRANEX_URL_PUBLICA / granex.urlPublica: dirección https donde se publica la web (con / final).
 *   Completa canonical y la vista previa al compartir el enlace (og:url, og:image); vacía = sin esas líneas.
 * - GRANEX_EMAIL_SOPORTE / granex.emailSoporte: contacto de las páginas legales (lo pide la Ley 25.326
 *   para ejercer los derechos sobre los datos). Sin configurar: supportgranex@gmail.com.
 */
val propiedadesPublicacion = Properties().apply {
  rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun datoPublicacion(variable: String, propiedad: String): String =
  (System.getenv(variable)?.takeIf { it.isNotBlank() } ?: propiedadesPublicacion.getProperty(propiedad).orEmpty()).trim()
val urlPublica = datoPublicacion("GRANEX_URL_PUBLICA", "granex.urlPublica").let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
val emailSoporte = datoPublicacion("GRANEX_EMAIL_SOPORTE", "granex.emailSoporte").ifEmpty { "supportgranex@gmail.com" }
require(urlPublica.isEmpty() || urlPublica.startsWith("https://")) { "GRANEX_URL_PUBLICA tiene que empezar con https://" }
require(emailSoporte.isEmpty() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(emailSoporte)) { "GRANEX_EMAIL_SOPORTE no es un correo válido" }

// La CSP de index.html solo deja conectarse a los hosts que lista: se agrega el del proyecto Supabase.
// Las líneas con un dato de publicación sin configurar se quitan (nunca se publica un marcador).
tasks.withType<ProcessResources>().configureEach {
  // Copias locales: el filtro no puede referenciar el script (caché de configuración de Gradle)
  val origen = supabaseUrl
  val url = urlPublica
  val email = emailSoporte
  inputs.property("origenSupabase", origen)
  inputs.property("urlPublica", url)
  inputs.property("emailSoporte", email)
  filesMatching(listOf("index.html", "legal/*.html", "robots.txt")) {
    filter { linea ->
      when {
        "__URL_PUBLICA__" in linea && url.isEmpty() -> ""
        "__EMAIL_SOPORTE__" in linea && email.isEmpty() -> ""
        else -> linea.replace("__ORIGEN_SUPABASE__", origen).replace("__URL_PUBLICA__", url).replace("__EMAIL_SOPORTE__", email)
      }
    }
  }
}

kotlin {
  // Navegador es un expect object (DOM distinto en Wasm y JS); las clases expect/actual están en Beta
  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
  js {
    browser()
    binaries.executable()
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    webMain {
      // La página del mapa GIS (Leaflet + mapa_gis.js) es la misma que usa la WebView de Android
      resources.srcDir(rootProject.file("app/src/main/assets"))
      kotlin.srcDir(generarConfiguracionSupabase)
      dependencies {
        implementation(project(":shared"))
        implementation(libs.compose.mp.runtime)
        implementation(libs.compose.mp.foundation)
        implementation(libs.compose.mp.ui)
        implementation(libs.compose.mp.material3)
        implementation(libs.compose.mp.material.icons.core)
        implementation(libs.compose.mp.components.resources)
        implementation(libs.lifecycle.mp.viewmodel.compose)
        implementation(libs.lifecycle.mp.runtime.compose)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    // El DOM (iframes, history, window.open) difiere entre Wasm y JS: va en expect/actual (Navegador.kt)
    wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
  }
}

compose.resources {
  packageOfResClass = "com.example.web.recursos"
  publicResClass = false
  // La dependencia components-resources está en webMain y el modo "auto" solo mira commonMain:
  // sin esto no se genera la clase Res
  generateResClass = always
}

/**
 * Copia una distribución web y le agrega el service worker: la plantilla pwa/sw.js recibe la lista
 * de archivos a precachear y una versión (hash del contenido), así cada build nuevo invalida la caché.
 */
abstract class ArmarPwa : DefaultTask() {
  @get:InputDirectory abstract val origen: DirectoryProperty
  @get:InputFile abstract val plantillaSw: RegularFileProperty
  /** Configuración del hosting (vercel.json): se copia a la raíz pero no se precarga. */
  @get:InputDirectory abstract val hosting: DirectoryProperty
  @get:OutputDirectory abstract val destino: DirectoryProperty
  @get:Inject abstract val archivos: FileSystemOperations
  /** Datos de publicación que faltan (se avisan al final: la carpeta se arma igual para probar). */
  @get:Input abstract val faltantes: ListProperty<String>

  @TaskAction
  fun armar() {
    archivos.sync {
      from(origen) { exclude("**/*.map", "**/*.LICENSE.txt") }
      from(hosting)
      into(destino)
    }
    val raiz = destino.get().asFile
    val deHosting = hosting.get().asFile.list()?.toSet().orEmpty()
    val lista = raiz.walkTopDown()
      .filter { it.isFile && it.name != "sw.js" && it.name !in deHosting }
      .map { it.relativeTo(raiz).invariantSeparatorsPath }
      .sorted()
      .toList()
    val digest = MessageDigest.getInstance("SHA-256")
    lista.forEach { ruta ->
      digest.update(ruta.toByteArray())
      digest.update(File(raiz, ruta).readBytes())
    }
    val version = digest.digest().joinToString("") { "%02x".format(it) }.take(12)

    // Modo compatibilidad: webApp.js carga originWasmWebApp.js u originJsWebApp.js, y cada uno pide los
    // .wasm/.js con hash de webpack cuyo nombre aparece en su código. Los demás scripts de la raíz
    // (skiko.wasm duplicado, skiko.mjs…) no los pide ninguna variante: quedan publicados sin precarga.
    val wasm = variante(raiz, "originWasmWebApp.js")
    val js = variante(raiz, "originJsWebApp.js")
    // Lo que index.html carga directamente (webApp.js, inicio.js…) siempre va en la precarga común
    val html = File(raiz, "index.html").takeIf { it.isFile }?.readText().orEmpty()
    val sinPrecarga = if (wasm.isEmpty() && js.isEmpty()) emptySet() else lista.filter {
      '/' !in it && "\"$it\"" !in html && it.substringAfterLast('.') in setOf("js", "mjs", "wasm") &&
        it !in wasm && it !in js
    }.toSet()
    val comun = lista.filter { it !in wasm && it !in js && it !in sinPrecarga }
    fun literal(rutas: Collection<String>) = rutas.sorted().joinToString(",\n  ") { "'./$it'" }
    File(raiz, "sw.js").writeText(
      plantillaSw.get().asFile.readText()
        .replace("__VERSION__", version)
        .replace("'__PRECARGA_COMUN__'", literal(comun))
        .replace("'__PRECARGA_WASM__'", literal(wasm))
        .replace("'__PRECARGA_JS__'", literal(js))
    )
    faltantes.get().forEach { logger.warn("AVISO para publicar: falta $it") }
    logger.lifecycle(
      "PWA lista en $raiz (${lista.size} archivos, versión $version): precarga ${comun.size} comunes + " +
        "${wasm.size} Wasm o ${js.size} JS; sin precarga: ${sinPrecarga.sorted().joinToString()}"
    )
  }

  /** La entrada de una variante y los archivos con hash de webpack que nombra; vacío si no existe. */
  private fun variante(raiz: File, entrada: String): Set<String> {
    val archivo = File(raiz, entrada)
    if (!archivo.isFile) return emptySet()
    val codigo = archivo.readText()
    val conHash = Regex("[0-9a-f]{16,}\\.(wasm|m?js)")
    return raiz.listFiles().orEmpty()
      .filter { it.isFile && conHash.matches(it.name) && it.name in codigo }
      .map { it.name }
      .toSet() + entrada
  }
}

tasks.register<ArmarPwa>("distribucionPwa") {
  faltantes.set(listOfNotNull(
    if (emailSoporte.isEmpty()) "GRANEX_EMAIL_SOPORTE (contacto de las páginas legales)" else null,
    if (urlPublica.isEmpty()) "GRANEX_URL_PUBLICA (canonical y vista previa al compartir)" else null,
    if (supabaseUrl.isEmpty()) "SUPABASE_URL / SUPABASE_ANON_KEY (sin cuentas ni sincronización)" else null
  ))
  group = "distribution"
  description = "Arma la PWA (Wasm + JS de respaldo) en build/dist/pwa, lista para publicar."
  dependsOn("composeCompatibilityBrowserDistribution")
  origen.set(layout.buildDirectory.dir("dist/composeWebCompatibility/productionExecutable"))
  plantillaSw.set(layout.projectDirectory.file("pwa/sw.js"))
  hosting.set(layout.projectDirectory.dir("pwa/hosting"))
  destino.set(layout.buildDirectory.dir("dist/pwa"))
}

tasks.register<ArmarPwa>("distribucionPwaWasm") {
  faltantes.set(listOfNotNull(
    if (emailSoporte.isEmpty()) "GRANEX_EMAIL_SOPORTE (contacto de las páginas legales)" else null,
    if (urlPublica.isEmpty()) "GRANEX_URL_PUBLICA (canonical y vista previa al compartir)" else null,
    if (supabaseUrl.isEmpty()) "SUPABASE_URL / SUPABASE_ANON_KEY (sin cuentas ni sincronización)" else null
  ))
  group = "distribution"
  description = "Arma la PWA solo con Wasm en build/dist/pwa-wasm (más rápida de compilar)."
  dependsOn("wasmJsBrowserDistribution")
  origen.set(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
  plantillaSw.set(layout.projectDirectory.file("pwa/sw.js"))
  hosting.set(layout.projectDirectory.dir("pwa/hosting"))
  destino.set(layout.buildDirectory.dir("dist/pwa-wasm"))
}

// Los recursos suman app/src/main/assets (la página del mapa) y desde el rediseño de Android esa carpeta
// también trae licencias/inter-OFL.txt, idéntica a la de la web: se copia una sola vez.
tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
