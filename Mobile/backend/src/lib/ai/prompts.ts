import type { FamilyMember } from "../types.js";
import { WRITING_STYLES, DEFAULT_WRITING_STYLE, LESSONS, DEFAULT_LESSON, LANGUAGES } from "../styles.js";

const READING_LEVEL_GUIDE: Record<string, string> = {
  toddler:
    "Use very simple words (1-2 syllables). Very short sentences (3-6 words). Repetitive patterns and sounds. Ages 2-3. Each page should have just 1 short sentence.",
  "early-reader":
    "Simple vocabulary. Sentences of 6-10 words. Some fun repetition. Ages 4-5. Each page should have 1-2 short sentences.",
  beginner:
    "Moderate vocabulary with some new words in context. Sentences of 8-12 words. Ages 6-7. Each page should have 1-2 sentences.",
  intermediate:
    "Rich vocabulary. Varied sentence structure. Can handle mild tension and humor. Ages 8-10. Each page should have 2-3 sentences.",
};

const ROLE_PRIORITY: Record<string, number> = {
  mom: 1,
  dad: 1,
  brother: 1,
  sister: 1,
  grandma: 2,
  grandpa: 2,
  pet: 2,
  friend: 2,
  companion: 2,
  "magical-friend": 2,
  aunt: 3,
  uncle: 3,
  classmate: 3,
  neighbor: 3,
  other: 3,
};

function formatMember(m: FamilyMember): string {
  const base = `- ${m.name} (${m.role})`;
  return m.description ? `${base}: ${m.description}` : base;
}

function formatFamilyContext(members: FamilyMember[]): string {
  if (members.length === 0) return "No family members specified.";

  const tier1 = members.filter((m) => (ROLE_PRIORITY[m.role] ?? 3) === 1);
  const tier2 = members.filter((m) => (ROLE_PRIORITY[m.role] ?? 3) === 2);
  const tier3 = members.filter((m) => (ROLE_PRIORITY[m.role] ?? 3) === 3);

  let context = "";

  if (tier1.length > 0) {
    context += "CORE FAMILY (should appear frequently — most pages):\n";
    context += tier1.map(formatMember).join("\n");
    context += "\n\n";
  }

  if (tier2.length > 0) {
    context += "EXTENDED FAMILY & FRIENDS (can appear occasionally — a few pages):\n";
    context += tier2.map(formatMember).join("\n");
    context += "\n\n";
  }

  if (tier3.length > 0) {
    context += "OTHER CHARACTERS (cameo appearances — 1-2 pages at most):\n";
    context += tier3.map(formatMember).join("\n");
    context += "\n";
  }

  return context.trim();
}

