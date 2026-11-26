// GRANEX: lectura de estaciones meteorológicas de cada marca, llevada a un formato común en unidades
// métricas (°C, %, km/h, hPa, mm, W/m²).
//
// - Davis WeatherLink v2 (api.weatherlink.com/v2): api-key en la URL + X-Api-Secret. El historial
//   (/historic) solo está en las suscripciones Pro: sin eso se muestran las condiciones actuales.
// - Ecowitt API v3 (api.ecowitt.net): application_key + api_key + MAC, con las unidades pedidas en
//   la URL.
// - Pessl Metos FieldClimate v2 (api.fieldclimate.com/v2): firma HMAC-SHA256 de
//   método + ruta + fecha + clave pública con la clave privada.
//
// Los nombres de los campos cambian entre modelos y versiones de firmware: cada lectura prueba varias
// claves y se queda con la primera que tenga un número.

export interface Lectura {
  momento: string; // ISO 8601
  temperaturaC?: number | null;
  humedadPct?: number | null;
  puntoRocioC?: number | null;
  vientoKmh?: number | null;
  rafagaKmh?: number | null;
  direccionGrados?: number | null;
  presionHpa?: number | null;
  lluviaHoyMm?: number | null;
  lluviaTasaMmH?: number | null;
  lluviaSemanaMm?: number | null;
  lluviaMesMm?: number | null;
  lluviaAnioMm?: number | null;
  radiacionWm2?: number | null;
  uv?: number | null;
  sueloTemperaturaC?: number | null;
  sueloHumedadPct?: number | null;
}

/** Un punto de las últimas 24 h. lluviaMm es la lluvia caída desde el punto anterior. */
export interface Punto {
  momento: string;
  temperaturaC?: number | null;
  humedadPct?: number | null;
  vientoKmh?: number | null;
  lluviaMm?: number | null;
}

export interface Dia {
  dia: string; // yyyy-MM-dd
  lluviaMm?: number | null;
  tempMinC?: number | null;
  tempMaxC?: number | null;
}

export interface Datos {
  actual: Lectura | null;
  serie: Punto[];
  diario: Dia[];
  avisos: string[];
}

export interface EstacionDeCuenta {
  identificador: string;
  nombre: string;
  latitud?: number | null;
  longitud?: number | null;
}

/** Error con un mensaje en castellano para mostrar al usuario. */
export class ErrorMarca extends Error {}

export type Credenciales = Record<string, string>;

const TIEMPO_MAXIMO_MS = 15_000;
const ARGENTINA_MS = -3 * 3600_000;

// ---------- Conversiones ----------

export const fAc = (f: number) => (f - 32) * 5 / 9;
export const mphAkmh = (v: number) => v * 1.609344;
export const pulgAmm = (v: number) => v * 25.4;
export const inHgAhPa = (v: number) => v * 33.8639;
export const msAkmh = (v: number) => v * 3.6;

function redondear(v: number | null | undefined, decimales = 1): number | null {
  if (v == null || !Number.isFinite(v)) return null;
  const f = 10 ** decimales;
  return Math.round(v * f) / f;
}

/** Número de un valor que puede venir como texto ("23.4"), o null. */
export function numero(v: unknown): number | null {
  if (typeof v === "number") return Number.isFinite(v) ? v : null;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v.trim());
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

/** El primer campo de [claves] que tenga un número. */
export function primero(obj: Record<string, unknown> | undefined, ...claves: string[]): number | null {
  if (!obj) return null;
  for (const c of claves) {
    const n = numero(obj[c]);
    if (n != null) return n;
  }
  return null;
}

function aplicar(v: number | null, f: (x: number) => number): number | null {
  return v == null ? null : f(v);
}

/** Día de Argentina (UTC−3) de un instante. */
export function diaArgentina(ms: number): string {
  return new Date(ms + ARGENTINA_MS).toISOString().slice(0, 10);
}

/**
 * Lluvia de cada intervalo a partir del contador diario de la estación, que se reinicia a la
 * medianoche: la diferencia con el anterior, o el valor nuevo cuando el contador volvió a empezar.
 */
