#!/usr/bin/env bash
# Starts the local Supabase stack from ./supabase (config.toml + migrations) with only the services
# the supabase-kt probe needs: Postgres, GoTrue (Auth), PostgREST, Realtime and the Kong gateway.
# Writes SUPABASE_URL and SUPABASE_ANON_KEY into supabase.env. Usage: supabase-start.sh <log-file>
set -u
LOG="${1:-supabase-log.txt}"
{
  echo "== supabase $(supabase --version)"
  supabase start -x imgproxy,vector,supavisor,postgres-meta,studio,mailpit,logflare,edge-runtime,storage-api
} >> "$LOG" 2>&1 || { echo "supabase start failed" | tee -a "$LOG"; tail -n 40 "$LOG"; exit 1; }

eval "$(supabase status -o env 2>> "$LOG")"
URL="${API_URL:-}"
KEY="${ANON_KEY:-${PUBLISHABLE_KEY:-}}"
if [ -z "$URL" ] || [ -z "$KEY" ]; then
  echo "supabase status gave no API_URL / ANON_KEY" | tee -a "$LOG"
  supabase status >> "$LOG" 2>&1
  exit 1
fi
printf 'SUPABASE_URL=%s\nSUPABASE_ANON_KEY=%s\n' "$URL" "$KEY" > supabase.env
echo "Supabase is up at $URL (services: db, auth, rest, realtime, kong)" | tee -a "$LOG"
