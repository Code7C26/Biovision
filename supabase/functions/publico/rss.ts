// Lector de RSS 2.0 para la función "publico": mismas reglas que LectorRss en Android
// (app/.../data/repository/LectorRss.kt). Puro y sin dependencias: se prueba con Node
// (supabase/functions/publico/prueba_rss.mjs).

export interface Fuente {
  nombre: string;
  url: string;
  /** Único host admitido para los enlaces de sus notas (o un subdominio). */
  dominio: string;
  rubro: "AGRO" | "ECONOMIA";
  maxNotas: number;
}

export interface Nota {
  titulo: string;
  resumen: string;
  enlace: string;
  imagen: string | null;
  categorias: string[];
  /** Milisegundos desde 1970; 0 si el feed no trae fecha legible. */
  fecha: number;
  fuente: string;
  rubro: "AGRO" | "ECONOMIA";
}

/** Notas patrocinadas o publirreportajes: no son información periodística. */
const MARCAS_PUBLICIDAD = [
  "patrocinado", "contenido de marca", "espacio de marca", "publinota", "advertorial",
  "sponsored", "brandstudio", "brand-studio", "contenido-patrocinado", "/espacio-publicitario",
];

const ENTIDADES: Record<string, string> = {
  amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " ",
  aacute: "á", eacute: "é", iacute: "í", oacute: "ó", uacute: "ú", ntilde: "ñ", uuml: "ü",
  Aacute: "Á", Eacute: "É", Iacute: "Í", Oacute: "Ó", Uacute: "Ú", Ntilde: "Ñ", Uuml: "Ü",
  iexcl: "¡", iquest: "¿", laquo: "«", raquo: "»", ndash: "–", mdash: "—", hellip: "…",
  lsquo: "‘", rsquo: "’", ldquo: "“", rdquo: "”", deg: "°", ordm: "º", ordf: "ª",
};

export function decodificarEntidades(texto: string): string {
  return texto.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (todo, cuerpo: string) => {
    if (cuerpo[0] === "#") {
      const codigo = cuerpo[1] === "x" || cuerpo[1] === "X" ? parseInt(cuerpo.slice(2), 16) : parseInt(cuerpo.slice(1), 10);
      return Number.isFinite(codigo) && codigo > 0 && codigo < 0x110000 ? String.fromCodePoint(codigo) : todo;
    }
    return ENTIDADES[cuerpo] ?? todo;
  });
}

/** Contenido de un elemento XML: sin CDATA y con las entidades XML resueltas (queda HTML si lo había). */
function contenido(crudo: string): string {
  const cdata = crudo.match(/^\s*<!\[CDATA\[([\s\S]*?)\]\]>\s*$/);
  return cdata ? cdata[1] : decodificarEntidades(crudo);
}

