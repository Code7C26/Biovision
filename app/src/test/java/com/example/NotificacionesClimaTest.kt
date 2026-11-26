package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.model.AgroClimateDashboard
import com.example.utils.WeatherNotificationService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Las alertas del clima se avisan una vez por día y por zona: abrir la app de nuevo, el chequeo en
 * segundo plano o pasar de la ciudad guardada a "Ubicación actual" no las repiten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificacionesClimaTest {

    private lateinit var contexto: Context
    private lateinit var notificaciones: NotificationManager

    /** Alta Gracia con 95 % de probabilidad de lluvia: dispara solo la alerta de lluvia. */
    private val lluvia = AgroClimateDashboard(
        ciudad = "Alta Gracia", lat = -31.66, lon = -64.43,
        tempCelsius = 20.0, tempMin = 12.0, tempMax = 25.0, humedadPorcentaje = 70, presionHpa = 1012,
        vientoKmH = 10.0, direccionVientoGrados = 90, condicionTexto = "Lluvia", evapotranspiracionET0 = 3.0,
        riesgoFuego = "Bajo", probLluviaHoy = 95
    )

    @Before
    fun antes() {
        contexto = ApplicationProvider.getApplicationContext()
        shadowOf(contexto as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificaciones = contexto.getSystemService(NotificationManager::class.java)
    }

    private fun avisadas(): Int = shadowOf(notificaciones).allNotifications.size

    @Test
    fun cadaAlertaSeAvisaUnaVezPorDiaYPorZona() {
        WeatherNotificationService.checkAndTriggerNotifications(contexto, lluvia)
        assertEquals("primera vez: avisa la lluvia", 1, avisadas())
        notificaciones.cancelAll()

        // Se abre de nuevo la app: primero la ciudad guardada, después la ubicación GPS del mismo lugar
        WeatherNotificationService.checkAndTriggerNotifications(contexto, lluvia.copy(lastUpdated = lluvia.lastUpdated + 60_000))
        WeatherNotificationService.checkAndTriggerNotifications(contexto, lluvia.copy(ciudad = "Ubicación actual", lat = -31.655, lon = -64.425))
        assertEquals("no se repite al reabrir ni con otro nombre", 0, avisadas())

        // Otra zona (Rosario, a más de 25 km): sí avisa
        WeatherNotificationService.checkAndTriggerNotifications(contexto, lluvia.copy(ciudad = "Rosario", lat = -32.95, lon = -60.64))
        assertEquals("otra zona avisa", 1, avisadas())
    }
}
