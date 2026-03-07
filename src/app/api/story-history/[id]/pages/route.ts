import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { storyHistory } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import fs from "fs/promises";
import path from "path";
import { getGeneratedDir } from "@/lib/paths";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const storyId = parseInt(id, 10);
  const url = new URL(request.url);
  const page = url.searchParams.get("page");

  if (isNaN(storyId) || !page) {
    return NextResponse.json({ error: "Invalid params" }, { status: 400 });
  }

  const story = db
    .select()
    .from(storyHistory)
    .where(eq(storyHistory.id, storyId))
    .get();

  if (!story || !story.pdfPath) {
    return NextResponse.json({ error: "Story not found" }, { status: 404 });
  }

  // Extract uuid from path like "generated/{uuid}.epub"
  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));
  const imagePath = path.join(getGeneratedDir(), uuid, `page-${page}.png`);

  try {
    const buffer = await fs.readFile(imagePath);
    return new NextResponse(buffer, {
      headers: {
        "Content-Type": "image/png",
        "Cache-Control": "public, max-age=31536000, immutable",
      },
    });
  } catch {
    return NextResponse.json({ error: "Image not found" }, { status: 404 });
  }
}
