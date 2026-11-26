package com.example.data.agenda

/**
 * Ícono de cada opción del menú de productos. Cada plataforma lo dibuja con lo suyo (Android con sus
 * íconos de granos y de Material, la web con IconosGranex); acá solo se dice qué representa.
 */
enum class IconoProducto {
    SOJA, MAIZ, TRIGO, GIRASOL, SORGO, SEMILLA,
    FERTILIZANTE, HERBICIDA, INSECTICIDA, FUNGICIDA, COADYUVANTE,
    SIEMBRA, PULVERIZACION, COSECHA, FLETE, LABRANZA, SERVICIO,
    // Maquinaria de las labores, con dibujo propio (TrazosIconos)
    PULVERIZADORA, AVION, COSECHADORA, EMBOLSADORA, EXTRACTORA, SEMBRADORA, FERTILIZADORA, RASTRA, ROLO, CAMION, MONITOREO,
    COMBUSTIBLE, LUBRICANTE,
    SEGURO, ANALISIS, ARRENDAMIENTO, MANO_DE_OBRA, REPARACION, OTRO,
    /** Un producto que el usuario ya cargó antes (no está en el catálogo). */
    USADO
}

/** Una opción del menú: el nombre que se completa, su ícono y la unidad con que suele comprarse. */
data class ProductoSugerido(val nombre: String, val icono: IconoProducto, val unidad: String)

/**
 * Productos y labores habituales por categoría, para elegir de un menú en vez de escribir. El usuario
 * siempre puede escribir otro: el menú solo completa el nombre (y la unidad, si todavía no la cambió).
 * Son nombres genéricos (principio activo o tipo), sin marcas comerciales.
 */
object CatalogoProductos {

    private fun p(nombre: String, icono: IconoProducto, unidad: String) = ProductoSugerido(nombre, icono, unidad)

