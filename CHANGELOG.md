# Changelog

## 0.1.0 (unreleased)

- Forked from EasyOCR 1.1.2.
- Renamed the app to SimpleOCR with the application ID `io.github.martin27222.simpleocr`.
- Added an automated build that produces a debug APK.
- The app opens straight into a camera; photos are read without being saved.
- Added a "Copy all" button; removed drawing and sharing.
- Photos of curved pages are flattened automatically when that improves the reading.
- Added a mode that joins the text of several overlapping photos of a round object.
- Added settings: flattening, rejoining hyphenated words, flowing text, automatic copy, automatic photo saving, light at start.
- "Copy all" gives flowing text with rejoined words by default.
- Photos are saved as JPEG instead of PNG.
- Pages are also flattened with the help of the recognized text itself, which undoes the squeeze of pages photographed at an angle.
- The readings of a photo and its flattened versions correct each other word by word.
- The camera has a speed mode and a quality mode.
- Joining several photos handles lines that lie inside another line, letters cut off at photo edges and flat pages photographed in parts.
- The app finds columns and separate areas of text itself. A side column is no longer mixed into the main text, and "Copy frame" copies one area on its own.
- Three quality levels (LQ, SQ, HQ) replace the speed and quality modes. Photo size and reading passes of each level can be set.
- Wider side margins around text that can be selected.
- Photos are read one after the other to use less memory.
- New app icon of its own: a camera frame around lines of text.
- Every build of `main` is a numbered release (0.1.N) with a fixed download link, so Obtainium sees each build as an update.
- Simpler README with install buttons and an AppVerifier check.
- The settings list the licenses of SimpleOCR and of every library inside the app, including the MIT and BSD texts that must travel with the app.
- Several photos are now laid over each other by the positions of their words instead of being joined by their text. Repeated words at the joins are no longer doubled or dropped, scraps of cut-off words are removed, and the joined text keeps its paragraphs and areas.
- Releases are signed with a private release key instead of the public debug key. The package name is `io.github.martin27222.simpleocr`; it installs next to the old debug app, which can then be removed.
- App icon in black on white, a little smaller.
- The main text keeps its amber box; a tapped side box turns white and "Copy frame" copies it.
- The text joined from several photos shows each area of text as a card with its own copy button, next to "Copy all".
- Paragraphs are measured inside each area of text with its own line spacing, so a side note in smaller type keeps its lines together.

## EasyOCR history

Changes before the fork are listed in the [EasyOCR changelog](https://github.com/MosesEllermann/EasyOCR/blob/main/CHANGELOG.md).
