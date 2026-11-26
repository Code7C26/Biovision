// GRANEX: datos públicos para la web, que el navegador no puede pedir directo porque esas fuentes no
// responden con CORS. No necesita una sesión: alcanza la clave pública del proyecto (la pide la puerta
// de entrada de Supabase). No guarda nada ni usa secretos.
//
//   GET /publico/noticias                       → { generado, notas: Nota[], fallidas: string[] }
//   GET /publico/a3/api/v2/closing-prices?…     → la respuesta del CEM de A3 Mercados, tal cual
//
// No es un proxy abierto: los feeds son una lista fija (FUENTES, la misma de FuentesNoticias.kt en
// Android) y a A3 solo se le reenvían los parámetros validados de un pedido de ajustes de futuros.
// Caché en memoria (15 min noticias, 30 min futuros) y un límite por IP. Errores: { error: "…" }.

import { decodificarXml, type Fuente, leerFeed, type Nota } from "./rss.ts";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-region, x-supabase-client-platform, x-supabase-client-platform-version",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

/** Mantener igual a FUENTES_NOTICIAS (app/src/main/java/com/example/data/repository/FuentesNoticias.kt). */
export const FUENTES: Fuente[] = [
  { nombre: "Clarín Rural", url: "https://www.clarin.com/rss/rural/", dominio: "clarin.com", rubro: "AGRO", maxNotas: 15 },
  { nombre: "La Nación Campo", url: "https://www.lanacion.com.ar/arc/outboundfeeds/rss/category/economia/campo/?outputType=xml", dominio: "lanacion.com.ar", rubro: "AGRO", maxNotas: 15 },
  { nombre: "La Voz Agro", url: "https://www.lavoz.com.ar/rss/agro/", dominio: "lavoz.com.ar", rubro: "AGRO", maxNotas: 15 },
  { nombre: "Agrofy News", url: "https://news.agrofy.com.ar/rss.xml", dominio: "agrofy.com.ar", rubro: "AGRO", maxNotas: 15 },
  { nombre: "Agrositio", url: "https://www.agrositio.com.ar/rss/rss.php?area=destacadas", dominio: "agrositio.com.ar", rubro: "AGRO", maxNotas: 15 },
  { nombre: "Bichos de Campo", url: "https://bichosdecampo.com/feed/", dominio: "bichosdecampo.com", rubro: "AGRO", maxNotas: 15 },
  { nombre: "Infocampo", url: "https://www.infocampo.com.ar/feed/", dominio: "infocampo.com.ar", rubro: "AGRO", maxNotas: 15 },
  { nombre: "El Cronista", url: "https://www.cronista.com/arc/outboundfeeds/rss/category/economia-politica/?outputType=xml", dominio: "cronista.com", rubro: "ECONOMIA", maxNotas: 12 },
];

const CEM = "https://apicem.matbarofex.com.ar/api/v2/closing-prices";
const PRODUCTOS_A3 = new Set(["SOJ Dolar MATba", "MAI Dolar MATba", "TRI Dolar MATba"]);
const FECHA = /^\d{4}-\d{2}-\d{2}$/;

const TTL_NOTICIAS_MS = 15 * 60 * 1000;
const TTL_FUTUROS_MS = 30 * 60 * 1000;
const TIEMPO_FEED_MS = 10_000;
const AGENTE = "GRANEX/1.0 (supportgranex@gmail.com)";

// --- Caché en memoria (vive mientras la instancia de la función esté caliente) ---

interface Guardado {
  vence: number;
  cuerpo: string;
}
const cache = new Map<string, Guardado>();
const enCurso = new Map<string, Promise<string>>();

/** Respuesta en caché o la produce una sola vez aunque lleguen varios pedidos juntos. */
async function conCache(clave: string, ttl: number, producir: () => Promise<string>): Promise<string> {
  const g = cache.get(clave);
  if (g && g.vence > Date.now()) return g.cuerpo;
  const pendiente = enCurso.get(clave);
  if (pendiente) return pendiente;
  const p = producir()
    .then((cuerpo) => {
      cache.set(clave, { vence: Date.now() + ttl, cuerpo });
      if (cache.size > 200) cache.delete(cache.keys().next().value!);
      return cuerpo;
    })
    .finally(() => enCurso.delete(clave));
  enCurso.set(clave, p);
  return p;
}

// --- Límite por IP: 60 pedidos por minuto (por instancia; alcanza para cortar abusos simples) ---

const PEDIDOS_POR_MINUTO = 60;
const pedidosPorIp = new Map<string, number[]>();

function excedido(ip: string): boolean {
  const ahora = Date.now();
  const recientes = (pedidosPorIp.get(ip) ?? []).filter((t) => ahora - t < 60_000);
  recientes.push(ahora);
  pedidosPorIp.set(ip, recientes);
  if (pedidosPorIp.size > 5000) pedidosPorIp.clear();
  return recientes.length > PEDIDOS_POR_MINUTO;
}

function json(cuerpo: unknown, estado = 200, extra: Record<string, string> = {}): Response {
  return new Response(typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo), {
    status: estado,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8", ...extra },
  });
}

