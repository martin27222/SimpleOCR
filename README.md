# SimpleOCR

SimpleOCR is an offline Android app with one job: point the camera at text, copy the text.

It is a fork of [EasyOCR](https://github.com/MosesEllermann/EasyOCR) by Moses Ellermann, a privacy-first screenshot OCR editor. SimpleOCR keeps its on-device text recognition and text selection, and adds what is needed for photographing real objects.

## Status

Early development. Builds exist for 64-bit ARM phones only.

| Feature | State |
| --- | --- |
| Open an image from another app and copy its text | works (from EasyOCR) |
| Take the photo inside the app | works, needs testing on real devices |
| Straighten curved pages (book pages) before recognition | works on test images, needs testing on real photos |
| Combine several photos of a round object (bottle labels) into one text | planned |

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

A photo of an open book has bent text lines. SimpleOCR first reads the photo as taken, so text shows up at once. It then flattens the photo with the [UVDoc](https://github.com/tanguymagne/UVDoc) network (MIT License), reads it again and switches to the flattened version only if that reads clearly better. A button swaps between both versions.

The network runs on the device through [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT License). The model file was converted from the original UVDoc checkpoint for this project; [third_party/uvdoc](third_party/uvdoc/README.md) documents where it comes from and how to rebuild it.

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
