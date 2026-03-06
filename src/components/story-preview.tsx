"use client";

import { useState, useEffect, useCallback } from "react";
import { QRCodeSVG } from "qrcode.react";
import type { StoryPage } from "@/lib/types";

interface StoryPreviewProps {
  pages: StoryPage[];
  storyId: string;
  imageBaseUrl: string;
  epubUrl: string;
  pdfUrl?: string;
  onCreateAnother?: () => void;
}

export function StoryPreview({
  pages,
  storyId,
  imageBaseUrl,
  epubUrl,
  pdfUrl,
  onCreateAnother,
}: StoryPreviewProps) {
  const [currentPage, setCurrentPage] = useState(0);
  const [networkInfo, setNetworkInfo] = useState<{ ip: string; port: string } | null>(null);
  const page = pages[currentPage];
  const isFirst = currentPage === 0;
  const isLast = currentPage === pages.length - 1;
  const isCover = currentPage === 0;

  // Fetch LAN IP for QR code
  useEffect(() => {
    fetch("/api/network-info")
      .then((res) => res.json())
      .then(setNetworkInfo)
      .catch(() => {});
  }, []);

  const qrUrl = networkInfo
    ? `http://${networkInfo.ip}:${networkInfo.port}${epubUrl}?inline=1`
    : null;

  // Keyboard navigation
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") setCurrentPage((p) => Math.max(0, p - 1));
      if (e.key === "ArrowRight") setCurrentPage((p) => Math.min(pages.length - 1, p + 1));
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [pages.length]);

  // Preload adjacent images
  useEffect(() => {
    const toPreload = [currentPage - 1, currentPage + 1]
      .filter((i) => i >= 0 && i < pages.length);
    for (const i of toPreload) {
      const img = new Image();
      img.src = `${imageBaseUrl}?page=${pages[i].page}`;
    }
  }, [currentPage, pages, imageBaseUrl]);

  const imageUrl = `${imageBaseUrl}?page=${page.page}`;

  // Parse cover title — split "Title - a bedtime story for ..." into title + subtitle
  let coverTitle = page.text;
  let coverSubtitle = "";
  if (isCover) {
    const bedtimeMatch = page.text.match(/^(.+?)\s*[-:,]?\s*(a bedtime story.*)$/i);
    const separatorMatch = !bedtimeMatch && page.text.match(/^(.+?)\s*[-:]\s*(.+)$/);
    const match = bedtimeMatch || separatorMatch;
    if (match) {
      coverTitle = match[1].trim();
      coverSubtitle = match[2].trim();
    }
  }

  return (
    <div className="w-full max-w-lg mx-auto space-y-4">
      {/* Page display card */}
      <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
        {/* Image */}
        <div className="aspect-[3/2] bg-muted relative overflow-hidden">
          <img
            key={page.page}
            src={imageUrl}
            alt={`Page ${page.page} illustration`}
            className="w-full h-full object-cover"
          />
          {!isCover && (
            <span className="absolute top-3 right-4 text-[11px] font-mono text-white/70 drop-shadow-md">
              {page.page}
            </span>
          )}
        </div>

        {/* Text */}
        <div className="px-6 py-5">
          {isCover ? (
            <div className="text-center">
              <p className="text-xl font-semibold text-foreground leading-snug">
                {coverTitle}
              </p>
              {coverSubtitle && (
                <p className="text-sm text-muted-foreground mt-2 italic">
                  {coverSubtitle}
                </p>
              )}
            </div>
          ) : (
            <p className="text-[15px] text-foreground leading-relaxed">
              {page.text}
            </p>
          )}
        </div>

        {/* Navigation bar */}
        <div className="flex items-center justify-between px-4 pb-4">
          <button
            onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
            disabled={isFirst}
            className="w-9 h-9 flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="Previous page"
          >
            <svg className="w-5 h-5 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>

          <span className="text-[13px] text-muted-foreground font-mono tabular-nums">
            {currentPage + 1} / {pages.length}
          </span>

          <button
            onClick={() => setCurrentPage(Math.min(pages.length - 1, currentPage + 1))}
            disabled={isLast}
            className="w-9 h-9 flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="Next page"
          >
            <svg className="w-5 h-5 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
      </div>

      {/* Action buttons */}
      <div className="flex flex-col gap-2.5">
        {pdfUrl && (
          <a
            href={pdfUrl}
            download
            className="flex items-center justify-center gap-2 px-5 py-2.5 border border-primary text-primary text-sm font-medium rounded-xl hover:bg-primary/5 transition-colors"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" x2="12" y1="15" y2="3" />
            </svg>
            Download PDF
          </a>
        )}
        <a
          href={epubUrl}
          download
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-primary text-white text-sm font-medium rounded-xl hover:bg-primary-hover transition-colors"
        >
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="7 10 12 15 17 10" />
            <line x1="12" x2="12" y1="15" y2="3" />
          </svg>
          Download EPUB
        </a>
        {qrUrl && (
          <div className="flex flex-col items-center gap-2 py-4 px-5 bg-muted/50 rounded-xl">
            <QRCodeSVG
              value={qrUrl}
              size={140}
              level="M"
              bgColor="transparent"
            />
            <p className="text-xs text-muted-foreground text-center">
              Scan to open in Apple Books
            </p>
          </div>
        )}
        {onCreateAnother && (
          <button
            onClick={onCreateAnother}
            className="px-5 py-2.5 text-sm font-medium text-muted-foreground rounded-xl hover:bg-muted transition-colors"
          >
            Create Another Story
          </button>
        )}
      </div>
    </div>
  );
}
