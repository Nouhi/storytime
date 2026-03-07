import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import * as schema from "./schema";
import { getDbPath } from "../paths";

const dbPath = getDbPath();

// Use a singleton pattern to avoid multiple connections during build
const globalForDb = globalThis as unknown as {
  sqlite: Database.Database | undefined;
};

if (!globalForDb.sqlite) {
  globalForDb.sqlite = new Database(dbPath);
  globalForDb.sqlite.pragma("journal_mode = WAL");
  globalForDb.sqlite.pragma("busy_timeout = 5000");
}

export const db = drizzle(globalForDb.sqlite, { schema });
