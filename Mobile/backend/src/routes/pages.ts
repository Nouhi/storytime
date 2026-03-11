import { Router } from "express";
import fs from "fs/promises";
import path from "path";
import { getGeneratedDir } from "../lib/paths.js";

const router = Router();

router.get("/:storyId/pages", async (req, res) => {
  const { storyId } = req.params;
  const page = req.query.page as string;

  if (!page) {
    res.status(400).json({ error: "page param required" });
    return;
  }

  const imagePath = path.join(getGeneratedDir(), storyId, `page-${page}.png`);

  try {
    const buffer = await fs.readFile(imagePath);
    res.set("Content-Type", "image/png");
    res.set("Cache-Control", "public, max-age=31536000, immutable");
    res.send(buffer);
  } catch {
    res.status(404).json({ error: "Image not found" });
  }
});

export default router;
