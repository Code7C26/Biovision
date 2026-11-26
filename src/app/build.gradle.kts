import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Supabase: URL y clave pública desde local.properties (supabase.url / supabase.anonKey) o variables de
// entorno; corta el build si se configura la clave secreta. Ver gradle/supabase.gradle.kts
apply(from = rootProject.file("gradle/supabase.gradle.kts"))
val supabaseUrl: String by extra
val supabaseClave: String by extra

// Sin Supabase la app abre sin cuenta (modo local de desarrollo): una versión para publicar tiene que
// tener el backend configurado, porque el acceso exige iniciar sesión.
val armaRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
  !project.hasProperty("pruebaR8")
require(!armaRelease || supabaseUrl.isNotEmpty()) {
  "La versión release necesita Supabase (supabase.url y supabase.anonKey en local.properties): sin backend la app no pide iniciar sesión."
}

// Google: client ID *web* de Google Cloud (es público; lo valida Supabase). Vacío = sin botón de Google.
// local.properties google.webClientId o variable de entorno GOOGLE_WEB_CLIENT_ID.
val googleWebClientId: String = (System.getenv("GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() }
  ?: Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
  }.getProperty("google.webClientId").orEmpty()).trim()
require(googleWebClientId.isEmpty() || googleWebClientId.endsWith(".apps.googleusercontent.com")) {
  "google.webClientId tiene que ser un client ID de Google (…apps.googleusercontent.com)"
}

/** Literal de Java para un buildConfigField de texto. */
fun literalJava(valor: String) = "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.granex.agtech"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Vacíos = sin backend: la app funciona solo con datos locales (ApiKeys.supabase devuelve null)
    buildConfigField("String", "SUPABASE_URL", literalJava(supabaseUrl))
    buildConfigField("String", "SUPABASE_ANON_KEY", literalJava(supabaseClave))
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", literalJava(googleWebClientId))
  }

  // Clave de subida a Play: solo por variables de entorno (nunca en el repo ni en gradle.properties).
  // Sin ellas el bundle de release sale sin firmar (sirve para verificar R8, no para publicar).
  val firmaRelease = System.getenv("KEYSTORE_PATH") != null && System.getenv("STORE_PASSWORD") != null
  signingConfigs {
    create("release") {
      storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8: ofusca y minifica el código y quita recursos sin uso (reglas en proguard-rules.pro).
      // Subir app/build/outputs/mapping/release/mapping.txt a Play para leer los stack traces.
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (firmaRelease) signingConfigs.getByName("release") else null
      // Solo para probar el release (R8) en un teléfono sin pisar la app instalada:
      // ./gradlew assembleRelease -PpruebaR8  → com.aistudio.granex.agtech.r8
      if (project.hasProperty("pruebaR8")) {
        applicationIdSuffix = ".r8"
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  // Lógica común con la web (Kotlin Multiplatform): ver shared/ y docs/web/MIGRACION_KMP.md
  implementation(project(":shared"))
  implementation(platform(libs.androidx.compose.bom))
  // Firebase no se usa hoy: se agrega (con su BoM) solo junto con la primera librería de Firebase
  // implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // supabase-kt pide lifecycle-process 2.9.4: eso sube todo Lifecycle a 2.9 y, con él, Compose de 1.7 (BOM
  // 2024.09) a 1.9 sin avisar. Se fija en la versión de la app; supabase solo usa ProcessLifecycleOwner y
  // DefaultLifecycleObserver, que no cambiaron. Subir Compose es una decisión aparte (fase 2 de la migración).
  implementation(libs.androidx.lifecycle.process) { version { strictly(libs.versions.lifecycleRuntimeKtx.get()) } }
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // "Continuar con Google" (Credential Manager → ID token → Supabase): ver data/repository/AccesoGoogle.kt
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.jsoup)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.webkit)
  implementation(libs.play.services.location)
  // El mapa es Leaflet en una WebView: Google Maps no se usa
  // implementation(libs.play.services.maps)
  // implementation(libs.maps.compose)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // MapSettings (Settings en memoria) para los tests de sincronización
  testImplementation(libs.multiplatform.settings.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
