import path from "path";
import fs from "fs/promises";
import sharp from "sharp";
import { getUploadsDir, getGeneratedDir, resolveDataPath } from "./paths";

const UPLOADS_DIR = getUploadsDir();
const GENERATED_DIR = getGeneratedDir();

export async function ensureDir(dir: string) {
  await fs.mkdir(dir, { recursive: true });
}

export async function savePhoto(
  memberId: number,
  buffer: Buffer,
  mimeType: string
): Promise<string> {
  await ensureDir(UPLOADS_DIR);

  const ext = mimeType.includes("png") ? "png" : "jpg";
  const filename = `${memberId}-${Date.now()}.${ext}`;
  const filepath = path.join(UPLOADS_DIR, filename);

  // Resize to max 1024x1024, maintaining aspect ratio
  const processed = await sharp(buffer)
    .resize(1024, 1024, { fit: "inside", withoutEnlargement: true })
    .toBuffer();

  await fs.writeFile(filepath, processed);
  return `uploads/photos/${filename}`;
}

export async function deletePhoto(photoPath: string) {
  const fullPath = resolveDataPath(photoPath);
  try {
    await fs.unlink(fullPath);
  } catch {
    // File may not exist, that's ok
  }
}

export async function readPhotoAsBase64(
  photoPath: string
): Promise<{ data: string; mimeType: string } | null> {
  const fullPath = resolveDataPath(photoPath);
  try {
    const buffer = await fs.readFile(fullPath);
    const ext = path.extname(photoPath).toLowerCase();
    const mimeType = ext === ".png" ? "image/png" : "image/jpeg";
    return {
      data: buffer.toString("base64"),
      mimeType,
    };
  } catch {
    return null;
  }
}

export async function saveGeneratedImage(
  storyId: string,
  pageNumber: number,
  buffer: Buffer
): Promise<string> {
  const dir = path.join(GENERATED_DIR, storyId);
  await ensureDir(dir);
  const filename = `page-${pageNumber}.png`;
  await fs.writeFile(path.join(dir, filename), buffer);
  return path.join(dir, filename);
}

export async function saveGeneratedEpub(
  storyId: string,
  epubBuffer: Uint8Array,
): Promise<string> {
  await ensureDir(GENERATED_DIR);
  const filename = `${storyId}.epub`;
  const filepath = path.join(GENERATED_DIR, filename);
  await fs.writeFile(filepath, epubBuffer);
  return `generated/${filename}`;
}

export async function saveStoryData(
  storyId: string,
  pages: { page: number; text: string; imageDescription: string }[],
): Promise<void> {
  await ensureDir(GENERATED_DIR);
  const filepath = path.join(GENERATED_DIR, `${storyId}-pages.json`);
  await fs.writeFile(filepath, JSON.stringify(pages));
}

export async function loadStoryData(
  storyId: string,
): Promise<{ page: number; text: string; imageDescription: string }[] | null> {
  const filepath = path.join(GENERATED_DIR, `${storyId}-pages.json`);
  try {
    const data = await fs.readFile(filepath, "utf8");
    return JSON.parse(data);
  } catch {
    return null;
  }
}

export async function cleanupGenerated(storyId: string) {
  const dir = path.join(GENERATED_DIR, storyId);
  try {
    await fs.rm(dir, { recursive: true });
  } catch {
    // May not exist
  }
}
