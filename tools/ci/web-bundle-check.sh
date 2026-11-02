#!/usr/bin/env bash
# Evidence about the production web bundle (ADR-004): where `import.meta` survives in the JS that
# index.html loads as a classic script (it must not), and webpack's warnings from the build log.
# Always exits 0; findings go to webApp/build/reports/runtime-evidence/web-bundle.txt.
set -u
DIST="webApp/build/dist/wasmJs/productionExecutable"
OUT="webApp/build/reports/runtime-evidence/web-bundle.txt"
mkdir -p "$(dirname "$OUT")"
{
  echo "== import.meta in $DIST/*.js (120 chars around each, first 15)"
  for f in "$DIST"/*.js; do
    [ -f "$f" ] || continue
    n=$(grep -o 'import\.meta' "$f" | wc -l)
    echo "-- $(basename "$f"): $n"
    grep -o '.\{0,120\}import\.meta.\{0,120\}' "$f" | head -n 15 | sed 's/^/   /'
  done
  echo "== webpack warnings and errors (web-log.txt)"
  grep -n -A4 'WARNING in\|ERROR in\|Critical dependency' web-log.txt 2>/dev/null | head -n 80
} > "$OUT" 2>&1
cat "$OUT"
exit 0
