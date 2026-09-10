# Plain PDF

An ad-free Android PDF reader and offline toolkit. No account, no cloud, no tracking.

**`HANDBOOK.md` is the manual: how to build it, release it, and keep it running, plus the
traps worth knowing before you touch anything.** `STATUS.md` says what is done and what is
left. `pdf-reader-build-spec.md` is the original design brief.

## What it does

- Reader with continuous and page-by-page modes, search, bookmarks, form filling and an
  always-visible Tools button so every tool is one tap away from the open document.
- Draw and highlight: pen, highlighter and eraser on the page, saved as a copy with real PDF
  annotations (Android 12+ with the PDF extension; older devices simply do not show the tool).
- Tools, all on-device: Organise pages, Merge, Split, Compress, Images to PDF, PDF to images,
  Password, Sign (drawn signature stamped into the page).
- Scan: take a photo, the page outline is found automatically, drag the corners to fix it,
  choose Colour, Grayscale or Black and white, and the PDF is ready to share. The detection
  and cleanup are plain Kotlin (`util/scan`), unit tested on synthetic photos, so the FOSS
  build carries no OpenCV or ML Kit.
- Searchable scans: text recognition runs on the device with Tesseract, entirely offline,
  and is written into the PDF as an invisible text layer so the scan can be searched and its
  text copied. Eight language packs ship in the app (English, Serbian in both scripts,
  German, Spanish, French, Italian, Russian). Twenty more are Play on-demand asset packs
  (`packs/lang_*`, one per language): Settings lists them with sizes and a Get button,
  Google Play downloads the pack with its own connection, and the app copies the file next
  to the bundled ones. The app keeps its no-internet permission. The FOSS build cannot use
  Play, so it keeps the `.traineddata` import instead. Before recognition, the app offers
  the pack for the phone's language if it is missing; after recognition, rubbish text
  triggers a hint that a language may be missing.
- Languages: English, Serbian (Latin), German, Spanish, French, Italian, Brazilian Portuguese, Russian, Polish, Turkish, Indonesian, Vietnamese, Japanese, Korean, Simplified Chinese, Hindi and Arabic. Per-app language selection on Android 13+.

Privacy: neither flavour holds the INTERNET permission, backup is disabled, release builds
strip logging. See `PRIVACY.md` and `docs/play-data-safety.md`.

## Build

Requirements: JDK 17 or newer, Android SDK with platform 37 (compileSdk) and build-tools 36.

```
./gradlew :app:assembleFossDebug      # F-Droid flavour, no network permission
./gradlew :app:assemblePlayDebug      # Play flavour, includes Play Billing
./gradlew :app:testFossDebugUnitTest  # unit tests incl. golden-file PDF tests
./gradlew :app:bundlePlayRelease      # signed AAB when keystore.properties exists
```

Local development on a cramped emulator: `./gradlew -PdevAbi=x86_64 :app:installFossDebug`
builds a single-ABI APK. Release builds always include every ABI.

## Flavours

| Flavour | Billing | INTERNET permission | Cosmetics |
|---|---|---|---|
| `foss` | none | none | all unlocked |
| `play` | Play Billing 9 | none (billing works over Binder) | Supporter unlock |

`SupporterRepository` is the only seam. The UI never checks the flavour.

## Signing

Create `keystore.properties` at the repo root (git-ignored):

```
storeFile=C:/path/to/leaf-release.jks
storePassword=...
keyAlias=leaf
keyPassword=...
```

Release builds pick it up automatically; without it they are unsigned.

### Cutting a release

```
./scripts/release.sh
```

bumps `versionCode`, builds the Play bundle, moves the previous bundle into
`releases/archive/` as `plainpdf-<versionName>-<versionCode>-<date>.aab`, writes the new one to
`releases/plainpdf-play.aab` (always that name, so the upload path never changes) and checks
the signature. `releases/` is git-ignored; commit the `versionCode` change with the release.
Use `--no-bump` to rebuild the current version without a new number.

## Tests

Golden-file tests for every document operation live in
`app/src/test/java/com/leaf/app/data/pdf/write/PdfBoxEngineTest.kt` against fixtures in
`app/src/test/resources/fixtures/`. Regenerate fixtures with `python tools/make_fixtures.py`
(needs `pypdf`, and `cryptography` for the AES-256 fixture).

