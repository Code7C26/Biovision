package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.WeatherNotificationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GRANEX", appName)
  }

  @Test
  fun `test weather notification preferences persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Set & Verify global notification enabled
    WeatherNotificationService.setNotificationsEnabled(context, false)
    assertFalse(WeatherNotificationService.isNotificationsEnabled(context))
    WeatherNotificationService.setNotificationsEnabled(context, true)
    assertTrue(WeatherNotificationService.isNotificationsEnabled(context))

    // Set & Verify frost alert settings
    WeatherNotificationService.setFrostAlertEnabled(context, false)
    assertFalse(WeatherNotificationService.isFrostAlertEnabled(context))
    WeatherNotificationService.setFrostAlertEnabled(context, true)
    assertTrue(WeatherNotificationService.isFrostAlertEnabled(context))

    WeatherNotificationService.setFrostTempThreshold(context, 1.5f)
    assertEquals(1.5f, WeatherNotificationService.getFrostTempThreshold(context), 0.01f)

    // Set & Verify heavy rain alert settings
    WeatherNotificationService.setRainAlertEnabled(context, false)
    assertFalse(WeatherNotificationService.isRainAlertEnabled(context))
    WeatherNotificationService.setRainAlertEnabled(context, true)
    assertTrue(WeatherNotificationService.isRainAlertEnabled(context))

    WeatherNotificationService.setRainPctThreshold(context, 75)
    assertEquals(75, WeatherNotificationService.getRainPctThreshold(context))
  }
}
