package com.leaf.app.di

import android.app.Application
import com.leaf.app.R
import com.leaf.app.data.billing.SupporterRepository
import com.leaf.app.data.billing.createSupporterRepository
import com.leaf.app.data.db.AppDatabase
import com.leaf.app.data.docs.BookmarkRepository
import com.leaf.app.data.docs.DocumentRepository
import com.leaf.app.data.docs.FolderRepository
import com.leaf.app.data.docs.SafDocumentProbe
import com.leaf.app.data.docs.ThumbnailGenerator
import com.leaf.app.data.ops.OperationsRepository
import com.leaf.app.data.pdf.write.OperationLauncher
import com.leaf.app.data.pdf.write.PdfBoxOperationRunner
import com.leaf.app.data.prefs.Settings
import com.leaf.app.data.pdf.read.AndroidXPdfEngine
import com.leaf.app.data.pdf.read.PdfEngine
import com.leaf.app.data.prefs.SettingsRepository
import com.leaf.app.data.prefs.settingsDataStore
import com.leaf.app.util.ContinueShortcuts
import com.leaf.app.util.ocr.LanguagePackSource
import com.leaf.app.util.ocr.OcrLanguages
import com.leaf.app.util.ocr.TessdataStore
import com.leaf.app.util.ocr.createLanguagePackSource
import kotlinx.coroutines.flow.first
import com.leaf.app.util.saf.SafAccess
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hand-rolled dependency graph. Everything is created lazily and lives for the process.
 * Flavour-specific pieces come from [createSupporterRepository], which each flavour defines.
 */
class AppContainer(private val app: Application) {

    // Background work must never take the process down; log and carry on.
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("Leaf", "Uncaught in appScope", e) },
    )

    val settings: SettingsRepository by lazy { SettingsRepository(app.settingsDataStore) }

    private val fallbackDocumentName: String get() = app.getString(R.string.document_fallback_name)

    val saf: SafAccess by lazy { SafAccess(app.contentResolver, ownAuthority = "${app.packageName}.files", fallbackName = fallbackDocumentName) }

    val database: AppDatabase by lazy { AppDatabase.create(app) }

    val documents: DocumentRepository by lazy {
        DocumentRepository(
            database.documentDao(),
            SafDocumentProbe(saf, ownAuthority = "${app.packageName}.files"),
            fallbackDisplayName = fallbackDocumentName,
            shortcuts = ContinueShortcuts(app),
        )
    }

    val bookmarks: BookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }

    val folders: FolderRepository by lazy { FolderRepository(database.folderDao(), documents, app.contentResolver, saf) }

    val thumbnails: ThumbnailGenerator by lazy { ThumbnailGenerator(app, app.contentResolver) }

    val pdfEngine: PdfEngine by lazy { AndroidXPdfEngine(app) }

    val operations: OperationsRepository by lazy { OperationsRepository(database.operationDao()) }

    val operationRunner: PdfBoxOperationRunner by lazy {
        PdfBoxOperationRunner(app, saf, outputPattern = { cachedOutputPattern })
    }

    val operationLauncher: OperationLauncher by lazy { OperationLauncher(app, operationRunner, operations, saf, appScope) }

    /** Tesseract language packs on disk; the runner installs the bundled ones on first use. */
    val tessdata: TessdataStore by lazy { TessdataStore(app) }

    /**
     * Language packs for text recognition. A freshly downloaded language switches itself on
     * for recognition, so the person who tapped Get never has to find a second setting.
     */
    val languagePacks: LanguagePackSource by lazy {
        createLanguagePackSource(app, tessdata, appScope) { code ->
            appScope.launch {
                val current = OcrLanguages.split(settings.settings.first().ocrLanguages)
                if (code !in current) settings.setOcrLanguages(OcrLanguages.join(current + code))
            }
        }
    }

    /** Kept current by [startBackgroundMaintenance]; read by the runner off the main thread. */
    @Volatile
    private var cachedOutputPattern: String = Settings.DEFAULT_OUTPUT_PATTERN

    /** Cheap housekeeping on app start: mirror the naming pattern and drop stale work files. */
    fun startBackgroundMaintenance() {
        appScope.launch {
            settings.settings.collect { cachedOutputPattern = it.outputNamePattern }
        }
        appScope.launch { operationRunner.cleanWorkDir() }
        appScope.launch {
            // Camera captures older than a day were either turned into a PDF or abandoned.
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(app.cacheDir, "camera").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            // Pages exported for the share sheet; the receiving app has long since read them.
            File(app.cacheDir, "work/share").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            // Thumbnails of documents that left the library.
            val referenced = documents.listWithThumbnails().mapNotNull { it.thumbnailPath }.toSet()
            thumbnails.pruneOrphans(referenced)
        }
    }

    val supporter: SupporterRepository by lazy { createSupporterRepository(app, settings, appScope) }
}
