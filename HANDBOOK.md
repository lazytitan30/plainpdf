# Plain PDF: the handbook

Everything needed to keep this app alive without asking anyone. Written for two readers:
the owner, who needs to ship updates and answer users, and a developer picking the code up
cold. Where the two need different depth, the owner's version comes first.

Three other documents sit beside this one and are not repeated here:

| File | What it holds |
|---|---|
| `README.md` | How to build, in brief. The public front page of the repository. |
| `STATUS.md` | What is done, what is left, what was decided and when. Updated every working session. |
| `pdf-reader-build-spec.md` | The original design brief. History, not instruction. |

---

## 1. What this is

Plain PDF is an Android app: a PDF reader plus an offline toolkit. It reads, signs, scans,
fills in forms, merges, splits, compresses, redacts, adds page numbers, and recognises text
in scanned pages. It holds no internet permission at all, so it cannot show ads, cannot
track anyone, and cannot send a document anywhere. That is the product's whole argument.

It is free. There is an optional Supporter purchase that unlocks colours and icons only.

- Store identity: `com.plainpdf.app`
- Current version: 1.0.0, version code 7, live on Google Play
- Licence: GNU GPL v3
- Written in Kotlin with Jetpack Compose

---

## 2. The map

Everything that matters, and where it is.

### Accounts and addresses

| Thing | Where | Notes |
|---|---|---|
| Google Play Console | The TekiTana organisation account | Where releases are uploaded and reviews are read |
| Source code | `github.com/lazytitan30/plainpdf` | Public, GPL-3.0, branch `main` |
| Website source | `docs/` in this repository | Edited here, published elsewhere (below) |
| Website hosting | `github.com/lazytitan30/plainpdf-site` | Separate public repository, GitHub Pages |
| Domain | `plainpdf.app`, registered at Spaceship | DNS points at GitHub Pages: four A records plus a `www` CNAME |
| Support email | `support@plainpdf.app` | Catch-all forwarding at the registrar to a personal mailbox |

### Files that are not in the repository, and must never be

| Thing | Where | If it is lost |
|---|---|---|
| Upload keystore (`plainpdf-upload.jks`) | A `Keys` folder outside the repository | **No further update can ever be published, by anyone.** See section 7. |
| `keystore.properties` | Repository root, git-ignored | Holds the keystore password. Recreate from the template in `README.md`. |
| Built bundles | `releases/`, git-ignored | Harmless. Rebuild them. |

**Back up the `Keys` folder and put its password in a password manager.** This is the single
most important sentence in this document.

### Repository layout

```
app/                     the application
  src/main/              code and resources shared by both flavours
  src/play/              Google Play only: billing, downloadable language packs
  src/foss/              F-Droid style build: no proprietary code
  src/test/              unit tests, run on the computer, no device needed
  src/main/res/values*/  18 folders: English plus 16 translations, plus night colours
  src/main/generated/    the recorded startup profile (committed on purpose)
baselineprofile/         test-only module that records the startup profile
packs/                   20 downloadable language packs for text recognition
docs/                    the website
fastlane/                the store listing text and images, in 17 languages
scripts/release.sh       builds a release bundle
tools/make_fixtures.py   regenerates test PDFs
```

Roughly 142 Kotlin files and 143 unit tests.

---

## 3. Setting up a computer from nothing

1. **Java 17 or newer.** Any distribution. Verify with `java -version`.
2. **Android Studio.** It brings the Android SDK, the emulator and `adb`. Install SDK
   platform 37 and build tools 36 from its SDK Manager.
3. **Git**, and `gh` if you want the GitHub commands in this document to work.
4. **Clone the repository:** `git clone https://github.com/lazytitan30/plainpdf.git`
5. **Put the keystore back:** copy the `Keys` folder from your backup, then create
   `keystore.properties` at the repository root with the four lines shown in `README.md`.
   Without it the app still builds, but release builds are signed with a throwaway key and
   Google Play will reject them.
6. **Check it works:** `./gradlew :app:testFossDebugUnitTest`. Everything should pass.

On Windows, run the commands from Git Bash. Gradle needs about 4 GB of memory, which is
already set in `gradle.properties`.

---

## 4. The jobs you will actually do

### Build the app and put it on a phone

```bash
./gradlew :app:assemblePlayDebug
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
```

If both a phone and an emulator are attached, `adb` refuses to guess. Add `-s <serial>`,
which `adb devices` lists.

### Run the tests

```bash
./gradlew :app:testFossDebugUnitTest
./gradlew :app:lintFossDebug
```

Both must pass before any release. The tests need no phone. Lint treats a missing
translation as an error, so a new piece of text must be added to all 17 language files or
the build fails. That is deliberate: it stops half-translated releases.

### Cut a release and upload it

```bash
./scripts/release.sh
```

That raises the version code by one, builds the bundle, moves the previous bundle into
`releases/archive/` named by version and date, and checks the signature. The result is
always at the same path, `releases/plainpdf-play.aab`, so the upload never changes.

Then commit the version change, which the script deliberately leaves for you:

```bash
git add app/build.gradle.kts && git commit -m "Version code N: what changed"
git push
```

