export interface WritingStyle {
  id: string;
  label: string;
  emoji: string;
  description: string;
  instructions: string;
}

export interface ImageStyle {
  id: string;
  label: string;
  emoji: string;
  description: string;
  prompt: string;
}

export const WRITING_STYLES: WritingStyle[] = [
  {
    id: "standard",
    label: "Standard",
    emoji: "📖",
    description: "Classic bedtime story narration",
    instructions: "Write in a warm, engaging narrative style with vivid descriptions and gentle pacing. Use a mix of dialogue and narration.",
  },
  {
    id: "rhyming",
    label: "Rhyming",
    emoji: "🎵",
    description: "Dr. Seuss-style rhyming verse",
    instructions: "Write the entire story in rhyming verse with a consistent meter and rhythm. Use playful, bouncy rhyme schemes (AABB or ABAB). Think Dr. Seuss — fun, rhythmic, and easy to read aloud. Keep rhymes natural, never forced.",
  },
  {
    id: "funny",
    label: "Funny",
    emoji: "😂",
    description: "Silly humor and unexpected twists",
    instructions: "Make the story genuinely funny with silly situations, unexpected twists, playful exaggeration, and kid-friendly humor. Include funny dialogue, absurd scenarios, and moments that will make kids giggle. Think Mo Willems or Captain Underpants energy (age-appropriate).",
  },
  {
    id: "sound-effects",
    label: "Sound Effects",
    emoji: "💥",
    description: "Onomatopoeia and interactive sounds",
    instructions: "Fill the story with fun sound effects and onomatopoeia (WHOOSH! SPLAT! DING-DONG! CRUNCH!). Write in a way that's interactive and fun to read aloud, with big expressive words that parents can act out. Make sound effects integral to the story, not just decoration.",
  },
  {
    id: "repetitive",
    label: "Repetitive",
    emoji: "🔁",
    description: "Cumulative story with repeating phrases",
    instructions: "Use a cumulative, repetitive structure like 'Brown Bear, Brown Bear' or 'If You Give a Mouse a Cookie.' Include a catchy repeating phrase or pattern that builds and grows throughout the story. Kids love predicting what comes next.",
  },
  {
    id: "bedtime",
    label: "Bedtime",
    emoji: "🌙",
    description: "Extra calm and dreamy for sleepy time",
    instructions: "Write an exceptionally calm, soothing story designed to help kids fall asleep. Use gentle, dreamy language with lots of soft sensory words (whisper, shimmer, float, cozy, warm). Slow the pacing as the story progresses. The world should get quieter and more peaceful with each page. End with everyone peacefully sleeping.",
  },
  {
    id: "adventure",
    label: "Adventure",
    emoji: "⚔️",
    description: "Epic quests and brave heroes",
    instructions: "Write an exciting adventure story with brave heroes, daring quests, and thrilling (but never scary) challenges. Include moments of courage, clever problem-solving, and triumph. Build excitement with cliffhangers between pages. Still end on a warm, cozy note for bedtime.",
  },
];

