"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export function Header() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-50 bg-background/80 backdrop-blur-xl border-b border-border">
      <div className="max-w-2xl mx-auto px-6 h-14 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="w-7 h-7 rounded-lg bg-primary flex items-center justify-center">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
            </svg>
          </div>
          <span className="text-base font-semibold text-foreground tracking-tight">
            Storytime
          </span>
        </Link>
        <nav className="flex items-center gap-1">
          <Link
            href="/"
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              pathname === "/"
                ? "bg-primary-light text-primary font-medium"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
            }`}
          >
            Create
          </Link>
          <Link
            href="/settings"
            className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
              pathname === "/settings"
                ? "bg-primary-light text-primary font-medium"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
            }`}
          >
            Settings
          </Link>
        </nav>
      </div>
    </header>
  );
}
