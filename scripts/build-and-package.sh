#!/usr/bin/env bash
# scripts/build-and-package.sh — build callmony and create the cPanel deploy archive.
# Follows DEPLOYMENT-GUIDE (1).md: pre-built .next, NO node_modules, symlinks preserved.
set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$PROJECT_DIR/builds"
BASE_PATH="${BASE_PATH:-}"
ARCHIVE_NAME="callmony-deploy-v1.2.0.tar.gz"
STAGE_DIR="$PROJECT_DIR/.deploy-stage"
BUILD_DIR=".next-build"   # side dir — the dev server keeps using .next/

cd "$PROJECT_DIR"

# [1] Build (NEXT_DIST_DIR keeps the running dev server's .next/ untouched)
NEXT_BUILD=true NODE_ENV=production BASE_PATH="$BASE_PATH" NEXT_DIST_DIR="$BUILD_DIR" npx next build

# [2] Stage runtime files (rule 2: NO node_modules)
rm -rf "$STAGE_DIR"; mkdir -p "$STAGE_DIR/logs" "$STAGE_DIR/scripts"
touch "$STAGE_DIR/logs/.gitkeep"
cp app.js                  "$STAGE_DIR/app.js"
cp deploy-package.json     "$STAGE_DIR/package.json"
cp next.config.mjs         "$STAGE_DIR/next.config.mjs"
cp .env.example            "$STAGE_DIR/.env.example"
cp -a public               "$STAGE_DIR/public"

# [3] Compiled output, minus build caches
mkdir -p "$STAGE_DIR/.next"
for item in "$BUILD_DIR"/*; do
  base=$(basename "$item")
  [ "$base" != "cache" ] && [ "$base" != "dev" ] && cp -a "$item" "$STAGE_DIR/.next/"
done
rm -rf "$STAGE_DIR/.next/types" "$STAGE_DIR/.next/diagnostics"

# [4] Archive — tar PRESERVES SYMLINKS by default (rule 7)
cd "$STAGE_DIR"
mkdir -p "$DEPLOY_DIR"
tar -czf "$DEPLOY_DIR/$ARCHIVE_NAME" \
  app.js package.json next.config.mjs .env.example logs/ .next/ public/
cd "$PROJECT_DIR"; rm -rf "$STAGE_DIR"

# [5] Verify the archive
BAD_NM=$(tar -tzf "$DEPLOY_DIR/$ARCHIVE_NAME" | grep -E "(^|/)node_modules(/|$)" \
         | grep -vE "^(\./)?\.next/node_modules/" || true)
[ -n "$BAD_NM" ] && { echo "FAIL: real node_modules in archive"; exit 1; }
tar -tzf "$DEPLOY_DIR/$ARCHIVE_NAME" | grep -q "BUILD_ID" || { echo "FAIL: no BUILD_ID"; exit 1; }
echo "OK → $DEPLOY_DIR/$ARCHIVE_NAME"
