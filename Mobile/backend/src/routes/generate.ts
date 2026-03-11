import { Router } from "express";
import { v4 as uuid } from "uuid";
import { db } from "../lib/db/index.js";
import { settings, familyMembers, storyHistory } from "../lib/db/schema.js";
import { createSession, updateSession } from "../lib/generation-manager.js";
import { generateStory } from "../lib/ai/story-generator.js";
import { generateAllIllustrations } from "../lib/ai/image-generator.js";
import { generateEpub } from "../lib/epub/epub-generator.js";
import { saveGeneratedEpub, saveGeneratedImage, saveStoryData } from "../lib/storage.js";
import { validateCustomInput } from "../lib/ai/validate-custom-input.js";

const CLAUDE_INPUT_COST_PER_TOKEN = 3.0 / 1_000_000;
const CLAUDE_OUTPUT_COST_PER_TOKEN = 15.0 / 1_000_000;
const GEMINI_COST_PER_IMAGE = 0.039;

const router = Router();

router.post("/", async (req, res) => {
  const { prompt, writingStyle, imageStyle, characterIds, lesson, customWritingStyle, customImageStyle, customLesson, bedtimeStory } = req.body;

  if (!prompt?.trim()) {
    res.status(400).json({ error: "Prompt is required" });
    return;
  }

  // Validate custom inputs — basic checks first (empty + max length)
  if (writingStyle === "custom") {
    const text = (customWritingStyle || "").trim();
    if (!text) {
      res.status(400).json({ error: "Please describe your custom writing style." });
      return;
    }
    if (text.length > 500) {
      res.status(400).json({ error: "Your custom writing style description is too long. Please keep it under 500 characters." });
      return;
    }
  }

  if (imageStyle === "custom") {
    const text = (customImageStyle || "").trim();
    if (!text) {
      res.status(400).json({ error: "Please describe your custom image style." });
      return;
    }
    if (text.length > 500) {
      res.status(400).json({ error: "Your custom image style description is too long. Please keep it under 500 characters." });
      return;
    }
  }

  if (lesson === "custom") {
    const text = (customLesson || "").trim();
    if (!text) {
      res.status(400).json({ error: "Please describe your custom lesson." });
      return;
    }
    if (text.length > 500) {
      res.status(400).json({ error: "Your custom lesson description is too long. Please keep it under 500 characters." });
      return;
    }
  }

  // Semantic validation — run all custom checks in parallel for speed
  try {
    const semanticChecks: Promise<{ field: string; error: string | null }>[] = [];

    if (writingStyle === "custom" && customWritingStyle?.trim()) {
      semanticChecks.push(
        validateCustomInput("writing style", customWritingStyle.trim()).then((error) => ({
          field: "writing style",
          error,
        })),
      );
    }
    if (imageStyle === "custom" && customImageStyle?.trim()) {
      semanticChecks.push(
        validateCustomInput("image style", customImageStyle.trim()).then((error) => ({
          field: "image style",
          error,
        })),
      );
    }
    if (lesson === "custom" && customLesson?.trim()) {
      semanticChecks.push(
        validateCustomInput("lesson", customLesson.trim()).then((error) => ({
          field: "lesson",
          error,
        })),
      );
    }

    if (semanticChecks.length > 0) {
      const results = await Promise.all(semanticChecks);
      const firstError = results.find((r) => r.error !== null);
      if (firstError) {
        res.status(400).json({
          error: `Your custom ${firstError.field} doesn't look right: ${firstError.error}`,
        });
        return;
      }
    }
  } catch (err) {
    // If semantic validation itself fails (e.g. API key issue), log but don't block generation
    console.error("[semantic-validation] Validation failed, allowing through:", err);
  }

  const storyId = uuid();
  createSession(storyId);

  runPipeline(storyId, prompt, writingStyle, imageStyle, characterIds, lesson, customWritingStyle, customImageStyle, customLesson, bedtimeStory).catch((err) => {
    console.error("Pipeline error:", err);
    updateSession(storyId, {
      status: "error",
      detail: err.message || "An unexpected error occurred",
    });
  });

  res.json({ storyId });
});

async function runPipeline(
  storyId: string,
  prompt: string,
  writingStyle?: string,
  imageStyle?: string,
  characterIds?: number[],
  lesson?: string,
  customWritingStyle?: string,
  customImageStyle?: string,
  customLesson?: string,
  bedtimeStory?: boolean,
) {
  const settingsRow = db.select().from(settings).get();
  const allMembers = db.select().from(familyMembers).all();

  // If specific character IDs were provided, filter to only those characters
  const members = characterIds && characterIds.length > 0
    ? allMembers.filter((m) => characterIds.includes(m.id))
    : allMembers;

  const kidName = settingsRow?.kidName || "";
  const kidGender = settingsRow?.kidGender || "";
  const kidPhotoPath = settingsRow?.kidPhotoPath || "";

  const context = {
    kidName,
    kidGender,
    readingLevel: settingsRow?.readingLevel || "early-reader",
    familyMembers: members,
  };

  updateSession(storyId, {
    status: "generating-story",
    progress: 5,
    detail: "Writing your story...",
  });

  const storyResult = await generateStory(prompt, context, writingStyle, lesson, customWritingStyle, customLesson, bedtimeStory);
  const storyPages = storyResult.pages;
  const characterSheet = storyResult.characterSheet;

  if (characterSheet) {
    console.log("Character sheet generated:", JSON.stringify(characterSheet, null, 2));
  }

  const skipImages = imageStyle === "none";
  const effectiveImageStyle = imageStyle === "custom" && customImageStyle ? customImageStyle : imageStyle;
  let images = new Map<number, Buffer>();
  let imageCount = 0;

  if (skipImages) {
    updateSession(storyId, {
      progress: 80,
      detail: "Story written!",
      storyPages,
    });
  } else {
    const totalPages = storyPages.length;
    updateSession(storyId, {
      status: "generating-images",
      progress: 15,
      detail: `Drawing illustrations (0/${totalPages})...`,
      storyPages,
    });

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
      effectiveImageStyle,
      characterSheet,
      kidGender,
    );

    imageCount = images.size;

    for (const [pageNum, buffer] of images) {
      try {
        await saveGeneratedImage(storyId, pageNum, buffer);
      } catch (err) {
        console.error(`Failed to save image for page ${pageNum}:`, err);
      }
    }
  }

  try {
    await saveStoryData(storyId, storyPages);
  } catch (err) {
    console.error("Failed to save story data:", err);
  }

  const expectedPages = storyPages.length;
  const failedCount = skipImages ? 0 : expectedPages - imageCount;

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

  let epubPath = "";
  try {
    epubPath = await saveGeneratedEpub(storyId, epubBuffer);
  } catch (err) {
    console.error("Failed to save EPUB to disk:", err);
  }

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

export default router;
