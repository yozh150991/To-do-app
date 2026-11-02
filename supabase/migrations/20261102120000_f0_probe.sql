-- Ф0 supabase-kt module matrix (spec §3.3.1): a probe table, RLS, an RPC and Realtime.
-- Not the product schema: the real cloud schema, gateway and grants are designed in phase Ф5a.

create table public.f0_probe (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    content text not null,
    created_at timestamptz not null default now()
);

alter table public.f0_probe enable row level security;

create policy f0_probe_own_rows on public.f0_probe
    for all to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

revoke all on public.f0_probe from anon;
grant select, insert, update, delete on public.f0_probe to authenticated;

-- Realtime postgres_changes for the probe table (RLS applies to subscribers too).
alter publication supabase_realtime add table public.f0_probe;

create function public.f0_echo(message text)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
    select jsonb_build_object('message', message, 'uid', auth.uid());
$$;

revoke execute on function public.f0_echo(text) from public, anon;
grant execute on function public.f0_echo(text) to authenticated;
