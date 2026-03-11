import { Router } from "express";
import { getSession, addListener, removeListener } from "../lib/generation-manager.js";
import type { GenerationEvent } from "../lib/types.js";

const router = Router();

router.get("/:storyId/stream", async (req, res) => {
  const { storyId } = req.params;

  // Retry a few times in case of a race condition
  let session = getSession(storyId);
  if (!session) {
    for (let i = 0; i < 5; i++) {
      await new Promise((r) => setTimeout(r, 200));
      session = getSession(storyId);
      if (session) break;
    }
  }

  if (!session) {
    res.status(404).json({ error: "Session not found" });
    return;
  }

  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
  });

  // Send current state immediately
  const currentEvent: GenerationEvent = {
    type: session.status === "complete" ? "complete" : session.status === "error" ? "error" : "progress",
    step: session.status,
    detail: session.detail,
    progress: session.progress,
  };
  if (session.status === "complete") {
    currentEvent.epubUrl = `/api/generate/${storyId}/epub`;
    currentEvent.storyId = storyId;
    currentEvent.storyPages = session.storyPages;
    currentEvent.hasImages = session.hasImages !== false;
  }
  res.write(`data: ${JSON.stringify(currentEvent)}\n\n`);

  if (session.status === "complete" || session.status === "error") {
    res.end();
    return;
  }

  const listener = (event: GenerationEvent) => {
    try {
      res.write(`data: ${JSON.stringify(event)}\n\n`);
      if (event.type === "complete" || event.type === "error") {
        res.end();
      }
    } catch {
      removeListener(storyId, listener);
    }
  };

  addListener(storyId, listener);

  req.on("close", () => {
    removeListener(storyId, listener);
  });
});

export default router;
