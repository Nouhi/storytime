"use client";

import { useSettings } from "@/hooks/use-settings";
import { useGeneration } from "@/hooks/use-generation";
import { PromptInput } from "@/components/prompt-input";
import { StoryProgress } from "@/components/story-progress";
import { StoryPreview } from "@/components/story-preview";
import Link from "next/link";

export default function Home() {
  const { settings, loading: settingsLoading } = useSettings();
  const { status, step, detail, progress, epubUrl, storyPages, storyId, errorMessage, generate, reset } =
    useGeneration();

  const kidName = settings?.kidName || "";
  const isGenerating = status === "generating";
  const isComplete = status === "complete";
  const isError = status === "error";

  return (
    <div className="flex flex-col items-center min-h-[calc(100vh-3.5rem)]">
      {/* Centered content area */}
      <div className="flex-1 flex flex-col items-center justify-center w-full max-w-2xl px-4 -mt-14">
        {/* Hero */}
        {!isComplete && (
          <div className="text-center space-y-3 mb-8">
            <h1 className="text-3xl font-semibold tracking-tight text-foreground">
              {kidName ? (
                <>
                  A story for{" "}
                  <span className="text-primary">{kidName}</span>
                </>
              ) : (
                "Storytime"
              )}
            </h1>
            <p className="text-[15px] text-muted-foreground max-w-md mx-auto leading-relaxed">
              {kidName
                ? "Describe tonight's adventure and we'll write and illustrate a story you can read together."
                : "Set up your story character in Settings to get started."}
            </p>
          </div>
        )}

        {/* No kid name - setup card */}
        {!settingsLoading && !kidName && (
          <div className="bg-card rounded-2xl border border-border p-8 text-center space-y-4 w-full max-w-sm shadow-sm">
            <div className="w-12 h-12 rounded-xl bg-primary-light flex items-center justify-center mx-auto text-primary">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <line x1="19" x2="19" y1="8" y2="14" />
                <line x1="22" x2="16" y1="11" y2="11" />
              </svg>
            </div>
            <div>
              <p className="font-medium text-foreground">Add your story character</p>
              <p className="text-sm text-muted-foreground mt-1">
                Enter your child&apos;s name and add family members.
              </p>
            </div>
            <Link
              href="/settings"
              className="inline-flex items-center justify-center px-5 py-2.5 bg-primary text-white text-sm font-medium rounded-xl hover:bg-primary-hover transition-colors"
            >
              Open Settings
            </Link>
          </div>
        )}

        {/* Prompt input */}
        {kidName && status === "idle" && (
          <div className="w-full">
            <PromptInput
              onSubmit={(prompt, writingStyle, imageStyle) => generate(prompt, writingStyle, imageStyle)}
              disabled={isGenerating}
              kidName={kidName}
            />
          </div>
        )}

        {/* Progress */}
        {isGenerating && (
          <div className="w-full max-w-md">
            <StoryProgress step={step} detail={detail} progress={progress} />
          </div>
        )}

        {/* Complete - Story Preview */}
        {isComplete && epubUrl && storyPages && storyId && (
          <StoryPreview
            pages={storyPages}
            storyId={storyId}
            imageBaseUrl={`/api/generate/${storyId}/pages`}
            epubUrl={epubUrl}
            pdfUrl={`/api/generate/${storyId}/pdf`}
            onCreateAnother={reset}
          />
        )}

        {/* Error */}
        {isError && (
          <div className="bg-card rounded-2xl border border-destructive/20 p-8 text-center space-y-4 w-full max-w-sm shadow-sm">
            <div className="w-12 h-12 rounded-xl bg-destructive/10 flex items-center justify-center mx-auto text-destructive">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <line x1="15" x2="9" y1="9" y2="15" />
                <line x1="9" x2="15" y1="9" y2="15" />
              </svg>
            </div>
            <div>
              <p className="font-medium text-foreground">Something went wrong</p>
              <p className="text-sm text-muted-foreground mt-1">{errorMessage}</p>
            </div>
            <button
              onClick={reset}
              className="px-5 py-2.5 bg-primary text-white text-sm font-medium rounded-xl hover:bg-primary-hover transition-colors"
            >
              Try Again
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
