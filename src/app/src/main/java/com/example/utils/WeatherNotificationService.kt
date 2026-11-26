package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.model.AgroClimateDashboard
import com.example.data.remote.model.EstadoRofex
import com.example.data.remote.model.FinancialDashboardData
import com.example.data.remote.model.evaluarAlertas
import com.example.data.repository.NewsArticle
import com.example.ui.theme.TemaGranex
import java.util.Locale

object WeatherNotificationService {

    private const val CHANNEL_ID = "granex_weather_alerts_channel"
    private const val CHANNEL_NAME = "Alertas GRANEX"
    private const val CHANNEL_DESC = "Alertas de clima, mercados y noticias del agro"
    private const val PREFS_NAME = "granex_weather_notification_prefs"

    // Preference Keys
    const val KEY_NOTIFICATIONS_ENABLED = "pref_notifications_enabled"
    const val KEY_ALERT_FROST_ENABLED = "pref_alert_frost_enabled"
    const val KEY_ALERT_FROST_TEMP = "pref_alert_frost_temp" // Float
    const val KEY_ALERT_RAIN_ENABLED = "pref_alert_rain_enabled"
    const val KEY_ALERT_RAIN_PCT = "pref_alert_rain_pct" // Int
    const val KEY_USER_NAME = "pref_user_name"
    const val KEY_USER_EMAIL = "pref_user_email"
    const val KEY_USER_FARM = "pref_user_farm"
    const val KEY_USER_LOCATION = "pref_user_location"
    const val KEY_USER_HECTARES = "pref_user_hectares"
    const val KEY_ALERT_MARKET_ENABLED = "pref_alert_market_enabled"
    const val KEY_ALERT_MARKET_DOLAR_ENABLED = "pref_alert_market_dolar_enabled"
    const val KEY_ALERT_MARKET_GRAINS_ENABLED = "pref_alert_market_grains_enabled"
    const val KEY_ALERT_NEWS_ENABLED = "pref_alert_news_enabled"
    const val KEY_APP_DARK_MODE = "pref_app_dark_mode"
    const val KEY_APP_TEMA = "pref_app_tema"
    const val KEY_DATA_SAVER_ENABLED = "pref_data_saver_enabled"
    // Credenciales de reMarkets de versiones anteriores: solo se usan para borrarlas
    const val KEY_ROFEX_USERNAME = "pref_rofex_username"
    const val KEY_ROFEX_PASSWORD = "pref_rofex_password"
    const val KEY_CDSE_CLIENT_ID = "pref_cdse_client_id"
    const val KEY_CDSE_CLIENT_SECRET = "pref_cdse_client_secret"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Umbrales de variación diaria (en %) para las alertas de mercado
    const val UMBRAL_DOLAR_PCT = 2.0
    const val UMBRAL_GRANOS_PCT = 2.0

    // Estado de deduplicación: compartido entre la app abierta y el chequeo en segundo plano
    private const val KEY_CLIMA_FECHA = "notif_clima_fecha"
    private const val KEY_CLIMA_ENVIADAS = "notif_clima_enviadas"
    private const val KEY_MERCADO_FECHA = "notif_mercado_fecha"
    private const val KEY_MERCADO_ENVIADAS = "notif_mercado_enviadas"
    private const val KEY_NOTICIAS_VISTAS = "notif_noticias_vistas"

