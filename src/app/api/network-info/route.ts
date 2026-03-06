import { NextResponse } from "next/server";
import os from "os";

export async function GET() {
  const interfaces = os.networkInterfaces();
  let lanIp = "localhost";

  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === "IPv4" && !iface.internal) {
        lanIp = iface.address;
        break;
      }
    }
    if (lanIp !== "localhost") break;
  }

  const port = process.env.PORT || "3000";

  return NextResponse.json({ ip: lanIp, port });
}
