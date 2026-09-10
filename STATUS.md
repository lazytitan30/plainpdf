# Plain PDF: project status

The single source of truth for where the app stands. Updated at the end of every working
session; if something is not in here, it is not agreed. The build spec
(`pdf-reader-build-spec.md`) is the original brief, the README says how to build, this file
says what is done, what is verified, what is left and who owns it.

Last updated: 2026-09-10 (live on Google Play, source published).

## At a glance

| | |
|---|---|
| Version | 1.0.0, `applicationId com.plainpdf.app`, Kotlin package `com.leaf.app` (intentional, see Decisions) |
| Branch | `main`, pushed to the public repository github.com/lazytitan30/plainpdf (created on release day as one clean commit; the pre-release history stays on the local branch `private-history`, never pushed) |
| On the phone | Version code 4 from Play's internal testing track (Galaxy S24 Ultra), installed 2026-09-06. Purchases went through earlier that day (a 9.99 tip, then the pack); the icon change works ("fix worked perfectly") |
| Release bundle | Always `C:\Diamond\pdf reader\releases\plainpdf-play.aab` (currently 1.0.0, version code 7, built 2026-09-10 evening, signed, 69 MB with all twenty packs inside; Play serves each phone only the base plus the packs it asks for). Made by `./scripts/release.sh`, which bumps the version code, builds, and moves the previous bundle into `releases\archive\` named by version and date. Version codes 1 and 2 (archived) went to internal testing on 2026-09-05; version code 3 (archived) was never uploaded, it still had the icon crash; version code 4 (archived) is the one that went live on 2026-09-10; version code 5 (archived, scrolling sheets) may or may not have been uploaded; version codes 6 and 7 (archived) both went live on 2026-09-10; the extra `plainpdf-1.0.0-code2-play.aab` file is a one-off from before the convention and can be deleted |
| Play Console | App created under the TekiTana account, internal testing releases 1 and 2 rolled out, questionnaires done (18+, no data collected, "Everyone" rating, sign-in details declare the cosmetic-only purchase), store listing with the user's own screenshots, four products active with purchase option `standard` (unlock 4.99, tips 0.99 / 2.99 / 9.99, tips allow quantity), licence tester added. The "cannot take payments" problem of 2026-09-05 was propagation for a brand-new app: the products appeared by themselves overnight and purchases work. The Monetization setup page (real-time notifications, licensing key) is not needed and stays untouched |
| Quality gates | 143 unit tests green, `lintFossDebug` clean, CI workflow in `.github/workflows/android.yml` (paused on GitHub until the account's billing lock is lifted) |
| Next step | **Live on Google Play since 2026-09-10** (version 1.0.0, code 4, approved after four days in review). Source published the same day; plainpdf.app/source links to it. **Version code 7 approved and live on Google Play** (startup baseline profile, status bar spacing). Nothing is queued; the next release is whenever a report or an idea calls for one |
| Blocking | User-owned items in "Left, yours" below |

## Decisions (dated)

- 2026-09-03 Renamed Quire → Leaf (non-English speakers cannot say "Quire").
- 2026-09-04 Renamed Leaf → **Plain PDF** ("Leaf PDF" taken on Play and App Store; every PDF+suffix coinage taken; Pedefko rejected, one letter from a Serbian slur). Reserves if the trademark check fails: PDF Bird, Prosto PDF, Lako PDF.
- 2026-09-04 Skip external testing: internal track, the user tests, then production.
- 2026-09-04 Cloud signing with the Serbian eID is shelved.
- Spec non-goals that were built anyway because the user asked for them: Compress, draw and highlight, form filling, redaction (rasterises the page so the text is really gone), OCR. Crash reporting exists only as an opt-in local file the user shares by hand; nothing leaves the phone on its own.
- Neither flavour holds the INTERNET permission. This is a selling point in the listing; keep it.
- Older, non-technical users are the target: no gesture-only actions, visible buttons for everything, a skippable welcome tour.
- 2026-09-05 `applicationId` is `com.plainpdf.app`, the Kotlin package stays `com.leaf.app` and identifiers still say Quire. Intentional: do not rename. Only the application id is visible to users and Play, and a package rename is churn with no benefit.
- 2026-09-05 The tour keeps its "Fill, draw, highlight" page on every phone; on phones without the PDF extension a note under the text says so, in plain words. Removing the page was judged too heavy.
- 2026-09-05 A session with a real older user happens after the production release, at the user's choice and responsibility.
- 2026-09-05 No em dashes anywhere, in any language: strings, listings, docs. A scan of every tracked file enforces it before a commit.
- 2026-09-05 Serbian store listing leads with the two facts that matter locally: no internet permission, offline Cyrillic and Latin text recognition. Written in Latin script, like the app.
- 2026-09-05 **Open source under GPL-3.0.** The premise of the app is "free for everyone, no ads"; GPL keeps every copy that way, because anyone who republishes the code must publish theirs under the same terms, which removes the incentive to rebuild it with ads. Apache was replaced in `LICENSE`. The `play` flavour links Google's non-free Play Billing library; the `foss` flavour is the fully free build. The user owns the copyright, so shipping the Play build is fine; if outside contributions ever arrive, ask contributors to agree to that combination.
- 2026-09-05 Domain `plainpdf.app` bought. Website lives in `docs/` (GitHub Pages, custom domain), privacy policy at plainpdf.app/privacy, source forward at plainpdf.app/source, support page at plainpdf.app/support, mail to support@plainpdf.app. The app and the store listing link only to those addresses. `plainpdf.rs` deferred for money reasons.
- 2026-09-06 **The big tip (9.99) grants the Supporter pack**; the two smaller tips only say thank you. The user saw the tip unlock everything and chose to keep that, with the button gone afterwards and a message that says the pack came with the gift. Reason for limiting it to the big tip: the pack costs 4.99, so a 0.99 tip unlocking it would make the pack pointless. Consequences: the pack has two sources in DataStore, the unlock Google Play holds for the account (restorable) and the big tip given on this phone (tips are consumed so they can be given again, which also means Play keeps no record and a reinstall cannot restore it; the app says so in one sentence). A tipper who later buys the pack, or a Supporter who tips again, just gets a thank-you; nothing is blocked.
- 2026-09-06 No disclaimer about future paid services on the Supporter screen. The intro already says every feature is free for everyone, so the pack cannot be read as "all future features"; it is cosmetics, and the list now promises that colours and icons added to the pack later come at no extra cost. If a paid service ever exists it will be a separate product, and nothing promised today conflicts with that.
- 2026-09-06 Settings rows for non-technical people: every row carries a one-line explanation under its value; "Default save location" became "Where new files are saved" with one dialog (Ask me each time / Always in this folder / Choose a folder) instead of two rows that looked like a loop; "Output naming" became "How new files are named" with two ready-made styles shown as examples (Contract_merged.pdf) and a custom pattern behind them.
- 2026-09-06 The website and the app say why the code stays open: the app was made to be passed along, one person helping the next (source page, home page, support page, Licence row).
- F-Droid is parked but now possible. It is a separate app store that builds open-source apps from source; the `foss` flavour and the fastlane folder were laid out for it. Its build scanner rejects JitPack, and Tesseract4Android comes from JitPack, so an F-Droid build would need Tesseract compiled from source. Not a Play blocker.

## Done

Each line says how it was checked. "Phone" means the user tried it; "emulator" means driven
by adb on the Android 16 or Android 10 emulator; "unit" means JVM tests.

### Reader
- Continuous and page-by-page modes, search with highlights, outline, bookmarks, jump to page, display modes, slider preview, keep screen on, volume-key paging, edge brightness. Phone.
- Always-visible Tools button and sheet; every tool opens with the current document. Phone.
- Form filling with "Save filled form" (Android 12+ with PDF extension 13). Emulator: typed two fields, ticked a box, saved, values read back with pypdf. A test form is in the phone's Downloads as `form.pdf`.
- Sign: drawn signature placed by tap or drag. Phone.
- Draw and highlight: pen, highlighter, eraser, saved as a copy with real PDF annotations (Android 12+ with PDF extension 18; hidden elsewhere). Phone.
- Print (system print dialog), Read aloud (system TTS with pause/stop), Copy or share text, Share this page (as picture or one-page PDF). Emulator; Read aloud and Copy text fall back to OCR when the page has no text or garbled text (Cyrillic case verified).
- Page taps on phones without the PDF extension no longer crash (Android 10 emulator, found through the crash report file).

### Library
- Recents with thumbnails, favourites, folders, search, sort, grid and list, locate-again for moved files. Phone.
- Continue reading card at the top of Recent. Emulator.
- Scan document button (camera → automatic page outline → corner and side drag with a loupe → Colour / Grayscale / Black and white → PDF → share). Phone, output 1546×2344 crisp.

### Tools (all on-device, all through the same temp-then-commit write path)
- Organise pages, Merge, Split, Images to PDF, PDF to images, Password. Unit (golden files) and phone.
- Compress (re-encodes the pictures inside the PDF). Unit and emulator.
- Page numbers, Make searchable (OCR for existing PDFs), Redact (rasterises the redacted pages). Emulator, output checked with pypdf.
- Write a note (typed text → A4 PDF with embedded DejaVu Sans, Cyrillic included). Unit and emulator.
- Result sheet after a save: Share, Done, Open in one row, Open as the main action. Emulator.

### OCR
- Tesseract 4.9 on the device, fully offline. Text goes into the PDF as an invisible layer, so scans are searchable. Phone ("astonished").
- Eight languages bundled (English, Serbian Latin and Cyrillic, German, Spanish, French, Italian, Russian). Twenty more are Play on-demand asset packs (`packs/lang_*`): Settings, "Languages for scanned text", lists them with sizes, most used first, then the region, then the rest; one tap on Get and Google Play downloads the pack, the app checks it with Tesseract, copies it into its own folder and ticks it on. The app keeps its no-internet permission; the privacy policy says so. Verified on the Android 16 Play-image emulator with bundletool local testing (Chinese: tapped Get, file in place and ticked within two seconds), and on 2026-09-06 on the user's phone with the copy installed from Play's internal testing track ("worked like a charm"). The FOSS build keeps the file import instead. Decided 2026-09-05 after the user called manual language downloads a deal breaker.
- Two suggestions: before recognition, if the phone's language has a pack that is not installed, a card on the scan sheet and in Make searchable offers it with its size ("Not now" is remembered per language); after recognition, text that looks like rubbish shows a hint pointing at the languages screen.
- Serbian default recognises both scripts at once; every other bundled language is the default on a phone set to it.

### Entry points
- Open with / share to the app for PDFs and images; share-sheet targets "Sign with Plain PDF" (PDF) and "Scan with Plain PDF" (photos, each gets the crop screen in turn). Registration verified on the emulator; the real share-sheet flow is not yet tried on the phone.
- Launcher shortcuts: Scan, Open, plus dynamic "Continue: <document>, page N" (up to two). Emulator; not yet checked on the Samsung launcher.
- Home-screen widget with Scan / Open / Library, shrinks to icons only when narrow. Emulator.

### Polish and release hygiene
- Welcome tour on first launch, skippable, ordered Sign, Scan and OCR, Fill, draw, highlight, Arrange; can be replayed from Settings. Phone. On phones without the PDF extension the third page carries a note saying which of those tools this phone lacks. Compiled, not yet seen on such a phone.
- Icon with a "PDF" band in all four variants and the themed icon. Phone.
- 17 languages with an in-app language picker; every string exists in every locale (lint enforces it). Strings added after the big language pass were reviewed in all 16 translations on 2026-09-05.
- Store listing in 17 locales in `fastlane/`, including Serbian; the full descriptions cover every feature as of 2026-09-05. No em dashes anywhere.
- Accessibility pass: labelled icon buttons and sliders, visible affordances for hidden gestures, 200% font size.
- Privacy: no INTERNET, no backup, logs stripped in release, cache cleanup, `PRIVACY.md`, `docs/play-data-safety.md`.
- Release builds are signed with the debug key when `keystore.properties` is missing, so the shrunk R8 build can be tested locally (done: library, OCR, reader).
- Opt-in crash report: uncaught crash → scrubbed text file → dialog on next launch with a Share button.
- The PDF library's isolated helper process skips the app set-up (no more database errors in the log).

### Supporter and purchases
- Google Play billing verified on the phone on 2026-09-06 with the copy from the internal testing track: the 9.99 tip and the pack both went through.
- Version code 3 (built, not yet uploaded): the big tip grants the pack and is remembered on the phone (the old code lost it at the next Play check, because a consumed tip is not in Play's purchase list); the pack button disappears for a tipper and a gift message takes its place; every completed purchase shows a thank-you the moment it completes, also for a Supporter who tips again; a tip whose consumption failed is consumed again before the next purchase; the "Google Play answered: ..." diagnostic became three plain sentences (not connected, items not listed yet, not offered in this country). Emulator: the gift state, the not-connected sentence, and the Settings row were checked on the Android 16 image; the purchase flow itself can only be checked on the phone through Play.

### App icon crash (fixed 2026-09-06, version code 4)
- Changing the app icon crashed on the phone with version code 2 (crash report from the user: "Component class com.plainpdf.app.launcher.Paper does not exist"). The switcher built the alias class name from the application id, but the aliases live under the manifest namespace `com.leaf.app`. It now derives the name from MainActivity's package; a unit test checks every icon against the aliases declared in the manifest. Emulator: switched to Paper on the Android 16 image, Settings shows Paper, no crash. Phone: confirmed by the user with version code 4 from Play on 2026-09-06. This is the one place where the application id and the namespace differing bit us; shortcuts.xml already used the right pair.

### Bottom sheets on short screens and large fonts (fixed 2026-09-10, version code 5)
- A friend of the user on an Honor Magic 7 could not see anything below "PDF to images" in the reader's Tools sheet and could not scroll. The sheet was a fixed column of up to fifteen rows. The tools sheet, the long-press document menu, the result sheet and the scan sheet now scroll inside the sheet. Emulator: Android 16 image forced to 1080x2000 with a 130% font; before the fix a swipe did nothing, after it Redact is reachable.

### Startup baseline profile (2026-09-10, version code 7)
- `:baselineprofile` is a test-only module that drives a fresh install through the tour, the library, the tools list and back, and records which methods ran. The recording is committed at `app/src/main/generated/baselineProfiles/baseline-prof.txt` and ships inside the app; an ordinary build needs no device. Rerun `./gradlew :app:generateBaselineProfile` when the startup path changes noticeably, and commit the result. A unit test guards the file against silently disappearing.
- The app already carried the profiles Compose and the other AndroidX libraries ship (5,316 rules). It now carries 31,318, of which 1,378 cover our own code.
- Measured on the emulator, cold start, ten iterations each: no compilation at all 487 ms, with the profile 453 ms, and 371 ms once the runtime has learned the app over a few days. So the profile moves the first launch about a third of the way toward the warmed-up state. Treat the size of that gain as indicative only: the emulator's spread was wide enough that the two distributions overlap, and a real phone is the only place to measure it properly.
- Gotcha for anyone repeating this: on this machine the emulator dies whenever a Gradle build runs beside it, so `:app:generateBaselineProfile` never completes. The way through is to build the APKs first, stop the Gradle daemon, boot the emulator, then run the recorder directly with `adb shell am instrument` against `com.leaf.app.baselineprofile.BaselineProfileGenerator` and pull the file from the output directory. A dedicated `Bench` AVD with a 12 GB data partition exists for this; the Pixel_9_Pro one is too full.

### Status bar spacing (2026-09-10, version code 7)
- Every screen sat about 50 dp too low. The app's outer layout left room for the status bar, and then each screen's own top bar left room for it again. The outer layout now hands its screens the bottom bar's height only, and the reader, which has no top bar of its own, keeps the page clear of the clock itself. On a 1080x2340 screen the library title moved from y=328 to y=192. Checked on the emulator across the library, tools, settings and the reader.

### Robustness sweep (2026-09-10, version code 6)
Prompted by the Honor report: what else breaks on phones unlike ours? Walked through on the Android 16 emulator forced to 1080x2000 at 420 dpi (a short, narrow phone) with the font at 200%, then in landscape, then in Arabic.
- Held up: the welcome tour (Next stays on screen), the Tools list, Merge, Split, Sign (draw and place), Organise, Write a note, PDF to images, Password, Page numbers, the reader's page grid and bookmark dialog, the Settings dialogs, the Supporter screen, the result sheet; Arabic mirrors correctly.
- Broke and fixed: the library's "Favourites" tab wrapped mid-word (labels now shrink instead); the two floating buttons covered the last list row and the empty state's button (clearance now grows with the font); the reader's Tools button hid the page counter (it now lifts by the measured bar height); in landscape on a short screen the stacked floating buttons covered the tabs (side by side under 500 dp of height).
- Found on the side: Android 13 and newer never showed the "Working on your document" notification because the app never asked for POST_NOTIFICATIONS. It now asks the first time a long job starts; the job runs whatever the answer.
- Documents from other apps: opening a PDF from the Files app works (Plain PDF appears in "Open with"). Sharing from the Files app shows one "Plain PDF" entry that opens the reader; the separate "Sign with Plain PDF" and "Scan with Plain PDF" targets did not appear in the emulator's share sheet. Not a fault, but worth a look on the phone (item 6 under "Left, yours").
- Still untested, worth a fixed step before each release: updating over the previous version with data in place (library, bookmarks, settings) whenever the database or DataStore changes; process death in the middle of a tool; dark, black and sepia themes at the largest font.

### Settings in plain words
- Every row has a one-line explanation under its value (all 17 languages). Save location is one row with one dialog; naming is a choice of two examples plus a custom pattern. Emulator: rows, both dialogs and the About group checked on the Android 16 image on 2026-09-06.

### Play review: foreground service declaration
- The bundle carries `FOREGROUND_SERVICE_DATA_SYNC` for two reasons: WorkManager runs long document jobs (text recognition, merge, split, compress, image conversion) as foreground work under a "Working on your document" notification, and Google's Play Asset Delivery library declares the same permission for the service that unpacks language packs. Play Console asks for a declaration before production; the answer is "Local processing: Other" with the description in the session notes of 2026-09-06, and a demo video recorded on the Android 16 emulator: `releases/play-review-foreground-service.mp4` (138 s: choose a 60-page scan, Save, leave the app, the notification with its progress bar in the shade, back to the app, the finished result). The user uploads the video somewhere linkable (unlisted YouTube or a Drive link) and pastes the link.
- Found while recording: the notification's title repeated the app name, which Android hides, so the collapsed shade showed a bare progress bar. Fixed on master (title "Working on your document", page counter as detail); not in version code 4, ships with the next update.

## Left, mine

Nothing is blocking. Worth doing when there is time:

- Fresh store screenshots from the emulator once you say the app is final (the six in `fastlane/metadata/android/en-US/images/phoneScreenshots` are from 2026-09-02, before the rename).
- Verify on a real launcher: the thumbnail icons on the Continue shortcuts, the widget on Samsung's home screen, and the share-sheet targets from Gallery and a file manager. I cannot look at the phone screen while you use it, so these need your eyes.

## Left, yours

1. Trademark check for "Plain PDF": TMview (EU) and the Serbian ZIS register. Fallbacks are listed under Decisions. The name is descriptive, so the risk of a conflict and the strength of protection are both low.
2. Website: done 2026-09-05. DNS at Spaceship points `plainpdf.app` at GitHub Pages (four A records, `www` CNAME), the certificate is issued and HTTPS is enforced; https://plainpdf.app, /privacy, /support and /source all answer. Mail to any address at plainpdf.app, including support@, forwards to the user's mailbox (catch-all at the registrar). For the Play listing: privacy policy https://plainpdf.app/privacy, contact support@plainpdf.app. Source: public repository `github.com/lazytitan30/plainpdf-site`.
   The code went public on release day, 2026-09-10: github.com/lazytitan30/plainpdf, and `docs/source.html` links to it. `docs/` in this repository is the source of the website; after editing it, copy the html files, `CNAME` and `.nojekyll` into a clone of `plainpdf-site` and push.
3. Upload keystore: done 2026-09-05. kept in a `Keys` folder outside the repository, alias `plainpdf`, password only in the ignored `keystore.properties`; copy the password into your password manager and back up the `Keys` folder somewhere off this PC. The signed Play bundle is at `C:\Diamond\pdf reader\releases\plainpdf-1.0.0-play.aab` (version 1.0.0, code 1); rebuild with `./gradlew :app:bundlePlayRelease` after any change and bump the version code first. The Play developer account is TekiTana, an organisation account, so no closed-testing gate applies.
4. Domain notes for later: `plainpdf.com` is registered until February 2030 by someone else, `plainpdf.net` since June 2026; `plainpdf.rs` is unchecked (rnids.rs) and deferred.
5. Play Console: create the app, answer Data safety from `docs/play-data-safety.md`, paste the listing from `fastlane/metadata/android/<locale>/` (17 locales including `sr`).
   Purchases: the app already has the Supporter screen (one-time "Become a Supporter" unlock for accent colours, icons and highlight sets, three "Give again" tips that can be bought any number of times, and Restore purchases). In Play Console create exactly these four in-app products: `supporter_unlock` (one-time, non-consumable), `tip_small`, `tip_medium`, `tip_large` (one-time, consumable), with prices of your choice, for example 2.99, 0.99, 2.99, 4.99 EUR. Then add your own Google account under Settings, Licence testing, so test purchases cost nothing. Billing only works in a build installed from Play, so the purchase flow is tested on the internal testing track with the `play` release bundle, never with the sideloaded debug build.
6. Test on the phone what only a real device shows: fill in `form.pdf` from Downloads, add the widget, long-press the icon for shortcuts, share a photo from Gallery to "Scan with Plain PDF", share a PDF to "Sign with Plain PDF".
   With version code 4 from Play: change the app icon (all four, then back); open Supporter (you own the pack, so it says "You are a Supporter"), give the 9.99 tip again and watch for the "Thank you for your gift" line at the bottom; the button stays gone, the tips stay available. The gift-only state (tip without the pack) needs a Google account that never bought the pack.
7. After production: one session with an older person, you watch and do not help. Note what they could not find.

## Known limitations

- Draw, highlight and form filling need Android 12 or newer with the PDF extension; on older phones the tools simply do not appear.
- Redact rasterises the redacted pages: the text layer on those pages is gone on purpose.
- The debug APK is about 112 MB (all ABIs plus OCR data); the release bundle splits by ABI.
- Compress cannot shrink PDFs that contain no pictures.
- Read aloud uses whatever TTS voices the phone has; Serbian needs a voice installed.

## Test recipes

- Unit tests: `./gradlew :app:testFossDebugUnitTest`. Lint: `./gradlew :app:lintFossDebug`.
- Android 16 emulator `Pixel_9_Pro`: forms, annotations, share sheet. Its storage is nearly full: `adb shell settings put global sys_storage_threshold_percentage 1`, and uninstall before installing.
- Android 10 emulator `LowEnd` (2 GB): weak-hardware checks, see README "Testing on weak hardware".
- Phone: `adb -s <serial> install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk` (the emulator is usually attached too, so always pass the serial).
- Before ending a session on this machine: `./gradlew --stop` and kill the emulator, or the desktop app crashes on locked files.

## History

- 2026-09-02 M0 to M7 of the spec: skeleton, reader, library, reader depth, write foundation and organiser, remaining tools, supporter, release scaffolding.
- 2026-09-03 Tools button in the reader, form filling, Sign, Scan with edge detection, rename to Leaf.
- 2026-09-04 Scan quality and crop editor, privacy audit, accessibility, Serbian and fifteen more languages, Compress, draw and highlight, offline OCR, rename to Plain PDF, welcome tour, icon band, release readiness (1.0.0, CI, lint, crash reports), shortcuts and widget, print, read aloud, copy text, page numbers, make searchable, redact, OCR fallback for garbled text.
- 2026-09-05 Continue-reading shortcuts and card, share-sheet targets, share a page, write a note, form-filling test, isolated-process fix, result sheet button row. Evening: GPL, website on plainpdf.app, language packs through Play, copy warm-up, Play Console set up with the user, two internal testing releases, release script and folder convention.
- 2026-09-06 Purchases confirmed on the phone; the big tip grants the pack and is remembered; plain-sentence billing problems; Settings explanations, save-location dialog, naming styles; website copy on passing the app along; app icon crash fixed (version code 4). Submitted for production review with the foreground service declaration.
- 2026-09-10 Evening: startup baseline profile recorded and shipped, status bar spacing fix; version code 7 published and approved the same day.
- 2026-09-10 Approved and live on Google Play. Source code published at github.com/lazytitan30/plainpdf; the website's source page links to it. First bug report from a real phone (Honor Magic 7: Tools sheet did not scroll) fixed the same day; version code 5 built. GitHub Actions is paused until the account's billing lock is lifted. Afternoon: robustness sweep at the largest font, on a short screen, in landscape and in Arabic; four layout fixes and the missing notification permission request; version code 6 built.
