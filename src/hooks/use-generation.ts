"use client";

import { useState, useCallback, useRef } from "react";
import type { GenerationEvent, StoryPage } from "@/lib/types";

type GenerationState = {
  status: "idle" | "generating" | "complete" | "error";
  step: string;
  detail: string;
  progress: number;
  epubUrl: string | null;
  storyPages: StoryPage[] | null;
  storyId: string | null;
  hasImages: boolean;
  errorMessage: string | null;
};

export function useGeneration() {
  const [state, setState] = useState<GenerationState>({
    status: "idle",
    step: "",
    detail: "",
    progress: 0,
    epubUrl: null,
    storyPages: null,
    storyId: null,
    hasImages: true,
    errorMessage: null,
  });
  const eventSourceRef = useRef<EventSource | null>(null);

  const generate = useCallback(async (prompt: string, writingStyle?: string, imageStyle?: string) => {
    setState({
      status: "generating",
      step: "starting",
      detail: "Starting your story...",
      progress: 0,
      epubUrl: null,
      storyPages: null,
      storyId: null,
      hasImages: imageStyle !== "none",
      errorMessage: null,
    });

    try {
      const res = await fetch("/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, writingStyle, imageStyle }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Failed to start generation");
      }

      const { storyId } = await res.json();

      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      const es = new EventSource(`/api/generate/${storyId}/stream`);
      eventSourceRef.current = es;

      es.onmessage = (e) => {
        const event: GenerationEvent = JSON.parse(e.data);

        if (event.type === "complete") {
          setState({
            status: "complete",
            step: "complete",
            detail: "Your story is ready!",
            progress: 100,
            epubUrl: event.epubUrl || null,
            storyPages: event.storyPages || null,
            storyId: event.storyId || null,
            hasImages: event.hasImages !== false,
            errorMessage: null,
          });
          es.close();
        } else if (event.type === "error") {
          setState((prev) => ({
            ...prev,
            status: "error",
            errorMessage: event.message || "Something went wrong",
          }));
          es.close();
        } else {
          setState((prev) => ({
            ...prev,
            step: event.step || prev.step,
            detail: event.detail || prev.detail,
            progress: event.progress ?? prev.progress,
          }));
        }
      };

      es.onerror = () => {
        setState((prev) => ({
          ...prev,
          status: "error",
          errorMessage: "Lost connection to server",
        }));
        es.close();
      };
    } catch (err) {
      setState((prev) => ({
        ...prev,
        status: "error",
        errorMessage: err instanceof Error ? err.message : "Something went wrong",
      }));
    }
  }, []);

  const reset = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }
    setState({
      status: "idle",
      step: "",
      detail: "",
      progress: 0,
      epubUrl: null,
      storyPages: null,
      storyId: null,
      hasImages: true,
      errorMessage: null,
    });
  }, []);

  return { ...state, generate, reset };
}
