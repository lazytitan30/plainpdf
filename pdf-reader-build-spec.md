# Android PDF Reader and Toolkit: Build Specification

Hand this file to Claude Code as the project brief. It is written to be executable top to bottom.

Revision 2. The write path and page-editing tools are now first-class, designed in from M0 rather than bolted on.

---

## 0. Before writing any code

Resolve these five things first. Do not guess them.

1. **Latest stable AGP, Kotlin, and Compose BOM versions.** Take them from a fresh Android Studio template or the Android release notes. Do not hardcode versions from memory.
2. **Current Google Play `targetSdk` requirement for new apps.** Verify at `developer.android.com/google/play/requirements/target-sdk`.
3. **Latest `androidx.pdf` alpha.** Verified state: `1.0.0-alpha18`, released 22 April 2026, read and render backported to `minSdk = 28`. Check `developer.android.com/jetpack/androidx/releases/pdf` and read the release notes for API breaks.
4. **Latest `com.tom-roush:pdfbox-android` release.** Apache 2.0. This is a port of Apache PDFBox 2.0.x, not 3.x, so use the 2.0.x API surface.
5. **Play Billing Library major version.** Verified: v8 or later is required for all new apps and updates from 31 August 2026.

Report what you found before scaffolding.

---

## 1. What this is

A PDF reader and offline toolkit for Android. No ads, ever. No account. No cloud. No tracking. Opens fast, remembers where you were, and can actually change a document without uploading it to a stranger's server.

Two reasons this exists:

- Every PDF reader on the Play Store is stuffed with ads and upsells.
- Every "merge your PDF" tool is a website you upload contracts and payslips to. Doing it locally is a real privacy difference, not a marketing line.

**Non-negotiables:**

- Zero ad SDKs. Zero analytics SDKs. Zero crash-reporting SDKs in the FOSS flavour.
- No network permission in the FOSS flavour at all. The Play flavour needs it only for Billing. Nothing else in this app ever touches the network.
- Every reading and editing feature is free. The paid tier is cosmetic only.
- No `MANAGE_EXTERNAL_STORAGE`. No `READ_EXTERNAL_STORAGE`. Storage Access Framework only.
- **No operation ever destroys a user's file silently.** See section 6.
- Cold start to first rendered page under 1 second on a mid-range device for a typical 2 MB document.

---

## 2. Stack

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose, Material 3 | Custom colour scheme, dynamic colour is an opt-in setting only |
| PDF render | `androidx.pdf:pdf-viewer-fragment` | Apache 2.0, first party, alpha. Read only. |
| PDF write | `com.tom-roush:pdfbox-android` | Apache 2.0. Merge, split, page ops, encryption, image import. |
| Page rasterise | `android.graphics.pdf.PdfRenderer` | Framework, free, used for thumbnails and PDF to image export |
| Fragment interop | `androidx.fragment:fragment-compose` | The viewer is a Fragment, host it via `AndroidFragment` |
| Background work | WorkManager | Long document operations, with foreground notification |
| Persistence | Room | Documents, bookmarks, folders |
| Settings | DataStore Preferences | |
| Async | Coroutines + Flow | |
| DI | Hand-rolled `AppContainer` | No Hilt or Koin. Too small to justify the build cost |
| Billing | Play Billing Library v8+ | `play` flavour only |
| Testing | JUnit, Turbine for Flows, Compose UI tests for the reader | Plus golden-file tests on document operations, see section 6 |

`minSdk = 28`. `compileSdk` matches `targetSdk`.

**Two engines, on purpose.** `androidx.pdf` renders beautifully and cannot write. PdfBox-Android writes well and renders poorly. Do not try to unify them. They sit behind two separate interfaces and never meet.

**PdfBox initialisation.** `PDFBoxResourceLoader.init(context)` loads font resources and is not free. Do **not** call it in `Application.onCreate`, it will hurt reader cold start. Call it lazily on first entry to any tool, guarded by an `AtomicBoolean`, off the main thread.

**APK size.** PdfBox-Android ships font assets and adds a few MB. Acceptable. Do not try to strip them, missing fonts cause silent output corruption.

**16 KB page size:** required for apps targeting Android 15+. Verify with `zipalign -c -P 16 -v` on the release bundle.

