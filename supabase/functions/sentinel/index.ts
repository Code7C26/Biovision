// GRANEX: imágenes Sentinel-2 L2A (10 m) de un lote, vía Sentinel Hub de Copernicus Data Space.
//
// La app y la web nunca ven la cuenta de Copernicus: el client ID y el secret de GRANEX son secretos
// de esta función (CDSE_CLIENT_ID, CDSE_CLIENT_SECRET). Solo atiende a usuarios con sesión y cada
// pedido suma al límite diario del usuario (tabla uso_sentinel), porque la cuota es compartida.
//
// POST { accion: "escenas", anillo: [[lon, lat], ...], dias?: number }
//   → { escenas: [{ fecha, nubesPct, ndvi, ndre, gndvi, pixelesValidos }] } (la más reciente primero)
// POST { accion: "imagen", anillo: [[lon, lat], ...], fecha: "yyyy-MM-dd" }
//   → { fecha, oeste, sur, este, norte, ancho, alto, png } (png en base64: 0 = sin dato,
//     1..255 = índice de -1 a 1; R = NDVI, G = NDRE, B = GNDVI)
// POST { accion: "campana", anillo: [[lon, lat], ...], desde: "yyyy-MM-dd", hasta: "yyyy-MM-dd" }
//   → igual que "imagen", pero R = NDVI pico de la campaña (percentil 90 de las pasadas sin nubes),
//     G = cantidad de pasadas sin nubes del píxel (0 a 255) y B = NDRE pico (se satura menos que el
//     NDVI en cultivos densos). Con menos de 3 pasadas, R = B = 0 (sin dato).
// POST { accion: "color", anillo, fecha } → igual que "imagen", pero la foto en color real (RGBA, A = 0
//     sin dato) de la caja del lote con un 20 % de margen, para ver el contexto.
// POST { accion: "relieve", anillo } → altura del terreno (Copernicus DEM GLO-30, remuestreado a 10 m)
//     en la caja del lote: altura = (R·256 + G) / 10 − 500 metros; B = 255 con dato, 0 sin dato. No usa
//     Sentinel Hub (ahí el GLO-30 está restringido): se lee de AWS Open Data, ver dem.ts.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { encodeBase64 } from "jsr:@std/encoding@1/base64";
import { alturasDem, pngRelieve } from "./dem.ts";

const LIMITE_DIARIO = 200;
const MAX_VERTICES = 2000;
const MAX_LADO_PX = 2500; // a 10 m: lotes de hasta 25 × 25 km
const METROS_LAT = 110_950;

const URL_TOKEN = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token";
const URL_PROCESO = "https://sh.dataspace.copernicus.eu/process/v1";
const URL_ESTADISTICAS = "https://sh.dataspace.copernicus.eu/api/v1/statistics";
const CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

// Píxel válido: vegetación (4) o suelo desnudo (5) según la clasificación de escena (SCL); se
// descartan nubes, sombras, cirros, agua, nieve y píxeles saturados
const BANDAS = `["B03", "B04", "B05", "B08", "B8A", "SCL", "dataMask"]`;
const INDICES = `
  var ndvi = (s.B08 - s.B04) / (s.B08 + s.B04);
  var ndre = (s.B8A - s.B05) / (s.B8A + s.B05);
  var gndvi = (s.B08 - s.B03) / (s.B08 + s.B03);
  var ok = s.dataMask === 1 && (s.SCL === 4 || s.SCL === 5) && isFinite(ndvi) && isFinite(ndre) && isFinite(gndvi);`;

const EVALSCRIPT_ESTADISTICAS = `//VERSION=3
function setup() {
  return {
    input: [{ bands: ${BANDAS} }],
    output: [{ id: "indices", bands: 3, sampleType: "FLOAT32" }, { id: "dataMask", bands: 1 }]
  };
}
function evaluatePixel(s) {${INDICES}
  return { indices: [ndvi, ndre, gndvi], dataMask: [ok ? 1 : 0] };
}`;

const EVALSCRIPT_IMAGEN = `//VERSION=3
function setup() {
  return { input: [{ bands: ${BANDAS} }], output: { bands: 3, sampleType: "UINT8" } };
}
function codificar(v) {
  return Math.round((Math.max(-1, Math.min(1, v)) + 1) / 2 * 254) + 1;
}
function evaluatePixel(s) {${INDICES}
  if (!ok) return [0, 0, 0];
  return [codificar(ndvi), codificar(ndre), codificar(gndvi)];
}`;

