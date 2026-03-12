import { PDFDocument, StandardFonts, rgb, type PDFPage, type PDFFont, type PDFImage } from "pdf-lib";
import type { StoryPage } from "../types.js";

// =============================================================================
// Booklet PDF Generator — Saddle-stitch imposition for print-ready mini books
// =============================================================================

// Physical sheet: US Letter landscape
const SHEET_WIDTH = 792; // 11 inches
const SHEET_HEIGHT = 612; // 8.5 inches

// Each mini page is half the sheet
const MINI_PAGE_WIDTH = SHEET_WIDTH / 2; // 396pt = 5.5in
const MINI_PAGE_HEIGHT = SHEET_HEIGHT; // 612pt = 8.5in

// Margins within each mini page
const MARGIN_X = 18; // 0.25in horizontal
const MARGIN_TOP = 18; // 0.25in top
const MARGIN_BOTTOM = 28; // bottom for page number

// Content area within mini page
const CONTENT_WIDTH = MINI_PAGE_WIDTH - 2 * MARGIN_X; // 360pt
const IMG_MAX_HEIGHT = MINI_PAGE_HEIGHT * 0.55; // ~336pt
const IMG_MAX_WIDTH = CONTENT_WIDTH;

// Colors
const TEXT_COLOR = rgb(0.1, 0.1, 0.1);
const SUBTITLE_COLOR = rgb(0.4, 0.4, 0.4);
const FOLD_LINE_COLOR = rgb(0.75, 0.75, 0.75);
const CROP_MARK_COLOR = rgb(0.6, 0.6, 0.6);

// Fonts bundle type
interface Fonts {
  regular: PDFFont;
  bold: PDFFont;
  italic: PDFFont;
  boldItalic: PDFFont;
}

// =============================================================================
// Utility functions (duplicated from pdf-generator.ts — small pure functions)
// =============================================================================

