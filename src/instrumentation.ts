import { runMigrations } from "./lib/db/migrate";
import path from "path";
import fs from "fs";

export async function onRequestError() {
  // Required export — no-op
}

export async function register() {
  // Only run migrations on the server side
  if (typeof window !== "undefined") return;

  // Find the drizzle migrations folder
  // In standalone mode, it's relative to the server directory
  const candidates = [
    path.join(process.cwd(), "drizzle"),
    path.join(process.cwd(), "..", "drizzle"),
    path.resolve(__dirname, "..", "drizzle"),
    path.resolve(__dirname, "..", "..", "drizzle"),
  ];

  const migrationsFolder = candidates.find((dir) => fs.existsSync(dir));
  if (!migrationsFolder) {
    console.log("[storytime] No drizzle/ folder found, skipping migrations");
    return;
  }

  try {
    runMigrations(migrationsFolder);
    console.log("[storytime] Migrations complete");
  } catch (err) {
    console.error("[storytime] Migration error:", err);
  }
}