async function leerFuente(f: Fuente): Promise<Nota[]> {
  const r = await fetch(f.url, {
    headers: { "User-Agent": AGENTE, "Accept": "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5" },
    redirect: "follow",
    signal: AbortSignal.timeout(TIEMPO_FEED_MS),
  });
  if (!r.ok) throw new Error(`${f.nombre} respondió ${r.status}`);
  const bytes = new Uint8Array(await r.arrayBuffer());
  return leerFeed(decodificarXml(bytes, r.headers.get("content-type")), f).slice(0, f.maxNotas);
}

/** Todas las fuentes en paralelo; una que falla no tapa a las demás. Sin ninguna nota, error (nunca notas inventadas). */
export async function noticias(fuentes: Fuente[] = FUENTES): Promise<string> {
  const resultados = await Promise.allSettled(fuentes.map(leerFuente));
  const notas: Nota[] = [];
  const fallidas: string[] = [];
  const vistos = new Set<string>();
  // Algún medio publica la hora con el huso equivocado: una nota "del futuro" quedaría siempre primera
  const ahora = Date.now();
  resultados.forEach((r, i) => {
    if (r.status === "rejected") {
      fallidas.push(fuentes[i].nombre);
      console.warn("feed", fuentes[i].nombre, String(r.reason));
      return;
    }
    for (const n of r.value) {
      if (vistos.has(n.enlace)) continue;
      vistos.add(n.enlace);
      notas.push({ ...n, fecha: Math.min(n.fecha, ahora) });
    }
  });
  if (notas.length === 0) throw new Error("Ningún medio respondió");
  notas.sort((a, b) => b.fecha - a.fecha);
  return JSON.stringify({ generado: new Date().toISOString(), notas, fallidas });
}

/** Solo los parámetros de un pedido de ajustes de futuros de granos, validados uno por uno. */
export function pedidoA3(params: URLSearchParams): URL | string {
  const producto = params.get("product") ?? "";
  if (!PRODUCTOS_A3.has(producto)) return "Producto no admitido.";
  if ((params.get("type") ?? "FUT") !== "FUT") return "Solo futuros.";
  if ((params.get("market") ?? "ROFX") !== "ROFX") return "Mercado no admitido.";
  const desde = params.get("from") ?? "";
  const hasta = params.get("to") ?? "";
  if (!FECHA.test(desde) || !FECHA.test(hasta)) return "Fechas inválidas (yyyy-MM-dd).";
  const dias = (Date.parse(hasta) - Date.parse(desde)) / 86_400_000;
  if (!(dias >= 0 && dias <= 400)) return "Rango de fechas inválido.";
  const tamano = Number(params.get("pageSize") ?? "300");
  const pagina = Number(params.get("page") ?? "1");
  if (!Number.isInteger(tamano) || tamano < 1 || tamano > 300) return "pageSize inválido.";
  if (!Number.isInteger(pagina) || pagina < 1 || pagina > 10) return "page inválido.";
  const orden = params.get("sortDir") ?? "DESC";
  if (orden !== "ASC" && orden !== "DESC") return "sortDir inválido.";

  const url = new URL(CEM);
  url.searchParams.set("product", producto);
  url.searchParams.set("type", "FUT");
  url.searchParams.set("from", desde);
  url.searchParams.set("to", hasta);
  url.searchParams.set("page", String(pagina));
  url.searchParams.set("pageSize", String(tamano));
  url.searchParams.set("sortDir", orden);
  url.searchParams.set("market", "ROFX");
  return url;
}

async function futuros(url: URL): Promise<string> {
  const r = await fetch(url, { headers: { "User-Agent": AGENTE, "Accept": "application/json" }, signal: AbortSignal.timeout(20_000) });
  if (!r.ok) throw new Error(`A3 respondió ${r.status}`);
  const datos = await r.json();
  if (!Array.isArray(datos?.data)) throw new Error("Respuesta inesperada de A3");
  return JSON.stringify(datos);
}

export async function atender(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "GET") return json({ error: "Método no permitido." }, 405);
  const ip = (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "desconocida";
  if (excedido(ip)) return json({ error: "Demasiados pedidos. Probá de nuevo en un minuto." }, 429, { "Retry-After": "60" });

  const url = new URL(req.url);
  const ruta = url.pathname.replace(/^.*\/publico/, "");
  try {
    if (ruta === "/noticias") {
      const cuerpo = await conCache("noticias", TTL_NOTICIAS_MS, () => noticias());
      return json(cuerpo, 200, { "Cache-Control": "public, max-age=300" });
    }
    if (ruta === "/a3/api/v2/closing-prices") {
      const pedido = pedidoA3(url.searchParams);
      if (typeof pedido === "string") return json({ error: pedido }, 400);
      const cuerpo = await conCache("a3 " + pedido.search, TTL_FUTUROS_MS, () => futuros(pedido));
      return json(cuerpo, 200, { "Cache-Control": "public, max-age=600" });
    }
    return json({ error: "Ruta desconocida." }, 404);
  } catch (e) {
    console.error("publico", ruta, e);
    const mensaje = ruta === "/noticias"
      ? "No se pudieron leer los medios en este momento. Probá de nuevo en unos minutos."
      : "No se pudieron leer los futuros de A3 Mercados en este momento.";
    return json({ error: mensaje }, 502);
  }
}

// En Supabase corre Deno; la prueba local con Node importa atender() sin levantar el servidor
if (typeof Deno !== "undefined") Deno.serve(atender);
