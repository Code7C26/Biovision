package com.example.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clase de ancho del área de contenido (criterio de las "window size classes" de Material 3):
 * - COMPACTA (< 600 dp): teléfono, una columna y barra de navegación abajo.
 * - MEDIANA (600–1199 dp): tablet o ventana angosta, dos columnas y riel lateral.
 * - EXPANDIDA (≥ 1200 dp): escritorio y monitores, tres columnas y menú lateral con nombres.
 */
enum class ClaseAncho {
    COMPACTA, MEDIANA, EXPANDIDA;

    companion object {
        fun de(ancho: Dp): ClaseAncho = when {
            ancho < 600.dp -> COMPACTA
            ancho < 1200.dp -> MEDIANA
            else -> EXPANDIDA
        }
    }
}

/** Clase de ancho de la ventana, la provee GranexWeb para que cada sección arme sus columnas. */
val LocalClaseAncho = compositionLocalOf { ClaseAncho.COMPACTA }

/** Separación entre paneles y margen de las secciones. */
val EspacioPanel = 16.dp

/**
 * Tablero de paneles en columnas. Cada elemento de [columnas] es una columna (lista de paneles) y cada
 * columna hace scroll por separado, como un tablero de escritorio. En COMPACTA todos los paneles se
 * apilan, en el mismo orden, en una sola columna con scroll: la vista de teléfono de siempre.
 * La sección elige el reparto según [LocalClaseAncho] (p. ej. 2 columnas en MEDIANA y 3 en EXPANDIDA).
 */
@Composable
fun Tablero(
    columnas: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    pesos: List<Float> = emptyList()
) {
    if (LocalClaseAncho.current == ClaseAncho.COMPACTA || columnas.size == 1) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EspacioPanel),
            verticalArrangement = Arrangement.spacedBy(EspacioPanel)
        ) { columnas.flatten().forEach { it() } }
        return
    }
    Row(
        modifier.fillMaxSize().padding(horizontal = EspacioPanel),
        horizontalArrangement = Arrangement.spacedBy(EspacioPanel)
    ) {
        columnas.forEachIndexed { i, paneles ->
            Column(
                Modifier.weight(pesos.getOrElse(i) { 1f }).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(vertical = EspacioPanel),
                verticalArrangement = Arrangement.spacedBy(EspacioPanel)
            ) { paneles.forEach { it() } }
        }
    }
}

/** Ancho máximo del contenido en monitores muy anchos (las líneas de texto no se estiran de borde a borde). */
val AnchoMaximoContenido = 1680.dp

/**
 * Comportamiento del menú lateral de escritorio (EXPANDIDA), elegido por el usuario y guardado como
 * "web_menu" en localStorage:
 * - FIJO: siempre abierto, con íconos y nombres; el contenido empieza a su derecha.
 * - AL_PASAR: riel de íconos; al pasar el mouse (o al llegar con Tab) se abre sobre el contenido,
 *   sin moverlo, y se vuelve a cerrar al salir.
 */
enum class ModoMenu(val clave: String) {
    FIJO("fijo"),
    AL_PASAR("al_pasar");

    companion object {
        fun deClave(clave: String?): ModoMenu = entries.firstOrNull { it.clave == clave } ?: FIJO
    }
}
