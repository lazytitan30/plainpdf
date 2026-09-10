package com.leaf.app.data.pdf.write

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Runs a [DocOperation] and streams progress. Implementations write to a temp file first,
 * verify it, and only then commit to [destination]. The user's source file is never touched
 * unless the caller explicitly passes it as the destination for an overwrite.
 *
 * For multi-file outputs (split, PDF to images) [destination] is a tree URI.
 */
interface DocOperationRunner {
    fun run(op: DocOperation, destination: Uri): Flow<OperationProgress>
}

sealed interface OperationProgress {
    /** [recognising] is set while OCR runs on a page, so a UI can show a localised label for that step. */
    data class Working(val fraction: Float?, val label: String, val recognising: Recognising? = null) : OperationProgress
    data class Recognising(val page: Int, val total: Int)
    data class Done(val outputs: List<OperationOutput>) : OperationProgress
    data class Failed(val reason: OperationError) : OperationProgress
}

data class OperationOutput(val uri: Uri, val displayName: String, val pageCount: Int, val sizeBytes: Long)

sealed interface OperationError {
    data object PasswordRequired : OperationError
    data object AssemblyRestricted : OperationError
    data object Corrupt : OperationError
    data object OutOfMemory : OperationError
    /** Temp file is kept so the user can retry with a different destination. */
    /** [pages] of the verified temp file, so a retried commit can describe its output. */
    data class DestinationWriteFailed(val tempFile: File, val pages: Int = 0) : OperationError
    data object Cancelled : OperationError
    data class Unknown(val message: String?) : OperationError
}
