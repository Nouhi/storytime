import { getSession, addListener, removeListener } from "@/lib/generation-manager";
import type { GenerationEvent } from "@/lib/types";

export const dynamic = "force-dynamic";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ storyId: string }> }
) {
  const { storyId } = await params;

  // Retry a few times in case of a race condition between session creation and SSE connection
  let session = getSession(storyId);
  if (!session) {
    for (let i = 0; i < 5; i++) {
      await new Promise((r) => setTimeout(r, 200));
      session = getSession(storyId);
      if (session) break;
    }
  }

  if (!session) {
    return new Response("Session not found", { status: 404 });
  }

  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
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
      }
      controller.enqueue(encoder.encode(`data: ${JSON.stringify(currentEvent)}\n\n`));

      // If already done, close
      if (session.status === "complete" || session.status === "error") {
        controller.close();
        return;
      }

      // Listen for updates
      const listener = (event: GenerationEvent) => {
        try {
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(event)}\n\n`));
          if (event.type === "complete" || event.type === "error") {
            controller.close();
          }
        } catch {
          // Stream may have been closed
          removeListener(storyId, listener);
        }
      };

      addListener(storyId, listener);

      // Cleanup on abort
      _request.signal.addEventListener("abort", () => {
        removeListener(storyId, listener);
      });
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    },
  });
}
