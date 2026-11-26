package com.example.web.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Íconos de Material Symbols que no están en material-icons-core. Se evita material-icons-extended
 * porque agrega varios MB al .wasm. Trazos de 24×24 (licencia Apache 2.0, Google).
 */
private fun icono(nombre: String, vararg trazos: String): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        trazos.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

/** Como [icono], pero los trazos internos recortan (renglones de la libreta). */
private fun iconoConHuecos(nombre: String, trazo: String): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(pathData = addPathNodes(trazo), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
    }.build()

object IconosGranex {
    val Dolar = icono(
        "Dolar",
        "M11.8,10.9c-2.27,-0.59 -3,-1.2 -3,-2.15 0,-1.09 1.01,-1.85 2.7,-1.85 1.78,0 2.44,0.85 2.5,2.1h2.21" +
            "c-0.07,-1.72 -1.12,-3.3 -3.21,-3.81V3h-3v2.16c-1.94,0.42 -3.5,1.68 -3.5,3.61 0,2.31 1.91,3.46 4.7,4.13 " +
            "2.5,0.6 3,1.48 3,2.41 0,0.69 -0.49,1.79 -2.7,1.79 -2.06,0 -2.87,-0.92 -2.98,-2.1h-2.2c0.12,2.19 1.76,3.42 " +
            "3.68,3.83V21h3v-2.15c1.95,-0.37 3.5,-1.5 3.5,-3.55 0,-2.84 -2.43,-3.81 -4.7,-4.4z"
    )
    val Nube = icono(
        "Nube",
        "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13" +
            "c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z"
    )
    val Mapa = icono(
        "Mapa",
        "M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 0.5,0.5" +
            "l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5z" +
            "M15,19l-6,-2.11V5l6,2.11V19z"
    )
    val Grafico = icono("Grafico", "M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99z")
    val Noticia = icono(
        "Noticia",
        "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
            "M14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z"
    )

    /** open_in_new: el enlace abre otro sitio en una pestaña nueva. */
    val AbrirAfuera = icono(
        "AbrirAfuera",
        "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
            "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z"
    )

    /** brightness_6: modo claro / oscuro. */
    val Tema = icono(
        "Tema",
        "M20,15.31L23.31,12 20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69z" +
            "M12,18V6c3.31,0 6,2.69 6,6s-2.69,6 -6,6z"
    )

    /** push_pin: fijar el menú abierto. */
    val Fijar = icono(
        "Fijar",
        "M16,9V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3z"
    )

    /** visibility: mostrar la contraseña. */
    val Ojo = icono(
        "Ojo",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
    )

    /** visibility_off: ocultar la contraseña. */
    val OjoTachado = icono(
        "OjoTachado",
        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
            " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
            "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84" +
            "l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
            "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08" +
            "l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
            "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z"
    )

    /** undo */
    val Deshacer = icono(
        "Deshacer",
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88" +
            " 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"
    )

    /** redo */
    val Rehacer = icono(
        "Rehacer",
        "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5" +
            " 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z"
    )

    // --- Clima (los mismos de material-icons-extended que usa DashboardClimaRadar en Android) ---

    /** wb_sunny */
    val Sol = icono(
        "Sol",
        "M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5L1,10.5v2h3v-2zM13,0.55h-2L11,3.5h2L13,0.55z" +
            "M20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79,1.8 1.41,-1.41 -1.8,-1.79 -1.4,1.4z" +
            "M20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM11,22.45h2L13,19.5h-2v2.95z" +
            "M3.55,18.54l1.41,1.41 1.79,-1.8 -1.41,-1.41 -1.79,1.8z"
    )

    /** bedtime: noche despejada */
    val Luna = icono(
        "Luna",
        "M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10c3.71,0 6.93,-2.02 8.66,-5.02C13.15,16.73 8.57,8.55 12.34,2.02z"
    )

    /** water_drop */
    val Gota = icono(
        "Gota",
        "M12,2c-5.33,4.55 -8,8.48 -8,11.8c0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2C20,10.48 17.33,6.55 12,2z" +
            "M7.83,14c0.37,0 0.67,0.26 0.74,0.62c0.41,2.22 2.28,2.98 3.64,2.87c0.43,-0.02 0.79,0.32 0.79,0.75" +
            "c0,0.4 -0.32,0.73 -0.72,0.75c-2.13,0.13 -4.62,-1.09 -5.19,-4.12C7.01,14.42 7.37,14 7.83,14z"
    )

