import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { storyHistory } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import { loadStoryData } from "@/lib/storage";
import { generatePdf } from "@/lib/pdf/pdf-generator";
import fs from "fs/promises";
import path from "path";
import { getGeneratedDir } from "@/lib/paths";

export async function GET(
  _request: Request,
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
    return NextResponse.json({ error: "Story not found" }, { status: 404 });
  }

  // Extract UUID from pdfPath (e.g., "generated/abc-123.epub" → "abc-123")
  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));

  const pages = await loadStoryData(uuid);
  if (!pages) {
    return NextResponse.json({ error: "Story data not found" }, { status: 404 });
  }

  // Load images from disk
  const images = new Map<number, Buffer>();
  for (const page of pages) {
    const imgPath = path.join(getGeneratedDir(), uuid, `page-${page.page}.png`);
    try {
      const buffer = await fs.readFile(imgPath);
      images.set(page.page, buffer);
    } catch {
      // Image might not exist if generation failed for this page
    }
  }

  const title = story.title;
  const pdfBuffer = await generatePdf(title, pages, images);

  const safeName = title
    .replace(/[^a-zA-Z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .slice(0, 60);
  const filename = `${safeName || "story"}.pdf`;

  return new NextResponse(Buffer.from(pdfBuffer), {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": `attachment; filename="${filename}"`,
      "Content-Length": pdfBuffer.length.toString(),
    },
  });
}