export function lluviaPorIntervalo(acumulados: (number | null)[]): (number | null)[] {
  let anterior: number | null = null;
  return acumulados.map((a) => {
    if (a == null) return null;
    const lluvia: number | null = anterior == null ? null : a >= anterior ? a - anterior : a;
    anterior = a;
    return lluvia == null ? null : redondear(lluvia, 1);
  });
}

/** Lectura con todos los valores redondeados. */
export function limpiarLectura(l: Lectura): Lectura {
  const r: Lectura = { momento: l.momento };
  for (const [clave, valor] of Object.entries(l)) {
    if (clave === "momento") continue;
    const decimales = clave === "presionHpa" || clave === "direccionGrados" || clave === "radiacionWm2" ? 0 : 1;
    (r as unknown as Record<string, number | null>)[clave] = redondear(valor as number | null, decimales);
  }
  return r;
}

async function pedirJson(url: string, init: RequestInit = {}): Promise<{ estado: number; cuerpo: unknown }> {
  let respuesta: Response;
  try {
    respuesta = await fetch(url, { ...init, signal: AbortSignal.timeout(TIEMPO_MAXIMO_MS) });
  } catch {
    throw new ErrorMarca("No se pudo conectar con el servidor de la estación. Probá de nuevo en unos minutos.");
  }
  const texto = await respuesta.text();
  let cuerpo: unknown = null;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    cuerpo = texto;
  }
  return { estado: respuesta.status, cuerpo };
}

function requerir(c: Credenciales, ...claves: string[]): void {
  for (const k of claves) {
    if (!c[k] || typeof c[k] !== "string" || c[k].length > 200) throw new ErrorMarca("Faltan datos de la cuenta de la estación.");
  }
}

// ---------- Ecowitt ----------

const ECOWITT = "https://api.ecowitt.net/api/v3";
// °C, hPa, km/h, mm, W/m²
const ECOWITT_UNIDADES = "temp_unitid=1&pressure_unitid=3&wind_speed_unitid=7&rainfall_unitid=12&solar_irradiance_unitid=16";

function erroresEcowitt(codigo: number, mensaje: string): ErrorMarca {
  const conocidos: Record<number, string> = {
    40010: "La Application Key de Ecowitt no es válida.",
    40011: "La API Key de Ecowitt no es válida.",
    40012: "La MAC de la estación no es válida o no está en esa cuenta de Ecowitt.",
    40013: "La MAC de la estación no es válida o no está en esa cuenta de Ecowitt.",
  };
  return new ErrorMarca(conocidos[codigo] ?? `Ecowitt respondió: ${mensaje || "error " + codigo}.`);
}

async function ecowitt(ruta: string, c: Credenciales, extra: string): Promise<Record<string, unknown>> {
  requerir(c, "applicationKey", "apiKey");
  const url = `${ECOWITT}/${ruta}?application_key=${encodeURIComponent(c.applicationKey)}&api_key=${encodeURIComponent(c.apiKey)}&${extra}`;
  const { cuerpo } = await pedirJson(url);
  const r = cuerpo as { code?: number; msg?: string; data?: unknown };
  if (!r || typeof r !== "object") throw new ErrorMarca("Ecowitt respondió algo inesperado.");
  if (r.code !== 0) throw erroresEcowitt(Number(r.code), String(r.msg ?? ""));
  return (r.data ?? {}) as Record<string, unknown>;
}

/** Valor de un nodo de Ecowitt: { value: "23.4", unit, time }. */
function ev(nodo: unknown, ...camino: string[]): number | null {
  let actual: unknown = nodo;
  for (const p of camino) {
    if (!actual || typeof actual !== "object") return null;
    actual = (actual as Record<string, unknown>)[p];
  }
  if (actual && typeof actual === "object") return numero((actual as Record<string, unknown>).value);
  return numero(actual);
}

/** Serie de historial de Ecowitt: { list: { "<segundos>": "valor" } } → mapa ms → número. */
function evLista(nodo: unknown, ...camino: string[]): Map<number, number> {
  let actual: unknown = nodo;
  for (const p of camino) {
    if (!actual || typeof actual !== "object") return new Map();
    actual = (actual as Record<string, unknown>)[p];
  }
  const lista = (actual as { list?: Record<string, unknown> } | undefined)?.list;
  const r = new Map<number, number>();
  if (!lista || typeof lista !== "object") return r;
  for (const [t, v] of Object.entries(lista)) {
    const n = numero(v);
    const s = Number(t);
    if (n != null && Number.isFinite(s)) r.set(s * 1000, n);
  }
  return r;
}