---

## 3. Build flavours

This matters and must be set up on day one. Retrofitting it later is painful.

The app ships to two stores with incompatible rules:

- **Play Store** wants Google Play Billing. Play Billing is a proprietary Google library.
- **F-Droid** rejects any build containing proprietary dependencies. It is also the natural home for an ad-free reader and where the target audience actually looks.

So: **two product flavours**, dimension `distribution`.

```
play  -> includes billing, Supporter screen, cosmetic unlocks
foss  -> no billing dependency, no INTERNET permission, About screen shows
         a plain donate link (permitted on F-Droid), all cosmetics unlocked
```

Both `androidx.pdf` and PdfBox-Android are Apache 2.0, so the entire feature set is F-Droid clean. Only billing is not.

Structure so `SupporterRepository` is an interface in `src/main` with two implementations:

```
src/main/java/.../data/billing/SupporterRepository.kt        (interface)
src/play/java/.../data/billing/PlayBillingSupporterRepository.kt
src/foss/java/.../data/billing/AlwaysUnlockedSupporterRepository.kt
```

`AppContainer` picks the implementation. No `BuildConfig.FLAVOR` checks scattered through the UI. The UI asks `supporterRepository.isSupporter: StateFlow<Boolean>` and does not know why.

`src/foss/AndroidManifest.xml` must not declare `android.permission.INTERNET`. Use `tools:node="remove"` if the merged manifest pulls it in.

---

## 4. Storage and permissions

This is where most PDF apps go wrong. Get it right before anything else.

### Reading

- `ACTION_OPEN_DOCUMENT` with `application/pdf`. Not `ACTION_GET_CONTENT`.
- Request `FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION` on the picker intent. Write access is needed later for overwrite-in-place, and asking up front costs the user nothing extra.
- Immediately call `takePersistableUriPermission` with whichever flags were actually granted. Record in the DB whether write was granted; do not assume.
- `ACTION_OPEN_DOCUMENT_TREE` for "Add folder". Take persistable permission, enumerate PDFs lazily with `DocumentsContract`.

### Writing

- `ACTION_CREATE_DOCUMENT` with `application/pdf` and a sensible suggested filename (`report_merged.pdf`, `report_pages_1-4.pdf`). This is the default output path for every operation.
- Overwrite in place is offered **only** when the source URI actually holds a persisted write grant. If it does not, do not show the option. Never silently downgrade to "save a copy" without telling the user.

### Incoming intents

Manifest filters on the reader activity:

```xml
<!-- Open with -->
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="content" android:mimeType="application/pdf" />
  <data android:scheme="file"    android:mimeType="application/pdf" />
</intent-filter>

<!-- Share to -->
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="application/pdf" />
</intent-filter>

<!-- Multi-share, feeds straight into Merge -->
<intent-filter>
  <action android:name="android.intent.action.SEND_MULTIPLE" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="application/pdf" />
</intent-filter>

<!-- Images to PDF, from the gallery share sheet -->
<intent-filter>
  <action android:name="android.intent.action.SEND_MULTIPLE" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="image/*" />
</intent-filter>
```

`SEND_MULTIPLE` of PDFs opens the Merge tool pre-populated. `SEND_MULTIPLE` of images opens Images to PDF pre-populated. These two are the cheapest distribution wins in the whole app, because the OS share sheet does the marketing.

### Permission loss

A remembered URI can break: file deleted, moved, granting app uninstalled. On failure, do not crash and do not silently drop the row. Set `permissionLost = true`, show the entry greyed with "File unavailable" and a "Locate again" action that reopens the picker and rebinds the row.

---

## 5. Data model (Room)

```kotlin
@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true)])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val pageCount: Int?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val lastPage: Int = 0,
    val lastZoom: Float = 1f,
    val lastScrollY: Int = 0,
    val isFavorite: Boolean = false,
    val thumbnailPath: String?,        // cacheDir/thumbs/{id}.webp
    val permissionLost: Boolean = false,
    val hasWriteGrant: Boolean = false,
    val isEncrypted: Boolean = false,
    val folderId: Long? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,              // FK, CASCADE delete
    val pageIndex: Int,
    val label: String?,
    val createdAt: Long
)

@Entity(tableName = "folders", indices = [Index(value = ["treeUri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val addedAt: Long
)

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                  // MERGE, SPLIT, ORGANISE, IMAGES_TO_PDF, ...
    val sourceSummary: String,         // "3 documents", "report.pdf"
    val outputUri: String?,
    val outputName: String?,
    val completedAt: Long,
    val succeeded: Boolean,
    val errorMessage: String?
)
```

