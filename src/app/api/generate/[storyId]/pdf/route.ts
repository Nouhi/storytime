import { NextResponse } from "next/server";
import { getSession } from "@/lib/generation-manager";
import { generatePdf } from "@/lib/pdf/pdf-generator";
import fs from "fs/promises";
import path from "path";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ storyId: string }> },
) {
  const { storyId } = await params;
  const session = getSession(storyId);

  if (!session || !session.storyPages) {
    return NextResponse.json({ error: "Story not found" }, { status: 404 });
  }

  // Load images from disk
  const images = new Map<number, Buffer>();
  for (const page of session.storyPages) {
    const imgPath = path.join(process.cwd(), "generated", storyId, `page-${page.page}.png`);
    try {
      const buffer = await fs.readFile(imgPath);
      images.set(page.page, buffer);
    } catch {
      // Image might not exist if generation failed for this page
    }
  }

  const title = session.storyPages[0]?.text || "Story";
  const pdfBuffer = await generatePdf(title, session.storyPages, images);

  const filename = `storytime-${Date.now()}.pdf`;
  return new NextResponse(Buffer.from(pdfBuffer), {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": `attachment; filename="${filename}"`,
      "Content-Length": pdfBuffer.length.toString(),
    },
  });
}
