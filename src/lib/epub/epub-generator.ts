import type { StoryPage } from "@/lib/types";
import * as zlib from "zlib";

// =============================================================================
// EPUB Generator — Reflowable EPUB 3.0 for iOS Books / Kindle
// =============================================================================
//
// Structure:
//   mimetype              (stored, no compression)
//   META-INF/container.xml
//   OEBPS/content.opf
//   OEBPS/toc.xhtml
//   OEBPS/css/style.css
//   OEBPS/text/page-NN.xhtml   (16 pages)
//   OEBPS/images/page-NN.png   (16 images)
// =============================================================================

function escapeXml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function padNum(n: number): string {
  return String(n).padStart(2, "0");
}

// =============================================================================
// EPUB content templates
// =============================================================================

const MIMETYPE = "application/epub+zip";

const CONTAINER_XML = `<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>`;

function buildContentOpf(title: string, storyId: string, pageCount: number, hasImages: boolean): string {
  const now = new Date().toISOString().replace(/\.\d+Z$/, "Z");

  let manifestItems = `    <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="css" href="css/style.css" media-type="text/css"/>`;

  let spineItems = "";

  for (let i = 1; i <= pageCount; i++) {
    const id = padNum(i);
    manifestItems += `
    <item id="page-${id}" href="text/page-${id}.xhtml" media-type="application/xhtml+xml"/>`;
    if (hasImages) {
      const props = i === 1 ? ' properties="cover-image"' : "";
      manifestItems += `
    <item id="img-${id}" href="images/page-${id}.png" media-type="image/png"${props}/>`;
    }
    spineItems += `
    <itemref idref="page-${id}"/>`;
  }

  return `<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid"
         prefix="ibooks: http://vocabulary.itunes.apple.com/rdf/ibooks/vocab-extensions-1.0/">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:${storyId}</dc:identifier>
    <dc:title>${escapeXml(title)}</dc:title>
    <dc:language>en</dc:language>
    <dc:creator>Storytime</dc:creator>
    <meta property="dcterms:modified">${now}</meta>
    <meta property="ibooks:specified-fonts">true</meta>
  </metadata>
  <manifest>
${manifestItems}
  </manifest>
  <spine>${spineItems}
  </spine>
</package>`;
}

