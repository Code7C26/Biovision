package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.ui.components.EscalaPorPantalla

/** Tema elegido por el usuario. Se carga en MainActivity.onCreate y se cambia desde Ajustes. */
object AppThemeState {
    /** Preferencia de modo oscuro; los temas de modo fijo la ignoran (ver [oscuroEfectivo]). */
    var isDark by mutableStateOf(false)
    var tema by mutableStateOf(TemaGranex.VERDE_CAMPO)

    val oscuroEfectivo: Boolean get() = tema.usaOscuro(isDark)
    val marca: ColoresMarca get() = tema.marca(oscuroEfectivo)
}

@Composable
fun GRANEXTheme(
    tema: TemaGranex = AppThemeState.tema,
    darkTheme: Boolean = AppThemeState.oscuroEfectivo,
    content: @Composable () -> Unit
) {
    // Íconos de la barra de estado y de navegación legibles sobre el fondo del tema
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val ventana = view.context.actividad()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(ventana, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // La app se ve igual en todos los teléfonos, agrandada o achicada según su pantalla
    EscalaPorPantalla {
        MaterialTheme(
            colorScheme = tema.esquema(darkTheme),
            typography = Typography,
            content = content
        )
    }
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}