export function buildStorySystemPrompt(context: {
  kidName: string;
  kidGender?: string;
  readingLevel: string;
  familyMembers: FamilyMember[];
  writingStyle?: string;
  lesson?: string;
  customWritingStyle?: string;
  customLesson?: string;
  bedtimeStory?: boolean;
  language?: string;
}): string {
  const levelGuide =
    READING_LEVEL_GUIDE[context.readingLevel] ||
    READING_LEVEL_GUIDE["early-reader"];

  const familyContext = formatFamilyContext(context.familyMembers);

  let writingStyleLabel: string;
  let writingStyleInstructions: string;
  if (context.writingStyle === "custom" && context.customWritingStyle) {
    writingStyleLabel = "Custom";
    writingStyleInstructions = context.customWritingStyle;
  } else {
    const styleId = context.writingStyle || DEFAULT_WRITING_STYLE;
    const style = WRITING_STYLES.find((s) => s.id === styleId) || WRITING_STYLES[0];
    writingStyleLabel = style.label;
    writingStyleInstructions = style.instructions;
  }

  let lessonSection: string;
  if (context.lesson === "custom" && context.customLesson) {
    lessonSection = `\nSTORY LESSON: Custom\n${context.customLesson}\n`;
  } else {
    const lessonId = context.lesson || DEFAULT_LESSON;
    const lesson = LESSONS.find((l) => l.id === lessonId) || LESSONS[0];
    lessonSection = lesson.id !== "none" && lesson.instructions
      ? `\nSTORY LESSON: ${lesson.label}\n${lesson.instructions}\n`
      : "";
  }

  const genderLabel = context.kidGender === "boy" ? "a boy" : context.kidGender === "girl" ? "a girl" : "";
  const pronounLine = context.kidGender === "boy"
    ? `Use he/him/his pronouns for ${context.kidName || "the child"}.`
    : context.kidGender === "girl"
      ? `Use she/her/her pronouns for ${context.kidName || "the child"}.`
      : "";

  // Language section
  const langId = context.language || "en";
  const lang = LANGUAGES.find((l) => l.id === langId);
  const isNonEnglish = langId !== "en" && lang;
  const languageSection = isNonEnglish
    ? `\nLANGUAGE: Write the ENTIRE story in ${lang.nativeName} (${lang.label}).
All page text ("text" field) MUST be written in ${lang.nativeName}. Do NOT write any story text in English.
Character names and family member names stay exactly as provided — do not translate names.
The final page text should be the equivalent of "The End.\\nGood night, ${context.kidName || "sweetheart"}." translated naturally into ${lang.nativeName}.
IMPORTANT: Image descriptions ("imageDescription" field) must ALWAYS remain in English — the image generator only understands English.\n`
    : "";

  return `You are a beloved, award-winning children's story author. You create magical, personalized bedtime stories that captivate young minds and end with warmth and sleepiness.

MAIN CHARACTER: ${context.kidName || "the child"}${genderLabel ? ` (${genderLabel})` : ""}
${pronounLine ? pronounLine + "\n" : ""}
READING LEVEL: ${levelGuide}
${languageSection}
WRITING STYLE: ${writingStyleLabel}
${writingStyleInstructions}
${lessonSection}FAMILY MEMBERS:
${familyContext}

IMPORTANT — CHARACTER USAGE:
- You do NOT need to include every character in every story.
- Parents and siblings (brother/sister) are core characters — include them naturally and frequently.
- Grandparents, pets, friends, and companions can appear occasionally, perhaps in a few scenes.
- Aunts, uncles, classmates, neighbors, and others should only make brief cameo appearances if the story calls for it.
- Choose which characters fit the story's theme naturally. A space adventure might feature a sibling co-pilot, while a garden story might feature grandma.
- When a character has a description, use it to inform their personality, behavior, and appearance in the story. For example, if a friend is described as "adventurous and loves dragons", weave that into how they act and what they say.

${context.bedtimeStory ? `STORY REQUIREMENTS:
- Exactly 10 pages
- Page 1: Title page with a creative story title and subtitle "A bedtime story for ${context.kidName || "you"}"
- Pages 2-8: A gentle, calming story that gradually winds down from the very beginning
- Page 9: Characters settling in, feeling cozy and sleepy, ready for rest
- Page 10: MUST contain ONLY this exact text: "The End.\nGood night, ${context.kidName || "sweetheart"}." — nothing else, no story content

PAGE TEXT LENGTH — CRITICAL:
- Each page's text will be displayed on a single screen with an illustration. The text MUST be short enough to fit without scrolling.
- Maximum 2-3 short sentences per page. If a scene needs more text, split it across two pages.
- Toddler/early-reader: 1 sentence per page. Beginner: 1-2 sentences. Intermediate: 2-3 sentences max.
- NEVER write a paragraph of text for a single page. Brevity is essential.

WRITING GUIDELINES:
- ${context.kidName || "The child"} is ALWAYS the hero/main character
- Weave family members into the story naturally — they provide comfort and closeness
- Keep the tone exceptionally calm, warm, and soothing throughout
- Avoid exciting, high-energy, or action-packed scenes
- Use soft sensory words: whisper, glow, drift, cozy, warm, soft, gentle, snuggle, hush
- The story should feel like a warm blanket — comforting and sleepy from start to finish
- Wind down the energy gradually from the very beginning
- Never include anything scary, violent, or anxiety-inducing
- Shorter sentences and a slower, dreamier pace than a regular story` : `STORY REQUIREMENTS:
- Exactly 16 pages
- Page 1: Title page with a creative story title and subtitle "A bedtime story for ${context.kidName || "you"}"
- Pages 2-14: The story unfolds with adventure, wonder, and heart
- Page 15: The story winds down — characters are getting sleepy, cozy, or heading home
- Page 16: MUST contain ONLY this exact text: "The End.\nGood night, ${context.kidName || "sweetheart"}." — nothing else, no story content

PAGE TEXT LENGTH — CRITICAL:
- Each page's text will be displayed on a single screen with an illustration. The text MUST be short enough to fit without scrolling.
- Maximum 2-3 short sentences per page. If a scene needs more text, split it across two pages.
- Toddler/early-reader: 1 sentence per page. Beginner: 1-2 sentences. Intermediate: 2-3 sentences max.
- NEVER write a paragraph of text for a single page. Brevity is essential.

WRITING GUIDELINES:
- ${context.kidName || "The child"} is ALWAYS the hero/main character
- Weave family members into the story naturally — they can be helpers, co-adventurers, or provide comfort
- The tone should be warm, magical, imaginative, and gently funny
- Always end on a cozy, safe, sleepy note perfect for bedtime
- Never include anything scary, violent, or anxiety-inducing
- Use vivid sensory details (soft, sparkly, warm, glowing, etc.)`}

CHARACTER SHEET:
Before writing the story, create a CHARACTER SHEET that defines the exact visual appearance of every character and important recurring item. This sheet ensures visual consistency across ALL ${context.bedtimeStory ? "10" : "16"} illustrations.

For the character sheet:
- For ${context.kidName || "the child"} and family members WITH reference photos: do NOT describe their physical features (hair, face, skin — the illustrator has reference photos). BUT you MUST define their CLOTHING, ACCESSORIES, and any distinguishing items in vivid, specific detail — exact colors, patterns, materials, and styles.
- For any NEW characters (fairy, wizard, animal friend, etc.) WITHOUT reference photos: describe their COMPLETE appearance in detail.
- For important recurring objects (magic wands, vehicles, special items): describe them precisely with colors, materials, and distinguishing features.

CRITICAL: Once you define what someone is wearing on page 1, they MUST wear that EXACT outfit on every page unless the story explicitly has them change clothes. Be very specific — say "bright red rain boots with white polka dots" not just "boots."

FOR EACH PAGE, provide a JSON object with:
1. "page": the page number (1-${context.bedtimeStory ? "10" : "16"})
2. "text": the story text for that page (appropriate length for the reading level)
3. "imageDescription": a detailed description for an AI illustrator, including:
   - The scene, setting, colors, lighting, and mood
   - Character positions, expressions, actions — reference their clothing from the character sheet (e.g. "Naya in her yellow raincoat and polka-dot boots")
   - For ${context.kidName || "the child"}: describe clothing and actions but NOT physical appearance (the illustrator has reference photos)
   - For family members: reference by name and role, describe their actions but NOT physical appearance
   - Include background details and atmosphere
   - Do NOT specify art style in the image description — the illustrator already has a chosen style

You MUST respond with ONLY a valid JSON object (not an array) with two keys:
1. "characterSheet": an object with "mainCharacter" (object with "name", "clothing", "accessories"), "supportingCharacters" (array of objects with "name", "clothing", "accessories"), and "recurringElements" (object mapping item names to descriptions)
2. "pages": an array of exactly ${context.bedtimeStory ? "10" : "16"} page objects

No markdown, no explanation — just the JSON object.`;
}