`operations` powers a "Recent output" strip on the Tools screen. Users lose freshly created files constantly because SAF drops them wherever the picker last was. This one table solves that complaint. Cap it at 30 rows and prune.

**Thumbnails.** Render page 0 at 320 px wide via the framework `PdfRenderer`. Encode WEBP quality 80 into `cacheDir/thumbs/`. Generate off the main thread. Never store thumbnails in `filesDir`.

**Migrations.** Room schema export on from commit one (`room.schemaLocation`). Check the schema JSON into git.

---

## 6. Document operation architecture

Design this at M0 even though it is implemented at M4. Everything in section 7.3 runs through it.

### Contract

```kotlin
sealed interface DocOperation {
    data class Merge(val sources: List<Uri>) : DocOperation
    data class Split(val source: Uri, val mode: SplitMode) : DocOperation
    data class Organise(val source: Uri, val plan: PagePlan) : DocOperation
    data class ImagesToPdf(val images: List<Uri>, val pageSize: PageSize, val fit: Fit) : DocOperation
    data class PdfToImages(val source: Uri, val pages: IntRange, val format: ImageFormat, val dpi: Int) : DocOperation
    data class SetPassword(val source: Uri, val password: String?) : DocOperation
    data class Watermark(val source: Uri, val text: String, val opts: WatermarkOptions) : DocOperation
}

interface DocOperationRunner {
    fun run(op: DocOperation, destination: Uri): Flow<OperationProgress>
}

sealed interface OperationProgress {
    data class Working(val fraction: Float?, val label: String) : OperationProgress
    data class Done(val outputUri: Uri, val pageCount: Int, val sizeBytes: Long) : OperationProgress
    data class Failed(val reason: OperationError) : OperationProgress
}
```

`PagePlan` is a single ordered list describing the output:

```kotlin
data class PagePlan(val pages: List<PageOp>)
data class PageOp(val sourceIndex: Int, val rotationDelta: Int)  // 0, 90, 180, 270
```

Deleting a page means omitting it from the list. Reordering means changing list order. Rotating means setting `rotationDelta`. Extracting means a shorter list into a new file. **One data structure, four features.** Do not write four separate code paths.

### Execution rules

1. **Never write to the destination incrementally.** Write to `cacheDir/work/{uuid}.pdf`, verify it opens and has the expected page count, then stream it to the destination URI. A crash mid-operation must leave the user's file untouched.
2. **Never modify the source in place** except for the explicit "Overwrite original" path, and even then, only after the temp file has been written and verified.
3. **Memory.** PdfBox loads structure into heap and will OOM on large documents. Always construct with `MemoryUsageSetting.setupMixed(maxMainMemoryBytes, cacheDir)`. Budget main memory from `ActivityManager.memoryClass`, not a hardcoded constant.
4. **Long operations run in WorkManager** with `setForeground` and a progress notification, so the OS does not kill a 200-page merge when the user switches apps. Short ones (under roughly 20 pages) can run in a `viewModelScope` coroutine with an in-UI progress bar. Pick the path by input page count, not by guessing.
5. **Always close `PDDocument` in a `finally`.** A leaked handle keeps a temp file alive and the cache grows without bound.
6. **Clean `cacheDir/work/` on app start** for anything older than 24 hours.

### Errors that must be handled explicitly

| Error | Behaviour |
|---|---|
| Source is password protected | Prompt for password before the operation, not during. Never store it. |
| Source has an owner password restricting assembly | Say so plainly. Do not attempt to bypass. |
| Corrupt or non-PDF file | "This file could not be read. It may be damaged." |
| Out of memory | Catch `OutOfMemoryError` at the runner boundary, fail cleanly, suggest splitting the document first. |
| Destination write failure | Delete nothing, keep the temp file, offer "Try a different location". |
| Zero pages in output plan | Block at the UI layer, do not let it reach the runner. |

