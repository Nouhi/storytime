import { Router } from "express";
import { db } from "../lib/db/index.js";
import { settings } from "../lib/db/schema.js";
import { eq } from "drizzle-orm";

const router = Router();

function maskKey(key: string): string {
  if (!key || key.length < 8) return key ? "****" : "";
  return key.slice(0, 4) + "..." + key.slice(-4);
}

router.get("/", (_req, res) => {
  const rows = db.select().from(settings).all();
  if (rows.length === 0) {
    db.insert(settings)
      .values({
        id: 1,
        kidName: "",
        kidGender: "",
        readingLevel: "early-reader",
        kidPhotoPath: "",
        anthropicApiKey: "",
        googleAiApiKey: "",
      })
      .run();
    res.json({
      id: 1,
      kidName: "",
      kidGender: "",
      readingLevel: "early-reader",
      kidPhotoPath: "",
      anthropicApiKey: "",
      googleAiApiKey: "",
      updatedAt: new Date().toISOString(),
    });
    return;
  }

  const row = rows[0];
  res.json({
    ...row,
    anthropicApiKey: maskKey(row.anthropicApiKey),
    googleAiApiKey: maskKey(row.googleAiApiKey),
  });
});

router.put("/", (req, res) => {
  const body = req.body;
  const { kidName, kidGender, readingLevel, kidPhotoPath, anthropicApiKey, googleAiApiKey } = body;

  const existing = db.select().from(settings).all();

  const update: Record<string, string> = {
    kidName,
    readingLevel,
    updatedAt: new Date().toISOString(),
  };

  if (kidGender !== undefined) {
    update.kidGender = kidGender;
  }

  if (kidPhotoPath !== undefined) {
    update.kidPhotoPath = kidPhotoPath;
  }

  if (anthropicApiKey && !anthropicApiKey.includes("...")) {
    update.anthropicApiKey = anthropicApiKey;
  }
  if (googleAiApiKey && !googleAiApiKey.includes("...")) {
    update.googleAiApiKey = googleAiApiKey;
  }

  if (existing.length === 0) {
    db.insert(settings)
      .values({
        id: 1,
        ...update,
        kidPhotoPath: update.kidPhotoPath || "",
        anthropicApiKey: update.anthropicApiKey || "",
        googleAiApiKey: update.googleAiApiKey || "",
      })
      .run();
  } else {
    db.update(settings).set(update).where(eq(settings.id, 1)).run();
  }

  const updated = db.select().from(settings).get()!;
  res.json({
    ...updated,
    anthropicApiKey: maskKey(updated.anthropicApiKey),
    googleAiApiKey: maskKey(updated.googleAiApiKey),
  });
});

export default router;