function escaparRegex(texto: string): string {
  return texto.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Texto del primer hijo [tag] del ítem ("" si no está). */
function hijo(item: string, tag: string): string {
  const m = item.match(new RegExp(`<${escaparRegex(tag)}(?:\\s[^>]*)?>([\\s\\S]*?)</${escaparRegex(tag)}>`, "i"));
  return m ? contenido(m[1]).trim() : "";
}

function hijos(item: string, tag: string): string[] {
  const re = new RegExp(`<${escaparRegex(tag)}(?:\\s[^>]*)?>([\\s\\S]*?)</${escaparRegex(tag)}>`, "gi");
  return [...item.matchAll(re)].map((m) => textoPlano(contenido(m[1]))).filter((c) => c.length > 0);
}

/** Atributos de las etiquetas [tag] (vacías o con contenido), en orden. */
function atributos(item: string, tag: string): Record<string, string>[] {
  const re = new RegExp(`<${escaparRegex(tag)}\\s([^>]*?)/?>`, "gi");
  return [...item.matchAll(re)].map((m) => {
    const attrs: Record<string, string> = {};
    for (const a of m[1].matchAll(/([\w:-]+)\s*=\s*("([^"]*)"|'([^']*)')/g)) {
      attrs[a[1].toLowerCase()] = decodificarEntidades(a[3] ?? a[4] ?? "");
    }
    return attrs;
  });
}

/** HTML de la bajada → texto (sin etiquetas ni scripts, entidades resueltas, espacios colapsados). */
export function textoPlano(html: string): string {
  if (!html.trim()) return "";
  const sinCodigo = html.replace(/<(script|style)\b[\s\S]*?<\/\1>/gi, " ");
  return decodificarEntidades(sinCodigo.replace(/<[^>]*>/g, " ")).replace(/\s+/g, " ").trim();
}

/** Como NewsArticle.cleanUrl: http:// y // pasan a https://. */
export function limpiarUrl(url: string): string {
  const u = url.trim();
  if (u.startsWith("http://")) return "https://" + u.slice(7);
  if (u.startsWith("//")) return "https:" + u;
  if (!u.startsWith("https://")) return "https://" + u;
  return u;
}

/** Enlace de la nota: https y del dominio del medio (o un subdominio); cualquier otro host se descarta. */
export function enlaceSeguro(url: string, dominio: string): string | null {
  if (!url.trim()) return null;
  const limpia = limpiarUrl(url);
  try {
    const u = new URL(limpia);
    const host = u.hostname.toLowerCase();
    if (u.protocol !== "https:" || !(host === dominio || host.endsWith("." + dominio))) return null;
    return limpia;
  } catch {
    return null;
  }
}

/** Portada: cualquier CDN, pero siempre https; nunca data:. */
export function imagenSegura(url: string | undefined | null): string | null {
  if (!url || !url.trim() || url.startsWith("data:")) return null;
  const limpia = limpiarUrl(url.replace(/&amp;/g, "&"));
  try {
    const u = new URL(limpia.replace(/ /g, "%20"));
    return u.protocol === "https:" && u.hostname ? u.toString() : null;
  } catch {
    return null;
  }
}

const PARECE_IMAGEN = /\.(jpe?g|png|webp|gif)(\?|$)/i;

function imagen(item: string, descripcionHtml: string, contenidoHtml: string): string | null {
  const enclosure = atributos(item, "enclosure")
    .find((a) => (a.type ?? "").startsWith("image") || PARECE_IMAGEN.test(a.url ?? ""))?.url;
  const media = [...atributos(item, "media:content"), ...atributos(item, "media:thumbnail")]
    .find((a) => (!a.medium || a.medium === "image") && (a.url ?? "").trim() !== "")?.url;
  const enHtml = [contenidoHtml, descripcionHtml]
    .map((html) => html.match(/<img\b[^>]*?\ssrc\s*=\s*("([^"]*)"|'([^']*)')/i))
    .map((m) => (m ? decodificarEntidades(m[2] ?? m[3] ?? "") : null))
    .find((u) => u);
  for (const candidata of [enclosure, media, enHtml]) {
    const segura = imagenSegura(candidata);
    if (segura) return segura;
  }
  return null;
}

export function leerFeed(xml: string, fuente: Fuente): Nota[] {
  const notas: Nota[] = [];
  for (const m of xml.matchAll(/<item\b[^>]*>([\s\S]*?)<\/item>/gi)) {
    const item = m[1];
    const titulo = textoPlano(hijo(item, "title"));
    if (titulo.length < 8) continue;
    const enlace = enlaceSeguro(hijo(item, "link") || hijo(item, "guid"), fuente.dominio);
    if (!enlace) continue;
    const categorias = hijos(item, "category");
    const textoPublicidad = `${titulo} ${enlace} ${categorias.join(" ")}`.toLowerCase();
    if (MARCAS_PUBLICIDAD.some((marca) => textoPublicidad.includes(marca))) continue;

    const descripcionHtml = hijo(item, "description");
    const contenidoHtml = hijo(item, "content:encoded");
    let resumen = textoPlano(descripcionHtml);
    if (resumen.length > 400) resumen = resumen.slice(0, 397).trimEnd() + "…";
    const fecha = Date.parse(hijo(item, "pubDate") || hijo(item, "dc:date"));

    notas.push({
      titulo,
      resumen,
      enlace,
      imagen: imagen(item, descripcionHtml, contenidoHtml),
      categorias,
      fecha: Number.isFinite(fecha) ? fecha : 0,
      fuente: fuente.nombre,
      rubro: fuente.rubro,
    });
  }
  return notas;
}

/** Bytes del feed → texto con el charset de la declaración XML (algunos medios usan ISO-8859-1). */
export function decodificarXml(bytes: Uint8Array, contentType: string | null): string {
  const inicio = new TextDecoder("latin1").decode(bytes.subarray(0, 200));
  const declarado = inicio.match(/encoding\s*=\s*["']([\w-]+)["']/i)?.[1] ??
    contentType?.match(/charset=([\w-]+)/i)?.[1] ?? "utf-8";
  try {
    return new TextDecoder(declarado.toLowerCase()).decode(bytes);
  } catch {
    return new TextDecoder("utf-8").decode(bytes);
  }
}