// Todas las pasadas de la campaña juntas (mosaicking ORBIT): por píxel, el percentil 90 del NDVI y del
// NDRE de las que están sin nubes. Es el pico de vigor de la campaña, más robusto que el máximo ante
// nubes finas que el SCL no detecta.
const EVALSCRIPT_CAMPANA = `//VERSION=3
function setup() {
  return { input: [{ bands: ["B04", "B05", "B08", "B8A", "SCL", "dataMask"] }], output: { bands: 3, sampleType: "UINT8" }, mosaicking: "ORBIT" };
}
function codificar(v) {
  return Math.round((Math.max(-1, Math.min(1, v)) + 1) / 2 * 254) + 1;
}
function p90(v) {
  v.sort(function (a, b) { return a - b; });
  return v[Math.floor(0.9 * (v.length - 1))];
}
function evaluatePixel(muestras) {
  var ndvi = [], ndre = [];
  for (var i = 0; i < muestras.length; i++) {
    var s = muestras[i];
    if (s.dataMask !== 1 || (s.SCL !== 4 && s.SCL !== 5)) continue;
    var a = (s.B08 - s.B04) / (s.B08 + s.B04);
    var b = (s.B8A - s.B05) / (s.B8A + s.B05);
    if (isFinite(a) && isFinite(b)) { ndvi.push(a); ndre.push(b); }
  }
  var n = Math.min(ndvi.length, 255);
  if (ndvi.length < 3) return [0, n, 0];
  return [codificar(p90(ndvi)), n, codificar(p90(ndre))];
}`;

// Color real: bandas rojo, verde y azul con ganancia y una curva suave para que se vea como una foto
const EVALSCRIPT_COLOR = `//VERSION=3
function setup() {
  return { input: [{ bands: ["B02", "B03", "B04", "dataMask"] }], output: { bands: 4, sampleType: "UINT8" } };
}
function c(v) {
  return Math.max(0, Math.min(255, Math.round(Math.pow(Math.max(0, v * 3.0), 1 / 1.4) * 255)));
}
function evaluatePixel(s) {
  return [c(s.B04), c(s.B03), c(s.B02), s.dataMask * 255];
}`;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-region, x-supabase-client-platform, x-supabase-client-platform-version",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

class ErrorUsuario extends Error {
  constructor(readonly estado: number, mensaje: string) {
    super(mensaje);
  }
}

