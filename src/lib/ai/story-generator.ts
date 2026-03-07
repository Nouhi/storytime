import Anthropic from "@anthropic-ai/sdk";
import { buildStorySystemPrompt } from "./prompts";
import type { FamilyMember, StoryPage, CharacterSheet } from "@/lib/types";
import { db } from "@/lib/db";
import { settings } from "@/lib/db/schema";

function getClient() {
  const row = db.select().from(settings).get();
  const apiKey = row?.anthropicApiKey || process.env.ANTHROPIC_API_KEY;
  if (!apiKey) throw new Error("Anthropic API key not configured. Add it in Settings.");
  return new Anthropic({ apiKey });
}

export interface StoryGenerationResult {
  pages: StoryPage[];
  characterSheet?: CharacterSheet;
  inputTokens: number;
  outputTokens: number;
}

export async function generateStory(
  prompt: string,
  context: {
    kidName: string;
    kidGender?: string;
    readingLevel: string;
    familyMembers: FamilyMember[];
  },
  writingStyle?: string,
): Promise<StoryGenerationResult> {
  const systemPrompt = buildStorySystemPrompt({ ...context, writingStyle });

  const message = await getClient().messages.create({
    model: "claude-sonnet-4-6",
    max_tokens: 8192,
    system: systemPrompt,
    messages: [
      {
        role: "user",
        content: `Create a bedtime story about: ${prompt}`,
      },
    ],
  });

  const text =
    message.content[0].type === "text" ? message.content[0].text : "";

  // Parse the JSON response — handle potential markdown wrapping
  let jsonStr = text.trim();
  if (jsonStr.startsWith("```")) {
    jsonStr = jsonStr.replace(/^```(?:json)?\n?/, "").replace(/\n?```$/, "");
  }

  const parsed = JSON.parse(jsonStr);

  // Support both new format { characterSheet, pages } and legacy plain array
  let pages: StoryPage[];
  let characterSheet: CharacterSheet | undefined;

  if (Array.isArray(parsed)) {
    // Legacy format: plain array of pages
    pages = parsed;
  } else if (parsed && Array.isArray(parsed.pages)) {
    // New format: object with characterSheet and pages
    pages = parsed.pages;
    characterSheet = parsed.characterSheet;
  } else {
    throw new Error("Unexpected response format: expected { characterSheet, pages } or array");
  }

  if (pages.length !== 16) {
    throw new Error(`Expected 16 pages, got ${pages.length}`);
  }

  return {
    pages,
    characterSheet,
    inputTokens: message.usage?.input_tokens ?? 0,
    outputTokens: message.usage?.output_tokens ?? 0,
  };
}
