package com.example.data.agenda

/**
 * Dibujos propios de la maquinaria y las labores del menú de productos (no hay íconos de Material para
 * una pulverizadora autopropulsada o una extractora de silo bolsa). Son trazos SVG de 24 × 24 rellenos,
 * los mismos para Android y la web: cada plataforma arma su ImageVector con ellos.
 */
object TrazosIconos {

    /** Pulverizadora autopropulsada ("araña" o "mosquito"): ruedas altas, cabina y botalón con picos. */
    val PULVERIZADORA = listOf(
        "M1,10.2h22v1.3H1z",                                                     // botalón
        "M1.4,11.5h1v2h-1z M5.4,11.5h1v2h-1z M17.6,11.5h1v2h-1z M21.6,11.5h1v2h-1z", // picos
        "M0.9,15.5l1,-2 1,2z M4.9,15.5l1,-2 1,2z M17.1,15.5l1,-2 1,2z M21.1,15.5l1,-2 1,2z", // abanicos de gotas
        "M7.6,2.6h5.4v5.6H7.6z M8.6,3.6v2.6h3.4V3.6z",                           // cabina con ventana (el hueco va al revés)
        "M13.6,4.6h3.6a1.6,1.6 0 0,1 1.6,1.6v2H13.6z",                           // tanque
        "M7.2,8.2h12v2h-12z",                                                    // chasis
        "M8.4,11.5h1.4v4.5H8.4z M16.4,11.5h1.4v4.5h-1.4z",                       // patas altas
        "M7.3,19a1.8,2.8 0 1,0 3.6,0a1.8,2.8 0 1,0 -3.6,0z M15.3,19a1.8,2.8 0 1,0 3.6,0a1.8,2.8 0 1,0 -3.6,0z" // ruedas angostas
    )

    /** Avión de pulverización aérea (silueta "flight" de Material, Apache 2.0). */
    val AVION = listOf(
        "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z"
    )

    /** Cosechadora: cabezal adelante, cabina, cuerpo, tubo de descarga, rueda grande y rueda trasera. */
    val COSECHADORA = listOf(
        "M0.6,11h6.6v4.6H2.2z",                    // cabezal
        "M1.8,9.6a1.8,1.8 0 1,0 3.6,0a1.8,1.8 0 1,0 -3.6,0z", // molinete
        "M7,8h11.2l2.2,3.2v4.4H7z",                // cuerpo
        "M7.4,3.2h4.3l0.6,4.8H7z M8.3,4.1l-0.3,2.9h3.3l-0.3,-2.9z", // cabina con ventana
        "M12.8,4.6h5.2v3.4h-5.2z",                 // tolva de grano
        "M18,4.9h4.6v1.2H18z M21.4,6.1h1.2v2h-1.2z", // sinfín de descarga y su boca
        "M6.8,17.6a3.7,3.7 0 1,0 7.4,0a3.7,3.7 0 1,0 -7.4,0z", // rueda delantera
        "M16.2,18.6a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0z" // rueda trasera
    )

    /** Embolsadora: tolva que recibe el grano y la bolsa larga que sale atrás. */
    val EMBOLSADORA = listOf(
        "M1.5,4h7.5l-1.6,5H3.1z",                 // tolva
        "M1.5,9.5h7.5v5.5H1.5z",                  // cuerpo
        "M9.6,10h10.6a2.8,2.8 0 0,1 0,5.6H9.6z",  // silo bolsa
        "M2.6,17.2a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z" // rueda
    )

    /** Extractora de silo bolsa: la bolsa atrás y el sinfín que sube a cargar el camión. */
    val EXTRACTORA = listOf(
        "M3.8,10.5h8.4v5.6H3.8a2.8,2.8 0 0,1 0,-5.6z", // silo bolsa
        "M12.6,9h5.8v7.2h-5.8z",                       // cuerpo
        "M17.3,9.2l3.4,-6.2 1.3,0.7 -3.4,6.2z",        // sinfín
        "M20.2,1.6h3v1.6h-3z",                         // boca de descarga
        "M13.4,18.2a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z" // rueda
    )

