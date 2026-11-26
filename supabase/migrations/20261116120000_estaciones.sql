-- GRANEX: estaciones meteorológicas propias del productor.
--
-- Todo pasa por la Edge Function `estaciones` (con la clave de servicio): la app no escribe estas
-- tablas. Las claves de las cuentas de cada marca (Davis WeatherLink, Ecowitt, Pessl FieldClimate) se
-- guardan cifradas con AES-GCM en `credenciales_estacion`, con una clave que solo tiene la función
-- (secreto CLAVE_ESTACIONES), y esa tabla no tiene políticas: ningún usuario la puede leer.
-- Las estaciones con "envío directo" mandan sus lecturas a la función con un código propio; de ese
-- código solo se guarda el hash.

create table if not exists public.estaciones (
  id uuid primary key default gen_random_uuid(),
  usuario_id uuid not null references auth.users (id) on delete cascade,
  nombre text not null check (char_length(nombre) between 1 and 60),
  marca text not null check (marca in ('davis', 'ecowitt', 'metos', 'envio')),
  -- Davis: station_id; Ecowitt: MAC; Pessl: número de serie. Envío directo: null.
  identificador text check (char_length(identificador) <= 80),
  latitud double precision check (latitud between -90 and 90),
  longitud double precision check (longitud between -180 and 180),
  token_hash text unique,
  ultimo_envio timestamptz,
  creada_en timestamptz not null default now()
);

create index if not exists estaciones_usuario on public.estaciones (usuario_id);

alter table public.estaciones enable row level security;

create policy "estaciones: leer las propias" on public.estaciones
  for select to authenticated using ((select auth.uid()) = usuario_id);

create table if not exists public.credenciales_estacion (
  estacion_id uuid primary key references public.estaciones (id) on delete cascade,
  cifrado text not null
);

alter table public.credenciales_estacion enable row level security;
-- Sin políticas: solo la clave de servicio (la Edge Function) la puede leer o escribir
revoke all on public.credenciales_estacion from anon, authenticated;

-- Lecturas de las estaciones con envío directo (las de las marcas con nube quedan en su nube). Se
-- guardan 10 días; el resumen diario queda para siempre.
create table if not exists public.lecturas_estacion (
  estacion_id uuid not null references public.estaciones (id) on delete cascade,
  momento timestamptz not null,
  temperatura_c real,
  humedad_pct real,
  punto_rocio_c real,
  viento_kmh real,
  rafaga_kmh real,
  direccion_grados real,
  presion_hpa real,
  lluvia_dia_mm real,
  lluvia_tasa_mmh real,
  radiacion_wm2 real,
  uv real,
  suelo_temperatura_c real,
  suelo_humedad_pct real,
  primary key (estacion_id, momento)
);

alter table public.lecturas_estacion enable row level security;

create policy "lecturas_estacion: leer las propias" on public.lecturas_estacion
  for select to authenticated using (
    exists (select 1 from public.estaciones e where e.id = estacion_id and e.usuario_id = (select auth.uid()))
  );

-- Registro diario (día de Argentina): lluvia del día y temperaturas extremas
create table if not exists public.diario_estacion (
  estacion_id uuid not null references public.estaciones (id) on delete cascade,
  dia date not null,
  lluvia_mm real,
  temp_min_c real,
  temp_max_c real,
  primary key (estacion_id, dia)
);

alter table public.diario_estacion enable row level security;

create policy "diario_estacion: leer el propio" on public.diario_estacion
  for select to authenticated using (
    exists (select 1 from public.estaciones e where e.id = estacion_id and e.usuario_id = (select auth.uid()))
  );

-- Guarda una lectura del envío directo, actualiza el resumen del día y borra lo de más de 10 días.
-- La lluvia del día es el contador de la estación (se reinicia a medianoche): el máximo del día es
-- el total.
create or replace function public.guardar_lectura_estacion(p_estacion uuid, p_momento timestamptz, p jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_dia date := (p_momento at time zone 'America/Argentina/Buenos_Aires')::date;
  v_temp real := (p ->> 'temperatura_c')::real;
  v_lluvia real := (p ->> 'lluvia_dia_mm')::real;
begin
  insert into public.lecturas_estacion (
    estacion_id, momento, temperatura_c, humedad_pct, punto_rocio_c, viento_kmh, rafaga_kmh,
    direccion_grados, presion_hpa, lluvia_dia_mm, lluvia_tasa_mmh, radiacion_wm2, uv,
    suelo_temperatura_c, suelo_humedad_pct
  ) values (
    p_estacion, p_momento, v_temp, (p ->> 'humedad_pct')::real, (p ->> 'punto_rocio_c')::real,
    (p ->> 'viento_kmh')::real, (p ->> 'rafaga_kmh')::real, (p ->> 'direccion_grados')::real,
    (p ->> 'presion_hpa')::real, v_lluvia, (p ->> 'lluvia_tasa_mmh')::real,
    (p ->> 'radiacion_wm2')::real, (p ->> 'uv')::real, (p ->> 'suelo_temperatura_c')::real,
    (p ->> 'suelo_humedad_pct')::real
  ) on conflict do nothing;

  insert into public.diario_estacion as d (estacion_id, dia, lluvia_mm, temp_min_c, temp_max_c)
  values (p_estacion, v_dia, v_lluvia, v_temp, v_temp)
  on conflict (estacion_id, dia) do update set
    lluvia_mm = greatest(d.lluvia_mm, excluded.lluvia_mm),
    temp_min_c = least(d.temp_min_c, excluded.temp_min_c),
    temp_max_c = greatest(d.temp_max_c, excluded.temp_max_c);

  update public.estaciones set ultimo_envio = p_momento where id = p_estacion;

  delete from public.lecturas_estacion
  where estacion_id = p_estacion and momento < p_momento - interval '10 days';
end;
$$;

revoke all on function public.guardar_lectura_estacion(uuid, timestamptz, jsonb) from public, anon, authenticated;