Then in Play Console: **Test and release > Production > Create new release**, upload
`releases/plainpdf-play.aab`, write release notes in plain language, **Next**, **Save**,
**Review release**, **Start rollout to Production**.

Release notes are read by real people. Say what changed for them, not what changed in the
code. "The Tools list now scrolls on every screen size" beats "fixed layout inset bug".

Google reviews it. The first release took four days; updates are usually faster. You get an
email when it is approved.

### Fix something and ship it

1. Make the change.
2. `./gradlew :app:testFossDebugUnitTest :app:lintFossDebug`
3. Install a debug build on a phone and see it working with your own eyes.
4. `git commit` and `git push`.
5. `./scripts/release.sh`, upload, write notes.

Do not skip step 3. Two of the bugs found after launch would have been caught by looking at
the screen.

### Update the website

The website source is `docs/` in this repository. Editing it does not publish it. To publish:

1. Edit the files in `docs/`.
2. Copy `index.html`, `privacy.html`, `support.html`, `source.html`, `CNAME` and `.nojekyll`
   into a clone of `github.com/lazytitan30/plainpdf-site`.
3. Commit and push there.
4. Commit the `docs/` change here too, so the two stay in step.

GitHub Pages republishes within a minute. The pages use no fonts, scripts or trackers from
anywhere else, which is why they load instantly and cannot leak a visitor's details. Keep it
that way.

### Answer a crash report

When the app crashes, it writes a file locally and offers to share it on the next launch.
Nothing is sent automatically. A user emails it to support@plainpdf.app.

The report names the app version, the Android version, the phone model, and the line where
it failed. That is usually enough. The report also carries a short marker line near the top
that identifies the build; leave it alone.

If the stack trace is unreadable because the release build is obfuscated, the mapping file
for each release is kept by Play Console under **Test and release > App bundle explorer >
your version > Downloads**. Play usually de-obfuscates traces for you under **Monitor and
improve > Crashes and ANRs**.

---

## 5. How the app is put together

For a developer. The owner can skip to section 6.

Single activity, Jetpack Compose, no dependency injection framework. Objects are created
once in `di/AppContainer.kt` and handed down through a composition local. That file is the
best starting point for reading the code: everything the app owns is listed there.

```
ui/            one folder per screen area: library, reader, tools, settings, scan, tour
ui/common/     shared pieces: buttons, dialogs, empty states
ui/theme/      colours, typography, shapes
data/pdf/      reading and writing PDFs. read/ wraps the viewer, write/ does the operations
data/db/       Room database: documents, folders, bookmarks, operation history
data/prefs/    DataStore settings, one file, one repository
data/billing/  the Supporter purchase, behind an interface with two implementations
util/scan/     page edge detection and cleanup, pure Kotlin, unit tested
util/ocr/      Tesseract text recognition and the downloadable language packs
util/saf/      the Android file access dance: picking, writing, naming
widget/        the home screen widget
```

**Document operations** all follow one path: work into a temporary file, verify it, then
commit it to the destination the user chose. Nothing overwrites an original until the new
file is known to be good. `data/pdf/write/` holds this, and the golden-file tests in
`app/src/test/.../PdfBoxEngineTest.kt` compare real output against fixtures.

**Long jobs** run through WorkManager under a notification, so Android does not kill them
when the user switches apps. Short ones run in process. `data/pdf/write/OperationLauncher.kt`
decides which, by input size, and always keeps password-protected work in process, because
WorkManager writes its input to disk.

**The two flavours** differ in exactly one seam each: where the Supporter entitlement comes
from, and whether language packs can be downloaded. The user interface never asks which
flavour it is running in.

**Text recognition** ships eight languages inside the app. Twenty more are Play asset packs
in `packs/`, downloaded on request by Google Play using Play's own connection, never the
app's. That is how the app can offer downloads while holding no internet permission.

---

## 6. Traps

Things that will bite. Each one already has.

**The application id and the package name are different, on purpose.** The store identity is
`com.plainpdf.app`; the Kotlin package and manifest namespace are `com.leaf.app`, left over
from an earlier working name. Renaming would be churn with no benefit, and the application id
can never change once published. The trap: any code that builds a class name from
`context.packageName` is wrong. It produced a crash on every icon change in version code 4.
Manifest components resolve against the namespace.

**Lint fails on a missing translation.** Add a new piece of text to `values/strings.xml` and
you must add it to all 16 other language folders. This is intentional.

**The emulator and Gradle fight over memory.** On a machine with limited RAM the emulator
dies whenever a Gradle build runs beside it, which is exactly what device tests do. The way
through: build first, stop the Gradle daemon with `./gradlew --stop`, boot the emulator, then
drive it with `adb` alone.

**Stop the Gradle daemon and the emulator before closing an editor** that has the project
open. Both hold files open and some editors crash on locked files.

**The Android plugin cannot be asked for a version twice.** Modules other than `app` apply it
without one: `id("com.android.test")`, not `alias(...)`. Applying it with a version fails the
whole build with a confusing message about an unknown classpath version.

