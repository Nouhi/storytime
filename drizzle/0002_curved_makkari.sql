CREATE TABLE `story_history` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`title` text NOT NULL,
	`prompt` text NOT NULL,
	`created_at` text NOT NULL,
	`claude_input_tokens` integer DEFAULT 0 NOT NULL,
	`claude_output_tokens` integer DEFAULT 0 NOT NULL,
	`gemini_image_count` integer DEFAULT 0 NOT NULL,
	`claude_cost` real DEFAULT 0 NOT NULL,
	`gemini_cost` real DEFAULT 0 NOT NULL,
	`total_cost` real DEFAULT 0 NOT NULL,
	`pdf_path` text DEFAULT '' NOT NULL
);
--> statement-breakpoint
PRAGMA foreign_keys=OFF;--> statement-breakpoint
CREATE TABLE `__new_settings` (
	`id` integer PRIMARY KEY DEFAULT 1 NOT NULL,
	`kid_name` text DEFAULT '' NOT NULL,
	`reading_level` text DEFAULT 'early-reader' NOT NULL,
	`kid_photo_path` text DEFAULT '' NOT NULL,
	`anthropic_api_key` text DEFAULT '' NOT NULL,
	`google_ai_api_key` text DEFAULT '' NOT NULL,
	`updated_at` text DEFAULT '2026-03-06T04:01:43.073Z' NOT NULL
);
--> statement-breakpoint
INSERT INTO `__new_settings`("id", "kid_name", "reading_level", "kid_photo_path", "anthropic_api_key", "google_ai_api_key", "updated_at") SELECT "id", "kid_name", "reading_level", '', "anthropic_api_key", "google_ai_api_key", "updated_at" FROM `settings`;--> statement-breakpoint
DROP TABLE `settings`;--> statement-breakpoint
ALTER TABLE `__new_settings` RENAME TO `settings`;--> statement-breakpoint
PRAGMA foreign_keys=ON;