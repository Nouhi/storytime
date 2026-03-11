import { Router } from "express";
import { db } from "../lib/db/index.js";
import { storyHistory } from "../lib/db/schema.js";
import { desc, eq } from "drizzle-orm";
import path from "path";
import fs from "fs/promises";
import { loadStoryData } from "../lib/storage.js";
import { resolveDataPath, getGeneratedDir } from "../lib/paths.js";

const router = Router();

// GET /api/story-history
router.get("/", (_req, res) => {
  const stories = db
    .select()
    .from(storyHistory)
    .orderBy(desc(storyHistory.createdAt))
    .all();
  res.json(stories);
});

// GET /api/story-history/:id/story-data
router.get("/:id/story-data", async (req, res) => {
  const id = parseInt(req.params.id, 10);

  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, id))
    .get();

  if (!story || !story.pdfPath) {
    res.status(404).json({ error: "Story not found" });
    return;
  }

  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));
  const pages = await loadStoryData(uuid);

  if (!pages) {
    res.status(404).json({ error: "Story data not found" });
    return;
  }

  res.json({ pages, title: story.title });
});

// GET /api/story-history/:id/pages
router.get("/:id/pages", async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const page = req.query.page as string;

  if (isNaN(id) || !page) {
    res.status(400).json({ error: "Invalid params" });
    return;
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, id))
    .get();

  if (!story || !story.pdfPath) {
    res.status(404).json({ error: "Story not found" });
    return;
  }

  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));
  const imagePath = path.join(getGeneratedDir(), uuid, `page-${page}.png`);

  try {
    const buffer = await fs.readFile(imagePath);
    res.set("Content-Type", "image/png");
    res.set("Cache-Control", "public, max-age=31536000, immutable");
    res.send(buffer);
  } catch {
    res.status(404).json({ error: "Image not found" });
  }
});

// GET /api/story-history/:id/download
router.get("/:id/download", async (req, res) => {
  const id = parseInt(req.params.id, 10);

  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, id))
    .get();

  if (!story || !story.pdfPath) {
    res.status(404).json({ error: "File not found" });
    return;
  }

  const fullPath = resolveDataPath(story.pdfPath);
  const ext = path.extname(story.pdfPath).toLowerCase();
  const isEpub = ext === ".epub";

  try {
    const buffer = await fs.readFile(fullPath);
    const safeName = story.title
      .replace(/[^a-zA-Z0-9\s-]/g, "")
      .replace(/\s+/g, "-")
      .slice(0, 60);

    const inline = req.query.inline === "1";
    const filename = `${safeName || "story"}.${isEpub ? "epub" : "pdf"}`;
    const disposition = inline
      ? `inline; filename="${filename}"`
      : `attachment; filename="${filename}"`;

    res.set("Content-Type", isEpub ? "application/epub+zip" : "application/pdf");
    res.set("Content-Disposition", disposition);
    res.set("Content-Length", buffer.length.toString());
    res.send(buffer);
  } catch {
    res.status(404).json({ error: "File not found on disk" });
  }
});

// DELETE /api/story-history/:id
router.delete("/:id", async (req, res) => {
  const id = parseInt(req.params.id, 10);

  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, id))
    .get();

  if (!story) {
    res.status(404).json({ error: "Story not found" });
    return;
  }

  // Clean up files if pdfPath exists
  if (story.pdfPath) {
    const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));

    // Delete EPUB/PDF file
    try {
      await fs.unlink(resolveDataPath(story.pdfPath));
    } catch {
      // File may not exist
    }

    // Delete story data JSON
    try {
      await fs.unlink(path.join(getGeneratedDir(), `${uuid}-pages.json`));
    } catch {
      // File may not exist
    }

    // Delete page images directory
    try {
      await fs.rm(path.join(getGeneratedDir(), uuid), { recursive: true, force: true });
    } catch {
      // Directory may not exist
    }
  }

  // Delete from database
  db.delete(storyHistory).where(eq(storyHistory.id, id)).run();
  res.json({ success: true });
});

export default router;
