# Reglas de R8 para el release (isMinifyEnabled = true en app/build.gradle.kts).
# Retrofit, OkHttp, Room, WorkManager, Coil y Moshi traen sus propias reglas (consumer rules);
# acá solo va lo específico de GRANEX.

# --- Stack traces legibles en Play Console (con mapping.txt) sin exponer nombres de archivo ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Moshi por reflexión (KotlinJsonAdapterFactory) ---
# Los DTO sin @JsonClass(generateAdapter = true) se leen por reflexión: hacen falta sus campos,
# constructores y la metadata de Kotlin. Los que tienen codegen ya están cubiertos por Moshi.
-keep class kotlin.Metadata { *; }
-keepclassmembers class com.example.data.remote.model.** {
    <init>(...);
    <fields>;
}
-keep class com.example.data.remote.model.** { *; }

# --- Puente página del mapa → app (fallback para WebViews viejas, GisMapController) ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- "Continuar con Google": Credential Manager carga el proveedor de Play Services por reflexión ---
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# --- Logs: en release se eliminan Log.v/d/i (Log.w/e quedan para diagnóstico) ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- Dependencias opcionales que no se usan ---
-dontwarn com.google.re2j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