The scan pipeline is tested in `app/src/test/java/com/leaf/app/util/scan/`: synthetic photos
with grain, shadow and text rows at several tilts must yield corners within 3% of the truth,
and flat or pure-noise frames must fall back to the adjustable full frame.

## Licence

Plain PDF is free software under the GNU General Public License, version 3 (`LICENSE`).
Anyone may rebuild and republish it, but only under the same licence with the source
published, so nobody can turn it back into an ad-supported app. The `play` flavour links
Google's Play Billing library for the optional Supporter purchase, which is not free
software; the `foss` flavour is the fully free build.

## Website

`docs/` is the source of the website at `plainpdf.app`: the privacy policy at `/privacy`, the
source-code page at `/source` and a support page at `/support`. The app and the store listing
link only to those addresses, never to a hosting service directly. The site is served by
GitHub Pages from the separate public repository `lazytitan30/plainpdf-site` (branch `main`,
root, custom domain from `CNAME`), so this repository can stay private. After editing anything
in `docs/`, copy the html files, `CNAME` and `.nojekyll` into a clone of that repository and
push; `docs/play-data-safety.md` stays here.

## Third-party notices

- [Tesseract](https://github.com/tesseract-ocr/tesseract) and
  [Leptonica](https://github.com/DanBloomberg/leptonica), built for Android by
  [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android): Apache License 2.0.
- Language packs in `app/src/main/assets/tessdata` come from
  [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast): Apache License 2.0.
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android): Apache License 2.0.

### Fonts

- [DejaVu Sans](https://dejavu-fonts.github.io/) in `app/src/main/assets/fonts`, embedded by
  Write a note so Latin, Cyrillic and Greek text all render: Bitstream Vera Fonts licence
  (permissive), with the DejaVu additions in the public domain.

## Testing on weak hardware

Most memory problems only show on 2 GB phones. A matching emulator profile:

```
sdkmanager "system-images;android-29;google_apis;x86_64"
avdmanager create avd -n LowEnd -k "system-images;android-29;google_apis;x86_64" -d pixel_3a
```

Then set `hw.ramSize=2048` and `hw.cpu.ncore=2` in `~/.android/avd/LowEnd.avd/config.ini`,
boot it, and give apps the heap a 2 GB phone gives them:

```
adb root && adb shell setprop dalvik.vm.heapgrowthlimit 192m && adb shell stop && adb shell start
```

The scan pipeline sizes its working image from `Runtime.maxMemory()`, so it degrades to a
smaller page on such a device instead of crashing. The emulator camera app crashes on that
image; share a photo into the app or use Images to PDF with a pushed file instead.

## Testing language packs

On-demand packs exist only in the bundle, so a plain APK install never has them. Build the
bundle, then let bundletool serve the packs locally, which is what Play does in production:

```
./gradlew :app:bundlePlayDebug
java -jar bundletool.jar build-apks --bundle=app/build/outputs/bundle/playDebug/app-play-debug.aab --output=/tmp/plainpdf.apks --local-testing --overwrite
java -jar bundletool.jar install-apks --apks=/tmp/plainpdf.apks
```

With `--local-testing` the packs are pushed to the device and the Play library hands them
out without the Play Store, so Settings, Get, the progress bar and the copy into the app's
own folder all run for real. The Pixel 9 Pro emulator (Play Store image) is the reference.

## F-Droid

The `foss` flavour has no proprietary dependencies. The Google Play services OCR module that
`androidx.pdf` lists is excluded for every configuration in `app/build.gradle.kts`.
A starting metadata recipe:

```yaml
Categories: [Reading, Office]
License: Apache-2.0
Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle: [foss]
```

## Before the first store upload

The live checklist is in `STATUS.md` ("Left, yours" and "Left, mine"). In short: trademark
check for the name, upload keystore, the website live at `plainpdf.app` (GitHub Pages from
`docs/`) with mail forwarding for `support@plainpdf.app`, the four in-app products in Play
Console (`supporter_unlock` one-time; `tip_small`, `tip_medium`, `tip_large` consumable),
fresh screenshots, and a baseline profile once the app has real usage paths.
