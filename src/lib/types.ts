export interface Settings {
  id: number;
  kidName: string;
  readingLevel: string;
  kidPhotoPath: string;
  anthropicApiKey: string;
  googleAiApiKey: string;
  updatedAt: string;
}

export interface FamilyMember {
  id: number;
  name: string;
  role: string;
  photoPath: string | null;
}

export interface StoryPage {
  page: number;
  text: string;
  imageDescription: string;
}

export interface CharacterAppearance {
  name: string;
  clothing: string;
  accessories: string;
}

export interface CharacterSheet {
  mainCharacter: CharacterAppearance;
  supportingCharacters: CharacterAppearance[];
  recurringElements: Record<string, string>;
}

export interface GenerationEvent {
  type: "progress" | "complete" | "error";
  step?: string;
  detail?: string;
  progress?: number;
  epubUrl?: string;
  storyId?: string;
  storyPages?: StoryPage[];
  message?: string;
}

export interface StoryHistoryEntry {
  id: number;
  title: string;
  prompt: string;
  createdAt: string;
  claudeInputTokens: number;
  claudeOutputTokens: number;
  geminiImageCount: number;
  claudeCost: number;
  geminiCost: number;
  totalCost: number;
  pdfPath: string;
}
