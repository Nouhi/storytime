import { PDFDocument, StandardFonts, rgb } from "pdf-lib";
import type { StoryPage } from "../types.js";

// =============================================================================
// PDF Generator — Simple landscape PDF for children's stories
// =============================================================================

const PAGE_WIDTH = 792; // US Letter landscape width (11in)
const PAGE_HEIGHT = 612; // US Letter landscape height (8.5in)
const MARGIN = 36; // 0.5in margins
const IMG_WIDTH = PAGE_WIDTH - 2 * MARGIN; // 720pt
const IMG_HEIGHT = IMG_WIDTH / 1.5; // 480pt (3:2 aspect ratio)
const TEXT_COLOR = rgb(0.1, 0.1, 0.1);
const SUBTITLE_COLOR = rgb(0.4, 0.4, 0.4);

/**
 * Strip characters that WinAnsi (used by pdf-lib StandardFonts) cannot encode.
 * Keeps printable ASCII, common Latin-1 supplement (accented chars), and
 * replaces common Unicode punctuation with ASCII equivalents.
 */
function sanitizeForPdf(text: string): string {
  return text
    // Replace newlines/tabs with spaces first
    .replace(/[\r\n\t]+/g, " ")
    // Replace smart quotes / curly quotes with straight ones
    .replace(/[\u2018\u2019\u201A]/g, "'")
    .replace(/[\u201C\u201D\u201E]/g, '"')
    // Replace en-dash / em-dash with hyphens
    .replace(/[\u2013\u2014]/g, "-")
    // Replace ellipsis
    .replace(/\u2026/g, "...")
    // Replace bullet
    .replace(/\u2022/g, "-")
    // Replace non-breaking space
    .replace(/\u00A0/g, " ")
    // Remove any remaining characters outside WinAnsi range
    // WinAnsi covers: 0x20-0x7E (basic ASCII printable) and 0xA0-0xFF (Latin-1 Supplement)
    .replace(/[^\x20-\x7E\xA0-\xFF]/g, "")
    // Collapse multiple spaces
    .replace(/\s{2,}/g, " ")
    .trim();
}

/**
 * Wrap text into lines that fit within maxWidth at the given font size.
 */
function wrapText(
  text: string,
  font: { widthOfTextAtSize: (text: string, size: number) => number },
  fontSize: number,
  maxWidth: number,
): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let currentLine = "";

  for (const word of words) {
    const testLine = currentLine ? `${currentLine} ${word}` : word;
    const width = font.widthOfTextAtSize(testLine, fontSize);

    if (width > maxWidth && currentLine) {
      lines.push(currentLine);
      currentLine = word;
    } else {
      currentLine = testLine;
    }
  }

  if (currentLine) {
    lines.push(currentLine);
  }

  return lines;
}

/**
 * Draw centered, wrapped text and return the Y position after the last line.
 */
function drawCenteredText(
  page: ReturnType<PDFDocument["addPage"]>,
  text: string,
  font: { widthOfTextAtSize: (text: string, size: number) => number },
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  pdfFont: any,
  fontSize: number,
  startY: number,
  color: ReturnType<typeof rgb>,
): number {
  const maxWidth = IMG_WIDTH;
  const lines = wrapText(text, font, fontSize, maxWidth);
  const lineHeight = fontSize * 1.5;
  let y = startY;

  for (const line of lines) {
    const lineWidth = font.widthOfTextAtSize(line, fontSize);
    const x = (PAGE_WIDTH - lineWidth) / 2;
    page.drawText(line, {
      x,
      y,
      size: fontSize,
      font: pdfFont,
      color,
    });
    y -= lineHeight;
  }

  return y;
}

/**
 * Detect whether a buffer is PNG or JPEG by checking magic bytes.
 */
function isPng(buffer: Buffer): boolean {
  return buffer.length >= 4 && buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47;
}

export async function generatePdf(
  title: string,
  pages: StoryPage[],
  images: Map<number, Buffer>,
): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  doc.setTitle(sanitizeForPdf(title));
  doc.setCreator("Storytime");

  const font = await doc.embedFont(StandardFonts.TimesRoman);
  const fontBold = await doc.embedFont(StandardFonts.TimesRomanBold);
  const fontItalic = await doc.embedFont(StandardFonts.TimesRomanItalic);
  const fontBoldItalic = await doc.embedFont(StandardFonts.TimesRomanBoldItalic);

  for (const storyPage of pages) {
    const page = doc.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
    const isCover = storyPage.page === 1;
    const isEnd = storyPage.page === pages.length;

    // Embed image if available
    const imgBuf = images.get(storyPage.page);
    if (imgBuf) {
      try {
        const pdfImage = isPng(imgBuf)
          ? await doc.embedPng(imgBuf)
          : await doc.embedJpg(imgBuf);

        const scaled = pdfImage.scaleToFit(IMG_WIDTH, IMG_HEIGHT);
        const imgX = (PAGE_WIDTH - scaled.width) / 2;
        const imgY = PAGE_HEIGHT - MARGIN - scaled.height;

        page.drawImage(pdfImage, {
          x: imgX,
          y: imgY,
          width: scaled.width,
          height: scaled.height,
        });
      } catch (err) {
        console.error(`Failed to embed image for page ${storyPage.page}:`, err);
      }
    }

    // Text starts below the image area
    const textStartY = PAGE_HEIGHT - MARGIN - IMG_HEIGHT - 24;

    const safeText = sanitizeForPdf(storyPage.text);

    if (isCover) {
      // Parse title / subtitle
      const bedtimeMatch = safeText.match(/^(.+?)\s*[-:,]?\s*(a bedtime story.*)$/i);
      const separatorMatch = !bedtimeMatch && safeText.match(/^(.+?)\s*[-:]\s*(.+)$/);
      const match = bedtimeMatch || separatorMatch;

      if (match) {
        const coverTitle = match[1].trim();
        const coverSubtitle = match[2].trim();
        const afterTitle = drawCenteredText(page, coverTitle, fontBold, fontBold, 22, textStartY, TEXT_COLOR);
        drawCenteredText(page, coverSubtitle, fontItalic, fontItalic, 13, afterTitle - 4, SUBTITLE_COLOR);
      } else {
        drawCenteredText(page, safeText, fontBold, fontBold, 22, textStartY, TEXT_COLOR);
      }
    } else if (isEnd) {
      drawCenteredText(page, safeText, fontBoldItalic, fontBoldItalic, 15, textStartY, TEXT_COLOR);
    } else {
      drawCenteredText(page, safeText, font, font, 13, textStartY, TEXT_COLOR);
    }
  }

  return doc.save();
}
