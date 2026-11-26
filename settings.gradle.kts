pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  // PREFER_SETTINGS y no FAIL_ON_PROJECT_REPOS: el plugin de Kotlin/JS agrega en el proyecto raíz sus
  // propios repositorios para descargar Node.js, Yarn y Binaryen. Con PREFER_SETTINGS esos se ignoran
  // y se usan los mismos repositorios, declarados acá abajo; el resto de los proyectos sigue sin
  // poder agregar repositorios propios.
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
    // Herramientas de Kotlin/JS y Kotlin/Wasm (solo esos módulos, así no se consultan para nada más)
    ivy("https://nodejs.org/dist") {
      name = "Node.js"
      patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
      metadataSources { artifact() }
      content { includeModule("org.nodejs", "node") }
    }
    ivy("https://github.com/yarnpkg/yarn/releases/download") {
      name = "Yarn"
      patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
      metadataSources { artifact() }
      content { includeModule("com.yarnpkg", "yarn") }
    }
    ivy("https://github.com/WebAssembly/binaryen/releases/download") {
      name = "Binaryen"
      patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
      metadataSources { artifact() }
      content { includeModule("com.github.webassembly", "binaryen") }
    }
  }
}

rootProject.name = "GRANEX"

include(":app")
// Kotlin Multiplatform: lógica compartida (Android + web) y la app web (PWA, Compose para Wasm/JS)
include(":shared")
include(":webApp")
