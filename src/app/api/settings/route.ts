import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { settings } from "@/lib/db/schema";
import { eq } from "drizzle-orm";

function maskKey(key: string): string {
  if (!key || key.length < 8) return key ? "****" : "";
  return key.slice(0, 4) + "..." + key.slice(-4);
}

export async function GET() {
  const rows = db.select().from(settings).all();
  if (rows.length === 0) {
    db.insert(settings)
      .values({
        id: 1,
        kidName: "",
        readingLevel: "early-reader",
        kidPhotoPath: "",
        anthropicApiKey: "",
        googleAiApiKey: "",
      })
      .run();
    return NextResponse.json({
      id: 1,
      kidName: "",
      readingLevel: "early-reader",
      kidPhotoPath: "",
      anthropicApiKey: "",
      googleAiApiKey: "",
      updatedAt: new Date().toISOString(),
    });
  }

  const row = rows[0];
  return NextResponse.json({
    ...row,
    anthropicApiKey: maskKey(row.anthropicApiKey),
    googleAiApiKey: maskKey(row.googleAiApiKey),
  });
}

export async function PUT(request: Request) {
  const body = await request.json();
  const { kidName, readingLevel, kidPhotoPath, anthropicApiKey, googleAiApiKey } = body;

  const existing = db.select().from(settings).all();

  const update: Record<string, string> = {
    kidName,
    readingLevel,
    updatedAt: new Date().toISOString(),
  };

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
  return NextResponse.json({
    ...updated,
    anthropicApiKey: maskKey(updated.anthropicApiKey),
    googleAiApiKey: maskKey(updated.googleAiApiKey),
  });
}
