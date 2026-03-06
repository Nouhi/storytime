import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { storyHistory } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import path from "path";
import { loadStoryData } from "@/lib/storage";

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

  const uuid = path.basename(story.pdfPath, path.extname(story.pdfPath));
  const pages = await loadStoryData(uuid);

  if (!pages) {
    return NextResponse.json({ error: "Story data not found" }, { status: 404 });
  }

  return NextResponse.json({ pages, title: story.title });
}