function buildTocXhtml(pages: StoryPage[]): string {
  let items = "";
  for (const p of pages) {
    const id = padNum(p.page);
    const label = p.page === 1 ? "Cover" : p.page === 16 ? "The End" : `Page ${p.page}`;
    items += `      <li><a href="text/page-${id}.xhtml">${escapeXml(label)}</a></li>\n`;
  }

  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Table of Contents</title></head>
<body>
  <nav epub:type="toc">
    <h1>Contents</h1>
    <ol>
${items}    </ol>
  </nav>
</body>
</html>`;
}

const STYLE_CSS = `
@page {
  margin: 0;
  padding: 0;
}

html, body {
  margin: 0;
  padding: 0;
  font-family: Georgia, "Times New Roman", serif;
  background: #fff;
}

.page {
  page-break-after: always;
  page-break-inside: avoid;
}

.image-container {
  text-align: center;
  margin: 0;
  padding: 0;
}

.image-container img {
  width: 100%;
  height: auto;
  display: block;
}

.text-container {
  padding: 1.5em 1.5em 2em 1.5em;
  text-align: center;
}

.text-container p {
  font-size: 1.2em;
  line-height: 1.6;
  color: #1a1a1a;
  margin: 0;
}

/* Cover page */
.cover .text-container {
  padding: 2em 1.5em;
}

.cover .text-container .title {
  font-size: 1.8em;
  font-weight: bold;
  line-height: 1.3;
  color: #1a1a1a;
  text-align: center;
  margin: 0 0 0.5em 0;
}

.cover .text-container .subtitle {
  font-size: 1.1em;
  color: #666;
  text-align: center;
  margin: 0;
  font-style: italic;
}

/* End page */
.end-page .text-container p {
  font-size: 1.4em;
  font-weight: bold;
  font-style: italic;
}

/* Text-only mode */
.text-only .text-container {
  padding: 2em 1.5em;
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
`;

function buildPageXhtml(page: StoryPage, isCover: boolean, isEnd: boolean, hasImages: boolean): string {
  const id = padNum(page.page);
  const text = escapeXml(page.text);

  let bodyClass = "page";
  if (isCover) bodyClass += " cover";
  if (isEnd) bodyClass += " end-page";
  if (!hasImages) bodyClass += " text-only";

  let textContent: string;
  if (isCover) {
    // Split "Title - a bedtime story for ..." into title + subtitle
    const bedtimeMatch = page.text.match(/^(.+?)\s*[-:,]?\s*(a bedtime story.*)$/i);
    const separatorMatch = !bedtimeMatch && page.text.match(/^(.+?)\s*[-:]\s*(.+)$/);
    const match = bedtimeMatch || separatorMatch;
    if (match) {
      textContent = `<p class="title">${escapeXml(match[1].trim())}</p><p class="subtitle">${escapeXml(match[2].trim())}</p>`;
    } else {
      textContent = `<p class="title">${text}</p>`;
    }
  } else {
    textContent = `<p>${text}</p>`;
  }

  const imageHtml = hasImages
    ? `    <div class="image-container">
      <img src="../images/page-${id}.png" alt="Illustration for page ${page.page}"/>
    </div>\n`
    : "";

  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <meta charset="UTF-8"/>
  <link rel="stylesheet" type="text/css" href="../css/style.css"/>
  <title>Page ${page.page}</title>
</head>
<body>
  <div class="${bodyClass}">
${imageHtml}    <div class="text-container">
      ${textContent}
    </div>
  </div>
</body>
</html>`;
}

// =============================================================================
// ZIP builder — minimal ZIP implementation for EPUB
// =============================================================================
// EPUB requires the `mimetype` file to be stored uncompressed as the first entry.
// We use a minimal ZIP builder rather than a library to control this.

interface ZipEntry {
  path: string;
  data: Buffer;
  compressed: boolean;
}

function crc32(buf: Buffer): number {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildZip(entries: ZipEntry[]): Buffer {
  const localHeaders: Buffer[] = [];
  const centralHeaders: Buffer[] = [];
  let offset = 0;

  for (const entry of entries) {
    const pathBuf = Buffer.from(entry.path, "utf8");
    const uncompressedSize = entry.data.length;
    const crc = crc32(entry.data);

    let compressedData: Buffer;
    let compressionMethod: number;

    if (entry.compressed) {
      // Deflate (raw, no zlib header)
      compressedData = zlib.deflateRawSync(entry.data, { level: 6 });
      compressionMethod = 8;
    } else {
      compressedData = entry.data;
      compressionMethod = 0;
    }

    const compressedSize = compressedData.length;

    // Local file header (30 bytes + path + data)
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0); // signature
    localHeader.writeUInt16LE(20, 4);          // version needed
    localHeader.writeUInt16LE(0, 6);           // flags
    localHeader.writeUInt16LE(compressionMethod, 8);
    localHeader.writeUInt16LE(0, 10);          // mod time
    localHeader.writeUInt16LE(0, 12);          // mod date
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(compressedSize, 18);
    localHeader.writeUInt32LE(uncompressedSize, 22);
    localHeader.writeUInt16LE(pathBuf.length, 26);
    localHeader.writeUInt16LE(0, 28);          // extra field length

    localHeaders.push(Buffer.concat([localHeader, pathBuf, compressedData]));

    // Central directory header (46 bytes + path)
    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0); // signature
    centralHeader.writeUInt16LE(20, 4);          // version made by
    centralHeader.writeUInt16LE(20, 6);          // version needed
    centralHeader.writeUInt16LE(0, 8);           // flags
    centralHeader.writeUInt16LE(compressionMethod, 10);
    centralHeader.writeUInt16LE(0, 12);          // mod time
    centralHeader.writeUInt16LE(0, 14);          // mod date
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(compressedSize, 20);
    centralHeader.writeUInt32LE(uncompressedSize, 24);
    centralHeader.writeUInt16LE(pathBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);          // extra field length
    centralHeader.writeUInt16LE(0, 32);          // comment length
    centralHeader.writeUInt16LE(0, 34);          // disk number
    centralHeader.writeUInt16LE(0, 36);          // internal attributes
    centralHeader.writeUInt32LE(0, 38);          // external attributes
    centralHeader.writeUInt32LE(offset, 42);     // local header offset

    centralHeaders.push(Buffer.concat([centralHeader, pathBuf]));

    offset += 30 + pathBuf.length + compressedSize;
  }

  const localData = Buffer.concat(localHeaders);
  const centralData = Buffer.concat(centralHeaders);
  const centralOffset = localData.length;
  const centralSize = centralData.length;

  // End of central directory (22 bytes)
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);          // signature
  eocd.writeUInt16LE(0, 4);                    // disk number
  eocd.writeUInt16LE(0, 6);                    // central dir disk
  eocd.writeUInt16LE(entries.length, 8);       // entries on disk
  eocd.writeUInt16LE(entries.length, 10);      // total entries
  eocd.writeUInt32LE(centralSize, 12);         // central dir size
  eocd.writeUInt32LE(centralOffset, 16);       // central dir offset
  eocd.writeUInt16LE(0, 20);                   // comment length

  return Buffer.concat([localData, centralData, eocd]);
}

// =============================================================================
// Public API
// =============================================================================

export async function generateEpub(
  title: string,
  pages: StoryPage[],
  images: Map<number, Buffer>,
  hasImages: boolean = true,
): Promise<Uint8Array> {
  const storyId = crypto.randomUUID();

  const entries: ZipEntry[] = [];

  // 1. mimetype MUST be first and uncompressed
  entries.push({
    path: "mimetype",
    data: Buffer.from(MIMETYPE, "ascii"),
    compressed: false,
  });

  // 2. META-INF/container.xml
  entries.push({
    path: "META-INF/container.xml",
    data: Buffer.from(CONTAINER_XML, "utf8"),
    compressed: true,
  });

  // 3. content.opf
  entries.push({
    path: "OEBPS/content.opf",
    data: Buffer.from(buildContentOpf(title, storyId, pages.length, hasImages), "utf8"),
    compressed: true,
  });

  // 4. toc.xhtml
  entries.push({
    path: "OEBPS/toc.xhtml",
    data: Buffer.from(buildTocXhtml(pages), "utf8"),
    compressed: true,
  });

  // 5. CSS
  entries.push({
    path: "OEBPS/css/style.css",
    data: Buffer.from(STYLE_CSS, "utf8"),
    compressed: true,
  });

  // 6. Pages + images
  for (const page of pages) {
    const id = padNum(page.page);
    const isCover = page.page === 1;
    const isEnd = page.page === pages.length;

    // XHTML page
    entries.push({
      path: `OEBPS/text/page-${id}.xhtml`,
      data: Buffer.from(buildPageXhtml(page, isCover, isEnd, hasImages), "utf8"),
      compressed: true,
    });

    // Image (PNG or JPEG from Gemini)
    const imgBuf = images.get(page.page);
    if (imgBuf) {
      entries.push({
        path: `OEBPS/images/page-${id}.png`,
        data: imgBuf,
        compressed: false, // PNGs are already compressed
      });
    }
  }

  return buildZip(entries);
}