### Testing

Golden-file tests. Commit small fixture PDFs to `src/test/resources`: a 5-page document, an encrypted one, a rotated one, a corrupt one, one with an outline. For each operation, assert output page count, page order via extracted text markers, and rotation values. This is the only part of the app where tests genuinely pay for themselves, because silent output corruption is invisible until a user's document is wrong.

---

## 7. Screens

Bottom navigation, two destinations: **Library** and **Tools**. The reader is a full-screen destination pushed on top, with no bottom bar.

### 7.1 Library

Segmented control: **Recent | Folders | Favourites**.

- **Recent**: ordered by `lastOpenedAt` desc. Row shows thumbnail, display name, "Page 12 of 340", relative time, file size. Long press opens a context sheet: Favourite, Rename label, Share, Organise pages, Remove from recents, Delete file.
- **Folders**: indexed trees. Tapping lists PDFs inside. Header action to add a folder.
- **Favourites**: `isFavorite = true`.

Top bar: app name, search, overflow (Sort, Grid/List, Settings). Search filters display names live. Sort by recently opened, recently added, name, size. Grid/list persisted.

FAB: "Open PDF", launches the SAF picker.

Empty state: "No documents yet. Open a PDF to get started." plus the same button label as the FAB.

### 7.2 Reader

The page is the hero. Chrome is thin and disappears.

- Hosts `PdfViewerFragment`.
- **Immersive by default.** Single tap in the centre third toggles system bars and chrome. Chrome auto-hides after 3 seconds, cancelled by touching the chrome itself.
- **Top bar**: back, document name (truncate the middle, not the end, so `report_2026_final.pdf` stays distinguishable), search, outline, overflow.
- **Bottom bar**: page slider with a live "142 / 340" readout. Dragging shows a thumbnail preview of the target page in a floating card above the thumb.
- **Overflow**: Jump to page, Add bookmark, Bookmarks, Organise pages, Page display mode, Reading mode, Keep screen on, Share, Document info.

"Organise pages" is the bridge between reader and tools. It opens the organiser (7.3.1) on the current document and returns to the reader on the new file afterwards.

**Reading modes:** continuous vertical scroll (default), page by page horizontal.

**Page display modes** (applied to the rendered page, independent of app theme): Normal; Night (invert via `ColorMatrix`, then desaturate 15% so inverted colour images are not lurid); Sepia (multiply with `#F4ECD8`); Grayscale. Persist per document with a global default.

**Search:** query bar replaces the top bar, shows "3 of 27" with previous/next chevrons, matches highlighted in the accent at 35% alpha. Back returns to normal chrome without losing position.

**Outline:** modal bottom sheet, document TOC indented by level, current section marked. If the document has no outline, show a page thumbnail grid instead. Never show an empty sheet.

**Bookmarks:** user bookmarks, separate from the outline. Bottom sheet with page number, optional label, thumbnail. Swipe to delete.

**Brightness:** optional vertical drag on the left edge, window brightness only. Off by default, because it conflicts with page navigation.

**Position restore:** restore `lastPage`, `lastScrollY`, `lastZoom` on open. Write back on pause, debounced at 500 ms, not on every scroll event.

**Password-protected documents:** prompt on open, do not store.

### 7.3 Tools

A plain list of tools, not a grid of coloured tiles. Each row: name, one-line description. Above it, a "Recent output" strip from the `operations` table showing the last few files produced, each tappable to open or share. Empty until something has been made.

Every tool follows the same three-step shape: **pick input, configure, save**. Same layout, same button positions, same progress treatment. Learn one, know all six.

#### 7.3.1 Organise pages

The centrepiece. Covers rotate, delete, reorder, and extract in one screen, backed by `PagePlan`.

- Grid of page thumbnails, 3 columns portrait, page number badge on each.
- Tap to select, long press to enter multi-select, drag to reorder.
- Action bar when anything is selected: Rotate left, Rotate right, Delete, Extract to new file, Duplicate.
- Deleted pages stay visible for the session, dimmed with a strikethrough, so the change is reversible before saving. Undo and redo in the top bar.
- Page count and "3 changes" indicator in the top bar.
- Save: "Save as new file" (default) or "Overwrite original" (only when `hasWriteGrant`).

