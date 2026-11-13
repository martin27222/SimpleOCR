# SimpleOCR

SimpleOCR is an offline Android app with one job: point the camera at text, copy the text.

It is a fork of [EasyOCR](https://github.com/MosesEllermann/EasyOCR) by Moses Ellermann, a privacy-first screenshot OCR editor. SimpleOCR keeps its on-device text recognition and text selection, and adds what is needed for photographing real objects.

## Status

Early development. Builds exist for 64-bit ARM phones only.

| Feature | State |
| --- | --- |
| Open an image from another app and copy its text | works (from EasyOCR) |
| Take the photo inside the app | works, needs testing on real devices |
| Straighten curved or tilted pages (book pages) before recognition | works, tested on one real photo so far |
| Combine several photos of a round object (bottle labels) into one text | works on simulated photos, needs testing on real ones |
| Settings | works, needs testing on real devices |

Target languages are German and English. The target device is a Pixel 9, the app runs on Android 8 and newer.

## Privacy

Text recognition runs on the device. Images are not uploaded.

SimpleOCR:

- does not request `INTERNET`
- explicitly removes the transitive `INTERNET` and `ACCESS_NETWORK_STATE` permissions from the merged manifest
- has no analytics, crash reporting or ads

## Text recognition

SimpleOCR uses [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) with the bundled Latin recognizer. The model ships inside the APK, so no Google account, model download or network access is needed.

ML Kit is not open source. The SimpleOCR code is Apache 2.0, the ML Kit library is governed by the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).

## Flattening curved pages

A photo of an open book has bent, tilted text lines, and letters on the far side of the page are squeezed. SimpleOCR first reads the photo as taken, so text shows up at once. It then flattens the photo in two ways, reads both results and switches to the best one only if it reads clearly better than the photo as taken. A button swaps between the photo and its flattened version.

| Method | How it works |
| --- | --- |
| Guided by the text | The first reading tells where every word is. From that the app works out how the photo must be pulled so that all lines are straight and all letters equally wide. Plain geometry and least squares, no model; see `TextRectifier` in the `core` module |
| [UVDoc](https://github.com/tanguymagne/UVDoc) network (MIT License) | A small neural network estimates the shape of the page from the whole photo. It helps when the first reading finds too little text to go by |

Finally the readings correct each other word by word: where they disagree, the spelling most of them share wins, otherwise the word the recognizer was surest about.

The network runs on the device through [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT License). The model file was converted from the original UVDoc checkpoint for this project; [third_party/uvdoc](third_party/uvdoc/README.md) documents where it comes from and how to rebuild it.

## Several photos of a round object

The label of a bottle does not fit into one photo. In the mode "Several photos" the object is turned a little after each photo, so that about a third of the text is in two photos. SimpleOCR reads every photo and joins the texts line by line: it looks for the stretch of text two lines share and keeps it once. Letters at the edge of a photo are often cut off, so the first half of the shared stretch is taken from the photo that shows the start of the line and the second half from the photo that continues it. The same works for a flat page photographed in several parts.

Example: `Aqua, Alcohol Denat., Parf` and `Denat., Parfum, Limonene` become `Aqua, Alcohol Denat., Parfum, Limonene`.

The images themselves are not stitched together, and no further model is involved.

## Settings

The app works without touching any setting.

| Setting | Default |
| --- | --- |
| Flatten curved pages | on |
| Rejoin words split with a hyphen at a line end | on |
| Flowing text instead of the line breaks of the photo | on |
| Copy text automatically after a photo | off |
| Save every photo to the gallery | off |
| Light on at start | off |
| Start in quality mode | off |

The camera has two capture modes, switched with the symbol at its top left: speed (the photo is there at once) and quality (the camera takes more care and the photo keeps its full size, which helps with small print).

## Install a development build

Every push to `main` publishes a debug APK as the [latest development build](https://github.com/martin27222/SimpleOCR/releases/tag/dev-latest). Pull requests attach their APK as a workflow artifact.

Development builds are signed with the debug key in `app/debug.keystore`. That key is public, so only install development builds from this repository.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36

```bash
./gradlew test assembleDebug
```

For a signed release build, configure a private keystore through `local.properties`:

```properties
simpleocr.release.storeFile=simpleocr-release.jks
simpleocr.release.storePassword=...
simpleocr.release.keyAlias=simpleocr
simpleocr.release.keyPassword=...
```

## License

SimpleOCR is open source under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
