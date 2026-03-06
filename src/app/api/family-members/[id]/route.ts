import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { familyMembers } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import { deletePhoto } from "@/lib/storage";

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const body = await request.json();
  const { name, role } = body;

  db.update(familyMembers)
    .set({ name, role })
    .where(eq(familyMembers.id, Number(id)))
    .run();

  const updated = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, Number(id)))
    .get();

  return NextResponse.json(updated);
}

export async function DELETE(
  _request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  // Delete associated photo
  const member = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, Number(id)))
    .get();

  if (member?.photoPath) {
    await deletePhoto(member.photoPath);
  }

  db.delete(familyMembers)
    .where(eq(familyMembers.id, Number(id)))
    .run();

  return NextResponse.json({ success: true });
}