Thumbnails render lazily via the framework `PdfRenderer` at 200 px wide, LRU-cached in memory, capped by page count.

#### 7.3.2 Merge

- Ordered list of chosen documents, drag to reorder, swipe to remove, "Add file" at the bottom.
- Each row shows name, page count, size.
- Optionally expand a document to select a page range rather than all pages.
- Total output page count shown live.
- Pre-populated when launched from a `SEND_MULTIPLE` intent.

#### 7.3.3 Split

Three modes, radio selection:
- **By page ranges**: free text like `1-4, 8, 12-20`, parsed and validated live with a plain-language preview ("3 files: 4 pages, 1 page, 9 pages").
- **Every N pages**: numeric input.
- **Extract selection**: hands off to the organiser.

Multi-file output goes to a folder chosen with `ACTION_OPEN_DOCUMENT_TREE`, not `ACTION_CREATE_DOCUMENT`.

#### 7.3.4 Images to PDF

- Reorderable image grid, add from the picker or camera.
- Page size: Fit to image, A4, Letter.
- Fit mode: Fit (letterbox) or Fill (crop).
- Margin: none, small, medium.
- Optional per-image rotation.
- Downscale images above 2000 px on the long edge before embedding, otherwise output files reach hundreds of MB.
- Pre-populated when launched from an image `SEND_MULTIPLE` intent.

#### 7.3.5 PDF to images

- Page range, format (PNG or JPEG), quality slider for JPEG, DPI (72 / 150 / 300).
- Output to a chosen tree URI.
- Warn before exporting more than 50 pages.
- Uses the framework `PdfRenderer`, not PdfBox.

#### 7.3.6 Password

- Add a password, change it, or remove it (requires the current one).
- Explain in one line what the password does: it stops the document opening, and it is not recoverable if forgotten.
- AES-256 via `StandardProtectionPolicy`.

### 7.4 Settings

Grouped list, plain, no icons.

**Appearance**: Theme (System, Light, Dark, Black, Sepia); Accent colour (Supporter); Use system colours, off by default; App icon (Supporter).

**Reading**: Default reading mode; Default page display mode; Keep screen on; Edge-drag brightness; Double tap to zoom; Volume keys turn pages.

**Tools**: Default save location (remembered tree URI, or always ask); Default output naming (`{name}_merged` style pattern); Confirm before overwriting an original, on by default.

**Library**: Default sort; Clear recents; Clear thumbnail cache with current size shown.

**About**: Version, source link, licence, Supporter (play) or Donate (foss).

### 7.5 Supporter (`play` flavour only)

One screen. Honest copy, no dark patterns, no countdown, no "limited offer".

The app is free, has no ads, and every feature works. If it is useful, you can pay for it. What you get is cosmetic.

- **Supporter** unlock, one time, roughly 3 to 5 EUR. Grants 6 accent colours, 3 alternate app icons, extra highlight colour sets, a "Supporter" line in About.
- Three optional consumable tiers for repeat giving. Same unlock.
- "Restore purchases", always present.

Never gate a reading or editing feature. Never show this screen unprompted. It lives in Settings and About, nowhere else.

---

## 8. Design system

### Direction

The subject is documents and reading. The app's job is to disappear. Colour lives in the library, tools, and settings, and drains almost entirely out of the reader. Spend the boldness in one place: the page slider's thumbnail preview. Keep everything else quiet.

Explicitly avoid: cream background with a terracotta accent; all-caps eyebrow labels; arrows appended to button text; identical rounded cards for everything; gradient washes; coloured tool tiles that make the Tools tab look like a utility-app ad farm.

### Colour tokens

Define as a Compose `ColorScheme` per theme in `ui/theme/Color.kt`. Do not use Material's baseline palette.

**Light**
```
background       #FAFAF8
surface          #FFFFFF
surfaceVariant   #EFEFEA
onBackground     #1A1A17
onSurfaceVariant #5C5C55
outline          #D6D6D0
```

**Dark** (slightly cool, so long sessions do not feel like a void)
```
background       #16181A
surface          #1E2124
surfaceVariant   #2A2E32
onBackground     #E4E4E0
onSurfaceVariant #9BA1A6
outline          #3A3F44
```