    /**
     * Notifica las alertas climáticas (helada, lluvia, viento): cada una, una vez por día y por zona
     * (celda de 0,25°, unos 25 km). Abrir la app de nuevo, el chequeo en segundo plano o pasar de la
     * ciudad guardada a la ubicación GPS del mismo lugar no la repiten. Antes la clave era el nombre de
     * la ciudad: al abrir la app se cargaba la ciudad guardada y después "Ubicación actual", y como el
     * nombre cambiaba se volvía a avisar en cada apertura.
     */
    @Synchronized
    fun checkAndTriggerNotifications(context: Context, dashboard: AgroClimateDashboard) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)) return

        val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val enviadas = if (prefs.getString(KEY_CLIMA_FECHA, null) == hoy) {
            prefs.getStringSet(KEY_CLIMA_ENVIADAS, emptySet()).orEmpty().toMutableSet()
        } else mutableSetOf()
        val zona = "${kotlin.math.floor(dashboard.lat * 4).toInt()}_${kotlin.math.floor(dashboard.lon * 4).toInt()}"

        val alertas = evaluarAlertas(dashboard, prefs.getFloat(KEY_ALERT_FROST_TEMP, 3.0f).toDouble())
        fun avisar(tipo: String, id: Int, titulo: String, mensaje: String) {
            if (enviadas.add("${zona}_$tipo")) showNotification(context, id, titulo, mensaje)
        }

        if (prefs.getBoolean(KEY_ALERT_FROST_ENABLED, true)) {
            alertas.helada?.let {
                avisar("helada", 101, "⚠️ Riesgo de helada en ${dashboard.ciudad}", "$it ¡Protege tus cultivos!")
            }
        }

        if (prefs.getBoolean(KEY_ALERT_RAIN_ENABLED, true)) {
            val probLluvia = dashboard.probLluviaHoy ?: 0
            val mensaje = alertas.lluvia
                ?: if (probLluvia >= prefs.getInt(KEY_ALERT_RAIN_PCT, 60)) "Probabilidad de lluvia de $probLluvia% para hoy." else null
            mensaje?.let { avisar("lluvia", 102, "🌧️ Alerta de lluvias en ${dashboard.ciudad}", it) }
        }

        alertas.viento?.let {
            avisar("viento", 103, "💨 Viento fuerte en ${dashboard.ciudad}", "$it Evita pulverizar.")
        }

        prefs.edit()
            .putString(KEY_CLIMA_FECHA, hoy)
            .putStringSet(KEY_CLIMA_ENVIADAS, enviadas)
            .apply()
    }

    /** Variaciones fuertes del dólar y de los futuros de granos (ajuste de A3): cada movimiento se avisa una vez por día. */
    @Synchronized
    fun notificarMercado(context: Context, data: FinancialDashboardData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true) || !prefs.getBoolean(KEY_ALERT_MARKET_ENABLED, true)) return

        val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val enviadas = if (prefs.getString(KEY_MERCADO_FECHA, null) == hoy) {
            prefs.getStringSet(KEY_MERCADO_ENVIADAS, emptySet()).orEmpty().toMutableSet()
        } else mutableSetOf()
        val loc = Locale.getDefault()
        fun signo(v: Double) = if (v > 0) "sube" else "baja"

        if (prefs.getBoolean(KEY_ALERT_MARKET_DOLAR_ENABLED, true)) {
            val lineas = data.divisas
                .filter { it.casa != "tarjeta" && kotlin.math.abs(it.variacion) >= UMBRAL_DOLAR_PCT }
                .filter { enviadas.add("dolar_${it.casa}_${signo(it.variacion)}") }
                .map { String.format(loc, "%s %+.2f%% (venta $ %.2f)", it.nombre, it.variacion, it.venta) }
            if (lineas.isNotEmpty()) {
                showNotification(context, 201, "💵 Movimiento fuerte del dólar", lineas.joinToString("\n"))
            }
        }

        val rofex = data.rofex
        if (prefs.getBoolean(KEY_ALERT_MARKET_GRAINS_ENABLED, true) && rofex is EstadoRofex.Ok) {
            val lineas = rofex.contratos
                .filter { c -> c.variacionPorcentaje?.let { kotlin.math.abs(it) >= UMBRAL_GRANOS_PCT } == true }
                .filter { enviadas.add("rofex_${it.simbolo}_${signo(it.variacionPorcentaje!!)}") }
                .map { String.format(loc, "%s %+.2f%% (US$ %.2f)", it.simbolo, it.variacionPorcentaje, it.precioAjusteUSD) }
            if (lineas.isNotEmpty()) {
                showNotification(context, 202, "🌾 Movimiento fuerte en futuros de granos", lineas.joinToString("\n"))
            }
        }

        prefs.edit().putString(KEY_MERCADO_FECHA, hoy).putStringSet(KEY_MERCADO_ENVIADAS, enviadas).apply()
    }

    /**
     * Avisa de noticias que no estaban en el chequeo anterior. La primera vez solo registra las
     * actuales; con [notificar] en false (lista vista en pantalla) solo las marca como vistas.
     */
    @Synchronized
    fun notificarNoticias(context: Context, articulos: List<NewsArticle>, notificar: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // NewsRepository solo devuelve notas reales (no hay datos de ejemplo)
        val reales = articulos
        if (reales.isEmpty()) return

        val vistas = prefs.getStringSet(KEY_NOTICIAS_VISTAS, null)
        val idsActuales = reales.map { it.id }.toSet()
        // Se guardan las actuales más las anteriores, con tope para que la lista no crezca sin límite
        val aGuardar = if (vistas == null || vistas.size + idsActuales.size > 1000) idsActuales else vistas + idsActuales
        prefs.edit().putStringSet(KEY_NOTICIAS_VISTAS, aGuardar).apply()

        if (vistas == null || !notificar) return
        if (!prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true) || !prefs.getBoolean(KEY_ALERT_NEWS_ENABLED, true)) return
        val nuevas = reales.filter { it.id !in vistas }
        if (nuevas.isEmpty()) return

        val titulo = if (nuevas.size == 1) "📰 Nueva noticia del agro" else "📰 ${nuevas.size} noticias nuevas del agro"
        val texto = nuevas.take(3).joinToString("\n") { "• ${it.title} (${it.sourceName})" }
        showNotification(context, 203, titulo, texto)
    }

    private fun showNotification(context: Context, notificationId: Int, title: String, message: String) {
        initNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use system warning icon or default launcher icon
        val smallIcon = android.R.drawable.stat_sys_warning

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    // Helper functions for reading/writing preferences from Compose
    fun isNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isFrostAlertEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_FROST_ENABLED, true)
    }

    fun setFrostAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_FROST_ENABLED, enabled)
            .apply()
    }

    fun getFrostTempThreshold(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_ALERT_FROST_TEMP, 3.0f)
    }

    fun setFrostTempThreshold(context: Context, temp: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_ALERT_FROST_TEMP, temp)
            .apply()
    }

    fun isRainAlertEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_RAIN_ENABLED, true)
    }

    fun setRainAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_RAIN_ENABLED, enabled)
            .apply()
    }

    fun getRainPctThreshold(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ALERT_RAIN_PCT, 60)
    }

    fun setRainPctThreshold(context: Context, pct: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ALERT_RAIN_PCT, pct)
            .apply()
    }

    // Antes había datos de ejemplo por defecto ("Productor Agropecuario"…): se tratan como vacíos
    private val DATOS_DE_EJEMPLO = setOf("Productor Agropecuario", "productor@granex.com.ar", "Estancia La Constancia")

    private fun leerDato(context: Context, clave: String): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(clave, "").orEmpty()
            .takeIf { it !in DATOS_DE_EJEMPLO }.orEmpty()

    fun getUserName(context: Context): String = leerDato(context, KEY_USER_NAME)

    /** "Localidad, Provincia" del perfil. */
    fun getUserLocation(context: Context): String = leerDato(context, KEY_USER_LOCATION)

    fun setUserLocation(context: Context, ubicacion: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_USER_LOCATION, ubicacion).apply()
    }

    fun getUserHectares(context: Context): Double? =
        leerDato(context, KEY_USER_HECTARES).toDoubleOrNull()

    fun setUserHectares(context: Context, hectareas: Double?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_HECTARES, hectareas?.toString().orEmpty()).apply()
    }

    fun setUserName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserEmail(context: Context): String = leerDato(context, KEY_USER_EMAIL)

    fun setUserEmail(context: Context, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserFarm(context: Context): String = leerDato(context, KEY_USER_FARM)

    fun setUserFarm(context: Context, farm: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_FARM, farm)
            .apply()
    }

    fun isMarketAlertEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_MARKET_ENABLED, true)
    }

    fun setMarketAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_MARKET_ENABLED, enabled)
            .apply()
    }

    fun isMarketDolarEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_MARKET_DOLAR_ENABLED, true)
    }

    fun setMarketDolarEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_MARKET_DOLAR_ENABLED, enabled)
            .apply()
    }

    fun isMarketGrainsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_MARKET_GRAINS_ENABLED, true)
    }

    fun setMarketGrainsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_MARKET_GRAINS_ENABLED, enabled)
            .apply()
    }

    fun isNewsAlertEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERT_NEWS_ENABLED, true)
    }

    fun setNewsAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_NEWS_ENABLED, enabled)
            .apply()
     }

    fun isDarkModeEnabled(context: Context): Boolean {
        // Default to system settings or just false/true as starting point (let's say false or true)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_DARK_MODE, false)
    }

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_DARK_MODE, enabled)
            .apply()
    }

    /** Paleta elegida en Ajustes (se guarda el nombre del enum; si no se reconoce, Verde Campo). */
    fun getTema(context: Context): TemaGranex {
        val guardado = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_APP_TEMA, null)
        return TemaGranex.entries.firstOrNull { it.name == guardado } ?: TemaGranex.VERDE_CAMPO
    }

    fun setTema(context: Context, tema: TemaGranex) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_APP_TEMA, tema.name).apply()
    }

    fun isDataSaverEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DATA_SAVER_ENABLED, false)
    }

    fun setDataSaverEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DATA_SAVER_ENABLED, enabled)
            .apply()
    }

    // --- Secretos del usuario (client secret de Copernicus) ---
    // Se guardan cifrados con CredentialCipher (AES-256-GCM, clave no exportable del Android
    // Keystore): el archivo de preferencias nunca contiene el valor en claro. El archivo además está
    // excluido de las copias de seguridad (backup_rules.xml / data_extraction_rules.xml).
    private val CLAVES_SECRETAS = listOf(KEY_CDSE_CLIENT_SECRET)

    /** Al cerrar sesión o eliminar la cuenta: el próximo usuario del teléfono no usa estas credenciales. */
    fun borrarCredencialesTerceros(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_ROFEX_USERNAME).remove(KEY_ROFEX_PASSWORD)
            .remove(KEY_CDSE_CLIENT_ID).remove(KEY_CDSE_CLIENT_SECRET)
            .apply()
    }

    private fun leerSecreto(context: Context, clave: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guardado = prefs.getString(clave, null) ?: return null
        if (guardado.isEmpty()) return ""
        if (!CredentialCipher.isEncrypted(guardado)) {
            // Valor en claro de una versión anterior: se cifra en el acto
            guardarSecreto(context, clave, guardado)
            return guardado
        }
        return CredentialCipher.decrypt(guardado) ?: run {
            // La clave del Keystore ya no existe (reinstalación o restauración): hay que cargarlo de nuevo
            prefs.edit().putString(clave, "").apply()
            ""
        }
    }

    private fun guardarSecreto(context: Context, clave: String, valor: String) {
        // Sin trim: una contraseña puede empezar o terminar con espacios
        val cifrado = if (valor.isEmpty()) "" else CredentialCipher.encrypt(valor)
        // commit (síncrono) para que el valor en claro no sobreviva en disco a un cierre inesperado
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(clave, cifrado).commit()
    }

    /** Cifra cualquier secreto que haya quedado en texto plano. Se llama al iniciar la app (fuera del hilo principal). */
    fun cifrarSecretosPendientes(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Los futuros ya no salen de reMarkets: el usuario y la contraseña que hayan quedado se borran
        if (prefs.contains(KEY_ROFEX_USERNAME) || prefs.contains(KEY_ROFEX_PASSWORD)) {
            prefs.edit().remove(KEY_ROFEX_USERNAME).remove(KEY_ROFEX_PASSWORD).commit()
        }
        CLAVES_SECRETAS.forEach { clave ->
            val guardado = prefs.getString(clave, null)
            if (!guardado.isNullOrEmpty() && !CredentialCipher.isEncrypted(guardado)) guardarSecreto(context, clave, guardado)
        }
    }

    // Cliente OAuth de Copernicus Data Space (Sentinel Hub) del usuario: el secret va cifrado
    fun getCopernicusClientId(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CDSE_CLIENT_ID, null).orEmpty()

    fun setCopernicusClientId(context: Context, clientId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CDSE_CLIENT_ID, clientId.trim()).apply()
    }

    fun getCopernicusClientSecret(context: Context): String = leerSecreto(context, KEY_CDSE_CLIENT_SECRET).orEmpty()

    fun setCopernicusClientSecret(context: Context, secret: String) = guardarSecreto(context, KEY_CDSE_CLIENT_SECRET, secret.trim())
}