function fechaEcowitt(ms: number): string {
  // Hora local de la cuenta; Ecowitt la interpreta en la zona de la estación (Argentina)
  return new Date(ms + ARGENTINA_MS).toISOString().slice(0, 19).replace("T", " ");
}

async function ecowittEstaciones(c: Credenciales): Promise<EstacionDeCuenta[]> {
  const data = await ecowitt("device/list", c, "limit=50");
  const lista = (data.list ?? []) as Record<string, unknown>[];
  return lista.map((d) => ({
    identificador: String(d.mac ?? ""),
    nombre: String(d.name ?? d.mac ?? "Estación Ecowitt"),
    latitud: numero(d.latitude),
    longitud: numero(d.longitude),
  })).filter((d) => d.identificador !== "");
}

async function ecowittDatos(c: Credenciales, mac: string): Promise<Datos> {
  const macQ = `mac=${encodeURIComponent(mac)}&${ECOWITT_UNIDADES}`;
  const ahora = Date.now();
  const [real, historia, diaria] = await Promise.all([
    ecowitt("device/real_time", c, `${macQ}&call_back=all`),
    ecowitt(
      "device/history",
      c,
      `${macQ}&start_date=${encodeURIComponent(fechaEcowitt(ahora - 24 * 3600_000))}&end_date=${encodeURIComponent(fechaEcowitt(ahora))}` +
        `&cycle_type=30min&call_back=outdoor.temperature,outdoor.humidity,wind.wind_speed,rainfall.daily,rainfall_piezo.daily`,
    ).catch(() => null),
    ecowitt(
      "device/history",
      c,
      `${macQ}&start_date=${encodeURIComponent(fechaEcowitt(ahora - 30 * 86400_000))}&end_date=${encodeURIComponent(fechaEcowitt(ahora))}` +
        `&cycle_type=1day&call_back=rainfall.daily,rainfall_piezo.daily`,
    ).catch(() => null),
  ]);

  // Los pluviómetros piezoeléctricos (WS90 y similares) informan en rainfall_piezo
  const lluvia = real.rainfall && ev(real, "rainfall", "daily") != null ? "rainfall" : "rainfall_piezo";
  const suelo = Object.keys(real).filter((k) => k.startsWith("soil_ch")).sort()[0];
  const tempSuelo = Object.keys(real).filter((k) => k.startsWith("temp_ch")).sort()[0];
  const actual: Lectura = {
    momento: new Date(ahora).toISOString(),
    temperaturaC: ev(real, "outdoor", "temperature"),
    humedadPct: ev(real, "outdoor", "humidity"),
    puntoRocioC: ev(real, "outdoor", "dew_point"),
    vientoKmh: ev(real, "wind", "wind_speed"),
    rafagaKmh: ev(real, "wind", "wind_gust"),
    direccionGrados: ev(real, "wind", "wind_direction"),
    presionHpa: ev(real, "pressure", "relative") ?? ev(real, "pressure", "absolute"),
    lluviaHoyMm: ev(real, lluvia, "daily"),
    lluviaTasaMmH: ev(real, lluvia, "rain_rate"),
    lluviaSemanaMm: ev(real, lluvia, "weekly"),
    lluviaMesMm: ev(real, lluvia, "monthly"),
    lluviaAnioMm: ev(real, lluvia, "yearly"),
    radiacionWm2: ev(real, "solar_and_uvi", "solar"),
    uv: ev(real, "solar_and_uvi", "uvi"),
    sueloHumedadPct: suelo ? ev(real, suelo, "soilmoisture") : null,
    sueloTemperaturaC: tempSuelo ? ev(real, tempSuelo, "temperature") : null,
  };
  // La hora real de la lectura viene en cada nodo (segundos)
  const t = numero((real.outdoor as Record<string, Record<string, unknown>> | undefined)?.temperature?.time);
  if (t != null) actual.momento = new Date(t * 1000).toISOString();

  const serie: Punto[] = [];
  if (historia) {
    const temp = evLista(historia, "outdoor", "temperature");
    const hum = evLista(historia, "outdoor", "humidity");
    const viento = evLista(historia, "wind", "wind_speed");
    const acum = (() => {
      const a = evLista(historia, "rainfall", "daily");
      return a.size > 0 ? a : evLista(historia, "rainfall_piezo", "daily");
    })();
    const tiempos = [...new Set([...temp.keys(), ...acum.keys()])].sort((a, b) => a - b);
    const lluvias = lluviaPorIntervalo(tiempos.map((x) => acum.get(x) ?? null));
    tiempos.forEach((x, i) =>
      serie.push({
        momento: new Date(x).toISOString(),
        temperaturaC: temp.get(x) ?? null,
        humedadPct: hum.get(x) ?? null,
        vientoKmh: viento.get(x) ?? null,
        lluviaMm: lluvias[i],
      })
    );
  }

  const diario: Dia[] = [];
  if (diaria) {
    const a = evLista(diaria, "rainfall", "daily");
    const acum = a.size > 0 ? a : evLista(diaria, "rainfall_piezo", "daily");
    for (const [x, mm] of [...acum.entries()].sort((p, q) => p[0] - q[0])) {
      diario.push({ dia: diaArgentina(x), lluviaMm: mm });
    }
  }
  return { actual: limpiarLectura(actual), serie, diario: unirDias(diario), avisos: [] };
}