    /** air */
    val Viento = icono(
        "Viento",
        "M14.5,17c0,1.65 -1.35,3 -3,3s-3,-1.35 -3,-3h2c0,0.55 0.45,1 1,1s1,-0.45 1,-1s-0.45,-1 -1,-1H2v-2h9.5" +
            "C13.15,14 14.5,15.35 14.5,17zM19,6.5C19,4.57 17.43,3 15.5,3S12,4.57 12,6.5h2C14,5.67 14.67,5 15.5,5" +
            "S17,5.67 17,6.5S16.33,8 15.5,8H2v2h13.5C17.43,10 19,8.43 19,6.5zM18.5,11H2v2h16.5c0.83,0 1.5,0.67 1.5,1.5" +
            "S19.33,16 18.5,16v2c1.93,0 3.5,-1.57 3.5,-3.5S20.43,11 18.5,11z"
    )

    /** thermostat */
    val Termometro = icono(
        "Termometro",
        "M15,13V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v8c-1.21,0.91 -2,2.37 -2,4c0,2.76 2.24,5 5,5s5,-2.24 5,-5" +
            "C17,15.37 16.21,13.91 15,13zM11,11V5c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v1h-1v1h1v1v1h-1v1h1v1H11z"
    )

    /** compress: presión atmosférica */
    val Presion = icono("Presion", "M8,19h3v3h2v-3h3l-4,-4l-4,4zM16,4h-3V1h-2v3H8l4,4l4,-4zM4,9v2h16V9H4zM4,12h16v2H4V12z")

    /** flash_on: tormenta */
    val Rayo = icono("Rayo", "M7,2v11h3v9l7,-12h-4l4,-8z")

    /** trending_up / trending_down: variación de precio */
    val Sube = icono("Sube", "M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12V6z")
    val Baja = icono("Baja", "M16,18l2.29,-2.29 -4.88,-4.88 -4,4L2,7.41 3.41,6l6,6 4,-4 6.3,6.29L22,12v6z")

    /** cloud_upload: cambios para subir a la nube. */
    val Subir = icono(
        "Subir",
        "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13" +
            "c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM14,13v4h-4v-4H7l5,-5 5,5h-3z"
    )

    /** cloud_off: sin conexión. */
    val SinConexion = icono(
        "SinConexion",
        "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6" +
            "c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45" +
            "C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
            "M3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27z" +
            "M7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z"
    )

    // ---------- Agenda (pestaña y menú de productos) ----------

    /** Libreta con renglones (la pestaña Agenda). */
    val Agenda = iconoConHuecos(
        "Agenda",
        "M6,2h12a2,2 0 0,1 2,2v16a2,2 0 0,1 -2,2H6a2,2 0 0,1 -2,-2V4a2,2 0 0,1 2,-2z" +
            "M8,7v2h8V7z M8,11v2h8v-2z M8,15v2h5v-2z"
    )
    val Brote = icono("Brote", "M11,13h2v9h-2z", "M12,12c0,-4 3,-7 7,-7 0,4 -3,7 -7,7z", "M12,14c0,-3 -2.5,-5.5 -6,-5.5 0,3 2.5,5.5 6,5.5z")
    val Matraz = icono("Matraz", "M9,2h6v2h-1v5.3l5.6,9.7c0.8,1.3 -0.2,3 -1.7,3H6.1c-1.5,0 -2.5,-1.7 -1.7,-3L10,9.3V4H9z")
    val Hoja = icono("Hoja", "M5,21c0,-8 4,-14 14,-16 -1,9 -6,15 -14,16z")
    val Insecto = icono(
        "Insecto",
        "M12,6a4,4 0 0,1 4,4v6a4,4 0 0,1 -8,0v-6a4,4 0 0,1 4,-4z",
        "M10,2.5h4v3h-4z",
        "M3,10h4.5v1.6H3z", "M16.5,10H21v1.6h-4.5z", "M3,15h4.5v1.6H3z", "M16.5,15H21v1.6h-4.5z"
    )
    val Tractor = icono(
        "Tractor",
        "M3,17a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
        "M15,18a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0z",
        "M4,8h7l2,5h7v3h-1.2a3.5,3.5 0 0,0 -5.6,0H12.3A5,5 0 0,0 4,13.6z",
        "M7.5,4H9v4H7.5z"
    )
    val Camion = icono(
        "Camion",
        "M2,6h12v10H2z", "M14,9h4l3,3v4h-7z",
        "M4,18a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z", "M15,18a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z"
    )
    val Surtidor = icono(
        "Surtidor",
        "M5,3h8v18H5z",
        "M14,8h2v8a1,1 0 0,0 2,0V9l-2,-2 1.4,-1.4 2.6,2.6V16a3,3 0 0,1 -6,0z"
    )
    val Escudo = icono("Escudo", "M12,2l8,3v6c0,5 -3.4,9.4 -8,11 -4.6,-1.6 -8,-6 -8,-11V5z")
}

