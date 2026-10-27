#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT="${1:-build_tree}"
rm -rf "$OUT"; mkdir -p "$OUT"
for f in *; do
  [ -f "$f" ] || continue
  case "$f" in README.md|FILES.md|setup.sh|test_keystore_b64.txt|LICENSE|.gitignore) continue;; esac
  dest="${f//__//}"
  mkdir -p "$OUT/$(dirname "$dest")"
  if [[ "$dest" == *.b64 ]]; then base64 -d "$f" > "$OUT/${dest%.b64}"; else cp "$f" "$OUT/$dest"; fi
done
echo "setup ok: $(find "$OUT" -type f | wc -l) files"
