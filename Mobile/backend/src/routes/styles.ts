import { Router } from "express";
import { WRITING_STYLES, IMAGE_STYLES, LESSONS, LANGUAGES, DEFAULT_WRITING_STYLE, DEFAULT_IMAGE_STYLE, DEFAULT_LESSON, DEFAULT_LANGUAGE } from "../lib/styles.js";

const router = Router();

router.get("/", (_req, res) => {
  res.json({
    writingStyles: WRITING_STYLES.map(({ instructions, ...rest }) => rest),
    imageStyles: IMAGE_STYLES.map(({ prompt, ...rest }) => rest),
    lessons: LESSONS.map(({ instructions, ...rest }) => rest),
    languages: LANGUAGES.map(({ nativeName, ...rest }) => ({ ...rest, description: nativeName })),
    defaults: {
      writingStyle: DEFAULT_WRITING_STYLE,
      imageStyle: DEFAULT_IMAGE_STYLE,
      lesson: DEFAULT_LESSON,
      language: DEFAULT_LANGUAGE,
    },
  });
});

export default router;