function json(cuerpo: unknown, estado = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

// ---------- Lote ----------

type Punto = [number, number]; // [lon, lat]

interface Lote {
  anillo: Punto[]; // cerrado
  oeste: number;
  sur: number;
  este: number;
  norte: number;
  metrosLon: number;
  areaM2: number;
}

function leerLote(valor: unknown): Lote {
  if (!Array.isArray(valor) || valor.length < 3 || valor.length > MAX_VERTICES) {
    throw new ErrorUsuario(400, "El contorno del lote no es válido.");
  }
  const puntos: Punto[] = valor.map((p) => {
    if (!Array.isArray(p) || p.length < 2) throw new ErrorUsuario(400, "El contorno del lote no es válido.");
    const lon = Number(p[0]);
    const lat = Number(p[1]);
    if (!Number.isFinite(lon) || !Number.isFinite(lat) || Math.abs(lon) > 180 || Math.abs(lat) > 90) {
      throw new ErrorUsuario(400, "El contorno del lote tiene coordenadas inválidas.");
    }
    return [lon, lat];
  });
  const [l0, la0] = puntos[0];
  const [ln, lan] = puntos[puntos.length - 1];
  const anillo = l0 === ln && la0 === lan ? puntos : [...puntos, puntos[0]];
  if (anillo.length < 4) throw new ErrorUsuario(400, "El contorno del lote no es válido.");

  const lons = anillo.map((p) => p[0]);
  const lats = anillo.map((p) => p[1]);
  const oeste = Math.min(...lons), este = Math.max(...lons);
  const sur = Math.min(...lats), norte = Math.max(...lats);
  const metrosLon = 111_412.84 * Math.cos(((norte + sur) / 2) * Math.PI / 180);
  if ((este - oeste) * metrosLon / 10 > MAX_LADO_PX || (norte - sur) * METROS_LAT / 10 > MAX_LADO_PX) {
    throw new ErrorUsuario(400, "El lote es demasiado grande para un escaneo (máximo 25 km por lado).");
  }
  // Área por la fórmula del polígono, en una proyección local (alcanza para estimar nubes)
  let doble = 0;
  for (let i = 0; i < anillo.length - 1; i++) {
    const [x1, y1] = anillo[i];
    const [x2, y2] = anillo[i + 1];
    doble += (x1 - oeste) * metrosLon * (y2 - sur) * METROS_LAT - (x2 - oeste) * metrosLon * (y1 - sur) * METROS_LAT;
  }
  return { anillo, oeste, sur, este, norte, metrosLon, areaM2: Math.abs(doble) / 2 };
}

function limites(lote: Lote) {
  return {
    geometry: { type: "Polygon", coordinates: [lote.anillo] },
    properties: { crs: CRS84 },
  };
}

// ---------- Sentinel Hub ----------

let token: string | null = null;
let tokenVence = 0;

async function obtenerToken(renovar = false): Promise<string> {
  if (!renovar && token && Date.now() < tokenVence) return token;
  const id = Deno.env.get("CDSE_CLIENT_ID");
  const secreto = Deno.env.get("CDSE_CLIENT_SECRET");
  if (!id || !secreto) {
    throw new ErrorUsuario(503, "El escaneo satelital todavía no está configurado en el servidor de GRANEX.");
  }
  const r = await fetch(URL_TOKEN, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "client_credentials", client_id: id, client_secret: secreto }),
  });
  if (!r.ok) {
    console.error("Token de Copernicus rechazado", r.status);
    throw new ErrorUsuario(503, "El servicio de imágenes no está disponible en este momento.");
  }
  const datos = await r.json();
  token = datos.access_token as string;
  tokenVence = Date.now() + Math.max(30, (datos.expires_in ?? 300) - 60) * 1000;
  return token;
}