/** Un registro por día (si hay dos del mismo día, el de más lluvia). */
function unirDias(dias: Dia[]): Dia[] {
  const m = new Map<string, Dia>();
  for (const d of dias) {
    const previo = m.get(d.dia);
    if (!previo || (d.lluviaMm ?? -1) > (previo.lluviaMm ?? -1)) m.set(d.dia, d);
  }
  return [...m.values()].sort((a, b) => a.dia.localeCompare(b.dia));
}

// ---------- Davis WeatherLink ----------

const WEATHERLINK = "https://api.weatherlink.com/v2";

async function davis(ruta: string, c: Credenciales, extra = ""): Promise<{ estado: number; cuerpo: Record<string, unknown> }> {
  requerir(c, "apiKey", "apiSecret");
  const url = `${WEATHERLINK}/${ruta}?api-key=${encodeURIComponent(c.apiKey)}${extra}`;
  const { estado, cuerpo } = await pedirJson(url, { headers: { "X-Api-Secret": c.apiSecret } });
  if (estado === 401) throw new ErrorMarca("La API Key o el API Secret de WeatherLink no son válidos.");
  return { estado, cuerpo: (cuerpo && typeof cuerpo === "object" ? cuerpo : {}) as Record<string, unknown> };
}

async function davisEstaciones(c: Credenciales): Promise<EstacionDeCuenta[]> {
  const { estado, cuerpo } = await davis("stations", c);
  if (estado !== 200) throw new ErrorMarca(`WeatherLink respondió con un error (${estado}).`);
  const lista = (cuerpo.stations ?? []) as Record<string, unknown>[];
  return lista.map((s) => ({
    identificador: String(s.station_id ?? ""),
    nombre: String(s.station_name ?? "Estación Davis"),
    latitud: numero(s.latitude),
    longitud: numero(s.longitude),
  })).filter((s) => s.identificador !== "");
}

/** Todos los registros de todos los sensores de una respuesta de WeatherLink. */
function registrosDavis(cuerpo: Record<string, unknown>): Record<string, unknown>[] {
  const sensores = (cuerpo.sensors ?? []) as { data?: Record<string, unknown>[] }[];
  return sensores.flatMap((s) => s.data ?? []);
}

