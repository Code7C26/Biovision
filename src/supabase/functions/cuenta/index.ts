// GRANEX: borrar la cuenta del usuario desde la app (lo exigen Google Play y el App Store).
//
// POST { accion: "eliminar" } con la sesión del usuario → { ok: true }
//
// Borra los archivos de su carpeta en el bucket "lotes" y después el usuario de Auth. Las filas de
// perfiles, lotes y uso_sentinel se borran solas (on delete cascade). Usa la clave de servicio que
// Supabase inyecta en las funciones (SUPABASE_SERVICE_ROLE_KEY): nunca sale del servidor.
// Errores: { error: "<mensaje en castellano>" }.

import { createClient } from "jsr:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-region, x-supabase-client-platform, x-supabase-client-platform-version",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const BUCKET = "lotes";

function json(cuerpo: unknown, estado = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

/** Todas las rutas de archivos debajo de [carpeta] (Storage lista de a un nivel). */
async function archivosDe(admin: ReturnType<typeof createClient>, carpeta: string): Promise<string[]> {
  const rutas: string[] = [];
  const pendientes = [carpeta];
  while (pendientes.length > 0) {
    const actual = pendientes.pop()!;
    for (let desde = 0; ; desde += 1000) {
      const { data, error } = await admin.storage.from(BUCKET).list(actual, { limit: 1000, offset: desde });
      if (error) throw error;
      for (const item of data ?? []) {
        const ruta = `${actual}/${item.name}`;
        // Las carpetas no tienen id
        if (item.id) rutas.push(ruta);
        else pendientes.push(ruta);
      }
      if ((data?.length ?? 0) < 1000) break;
    }
  }
  return rutas;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);
  try {
    const autorizacion = req.headers.get("Authorization") ?? "";
    const url = Deno.env.get("SUPABASE_URL")!;
    const publica = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { auth: { persistSession: false } });
    // La clave pública también es un JWT válido: hace falta un usuario real
    const { data: { user } } = await publica.auth.getUser(autorizacion.replace(/^Bearer\s+/i, ""));
    if (!user) return json({ error: "Iniciá sesión para eliminar tu cuenta." }, 401);

    let cuerpo: Record<string, unknown>;
    try {
      cuerpo = await req.json();
    } catch {
      return json({ error: "Pedido inválido." }, 400);
    }
    if (cuerpo.accion !== "eliminar") return json({ error: "Acción desconocida." }, 400);

    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
    const rutas = await archivosDe(admin, user.id);
    for (let i = 0; i < rutas.length; i += 100) {
      const { error } = await admin.storage.from(BUCKET).remove(rutas.slice(i, i + 100));
      if (error) throw error;
    }
    const { error } = await admin.auth.admin.deleteUser(user.id);
    if (error) throw error;
    return json({ ok: true });
  } catch (e) {
    console.error("eliminar cuenta", e);
    return json({ error: "No se pudo eliminar la cuenta. Probá de nuevo en unos minutos o escribinos a supportgranex@gmail.com." }, 500);
  }
});