async function sentinelHub(url: string, cuerpo: unknown, acepta: string): Promise<Response> {
  for (let intento = 0; intento < 2; intento++) {
    const r = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${await obtenerToken(intento > 0)}`,
        "Content-Type": "application/json",
        Accept: acepta,
      },
      body: JSON.stringify(cuerpo),
    });
    if (r.ok) return r;
    if (r.status === 401 && intento === 0) continue; // token vencido: se pide otro
    const detalle = (await r.text()).slice(0, 300);
    console.error("Sentinel Hub", r.status, detalle);
    if (r.status === 429) throw new ErrorUsuario(429, "El servicio de imágenes está saturado. Probá de nuevo en un minuto.");
    if (r.status === 403 && detalle.includes("INSUFFICIENT_PERMISSIONS")) {
      throw new ErrorUsuario(503, "La cuenta de imágenes de GRANEX no tiene acceso a ese dato.");
    }
    if (r.status === 403) throw new ErrorUsuario(503, "Se agotó la cuota mensual de imágenes de GRANEX.");
    throw new ErrorUsuario(502, "El servicio de imágenes respondió con un error. Probá de nuevo más tarde.");
  }
  throw new ErrorUsuario(503, "El servicio de imágenes no está disponible en este momento.");
}

function fechaIso(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

async function escenas(lote: Lote, dias: number) {
  const ahora = Date.now();
  const r = await sentinelHub(URL_ESTADISTICAS, {
    input: {
      bounds: limites(lote),
      data: [{ type: "sentinel-2-l2a", dataFilter: { maxCloudCoverage: 90 } }],
    },
    aggregation: {
      timeRange: { from: `${fechaIso(ahora - dias * 86_400_000)}T00:00:00Z`, to: `${fechaIso(ahora + 86_400_000)}T00:00:00Z` },
      aggregationInterval: { of: "P1D" },
      evalscript: EVALSCRIPT_ESTADISTICAS,
      resx: 20 / lote.metrosLon,
      resy: 20 / METROS_LAT,
    },
    calculations: { default: {} },
  }, "application/json");
  const datos = (await r.json()).data ?? [];
  // Píxeles de 20 m que entran en el lote: el % sin dato es nubes, sombras o agua sobre el lote
  const esperados = Math.max(1, lote.areaM2 / 400);
  const lista = [];
  for (const d of datos) {
    const bandas = d?.outputs?.indices?.bands;
    const s0 = bandas?.B0?.stats;
    if (!s0) continue;
    const validos = (s0.sampleCount ?? 0) - (s0.noDataCount ?? 0);
    if (validos <= 0) continue;
    const media = (b: string) => {
      const v = bandas?.[b]?.stats?.mean;
      return typeof v === "number" && Number.isFinite(v) ? v : null;
    };
    lista.push({
      fecha: String(d.interval.from).slice(0, 10),
      nubesPct: Math.min(100, Math.max(0, 100 * (1 - validos / esperados))),
      ndvi: media("B0"),
      ndre: media("B1"),
      gndvi: media("B2"),
      pixelesValidos: validos,
    });
  }
  lista.sort((a, b) => (a.fecha < b.fecha ? 1 : -1));
  return { escenas: lista };
}

async function imagen(lote: Lote, fecha: string) {
  const ancho = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.este - lote.oeste) * lote.metrosLon / 10)));
  const alto = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.norte - lote.sur) * METROS_LAT / 10)));
  const r = await sentinelHub(URL_PROCESO, {
    input: {
      bounds: limites(lote),
      data: [{
        type: "sentinel-2-l2a",
        dataFilter: { timeRange: { from: `${fecha}T00:00:00Z`, to: `${fecha}T23:59:59Z` }, mosaickingOrder: "leastCC" },
      }],
    },
    output: { width: ancho, height: alto, responses: [{ identifier: "default", format: { type: "image/png" } }] },
    evalscript: EVALSCRIPT_IMAGEN,
  }, "image/png");
  const png = new Uint8Array(await r.arrayBuffer());
  return { fecha, oeste: lote.oeste, sur: lote.sur, este: lote.este, norte: lote.norte, ancho, alto, png: encodeBase64(png) };
}

async function campana(lote: Lote, desde: string, hasta: string) {
  const ancho = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.este - lote.oeste) * lote.metrosLon / 10)));
  const alto = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.norte - lote.sur) * METROS_LAT / 10)));
  const r = await sentinelHub(URL_PROCESO, {
    input: {
      bounds: limites(lote),
      data: [{
        type: "sentinel-2-l2a",
        // Escenas casi totalmente nubladas no aportan y cuestan cuota
        dataFilter: { timeRange: { from: `${desde}T00:00:00Z`, to: `${hasta}T23:59:59Z` }, maxCloudCoverage: 80 },
      }],
    },
    output: { width: ancho, height: alto, responses: [{ identifier: "default", format: { type: "image/png" } }] },
    evalscript: EVALSCRIPT_CAMPANA,
  }, "image/png");
  const png = new Uint8Array(await r.arrayBuffer());
  return { fecha: `${desde}/${hasta}`, oeste: lote.oeste, sur: lote.sur, este: lote.este, norte: lote.norte, ancho, alto, png: encodeBase64(png) };
}

/** Caja del lote agrandada un [margen] por lado (para ver el contexto alrededor del lote). */
function cajaConMargen(lote: Lote, margen: number) {
  const dLon = (lote.este - lote.oeste) * margen;
  const dLat = (lote.norte - lote.sur) * margen;
  return { oeste: lote.oeste - dLon, sur: lote.sur - dLat, este: lote.este + dLon, norte: lote.norte + dLat };
}

async function color(lote: Lote, fecha: string) {
  const c = cajaConMargen(lote, 0.2);
  const ancho = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((c.este - c.oeste) * lote.metrosLon / 10)));
  const alto = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((c.norte - c.sur) * METROS_LAT / 10)));
  const r = await sentinelHub(URL_PROCESO, {
    input: {
      bounds: { bbox: [c.oeste, c.sur, c.este, c.norte], properties: { crs: CRS84 } },
      data: [{
        type: "sentinel-2-l2a",
        dataFilter: { timeRange: { from: `${fecha}T00:00:00Z`, to: `${fecha}T23:59:59Z` }, mosaickingOrder: "leastCC" },
      }],
    },
    output: { width: ancho, height: alto, responses: [{ identifier: "default", format: { type: "image/png" } }] },
    evalscript: EVALSCRIPT_COLOR,
  }, "image/png");
  const png = new Uint8Array(await r.arrayBuffer());
  return { fecha, oeste: c.oeste, sur: c.sur, este: c.este, norte: c.norte, ancho, alto, png: encodeBase64(png) };
}

async function relieve(lote: Lote) {
  const ancho = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.este - lote.oeste) * lote.metrosLon / 10)));
  const alto = Math.min(MAX_LADO_PX, Math.max(1, Math.ceil((lote.norte - lote.sur) * METROS_LAT / 10)));
  let png: Uint8Array;
  try {
    png = await pngRelieve(await alturasDem(lote.oeste, lote.sur, lote.este, lote.norte, ancho, alto), ancho, alto);
  } catch (e) {
    console.error("Copernicus DEM", e);
    throw new ErrorUsuario(502, "El relieve no está disponible en este momento. Probá de nuevo más tarde.");
  }
  return { fecha: "dem", oeste: lote.oeste, sur: lote.sur, este: lote.este, norte: lote.norte, ancho, alto, png: encodeBase64(png) };
}

function fechaValida(f: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(f) && !Number.isNaN(Date.parse(`${f}T00:00:00Z`));
}

// ---------- Servidor ----------

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);
  try {
    const autorizacion = req.headers.get("Authorization") ?? "";
    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: autorizacion } },
      auth: { persistSession: false },
    });
    // La clave pública también es un JWT válido: hace falta un usuario real
    const { data: { user } } = await supabase.auth.getUser(autorizacion.replace(/^Bearer\s+/i, ""));
    if (!user) throw new ErrorUsuario(401, "Iniciá sesión en Ajustes para usar el escaneo satelital.");

    let cuerpo: Record<string, unknown>;
    try {
      cuerpo = await req.json();
    } catch {
      throw new ErrorUsuario(400, "Pedido inválido.");
    }
    const lote = leerLote(cuerpo.anillo);
    const accion = cuerpo.accion;
    if (!["escenas", "imagen", "campana", "color", "relieve"].includes(String(accion))) throw new ErrorUsuario(400, "Acción desconocida.");
    const desde = String(cuerpo.desde ?? "");
    const hasta = String(cuerpo.hasta ?? "");
    if (accion === "campana") {
      if (!fechaValida(desde) || !fechaValida(hasta) || desde > hasta) throw new ErrorUsuario(400, "Campaña inválida.");
      const duracion = (Date.parse(hasta) - Date.parse(desde)) / 86_400_000;
      if (duracion > 250) throw new ErrorUsuario(400, "Una campaña puede abarcar como mucho 250 días.");
    }
    const fecha = String(cuerpo.fecha ?? "");
    if ((accion === "imagen" || accion === "color") && !fechaValida(fecha)) throw new ErrorUsuario(400, "Fecha inválida.");
    const dias = Math.round(Number(cuerpo.dias ?? 120));
    if (accion === "escenas" && !(dias >= 1 && dias <= 400)) throw new ErrorUsuario(400, "Rango de días inválido.");

    const { data: pedidos, error } = await supabase.rpc("registrar_pedido_sentinel");
    if (error) {
      console.error("registrar_pedido_sentinel", error.message);
      throw new ErrorUsuario(500, "No se pudo registrar el pedido. Probá de nuevo.");
    }
    if ((pedidos as number) > LIMITE_DIARIO) {
      throw new ErrorUsuario(429, `Llegaste al límite de ${LIMITE_DIARIO} consultas satelitales por día. Mañana se renueva.`);
    }

    if (accion === "escenas") return json(await escenas(lote, dias));
    if (accion === "campana") return json(await campana(lote, desde, hasta));
    if (accion === "color") return json(await color(lote, fecha));
    if (accion === "relieve") return json(await relieve(lote));
    return json(await imagen(lote, fecha));
  } catch (e) {
    if (e instanceof ErrorUsuario) return json({ error: e.message }, e.estado);
    console.error("Error inesperado", e);
    return json({ error: "Error inesperado en el servidor de imágenes." }, 500);
  }
});
