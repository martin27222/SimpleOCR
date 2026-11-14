<p align="center"><img src="docs/icon.svg" width="96" height="96" alt="SimpleOCR icon"></p>

<h1 align="center">SimpleOCR</h1>

<p align="center"><b>Point the camera at text. Copy the text. Nothing leaves the phone.</b></p>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/martin27222/SimpleOCR"><img src="https://img.shields.io/badge/Get%20it%20on-Obtainium-6E40C9?style=for-the-badge" alt="Get it on Obtainium"></a>
  <a href="https://github.com/martin27222/SimpleOCR/releases/latest/download/SimpleOCR.apk"><img src="https://img.shields.io/badge/Download-APK-0E6B63?style=for-the-badge" alt="Download the APK"></a>
</p>

Built for one job: getting text out of a photo as accurately as possible. Take the photo in the app or pick one from the gallery, then copy the text. Free, open source, no account, no ads, works in flight mode.

## What makes it different

| | |
| --- | --- |
| **Accurate** | Each photo is read up to three times (as taken and flattened in two ways), and the readings correct each other word by word. |
| **Offline** | No internet permission at all. Text recognition runs on the phone. |
| **Keeps the layout** | Columns, side boxes and page numbers stay apart. Copy everything or one box. |
| **Clean text** | Hyphenated words are rejoined and lines flow into paragraphs, ready to paste. |

## Use cases

| | Situation | What SimpleOCR does |
| --- | --- | --- |
| 1 | **An open book**, page bent towards the spine, photographed at an angle | Straightens the bent lines and widens squeezed letters before reading |
| 2 | **Text wider than one photo**, such as a label around a bottle or a wide page | Take several overlapping photos; they are laid over each other by the positions of their words, so nothing is doubled or lost at the joins |
| 3 | **A busy page** with a heading, a side column and a page number | Finds each area of text and keeps its lines together, so a side column never ends up between the lines of the main text |

German and English text work best. Android 8 or newer, 64-bit phones.

## How to use it

1. Point the camera at the text and take the photo, or open a picture from the gallery.
2. The text shows up after a few seconds and gets better while the photo is read again.
3. Tap "Copy all", or tap a box and "Copy frame". Or select a few words by hand.

## Install

**With [Obtainium](https://github.com/ImranR98/Obtainium)** (gets updates automatically): tap the Obtainium button above on your phone, or add `https://github.com/martin27222/SimpleOCR` in Obtainium.

**Without Obtainium:** download [SimpleOCR.apk](https://github.com/martin27222/SimpleOCR/releases/latest/download/SimpleOCR.apk) and open it.

**Check the download with [AppVerifier](https://github.com/soupslurpr/AppVerifier):** copy this and compare it in AppVerifier.

```
io.github.martin27222.simpleocr
66:C3:69:84:32:6B:E2:62:34:E7:97:2C:FB:A4:E3:17:63:38:05:16:7F:6E:A8:BB:BD:35:9C:EF:58:3A:A2:5E
```

## Status

Early development, tested by its author on a Pixel 9.

| Feature | State |
| --- | --- |
| Read text from a photo or a shared image, copy it | works |
| Flatten curved book pages | works, tested on a few real photos |
| Columns and frames, "Copy frame" | new, needs testing on real pages |
| Several photos of a round object | rebuilt: photos are laid over each other by word positions, needs testing |
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

Turn the object a little after each photo, so that about a third of the text is in two photos. SimpleOCR reads every photo and then lays the photos over each other by the positions of their words:

1. A longer word that two photos both show, such as "mushrooms", is one spot seen twice. From many such spots the app works out how the second photo is shifted, enlarged and turned against the first. Spots that do not fit are words that merely repeat.
2. Near every word, the spots around it count most, so a page that bends is followed line by line.
3. Two words that land on the same place are the same printed word. The one further from the edge of its own photo is kept, because words at the edge get cut off. Scraps of cut-off letters between two photos are dropped.

Because the place decides and not the text, a word that repeats in the text ("until ... until") is neither dropped nor doubled. If a photo shares too few words with the others, the texts are joined line by line instead, by looking for the stretch of text two lines share.

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

Every push to `main` publishes a numbered release, signed with the project's private release key (repository secrets `SIMPLEOCR_KEYSTORE_BASE64` and `SIMPLEOCR_KEYSTORE_PASSWORD`). Pull requests attach their APKs as workflow artifacts.

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

Open source under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). The app lists the licenses of all libraries it ships with under Settings, "Open-source licenses". All of them allow use in this app; ML Kit is the only one that is not open source.
