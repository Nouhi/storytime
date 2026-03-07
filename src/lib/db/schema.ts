import { sqliteTable, text, integer, real } from "drizzle-orm/sqlite-core";

export const settings = sqliteTable("settings", {
  id: integer("id").primaryKey().default(1),
  kidName: text("kid_name").notNull().default(""),
  readingLevel: text("reading_level").notNull().default("early-reader"),
  kidGender: text("kid_gender").notNull().default(""),
  kidPhotoPath: text("kid_photo_path").notNull().default(""),
  anthropicApiKey: text("anthropic_api_key").notNull().default(""),
  googleAiApiKey: text("google_ai_api_key").notNull().default(""),
  updatedAt: text("updated_at")
    .notNull()
    .default(new Date().toISOString()),
});

export const familyMembers = sqliteTable("family_members", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  role: text("role").notNull(),
  photoPath: text("photo_path"),
});

export const storyHistory = sqliteTable("story_history", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  title: text("title").notNull(),
  prompt: text("prompt").notNull(),
  createdAt: text("created_at").notNull(),
  claudeInputTokens: integer("claude_input_tokens").notNull().default(0),
  claudeOutputTokens: integer("claude_output_tokens").notNull().default(0),
  geminiImageCount: integer("gemini_image_count").notNull().default(0),
  claudeCost: real("claude_cost").notNull().default(0),
  geminiCost: real("gemini_cost").notNull().default(0),
  totalCost: real("total_cost").notNull().default(0),
  pdfPath: text("pdf_path").notNull().default(""),
});
