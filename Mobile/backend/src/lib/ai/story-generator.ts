import Anthropic from "@anthropic-ai/sdk";
import { buildStorySystemPrompt } from "./prompts.js";
import type { FamilyMember, StoryPage, CharacterSheet } from "../types.js";
import { db } from "../db/index.js";
import { settings } from "../db/schema.js";

function getClient() {
  const row = db.select().from(settings).get();
  const apiKey = row?.anthropicApiKey || process.env.ANTHROPIC_API_KEY;
  if (!apiKey) throw new Error("Anthropic API key not configured. Add it in Settings.");
  return new Anthropic({ apiKey });
}

/**
 * Attempt to repair truncated or malformed JSON from LLM output.
 * Handles: truncated strings, missing closing brackets/braces,
 * trailing commas, and incomplete key-value pairs.
 */
function repairJSON(input: string): string {
  let s = input.trim();

  // Remove trailing comma before we close things
  s = s.replace(/,\s*$/, "");

  // If we're inside an unterminated string, close it
  // Count unescaped quotes to see if we have an odd number
  let inString = false;
  let lastQuoteIndex = -1;
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '"' && (i === 0 || s[i - 1] !== "\\")) {
      inString = !inString;
      lastQuoteIndex = i;
    }
  }
  if (inString) {
    // Truncated inside a string — close it
    s += '"';
  }

  // Remove any trailing incomplete key-value pair like `"foo": ` or `"foo"`
  // that doesn't have a value yet (after the last complete value)
  s = s.replace(/,\s*"[^"]*"\s*:\s*$/, "");
  s = s.replace(/,\s*"[^"]*"\s*$/, "");
  s = s.replace(/,\s*$/, "");

  // Count open/close braces and brackets
  let openBraces = 0;
  let openBrackets = 0;
  inString = false;
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '"' && (i === 0 || s[i - 1] !== "\\")) {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (s[i] === "{") openBraces++;
    if (s[i] === "}") openBraces--;
    if (s[i] === "[") openBrackets++;
    if (s[i] === "]") openBrackets--;
  }

  // Close any unclosed brackets/braces
  // Remove trailing commas before closing
  s = s.replace(/,\s*$/, "");
  while (openBrackets > 0) {
    s += "]";
    openBrackets--;
  }
  s = s.replace(/,\s*\]/, "]");
  while (openBraces > 0) {
    s += "}";
    openBraces--;
  }
  s = s.replace(/,\s*\}/, "}");

  return s;
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
  lesson?: string,
): Promise<StoryGenerationResult> {
  const systemPrompt = buildStorySystemPrompt({ ...context, writingStyle, lesson });
  const client = getClient();

  const MAX_ATTEMPTS = 2;
  let totalInputTokens = 0;
  let totalOutputTokens = 0;
  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    const message = await client.messages.create({
      model: "claude-sonnet-4-6",
      max_tokens: 16384,
      system: systemPrompt,
      messages: [
        {
          role: "user",
          content: `Create a bedtime story about: ${prompt}`,
        },
      ],
    });

    totalInputTokens += message.usage?.input_tokens ?? 0;
    totalOutputTokens += message.usage?.output_tokens ?? 0;

    const text =
      message.content[0].type === "text" ? message.content[0].text : "";

    let jsonStr = text.trim();
    if (jsonStr.startsWith("```")) {
      jsonStr = jsonStr.replace(/^```(?:json)?\n?/, "").replace(/\n?```$/, "");
    }

    // Check if response was truncated (hit max_tokens)
    const wasTruncated = message.stop_reason === "max_tokens";

    try {
      const parsed = JSON.parse(jsonStr);
      return extractResult(parsed, totalInputTokens, totalOutputTokens);
    } catch (parseError) {
      // Try repairing the JSON (common with truncated responses)
      try {
        const repaired = repairJSON(jsonStr);
        const parsed = JSON.parse(repaired);
        const result = extractResult(parsed, totalInputTokens, totalOutputTokens);
        console.log(
          `[story-generator] JSON repaired successfully${wasTruncated ? " (response was truncated)" : ""}`
        );
        return result;
      } catch {
        lastError = parseError instanceof Error ? parseError : new Error(String(parseError));
        console.error(
          `[story-generator] Attempt ${attempt}/${MAX_ATTEMPTS} failed: ${lastError.message}${wasTruncated ? " (response was truncated at max_tokens)" : ""}`
        );
        if (attempt < MAX_ATTEMPTS) {
          console.log("[story-generator] Retrying generation...");
        }
      }
    }
  }

  throw new Error(
    `Failed to parse story JSON after ${MAX_ATTEMPTS} attempts: ${lastError?.message}`
  );
}

function extractResult(
  parsed: any,
  inputTokens: number,
  outputTokens: number,
): StoryGenerationResult {
  let pages: StoryPage[];
  let characterSheet: CharacterSheet | undefined;

  if (Array.isArray(parsed)) {
    pages = parsed;
  } else if (parsed && Array.isArray(parsed.pages)) {
    pages = parsed.pages;
    characterSheet = parsed.characterSheet;
  } else {
    throw new Error("Unexpected response format: expected { characterSheet, pages } or array");
  }

  if (pages.length < 14) {
    throw new Error(`Expected 16 pages, got ${pages.length}`);
  }

  // If we got 14-15 pages (truncated), pad with placeholder ending
  if (pages.length < 16) {
    console.log(
      `[story-generator] Got ${pages.length}/16 pages, padding with ending`
    );
    while (pages.length < 15) {
      pages.push({
        page: pages.length + 1,
        text: "And the adventure continued, filling hearts with joy...",
        imageDescription:
          "A warm, cozy scene with soft golden light and happy characters.",
      });
    }
    if (pages.length === 15) {
      pages.push({
        page: 16,
        text: "And so, with happy memories and sleepy smiles, it was time for bed. Sweet dreams... The End.",
        imageDescription:
          "A peaceful bedroom scene with soft moonlight, cozy blankets, and a sleeping child with a gentle smile.",
      });
    }
  }

  return {
    pages,
    characterSheet,
    inputTokens,
    outputTokens,
  };
}