/** Davis informa en °F, mph e inHg; la lluvia viene también en mm en los campos *_mm. */
export function lecturaDavis(registros: Record<string, unknown>[]): Lectura {
  const de = (...claves: string[]) => {
    for (const r of registros) {
      const n = primero(r, ...claves);
      if (n != null) return n;
    }
    return null;
  };
  const mm = (claveMm: string[], clavePulg: string[]) => de(...claveMm) ?? aplicar(de(...clavePulg), pulgAmm);
  const ts = de("ts");
  return limpiarLectura({
    momento: new Date((ts ?? Date.now() / 1000) * 1000).toISOString(),
    temperaturaC: aplicar(de("temp", "temp_out"), fAc),
    humedadPct: de("hum", "hum_out"),
    puntoRocioC: aplicar(de("dew_point"), fAc),
    vientoKmh: aplicar(de("wind_speed_avg_last_2_min", "wind_speed_last", "wind_speed", "wind_speed_avg_last_1_min"), mphAkmh),
    rafagaKmh: aplicar(de("wind_speed_hi_last_10_min", "wind_gust_10_min", "wind_speed_hi_last_2_min"), mphAkmh),
    direccionGrados: de("wind_dir_last", "wind_dir", "wind_dir_scalar_avg_last_2_min"),
    presionHpa: aplicar(de("bar_sea_level", "bar", "bar_absolute"), inHgAhPa),
    lluviaHoyMm: mm(["rainfall_daily_mm", "rainfall_day_mm", "rain_day_mm"], ["rainfall_daily_in", "rainfall_day_in", "rain_day_in"]),
    lluviaTasaMmH: mm(["rain_rate_last_mm", "rain_rate_mm"], ["rain_rate_last_in", "rain_rate_in"]),
    lluviaMesMm: mm(["rainfall_monthly_mm", "rainfall_month_mm", "rain_month_mm"], ["rainfall_monthly_in", "rainfall_month_in", "rain_month_in"]),
    lluviaAnioMm: mm(["rainfall_year_mm", "rain_year_mm"], ["rainfall_year_in", "rain_year_in"]),
    radiacionWm2: de("solar_rad"),
    uv: de("uv_index", "uv"),
    sueloTemperaturaC: aplicar(de("temp_1", "temp_soil_1"), fAc),
    sueloHumedadPct: null, // Davis mide humedad de suelo en centibares, no en %
  });
}

async function davisDatos(c: Credenciales, id: string): Promise<Datos> {
  const avisos: string[] = [];
  const actual = await davis(`current/${encodeURIComponent(id)}`, c);
  if (actual.estado === 403 || actual.estado === 404) {
    throw new ErrorMarca("Esa estación no está en la cuenta de WeatherLink, o la clave no tiene permiso para verla.");
  }
  if (actual.estado !== 200) throw new ErrorMarca(`WeatherLink respondió con un error (${actual.estado}).`);
  const lectura = lecturaDavis(registrosDavis(actual.cuerpo));

  const fin = Math.floor(Date.now() / 1000);
  const historia = await davis(`historic/${encodeURIComponent(id)}`, c, `&start-timestamp=${fin - 86400}&end-timestamp=${fin}`)
    .catch(() => null);
  const serie: Punto[] = [];
  if (historia && historia.estado === 200) {
    const porMomento = new Map<number, Record<string, unknown>[]>();
    for (const r of registrosDavis(historia.cuerpo)) {
      const ts = numero(r.ts);
      if (ts == null) continue;
      porMomento.set(ts, [...(porMomento.get(ts) ?? []), r]);
    }
    for (const ts of [...porMomento.keys()].sort((a, b) => a - b)) {
      const rs = porMomento.get(ts)!;
      const de = (...claves: string[]) => rs.map((r) => primero(r, ...claves)).find((v) => v != null) ?? null;
      serie.push({
        momento: new Date(ts * 1000).toISOString(),
        temperaturaC: redondear(aplicar(de("temp_last", "temp_avg", "temp_out"), fAc)),
        humedadPct: redondear(de("hum_last", "hum_out")),
        vientoKmh: redondear(aplicar(de("wind_speed_avg", "wind_speed"), mphAkmh)),
        lluviaMm: redondear(de("rainfall_mm", "rain_mm") ?? aplicar(de("rainfall_in"), pulgAmm)),
      });
    }
  } else if (historia && (historia.estado === 403 || historia.estado === 401)) {
    avisos.push("El gráfico de las últimas 24 h necesita una suscripción WeatherLink Pro. Se muestran las condiciones actuales.");
  }
  return { actual: lectura, serie, diario: [], avisos };
}

// ---------- Pessl Metos FieldClimate ----------

const FIELDCLIMATE = "https://api.fieldclimate.com/v2";

