# Zero-Terminal Web App Deployment Guide
## cPanel + CloudLinux + Passenger + Node.js + MySQL

> A **reusable pattern guide** for deploying any Node.js web app (Next.js, Express,
> Nuxt, SvelteKit, Remix, NestJS…) to a shared cPanel host **without SSH access**,
> extracted from a production deployment that follows this exact flow.
>
> Copy this file into any project and adapt the `{placeholders}`.

---

## Table of Contents

1. [When to use this guide](#1-when-to-use-this-guide)
2. [The deployment philosophy — 7 rules](#2-the-deployment-philosophy--7-rules)
3. [The full pipeline at a glance](#3-the-full-pipeline-at-a-glance)
4. [Host prerequisites checklist](#4-host-prerequisites-checklist)
5. [Prepare the app — reusable code templates](#5-prepare-the-app--reusable-code-templates)
6. [Build & package script](#6-build--package-script)
7. [Create the MySQL database](#7-create-the-mysql-database)
8. [Upload & extract the archive](#8-upload--extract-the-archive)
9. [Create the Node.js app in cPanel](#9-create-the-nodejs-app-in-cpanel)
10. [Set environment variables](#10-set-environment-variables)
11. [Run NPM Install](#11-run-npm-install)
12. [Start, verify, first-run setup](#12-start-verify-first-run-setup)
13. [Re-deploys and rollback](#13-re-deploys-and-rollback)
14. [File permissions](#14-file-permissions)
15. [Troubleshooting table](#15-troubleshooting-table)
16. [Performance tuning](#16-performance-tuning)
17. [Security checklist](#17-security-checklist)
18. [Master checklist (print me)](#18-master-checklist-print-me)
19. [Appendix: adapting to other frameworks](#19-appendix-adapting-to-other-frameworks)

---

## 1. When to use this guide

Use this guide when **all** of these are true:

- Your host is a **shared / managed cPanel account** (CloudLinux + LiteSpeed +
  Phusion Passenger) — the most common budget hosting setup.
- You have **no SSH** and no terminal on the server. Everything happens through
  cPanel's File Manager and "Setup Node.js App" UI.
- The database is **MySQL or MariaDB** provided by cPanel.
- Your app is a **Node.js web app** (any framework).

Do NOT use this guide if you have a VPS/root access (use Docker, PM2, systemd,
nginx instead — that's a different, simpler world) or if you're deploying to
Vercel/Netlify/Render (they build and run the app for you).

**The core problem this guide solves:** you cannot run `npm install`, `next
build`, or database migrations on a locked-down shared host. Therefore the app
must arrive **pre-built**, the host must install dependencies through its own
managed Node environment, and the database schema must be applied by the app
itself at startup.

---

## 2. The deployment philosophy — 7 rules

These rules are the distillation of every hard lesson in this deployment
pattern. Follow them and the deploy works; break one and you'll spend an
afternoon in error logs.

1. **Build on your machine, ship an archive.** The server never compiles
   anything. You build (compile TS, bundle CSS, produce `.next/` or `dist/`)
   where the toolchain exists, and upload a `.tar.gz`.
2. **Never ship `node_modules`.** cPanel's "Run NPM Install" button creates a
   CloudLinux-managed `node_modules` inside a nodevenv and points `NODE_PATH`
   at it. Your archive ships a *runtime-only* `package.json` and the host
   installs it. (Bonus: archives stay small — MBs, not hundreds of MBs.)
3. **The entry point is a tiny plain-JS file** that `require()`s the framework
   — not a `.bin` path lookup. `require('next')` respects `NODE_PATH`, which
   cPanel sets for you. A `node_modules/.bin/next` path check would fail.
4. **Config files must be plain JavaScript** (`next.config.mjs` /
   `app.config.js`) — not TypeScript. The runtime has no build step to compile
   them.
5. **Migrate at app startup, never at install.** `npm install` has no env
   vars, no cwd guarantee, no DB credentials. The *app process* does. Ship a
   startup hook that pings the DB and applies idempotent migrations, so the
   first "Start" button click is also your database provisioning step.
6. **A health endpoint from day one.** `/api/health` returns 200 + JSON when
   everything is up, 503 with a `requestId` when it's not. It is your only
   window into the server because you have no terminal.
7. **Preserve symlinks in the archive.** Modern bundlers (Turbopack/webpack 5)
   fill `.next/` with hashed symlinks. `tar` preserves them by default —
   **do not** let a zip tool or a "helpful" normalization step flatten them,
   and never re-pack with flags that dereference links.

---

## 3. The full pipeline at a glance

```
 YOUR MACHINE (build)                     THE HOST (cPanel)
 ┌────────────────────────┐               ┌───────────────────────────────────┐
 │ 1. next build          │               │ 4. File Manager → Upload .tar.gz  │
 │    (NODE_BUILD=true)   │               │    → Extract into app root        │
 │ 2. stage runtime files │    ── FTP ──► │ 5. Setup Node.js App              │
 │    (entry, package,    │               │    Node 20 · Production · app.js  │
 │     config, migrations)│               │ 6. env vars (DB URL, secrets)     │
 │ 3. tar -czf app.tar.gz │               │ 7. Run NPM Install (nodevenv)     │
 │    (no node_modules!)  │               │ 8. Start → migrations auto-run    │
 └────────────────────────┘               │    → /api/health = green         │
                                          └───────────────────────────────────┘
```

What the app process does the moment Passenger starts it:

```
Passenger boots app.js
  └─ http.createServer → framework handler
       └─ framework startup hook (instrumentation)
            ├─ db.ping()                      → is MySQL reachable?
            ├─ runMigrations()                → apply pending, idempotent
            └─ log "Server starting up"       → logs/app.log
```

If the DB is down, the app **does not crash-loop** — it stays up, `/api/health`
returns 503 with the error, and every failing request carries a `requestId`
that also appears in `logs/error.log`.

---

## 4. Host prerequisites checklist

Before starting, confirm your cPanel account has:

- [ ] **"Setup Node.js App"** (Software section) — this means Passenger + CloudLinux Node selector
- [ ] **Node.js 20 available** in the version dropdown (Next.js ≥14 needs 18.6+; 20 is the safe choice)
- [ ] **MySQL 5.7+ or MariaDB 10.4+** ("MySQL Databases" / "MySQL Database Wizard")
- [ ] **File Manager** with upload + extract (tar.gz)
- [ ] Enough disk quota for the app (~200–500 MB installed)

You do NOT need: SSH, Git, cron, a dedicated IP, or shell access.

---

## 5. Prepare the app — reusable code templates

These four files are the deployment contract. Everything else is your normal
app code.

### 5.1 The Passenger entry point — `app.js`

Tiny, plain JavaScript, no build step. For a Next.js app:

```js
/**
 * app.js — cPanel / Passenger entry point.
 *
 * Why require('next') instead of node_modules/.bin/next?
 * cPanel's "Run NPM Install" creates node_modules in the CloudLinux
 * nodevenv and sets NODE_PATH to it. require() honors NODE_PATH;
 * a .bin path lookup does not.
 */
const { createServer } = require('http')
const path = require('path')
const fs = require('fs')

const PORT = process.env.PORT || 3000
const HOSTNAME = process.env.HOSTNAME || '0.0.0.0'

let next
try {
  next = require('next')          // respects NODE_PATH (cPanel sets this)
} catch (err) {
  console.error('Cannot find "next". Did you click "Run NPM Install" in cPanel?')
  console.error(err.message)
  process.exit(1)
}

// Find the directory that contains the build output
const appRoot = [process.cwd(), __dirname, path.join(__dirname, '..')].find(p =>
  fs.existsSync(path.join(p, '.next', 'BUILD_ID'))   // Next.js build marker
)

if (!appRoot) {
  console.error('Cannot find .next/BUILD_ID. Make sure the .next/ folder is uploaded.')
  process.exit(1)
}

const app = next({ dev: false, dir: appRoot })
const handle = app.getRequestHandler()

app.prepare().then(() => {
  createServer((req, res) => {
    const parsedUrl = new URL(req.url, `http://${HOSTNAME}:${PORT}`)
    handle(req, res, {
      pathname: parsedUrl.pathname,
      query: Object.fromEntries(parsedUrl.searchParams.entries()),
    })
  }).listen(PORT, HOSTNAME, () => {
    console.log(`App ready on http://${HOSTNAME}:${PORT}`)
    console.log(`Serving from ${appRoot} (NODE_ENV=${process.env.NODE_ENV})`)
  })
})
```

(For non-Next.js apps see [Appendix A](#19-appendix-adapting-to-other-frameworks) —
the same skeleton with your framework's handler.)

### 5.2 The runtime `package.json` (deploy manifest)

Your dev `package.json` has build tools, native dev-only drivers, TypeScript,
linters. **None of that belongs on the server.** Keep a separate
`deploy-package.json` with only what the *runtime* needs, and have your build
script copy it in as `package.json`:

```json
{
  "name": "{your-app}",
  "version": "1.0.0",
  "private": true,
  "main": "app.js",
  "scripts": {
    "start": "node app.js",
    "migrate": "node scripts/migrate.js"
  },
  "engines": { "node": ">=18.0.0" },
  "dependencies": {
    "mysql2": "^3.24.3",
    "next": "16.1.3",
    "pino": "^10.3.1",
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  }
}
```

Rules of thumb:

- **No devDependencies at all** — cPanel runs a *production* install.
- **No native dev-only modules** (e.g. `better-sqlite3` when prod uses MySQL).
  Native compilation can fail on CloudLinux with cryptic
  `AttributeError: 'NoneType'` errors.
- Pin the framework version exactly (`"next": "16.1.3"`, no `^`) so the host
  never installs a version you didn't build against.

### 5.3 Plain-JS config — `next.config.mjs`

TypeScript config files need a compiler the server doesn't have. Use `.mjs`:

```js
/** next.config.mjs — plain JS on purpose (rule 4). */
const basePath = process.env.BASE_PATH || ''
export default {
  basePath,
  output: undefined,       // NOT 'standalone' — Passenger runs app.js itself
  compress: true,
}
```

### 5.4 The health endpoint — `/api/health`

Your only observability window. It must distinguish *app is up* from *DB is
down*, and it must never throw:

```ts
// src/app/api/health/route.ts (Next.js example)
import { NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

export async function GET() {
  const checks: Record<string, unknown> = { app: 'ok', backend: 'mysql' }
  let healthy = true
  try {
    await db.ping()                     // 1 = SELECT 1
    checks.database = 'ok'
  } catch (err) {
    healthy = false
    checks.database = 'error'
    checks.databaseError = err instanceof Error ? err.message : String(err)
  }
  return NextResponse.json(
    { ok: healthy, status: healthy ? 200 : 503, checks, timestamp: new Date().toISOString() },
    { status: healthy ? 200 : 503 }
  )
}
```

### 5.5 Startup migrations (the "no SSH, no terminal, no manual SQL" trick)

Ship a migrations folder of **idempotent, dual-safe SQL** (every statement
`IF NOT EXISTS` / guarded, never destructive) plus a runner that:

1. Creates a `schema_migrations` tracking table
   (`CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(255) PRIMARY KEY, applied_at DATETIME(3) …)`)
2. Reads `migrations/*.js` sorted alphabetically
3. Applies each un-applied migration **inside a transaction**, recording it in
   the tracking table

Wire it to the framework's startup hook so it runs once per process, before
any request:

```ts
// src/instrumentation.ts (Next.js — runs once per server process)
export async function register() {
  if (process.env.NEXT_RUNTIME !== 'nodejs') return   // skip Edge runtime
  const { db } = await import('./lib/db')
  await db.ping()                                    // fail loudly in logs
  const { runMigrations } = await import('./lib/migrations')
  await runMigrations(db)                            // idempotent
}
```

**Failure policy:** log the error and keep running (return 503 from
`/api/health`), so a broken DB doesn't make Passenger crash-loop the app.

Keep a standalone `scripts/migrate.js` CLI in the archive that bootstraps its
own DB connection (plain `node`, no TypeScript) — useful for manual runs and
status checks if you ever do get terminal access.

### 5.6 Build-phase DB no-op (only if your build imports DB code)

If `next build` (or equivalent) evaluates modules that connect to a database,
the build would fail on a machine with no DB. Detect the build phase and
return a client that throws **only if a query is actually executed**:

```ts
const isBuildPhase =
  process.env.NEXT_PHASE === 'phase-production-build' ||
  process.env.NEXT_BUILD === 'true'

if (isBuildPhase) return createNoOpClient()   // ping/end = no-op, execute throws
```

Pass `NEXT_BUILD=true` during your build command (see §6).

---

## 6. Build & package script

A `scripts/build-and-package.sh` that follows rule 1 + 2 + 7:

```bash
#!/usr/bin/env bash
# scripts/build-and-package.sh — build {your-app} and create the deploy archive.
set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="$PROJECT_DIR/deploy"
BASE_PATH="${BASE_PATH:-}"
ARCHIVE_NAME="{your-app}-$(date +%Y%m%d-%H%M%S).tar.gz"
STAGE_DIR="$PROJECT_DIR/.deploy-stage"

cd "$PROJECT_DIR"

# [1] Build (NEXT_BUILD=true → DB code becomes a no-op during build)
NEXT_BUILD=true NODE_ENV=production BASE_PATH="$BASE_PATH" npx next build

# [2] Stage the runtime files (rule 2: NO node_modules)
rm -rf "$STAGE_DIR"; mkdir -p "$STAGE_DIR"
cp app.js                 "$STAGE_DIR/"        # Passenger entry (§5.1)
cp deploy-package.json    "$STAGE_DIR/package.json"   # runtime deps only (§5.2)
cp next.config.mjs        "$STAGE_DIR/"        # plain-JS config (§5.3)
cp .env.example           "$STAGE_DIR/"        # env var template (§10)
mkdir -p "$STAGE_DIR/logs"; touch "$STAGE_DIR/logs/.gitkeep"
cp -a public              "$STAGE_DIR/public"  # static assets
cp -a migrations          "$STAGE_DIR/migrations"   # idempotent schema (§5.5)
mkdir -p "$STAGE_DIR/scripts"
cp scripts/migrate.js     "$STAGE_DIR/scripts/"     # manual migration CLI

# compiled output, minus build caches:
mkdir -p "$STAGE_DIR/.next"
for item in .next/*; do
  base=$(basename "$item")
  [ "$base" != "cache" ] && [ "$base" != "dev" ] && cp -a "$item" "$STAGE_DIR/.next/"
done
rm -rf "$STAGE_DIR/.next/types" "$STAGE_DIR/.next/diagnostics"

# [3] Archive — tar PRESERVES SYMLINKS by default (rule 7)
cd "$STAGE_DIR"
tar -czf "$DEPLOY_DIR/$ARCHIVE_NAME" \
  app.js package.json next.config.mjs .env.example logs/ .next/ public/ scripts/ migrations/
cd "$PROJECT_DIR"; rm -rf "$STAGE_DIR"

# [4] Verify the archive
BAD_NM=$(tar -tzf "$DEPLOY_DIR/$ARCHIVE_NAME" | grep -E "(^|/)node_modules(/|$)" \
         | grep -vE "^(\./)?\.next/node_modules/" || true)
[ -n "$BAD_NM" ] && { echo "FAIL: real node_modules in archive"; exit 1; }
tar -tzf "$DEPLOY_DIR/$ARCHIVE_NAME" | grep -q "BUILD_ID" || { echo "FAIL: no BUILD_ID"; exit 1; }
echo "OK → $DEPLOY_DIR/$ARCHIVE_NAME"
```

**Final archive manifest** (what the server receives):

```
app.js               Passenger entry
package.json         runtime deps only (NO better-sqlite3 / dev tools / TS)
next.config.mjs      plain-JS config
.env.example         env var template
.next/               compiled app (symlinks intact, caches stripped)
public/              static assets
migrations/          idempotent schema — auto-runs at startup
scripts/migrate.js   optional manual migration CLI
logs/                runtime log dir
```

---

## 7. Create the MySQL database

In cPanel (no SQL typing needed):

1. Open **MySQL Database Wizard**.
2. **Database name**: `cpuser_yourapp` (cPanel prefixes your username).
3. **User**: `cpuser_appuser` + a strong password (avoid `@ : / # ? &` if you
   can — otherwise you'll URL-encode them in the connection string, see §10).
4. **Privileges**: check **ALL PRIVILEGES** for this user on this database.
5. Note the three values — you'll build the connection URL from them:

```
mysql://USER:PASSWORD@localhost:3306/DATABASE
```

On cPanel the DB host is **`localhost`** — the MySQL server runs on the same
machine as Passenger. Never use your public domain or an external IP unless
your host explicitly tells you otherwise.

Create the database **before** first app start, so startup migrations have
somewhere to write.

---

## 8. Upload & extract the archive

1. cPanel → **File Manager**
2. Create/navigate to your app directory (outside `public_html` is safer,
   e.g. `~/apps/{your-app}/` — Passenger serves it; the framework handles
   static files)
3. **Upload** the `.tar.gz`
4. Select it → **Extract** → confirm it created `app.js`, `package.json`,
   `.next/`, `migrations/`, `logs/`, …
5. Delete the `.tar.gz` after extraction to save quota

> If your File Manager extract mangles symlinks (rare, but it happens — you'd
> see errors like "Cannot find module" pointing into `.next/node_modules`),
> extract with the host's "Extract with defaults (preserve symlinks)" option,
> or re-upload and extract via cPanel's built-in tar handling rather than a
> third-party zip.

---

## 9. Create the Node.js app in cPanel

cPanel → **Software → Setup Node.js App → Create Application**:

| Setting | Value | Why |
|---|---|---|
| **Node.js version** | **20** | 18.6+ minimum for modern Next; 20 is the sweet spot |
| **Application mode** | **Production** | sets `NODE_ENV=production` — this is your app's env switch! |
| **Application root** | `apps/{your-app}` (where you extracted) | must contain `app.js` |
| **Application URL** | your (sub)domain | how visitors reach it |
| **Application startup file** | **`app.js`** | the §5.1 entry point |

Click **Create**. Don't start it yet — env vars come first.

---

## 10. Set environment variables

Still in the Node.js App screen, click **Add Variable** for each:

| Variable | Example | Notes |
|---|---|---|
| `NODE_ENV` | `production` | usually already set by app mode — set anyway to be safe |
| `PROD_DATABASE_URL` | `mysql://cpuser_appuser:p%40ss@localhost:3306/cpuser_yourapp` | the §7 connection URL |
| `APP_SECRET` / `NEXTAUTH_SECRET` | output of `openssl rand -hex 32` | token/session salting — generate locally |
| `BASE_PATH` | `/myapp` | only for sub-path deploys; omit for root |
| `MYSQL_POOL_SIZE` | `5` | see §16 before raising |

> **Do NOT create a `.env` file on the server.** cPanel env vars are the
> correct mechanism (Passenger injects them into the process). A `.env` can
> be misread, stale, or world-readable.

**URL-encode special characters in the DB password:**

| char | encode | | char | encode |
|---|---|---|---|---|
| `@` | `%40` | | `#` | `%23` |
| `:` | `%3A` | | `?` | `%3F` |
| `/` | `%2F` | | `&` | `%26` |

Example: password `P@ss:w0rd` → `P%40ss%3Aw0rd`.

**Validate the URL at app startup** (before the driver parses it) so
malformed values fail fast with a human-readable message instead of the
driver's cryptic `TypeError: Invalid URL`. Also reject obvious placeholder
text (`your-mysql-host`, `user:password`).

---

## 11. Run NPM Install

On the Node.js App screen, click **Run NPM Install**.

- This creates a CloudLinux-managed `node_modules` (a nodevenv, often a
  symlink farm) and installs exactly what your runtime `package.json`
  declares.
- It does **not** run your build (there is none — the app is pre-built) and
  must **not** run migrations (they happen at app start, rule 5).
- If it finishes with a Python-looking error
  (`AttributeError: 'NoneType' object has no attribute…`), a native/dev-only
  dependency slipped into your runtime `package.json` — remove it and
  re-deploy (§15).

---

## 12. Start, verify, first-run setup

Click **Start** (or Restart). Then, in order:

**1. Health check** — visit `https://{your-domain}/api/health`:

```json
{"ok":true,"status":200,"checks":{"app":"ok","backend":"mysql","database":"ok"}}
```

- `"database":"ok"` → startup migrations already ran; tables exist.
- `503` → the body includes `databaseError` and a `requestId`; open
  `logs/error.log` in File Manager and search for that ID.

**2. First-run setup** — if your app has an onboarding route (first admin
   creation, license activation), it should be gated so it **only works while
   no admin exists** (check on every request). Visit it, create the admin,
   and it locks itself.

**3. Log in** and click around. If anything is off, `logs/app.log` +
`logs/error.log` in File Manager are your terminal now.

---

## 13. Re-deploys and rollback

**Shipping version N+1:**

1. Build a new archive on your machine (same script — new timestamp in the name).
2. (Optional but smart) On the server, rename the current app dir:
   `apps/{your-app}` → `apps/{your-app}-v{n}` — this is your rollback point.
3. Upload + extract the new archive into a fresh `apps/{your-app}`.
4. Copy any runtime-generated content forward: `public/uploads/`, `logs/`
   (skip if you prefer fresh logs).
5. In the Node.js App screen: **Run NPM Install** → **Restart**.
6. `/api/health` must go green; migrations apply themselves if the new version
   ships new ones.

**Rollback:** rename the old dir back → Restart. Since migrations are
additive-only (never destructive), an older app version still runs fine
against the newer schema — extra columns are simply ignored.

**Versioning tip:** tag your archives
(`{your-app}-v12-20260101-120000.tar.gz`) and keep the last two on the server.

---

## 14. File permissions

Make sure the app can write to the directories it writes to at runtime:

| Directory | Perm | Why |
|---|---|---|
| `logs/` | 755 | log files |
| `public/uploads/` (if any) | 755 | user-uploaded content |
| `.next/cache/` (if shipped) | 755 | framework cache |

Files 644, dirs 755. On shared hosts, the app runs as *your* cPanel user, so
ownership is usually already correct. If you see `EACCES` in logs, fix with
File Manager → Permissions.

---

## 15. Troubleshooting table

| Symptom | Cause → Fix |
|---|---|
| `Cannot find module 'next'` | Deps not installed → click **Run NPM Install**; they live in the nodevenv, not the app root |
| `Cannot find .next/BUILD_ID` | Archive built wrong / extract incomplete → rebuild with the §6 script, re-extract |
| `TypeError: Invalid URL` (mysql2) | `PROD_DATABASE_URL` malformed → check prefix `mysql://`, URL-encode password (§10) |
| `ECONNREFUSED localhost:3306` | MySQL down or DB doesn't exist → cPanel → MySQL Databases, re-check wizard |
| `ER_ACCESS_DENIED_ERROR` | Wrong DB user/pass → use the DB user from §7 with ALL PRIVILEGES, not your cPanel login |
| Tables never created | Migrations run at **Start**, not at install → check `logs/app.log` for "Migrations complete" |
| `/api/health` 503 | DB unreachable → response body has `requestId` + `databaseError`; grep `logs/error.log` |
| 500s with `ER_TOO_MANY_USER_CONNECTIONS` | Pool too big for shared hosting → set `MYSQL_POOL_SIZE=5` (§16), add retry-with-backoff |
| Install dies with CloudLinux `AttributeError: 'NoneType'` | A native/dev-only dep is in the runtime `package.json` → strip it (§5.2), re-deploy |
| App boots on the wrong port | Passenger sets `PORT` itself; only override it outside Passenger |
| 404 on all pages under a sub-path | Forgot `BASE_PATH` at build AND at runtime → rebuild with `BASE_PATH=/myapp` and set the env var |
| Infinite crash-restart loop | App exits on startup errors → switch to the "log and keep running, 503 at health" policy (§5.5) |
| PWABuilder fails with a JSON error (`Unexpected token …`) | The hosting's **Imunify360 bot-protection** blocks PWABuilder's server-side fetch of `/manifest.webmanifest` (returns 403 `Access denied by Imunify360…`) → cPanel → Security → Imunify360: disable **Bot Protection** for the domain (or whitelist the URLs) and retry the packaging |
| Manifest served as `application/octet-stream` | Old builds shipped `public/manifest.webmanifest` (static MIME unknown to LiteSpeed) → since task 43 the manifest is the `src/app/manifest.ts` route, always served as `application/manifest+json`; **task 52 deleted the leftover static file** (it shadowed the route and even caused a dev-mode 500 "conflicting public file and page file") — redeploy the current tar |
| Android app (PWABuilder TWA) shows the browser address bar / ✕ close button | Digital Asset Links verification failed. Checklist: **(1)** open `https://drtest.drjeilan.net/.well-known/assetlinks.json` — if you see `[]` the file is published but EMPTY: add the app's **package name + SHA-256 signing-certificate fingerprint** in the admin panel (روابط التطبيق — the status card at the top shows the live file state; alternative: `ASSETLINKS_PACKAGE_NAME` + `ASSETLINKS_SHA256` env vars on the client app while the table is empty); **(2)** if the URL returns an HTML "Please wait while your request is being verified…" page (Imunify360 challenge), Google's verifier can never read it → cPanel → Security → Imunify360: disable **Bot Protection** for the domain or whitelist `/.well-known/*`; **(3)** the fingerprint must match the keystore that signed the APK (`keytool -list -v -keystore my.keystore`) — or, for a Play-published app, the **App signing** certificate from Play Console; **(4)** reopen the app — verification is cached, changing the file takes effect on next launch |
| Book reader shows «تعذّر تحميل الكتاب» although the download works | The old service worker (dj-v4) cached an Imunify360 **HTML challenge page** as the PDF response (cache-first) — redeploy the current build: SW **dj-v5** deletes every poisoned cache on activate and the reader now fetches + validates the file itself (`%PDF` magic, no HTML) before rendering; visitors need to reopen the site/app once so the SW updates |

---

## 16. Performance tuning

**App side (the part you control):**

- **Pool size 5 per process** on shared hosting. The MySQL *user* connection
  limit is shared by every Passenger process + phpMyAdmin + cron jobs. A
  default 50-per-process blows past it instantly. Dedicated server? Raise
  `MYSQL_POOL_SIZE` (keep ≤ `max_connections / processes`).
- `waitForConnections: true`, `queueLimit: 0` — queue instead of erroring.
- `enableKeepAlive: true` + 60s `idleTimeout` — no cold reconnects.
- Retry-on-deadlock/connection-loss with **exponential backoff** (50ms →
  100ms → 200ms; connection-exhaustion gets 500ms → 1s → 2s).
- Chunked multi-row `INSERT`s (500–1000 rows per statement) for bulk loads.

**MySQL side (ask your host / `my.cnf` if self-managed):**

```ini
[mysqld]
innodb_flush_log_at_trx_commit = 2     # throughput over strict durability
innodb_buffer_pool_size        = 4G    # ~60-70% of RAM
innodb_log_file_size           = 1G
innodb_flush_method            = O_DIRECT
max_connections                = 200
innodb_lock_wait_timeout       = 30
```

---

## 17. Security checklist

- [ ] Secrets only as cPanel env vars — never a committed `.env`, never in the archive
- [ ] The archive ships `.env.example` (template), never real credentials
- [ ] First-run setup route self-locks after the first admin exists
- [ ] DB user has ALL PRIVILEGES on **one database only** — not global GRANT
- [ ] `multipleStatements: false` on the MySQL driver (SQL-injection safety)
- [ ] App dir outside `public_html` (Passenger serves it; no direct source download)
- [ ] Uploads directory rejects executable file types; served with safe content types
- [ ] Health endpoint leaks no secrets — DB URLs in it are password-redacted
- [ ] Logs redact passwords/secrets; log files not web-readable
- [ ] HTTPS (AutoSSL/Let's Encrypt in cPanel) before going live

---

## 18. Master checklist (print me)

```
BUILD MACHINE
[ ] 1. Tests green, lint clean
[ ] 2. NEXT_BUILD=true NODE_ENV=production npx next build
[ ] 3. bash scripts/build-and-package.sh (or BASE_PATH=/sub …)
[ ] 4. Archive verified: no node_modules, BUILD_ID present, symlinks preserved

HOST — cPanel
[ ] 5. MySQL Wizard: db + user + ALL PRIVILEGES        → note URL parts
[ ] 6. File Manager: upload .tar.gz → extract to apps/{your-app}
[ ] 7. Setup Node.js App: Node 20 · Production · app.js
[ ] 8. Env vars: PROD_DATABASE_URL (URL-encoded!) · NODE_ENV=production
       · APP_SECRET (openssl rand -hex 32) · BASE_PATH? · MYSQL_POOL_SIZE=5
[ ] 9. Run NPM Install                                  → no CloudLinux errors
[ ] 10. Start                                            → migrations auto-run
[ ] 11. /api/health → {"ok":true, backend:"mysql"}      → 200
[ ] 12. First-run setup → create admin → route self-locks
[ ] 13. Log in, click through, check logs/app.log
[ ] 14. HTTPS on, old .tar.gz deleted
```

---

## 19. Appendix: adapting to other frameworks

The 7 rules don't change — only the entry file and build output do.

### Express / Fastify / Koa / NestJS (server framework)

```js
// app.js — same skeleton, different handler
const app = require('./dist/server')   // or require('./server') if plain JS
const { createServer } = require('http')
createServer(app).listen(process.env.PORT || 3000, '0.0.0.0', () =>
  console.log(`App ready (NODE_ENV=${process.env.NODE_ENV})`)
)
```

Build step: `tsc` / `babel` → `dist/`. Archive ships `dist/` instead of
`.next/`. Migrations run the same way — just call them from the entry file
before `listen()` instead of a framework hook.

### Nuxt 3/4

```js
// app.js
const { Nuxt } = require('nuxt')
const config = require('./nuxt.config.js')      // plain JS config (rule 4)
const nuxt = new Nuxt({ ...config, dev: false })
nuxt.ready().then(() => {
  const { createServer } = require('http')
  createServer(nuxt.render).listen(process.env.PORT || 3000)
})
```

### SvelteKit (`adapter-node`) / Remix

`adapter-node` emits `build/index.js` that expects `node build`. Your
`app.js` just does `process.env.PORT …; require('./build/index.js')` — or
set the cPanel startup file directly to `build/index.js` if the adapter
respects `PORT` (it does).

### Vite SPA (no SSR)

No server needed at all — you don't need Node on the host. Build `dist/`,
upload into `public_html/`, done. (This guide doesn't apply; use it only if
you later add an API layer.)

### Static export Next.js

`next build && next export` → upload `out/` to `public_html/`. Same as Vite —
no Passenger app, no Node, no this guide.

---

*End of guide — adapt `{placeholders}`, keep the 7 rules, and any Node web app
deploys to cPanel this way. Pair it with a `DATABASE.md` describing your app's
data layer (SQLite dev / MySQL prod or whatever you choose) and a
`README.md` quickstart, and the project is self-documenting.*
