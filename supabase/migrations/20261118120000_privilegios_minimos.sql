-- GRANEX: privilegios mínimos sobre las tablas públicas (defensa en profundidad además de RLS).
--
-- Supabase da a los roles anon y authenticated todos los privilegios sobre cada tabla nueva, y RLS
-- es lo que filtra. Esta migración deja solo lo que la app usa de verdad:
--   - anon (sin sesión): nada. La app exige cuenta y las funciones públicas no tocan tablas.
--   - authenticated: leer, crear y editar sus lotes y su perfil (nunca borrar: los lotes usan
--     borrado lógico y el perfil se va con la cuenta); solo leer el resto. Las escrituras de
--     estaciones, lecturas y uso las hacen las Edge Functions con la clave de servicio.
-- TRUNCATE, REFERENCES y TRIGGER no pasan por RLS: no los tiene ningún rol de cliente.
-- Se puede correr dos veces.

revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;

revoke all on all tables in schema public from authenticated;
grant select, insert, update on public.lotes to authenticated;
grant select, insert, update on public.perfiles to authenticated;
grant select on public.estaciones, public.lecturas_estacion, public.diario_estacion, public.uso_sentinel to authenticated;
-- credenciales_estacion: sin acceso para ningún cliente (cifradas, solo la función las lee)

-- Las tablas que se creen más adelante tampoco nacen abiertas: cada migración da lo que necesita
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon;
alter default privileges for role postgres in schema public revoke all on functions from anon;

-- El disparador de lotes no se llama por RPC
revoke execute on function public.lotes_antes_de_guardar() from public, anon, authenticated;