async function hmacHex(clave: string, texto: string): Promise<string> {
  const k = await crypto.subtle.importKey("raw", new TextEncoder().encode(clave), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const firma = new Uint8Array(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(texto)));
  return [...firma].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function metos(ruta: string, c: Credenciales): Promise<{ estado: number; cuerpo: unknown }> {
  requerir(c, "clavePublica", "clavePrivada");
  const fecha = new Date().toUTCString();
  const firma = await hmacHex(c.clavePrivada, "GET" + ruta + fecha + c.clavePublica);
  const r = await pedirJson(FIELDCLIMATE + ruta, {
    headers: { Accept: "application/json", Authorization: `hmac ${c.clavePublica}:${firma}`, "Request-Date": fecha },
  });
  if (r.estado === 401 || r.estado === 403) throw new ErrorMarca("Las claves de FieldClimate no son válidas o no tienen permiso para esa estación.");
  return r;
}

async function metosEstaciones(c: Credenciales): Promise<EstacionDeCuenta[]> {
  const { estado, cuerpo } = await metos("/user/stations", c);
  if (estado !== 200 || !Array.isArray(cuerpo)) throw new ErrorMarca(`FieldClimate respondió con un error (${estado}).`);
  return (cuerpo as Record<string, unknown>[]).map((s) => {
    const nombre = (s.name ?? {}) as Record<string, unknown>;
    const coords = (((s.position ?? {}) as Record<string, unknown>).geo as Record<string, unknown> | undefined)?.coordinates as unknown[] | undefined;
    return {
      identificador: String(nombre.original ?? ""),
      nombre: String(nombre.custom || nombre.original || "Estación Pessl"),
      longitud: numero(coords?.[0]),
      latitud: numero(coords?.[1]),
    };
  }).filter((s) => s.identificador !== "");
}

interface SensorMetos {
  nombre: string;
  unidad: string;
  valores: Record<string, (number | null)[]>;
}

/** Respuesta de /data: { dates: [...], data: [{ name, unit, values: { avg, min, max, sum, last } }] }. */
export function sensoresMetos(cuerpo: unknown): { fechas: number[]; sensores: SensorMetos[] } {
  const c = (cuerpo ?? {}) as Record<string, unknown>;
  const fechas = ((c.dates ?? []) as unknown[]).map((f) => {
    // Hora local de la estación, sin zona: se toma como hora de Argentina
    const s = String(f).replace(" ", "T");
    return Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(s) ? s : s + "-03:00");
  });
  const sensores = ((c.data ?? []) as Record<string, unknown>[]).map((d) => {
    const valores: Record<string, (number | null)[]> = {};
    for (const [k, v] of Object.entries((d.values ?? {}) as Record<string, unknown>)) {
      if (Array.isArray(v)) valores[k] = v.map(numero);
    }
    return { nombre: String(d.name ?? ""), unidad: String(d.unit ?? ""), valores };
  });
  return { fechas, sensores };
}

/** Serie de un sensor cuyo nombre contenga alguna de las [claves], en el agregado pedido. */
function serieMetos(sensores: SensorMetos[], claves: string[], agregado: string[], excluir: string[] = []): (number | null)[] | null {
  for (const clave of claves) {
    const s = sensores.find((x) => {
      const n = x.nombre.toLowerCase();
      return n.includes(clave) && !excluir.some((e) => n.includes(e));
    });
    if (!s) continue;
    for (const a of agregado) if (s.valores[a]) return s.valores[a].map((v) => (v != null && /m\/s/.test(s.unidad) && clave.includes("wind") ? msAkmh(v) : v));
  }
  return null;
}

function ultimo(v: (number | null)[] | null): number | null {
  if (!v) return null;
  for (let i = v.length - 1; i >= 0; i--) if (v[i] != null) return v[i];
  return null;
}