**Black** (OLED, a distinct theme, not a dark variant)
```
background       #000000
surface          #0A0A0A
surfaceVariant   #141414
onBackground     #E0E0DC
onSurfaceVariant #8A8A85
outline          #262626
```

**Sepia** (a reading convention, earned here rather than decorative)
```
background       #F4ECD8
surface          #FBF5E6
surfaceVariant   #E8DEC5
onBackground     #4A3F2F
onSurfaceVariant #7A6A52
outline          #D4C6A8
```

**Accents.** Free default:
```
teal  #3A6E5F (light) / #7FB6A4 (dark, black) / #2F6B57 (sepia, warmer)
```

Supporter accents, light and dark tone each:
```
amber    #C8862A / #E0A85C
crimson  #B4443C / #D97169
indigo   #4A5A9E / #8494D4
violet   #7A5AA8 / #AE93D6
forest   #3D7A4E / #74B486
slate    #5A6B78 / #97A8B4
```

Accent is used for selected state, search match highlight, slider thumb, FAB, and page selection in the organiser. Nothing else. It never becomes a background wash.

**Destructive state.** Page deletion in the organiser uses `onSurfaceVariant` with a strikethrough, not red. Red is reserved for the single confirm dialog on "Overwrite original".

### Type

System font family. Deliberate, not lazy: zero APK bytes, instant cold start, and it respects the user's own font size setting, which matters in a reading app. Set an explicit scale rather than accepting Material's.

```
displaySmall   28sp / 34sp  w500   Section headers
titleLarge     20sp / 26sp  w500   Top bar titles
titleMedium    16sp / 22sp  w500   Document names, tool names
bodyLarge      15sp / 22sp  w400   Settings rows, sheet content
bodyMedium     14sp / 20sp  w400   Tool descriptions, secondary text
labelMedium    13sp / 16sp  w500   Page counters, chips, page badges
labelSmall     12sp / 16sp  w400   Timestamps, file sizes
```

Sentence case everywhere. No all-caps labels. Letter spacing 0 except `labelMedium` at 0.1sp.

### Spacing and shape

4dp base unit. Screen horizontal padding 16dp. List row vertical padding 12dp. Section gap 24dp.

Radii carry hierarchy rather than being uniform: sheets 20dp top corners, cards and thumbnails 10dp, buttons 10dp, chips full round, dialogs 24dp.

No drop shadows. Separate surfaces with `surfaceVariant` fills and 1dp `outline` hairlines. Elevation only on the FAB.

### Motion

Motion answers actions, it does not decorate.

- Chrome show and hide: 180 ms fade plus 8dp vertical slide.
- Organiser reorder: standard drag lift and settle, no bounce.
- Sheets: standard Material 3 sheet motion, unmodified.
- No entrance animations on library rows. No press-scale on cards.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and disable non-essential motion.

### Copy rules

- Buttons name the outcome: "Open PDF", "Save as new file", "Add folder", "Locate again". Not "Submit", not "OK" where a verb fits.
- Errors say what happened and what to do: "This file could not be opened. It may have been moved or deleted." plus "Locate again".
- Operation results say where the file went, with the filename, and offer Open and Share inline. Never a bare "Success".
- Empty states are invitations, not apologies.
- Never use the word "premium".

---

## 9. Billing (`play` flavour)

**Products**

| ID | Type | Purpose |
|---|---|---|
| `supporter_unlock` | one-time, non-consumable | The unlock |
| `tip_small` | consumable | Optional repeat giving |
| `tip_medium` | consumable | |
| `tip_large` | consumable | |

**Flow**

1. `BillingClient` v8+, pending purchases enabled.
2. On app start and `ON_RESUME`, call `queryPurchasesAsync(INAPP)`.
3. If `supporter_unlock` is `PURCHASED` and acknowledged, write `isSupporter = true` to DataStore.
4. **Acknowledge every purchase within 3 days** via `acknowledgePurchase` or Google auto-refunds it. This is the single most common billing bug. Acknowledge immediately on receipt.
5. Consume tip products immediately with `consumeAsync`.
6. `queryProductDetailsAsync` for prices. Never hardcode a price string; Play returns localised formatted prices.

