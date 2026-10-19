import { NextResponse } from "next/server";

/* Version endpoint — the app checks this when online to detect updates
   and trigger a service-worker refresh + reload prompt. */

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    app: "dial-ledger",
    version: "1.0.0",
    buildTime: new Date().toISOString(),
  });
}
