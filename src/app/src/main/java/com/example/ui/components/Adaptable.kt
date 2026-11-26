package com.example.ui.components

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Ancho del teléfono con el que se diseñó la app (un Pixel de 411 dp). En cualquier teléfono la app
 * se dibuja como en ese ancho y se agranda o achica entera en proporción a su pantalla
 * ([EscalaPorPantalla]): en uno de 360 dp todo queda un 12 % más chico, con la misma disposición.
 */
const val ANCHO_DISENO_DP = 411f

/** Límites del escalado: en una tablet la app no se agranda más de 1,3× ni se achica de 0,7×. */
private const val ESCALA_MINIMA = 0.7f
private const val ESCALA_MAXIMA = 1.3f

/**
 * Escala de la actividad entera: densidad = lado corto en píxeles / [ANCHO_DISENO_DP] (con los límites
 * de [ESCALA_MINIMA]–[ESCALA_MAXIMA]) y letra 1×, aplicadas a la configuración del contexto en
 * `MainActivity.attachBaseContext`.
 *
 * Hace falta además de [EscalaPorPantalla]: cada ventana aparte (ModalBottomSheet, diálogos, menús,
 * popups) arma su propia raíz de Compose, que vuelve a poner la densidad real del teléfono y su
 * tamaño de letra. En un Xiaomi de 384 dp con letra 1,45× los desplegables de Clima y Mercado se
 * veían deformes aunque la pantalla principal estaba bien. Con la configuración ya escalada, todas
 * las ventanas (y las WebView) heredan la misma densidad, y [EscalaPorPantalla] queda en 1×.
 */
object EscalaPantalla {
    fun contexto(base: Context): Context {
        val conf = base.resources.configuration
        val real = base.resources.displayMetrics.density
        val ladoCortoDp = minOf(conf.screenWidthDp, conf.screenHeightDp)
        if (ladoCortoDp <= 0 || real <= 0f) return base
        val escala = (ladoCortoDp / ANCHO_DISENO_DP).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA)
        val dpi = (real * escala * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
        val factor = real * DisplayMetrics.DENSITY_DEFAULT / dpi
        val nueva = Configuration(conf).apply {
            densityDpi = dpi
            fontScale = 1f
            screenWidthDp = (conf.screenWidthDp * factor).roundToInt()
            screenHeightDp = (conf.screenHeightDp * factor).roundToInt()
            smallestScreenWidthDp = (conf.smallestScreenWidthDp * factor).roundToInt()
        }
        return base.createConfigurationContext(nueva)
    }
}

/**
 * Escala toda la app según el tamaño de la pantalla, como una foto: las proporciones de cuadros,
 * textos e íconos son las mismas en todos los teléfonos.
 *
 * - Se toma el lado corto de la pantalla, así que apaisada la escala no cambia: solo hay más ancho.
 * - La letra sigue la misma escala y no el tamaño de letra de Ajustes de Android. Con la letra del
 *   sistema agrandada, el texto crecía y los cuadros no, y la app se veía desproporcionada.
 * - Probado en FinanzasMaquetacionScreenshotTest: un teléfono de 360 dp con letra 1,3× se ve como el
 *   Pixel.
 */
@Composable
fun EscalaPorPantalla(content: @Composable () -> Unit) {
    val configuracion = LocalConfiguration.current
    val densidad = LocalDensity.current
    val ladoCorto = minOf(configuracion.screenWidthDp, configuracion.screenHeightDp)
    val escala = if (ladoCorto > 0) (ladoCorto / ANCHO_DISENO_DP).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA) else 1f
    val escalada = remember(densidad.density, escala) { Density(densidad.density * escala, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides escalada, content = content)
}

/**
 * Dos elementos lado a lado (el primero al principio, el segundo al final) si entran en el ancho; si
 * no, el segundo va debajo del primero. Sirve para nombre + variación o compra + venta: nunca parte un
 * número para hacerlo entrar. A diferencia de FlowRow, informa bien su altura intrínseca, así que
 * funciona dentro de las filas de cuadros de igual altura de [GrillaAdaptable] (con FlowRow, el
 * cuadro que bajaba un renglón perdía su última línea).
 */
@Composable
fun LadoALadoOApilados(
    modifier: Modifier = Modifier,
    espacioHorizontal: Dp = 8.dp,
    espacioVertical: Dp = 4.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier, measurePolicy = remember(espacioHorizontal, espacioVertical) {
        PoliticaLadoALado(espacioHorizontal, espacioVertical)
    })
}

private class PoliticaLadoALado(private val espacioH: Dp, private val espacioV: Dp) : MeasurePolicy {

    private fun IntrinsicMeasureScope.entranJuntos(a: IntrinsicMeasurable, b: IntrinsicMeasurable, ancho: Int): Boolean =
        a.maxIntrinsicWidth(Constraints.Infinity) + espacioH.roundToPx() + b.maxIntrinsicWidth(Constraints.Infinity) <= ancho

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        require(measurables.size == 2) { "LadoALadoOApilados espera exactamente dos elementos" }
        val libres = constraints.copy(minWidth = 0, minHeight = 0)
        val a = measurables[0].measure(libres)
        val b = measurables[1].measure(libres)
        val h = espacioH.roundToPx()
        val juntos = a.width + h + b.width <= constraints.maxWidth
        val anchoContenido = if (juntos) a.width + h + b.width else maxOf(a.width, b.width)
        val ancho = if (constraints.hasBoundedWidth) constraints.maxWidth else anchoContenido
        val alto = (if (juntos) maxOf(a.height, b.height) else a.height + espacioV.roundToPx() + b.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(ancho, alto) {
            if (juntos) {
                a.place(0, (alto - a.height) / 2)
                b.place(ancho - b.width, (alto - b.height) / 2)
            } else {
                a.place(0, 0)
                b.place(0, a.height + espacioV.roundToPx())
            }
        }
    }

    private fun IntrinsicMeasureScope.alto(measurables: List<IntrinsicMeasurable>, width: Int, max: Boolean): Int {
        val (a, b) = measurables
        fun IntrinsicMeasurable.alto(w: Int) = if (max) maxIntrinsicHeight(w) else minIntrinsicHeight(w)
        return if (entranJuntos(a, b, width)) maxOf(a.alto(width), b.alto(width))
        else a.alto(width) + espacioV.roundToPx() + b.alto(width)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        alto(measurables, width, max = false)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        alto(measurables, width, max = true)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOf { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + espacioH.roundToPx()
}

/** Cuántas columnas de al menos [minimo] entran en [ancho] con [espacio] entre ellas (de 1 a [maximo]). */
fun columnasQueEntran(ancho: Dp, minimo: Dp, espacio: Dp, maximo: Int): Int =
    ((ancho + espacio) / (minimo + espacio)).toInt().coerceIn(1, maximo)

/**
 * Cuadros en filas: entran tantas columnas de al menos [anchoMinimo] como permita el ancho. En el
 * teléfono (escalado a [ANCHO_DISENO_DP]) quedan las de siempre; apaisado o en una tablet, más. Los
 * cuadros de una fila tienen la misma altura.
 */
@Composable
fun <T> GrillaAdaptable(
    elementos: List<T>,
    anchoMinimo: Dp,
    modifier: Modifier = Modifier,
    maxColumnas: Int = 5,
    espacio: Dp = 10.dp,
    celda: @Composable (elemento: T, modifier: Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnas = columnasQueEntran(maxWidth, anchoMinimo, espacio, maxColumnas)
        Column(verticalArrangement = Arrangement.spacedBy(espacio)) {
            elementos.chunked(columnas).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(espacio)
                ) {
                    fila.forEach { celda(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
