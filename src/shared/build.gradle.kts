import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Código común de GRANEX para Android (:app) y la web (:webApp): lógica pura (márgenes, geometría,
// análisis satelital, VRA), clientes HTTP con Ktor, preferencias con multiplatform-settings y el
// backend Supabase (base de datos, cuentas y archivos) con sincronización offline de lotes.
// Sin Compose a propósito: la app Android usa el BOM 2024.09 y Compose Multiplatform la subiría a
// Compose 1.10 (ver docs/web/MIGRACION_KMP.md, fase 2).
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.example.shared"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    minSdk = 24
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    // Corre commonTest en la JVM (sin emulador)
    withHostTestBuilder {}
  }

  // Biblioteca: el empaquetado para el navegador lo hace :webApp. Node solo corre commonTest
  // (./gradlew :shared:allTests = JVM + JS + Wasm), sin depender de un navegador instalado.
  js { nodejs() }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs { nodejs() }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      // api: los servicios reciben un HttpClient creado por quien los usa
      api(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
      api(libs.multiplatform.settings)
      // Supabase: Postgrest (tablas), Auth (cuentas; api porque :app implementa su SessionManager
      // cifrado) y Storage (archivos de lotes). Ver docs/backend/SUPABASE.md
      api(libs.supabase.postgrest)
      api(libs.supabase.auth)
      api(libs.supabase.storage)
      api(libs.supabase.functions)
    }
    // Motor HTTP de Supabase en Android (HttpURLConnection: respeta network_security_config)
    androidMain.dependencies { implementation(libs.ktor.client.android) }
    // Motor HTTP del navegador (fetch). En Android todavía se usa Retrofit/OkHttp 4: el motor OkHttp de
    // Ktor 3 trae OkHttp 5 y se agrega recién al migrar los repositorios (fase 2).
    webMain.dependencies { implementation(libs.ktor.client.js) }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.multiplatform.settings.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }
  }
}