export const IMAGE_STYLES: ImageStyle[] = [
  {
    id: "watercolor",
    label: "Watercolor",
    emoji: "🎨",
    description: "Soft, dreamy watercolor paintings",
    prompt: "Warm, soft watercolor children's book illustration. Gentle rounded shapes, dreamy lighting, pastel and warm color palette. The art should feel cozy, magical, and perfect for a bedtime story. Characters should have soft, friendly features. Delicate brush strokes with color bleeding naturally at the edges.",
  },
  {
    id: "fantasy",
    label: "Fantasy",
    emoji: "🧙",
    description: "Rich, magical fantasy art",
    prompt: "Rich, detailed fantasy children's book illustration with magical lighting and ethereal atmosphere. Glowing elements, enchanted forests, mystical creatures. Vibrant but warm color palette with dramatic yet gentle lighting. Think classic fantasy picture book art — detailed but not overwhelming.",
  },
  {
    id: "realistic",
    label: "Realistic",
    emoji: "📷",
    description: "Photo-realistic digital art",
    prompt: "Highly realistic digital illustration with warm, cinematic lighting. Photo-realistic characters and environments with a slight storybook softness. Rich textures, natural lighting, and lifelike details. Warm color grading like a beautifully photographed children's film.",
  },
  {
    id: "cartoon",
    label: "Cartoon",
    emoji: "🦸",
    description: "Bold, colorful cartoon style",
    prompt: "Bold, colorful cartoon illustration with clean outlines and expressive characters. Bright, saturated colors with dynamic compositions. Characters have large, expressive eyes and exaggerated features. Think modern animated movie style — appealing, clean, and full of personality.",
  },
  {
    id: "classic-storybook",
    label: "Classic Storybook",
    emoji: "📚",
    description: "Vintage children's book illustrations",
    prompt: "Classic vintage children's book illustration style reminiscent of Beatrix Potter or Ernest Shepard. Delicate pen-and-ink lines with soft watercolor washes. Gentle, nostalgic feel with muted earth tones and pastoral settings. Fine cross-hatching details with an old-fashioned, timeless charm.",
  },
  {
    id: "anime",
    label: "Anime",
    emoji: "✨",
    description: "Japanese anime-inspired art",
    prompt: "Beautiful anime-style children's illustration with large expressive eyes, detailed backgrounds, and vibrant colors. Soft cel-shading with delicate line work. Sparkles, soft glows, and dreamy atmospheric effects. Think gentle slice-of-life anime aesthetic, warm and inviting.",
  },
  {
    id: "ghibli",
    label: "Ghibli",
    emoji: "🏔️",
    description: "Studio Ghibli-inspired art",
    prompt: "Studio Ghibli-inspired illustration with lush, painterly backgrounds and warm, rounded character designs. Rich natural environments with incredible detail — rolling hills, puffy clouds, dappled sunlight through trees. Soft, luminous color palette with a sense of wonder and gentle magic. Characters have simple, expressive features.",
  },
  {
    id: "chibi",
    label: "Chibi",
    emoji: "🎀",
    description: "Cute, super-deformed chibi art",
    prompt: "Adorable chibi-style illustration with super-deformed proportions — large heads, tiny bodies, and enormous sparkly eyes. Bright, candy-colored palette with cute decorative elements (stars, hearts, sparkles). Characters are irresistibly cute with exaggerated expressions. Kawaii aesthetic with clean, bubbly art style.",
  },
  {
    id: "papercraft",
    label: "Papercraft",
    emoji: "✂️",
    description: "Cut-paper collage style",
    prompt: "Beautiful papercraft and cut-paper collage illustration style. Layered paper textures with visible edges and subtle shadows between layers. Rich, textured colors that look like carefully cut and arranged paper. Think Eric Carle's textured collage style — tactile, colorful, and dimensional.",
  },
  {
    id: "pixel",
    label: "Pixel Art",
    emoji: "👾",
    description: "Retro pixel art style",
    prompt: "Charming retro pixel art illustration with a warm, modern twist. Clean pixel work with a limited but carefully chosen color palette. Detailed pixel environments with cozy lighting effects. Think modern indie game art — nostalgic but polished, with beautiful pixel-level detail.",
  },
  {
    id: "minimalist",
    label: "Minimalist",
    emoji: "⚪",
    description: "Clean, simple geometric shapes",
    prompt: "Clean, minimalist children's illustration with simple geometric shapes and a limited color palette. Bold, flat colors with plenty of white space. Characters are simplified to their essential shapes. Think Scandinavian design meets children's book art — elegant, modern, and uncluttered.",
  },
  {
    id: "crayon",
    label: "Crayon",
    emoji: "🖍️",
    description: "Child-like crayon and colored pencil",
    prompt: "Charming crayon and colored pencil illustration that looks like it was drawn by a talented child. Visible crayon textures, slightly wobbly lines, and vibrant but natural coloring. Warm, naive art style that feels authentic and endearing. Think Harold and the Purple Crayon — simple, expressive, and full of imagination.",
  },
  {
    id: "pop-art",
    label: "Pop Art",
    emoji: "🎪",
    description: "Bold pop art with halftone dots",
    prompt: "Bold, colorful pop art-inspired children's illustration with halftone dots, strong outlines, and vibrant contrasting colors. Dynamic compositions with comic-book energy. Ben-Day dots, bold typography elements, and graphic shapes. Fun, energetic, and visually striking.",
  },
  {
    id: "oil-painting",
    label: "Oil Painting",
    emoji: "🖼️",
    description: "Rich, textured oil painting style",
    prompt: "Rich oil painting-style children's illustration with visible brushstrokes and thick, textured paint application. Warm, glowing colors with dramatic but gentle lighting. Impressionistic background details with more refined character focus. Think a children's book painted by a classical artist — warm, rich, and luminous.",
  },
  {
    id: "none",
    label: "No Images",
    emoji: "📝",
    description: "Text-only story, no illustrations",
    prompt: "",
  },
];

export const DEFAULT_WRITING_STYLE = "standard";
export const DEFAULT_IMAGE_STYLE = "watercolor";
