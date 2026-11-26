// GRANEX: estaciones meteorológicas propias del productor.
//
// Con la sesión del usuario (POST JSON):
//   { accion: "listar" } → { estaciones: [Estacion] }
//   { accion: "agregar", marca: "davis"|"ecowitt"|"metos", nombre, credenciales: {...}, identificador? }
//     → { estacion } si la cuenta tiene una sola estación o se indicó cuál; si tiene varias y no se
//       indicó, { elegir: [{ identificador, nombre, latitud, longitud }] } (no guarda nada).
//   { accion: "agregar", marca: "envio", nombre, latitud?, longitud? } → { estacion, token }: el token
//       se muestra una sola vez (se guarda su hash).
//   { accion: "datos", id } → { actual, serie, diario, avisos }
//   { accion: "eliminar", id } → { ok: true }
//
// Sin sesión: la estación con envío directo manda sus lecturas a
//   …/functions/v1/estaciones/subir/<token>   (protocolo Ecowitt por POST o Wunderground por GET)
// y recibe "success". Esa ruta no lleva JWT: la función se despliega con verify_jwt = false y valida
// la sesión ella misma en todo lo demás.
//
// Las claves de Davis, Ecowitt y FieldClimate se validan contra la marca antes de guardarlas y quedan
// cifradas (AES-256-GCM, secreto CLAVE_ESTACIONES, con el id de la estación como dato asociado).
// Nunca vuelven a la app. Errores: { error: "<mensaje en castellano>" }.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { decodeBase64, encodeBase64 } from "jsr:@std/encoding@1/base64";
import {
  credencialesDe,
  type Datos,
  datosDeMarca,
  type Dia,
  ErrorMarca,
  estacionesDeCuenta,
  lecturaDeEnvio,
  lluviaPorIntervalo,
  MARCAS_CON_NUBE,
  type MarcaConNube,
  momentoDeEnvio,
  type Punto,
} from "./marcas.ts";

const MAX_ESTACIONES = 5;
const CACHE_MS = 60_000;
const INTERVALO_MINIMO_ENVIO_MS = 50_000;
const MAX_CUERPO_ENVIO = 8192;
const SIN_ENVIOS_MS = 2 * 3600_000;

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

const URL_SUPABASE = Deno.env.get("SUPABASE_URL")!;
const admin = createClient(URL_SUPABASE, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

// ---------- Cifrado de credenciales ----------

let claveCifrado: Promise<CryptoKey> | null = null;

function clave(): Promise<CryptoKey> {
  if (!claveCifrado) {
    const b64 = Deno.env.get("CLAVE_ESTACIONES") ?? "";
    const bytes = b64 ? decodeBase64(b64) : new Uint8Array();
    if (bytes.length !== 32) throw new Error("Falta el secreto CLAVE_ESTACIONES (32 bytes en base64)");
    claveCifrado = crypto.subtle.importKey("raw", bytes, "AES-GCM", false, ["encrypt", "decrypt"]);
  }
  return claveCifrado;
}

async function cifrar(texto: string, estacionId: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const datos = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv, additionalData: new TextEncoder().encode(estacionId) },
      await clave(),
      new TextEncoder().encode(texto),
    ),
  );
  const todo = new Uint8Array(iv.length + datos.length);
  todo.set(iv);
  todo.set(datos, iv.length);
  return encodeBase64(todo);
}

async function descifrar(b64: string, estacionId: string): Promise<string> {
  const todo = decodeBase64(b64);
  const texto = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: todo.slice(0, 12), additionalData: new TextEncoder().encode(estacionId) },
    await clave(),
    todo.slice(12),
  );
  return new TextDecoder().decode(texto);
}

