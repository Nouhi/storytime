import { NextResponse } from "next/server";
import { v4 as uuid } from "uuid";
import { db } from "@/lib/db";
import { settings, familyMembers, storyHistory } from "@/lib/db/schema";
import { createSession, updateSession } from "@/lib/generation-manager";
import { generateStory } from "@/lib/ai/story-generator";
import { generateAllIllustrations } from "@/lib/ai/image-generator";
import { generateEpub } from "@/lib/epub/epub-generator";
import { saveGeneratedEpub, saveGeneratedImage, saveStoryData } from "@/lib/storage";

// Claude Sonnet 4.6 pricing: $3/MTok input, $15/MTok output
const CLAUDE_INPUT_COST_PER_TOKEN = 3.0 / 1_000_000;
const CLAUDE_OUTPUT_COST_PER_TOKEN = 15.0 / 1_000_000;
// Gemini 2.5 Flash image generation: ~$0.039 per image (approximate)
const GEMINI_COST_PER_IMAGE = 0.039;

export async function POST(request: Request) {
  const { prompt, writingStyle, imageStyle } = await request.json();

  if (!prompt?.trim()) {
    return NextResponse.json({ error: "Prompt is required" }, { status: 400 });
  }

  const storyId = uuid();
  createSession(storyId);

  // Run the pipeline asynchronously
  runPipeline(storyId, prompt, writingStyle, imageStyle).catch((err) => {
    console.error("Pipeline error:", err);
    updateSession(storyId, {
      status: "error",
      detail: err.message || "An unexpected error occurred",
    });
  });

  return NextResponse.json({ storyId });
}

async function runPipeline(storyId: string, prompt: string, writingStyle?: string, imageStyle?: string) {
  // 1. Load settings and family members
  const settingsRow = db.select().from(settings).get();
  const members = db.select().from(familyMembers).all();

  const kidName = settingsRow?.kidName || "";
  const kidGender = settingsRow?.kidGender || "";
  const kidPhotoPath = settingsRow?.kidPhotoPath || "";

  const context = {
    kidName,
    kidGender,
    readingLevel: settingsRow?.readingLevel || "early-reader",
    familyMembers: members,
  };

  // 2. Generate story text with Claude
  updateSession(storyId, {
    status: "generating-story",
    progress: 5,
    detail: "Writing your story...",
  });

  const storyResult = await generateStory(prompt, context, writingStyle);
  const storyPages = storyResult.pages;
  const characterSheet = storyResult.characterSheet;

  if (characterSheet) {
    console.log("Character sheet generated:", JSON.stringify(characterSheet, null, 2));
  }

  const skipImages = imageStyle === "none";
  let images = new Map<number, Buffer>();
  let imageCount = 0;

  if (skipImages) {
    // Update session with story pages for SSE stream
    updateSession(storyId, {
      progress: 80,
      detail: "Story written!",
      storyPages,
    });
  } else {
    updateSession(storyId, {
      status: "generating-images",
      progress: 15,
      detail: "Drawing illustrations (0/16)...",
      storyPages,
    });

    // 3. Generate illustrations with Gemini
    images = await generateAllIllustrations(
      storyPages.map((p) => ({ page: p.page, imageDescription: p.imageDescription })),
      members,
      (completed, total) => {
        const imageProgress = 15 + (completed / total) * 70;
        updateSession(storyId, {
          progress: Math.round(imageProgress),
          detail: `Drawing illustrations (${completed}/${total})...`,
        });
      },
      kidName,
      kidPhotoPath,
      imageStyle,
      characterSheet,
      kidGender,
    );

    imageCount = images.size;

    // 3.5. Save images to disk for preview
    for (const [pageNum, buffer] of images) {
      try {
        await saveGeneratedImage(storyId, pageNum, buffer);
      } catch (err) {
        console.error(`Failed to save image for page ${pageNum}:`, err);
      }
    }
  }

  // 3.6. Save story page data for historical preview
  try {
    await saveStoryData(storyId, storyPages);
  } catch (err) {
    console.error("Failed to save story data:", err);
  }

  const failedCount = skipImages ? 0 : 16 - imageCount;

  // 4. Assemble EPUB
  updateSession(storyId, {
    status: "assembling-ebook",
    progress: 90,
    detail: skipImages
      ? "Making your ebook..."
      : failedCount > 0
        ? `Making your ebook (${failedCount} images couldn't be generated)...`
        : "Making your ebook...",
  });

  const title = storyPages[0]?.text || prompt.slice(0, 100);
  const epubBuffer = await generateEpub(title, storyPages, images, !skipImages);

  // 5. Save EPUB to disk
  let epubPath = "";
  try {
    epubPath = await saveGeneratedEpub(storyId, epubBuffer);
  } catch (err) {
    console.error("Failed to save EPUB to disk:", err);
  }

  // 6. Calculate costs and save to history
  const claudeCost =
    storyResult.inputTokens * CLAUDE_INPUT_COST_PER_TOKEN +
    storyResult.outputTokens * CLAUDE_OUTPUT_COST_PER_TOKEN;
  const geminiCost = imageCount * GEMINI_COST_PER_IMAGE;
  const totalCost = claudeCost + geminiCost;

  try {
    db.insert(storyHistory).values({
      title,
      prompt,
      createdAt: new Date().toISOString(),
      claudeInputTokens: storyResult.inputTokens,
      claudeOutputTokens: storyResult.outputTokens,
      geminiImageCount: imageCount,
      claudeCost: Math.round(claudeCost * 10000) / 10000,
      geminiCost: Math.round(geminiCost * 10000) / 10000,
      totalCost: Math.round(totalCost * 10000) / 10000,
      pdfPath: epubPath,
    }).run();
  } catch (err) {
    console.error("Failed to save story history:", err);
  }

  updateSession(storyId, {
    status: "complete",
    progress: 100,
    detail: "Your story is ready!",
    epubBuffer,
    storyPages,
    hasImages: !skipImages,
  });
}
