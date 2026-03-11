import Anthropic from "@anthropic-ai/sdk";
import { db } from "../db/index.js";
import { settings } from "../db/schema.js";

function getClient() {
  const row = db.select().from(settings).get();
  const apiKey = row?.anthropicApiKey || process.env.ANTHROPIC_API_KEY;
  if (!apiKey) throw new Error("Anthropic API key not configured. Add it in Settings.");
  return new Anthropic({ apiKey });
}

export type CustomInputCategory = "writing style" | "image style" | "lesson";

const CATEGORY_EXAMPLES: Record<CustomInputCategory, string> = {
  "writing style":
    "Examples of valid writing styles: 'Spooky and suspenseful with lots of mystery', 'Lyrical and poetic with beautiful imagery', 'Silly and absurd with tongue-twisters'.",
  "image style":
    "Examples of valid image styles: 'Dark gothic fairy tale illustrations with rich textures', 'Retro 1950s cartoon style with bold outlines', 'Soft pastel crayon drawings'.",
  lesson:
    "Examples of valid lessons: 'Learning to be comfortable with trying new foods', 'Understanding that it's okay to make mistakes', 'Being a good friend even when it's hard'.",
};

/**
 * Uses Claude Haiku to semantically validate whether a custom input
 * is appropriate for its category (writing style, image style, or lesson).
 *
 * @returns null if valid, or an error message string if invalid.
 */
export async function validateCustomInput(
  category: CustomInputCategory,
  text: string,
): Promise<string | null> {
  const client = getClient();

  const message = await client.messages.create({
    model: "claude-haiku-4",
    max_tokens: 256,
    system: `You are a validator for a children's bedtime story app. Your job is to check whether user-provided text is a valid description for a specific category. Be reasonably lenient — the user doesn't need to be an expert writer. But flag clearly irrelevant, nonsensical, harmful, or off-category inputs.

Respond with ONLY a JSON object: {"valid": true} or {"valid": false, "reason": "brief explanation"}`,
    messages: [
      {
        role: "user",
        content: `Category: ${category}
${CATEGORY_EXAMPLES[category]}

User input: "${text}"

Is this a valid ${category} description for a children's bedtime story? Check:
1. Is it actually describing a ${category} (not something completely unrelated)?
2. Is it appropriate for children's content?
3. Does it make enough sense to be usable?`,
      },
    ],
  });

  const responseText =
    message.content[0].type === "text" ? message.content[0].text : "";

  try {
    let jsonStr = responseText.trim();
    if (jsonStr.startsWith("```")) {
      jsonStr = jsonStr.replace(/^```(?:json)?\n?/, "").replace(/\n?```$/, "");
    }
    const result = JSON.parse(jsonStr);

    if (result.valid) {
      return null;
    }

    // Map category to user-friendly label
    const categoryLabel =
      category === "writing style"
        ? "writing style"
        : category === "image style"
          ? "image/art style"
          : "lesson or moral";

    return (
      result.reason ||
      `That doesn't seem to describe a ${categoryLabel}. Please try again with a clearer description.`
    );
  } catch {
    // If we can't parse the response, let it through rather than blocking
    console.error("[validate-custom-input] Failed to parse validation response:", responseText);
    return null;
  }
}