**Play's purchase model has two shapes.** Newer one-time products carry purchase options and
need an offer token; older ones do not. The billing code reads both. Also: a brand new app's
products take hours to appear, and until they do the app correctly reports that Play has
nothing to sell. That is not a bug, and it cost a day to learn.

**Tips are consumed, so Google Play keeps no record of them.** The large tip grants the
Supporter pack, and that grant is therefore remembered on the phone only. A reinstall cannot
restore it, and the app says so plainly. The pack bought directly is restorable, because Play
does keep that.

**Insets are applied once, by whoever owns the top of the screen.** Each screen's own top bar
leaves room for the status bar. The application shell must not do it as well, or every screen
sits too low. The reader, which has no top bar of its own, does it itself.

**The startup profile is a recording, not generated code.** It lives at
`app/src/main/generated/baselineProfiles/baseline-prof.txt` and is committed. A unit test
guards it. Re-record it only when the startup path changes noticeably:

```bash
./gradlew :app:generateBaselineProfile
```

If that fails because the emulator dies, build the APKs first, stop Gradle, boot the emulator,
then run the recorder directly with `adb shell am instrument` against
`com.leaf.app.baselineprofile.BaselineProfileGenerator` and pull the file from the output
directory it names.

---

## 7. When something goes wrong

### A release is bad and users are hitting it

In Play Console, **Production > Releases**, you can halt a rollout. If the previous version
is still available you can resume serving it while you fix. Then ship a new version code:
you can never re-upload an old number.

### The keystore is lost

This is the serious one. Google Play identifies your app by the key it was signed with.

If Play App Signing was enabled when the app was created, Google holds the real signing key
and yours is only an upload key. In that case: **Play Console > Setup > App integrity**, and
request an upload key reset. Google issues a new one. Recovery takes a few days.

If it was not enabled, the app cannot be updated by anyone, ever, and the only path is a new
listing under a new application id, losing every install and review.

Check which situation you are in **now**, while nothing is wrong, under
**Setup > App integrity**. Then back up the keystore anyway.

### The website is down

Check three things in order: the domain is still registered and paid for; the DNS records at
Spaceship still point at GitHub Pages; and the `plainpdf-site` repository still has Pages
enabled with the custom domain and HTTPS. The certificate is issued by GitHub automatically
and occasionally needs the custom domain removed and re-added to renew.

### A build suddenly fails on a machine that worked

In order: `./gradlew --stop`, then `./gradlew clean`, then delete `.gradle/` in the project,
then check that Java is still 17 or newer. If it started after an Android Studio update, the
SDK may have moved platform 37 or build tools 36 out; reinstall them.

### Someone reports something you cannot reproduce

Ask three questions: which phone, which Android version, and what font size. Most reports so
far have been layout problems on a screen shape or text size other than yours. The emulator
can imitate both:

```bash
adb shell wm size 1080x2000
adb shell settings put system font_scale 2.0
# undo with
adb shell wm size reset
adb shell settings put system font_scale 1.0
```

---

## 8. The calendar

Nothing here is urgent, but all of it arrives eventually.

**Every year, around August.** Google raises the minimum `targetSdk` that Play accepts. When
that happens the app must be rebuilt against the newer Android and tested, or updates stop
being accepted. This is the one recurring obligation that cannot be ignored.

**Whenever a dependency has a security fix.** GitHub will email if it spots one. Otherwise
libraries can sit still for a long time; nothing here chases the newest version for its own
sake.

**When the domain renews.** Yearly, at Spaceship. If it lapses, the privacy policy link in the
store listing breaks, which Google does notice.

**When reviews arrive.** Play Console, **Ratings and reviews**. Replying is worthwhile and
takes a minute.

---

## 9. Deliberate decisions that look like mistakes

Do not "fix" these without understanding why they are the way they are. The reasoning for
each is dated in `STATUS.md`.

- **No internet permission.** Not a limitation, the entire product argument. Adding it would
  break the promise the store listing makes.
- **The application id and package name differ.** See section 6.
- **The Supporter purchase unlocks cosmetics only.** Every real feature is free for everyone,
  in every build. Do not move a feature behind it.
- **GPL v3, not a permissive licence.** Anyone may take this code, but they must publish
  theirs under the same terms, which removes the incentive to rebuild it with ads.
- **No em dashes anywhere**, in any language, in the app, the listing or the website.
- **Redaction turns pages into images.** The text really is gone, which is the point, and it
  is why redacted pages stop being searchable.
- **The welcome tour keeps its drawing page on phones that cannot draw**, with a note
  explaining why, rather than hiding the page.
- **No gesture-only actions.** Every action has a visible control, because the app is built
  for older and less confident users.

---

## 10. If you hand this to a developer

Give them this file, then point at these four things in order:

1. `di/AppContainer.kt`, to see everything the app owns in one screen.
2. `data/pdf/write/`, to see how a document operation is done safely.
3. `ui/library/LibraryScreen.kt` and `ui/reader/ReaderScreen.kt`, the two screens everything
   else hangs off.
4. `STATUS.md`, for what was decided and why.

Ask them to run `./gradlew :app:testFossDebugUnitTest` before touching anything, and to keep
it passing. The tests are the only thing standing between a small change and a bad release.
