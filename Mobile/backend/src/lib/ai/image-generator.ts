import { GoogleGenAI, type Content } from "@google/genai";
import { readPhotoAsBase64 } from "../storage.js";
import type { FamilyMember, CharacterSheet } from "../types.js";
import { db } from "../db/index.js";
import { settings } from "../db/schema.js";
import { IMAGE_STYLES, DEFAULT_IMAGE_STYLE } from "../styles.js";

function getAI() {
  const row = db.select().from(settings).get();
  const apiKey = row?.googleAiApiKey || process.env.GOOGLE_AI_API_KEY;
  if (!apiKey) throw new Error("Google AI API key not configured. Add it in Settings.");
  return new GoogleGenAI({ apiKey });
}

async function withRetry<T>(
  fn: () => Promise<T>,
  maxRetries: number = 3,
  baseDelay: number = 2000
): Promise<T> {
  let lastError: Error | undefined;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await fn();
    } catch (err: unknown) {
      lastError = err instanceof Error ? err : new Error(String(err));
      const statusMatch = String(err).match(/status["\s:]+(\d+)/i);
      const status = statusMatch ? parseInt(statusMatch[1]) : 0;

      if (status !== 429 && status !== 503) throw lastError;
      if (attempt === maxRetries) break;

      const retryMatch = String(err).match(/retryDelay["\s:]+(\d+)/);
      const retrySeconds = retryMatch ? parseInt(retryMatch[1]) : 0;
      const delay = retrySeconds > 0
        ? retrySeconds * 1000 + Math.random() * 1000
        : baseDelay * Math.pow(2, attempt) + Math.random() * 1000;

      console.log(`Rate limited on attempt ${attempt + 1}, retrying in ${Math.round(delay / 1000)}s...`);
      await new Promise((r) => setTimeout(r, delay));
    }
  }
  throw lastError!;
}

function getImageStylePrompt(imageStyleId?: string): string {
  const styleId = imageStyleId || DEFAULT_IMAGE_STYLE;
  const style = IMAGE_STYLES.find((s) => s.id === styleId);
  if (style) return style.prompt;
  // If no matching style found, treat the ID as a custom image style prompt
  return styleId;
}

function formatCharacterSheet(sheet: CharacterSheet): string {
  const lines: string[] = [];

  const mc = sheet.mainCharacter;
  if (mc) {
    lines.push(`Main character (${mc.name}): Clothing: ${mc.clothing}. Accessories: ${mc.accessories}.`);
  }

  for (const sc of sheet.supportingCharacters || []) {
    lines.push(`${sc.name}: Clothing: ${sc.clothing}. Accessories: ${sc.accessories}.`);
  }

  for (const [item, desc] of Object.entries(sheet.recurringElements || {})) {
    lines.push(`${item}: ${desc}`);
  }

  return lines.join("\n");
}

export async function generateIllustration(
  imageDescription: string,
  pageNumber: number,
  familyMembers: FamilyMember[],
  kidName?: string,
  kidPhotoPath?: string,
  imageStyleId?: string,
  characterSheet?: CharacterSheet,
  kidGender?: string,
): Promise<Buffer> {
  const photoRefs: { name: string; role: string; data: string; mimeType: string }[] = [];

  if (kidPhotoPath && kidName) {
    const kidPhoto = await readPhotoAsBase64(kidPhotoPath);
    if (kidPhoto) {
      photoRefs.push({
        name: kidName,
        role: "main character",
        ...kidPhoto,
      });
    }
  }

  for (const member of familyMembers) {
    if (!member.photoPath) continue;
    const nameLower = member.name.toLowerCase();
    const roleLower = member.role.toLowerCase();
    if (
      imageDescription.toLowerCase().includes(nameLower) ||
      imageDescription.toLowerCase().includes(roleLower)
    ) {
      const photo = await readPhotoAsBase64(member.photoPath);
      if (photo) {
        photoRefs.push({
          name: member.name,
          role: member.role,
          ...photo,
        });
      }
    }
  }

  const parts: Content["parts"] = [];

  for (const ref of photoRefs) {
    const genderHint = ref.role === "main character" && kidGender
      ? ` (${kidGender})`
      : "";
    parts.push({
      text: ref.role === "main character"
        ? `Reference photo of ${ref.name}${genderHint} — the MAIN CHARACTER of this story. This child MUST appear in every illustration and should closely resemble this photo.`
        : `Reference photo of ${ref.name} (${ref.role}). The illustrated character should resemble this person's appearance.`,
    });
    parts.push({
      inlineData: {
        mimeType: ref.mimeType,
        data: ref.data,
      },
    });
  }

  const stylePrompt = getImageStylePrompt(imageStyleId);

  const pageContext =
    pageNumber === 1
      ? "This is a TITLE PAGE. Create a beautiful cover illustration."
      : pageNumber === 16
        ? "This is the FINAL PAGE. Create a warm, cozy closing illustration."
        : "Create a children's book illustration.";

  const characterContext = characterSheet
    ? `\nCHARACTER REFERENCE (maintain these EXACT details in every illustration):\n${formatCharacterSheet(characterSheet)}\n`
    : "";

  parts.push({
    text: `${pageContext}
${characterContext}
Scene: ${imageDescription}

Style: ${stylePrompt}

IMPORTANT: The illustration must be FULL-BLEED — the artwork should extend to all edges of the image with NO white borders, NO margins, and NO empty space around the edges. Fill the ENTIRE canvas with the scene. Use a landscape aspect ratio.
${photoRefs.length > 0 ? "\nIMPORTANT: Characters should resemble the reference photos provided above." : ""}
No text or words in the image.`,
  });

  return withRetry(async () => {
    const response = await getAI().models.generateContent({
      model: "gemini-2.5-flash-image",
      contents: [{ role: "user", parts }],
      config: {
        responseModalities: ["IMAGE", "TEXT"],
      },
    });

    if (response.candidates?.[0]?.content?.parts) {
      for (const part of response.candidates[0].content.parts) {
        if (part.inlineData?.data) {
          return Buffer.from(part.inlineData.data, "base64");
        }
      }
    }

    throw new Error(`No image generated for page ${pageNumber}`);
  });
}

export async function generateAllIllustrations(
  pages: { page: number; imageDescription: string }[],
  familyMembers: FamilyMember[],
  onProgress: (completed: number, total: number) => void,
  kidName?: string,
  kidPhotoPath?: string,
  imageStyleId?: string,
  characterSheet?: CharacterSheet,
  kidGender?: string,
): Promise<Map<number, Buffer>> {
  const results = new Map<number, Buffer>();
  const concurrency = 1;
  let completed = 0;

  for (let i = 0; i < pages.length; i += concurrency) {
    const batch = pages.slice(i, i + concurrency);
    const batchResults = await Promise.allSettled(
      batch.map(async (page) => {
        const buffer = await generateIllustration(
          page.imageDescription,
          page.page,
          familyMembers,
          kidName,
          kidPhotoPath,
          imageStyleId,
          characterSheet,
          kidGender,
        );
        return { page: page.page, buffer };
      })
    );

    for (const result of batchResults) {
      completed++;
      if (result.status === "fulfilled") {
        results.set(result.value.page, result.value.buffer);
      } else {
        console.error(
          `Failed to generate image for page ${batch[batchResults.indexOf(result)]?.page}:`,
          result.reason
        );
      }
      onProgress(completed, pages.length);
    }
  }

  return results;
}
