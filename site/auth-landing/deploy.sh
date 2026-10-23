#!/usr/bin/env bash
# Deploy magic-link fallback page + /demo mockup to Cloudflare Pages.
# Prerequisites: wrangler logged in (`npx wrangler login`).
# Demo auth: DEMO_BASIC_AUTH_USER / DEMO_BASIC_AUTH_PASSWORD via ./scripts/configure-demo-pages-auth.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$DIR/../.." && pwd)"
rsync -a --delete "$REPO/site/ios-demo/" "$DIR/demo/"
# Deploy from site root so wrangler always bundles functions/ (absolute-path deploy can skip the Worker).
cd "$DIR"
npx wrangler pages deploy . --project-name=morning-guard-auth --branch=main --commit-dirty=true
echo "Custom domain morning-guard.codingagents.cc is attached via API when using scripts/attach-pages-domain.sh"