async function metosDatos(c: Credenciales, serial: string): Promise<Datos> {
  const id = encodeURIComponent(serial);
  const [horaria, diaria] = await Promise.all([
    metos(`/data/${id}/hourly/last/48h`, c),
    metos(`/data/${id}/daily/last/30d`, c).catch(() => null),
  ]);
  if (horaria.estado === 204) throw new ErrorMarca("La estación no tiene datos de las últimas 48 horas.");
  if (horaria.estado !== 200) throw new ErrorMarca(`FieldClimate respondió con un error (${horaria.estado}).`);
  const { fechas, sensores } = sensoresMetos(horaria.cuerpo);
  const temp = serieMetos(sensores, ["air temperature", "temperature"], ["avg", "last"], ["soil", "leaf", "dew"]);
  const hum = serieMetos(sensores, ["relative humidity"], ["avg", "last"]);
  const rocio = serieMetos(sensores, ["dew point"], ["avg", "last"]);
  const viento = serieMetos(sensores, ["wind speed"], ["avg", "last"], ["max"]);
  const rafaga = serieMetos(sensores, ["wind speed", "wind gust"], ["max"]);
  const dir = serieMetos(sensores, ["wind direction"], ["last", "avg"]);
  const lluvia = serieMetos(sensores, ["precipitation"], ["sum"]);
  const radiacion = serieMetos(sensores, ["solar radiation"], ["avg", "last"]);
  const suelo = serieMetos(sensores, ["volumetric", "soil moisture"], ["avg", "last"]);
  const sueloT = serieMetos(sensores, ["soil temperature"], ["avg", "last"]);
  const presion = serieMetos(sensores, ["pressure"], ["avg", "last"]);

  const n = fechas.length;
  const hoy = diaArgentina(Date.now());
  let lluviaHoy: number | null = null;
  if (lluvia) {
    for (let i = 0; i < n; i++) {
      // El valor horario de las 10:00 es la lluvia de 9 a 10: cuenta para el día de su comienzo
      if (diaArgentina(fechas[i] - 1) === hoy && lluvia[i] != null) lluviaHoy = (lluviaHoy ?? 0) + lluvia[i]!;
    }
    if (lluviaHoy == null) lluviaHoy = 0;
  }
  const actual = limpiarLectura({
    momento: new Date(n > 0 ? fechas[n - 1] : Date.now()).toISOString(),
    temperaturaC: ultimo(temp),
    humedadPct: ultimo(hum),
    puntoRocioC: ultimo(rocio),
    vientoKmh: ultimo(viento),
    rafagaKmh: ultimo(rafaga),
    direccionGrados: ultimo(dir),
    presionHpa: ultimo(presion),
    lluviaHoyMm: lluviaHoy,
    radiacionWm2: ultimo(radiacion),
    sueloHumedadPct: ultimo(suelo),
    sueloTemperaturaC: ultimo(sueloT),
  });

  const desde = Date.now() - 24 * 3600_000;
  const serie: Punto[] = [];
  for (let i = 0; i < n; i++) {
    if (fechas[i] < desde) continue;
    serie.push({
      momento: new Date(fechas[i]).toISOString(),
      temperaturaC: redondear(temp?.[i]),
      humedadPct: redondear(hum?.[i]),
      vientoKmh: redondear(viento?.[i]),
      lluviaMm: redondear(lluvia?.[i]),
    });
  }

  const diario: Dia[] = [];
  if (diaria && diaria.estado === 200) {
    const d = sensoresMetos(diaria.cuerpo);
    const ll = serieMetos(d.sensores, ["precipitation"], ["sum"]);
    const tmin = serieMetos(d.sensores, ["air temperature", "temperature"], ["min"], ["soil", "leaf", "dew"]);
    const tmax = serieMetos(d.sensores, ["air temperature", "temperature"], ["max"], ["soil", "leaf", "dew"]);
    d.fechas.forEach((f, i) =>
      diario.push({ dia: diaArgentina(f), lluviaMm: redondear(ll?.[i]), tempMinC: redondear(tmin?.[i]), tempMaxC: redondear(tmax?.[i]) })
    );
  }
  return { actual, serie, diario: unirDias(diario), avisos: [] };
}

// ---------- Envío directo (protocolos Wunderground y Ecowitt) ----------

/**
 * Lectura de un envío de la estación. Las dos variantes mandan unidades imperiales: tempf,
 * humidity, dewptf, windspeedmph, windgustmph, winddir, baromin/baromrelin, rainratein,
 * dailyrainin (o drain_piezo/rrain_piezo en los pluviómetros piezoeléctricos), solarradiation, UV,
 * soiltempf y soilmoisture.
 */
