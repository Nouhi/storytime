import type { GenerationEvent, StoryPage } from "@/lib/types";

export interface GenerationSession {
  id: string;
  status: "pending" | "generating-story" | "generating-images" | "assembling-ebook" | "complete" | "error";
  progress: number;
  detail: string;
  epubBuffer?: Uint8Array;
  storyPages?: StoryPage[];
  hasImages?: boolean;
  createdAt: Date;
  listeners: Set<(event: GenerationEvent) => void>;
}

// Use globalThis to share sessions across all Next.js route handler instances.
// Without this, different API routes may get different module instances (especially with Turbopack),
// causing the SSE stream route to not find sessions created by the generate route.
const globalStore = globalThis as unknown as {
  __storytime_sessions?: Map<string, GenerationSession>;
  __storytime_cleanup?: boolean;
};

if (!globalStore.__storytime_sessions) {
  globalStore.__storytime_sessions = new Map<string, GenerationSession>();
}

const sessions = globalStore.__storytime_sessions;

// Clean up old sessions every 30 minutes (only register the interval once)
if (!globalStore.__storytime_cleanup) {
  globalStore.__storytime_cleanup = true;
  setInterval(() => {
    const cutoff = Date.now() - 60 * 60 * 1000; // 1 hour
    for (const [id, session] of sessions) {
      if (session.createdAt.getTime() < cutoff) {
        sessions.delete(id);
      }
    }
  }, 30 * 60 * 1000);
}

export function createSession(id: string): GenerationSession {
  const session: GenerationSession = {
    id,
    status: "pending",
    progress: 0,
    detail: "",
    createdAt: new Date(),
    listeners: new Set(),
  };
  sessions.set(id, session);
  return session;
}

export function getSession(id: string): GenerationSession | undefined {
  return sessions.get(id);
}

export function updateSession(
  id: string,
  update: Partial<Pick<GenerationSession, "status" | "progress" | "detail" | "epubBuffer" | "storyPages" | "hasImages">>
) {
  const session = sessions.get(id);
  if (!session) return;

  Object.assign(session, update);

  // Notify listeners
  const event: GenerationEvent = {
    type: session.status === "complete" ? "complete" : session.status === "error" ? "error" : "progress",
    step: session.status,
    detail: session.detail,
    progress: session.progress,
  };

  if (session.status === "complete") {
    event.epubUrl = `/api/generate/${id}/epub`;
    event.storyId = id;
    event.storyPages = session.storyPages;
    event.hasImages = session.hasImages !== false;
  }
  if (session.status === "error") {
    event.message = session.detail;
  }

  for (const listener of session.listeners) {
    listener(event);
  }
}

export function addListener(id: string, listener: (event: GenerationEvent) => void) {
  const session = sessions.get(id);
  if (session) {
    session.listeners.add(listener);
  }
}

export function removeListener(id: string, listener: (event: GenerationEvent) => void) {
  const session = sessions.get(id);
  if (session) {
    session.listeners.delete(listener);
  }
}

export function deleteSession(id: string) {
  sessions.delete(id);
}
