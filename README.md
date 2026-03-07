# Storytime

A personalized bedtime story generator. Enter a prompt, and it writes and illustrates a complete story starring your kid — delivered as an EPUB you can read together on Apple Books.

Uses Claude for writing and Gemini for illustrations.

## Install

1. Download [Storytime-arm64.dmg](https://github.com/fananta/storytime/releases/latest/download/Storytime-arm64.dmg)
2. Open the DMG and drag Storytime to Applications
3. **First launch:** macOS will block it because it's not signed. Right-click (or Control-click) the app and select **Open**, then click **Open** again in the dialog. You only need to do this once.

## Setup

1. Open Storytime and go to **Settings**
2. Add your child's name and reading level
3. Add your **Anthropic API key** (for Claude) and **Google AI API key** (for Gemini image generation)
4. Optionally add family members so they can appear in stories

## Usage

1. Type a prompt describing tonight's adventure
2. Pick a writing style and image style (or "No Images" for text-only)
3. Wait ~2 minutes while it writes and illustrates
4. Download the EPUB or scan the QR code to open directly in Apple Books

## Dev Setup

```bash
pnpm install
pnpm dev          # Next.js dev server at localhost:3000
```

To build the Electron app:

```bash
pnpm electron:build:mac
```

DMGs are output to `release/`.
