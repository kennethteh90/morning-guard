#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CATALOG="$ROOT/MorningGuard/Localization/strings-catalog.json"
OUT="$ROOT/MorningGuard/Localizable.xcstrings"
python3 - "$CATALOG" "$OUT" <<'PY'
import json, sys
catalog_path, out_path = sys.argv[1], sys.argv[2]
with open(catalog_path, encoding="utf-8") as f:
    catalog = json.load(f)
strings = {
    "sourceLanguage": "en",
    "strings": {
        key: {
            "extractionState": "manual",
            "localizations": {
                "en": {"stringUnit": {"state": "translated", "value": value}}
            },
        }
        for key, value in sorted(catalog.items())
    },
    "version": "1.0",
}
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(strings, f, indent=2, ensure_ascii=False)
    f.write("\n")
print(f"Wrote {len(catalog)} keys to {out_path}")
PY
