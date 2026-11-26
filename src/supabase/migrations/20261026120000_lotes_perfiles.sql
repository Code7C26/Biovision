-- GRANEX: perfiles de productores, lotes con PostGIS y archivos de lotes.
-- Aplicar con la CLI (`supabase db push`) o pegando el archivo en el SQL Editor del proyecto.
--
-- Seguridad: la app usa la clave pública (anon/publishable), que cualquiera puede leer del APK o de
-- la web. Lo que protege los datos es RLS: cada usuario solo ve y modifica sus filas. La clave secreta
-- (service_role) nunca va en la app.

create extension if not exists postgis with schema extensions;

-- ---------------------------------------------------------------------------------------------------
-- Perfiles: uno por cuenta (id = auth.users.id)
-- ---------------------------------------------------------------------------------------------------
create table if not exists public.perfiles (
  id uuid primary key references auth.users (id) on delete cascade,
  nombre_productor text check (char_length(nombre_productor) <= 120),
  establecimiento text check (char_length(establecimiento) <= 120),
  ubicacion_principal text check (char_length(ubicacion_principal) <= 200),
  hectareas_totales double precision check (hectareas_totales >= 0),
  actualizado_en timestamptz not null default now()
);

alter table public.perfiles enable row level security;

create policy "perfiles: leer el propio" on public.perfiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "perfiles: crear el propio" on public.perfiles
  for insert to authenticated with check ((select auth.uid()) = id);
create policy "perfiles: editar el propio" on public.perfiles
  for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- ---------------------------------------------------------------------------------------------------
-- Lotes
-- ---------------------------------------------------------------------------------------------------
create table if not exists public.lotes (
  -- UUID generado en el dispositivo: el mismo lote tiene el mismo id offline y en la nube
  id uuid primary key,
  usuario_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  nombre text not null check (char_length(nombre) between 1 and 120),
  provincia text not null default '' check (char_length(provincia) <= 80),
  superficie_ha double precision not null check (superficie_ha >= 0 and superficie_ha < 1000000),
  -- Feature GeoJSON tal como lo guarda la app (anillo cerrado, [lon, lat], WGS84)
  geojson jsonb not null check (jsonb_typeof(geojson) = 'object' and pg_column_size(geojson) < 1000000),
  -- PostGIS: la calcula el trigger a partir de geojson. Permite ST_Intersects, ST_Area(geom::geography), etc.
  geom extensions.geometry(Polygon, 4326),
  -- Reloj del dispositivo (ms) en la última edición: en un conflicto gana el mayor
  modificado_en bigint not null,
  creado_en_ms bigint,
  -- Borrado lógico: el borrado también tiene que llegar a los otros dispositivos
  eliminado boolean not null default false,
  creado_en timestamptz not null default now(),
  -- Reloj del servidor: cursor de la sincronización incremental
  actualizado_en timestamptz not null default now()
);

create index if not exists lotes_usuario_actualizado on public.lotes (usuario_id, actualizado_en);
create index if not exists lotes_geom on public.lotes using gist (geom);

alter table public.lotes enable row level security;

-- Sin política de DELETE: los borrados son lógicos (eliminado = true) para que lleguen a todos los
-- dispositivos. Para purgar filas viejas, una tarea del servidor con la clave secreta.
create policy "lotes: leer los propios" on public.lotes
  for select to authenticated using ((select auth.uid()) = usuario_id);
create policy "lotes: crear propios" on public.lotes
  for insert to authenticated with check ((select auth.uid()) = usuario_id);
create policy "lotes: editar los propios" on public.lotes
  for update to authenticated using ((select auth.uid()) = usuario_id) with check ((select auth.uid()) = usuario_id);

-- Antes de guardar:
--  * en una edición más vieja que la guardada (otro dispositivo editó después) se conserva la fila
--    actual: "gana el último que editó", aunque llegue antes al servidor la edición vieja;
--  * el dueño no se puede cambiar;
--  * se calcula la geometría PostGIS y la marca de tiempo del servidor.
create or replace function public.lotes_antes_de_guardar()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' then
    if new.modificado_en < old.modificado_en then
      return old;
    end if;
    new.usuario_id := old.usuario_id;
    new.creado_en := old.creado_en;
  end if;

  if new.geojson ? 'geometry' then
    new.geom := extensions.st_setsrid(extensions.st_geomfromgeojson((new.geojson -> 'geometry')::text), 4326);
  else
    new.geom := null;
  end if;

  new.actualizado_en := clock_timestamp();
  return new;
end;
$$;

create trigger lotes_antes_de_guardar
  before insert or update on public.lotes
  for each row execute function public.lotes_antes_de_guardar();

-- ---------------------------------------------------------------------------------------------------
-- Storage: archivos de lotes (GeoJSON, Shapefile, prescripciones). Bucket privado, una carpeta por usuario.
-- ---------------------------------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('lotes', 'lotes', false, 10485760)
on conflict (id) do nothing;

create policy "lotes (archivos): leer los propios" on storage.objects
  for select to authenticated
  using (bucket_id = 'lotes' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "lotes (archivos): subir a la carpeta propia" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'lotes' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "lotes (archivos): reemplazar los propios" on storage.objects
  for update to authenticated
  using (bucket_id = 'lotes' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "lotes (archivos): borrar los propios" on storage.objects
  for delete to authenticated
  using (bucket_id = 'lotes' and (storage.foldername(name))[1] = (select auth.uid())::text);