export function lecturaDeEnvio(p: Record<string, string>): Record<string, number | null> {
  const n = (...claves: string[]) => primero(p, ...claves);
  const lectura = {
    temperatura_c: aplicar(n("tempf"), fAc),
    humedad_pct: n("humidity"),
    punto_rocio_c: aplicar(n("dewptf"), fAc),
    viento_kmh: aplicar(n("windspeedmph"), mphAkmh),
    rafaga_kmh: aplicar(n("windgustmph"), mphAkmh),
    direccion_grados: n("winddir"),
    presion_hpa: aplicar(n("baromrelin", "baromin", "baromabsin"), inHgAhPa),
    lluvia_dia_mm: aplicar(n("dailyrainin", "drain_piezo"), pulgAmm),
    lluvia_tasa_mmh: aplicar(n("rainratein", "rrain_piezo"), pulgAmm),
    radiacion_wm2: n("solarradiation"),
    uv: n("UV", "uv"),
    suelo_temperatura_c: aplicar(n("soiltempf", "soiltemp1f", "tf_ch1"), fAc),
    suelo_humedad_pct: n("soilmoisture", "soilmoisture1"),
  };
  // Valores imposibles = sensor desconectado (algunas estaciones mandan -9999)
  const rangos: Record<string, [number, number]> = {
    temperatura_c: [-50, 60], humedad_pct: [0, 100], punto_rocio_c: [-60, 50], viento_kmh: [0, 300],
    rafaga_kmh: [0, 400], direccion_grados: [0, 360], presion_hpa: [800, 1100], lluvia_dia_mm: [0, 1000],
    lluvia_tasa_mmh: [0, 2000], radiacion_wm2: [0, 2000], uv: [0, 20], suelo_temperatura_c: [-30, 70],
    suelo_humedad_pct: [0, 100],
  };
  const r: Record<string, number | null> = {};
  for (const [k, v] of Object.entries(lectura)) {
    const [min, max] = rangos[k];
    r[k] = v != null && v >= min && v <= max ? redondear(v, 2) : null;
  }
  return r;
}

/** Instante de la lectura: dateutc ("2026-11-16 13:05:00" o "now"), si es razonable; si no, ahora. */
export function momentoDeEnvio(p: Record<string, string>, ahora = Date.now()): number {
  const d = p.dateutc;
  if (d && d !== "now") {
    const t = Date.parse(d.replace(" ", "T") + "Z");
    if (Number.isFinite(t) && Math.abs(t - ahora) < 3600_000) return t;
  }
  return ahora;
}

// ---------- Selección por marca ----------

export const MARCAS_CON_NUBE = ["davis", "ecowitt", "metos"] as const;
export type MarcaConNube = typeof MARCAS_CON_NUBE[number];

export function estacionesDeCuenta(marca: MarcaConNube, c: Credenciales): Promise<EstacionDeCuenta[]> {
  switch (marca) {
    case "davis": return davisEstaciones(c);
    case "ecowitt": return ecowittEstaciones(c);
    case "metos": return metosEstaciones(c);
  }
}

export function datosDeMarca(marca: MarcaConNube, c: Credenciales, identificador: string): Promise<Datos> {
  switch (marca) {
    case "davis": return davisDatos(c, identificador);
    case "ecowitt": return ecowittDatos(c, identificador);
    case "metos": return metosDatos(c, identificador);
  }
}

/** Solo los campos de credenciales de cada marca (nada más se guarda). */
export function credencialesDe(marca: MarcaConNube, entrada: unknown): Credenciales {
  const e = (entrada ?? {}) as Record<string, unknown>;
  const campos: Record<MarcaConNube, string[]> = {
    davis: ["apiKey", "apiSecret"],
    ecowitt: ["applicationKey", "apiKey"],
    metos: ["clavePublica", "clavePrivada"],
  };
  const r: Credenciales = {};
  for (const k of campos[marca]) {
    const v = typeof e[k] === "string" ? (e[k] as string).trim() : "";
    if (!v || v.length > 200) throw new ErrorMarca("Completá todas las claves de la cuenta de la estación.");
    r[k] = v;
  }
  return r;
}
