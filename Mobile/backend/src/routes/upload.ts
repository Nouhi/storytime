import { Router } from "express";
import multer from "multer";
import { db } from "../lib/db/index.js";
import { familyMembers, settings } from "../lib/db/schema.js";
import { eq } from "drizzle-orm";
import { savePhoto, deletePhoto } from "../lib/storage.js";

const router = Router();
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });

router.post("/", upload.single("file"), async (req, res) => {
  const file = req.file;
  const memberId = req.body.memberId as string | undefined;
  const uploadType = req.body.type as string | undefined;

  if (!file) {
    res.status(400).json({ error: "File is required" });
    return;
  }

  if (uploadType === "kid") {
    const settingsRow = db.select().from(settings).get();
    if (settingsRow?.kidPhotoPath) {
      await deletePhoto(settingsRow.kidPhotoPath);
    }

    const photoPath = await savePhoto(0, file.buffer, file.mimetype);

    db.update(settings)
      .set({ kidPhotoPath: photoPath })
      .where(eq(settings.id, 1))
      .run();

    res.json({ photoPath });
    return;
  }

  if (!memberId) {
    res.status(400).json({ error: "memberId is required" });
    return;
  }

  const member = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, Number(memberId)))
    .get();

  if (member?.photoPath) {
    await deletePhoto(member.photoPath);
  }

  const photoPath = await savePhoto(Number(memberId), file.buffer, file.mimetype);

  db.update(familyMembers)
    .set({ photoPath })
    .where(eq(familyMembers.id, Number(memberId)))
    .run();

  res.json({ photoPath });
});

export default router;
