CREATE TABLE `family_members` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`name` text NOT NULL,
	`role` text NOT NULL,
	`photo_path` text
);
--> statement-breakpoint
CREATE TABLE `settings` (
	`id` integer PRIMARY KEY DEFAULT 1 NOT NULL,
	`kid_name` text DEFAULT '' NOT NULL,
	`reading_level` text DEFAULT 'early-reader' NOT NULL,
	`updated_at` text DEFAULT '2026-02-21T23:25:55.584Z' NOT NULL
);
