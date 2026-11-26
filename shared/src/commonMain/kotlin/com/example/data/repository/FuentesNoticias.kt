package com.example.data.repository

/** Filtros temáticos de Novedades. Una nota puede pertenecer a varios. */
enum class TemaNoticia(val etiqueta: String) {
    ECONOMIA_FINANZAS("Economía & Finanzas"),
    MAQUINARIA_TECNOLOGIA("Maquinaria & Tecnología"),
    INSUMOS_MERCADOS("Insumos & Mercados")
}

/** Rubro de la fuente: los medios económicos aportan siempre al filtro de Economía. */
enum class RubroFuente { AGRO, ECONOMIA }

/**
 * Medio confiable con su RSS oficial. [dominio] es el único host admitido para los enlaces de sus
 * notas (descarta redirecciones a terceros). [maxNotas] evita que un medio con 60 ítems tape al resto.
 */
data class FuenteNoticias(
    val nombre: String,
    val url: String,
    val dominio: String,
    val rubro: RubroFuente,
    val maxNotas: Int = 15
)

/**
 * En :shared para que la web clasifique igual que Android. La función supabase/functions/publico,
 * que lee estos feeds para la web, tiene una copia de esta lista: mantenerlas iguales.
 *
 * Fuentes verificadas el 21/10/2026 (todas responden 200 con ítems del día). Las URLs que antes
 * usaba la app para Agrofy (/rss y /feed) daban 404: el feed real es /rss.xml.
 */
val FUENTES_NOTICIAS = listOf(
    // Agro y maquinarias
    FuenteNoticias("Clarín Rural", "https://www.clarin.com/rss/rural/", "clarin.com", RubroFuente.AGRO),
    FuenteNoticias("La Nación Campo", "https://www.lanacion.com.ar/arc/outboundfeeds/rss/category/economia/campo/?outputType=xml", "lanacion.com.ar", RubroFuente.AGRO),
    FuenteNoticias("La Voz Agro", "https://www.lavoz.com.ar/rss/agro/", "lavoz.com.ar", RubroFuente.AGRO),
    FuenteNoticias("Agrofy News", "https://news.agrofy.com.ar/rss.xml", "agrofy.com.ar", RubroFuente.AGRO),
    FuenteNoticias("Agrositio", "https://www.agrositio.com.ar/rss/rss.php?area=destacadas", "agrositio.com.ar", RubroFuente.AGRO),
    FuenteNoticias("Bichos de Campo", "https://bichosdecampo.com/feed/", "bichosdecampo.com", RubroFuente.AGRO),
    FuenteNoticias("Infocampo", "https://www.infocampo.com.ar/feed/", "infocampo.com.ar", RubroFuente.AGRO),
    // Economía y mercados
    FuenteNoticias("El Cronista", "https://www.cronista.com/arc/outboundfeeds/rss/category/economia-politica/?outputType=xml", "cronista.com", RubroFuente.ECONOMIA, maxNotas = 12)
)

/** Clasifica notas por palabras clave (título, bajada y categorías del feed). Puro: testeado en NoticiasTest. */
object ClasificadorNoticias {

    private val MAQUINARIA = listOf(
        "tractor", "cosechadora", "sembradora", "pulverizadora", "maquinaria", "maquina agricola", "fierros",
        "agtech", "dron", "drones", "agricultura de precision", "tasa variable", "vra", "monitor de rendimiento",
        "piloto automatico", "autoguiado", "satelit", "inteligencia artificial", "software", "robot", "telemetria",
        "camion", "camiones", "tecnologia", "agricultura digital", "biotecnologia", "conectividad rural"
    )
    private val INSUMOS = listOf(
        "fertiliz", "urea", "fosfato", "nitrogen", "semilla", "fitosanitario", "herbicida", "fungicida",
        "insecticida", "agroquimic", "glifosato", "insumo", "bioinsumo", "biologico", "gasoil", "combustible",
        "nafta", "pizarra", "matba", "rofex", "chicago", "precio de la soja", "precio del maiz", "precio del trigo",
        "precios de los granos", "mercado de granos", "cotizacion", "tonelada"
    )
    private val ECONOMIA = listOf(
        "dolar", "inflacion", "ipc", "bcra", "banco central", "tasa de interes", "tasas", "credito", "creditos",
        "financiamiento", "prestamo", "retenciones", "derechos de exportacion", "impuesto", "fiscal", "indec",
        "actividad economica", "riesgo pais", "bonos", "merval", "cepo", "reservas", "fmi", "economia",
        "exportaciones", "importaciones", "salario", "presupuesto"
    )
    private val CLIMA = listOf("lluvia", "lluvias", "sequia", "helada", "heladas", "clima", "pronostico", "tormenta", "nino", "nina", "temperatura")
    private val GANADERIA = listOf("ganaderia", "ganado", "hacienda", "novillo", "vaca", "vacas", "carne", "tambo", "leche", "feedlot", "corrales", "bovino", "porcino", "cerdo")
    private val GRANOS = listOf("soja", "maiz", "trigo", "girasol", "sorgo", "cebada", "cosecha", "siembra", "granos")

