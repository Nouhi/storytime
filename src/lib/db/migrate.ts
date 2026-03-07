import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { getDbPath } from "../paths";
import fs from "fs";
import path from "path";

/**
 * Run Drizzle migrations programmatically.
 * Called on Electron app startup before the Next.js server boots.
 */
export function runMigrations(migrationsFolder: string): void {
  const dbPath = getDbPath();

  // Ensure the directory for the DB exists
  const dbDir = path.dirname(dbPath);
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
  }

  const sqlite = new Database(dbPath);
  sqlite.pragma("journal_mode = WAL");
  sqlite.pragma("busy_timeout = 5000");

  const db = drizzle(sqlite);
  migrate(db, { migrationsFolder });
  sqlite.close();
}
