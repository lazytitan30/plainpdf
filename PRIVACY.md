# Privacy policy for Plain PDF Reader & Editor

Last updated: 5 September 2026

Plain PDF is a PDF reader and editor that works entirely on your device. This policy explains what the app stores, what it never does, and how to remove what it keeps.

## The short version

- Plain PDF has no account, no ads and no analytics.
- Plain PDF makes no network connections. The FOSS build does not even hold the permission to use the internet, and neither does the Google Play build.
- Extra languages for reading scanned pages are delivered by Google Play when you tap Get in Settings. The Play Store app does that download with its own connection; Plain PDF only receives the file. Google learns that you requested that language pack, nothing about your documents.
- Everything you open, scan or create stays on your device unless you choose to share or save it somewhere yourself.
- We, the developers, receive nothing from the app. We cannot see your documents, your file names or how you use the app.
- The source code is public, so anyone can check these statements.

## What the app stores on your device

Plain PDF keeps a small amount of data in its private app storage so that it can work as a library:

| Data | Why | How long |
|---|---|---|
| Links to the documents you opened, their names, page counts, your reading position, favourites, labels and bookmarks | To show your library, resume where you left off and remember bookmarks | Until you remove the document from the library or uninstall the app |
| Links to folders you added | To list the PDFs inside them | Until you remove the folder |
| A record of the last 30 tool operations (type, document name, output name, success or error text) | To show recent outputs | Rolling, oldest entries are dropped |
| Your settings (theme, reading mode, default save folder and similar) | To keep your preferences | Until uninstall |
| PDFs created with Scan | So the scan is ready to share immediately | Until you delete them from the library |
| Small first-page thumbnails | For the library view | Removed with the document, and unused ones are cleaned up automatically |
| Temporary working copies while a tool runs | Tools work on a copy and only replace your file after the result is verified | Deleted when the tool finishes; leftovers are cleared within a day |
| Camera photos taken for Scan | To build the PDF | Deleted when the scan is finished or within a day |
| A crash report, if the app ever crashes | So you can send us what went wrong, if you choose to | Kept until you share or dismiss it; nothing is sent on its own |

The crash report contains the technical error and the app version. File names, folder paths and document contents are removed from it before it is written. It leaves your phone only if you tap Share and pick where it goes.

Passwords you type to open a protected PDF are kept in memory only while the tool runs and are never written to storage.

## What the app never does

- It never uploads, syncs or transmits your documents or any information about them.
- It never reads files you did not pick. Plain PDF uses the Android file picker, so it only sees documents and folders you choose.
- It never backs up its data to Google or transfers it to a new phone. Backup is switched off for the whole app.
- It never writes document names, paths or contents to the system log in release builds.
- It never uses your location, contacts, camera roll or any other personal data. Scan uses the camera app you already have, and only receives the photo you take for it.

## Permissions

Plain PDF asks for notification permission only to show progress for long-running tools. It uses no storage permission; access to files goes through the Android file picker, one document or folder at a time, and you can revoke it by removing the item from the library.

The Google Play build additionally holds the Play Billing permission for the optional Supporter purchase. Purchases are handled by Google Play; Plain PDF only learns whether the purchase exists. See Google's own privacy policy for how Google handles payments.

## Removing your data

- Remove a document or folder from the library to delete what Plain PDF knows about it. Deleting a scan deletes the PDF as well.
- Clear the app's storage in Android settings, or uninstall the app, to remove everything at once.

## Children

Plain PDF collects no data from anyone, including children.

## Changes

If this policy changes, the new version ships with the app and is published at https://plainpdf.app/privacy. The date at the top tells you when it last changed.

## Contact

Questions about privacy: write to support@plainpdf.app. The source code is published from https://plainpdf.app/source.
