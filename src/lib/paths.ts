import path from "path";

/**
 * Returns the root data directory for all persistent storage.
 * In Electron, STORYTIME_DATA_DIR is set to app.getPath('userData').
 * In normal dev/server mode, falls back to process.cwd().
 */
export function getDataDir(): string {
  return process.env.STORYTIME_DATA_DIR || process.cwd();
}

export function getDbPath(): string {
  return path.join(getDataDir(), "storytime.db");
}

export function getUploadsDir(): string {
  return path.join(getDataDir(), "uploads", "photos");
}

export function getGeneratedDir(): string {
  return path.join(getDataDir(), "generated");
}

export function resolveDataPath(...segments: string[]): string {
  return path.join(getDataDir(), ...segments);
}
