import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { storyHistory } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import fs from "fs/promises";
import path from "path";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const storyId = parseInt(id, 10);

  if (isNaN(storyId)) {
    return NextResponse.json({ error: "Invalid ID" }, { status: 400 });
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, storyId))
    .get();

  if (!story || !story.pdfPath) {
    return NextResponse.json({ error: "File not found" }, { status: 404 });
  }

  const fullPath = path.join(process.cwd(), story.pdfPath);
  const ext = path.extname(story.pdfPath).toLowerCase();
  const isEpub = ext === ".epub";

  try {
    const buffer = await fs.readFile(fullPath);
    const safeName = story.title
      .replace(/[^a-zA-Z0-9\s-]/g, "")
      .replace(/\s+/g, "-")
      .slice(0, 60);

    const url = new URL(request.url);
    const inline = url.searchParams.get("inline") === "1";
    const filename = `${safeName || "story"}.${isEpub ? "epub" : "pdf"}`;
    const disposition = inline
      ? `inline; filename="${filename}"`
      : `attachment; filename="${filename}"`;

    return new NextResponse(buffer, {
      headers: {
        "Content-Type": isEpub ? "application/epub+zip" : "application/pdf",
        "Content-Disposition": disposition,
        "Content-Length": buffer.length.toString(),
      },
    });
  } catch {
    return NextResponse.json({ error: "File not found on disk" }, { status: 404 });
  }
}
