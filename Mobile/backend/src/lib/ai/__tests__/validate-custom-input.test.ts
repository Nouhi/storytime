import { describe, it, expect, vi, beforeAll } from "vitest";

// Mock the db module so SQLite doesn't initialize during tests
vi.mock("../../db/index.js", () => ({
  db: {
    select: () => ({
      from: () => ({
        get: () => ({
          anthropicApiKey: process.env.ANTHROPIC_API_KEY || "",
        }),
      }),
    }),
  },
}));

import { validateCustomInput } from "../validate-custom-input.js";

// These are integration tests that call the real Anthropic API.
// They require ANTHROPIC_API_KEY to be set in the environment.
const API_KEY = process.env.ANTHROPIC_API_KEY;
const describeIfKey = API_KEY ? describe : describe.skip;

describeIfKey("validateCustomInput (live API)", () => {
  // Generous timeout — Haiku is fast but network can vary
  const TIMEOUT = 15_000;

  // ──────────────────────────────────────────────
  //  WRITING STYLE
  // ──────────────────────────────────────────────
  describe("writing style", () => {
    describe("valid inputs", () => {
      it.each([
        "spooky",
        "funny and silly",
        "Write in a calm, soothing bedtime tone",
        "Rhyming couplets like Dr. Seuss",
        "poetic",
        "adventurous",
        "mysterious and suspenseful with cliffhangers",
      ])("should accept: %s", async (input) => {
        const result = await validateCustomInput("writing style", input);
        expect(result).toBeNull();
      }, TIMEOUT);
    });

    describe("invalid inputs", () => {
      it.each([
        ["pizza recipe with extra cheese", "completely unrelated to writing"],
        ["SELECT * FROM users", "SQL injection / nonsense"],
        ["12345", "random numbers"],
        ["buy bitcoin now!!!", "spam / unrelated"],
      ])("should reject: %s (%s)", async (input, _reason) => {
        const result = await validateCustomInput("writing style", input);
        expect(result).not.toBeNull();
        expect(typeof result).toBe("string");
      }, TIMEOUT);
    });
  });

  // ──────────────────────────────────────────────
  //  IMAGE STYLE
  // ──────────────────────────────────────────────
  describe("image style", () => {
    describe("valid inputs", () => {
      it.each([
        "watercolor",
        "cartoon",
        "pixel art",
        "Dark gothic fairy tale illustrations",
        "Soft pastel crayon drawings like a child made them",
        "anime style with bright colors",
        "oil painting Renaissance style",
      ])("should accept: %s", async (input) => {
        const result = await validateCustomInput("image style", input);
        expect(result).toBeNull();
      }, TIMEOUT);
    });

    describe("invalid inputs", () => {
      it.each([
        ["how to cook pasta", "unrelated to art/images"],
        ["the weather is nice today", "random sentence"],
        ["hack the mainframe", "nonsense / harmful"],
        ["asdfghjkl", "keyboard mash"],
      ])("should reject: %s (%s)", async (input, _reason) => {
        const result = await validateCustomInput("image style", input);
        expect(result).not.toBeNull();
        expect(typeof result).toBe("string");
      }, TIMEOUT);
    });
  });

  // ──────────────────────────────────────────────
  //  LESSON
  // ──────────────────────────────────────────────
  describe("lesson", () => {
    describe("valid inputs", () => {
      it.each([
        "sharing",
        "kindness",
        "bravery",
        "Learning to be comfortable with trying new foods",
        "It's okay to make mistakes",
        "Being a good friend even when it's hard",
        "patience and perseverance",
        "telling the truth is important",
      ])("should accept: %s", async (input) => {
        const result = await validateCustomInput("lesson", input);
        expect(result).toBeNull();
      }, TIMEOUT);
    });

    describe("invalid inputs", () => {
      it.each([
        ["the mitochondria is the powerhouse of the cell", "science trivia, not a lesson"],
        ["www.google.com", "URL, not a lesson"],
        ["aaa bbb ccc ddd", "gibberish"],
        ["buy low sell high", "financial advice, not a children's lesson"],
      ])("should reject: %s (%s)", async (input, _reason) => {
        const result = await validateCustomInput("lesson", input);
        expect(result).not.toBeNull();
        expect(typeof result).toBe("string");
      }, TIMEOUT);
    });
  });

  // ──────────────────────────────────────────────
  //  CROSS-CATEGORY (wrong category for valid text)
  // ──────────────────────────────────────────────
  describe("cross-category rejection", () => {
    it("should reject a lesson when given as a writing style", async () => {
      const result = await validateCustomInput(
        "writing style",
        "Learning to share with your siblings",
      );
      // This is a lesson, not a writing style — should be flagged
      expect(result).not.toBeNull();
    }, TIMEOUT);

    it("should reject a writing style when given as an image style", async () => {
      const result = await validateCustomInput(
        "image style",
        "Write with lots of rhyming couplets and alliteration",
      );
      expect(result).not.toBeNull();
    }, TIMEOUT);

    it("should reject an image style when given as a lesson", async () => {
      const result = await validateCustomInput(
        "lesson",
        "Watercolor paintings with soft pastel colors",
      );
      expect(result).not.toBeNull();
    }, TIMEOUT);
  });

  // ──────────────────────────────────────────────
  //  EDGE CASES
  // ──────────────────────────────────────────────
  describe("edge cases", () => {
    it("should accept a single valid word for writing style", async () => {
      const result = await validateCustomInput("writing style", "funny");
      expect(result).toBeNull();
    }, TIMEOUT);

    it("should accept a single valid word for image style", async () => {
      const result = await validateCustomInput("image style", "gothic");
      expect(result).toBeNull();
    }, TIMEOUT);

    it("should accept a single valid word for lesson", async () => {
      const result = await validateCustomInput("lesson", "honesty");
      expect(result).toBeNull();
    }, TIMEOUT);

    it("should handle text with special characters gracefully", async () => {
      const result = await validateCustomInput(
        "writing style",
        "fun & playful — with lots of exclamation marks!",
      );
      expect(result).toBeNull();
    }, TIMEOUT);

    it("should handle emoji in input", async () => {
      const result = await validateCustomInput("image style", "dreamy and sparkly ✨");
      expect(result).toBeNull();
    }, TIMEOUT);
  });
});
