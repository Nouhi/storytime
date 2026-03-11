import { Router } from "express";
import fs from "fs/promises";
import path from "path";
import { getSession } from "../lib/generation-manager.js";
import { getGeneratedDir } from "../lib/paths.js";

const router = Router();

router.get("/:storyId/epub", async (req, res) => {
  const { storyId } = req.params;
  const session = getSession(storyId);

  let epubData: Buffer | null = null;

  // Try in-memory session first
  if (session?.epubBuffer) {
    epubData = Buffer.from(session.epubBuffer);
  } else {
    // Fall back to reading from disk
    try {
      const epubPath = path.join(getGeneratedDir(), `${storyId}.epub`);
      epubData = await fs.readFile(epubPath);
    } catch {
      // File not found on disk either
    }
  }

  if (!epubData) {
    res.status(404).json({ error: "EPUB not found" });
    return;
  }

  const inline = req.query.inline === "1";
  const filename = `storytime-${Date.now()}.epub`;
  const disposition = inline
    ? `inline; filename="${filename}"`
    : `attachment; filename="${filename}"`;

  res.set("Content-Type", "application/epub+zip");
  res.set("Content-Disposition", disposition);
  res.send(epubData);
});

export default router;
