<p align="center"><img src="docs/icon.svg" width="96" height="96" alt="SimpleOCR icon"></p>

<h1 align="center">SimpleOCR</h1>

<p align="center"><b>Point the camera at text. Copy the text. Nothing leaves the phone.</b></p>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/martin27222/SimpleOCR"><img src="https://img.shields.io/badge/Get%20it%20on-Obtainium-6E40C9?style=for-the-badge" alt="Get it on Obtainium"></a>
  <a href="https://github.com/martin27222/SimpleOCR/releases/latest/download/SimpleOCR.apk"><img src="https://img.shields.io/badge/Download-APK-0E6B63?style=for-the-badge" alt="Download the APK"></a>
</p>

A recipe in a cookbook, the ingredients on a bottle, a page of a letter: take a photo, and the text is ready to paste into your notes. Free, open source, no account, no ads, works in flight mode.

## What makes it different

| | |
| --- | --- |
| **Offline, for real** | The app has no internet permission at all. Text recognition runs on the phone. |
| **Book pages come out straight** | A photo of an open book has bent lines. SimpleOCR flattens the page before reading it, so words at the curved edge are read too. |
| **Columns stay apart** | A side box with ingredients is not mixed into the main text. Each area of text gets its own frame, and "Copy frame" copies just that one. |
| **Labels around a bottle** | Take several photos while turning the bottle. SimpleOCR joins the texts into one and keeps each overlapping word once. |
| **Clean text to paste** | Words split with a hyphen at the line end are rejoined, and lines flow into paragraphs. Both can be switched off. |

German and English text work best. Android 8 or newer, 64-bit phones.

## How to use it

1. Open the app and point the camera at the text.
2. Take the photo. The text shows up after a few seconds.
3. Tap "Copy all", or tap a frame and "Copy frame". Or select a few words by hand.

## Install

**With [Obtainium](https://github.com/ImranR98/Obtainium)** (gets updates automatically): tap the Obtainium button above on your phone, or add `https://github.com/martin27222/SimpleOCR` in Obtainium.

**Without Obtainium:** download [SimpleOCR.apk](https://github.com/martin27222/SimpleOCR/releases/latest/download/SimpleOCR.apk) and open it.

**Check the download with [AppVerifier](https://github.com/soupslurpr/AppVerifier):** copy this and compare it in AppVerifier.

```
io.github.martin27222.simpleocr.debug
DA:13:70:3C:D8:A2:CF:9A:2E:8C:E4:22:E9:A2:8F:1E:1E:A2:53:A2:E2:3B:FF:24:FA:FE:4C:B5:AE:1A:62:A4
```

> The current builds are development builds, signed with a debug key that is public in this repository. The check above proves the APK was built from this repository's setup, but anyone could sign an APK with the same key. Only install builds from this page.

## Status

Early development, tested by its author on a Pixel 9.

| Feature | State |
| --- | --- |
| Read text from a photo or a shared image, copy it | works |
| Flatten curved book pages | works, tested on a few real photos |
| Columns and frames, "Copy frame" | new, needs testing on real pages |
| Several photos of a round object | works on test photos, real photos still mix up words at the joins |
| Quality levels LQ, SQ, HQ | new, needs testing |

Changes are listed in the [CHANGELOG](CHANGELOG.md).

## Privacy

Images and text stay on the phone. SimpleOCR has no internet permission (it even removes the ones its libraries ask for), no analytics, no crash reporting and no ads. See [PRIVACY.md](PRIVACY.md).

<details>
<summary><b>How it works inside</b></summary>

### Text recognition

[ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) with the bundled Latin model. The model ships inside the APK, so no Google account, model download or network access is needed. ML Kit is not open source; it is governed by the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).

### Flattening curved pages

SimpleOCR first reads the photo as taken, so text shows up at once. It then flattens the photo in up to two ways, reads the results and switches to the best one only if it reads clearly better. A button swaps between the photo and its flattened version.

| Method | How it works |
| --- | --- |
| Guided by the text | The first reading tells where every word is. From that the app works out how the photo must be pulled so that all lines are straight and all letters equally wide. Plain geometry and least squares, no model (`TextRectifier` in `core`). |
| [UVDoc](https://github.com/tanguymagne/UVDoc) network (MIT License) | A small neural network estimates the shape of the page from the whole photo. It helps when the first reading finds too little text to go by. It runs through [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT License); see [third_party/uvdoc](third_party/uvdoc/README.md). |

Finally the readings correct each other word by word: where they disagree, the spelling most of them share wins, otherwise the word the recognizer was surest about.

### Columns and frames

`PageLayout` in `core` works out the areas of text from the positions of the recognized lines: a tall empty strip that no line crosses separates two columns, a wide empty band between lines starts a new paragraph, a very wide one a new area. "Copy frame" starts with the largest area; a tap on another box chooses that one.

### Several photos of a round object

Turn the object a little after each photo, so that about a third of the text is in two photos. SimpleOCR reads every photo and joins the texts line by line: it looks for the stretch of text two lines share and keeps it once. Letters at the edge of a photo are often cut off, so the first half of the shared stretch comes from the photo that shows the start of the line and the second half from the photo that continues it.

Example: `Aqua, Alcohol Denat., Parf` and `Denat., Parfum, Limonene` become `Aqua, Alcohol Denat., Parfum, Limonene`.

### Settings

The app works without touching any setting.

| Setting | Default |
| --- | --- |
| Flatten curved pages | on |
| Rejoin words split with a hyphen at a line end | on |
| Flowing text instead of the line breaks of the photo | on |
| Copy text automatically after a photo | off |
| Save every photo to the gallery | off |
| Light on at start | off |
| Quality level at start | SQ |

The button at the top left of the camera switches between three quality levels. Photo size and number of reading passes can be changed per level in the settings.

| Level | Photo size (longer side) | Reading passes |
| --- | --- | --- |
| LQ | 2000 px | 2 |
| SQ | 3000 px | 2 |
| HQ | 3000 px | 3 |

Pass 1 reads the photo as taken, pass 2 the photo flattened with the help of the text, pass 3 the photo flattened by UVDoc. At 4096 px a 12 megapixel photo keeps its full size and the camera takes more time for a cleaner photo.

</details>

<details>
<summary><b>Build it yourself</b></summary>

Requirements: JDK 17 and Android SDK Platform 36.

```bash
./gradlew test assembleDebug
```

Every push to `main` publishes a numbered release with the debug APK. Pull requests attach their APK as a workflow artifact.

For a signed release build, configure a private keystore through `local.properties`:

```properties
simpleocr.release.storeFile=simpleocr-release.jks
simpleocr.release.storePassword=...
simpleocr.release.keyAlias=simpleocr
simpleocr.release.keyPassword=...
```

</details>

## Credits and license

SimpleOCR is a fork of [EasyOCR](https://github.com/MosesEllermann/EasyOCR) by Moses Ellermann, a privacy-first screenshot OCR editor. It keeps EasyOCR's on-device recognition and text selection and adds what is needed for photographing real objects.

Open source under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
