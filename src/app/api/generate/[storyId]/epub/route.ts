import { NextResponse } from "next/server";
import { getSession } from "@/lib/generation-manager";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ storyId: string }> },
) {
  const { storyId } = await params;
  const session = getSession(storyId);

  if (!session || !session.epubBuffer) {
    return NextResponse.json({ error: "EPUB not found" }, { status: 404 });
  }

  const url = new URL(request.url);
  const inline = url.searchParams.get("inline") === "1";
  const filename = `storytime-${Date.now()}.epub`;
  const disposition = inline
    ? `inline; filename="${filename}"`
    : `attachment; filename="${filename}"`;

  return new NextResponse(Buffer.from(session.epubBuffer), {
    headers: {
      "Content-Type": "application/epub+zip",
      "Content-Disposition": disposition,
    },
  });
}
