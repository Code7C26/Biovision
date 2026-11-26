-- GRANEX: límite diario de pedidos de imágenes Sentinel-2 por usuario.
-- La Edge Function `sentinel` consulta Sentinel Hub (Copernicus) con una sola cuenta de GRANEX, así
-- que la cuota mensual es compartida: cada pedido suma uno al contador del usuario y la función
-- rechaza los que pasan el límite.

create table if not exists public.uso_sentinel (
  usuario_id uuid not null references auth.users (id) on delete cascade,
  dia date not null,
  pedidos integer not null default 0 check (pedidos >= 0),
  primary key (usuario_id, dia)
);

alter table public.uso_sentinel enable row level security;

-- Cada uno ve su propio uso. No hay políticas de escritura: solo suma registrar_pedido_sentinel.
create policy "uso_sentinel: leer el propio" on public.uso_sentinel
  for select to authenticated using ((select auth.uid()) = usuario_id);

-- Suma un pedido al usuario con sesión y devuelve cuántos lleva hoy (día UTC). La llama la Edge
-- Function con el token del usuario; el límite lo decide la función, así que llamarla directo solo
-- sirve para gastar el cupo propio.
create or replace function public.registrar_pedido_sentinel()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  usuario uuid := auth.uid();
  total integer;
begin
  if usuario is null then
    raise exception 'Sin sesión' using errcode = '42501';
  end if;
  insert into public.uso_sentinel as u (usuario_id, dia, pedidos)
  values (usuario, (now() at time zone 'utc')::date, 1)
  on conflict (usuario_id, dia) do update set pedidos = u.pedidos + 1
  returning u.pedidos into total;
  return total;
end;
$$;

revoke all on function public.registrar_pedido_sentinel() from public, anon;
grant execute on function public.registrar_pedido_sentinel() to authenticated;