    /** Un patrón por clave, armado una sola vez (las listas no cambian; sin synchronized, que no existe en la web). */
    private val patrones: Map<String, Regex> by lazy {
        (MAQUINARIA + INSUMOS + ECONOMIA + CLIMA + GANADERIA + GRANOS).associateWith { Regex("(^|[^a-z0-9])" + Regex.escape(it)) }
    }

    /** Coincidencia al inicio de palabra: "dron" no matchea en "padrón", "ipc" no en "participación". */
    private fun contiene(texto: String, clave: String): Boolean = patrones.getValue(clave).containsMatchIn(texto)

    private fun algun(texto: String, claves: List<String>) = claves.any { contiene(texto, it) }

    fun temas(titulo: String, descripcion: String, categoriasFeed: List<String>, rubro: RubroFuente): Set<TemaNoticia> {
        val texto = normalizar("$titulo $descripcion ${categoriasFeed.joinToString(" ")}")
        return buildSet {
            if (rubro == RubroFuente.ECONOMIA || algun(texto, ECONOMIA)) add(TemaNoticia.ECONOMIA_FINANZAS)
            if (algun(texto, MAQUINARIA)) add(TemaNoticia.MAQUINARIA_TECNOLOGIA)
            if (algun(texto, INSUMOS)) add(TemaNoticia.INSUMOS_MERCADOS)
        }
    }

    /** Etiqueta visible en la tarjeta (y elige la imagen de respaldo). Se prioriza el título. */
    fun categoria(titulo: String, descripcion: String, rubro: RubroFuente): String {
        val t = normalizar(titulo)
        val todo = normalizar("$titulo $descripcion")
        return when {
            algun(t, MAQUINARIA) -> CATEGORIA_MAQUINARIA
            algun(t, INSUMOS) -> CATEGORIA_INSUMOS
            algun(t, CLIMA) -> CATEGORIA_CLIMA
            algun(t, GANADERIA) -> CATEGORIA_GANADERIA
            rubro == RubroFuente.ECONOMIA || algun(t, ECONOMIA) -> CATEGORIA_ECONOMIA
            algun(todo, MAQUINARIA) -> CATEGORIA_MAQUINARIA
            algun(todo, INSUMOS) -> CATEGORIA_INSUMOS
            algun(todo, CLIMA) -> CATEGORIA_CLIMA
            algun(todo, GANADERIA) -> CATEGORIA_GANADERIA
            algun(todo, GRANOS) -> CATEGORIA_AGRICULTURA
            algun(todo, ECONOMIA) -> CATEGORIA_ECONOMIA
            else -> CATEGORIA_AGRICULTURA
        }
    }

    const val CATEGORIA_MAQUINARIA = "Maquinaria"
    const val CATEGORIA_INSUMOS = "Insumos y mercados"
    const val CATEGORIA_CLIMA = "Clima"
    const val CATEGORIA_GANADERIA = "Ganadería"
    const val CATEGORIA_ECONOMIA = "Economía"
    const val CATEGORIA_AGRICULTURA = "Agricultura"

    private val SIN_TILDE = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o', 'õ' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c'
    )

    /** Minúsculas y sin tildes. Sin java.text.Normalizer, que no existe en la web. */
    fun normalizar(texto: String): String {
        val sb = StringBuilder(texto.length)
        for (c in texto.lowercase()) sb.append(SIN_TILDE[c] ?: c)
        return sb.toString()
    }
}
