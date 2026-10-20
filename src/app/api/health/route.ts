import { NextResponse } from "next/server";

/* Health endpoint — the only observability window on a no-SSH cPanel host.
   callmony stores all data locally in the browser (offline-first PWA),
   so there is no database to ping: "app ok" is the whole healthcheck. */

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    ok: true,
    status: 200,
    checks: {
      app: "ok",
      storage: "local (offline-first — data lives in the browser)",
      version: "1.2.0",
    },
    timestamp: new Date().toISOString(),
  });
}
