#!/usr/bin/env bash
# Deploy magic-link fallback page to Cloudflare Pages.
# Prerequisites: wrangler logged in (`npx wrangler login`).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
npx wrangler pages deploy "$DIR" --project-name=morning-guard-auth --branch=main --commit-dirty=true
echo "Custom domain morning-guard.codingagents.cc is attached via API when using scripts/attach-pages-domain.sh"