**Offline behaviour.** The DataStore flag is the source of truth for the UI. Never block a cosmetic behind a live billing query. If the network is down, supporters stay supporters.

**No server.** No backend receipt validation, no webhook, no database. This is a tip jar on an offline utility. Local verification is trivially bypassable and that is an acceptable trade for zero infrastructure and zero running cost.

---

## 10. Repo layout

```
app/
  src/main/java/com/<domain>/pdf/
    App.kt
    MainActivity.kt
    di/AppContainer.kt
    data/
      db/       AppDatabase.kt, DocumentDao.kt, BookmarkDao.kt, FolderDao.kt,
                OperationDao.kt, entities/
      prefs/    SettingsRepository.kt
      docs/     DocumentRepository.kt, FolderIndexer.kt, ThumbnailGenerator.kt
      billing/  SupporterRepository.kt              (interface)
      pdf/
        read/   PdfEngine.kt, AndroidXPdfEngine.kt
        write/  DocOperation.kt, DocOperationRunner.kt, PdfBoxOperationRunner.kt,
                PagePlan.kt, OperationWorker.kt, PdfBoxInitializer.kt
    ui/
      theme/    Color.kt, Theme.kt, Type.kt, Shape.kt
      library/  LibraryScreen.kt, LibraryViewModel.kt, components/
      reader/   ReaderScreen.kt, ReaderViewModel.kt, PdfViewerHost.kt, sheets/
      tools/
        ToolsScreen.kt
        organise/  OrganiseScreen.kt, OrganiseViewModel.kt
        merge/     MergeScreen.kt, MergeViewModel.kt
        split/     SplitScreen.kt, SplitViewModel.kt
        images/    ImagesToPdfScreen.kt, PdfToImagesScreen.kt
        password/  PasswordScreen.kt
        common/    ToolScaffold.kt, ProgressSheet.kt, SaveDestinationPicker.kt
      settings/ SettingsScreen.kt, SettingsViewModel.kt
      supporter/SupporterScreen.kt
      common/   shared composables
    util/
  src/play/java/.../data/billing/PlayBillingSupporterRepository.kt
  src/foss/java/.../data/billing/AlwaysUnlockedSupporterRepository.kt
  src/foss/AndroidManifest.xml
  src/test/resources/fixtures/          golden-file test PDFs
fastlane/metadata/android/en-US/
gradle/libs.versions.toml
```

Single module. Do not split into feature modules; the build-time cost is not worth it at this size.

`ToolScaffold` is load-bearing. Every tool screen uses it: input section, config section, sticky save bar, shared progress sheet. If a tool needs to break the scaffold, that is a signal the tool is wrong, not the scaffold.

---

## 11. Explicit non-goals for v1

Do not build these. If a decision seems to require one, stop and flag it.

- **Compression.** Genuinely hard: it means walking every `PDImageXObject`, re-encoding at lower quality, and rewriting the resource dictionary. Rasterising pages instead destroys the text layer and is not acceptable. This is a v2 project of its own, not a tool row.
- Editing text inside the PDF content stream
- Annotations, highlighting, freehand drawing, signatures (v2, via `EditablePdfViewerFragment`)
- Form filling (v2, PdfBox `PDAcroForm` handles it, but the UI is a real project)
- Redaction (must strip content, not draw black boxes; do it properly or not at all)
- OCR
- Conversion to or from Office formats
- Cloud sync, accounts, sign-in of any kind
- Analytics or crash reporting
- A custom file browser over raw filesystem paths

---

## 12. Milestones

Each ends with a working, installable app.

**M0. Skeleton**
Project, flavours, version catalog, theme system, bottom navigation with both tabs, DataStore settings, Settings screen with live theme switching. `DocOperation`, `PagePlan`, and `DocOperationRunner` defined as interfaces with no implementation. SAF read and write helpers written and unit tested.
*Done when:* both flavours build and install, all five themes switch live, the Tools tab exists and is empty, and no PDF library is wired in yet.

**M1. Read a file**
`PdfEngine` interface, `androidx.pdf` implementation, reader screen, SAF picker, VIEW and SEND intent filters.
*Done when:* a PDF opens from the in-app picker, from a file manager's "Open with", and from a share sheet. Zoom and scroll work. Position restores after leaving and returning.