function sanitizeForPdf(text: string): string {
  return text
    .replace(/[\r\n\t]+/g, " ")
    .replace(/[\u2018\u2019\u201A]/g, "'")
    .replace(/[\u201C\u201D\u201E]/g, '"')
    .replace(/[\u2013\u2014]/g, "-")
    .replace(/\u2026/g, "...")
    .replace(/\u2022/g, "-")
    .replace(/\u00A0/g, " ")
    .replace(/[^\x20-\x7E\xA0-\xFF]/g, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

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
  if (currentLine) lines.push(currentLine);
  return lines;
}

function isPng(buffer: Buffer): boolean {
  return buffer.length >= 4 && buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47;
}

// =============================================================================
// Imposition logic
// =============================================================================

/**
 * Pad story pages to a multiple of 4 (required for saddle-stitch binding).
 * Blank positions are represented as null.
 */
function padToMultipleOf4(pages: StoryPage[]): (StoryPage | null)[] {
  const result: (StoryPage | null)[] = [...pages];
  while (result.length % 4 !== 0) {
    result.push(null);
  }
  return result;
}

/**
 * Compute saddle-stitch imposition order.
 * Returns pairs of [leftPageIndex, rightPageIndex] for each physical half-sheet
 * (front and back of each sheet, in print order).
 *
 * For N pages across N/4 sheets:
 *   Sheet i front: [N-1-2i, 2i]         (0-indexed)
 *   Sheet i back:  [2i+1,   N-2-2i]     (0-indexed)
 */
function computeImpositionOrder(totalPages: number): Array<[number, number]> {
  const sheets = totalPages / 4;
  const pairs: Array<[number, number]> = [];

  for (let i = 0; i < sheets; i++) {
    // Front of sheet: last pages on left, first pages on right
    pairs.push([totalPages - 1 - 2 * i, 2 * i]);
    // Back of sheet: first pages on left, last pages on right
    pairs.push([2 * i + 1, totalPages - 2 - 2 * i]);
  }

  return pairs;
}

// =============================================================================
// Drawing functions
// =============================================================================

/**
 * Draw centered, wrapped text within a mini-page region.
 * Returns the Y position after the last line.
 */
function drawCenteredTextInRegion(
  page: PDFPage,
  text: string,
  font: PDFFont,
  fontSize: number,
  startY: number,
  xOffset: number,
  maxWidth: number,
  color: ReturnType<typeof rgb>,
): number {
  const lines = wrapText(text, font, fontSize, maxWidth);
  const lineHeight = fontSize * 1.5;
  let y = startY;

  for (const line of lines) {
    const lineWidth = font.widthOfTextAtSize(line, fontSize);
    const x = xOffset + (MINI_PAGE_WIDTH - lineWidth) / 2;
    page.drawText(line, { x, y, size: fontSize, font, color });
    y -= lineHeight;
  }

  return y;
}

/**
 * Draw a single story page within one half of a physical sheet.
 */
function drawMiniPage(
  sheet: PDFPage,
  xOffset: number,
  storyPage: StoryPage | null,
  embeddedImage: PDFImage | undefined,
  fonts: Fonts,
  isCover: boolean,
  isEnd: boolean,
  displayPageNum: number | null,
): void {
  if (!storyPage) return; // blank page — leave empty

  let currentY = SHEET_HEIGHT - MARGIN_TOP;

  // Draw image at top if available
  if (embeddedImage) {
    const scaled = embeddedImage.scaleToFit(IMG_MAX_WIDTH, IMG_MAX_HEIGHT);
    const imgX = xOffset + (MINI_PAGE_WIDTH - scaled.width) / 2;
    const imgY = currentY - scaled.height;
    sheet.drawImage(embeddedImage, {
      x: imgX,
      y: imgY,
      width: scaled.width,
      height: scaled.height,
    });
    currentY = imgY - 12; // gap below image
  }

  // Draw text
  const safeText = sanitizeForPdf(storyPage.text);

  if (isCover) {
    // Parse title / subtitle (same logic as pdf-generator)
    const bedtimeMatch = safeText.match(/^(.+?)\s*[-:,]?\s*(a bedtime story.*)$/i);
    const separatorMatch = !bedtimeMatch && safeText.match(/^(.+?)\s*[-:]\s*(.+)$/);
    const match = bedtimeMatch || separatorMatch;

    if (match) {
      const afterTitle = drawCenteredTextInRegion(
        sheet, match[1].trim(), fonts.bold, 16, currentY, xOffset, CONTENT_WIDTH, TEXT_COLOR,
      );
      drawCenteredTextInRegion(
        sheet, match[2].trim(), fonts.italic, 10, afterTitle - 4, xOffset, CONTENT_WIDTH, SUBTITLE_COLOR,
      );
    } else {
      drawCenteredTextInRegion(sheet, safeText, fonts.bold, 16, currentY, xOffset, CONTENT_WIDTH, TEXT_COLOR);
    }
  } else if (isEnd) {
    drawCenteredTextInRegion(sheet, safeText, fonts.boldItalic, 12, currentY, xOffset, CONTENT_WIDTH, TEXT_COLOR);
  } else {
    drawCenteredTextInRegion(sheet, safeText, fonts.regular, 10, currentY, xOffset, CONTENT_WIDTH, TEXT_COLOR);
  }

  // Draw page number at bottom center
  if (displayPageNum !== null) {
    const numStr = `${displayPageNum}`;
    const numWidth = fonts.regular.widthOfTextAtSize(numStr, 8);
    sheet.drawText(numStr, {
      x: xOffset + (MINI_PAGE_WIDTH - numWidth) / 2,
      y: MARGIN_BOTTOM / 2,
      size: 8,
      font: fonts.regular,
      color: SUBTITLE_COLOR,
    });
  }
}

/**
 * Draw a dashed fold line at the center of the sheet.
 */
function drawFoldLine(sheet: PDFPage): void {
  const centerX = SHEET_WIDTH / 2;
  const dashLen = 4;
  const gapLen = 4;
  let y = SHEET_HEIGHT - 8;

  while (y > 8) {
    const endY = Math.max(y - dashLen, 8);
    sheet.drawLine({
      start: { x: centerX, y },
      end: { x: centerX, y: endY },
      thickness: 0.5,
      color: FOLD_LINE_COLOR,
    });
    y -= dashLen + gapLen;
  }
}

/**
 * Draw small crop marks at the top and bottom center (where the fold meets the edge).
 */
function drawCropMarks(sheet: PDFPage): void {
  const cx = SHEET_WIDTH / 2;
  const markLen = 12;

  // Top center
  sheet.drawLine({
    start: { x: cx, y: SHEET_HEIGHT },
    end: { x: cx, y: SHEET_HEIGHT - markLen },
    thickness: 0.3,
    color: CROP_MARK_COLOR,
  });
  // Bottom center
  sheet.drawLine({
    start: { x: cx, y: 0 },
    end: { x: cx, y: markLen },
    thickness: 0.3,
    color: CROP_MARK_COLOR,
  });
}

/**
 * Draw the instruction page (first page of the PDF, not part of the booklet itself).
 */
function drawInstructionPage(
  doc: PDFDocument,
  fonts: Fonts,
  totalSheets: number,
  title: string,
): void {
  const page = doc.addPage([SHEET_WIDTH, SHEET_HEIGHT]);
  let y = SHEET_HEIGHT - 72;

  // Heading
  const heading = "Printing Instructions";
  const headingWidth = fonts.bold.widthOfTextAtSize(heading, 24);
  page.drawText(heading, {
    x: (SHEET_WIDTH - headingWidth) / 2,
    y,
    size: 24,
    font: fonts.bold,
    color: TEXT_COLOR,
  });
  y -= 36;

  // Story title subtitle
  const safeTitle = sanitizeForPdf(title);
  const subtitle = `for "${safeTitle}"`;
  const subtitleWidth = fonts.italic.widthOfTextAtSize(subtitle, 14);
  page.drawText(subtitle, {
    x: (SHEET_WIDTH - subtitleWidth) / 2,
    y,
    size: 14,
    font: fonts.italic,
    color: SUBTITLE_COLOR,
  });
  y -= 56;

  // Numbered instructions
  const instructions = [
    `Print pages 2-${totalSheets * 2 + 1} of this PDF (skip this instruction page).`,
    "Use double-sided printing with \"Flip on Short Edge\" selected.",
    `You will need ${totalSheets} sheet${totalSheets > 1 ? "s" : ""} of paper.`,
    "Stack all printed sheets together, in order.",
    "Fold the entire stack in half along the center line.",
    "Staple along the folded spine (2-3 staples).",
    "Trim edges if desired, then enjoy your handmade picture book!",
  ];

  const leftMargin = 140;
  for (let i = 0; i < instructions.length; i++) {
    const num = `${i + 1}.`;
    const text = sanitizeForPdf(instructions[i]);

    page.drawText(num, {
      x: leftMargin,
      y,
      size: 13,
      font: fonts.bold,
      color: TEXT_COLOR,
    });
    page.drawText(text, {
      x: leftMargin + 24,
      y,
      size: 13,
      font: fonts.regular,
      color: TEXT_COLOR,
    });
    y -= 30;
  }

  // Visual hint — small booklet diagram
  y -= 20;
  const diagramLines = [
    "       ___________",
    "      |           |",
    "      |   fold    |",
    "      |   here    |",
    "      |     |     |",
    "      |     |     |",
    "      |_____|_____|",
    "         staple",
  ];
  for (const line of diagramLines) {
    const lineWidth = fonts.regular.widthOfTextAtSize(line, 11);
    page.drawText(line, {
      x: (SHEET_WIDTH - lineWidth) / 2,
      y,
      size: 11,
      font: fonts.regular,
      color: SUBTITLE_COLOR,
    });
    y -= 14;
  }
}

// =============================================================================
// Main export
// =============================================================================

export async function generateBookletPdf(
  title: string,
  pages: StoryPage[],
  images: Map<number, Buffer>,
): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  doc.setTitle(sanitizeForPdf(title));
  doc.setCreator("Storytime - Booklet Edition");

  // Embed fonts
  const fonts: Fonts = {
    regular: await doc.embedFont(StandardFonts.TimesRoman),
    bold: await doc.embedFont(StandardFonts.TimesRomanBold),
    italic: await doc.embedFont(StandardFonts.TimesRomanItalic),
    boldItalic: await doc.embedFont(StandardFonts.TimesRomanBoldItalic),
  };

  // Pad pages to multiple of 4
  const paddedPages = padToMultipleOf4(pages);
  const totalPages = paddedPages.length;
  const totalSheets = totalPages / 4;

  // Embed all images once (reuse PDFImage references)
  const embeddedImages = new Map<number, PDFImage>();
  for (const [pageNum, buffer] of images) {
    try {
      const img = isPng(buffer)
        ? await doc.embedPng(buffer)
        : await doc.embedJpg(buffer);
      embeddedImages.set(pageNum, img);
    } catch (err) {
      console.error(`Booklet: Failed to embed image for page ${pageNum}:`, err);
    }
  }

  // 1. Instruction page (first page of the PDF)
  drawInstructionPage(doc, fonts, totalSheets, title);

  // 2. Compute imposition order
  const pairs = computeImpositionOrder(totalPages);

  // 3. Draw each physical sheet side
  for (const [leftIdx, rightIdx] of pairs) {
    const sheet = doc.addPage([SHEET_WIDTH, SHEET_HEIGHT]);

    // Left mini page
    const leftPage = paddedPages[leftIdx] ?? null;
    const leftImg = leftPage ? embeddedImages.get(leftPage.page) : undefined;
    drawMiniPage(
      sheet, 0, leftPage, leftImg, fonts,
      leftPage?.page === 1,
      leftPage?.page === pages.length,
      leftPage?.page ?? null,
    );

    // Right mini page
    const rightPage = paddedPages[rightIdx] ?? null;
    const rightImg = rightPage ? embeddedImages.get(rightPage.page) : undefined;
    drawMiniPage(
      sheet, MINI_PAGE_WIDTH, rightPage, rightImg, fonts,
      rightPage?.page === 1,
      rightPage?.page === pages.length,
      rightPage?.page ?? null,
    );

    // Draw fold line and crop marks
    drawFoldLine(sheet);
    drawCropMarks(sheet);
  }

  return doc.save();
}
