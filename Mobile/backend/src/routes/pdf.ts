import { Router } from "express";
import { getSession } from "../lib/generation-manager.js";
import { generatePdf } from "../lib/pdf/pdf-generator.js";
import { db } from "../lib/db/index.js";
import { storyHistory } from "../lib/db/schema.js";
import { eq } from "drizzle-orm";
import { loadStoryData } from "../lib/storage.js";
import { getGeneratedDir } from "../lib/paths.js";
import path from "path";
import fs from "fs/promises";

// Router for fresh story PDF: GET /api/generate/:storyId/pdf
export const generatePdfRouter = Router();

generatePdfRouter.get("/:storyId/pdf", async (req, res) => {
  const { storyId } = req.params;
  const session = getSession(storyId);

  // Get story pages from session or fall back to disk
  let storyPages = session?.storyPages;
  if (!storyPages) {
    const diskPages = await loadStoryData(storyId);
    if (diskPages) {
      storyPages = diskPages.map((p) => ({
        page: p.page,
        text: p.text,
        imageDescription: p.imageDescription || "",
      }));
    }
  }

  if (!storyPages || storyPages.length === 0) {
    res.status(404).json({ error: "Story not found" });
    return;
  }

  // Load images from disk
  const images = new Map<number, Buffer>();
  for (const page of storyPages) {
    const imgPath = path.join(getGeneratedDir(), storyId, `page-${page.page}.png`);
    try {
      const buffer = await fs.readFile(imgPath);
      images.set(page.page, buffer);
    } catch {
      // Image might not exist if generation failed for this page
    }
  }

  const title = storyPages[0]?.text || "Story";
  const pdfBuffer = await generatePdf(title, storyPages, images);

  const filename = `storytime-${Date.now()}.pdf`;
  res.set("Content-Type", "application/pdf");
  res.set("Content-Disposition", `attachment; filename="${filename}"`);
  res.set("Content-Length", pdfBuffer.length.toString());
  res.send(Buffer.from(pdfBuffer));
});

// Router for historical story PDF: GET /api/story-history/:id/pdf
export const historyPdfRouter = Router();

historyPdfRouter.get("/:id/pdf", async (req, res) => {
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

  // Extract UUID from pdfPath (e.g., "generated/abc-123.epub" → "abc-123")
  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));

  const pages = await loadStoryData(uuid);
  if (!pages) {
    res.status(404).json({ error: "Story data not found" });
    return;
  }

  // Load images from disk
  const images = new Map<number, Buffer>();
  for (const page of pages) {
    const imgPath = path.join(getGeneratedDir(), uuid, `page-${page.page}.png`);
    try {
      const buffer = await fs.readFile(imgPath);
      images.set(page.page, buffer);
    } catch {
      // Image might not exist if generation failed for this page
    }
  }

  const title = story.title;
  const pdfBuffer = await generatePdf(title, pages, images);

  const safeName = title
    .replace(/[^a-zA-Z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .slice(0, 60);
  const filename = `${safeName || "story"}.pdf`;

  res.set("Content-Type", "application/pdf");
  res.set("Content-Disposition", `attachment; filename="${filename}"`);
  res.set("Content-Length", pdfBuffer.length.toString());
  res.send(Buffer.from(pdfBuffer));
});
