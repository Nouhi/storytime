import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { familyMembers, settings } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import { savePhoto, deletePhoto } from "@/lib/storage";

export async function POST(request: Request) {
  const formData = await request.formData();
  const file = formData.get("file") as File | null;
  const memberId = formData.get("memberId") as string | null;
  const uploadType = formData.get("type") as string | null; // "kid" or null for family member

  if (!file) {
    return NextResponse.json({ error: "File is required" }, { status: 400 });
  }

  const buffer = Buffer.from(await file.arrayBuffer());

  if (uploadType === "kid") {
    // Upload kid's main character photo
    const settingsRow = db.select().from(settings).get();
    if (settingsRow?.kidPhotoPath) {
      await deletePhoto(settingsRow.kidPhotoPath);
    }

    // Use memberId=0 convention for kid photo
    const photoPath = await savePhoto(0, buffer, file.type);

    db.update(settings)
      .set({ kidPhotoPath: photoPath })
      .where(eq(settings.id, 1))
      .run();

    return NextResponse.json({ photoPath });
  }

  // Family member photo upload
  if (!memberId) {
    return NextResponse.json({ error: "memberId is required" }, { status: 400 });
  }

  const member = db
    .select()
    .from(familyMembers)
    .where(eq(familyMembers.id, Number(memberId)))
    .get();

  if (member?.photoPath) {
    await deletePhoto(member.photoPath);
  }

  const photoPath = await savePhoto(Number(memberId), buffer, file.type);

  db.update(familyMembers)
    .set({ photoPath })
    .where(eq(familyMembers.id, Number(memberId)))
    .run();

  return NextResponse.json({ photoPath });
}