**M2. Library**
Room, recents, thumbnail generation, favourites, folder trees, search, sort, grid and list.
*Done when:* opened documents appear in Recent with thumbnails and correct page positions. A folder can be added and browsed. A deleted file shows the unavailable state rather than crashing.

**M3. Reader depth**
Text search, outline sheet, user bookmarks, jump to page, page display modes, immersive chrome, slider thumbnail preview, keep screen on, volume key paging.
*Done when:* every feature in 7.2 works and chrome auto-hide feels right on a real device.

**M4. Write foundation and the organiser**
PdfBox wired in with lazy init and mixed memory settings. `PdfBoxOperationRunner`. `OperationWorker`. Temp-then-commit write path. Golden-file test fixtures and the first tests. Organise pages screen with rotate, delete, reorder, extract, undo and redo. `operations` table and the Recent output strip.
*Done when:* a 300-page document can be reordered and saved without OOM, the source file is provably untouched on a mid-operation kill, and the golden-file tests pass.

**M5. Remaining tools**
Merge, Split, Images to PDF, PDF to images, Password. `SEND_MULTIPLE` intent filters for PDFs and images. `ToolScaffold` shared across all of them.
*Done when:* every tool in 7.3 works, share-sheet entry pre-populates Merge and Images to PDF, and every error in the section 6 table is reachable and handled.

**M6. Supporter**
Billing repository, both flavour implementations, Supporter screen, accent colours, alternate icons, restore purchases.
*Done when:* a test purchase unlocks accents, survives reinstall via restore, and the `foss` flavour has all cosmetics free with no billing code compiled in.

**M7. Release**
Adaptive icon, R8 rules, baseline profile, Play listing, F-Droid fastlane metadata, signing config, screenshots.
*Done when:* a signed release bundle uploads to internal testing and the F-Droid build recipe passes locally.

M0 through M3 is a shippable reader on its own. If the project stalls, ship there.

---

## 13. Quality floor

- Rotation and process death preserve reader state and unsaved organiser state. Test with "Don't keep activities" on.
- An operation in progress survives the app going to background.
- TalkBack: every icon button has a `contentDescription`. The page slider announces its value. Organiser pages announce page number and selection state.
- All text scales to 200% font size without clipping.
- Dark and Black themes pass 4.5:1 contrast for body text.
- No `!!` in production code paths. No `runBlocking` on the main thread.
- `StrictMode` in debug with disk and network penalties logged.
- Every URI-touching function handles `SecurityException` and `FileNotFoundException`.
- Unit tests on `DocumentRepository`, `SettingsRepository`, position restore, page-range parsing, and every `DocOperation`. Do not chase coverage on the UI.

---

## 14. Known risks

1. **`androidx.pdf` is alpha.** APIs will break between versions. Contained by `PdfEngine`, but the `PdfViewerFragment` host is still exposed. Pin the version, upgrade deliberately, read release notes each time.
2. **Alpha feature gaps.** Verify at M1 that text search and outline extraction actually work through the public API at your version. If either is missing, that milestone shifts and the PDFium fallback (`mhiew/android-pdf-viewer`; `barteksc`'s original is abandoned) becomes more attractive.
3. **PdfBox memory pressure is the main technical risk in this project.** It is a JVM library ported to a phone. Large or image-heavy documents will OOM if `MemoryUsageSetting` is wrong. Test against a 500-page scanned document early, at M4, not at M5.
4. **PdfBox is PDFBox 2.0.x.** Documentation and Stack Overflow answers for PDFBox 3.x will not compile. Check the API version before copying any snippet.
5. **Silent output corruption is the worst failure mode here.** A merge that loses a page or drops an embedded font is invisible until a user's document is wrong in front of someone else. This is what the golden-file tests are for. Do not skip them to save time.
6. **F-Droid inclusion is not automatic.** It needs a reproducible build recipe and a merge request to their metadata repo. Budget separate time and do not block the Play release on it.
7. **Name and package.** Pick before M0; `applicationId` cannot change after a Play upload. Something short and non-descriptive beats a keyword-stuffed "PDF Reader Editor Pro".
