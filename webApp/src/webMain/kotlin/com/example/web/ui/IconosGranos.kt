package com.example.web.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Íconos de los granos, generados de app/src/main/res/drawable/ic_grano_*.xml (mismos trazos que la
 * app Android). Un solo color: se tiñen con el color de cada grano (GranoWeb). Si cambian los XML,
 * volver a generarlos en lugar de editar a mano.
 */
private fun grano(nombre: String, bloque: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply(bloque).build()

private fun ImageVector.Builder.relleno(datos: String, parImpar: Boolean = false) {
    addPath(
        pathData = addPathNodes(datos),
        pathFillType = if (parImpar) PathFillType.EvenOdd else PathFillType.NonZero,
        fill = SolidColor(Color.Black)
    )
}

private fun ImageVector.Builder.trazo(datos: String, ancho: Float) {
    addPath(pathData = addPathNodes(datos), stroke = SolidColor(Color.Black), strokeLineWidth = ancho, strokeLineCap = StrokeCap.Round)
}

object IconosGranos {
    val Soja = grano("GranoSoja") {
        relleno("M18.61,10.9 A4.1,4.1 0 0,0 12.33,5.63 L5.39,13.9 A4.1,4.1 0 0,0 11.67,19.17 Z M6.28,16.54 a2.25,2.25 0 1,0 4.5,0 a2.25,2.25 0 1,0 -4.5,0 Z M9.75,12.4 a2.25,2.25 0 1,0 4.5,0 a2.25,2.25 0 1,0 -4.5,0 Z M13.22,8.26 a2.25,2.25 0 1,0 4.5,0 a2.25,2.25 0 1,0 -4.5,0 Z", parImpar = true)
        relleno("M17.6,5.6 Q20.07,4.2 21.2,1.6 Q19.17,3.39 17.6,5.6 Z")
    }

    val Maiz = grano("GranoMaiz") {
        relleno("M12,1.6 A8,4.4 90 1,1 12,17.6 A8,4.4 90 1,1 12,1.6 Z M10.6,3.6 A1,0.85 90 1,1 10.6,5.6 A1,0.85 90 1,1 10.6,3.6 Z M13.4,3.6 A1,0.85 90 1,1 13.4,5.6 A1,0.85 90 1,1 13.4,3.6 Z M9.4,6.1 A1,0.85 90 1,1 9.4,8.1 A1,0.85 90 1,1 9.4,6.1 Z M12,6.1 A1,0.85 90 1,1 12,8.1 A1,0.85 90 1,1 12,6.1 Z M14.6,6.1 A1,0.85 90 1,1 14.6,8.1 A1,0.85 90 1,1 14.6,6.1 Z M9.4,8.6 A1,0.85 90 1,1 9.4,10.6 A1,0.85 90 1,1 9.4,8.6 Z M12,8.6 A1,0.85 90 1,1 12,10.6 A1,0.85 90 1,1 12,8.6 Z M14.6,8.6 A1,0.85 90 1,1 14.6,10.6 A1,0.85 90 1,1 14.6,8.6 Z M9.4,11.1 A1,0.85 90 1,1 9.4,13.1 A1,0.85 90 1,1 9.4,11.1 Z M12,11.1 A1,0.85 90 1,1 12,13.1 A1,0.85 90 1,1 12,11.1 Z M14.6,11.1 A1,0.85 90 1,1 14.6,13.1 A1,0.85 90 1,1 14.6,11.1 Z M10.6,13.6 A1,0.85 90 1,1 10.6,15.6 A1,0.85 90 1,1 10.6,13.6 Z M13.4,13.6 A1,0.85 90 1,1 13.4,15.6 A1,0.85 90 1,1 13.4,13.6 Z", parImpar = true)
        relleno("M12,22.8 Q5.34,17.77 4.2,9.5 Q9.07,15.58 12,22.8 Z")
        relleno("M12,22.8 Q18.66,17.77 19.8,9.5 Q14.93,15.58 12,22.8 Z")
    }

    val Trigo = grano("GranoTrigo") {
        relleno("M11.45,9.5 L12.55,9.5 L12.55,23 L11.45,23 Z M12,3 A2.2,1.05 90 1,1 12,7.4 A2.2,1.05 90 1,1 12,3 Z M8.69,6.53 A2.25,1.05 56 1,1 11.21,10.27 A2.25,1.05 56 1,1 8.69,6.53 Z M15.31,6.53 A2.25,1.05 124 1,1 12.79,10.27 A2.25,1.05 124 1,1 15.31,6.53 Z M8.69,9.83 A2.25,1.05 56 1,1 11.21,13.57 A2.25,1.05 56 1,1 8.69,9.83 Z M15.31,9.83 A2.25,1.05 124 1,1 12.79,13.57 A2.25,1.05 124 1,1 15.31,9.83 Z M8.69,13.13 A2.25,1.05 56 1,1 11.21,16.87 A2.25,1.05 56 1,1 8.69,13.13 Z M15.31,13.13 A2.25,1.05 124 1,1 12.79,16.87 A2.25,1.05 124 1,1 15.31,13.13 Z")
        trazo("M12,3 L12,0.8 M8.69,6.53 L7.41,4.63 M15.31,6.53 L16.59,4.63 M8.69,9.83 L7.41,7.93 M15.31,9.83 L16.59,7.93 M8.69,13.13 L7.41,11.23 M15.31,13.13 L16.59,11.23", 0.9f)
    }

    val Sorgo = grano("GranoSorgo") {
        relleno("M11.6,1.9 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M10.5,3.7 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M12.7,3.7 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M9.4,5.5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M11.6,5.5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M13.8,5.5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M8.9,7.3 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M11.1,7.3 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M13.3,7.3 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M15.3,7.3 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M9.4,9.1 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M11.6,9.1 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M13.8,9.1 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M10.3,10.9 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M12.5,10.9 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M14.4,10.9 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M11.4,12.6 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M13.4,12.6 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z")
        relleno("M12.8,13.6 C13.4,16.6 12.8,19.8 11.5,23 L12.9,23 C14.2,19.7 14.8,16.5 14.1,13.4 Z")
        relleno("M12.6,18.6 Q8.01,18.1 5,14.6 Q9.08,16.07 12.6,18.6 Z")
    }

    val Girasol = grano("GranoGirasol") {
        relleno("M12,1.7 A3,1.75 90 1,1 12,7.7 A3,1.75 90 1,1 12,1.7 Z M18.05,3.67 A3,1.75 126 1,1 14.53,8.52 A3,1.75 126 1,1 18.05,3.67 Z M21.8,8.82 A3,1.75 162 1,1 16.09,10.67 A3,1.75 162 1,1 21.8,8.82 Z M21.8,15.18 A3,1.75 198 1,1 16.09,13.33 A3,1.75 198 1,1 21.8,15.18 Z M18.05,20.33 A3,1.75 234 1,1 14.53,15.48 A3,1.75 234 1,1 18.05,20.33 Z M12,22.3 A3,1.75 270 1,1 12,16.3 A3,1.75 270 1,1 12,22.3 Z M5.95,20.33 A3,1.75 306 1,1 9.47,15.48 A3,1.75 306 1,1 5.95,20.33 Z M2.2,15.18 A3,1.75 342 1,1 7.91,13.33 A3,1.75 342 1,1 2.2,15.18 Z M2.2,8.82 A3,1.75 378 1,1 7.91,10.67 A3,1.75 378 1,1 2.2,8.82 Z M5.95,3.67 A3,1.75 414 1,1 9.47,8.52 A3,1.75 414 1,1 5.95,3.67 Z")
        relleno("M7.8,12 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M11.25,12 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M9.45,10.9 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M13.05,10.9 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M9.45,13.1 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M13.05,13.1 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M11.25,9.8 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z M11.25,14.2 a0.75,0.75 0 1,0 1.5,0 a0.75,0.75 0 1,0 -1.5,0 Z", parImpar = true)
    }
}

