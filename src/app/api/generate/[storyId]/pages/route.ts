import { NextResponse } from "next/server";
import fs from "fs/promises";
import path from "path";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ storyId: string }> },
) {
  const { storyId } = await params;
  const url = new URL(request.url);
  const page = url.searchParams.get("page");

  if (!page) {
    return NextResponse.json({ error: "page param required" }, { status: 400 });
  }

  const imagePath = path.join(process.cwd(), "generated", storyId, `page-${page}.png`);

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
