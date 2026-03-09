import { Router } from "express";
import { v4 as uuid } from "uuid";
import { db } from "../lib/db/index.js";
import { settings, familyMembers, storyHistory } from "../lib/db/schema.js";
import { createSession, updateSession } from "../lib/generation-manager.js";
import { generateStory } from "../lib/ai/story-generator.js";
import { generateAllIllustrations } from "../lib/ai/image-generator.js";
import { generateEpub } from "../lib/epub/epub-generator.js";
import { saveGeneratedEpub, saveGeneratedImage, saveStoryData } from "../lib/storage.js";

const CLAUDE_INPUT_COST_PER_TOKEN = 3.0 / 1_000_000;
const CLAUDE_OUTPUT_COST_PER_TOKEN = 15.0 / 1_000_000;
const GEMINI_COST_PER_IMAGE = 0.039;

const router = Router();

router.post("/", (req, res) => {
  const { prompt, writingStyle, imageStyle, characterIds } = req.body;

  if (!prompt?.trim()) {
    res.status(400).json({ error: "Prompt is required" });
    return;
  }

  const storyId = uuid();
  createSession(storyId);

  runPipeline(storyId, prompt, writingStyle, imageStyle, characterIds).catch((err) => {
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

  const failedCount = skipImages ? 0 : 16 - imageCount;

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
