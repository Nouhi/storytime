import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import * as schema from "./schema.js";
import { getDbPath, getDataDir } from "../paths.js";
import fs from "fs";

// Ensure data directory exists
const dataDir = getDataDir();
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = getDbPath();
const sqlite = new Database(dbPath);
sqlite.pragma("journal_mode = WAL");
sqlite.pragma("busy_timeout = 5000");

export const db = drizzle(sqlite, { schema });

// Run lightweight migrations for schema changes
function runMigrations() {
  // Add description column to family_members if it doesn't exist
  const columns = sqlite.pragma("table_info(family_members)") as { name: string }[];
  const hasDescription = columns.some((c) => c.name === "description");
  if (!hasDescription && columns.length > 0) {
    sqlite.exec("ALTER TABLE family_members ADD COLUMN description TEXT");
    console.log("Migration: added 'description' column to family_members");
  }
}

// Initialize tables if they don't exist
export function initializeDatabase() {
  sqlite.exec(`
    CREATE TABLE IF NOT EXISTS settings (
      id INTEGER PRIMARY KEY DEFAULT 1 NOT NULL,
      kid_name TEXT DEFAULT '' NOT NULL,
      reading_level TEXT DEFAULT 'early-reader' NOT NULL,
      kid_gender TEXT DEFAULT '' NOT NULL,
      kid_photo_path TEXT DEFAULT '' NOT NULL,
      anthropic_api_key TEXT DEFAULT '' NOT NULL,
      google_ai_api_key TEXT DEFAULT '' NOT NULL,
      updated_at TEXT DEFAULT '' NOT NULL
    );

    CREATE TABLE IF NOT EXISTS family_members (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      name TEXT NOT NULL,
      role TEXT NOT NULL,
      photo_path TEXT,
      description TEXT
    );

    CREATE TABLE IF NOT EXISTS story_history (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      title TEXT NOT NULL,
      prompt TEXT NOT NULL,
      created_at TEXT NOT NULL,
      claude_input_tokens INTEGER DEFAULT 0 NOT NULL,
      claude_output_tokens INTEGER DEFAULT 0 NOT NULL,
      gemini_image_count INTEGER DEFAULT 0 NOT NULL,
      claude_cost REAL DEFAULT 0 NOT NULL,
      gemini_cost REAL DEFAULT 0 NOT NULL,
      total_cost REAL DEFAULT 0 NOT NULL,
      pdf_path TEXT DEFAULT '' NOT NULL
    );
  `);

  // Run migrations for existing databases
  runMigrations();
}