    /** Sembradora: tolvas arriba, barra y discos que abren el surco. */
    val SEMBRADORA = listOf(
        "M2.4,3.5h4.6l-0.8,4.5H3.2z M9.7,3.5h4.6l-0.8,4.5h-3z M17,3.5h4.6l-0.8,4.5h-3z", // tolvas
        "M1.5,8.6h21v1.6h-21z",                                                       // barra
        "M4.2,10.2h1v1.6h-1z M11.5,10.2h1v1.6h-1z M18.8,10.2h1v1.6h-1z",              // brazos
        "M2.7,14.2a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z M10,14.2a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z M17.3,14.2a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z", // discos
        "M4.3,18.6a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0z M11.6,18.6a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0z M18.9,18.6a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0z" // semillas
    )

    /** Fertilizadora al voleo: tolva, plato que gira y el fertilizante que sale despedido. */
    val FERTILIZADORA = listOf(
        "M5,3h14l-5,7.5h-4z",                      // tolva
        "M11.2,10.5h1.6v1.5h-1.6z",                // eje
        "M6.5,12h11v1.5h-11z",                     // plato
        "M2,16.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z M6.5,18.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z M11,19.8a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z M15.5,18.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z M20,16.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z" // gránulos
    )

    /** Rastra de discos: el bastidor y la fila de discos inclinados. */
    val RASTRA = listOf(
        "M1.5,7.5h21v1.7h-21z",                    // bastidor
        "M0.5,7.9h1.5v0.9H0.5z",                   // enganche
        "M3.9,9.2h1v2h-1z M8.9,9.2h1v2h-1z M13.9,9.2h1v2h-1z M18.9,9.2h1v2h-1z", // brazos
        "M2.7,14.5a1.7,3.3 0 1,0 3.4,0a1.7,3.3 0 1,0 -3.4,0z M7.7,14.5a1.7,3.3 0 1,0 3.4,0a1.7,3.3 0 1,0 -3.4,0z " +
            "M12.7,14.5a1.7,3.3 0 1,0 3.4,0a1.7,3.3 0 1,0 -3.4,0z M17.7,14.5a1.7,3.3 0 1,0 3.4,0a1.7,3.3 0 1,0 -3.4,0z" // discos
    )

    /** Rolo de cuchillas (rolado): el tambor con sus cuchillas y el brazo de tiro. */
    val ROLO = listOf(
        "M6.5,10h12a3,3 0 0,1 0,6h-12a3,3 0 0,1 0,-6z", // tambor de costado
        "M7.6,8h1.4v2H7.6z M11.1,8h1.4v2h-1.4z M14.6,8H16v2h-1.4z M18.1,8h1.4v2h-1.4z", // cuchillas de arriba
        "M7.6,16h1.4v2H7.6z M11.1,16h1.4v2h-1.4z M14.6,16H16v2h-1.4z M18.1,16h1.4v2h-1.4z", // cuchillas de abajo
        "M0.6,12.4h3.4v1.2H0.6z",                  // barra de tiro
        "M3.6,10.6l1.6,-0.8v6.4l-1.6,-0.8z"        // cabezal del enganche
    )

    /** Camión para el flete: chasis con caja y cabina. */
    val CAMION = listOf(
        "M1.5,5.5h12.5v10H1.5z",                   // caja
        "M14.5,8.5h4.2l3.3,3.5v3.5h-7.5z",         // cabina
        "M3.2,17.6a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z M15.6,17.6a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z" // ruedas
    )

    /** Lupa del monitoreo de plagas ("search" de Material, Apache 2.0). */
    val MONITOREO = listOf(
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16" +
            "c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    )

    /** El dibujo propio de un ícono; null si la plataforma usa uno suyo (granos, Material, IconosGranex). */
    fun de(icono: IconoProducto): List<String>? = when (icono) {
        IconoProducto.PULVERIZADORA -> PULVERIZADORA
        IconoProducto.AVION -> AVION
        IconoProducto.COSECHADORA -> COSECHADORA
        IconoProducto.EMBOLSADORA -> EMBOLSADORA
        IconoProducto.EXTRACTORA -> EXTRACTORA
        IconoProducto.SEMBRADORA -> SEMBRADORA
        IconoProducto.FERTILIZADORA -> FERTILIZADORA
        IconoProducto.RASTRA -> RASTRA
        IconoProducto.ROLO -> ROLO
        IconoProducto.CAMION -> CAMION
        IconoProducto.MONITOREO -> MONITOREO
        else -> null
    }
}