async function sha256(texto: string): Promise<string> {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto)));
  return [...h].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function tokenNuevo(): string {
  return encodeBase64(crypto.getRandomValues(new Uint8Array(24))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ---------- Estaciones del usuario ----------

interface FilaEstacion {
  id: string;
  usuario_id: string;
  nombre: string;
  marca: string;
  identificador: string | null;
  latitud: number | null;
  longitud: number | null;
  ultimo_envio: string | null;
  creada_en: string;
}

const COLUMNAS = "id, usuario_id, nombre, marca, identificador, latitud, longitud, ultimo_envio, creada_en";

function aCliente(e: FilaEstacion) {
  return {
    id: e.id,
    nombre: e.nombre,
    marca: e.marca,
    identificador: e.identificador,
    latitud: e.latitud,
    longitud: e.longitud,
    ultimoEnvio: e.ultimo_envio,
  };
}

async function estacionDelUsuario(usuario: string, id: unknown): Promise<FilaEstacion> {
  if (typeof id !== "string" || !/^[0-9a-f-]{36}$/i.test(id)) throw new ErrorUsuario(400, "Estación inválida.");
  const { data, error } = await admin.from("estaciones").select(COLUMNAS).eq("id", id).eq("usuario_id", usuario).maybeSingle();
  if (error) throw error;
  if (!data) throw new ErrorUsuario(404, "La estación ya no existe.");
  return data as FilaEstacion;
}

function nombreValido(v: unknown): string {
  const n = typeof v === "string" ? v.trim() : "";
  if (n.length < 1 || n.length > 60) throw new ErrorUsuario(400, "Poné un nombre de hasta 60 letras para la estación.");
  return n;
}

function coordenada(v: unknown, limite: number): number | null {
  return typeof v === "number" && Number.isFinite(v) && Math.abs(v) <= limite ? v : null;
}

async function agregar(usuario: string, cuerpo: Record<string, unknown>): Promise<unknown> {
  const { count, error: errorCuenta } = await admin.from("estaciones").select("id", { count: "exact", head: true }).eq("usuario_id", usuario);
  if (errorCuenta) throw errorCuenta;
  if ((count ?? 0) >= MAX_ESTACIONES) throw new ErrorUsuario(400, `Podés conectar hasta ${MAX_ESTACIONES} estaciones.`);

  const nombre = nombreValido(cuerpo.nombre);
  const marca = String(cuerpo.marca ?? "");

  if (marca === "envio") {
    const token = tokenNuevo();
    const { data, error } = await admin.from("estaciones").insert({
      usuario_id: usuario,
      nombre,
      marca,
      latitud: coordenada(cuerpo.latitud, 90),
      longitud: coordenada(cuerpo.longitud, 180),
      token_hash: await sha256(token),
    }).select(COLUMNAS).single();
    if (error) throw error;
    return { estacion: aCliente(data as FilaEstacion), token };
  }

  if (!(MARCAS_CON_NUBE as readonly string[]).includes(marca)) throw new ErrorUsuario(400, "Marca de estación desconocida.");
  const m = marca as MarcaConNube;
  const credenciales = credencialesDe(m, cuerpo.credenciales);
  // Valida las claves y averigua qué estaciones tiene la cuenta
  const disponibles = await estacionesDeCuenta(m, credenciales);
  if (disponibles.length === 0) throw new ErrorUsuario(400, "Esa cuenta no tiene ninguna estación.");
  const pedido = typeof cuerpo.identificador === "string" ? cuerpo.identificador.trim() : "";
  let elegida = pedido ? disponibles.find((d) => d.identificador.toLowerCase() === pedido.toLowerCase()) : undefined;
  if (pedido && !elegida) throw new ErrorUsuario(400, "Esa estación no está en la cuenta.");
  if (!elegida) {
    if (disponibles.length > 1) return { elegir: disponibles };
    elegida = disponibles[0];
  }
  // Prueba que se puedan leer los datos antes de guardar
  await datosDeMarca(m, credenciales, elegida.identificador);

  const { data, error } = await admin.from("estaciones").insert({
    usuario_id: usuario,
    nombre,
    marca,
    identificador: elegida.identificador,
    latitud: elegida.latitud ?? null,
    longitud: elegida.longitud ?? null,
  }).select(COLUMNAS).single();
  if (error) throw error;
  const fila = data as FilaEstacion;
  const { error: errorCred } = await admin.from("credenciales_estacion").insert({
    estacion_id: fila.id,
    cifrado: await cifrar(JSON.stringify(credenciales), fila.id),
  });
  if (errorCred) {
    await admin.from("estaciones").delete().eq("id", fila.id);
    throw errorCred;
  }
  return { estacion: aCliente(fila) };
}

// ---------- Datos ----------

const cache = new Map<string, { hasta: number; datos: Datos }>();

async function datos(usuario: string, id: unknown): Promise<Datos> {
  const e = await estacionDelUsuario(usuario, id);
  const enCache = cache.get(e.id);
  if (enCache && enCache.hasta > Date.now()) return enCache.datos;

  let resultado: Datos;
  if (e.marca === "envio") {
    resultado = await datosDeEnvio(e);
  } else {
    const { data, error } = await admin.from("credenciales_estacion").select("cifrado").eq("estacion_id", e.id).maybeSingle();
    if (error) throw error;
    if (!data) throw new ErrorUsuario(400, "Faltan las claves de la estación: eliminala y conectala de nuevo.");
    const credenciales = JSON.parse(await descifrar((data as { cifrado: string }).cifrado, e.id));
    resultado = await datosDeMarca(e.marca as MarcaConNube, credenciales, e.identificador ?? "");
  }
  cache.set(e.id, { hasta: Date.now() + CACHE_MS, datos: resultado });
  if (cache.size > 500) cache.delete(cache.keys().next().value!);
  return resultado;
}

interface FilaLectura {
  momento: string;
  temperatura_c: number | null;
  humedad_pct: number | null;
  punto_rocio_c: number | null;
  viento_kmh: number | null;
  rafaga_kmh: number | null;
  direccion_grados: number | null;
  presion_hpa: number | null;
  lluvia_dia_mm: number | null;
  lluvia_tasa_mmh: number | null;
  radiacion_wm2: number | null;
  uv: number | null;
  suelo_temperatura_c: number | null;
  suelo_humedad_pct: number | null;
}

async function datosDeEnvio(e: FilaEstacion): Promise<Datos> {
  const desde = new Date(Date.now() - 24 * 3600_000).toISOString();
  const [lecturas, diario] = await Promise.all([
    admin.from("lecturas_estacion").select("*").eq("estacion_id", e.id).gte("momento", desde).order("momento").limit(3000),
    admin.from("diario_estacion").select("dia, lluvia_mm, temp_min_c, temp_max_c").eq("estacion_id", e.id)
      .order("dia", { ascending: false }).limit(400),
  ]);
  if (lecturas.error) throw lecturas.error;
  if (diario.error) throw diario.error;
  const filas = (lecturas.data ?? []) as FilaLectura[];
  const dias: Dia[] = ((diario.data ?? []) as { dia: string; lluvia_mm: number | null; temp_min_c: number | null; temp_max_c: number | null }[])
    .map((d) => ({ dia: d.dia, lluviaMm: d.lluvia_mm, tempMinC: d.temp_min_c, tempMaxC: d.temp_max_c }))
    .reverse();

  const avisos: string[] = [];
  const ultima = filas[filas.length - 1];
  if (!e.ultimo_envio) avisos.push("Todavía no llegó ningún dato de la estación. Revisá la configuración del servidor propio en la estación.");
  else if (Date.now() - Date.parse(e.ultimo_envio) > SIN_ENVIOS_MS) avisos.push("La estación no envía datos hace más de 2 horas. Revisá que tenga conexión a internet.");

  // Serie: un punto cada 30 minutos (el último de cada media hora)
  const porMedia = new Map<number, FilaLectura>();
  for (const f of filas) porMedia.set(Math.floor(Date.parse(f.momento) / 1800_000), f);
  const puntosFilas = [...porMedia.values()];
  const lluvias = lluviaPorIntervalo(puntosFilas.map((f) => f.lluvia_dia_mm));
  const serie: Punto[] = puntosFilas.map((f, i) => ({
    momento: f.momento,
    temperaturaC: f.temperatura_c,
    humedadPct: f.humedad_pct,
    vientoKmh: f.viento_kmh,
    lluviaMm: lluvias[i],
  }));

  // Días de Argentina (UTC−3)
  const hoyAr = new Date(Date.now() - 3 * 3600_000);
  const inicioSemana = new Date(hoyAr.getTime() - 6 * 86400_000).toISOString().slice(0, 10);
  const inicioMes = hoyAr.toISOString().slice(0, 8) + "01";
  const inicioAnio = hoyAr.toISOString().slice(0, 4) + "-01-01";
  const sumaDesdeFecha = (inicio: string) =>
    Math.round(dias.filter((d) => d.dia >= inicio).reduce((s, d) => s + (d.lluviaMm ?? 0), 0) * 10) / 10;

  return {
    actual: ultima
      ? {
        momento: ultima.momento,
        temperaturaC: ultima.temperatura_c,
        humedadPct: ultima.humedad_pct,
        puntoRocioC: ultima.punto_rocio_c,
        vientoKmh: ultima.viento_kmh,
        rafagaKmh: ultima.rafaga_kmh,
        direccionGrados: ultima.direccion_grados,
        presionHpa: ultima.presion_hpa,
        lluviaHoyMm: ultima.lluvia_dia_mm,
        lluviaTasaMmH: ultima.lluvia_tasa_mmh,
        lluviaSemanaMm: sumaDesdeFecha(inicioSemana),
        lluviaMesMm: sumaDesdeFecha(inicioMes),
        // El registro de GRANEX empieza cuando se conectó la estación: el año no está completo
        lluviaAnioMm: Date.parse(e.creada_en) <= Date.parse(inicioAnio + "T03:00:00Z") ? sumaDesdeFecha(inicioAnio) : null,
        radiacionWm2: ultima.radiacion_wm2,
        uv: ultima.uv,
        sueloTemperaturaC: ultima.suelo_temperatura_c,
        sueloHumedadPct: ultima.suelo_humedad_pct,
      }
      : null,
    serie,
    diario: dias.slice(-30),
    avisos,
  };
}

// ---------- Envío directo ----------

async function recibirEnvio(req: Request, tokenRuta: string | null): Promise<Response> {
  const ok = () => new Response("success", { status: 200, headers: { "Content-Type": "text/plain" } });
  const params: Record<string, string> = {};
  const url = new URL(req.url);
  url.searchParams.forEach((v, k) => (params[k] = v));
  if (req.method === "POST") {
    // Se rechaza antes de leer: un cuerpo enorme no llega a ocupar memoria
    const declarado = Number(req.headers.get("Content-Length") ?? "0");
    if (declarado > MAX_CUERPO_ENVIO) return new Response("too large", { status: 413 });
    const texto = await req.text();
    if (texto.length > MAX_CUERPO_ENVIO) return new Response("too large", { status: 413 });
    new URLSearchParams(texto).forEach((v, k) => (params[k] = v));
  }
  const token = tokenRuta ?? params.PASSWORD ?? params.token ?? "";
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(token)) return new Response("unauthorized", { status: 401 });

  const { data, error } = await admin.from("estaciones").select("id, ultimo_envio").eq("token_hash", await sha256(token)).maybeSingle();
  if (error) throw error;
  if (!data) return new Response("unauthorized", { status: 401 });
  const fila = data as { id: string; ultimo_envio: string | null };

  const momento = momentoDeEnvio(params);
  // Algunas estaciones mandan cada 16 s: se guarda como mucho una lectura por minuto
  if (fila.ultimo_envio && momento - Date.parse(fila.ultimo_envio) < INTERVALO_MINIMO_ENVIO_MS) return ok();

  const lectura = lecturaDeEnvio(params);
  if (Object.values(lectura).every((v) => v == null)) return new Response("no data", { status: 400 });
  const { error: errorGuardar } = await admin.rpc("guardar_lectura_estacion", {
    p_estacion: fila.id,
    p_momento: new Date(momento).toISOString(),
    p: lectura,
  });
  if (errorGuardar) throw errorGuardar;
  cache.delete(fila.id);
  return ok();
}

// ---------- Servidor ----------

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const ruta = new URL(req.url).pathname;
  const subir = ruta.match(/\/subir(?:\/([A-Za-z0-9_-]+))?\/?$/);
  try {
    if (subir) return await recibirEnvio(req, subir[1] ?? null);
    if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);

    const autorizacion = req.headers.get("Authorization") ?? "";
    const publica = createClient(URL_SUPABASE, Deno.env.get("SUPABASE_ANON_KEY")!, { auth: { persistSession: false } });
    // La clave pública también es un JWT válido: hace falta un usuario real
    const { data: { user } } = await publica.auth.getUser(autorizacion.replace(/^Bearer\s+/i, ""));
    if (!user) return json({ error: "Iniciá sesión para usar tus estaciones." }, 401);

    let cuerpo: Record<string, unknown>;
    try {
      cuerpo = await req.json();
    } catch {
      return json({ error: "Pedido inválido." }, 400);
    }

    switch (cuerpo.accion) {
      case "listar": {
        const { data, error } = await admin.from("estaciones").select(COLUMNAS).eq("usuario_id", user.id).order("creada_en");
        if (error) throw error;
        return json({ estaciones: (data as FilaEstacion[]).map(aCliente) });
      }
      case "agregar":
        return json(await agregar(user.id, cuerpo));
      case "datos":
        return json(await datos(user.id, cuerpo.id));
      case "eliminar": {
        const e = await estacionDelUsuario(user.id, cuerpo.id);
        const { error } = await admin.from("estaciones").delete().eq("id", e.id);
        if (error) throw error;
        cache.delete(e.id);
        return json({ ok: true });
      }
      default:
        return json({ error: "Acción desconocida." }, 400);
    }
  } catch (e) {
    if (e instanceof ErrorUsuario) return json({ error: e.message }, e.estado);
    if (e instanceof ErrorMarca) return json({ error: e.message }, 502);
    console.error("estaciones", e);
    if (subir) return new Response("error", { status: 500 });
    return json({ error: "No se pudo completar el pedido. Probá de nuevo en unos minutos." }, 500);
  }
});
