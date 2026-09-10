package com.leaf.app.util.ocr

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The on-disk home of Tesseract language packs: `filesDir/ocr/tessdata/<code>.traineddata`.
 * Bundled packs are copied out of the APK on first use; others arrive through [import],
 * a file the user downloaded themselves, because the app has no network permission.
 */
class TessdataStore(private val context: Context) {

    /** What [OcrEngine] takes: the parent of the `tessdata` folder. */
    val dataParent: File get() = File(context.filesDir, "ocr")

    private val tessdata: File get() = File(dataParent, "tessdata")

    /**
     * Copies every bundled pack that is missing or has the wrong size. Safe to call on
     * every run; a pack that is already complete costs one stat.
     */
    @Synchronized
    fun ensureBundled() {
        tessdata.mkdirs()
        val assets = context.assets
        for (code in OcrLanguages.bundled) {
            val name = fileName(code)
            val target = File(tessdata, name)
            val expected = runCatching { assets.openFd("$ASSET_DIR/$name").use { it.length } }.getOrDefault(-1L)
            if (target.exists() && expected >= 0 && target.length() == expected) continue
            val temp = File(tessdata, "$name.part")
            try {
                assets.open("$ASSET_DIR/$name").use { i -> temp.outputStream().use { o -> i.copyTo(o) } }
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) throw IOException("Could not install $name")
                }
            } catch (e: IOException) {
                temp.delete()
                throw e
            }
        }
    }

    /** Codes with a pack on disk, bundled ones first and in catalogue order, then imports by name. */
    fun installed(): List<String> {
        val onDisk = tessdata.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }
            ?.map { it.name.removeSuffix(EXTENSION) }
            ?.filter { OcrLanguages.isValidCode(it) }
            .orEmpty()
        val bundled = OcrLanguages.known.map { it.code }.filter { it in OcrLanguages.bundled }
        val imported = onDisk.filter { it !in OcrLanguages.bundled }.sorted()
        return bundled + imported
    }

    fun isInstalled(code: String): Boolean = code in OcrLanguages.bundled || File(tessdata, fileName(code)).isFile

    /**
     * Copies a picked `.traineddata` file in, after Tesseract has agreed to load it from a
     * scratch folder. Returns the language code, taken from the file name.
     */
    fun import(uri: Uri, resolver: ContentResolver): Result<String> {
        val displayName = displayName(uri, resolver) ?: return Result.failure(IOException("The file has no name"))
        if (!displayName.endsWith(EXTENSION)) return Result.failure(IOException("Pick a .traineddata file"))
        val code = displayName.removeSuffix(EXTENSION).lowercase()
        if (!OcrLanguages.isValidCode(code)) return Result.failure(IOException("\"$displayName\" is not a Tesseract language file name"))
        if (code in OcrLanguages.bundled) return Result.failure(IOException("${OcrLanguages.nameOf(code)} is already included"))

        val scratch = File(dataParent, "import-${UUID.randomUUID()}")
        val scratchData = File(scratch, "tessdata").apply { mkdirs() }
        val candidate = File(scratchData, fileName(code))
        return try {
            val input = resolver.openInputStream(uri) ?: throw IOException("The file could not be opened")
            input.use { i -> candidate.outputStream().use { o -> i.copyTo(o) } }
            if (candidate.length() == 0L) throw IOException("The file is empty")
            if (!trialInit(scratch, code)) throw IOException("Tesseract could not load \"$displayName\"")
            tessdata.mkdirs()
            val target = File(tessdata, fileName(code))
            target.delete()
            if (!candidate.renameTo(target)) throw IOException("Could not store the language file")
            Result.success(code)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(IOException("No permission to read the file", e))
        } catch (e: RuntimeException) {
            Result.failure(IOException("Tesseract rejected the file: ${e.message}", e))
        } finally {
            scratch.deleteRecursively()
        }
    }

    /**
     * Installs a pack that Google Play delivered: Tesseract must load it from a scratch
     * folder first, then it moves next to the others. Returns the code on success.
     */
    fun installFromFile(code: String, source: File): Result<String> {
        if (!OcrLanguages.isValidCode(code)) return Result.failure(IOException("Bad language code"))
        if (!source.isFile || source.length() == 0L) return Result.failure(IOException("The language file is missing"))
        val scratch = File(dataParent, "pack-${UUID.randomUUID()}")
        val scratchData = File(scratch, "tessdata").apply { mkdirs() }
        val candidate = File(scratchData, fileName(code))
        return try {
            source.inputStream().use { i -> candidate.outputStream().use { o -> i.copyTo(o) } }
            if (!trialInit(scratch, code)) throw IOException("Tesseract could not load ${fileName(code)}")
            tessdata.mkdirs()
            val target = File(tessdata, fileName(code))
            target.delete()
            if (!candidate.renameTo(target)) throw IOException("Could not store the language file")
            Result.success(code)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: RuntimeException) {
            Result.failure(IOException("Tesseract rejected the file: ${e.message}", e))
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** Removes an imported pack. Bundled packs stay. */
    fun delete(code: String): Boolean {
        if (code in OcrLanguages.bundled || !OcrLanguages.isValidCode(code)) return false
        return File(tessdata, fileName(code)).delete()
    }

    private fun trialInit(parent: File, code: String): Boolean {
        val api = TessBaseAPI()
        return try {
            api.init(parent.absolutePath, code, TessBaseAPI.OEM_LSTM_ONLY)
        } finally {
            runCatching { api.recycle() }
        }
    }

    private fun displayName(uri: Uri, resolver: ContentResolver): String? {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val EXTENSION = ".traineddata"
        private const val ASSET_DIR = "tessdata"

        fun fileName(code: String): String = "$code$EXTENSION"
    }
}