    val porCategoria: Map<CategoriaGasto, List<ProductoSugerido>> = mapOf(
        CategoriaGasto.SEMILLA to listOf(
            p("Semilla de soja", IconoProducto.SOJA, "bolsas"),
            p("Semilla de maíz", IconoProducto.MAIZ, "bolsas"),
            p("Semilla de trigo", IconoProducto.TRIGO, "kg"),
            p("Semilla de girasol", IconoProducto.GIRASOL, "bolsas"),
            p("Semilla de sorgo", IconoProducto.SORGO, "bolsas"),
            p("Semilla de cebada", IconoProducto.TRIGO, "kg"),
            p("Semilla de maní", IconoProducto.SEMILLA, "kg"),
            p("Semilla de alfalfa", IconoProducto.SEMILLA, "kg"),
            p("Semilla de vicia (cultivo de servicio)", IconoProducto.SEMILLA, "kg"),
            p("Inoculante", IconoProducto.SEMILLA, "unidades"),
            p("Curasemilla", IconoProducto.FUNGICIDA, "L")
        ),
        CategoriaGasto.FERTILIZANTE to listOf(
            p("Urea 46 %", IconoProducto.FERTILIZANTE, "kg"),
            p("Fosfato monoamónico (MAP 11-52-0)", IconoProducto.FERTILIZANTE, "kg"),
            p("Fosfato diamónico (DAP 18-46-0)", IconoProducto.FERTILIZANTE, "kg"),
            p("Superfosfato triple (SPT)", IconoProducto.FERTILIZANTE, "kg"),
            p("Superfosfato simple (SPS)", IconoProducto.FERTILIZANTE, "kg"),
            p("UAN 32 % (solución nitrogenada)", IconoProducto.FERTILIZANTE, "L"),
            p("Sulfato de amonio", IconoProducto.FERTILIZANTE, "kg"),
            p("Tiosulfato de amonio", IconoProducto.FERTILIZANTE, "L"),
            p("Nitrato de amonio calcáreo (CAN)", IconoProducto.FERTILIZANTE, "kg"),
            p("Mezcla física NPKS", IconoProducto.FERTILIZANTE, "kg"),
            p("Yeso agrícola", IconoProducto.FERTILIZANTE, "kg"),
            p("Fertilizante foliar", IconoProducto.FERTILIZANTE, "L")
        ),
        CategoriaGasto.FITOSANITARIO to listOf(
            p("Glifosato", IconoProducto.HERBICIDA, "L"),
            p("2,4-D", IconoProducto.HERBICIDA, "L"),
            p("Dicamba", IconoProducto.HERBICIDA, "L"),
            p("Atrazina", IconoProducto.HERBICIDA, "kg"),
            p("S-metolacloro", IconoProducto.HERBICIDA, "L"),
            p("Glufosinato de amonio", IconoProducto.HERBICIDA, "L"),
            p("Paraquat", IconoProducto.HERBICIDA, "L"),
            p("Cletodim", IconoProducto.HERBICIDA, "L"),
            p("Imazetapir", IconoProducto.HERBICIDA, "L"),
            p("Flumioxazin", IconoProducto.HERBICIDA, "kg"),
            p("Metsulfurón metil", IconoProducto.HERBICIDA, "kg"),
            p("Lambdacialotrina", IconoProducto.INSECTICIDA, "L"),
            p("Bifentrin", IconoProducto.INSECTICIDA, "L"),
            p("Clorantraniliprole", IconoProducto.INSECTICIDA, "L"),
            p("Acetamiprid", IconoProducto.INSECTICIDA, "kg"),
            p("Azoxistrobina + ciproconazole", IconoProducto.FUNGICIDA, "L"),
            p("Tebuconazole", IconoProducto.FUNGICIDA, "L"),
            p("Coadyuvante / aceite", IconoProducto.COADYUVANTE, "L")
        ),
        CategoriaGasto.LABOR to listOf(
            p("Siembra", IconoProducto.SEMBRADORA, "ha"),
            p("Pulverización terrestre", IconoProducto.PULVERIZADORA, "ha"),
            p("Pulverización aérea", IconoProducto.AVION, "ha"),
            p("Fertilización", IconoProducto.FERTILIZADORA, "ha"),
            p("Cosecha", IconoProducto.COSECHADORA, "ha"),
            p("Embolsado (silo bolsa)", IconoProducto.EMBOLSADORA, "unidades"),
            p("Extracción de silo bolsa", IconoProducto.EXTRACTORA, "unidades"),
            p("Flete corto", IconoProducto.CAMION, "t"),
            p("Flete largo", IconoProducto.CAMION, "t"),
            p("Labranza / rastra", IconoProducto.RASTRA, "ha"),
            p("Rolado", IconoProducto.ROLO, "ha"),
            p("Monitoreo de plagas", IconoProducto.MONITOREO, "ha")
        ),
        CategoriaGasto.COMBUSTIBLE to listOf(
            p("Gasoil", IconoProducto.COMBUSTIBLE, "L"),
            p("Nafta", IconoProducto.COMBUSTIBLE, "L"),
            p("Aceite de motor", IconoProducto.LUBRICANTE, "L"),
            p("Aceite hidráulico", IconoProducto.LUBRICANTE, "L"),
            p("Grasa", IconoProducto.LUBRICANTE, "kg")
        ),
        CategoriaGasto.OTRO to listOf(
            p("Seguro agrícola", IconoProducto.SEGURO, "ha"),
            p("Análisis de suelo", IconoProducto.ANALISIS, "unidades"),
            p("Asesoramiento agronómico", IconoProducto.SERVICIO, "ha"),
            p("Arrendamiento", IconoProducto.ARRENDAMIENTO, "ha"),
            p("Mano de obra", IconoProducto.MANO_DE_OBRA, "horas"),
            p("Reparación de maquinaria", IconoProducto.REPARACION, "unidades"),
            p("Repuestos", IconoProducto.REPARACION, "unidades"),
            p("Bolsas para silo", IconoProducto.EMBOLSADORA, "unidades"),
            p("Impuesto inmobiliario rural", IconoProducto.OTRO, "unidades")
        )
    )

    /**
     * Opciones del menú para lo que el usuario va escribiendo: primero lo que ya cargó en esa categoría
     * (con ícono de "usado"), después el catálogo. Sin texto muestra todo; con texto, lo que contiene
     * esas letras sin importar acentos ni mayúsculas. No repite el texto exacto que ya está escrito.
     */
    fun sugerencias(
        categoria: CategoriaGasto,
        texto: String,
        usados: List<Pair<CategoriaGasto, String>> = emptyList(),
        maximo: Int = 30
    ): List<ProductoSugerido> {
        val buscado = normalizar(texto)
        val catalogo = porCategoria[categoria].orEmpty()
        val enCatalogo = catalogo.map { normalizar(it.nombre) }.toSet()
        val propios = usados.filter { it.first == categoria }
            .map { it.second.trim() }
            .filter { it.isNotEmpty() && normalizar(it) !in enCatalogo }
            .distinctBy { normalizar(it) }
            .map { ProductoSugerido(it, IconoProducto.USADO, "") }
        return (propios + catalogo)
            .filter { buscado.isEmpty() || normalizar(it.nombre).contains(buscado) }
            .filterNot { normalizar(it.nombre) == buscado }
            .take(maximo)
    }

    /** Minúsculas y sin acentos, para comparar lo escrito con el catálogo. */
    fun normalizar(s: String): String = buildString {
        for (c in s.trim().lowercase()) {
            append(
                when (c) {
                    'á', 'à', 'ä', 'â' -> 'a'
                    'é', 'è', 'ë', 'ê' -> 'e'
                    'í', 'ì', 'ï', 'î' -> 'i'
                    'ó', 'ò', 'ö', 'ô' -> 'o'
                    'ú', 'ù', 'ü', 'û' -> 'u'
                    else -> c
                }
            )
        }
    }
}
