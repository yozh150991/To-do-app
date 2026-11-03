-- pgTAP (spec §12.1): database tests run by `supabase test db` in CI (tools/ci/qa-tools-smoke.sh).
-- Ф0 covers the probe schema; RLS, RPC and grant tests of the product schema start in Ф5a.
begin;
create extension if not exists pgtap with schema extensions;

select plan(5);
select diag('pgTAP ' || extensions.pgtap_version()::text || '; ' || version());

select has_table('public', 'f0_probe', 'probe table exists');
select is(
    (select relrowsecurity from pg_class where oid = 'public.f0_probe'::regclass),
    true,
    'row level security is enabled on f0_probe'
);
select policies_are('public', 'f0_probe', array['f0_probe_own_rows'], 'only the own-rows policy');
select function_privs_are('public', 'f0_echo', array['text'], 'anon', array[]::text[], 'anon cannot call f0_echo');
select function_privs_are(
    'public', 'f0_echo', array['text'], 'authenticated', array['EXECUTE'], 'authenticated can call f0_echo'
);

select * from finish();
rollback;
