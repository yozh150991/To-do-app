#!/usr/bin/env bash
# Smoke runs of the QA tools pinned in Ф0 (spec §12.1) against the local Supabase stack:
#   pgTAP     — `supabase test db` (supabase/tests/database/*.test.sql);
#   Toxiproxy — a proxy in front of the Supabase API with a latency toxic (sync tests, Ф5).
# Needs supabase.env from supabase-start.sh and TOXIPROXY_VERSION. Evidence goes to
# build/reports/runtime-evidence/qa-tools.txt. Exit code 1 if a tool did not work.
set -u
EVIDENCE_DIR="build/reports/runtime-evidence"
EVIDENCE="$EVIDENCE_DIR/qa-tools.txt"
mkdir -p "$EVIDENCE_DIR"
: > "$EVIDENCE"
evidence() { echo "$*" | tee -a "$EVIDENCE"; }
STATUS=0

# --- pgTAP ------------------------------------------------------------------------------------
if supabase test db > pgtap-log.txt 2>&1; then PGTAP=pass; else PGTAP=fail; STATUS=1; fi
evidence "pgTAP via supabase test db (CLI $(supabase --version 2>/dev/null)): $PGTAP"
sed 's/^/  /' pgtap-log.txt | tail -n 20 >> "$EVIDENCE"

# --- Toxiproxy --------------------------------------------------------------------------------
set -a; . ./supabase.env; set +a
BASE="https://github.com/Shopify/toxiproxy/releases/download/v${TOXIPROXY_VERSION}"
if curl -fsSL -o toxiproxy-server "$BASE/toxiproxy-server-linux-amd64" \
    && curl -fsSL -o toxiproxy-cli "$BASE/toxiproxy-cli-linux-amd64"; then
  chmod +x toxiproxy-server toxiproxy-cli
  ./toxiproxy-server -host 127.0.0.1 -port 8474 > toxiproxy-log.txt 2>&1 &
  SERVER=$!
  for _ in $(seq 1 30); do curl -fs http://127.0.0.1:8474/version > /dev/null && break; sleep 0.5; done
  evidence "toxiproxy server $(curl -fs http://127.0.0.1:8474/version)"
  UPSTREAM="${SUPABASE_URL#http://}"
  ./toxiproxy-cli create -l 127.0.0.1:54400 -u "$UPSTREAM" supabase_api >> toxiproxy-log.txt 2>&1
  probe() { curl -s -o /dev/null -w '%{http_code} %{time_total}' -H "apikey: $SUPABASE_ANON_KEY" "$1/rest/v1/"; }
  read -r DIRECT_CODE DIRECT_TIME <<< "$(probe "$SUPABASE_URL")"
  read -r CLEAN_CODE CLEAN_TIME <<< "$(probe http://127.0.0.1:54400)"
  ./toxiproxy-cli toxic add -t latency -a latency=400 supabase_api >> toxiproxy-log.txt 2>&1
  read -r SLOW_CODE SLOW_TIME <<< "$(probe http://127.0.0.1:54400)"
  evidence "  direct:          HTTP $DIRECT_CODE in ${DIRECT_TIME}s"
  evidence "  via proxy:       HTTP $CLEAN_CODE in ${CLEAN_TIME}s"
  evidence "  +400 ms latency: HTTP $SLOW_CODE in ${SLOW_TIME}s"
  if [ "$CLEAN_CODE" = "$DIRECT_CODE" ] && [ "$SLOW_CODE" = "$DIRECT_CODE" ] \
      && awk -v t="$SLOW_TIME" 'BEGIN { exit !(t >= 0.4) }'; then
    evidence "Toxiproxy ${TOXIPROXY_VERSION}: pass"
  else
    evidence "Toxiproxy ${TOXIPROXY_VERSION}: fail (same status through the proxy and >= 0.4 s with the toxic expected)"
    STATUS=1
  fi
  kill "$SERVER" 2> /dev/null || true
else
  evidence "Toxiproxy ${TOXIPROXY_VERSION}: fail (download)"
  STATUS=1
fi
sed 's/^/  /' toxiproxy-log.txt | tail -n 10 >> "$EVIDENCE"
exit "$STATUS"
