import path from "path";

export function getDataDir(): string {
  return process.env.STORYTIME_DATA_DIR || path.join(process.cwd(), "data");
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
