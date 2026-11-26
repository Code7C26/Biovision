package com.example

import com.example.ui.components.urlNavegable
import com.example.ui.screens.esUrlWindy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardas de la configuración de seguridad: si alguien vuelve a habilitar HTTP en claro, agrega
 * permisos sin uso, desactiva R8 o compila credenciales en BuildConfig, este test falla.
 * (Los tests de unidad corren con el directorio del módulo app/ como directorio de trabajo.)
 */
class SeguridadConfigTest {

    private fun leer(ruta: String) = File(ruta).readText()

    @Test
    fun soloHttps() {
        // Sin comentarios XML: el de ese archivo menciona cómo habilitar un dominio como ejemplo
        val red = leer("src/main/res/xml/network_security_config.xml").replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(red.contains("""<base-config cleartextTrafficPermitted="false">"""))
        assertFalse("Ningún dominio con HTTP en claro", red.contains("""cleartextTrafficPermitted="true""""))
        assertFalse(leer("src/main/AndroidManifest.xml").contains("usesCleartextTraffic=\"true\""))
    }

    @Test
    fun permisosMinimos() {
        val manifiesto = leer("src/main/AndroidManifest.xml")
        val permisos = Regex("""<uses-permission android:name="([^"]+)"(?![^>]*tools:node="remove")""")
            .findAll(manifiesto).map { it.groupValues[1] }.toSet()
        assertTrue(
            "Permisos declarados: $permisos",
            permisos == setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.POST_NOTIFICATIONS"
            )
        )
        assertFalse(manifiesto.contains("ACCESS_BACKGROUND_LOCATION"))
        assertFalse(manifiesto.contains("android.permission.CAMERA"))
    }

    @Test
    fun releaseOfuscadoYSinCredencialesCompiladas() {
        val gradle = leer("build.gradle.kts")
        assertTrue(gradle.contains("isMinifyEnabled = true"))
        assertTrue(gradle.contains("isShrinkResources = true"))
        // Las contraseñas de firma solo llegan por variables de entorno
        assertFalse(Regex("""storePassword = "[^"]+"\s*\n\s*keyAlias = System""").containsMatchIn(gradle))
        val ejemplo = leer("../.env.example")
        assertFalse("Credenciales de usuario fuera de BuildConfig", ejemplo.contains("REMARKETS_PASSWORD="))
        assertFalse(ejemplo.contains("REMARKETS_USERNAME="))
    }

    @Test
    fun losDatosDeLaCuentaNoSalenEnLasCopiasDeSeguridad() {
        for (archivo in listOf("src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml")) {
            val reglas = leer(archivo)
            for (dato in listOf("granex_sesion.xml", "granex_weather_notification_prefs.xml", "granex_agtech_db", "agronomia/", "agenda/")) {
                assertTrue("$archivo excluye $dato", reglas.contains("path=\"$dato\""))
            }
        }
        // Sin backend la app abre sin cuenta: el release lo exige
        assertTrue(leer("build.gradle.kts").contains("La versión release necesita Supabase"))
    }

    @Test
    fun laWebViewDeWindySoloNavegaEnWindy() {
        assertTrue(esUrlWindy("https", "embed.windy.com"))
        assertTrue(esUrlWindy("https", "www.windy.com"))
        assertTrue(esUrlWindy("https", "windy.com"))
        assertFalse(esUrlWindy("http", "embed.windy.com"))
        assertFalse(esUrlWindy("https", "windy.com.evil.example"))
        assertFalse(esUrlWindy("https", "notwindy.com"))
        assertFalse(esUrlWindy("javascript", null))
        // Lo que sale al navegador externo también tiene que ser https
        assertNull(urlNavegable("intent://scan/#Intent;scheme=zxing;end"))
    }
}
