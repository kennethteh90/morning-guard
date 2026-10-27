#!/usr/bin/env bash
# Deploy magic-link fallback page + /demo mockup to Cloudflare Pages.
# Prerequisites: wrangler logged in (`npx wrangler login`).
# Demo auth: DEMO_BASIC_AUTH_USER / DEMO_BASIC_AUTH_PASSWORD via ./scripts/configure-demo-pages-auth.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$DIR/../.." && pwd)"
rsync -a --delete "$REPO/site/ios-demo/" "$DIR/demo/"
mkdir -p "$DIR/iphone"
rsync -a \
  "$REPO/docs/ios/downtime-focus-guide.css" \
  "$REPO/docs/ios/downtime-and-focus-with-morning-guard.html" \
  "$REPO/docs/ios/downtime-and-focus-without-morning-guard.html" \
  "$DIR/iphone/"
rsync -a "$REPO/docs/ios/guide-assets/" "$DIR/iphone/guide-assets/"
cp "$REPO/docs/ios/downtime-and-focus-guide.html" "$DIR/iphone/index.html"
# Deploy from site root so wrangler always bundles functions/ (absolute-path deploy can skip the Worker).
cd "$DIR"
npx wrangler pages deploy . --project-name=morning-guard-auth --branch=main --commit-dirty=true
echo "Custom domain morning-guard.codingagents.cc is attached via API when using scripts/attach-pages-domain.sh"
