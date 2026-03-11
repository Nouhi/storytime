import express from "express";
import cors from "cors";
import { initializeDatabase } from "./lib/db/index.js";

import settingsRouter from "./routes/settings.js";
import uploadRouter from "./routes/upload.js";
import photosRouter from "./routes/photos.js";
import stylesRouter from "./routes/styles.js";
import generateRouter from "./routes/generate.js";
import streamRouter from "./routes/stream.js";
import epubRouter from "./routes/epub.js";
import pagesRouter from "./routes/pages.js";
import historyRouter from "./routes/history.js";
import familyMembersRouter from "./routes/family-members.js";
import { generatePdfRouter, historyPdfRouter } from "./routes/pdf.js";

const app = express();
const PORT = parseInt(process.env.PORT || "3002", 10);

// Middleware
app.use(cors());
app.use(express.json());

// Initialize database tables
initializeDatabase();

// Routes
app.use("/api/settings", settingsRouter);
app.use("/api/upload", uploadRouter);
app.use("/api/photos", photosRouter);
app.use("/api/styles", stylesRouter);
app.use("/api/generate", generateRouter);
app.use("/api/generate", streamRouter);
app.use("/api/generate", epubRouter);
app.use("/api/generate", pagesRouter);
app.use("/api/story-history", historyRouter);
app.use("/api/family-members", familyMembersRouter);
app.use("/api/generate", generatePdfRouter);
app.use("/api/story-history", historyPdfRouter);

// Health check
app.get("/api/health", (_req, res) => {
  res.json({ status: "ok", timestamp: new Date().toISOString() });
});

// Start server
app.listen(PORT, "0.0.0.0", () => {
  console.log(`Storytime backend running on http://0.0.0.0:${PORT}`);
});
