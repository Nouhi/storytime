import { Router } from "express";
import path from "path";
import fs from "fs/promises";
import { getUploadsDir } from "../lib/paths.js";

const router = Router();

router.get("/:filename", async (req, res) => {
  const filePath = path.join(getUploadsDir(), req.params.filename);

  try {
    const buffer = await fs.readFile(filePath);
    const ext = path.extname(filePath).toLowerCase();
    const contentType = ext === ".png" ? "image/png" : "image/jpeg";

    res.set("Content-Type", contentType);
    res.set("Cache-Control", "public, max-age=3600");
    res.send(buffer);
  } catch {
    res.status(404).json({ error: "Not found" });
  }
});

export default router;
