import { Router } from "express";
import { db } from "../lib/db/index.js";
import { familyMembers } from "../lib/db/schema.js";
import { eq } from "drizzle-orm";
import { deletePhoto } from "../lib/storage.js";

const router = Router();

// GET /api/family-members
router.get("/", (_req, res) => {
  const members = db.select().from(familyMembers).all();
  res.json(members);
});

// POST /api/family-members
router.post("/", (req, res) => {
  const { name, role, description } = req.body;

  if (!name?.trim() || !role?.trim()) {
    res.status(400).json({ error: "name and role are required" });
    return;
  }

  const result = db
    .insert(familyMembers)
    .values({
      name: name.trim(),
      role: role.trim(),
      description: description?.trim() || null,
    })
    .returning()
    .get();

  res.status(201).json(result);
});

// PATCH /api/family-members/:id
router.patch("/:id", (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }

  const { name, role, description } = req.body;
  const update: Record<string, string | null> = {};
  if (name !== undefined) update.name = name.trim();
  if (role !== undefined) update.role = role.trim();
  if (description !== undefined) update.description = description?.trim() || null;

  if (Object.keys(update).length === 0) {
    res.status(400).json({ error: "Nothing to update" });
    return;
  }

  db.update(familyMembers).set(update).where(eq(familyMembers.id, id)).run();

  const updated = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, id))
    .get();

  if (!updated) {
    res.status(404).json({ error: "Member not found" });
    return;
  }

  res.json(updated);
});

// DELETE /api/family-members/:id
router.delete("/:id", async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }

  const member = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, id))
    .get();

  if (!member) {
    res.status(404).json({ error: "Member not found" });
    return;
  }

  // Clean up photo if exists
  if (member.photoPath) {
    await deletePhoto(member.photoPath);
  }

  db.delete(familyMembers).where(eq(familyMembers.id, id)).run();
  res.json({ success: true });
});

export default router;
