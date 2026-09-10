package com.leaf.app.data.pdf.write

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.leaf.app.data.ops.OperationsRepository
import com.leaf.app.util.saf.SafAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/** One operation the UI can watch. Only one runs at a time. */
data class ActiveOperation(
    val type: String,
    val summary: String,
    val progress: OperationProgress,
    /** Present when a destination write failed and the verified temp file is still on disk. */
    val retryTemp: File? = (progress as? OperationProgress.Failed)?.let { (it.reason as? OperationError.DestinationWriteFailed)?.tempFile },
    val pages: Int = 0,
)

/**
 * Picks the execution path by input size, not by guessing: short jobs run in-process,
 * long ones go to WorkManager under a foreground notification. Jobs that need a
 * password always stay in-process, because WorkManager input is written to disk.
 */
class OperationLauncher(
    private val context: Context,
    private val runner: PdfBoxOperationRunner,
    private val operations: OperationsRepository,
    private val saf: SafAccess,
    private val scope: CoroutineScope,
) {
    private val _active = MutableStateFlow<ActiveOperation?>(null)
    val active: StateFlow<ActiveOperation?> = _active

    private var job: Job? = null

    /**
     * ACTION_CREATE_DOCUMENT makes the file before anything runs, so a failed job would
     * leave an empty document behind. Never set for a tree or for an overwrite target.
     */
    private var pendingOutput: Uri? = null

    val isBusy: Boolean get() = _active.value?.progress is OperationProgress.Working

    fun launch(op: DocOperation, destination: Uri, passwords: Map<Uri, String>, inputPages: Int, summary: String) {
        if (isBusy) return
        val type = OperationCodec.typeOf(op)
        pendingOutput = destination.takeIf { op.writesSingleFile() && destination !in op.inputUris() }
        // Anything carrying a password stays in memory; WorkManager would persist the spec.
        if (inputPages > LONG_JOB_PAGES && passwords.isEmpty() && op !is DocOperation.SetPassword) {
            enqueue(op, destination, type, summary)
            return
        }
        _active.value = ActiveOperation(type, summary, OperationProgress.Working(null, "Preparing"))
        job?.cancel()
        job = scope.launch {
            runner.run(op, destination, passwords)
                .onEach { progress ->
                    _active.value = ActiveOperation(type, summary, progress, pages = pagesOf(progress))
                    when (progress) {
                        is OperationProgress.Done -> operations.recordSuccess(type, summary, progress.outputs)
                        is OperationProgress.Failed -> {
                            operations.recordFailure(type, summary, OperationErrors.describe(context, progress.reason))
                            discardOutput(progress.reason)
                        }
                        is OperationProgress.Working -> Unit
                    }
                }
                .collect()
        }
    }

    /** After a destination write failure, try again somewhere else with the kept temp file. */
    fun retryCommit(temp: File, destination: Uri) {
        val current = _active.value ?: return
        job?.cancel()
        job = scope.launch {
            runner.commitAgain(temp, destination, current.pages)
                .onEach { progress ->
                    val pages = if (progress is OperationProgress.Done) pagesOf(progress) else current.pages
                    _active.value = current.copy(progress = progress, retryTemp = (progress as? OperationProgress.Failed)?.let { temp }, pages = pages)
                    if (progress is OperationProgress.Done) operations.recordSuccess(current.type, current.summary, progress.outputs)
                }
                .collect()
        }
    }

    fun cancel() {
        job?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        _active.value?.let { _active.value = it.copy(progress = OperationProgress.Failed(OperationError.Cancelled)) }
        discardOutput(OperationError.Cancelled)
    }

    fun dismiss() {
        if (!isBusy) {
            _active.value?.retryTemp?.parentFile?.deleteRecursively()
            _active.value = null
        }
    }

    private fun pagesOf(progress: OperationProgress): Int = when (progress) {
        is OperationProgress.Done -> progress.outputs.sumOf { it.pageCount }
        // The verified temp file's count survives the failure so a retried commit can report it.
        is OperationProgress.Failed -> (progress.reason as? OperationError.DestinationWriteFailed)?.pages ?: 0
        is OperationProgress.Working -> 0
    }

    /** Drop the pre-created destination of a job that produced nothing. */
    private fun discardOutput(reason: OperationError) {
        val uri = pendingOutput ?: return
        pendingOutput = null
        // A write failure keeps the temp file and offers a retry; the destination is left alone.
        if (reason is OperationError.DestinationWriteFailed) return
        scope.launch { saf.delete(uri) }
    }

    private fun enqueue(op: DocOperation, destination: Uri, type: String, summary: String) {
        val request = OneTimeWorkRequestBuilder<OperationWorker>()
            .setInputData(
                workDataOf(
                    OperationWorker.KEY_SPEC to OperationCodec.encode(op),
                    OperationWorker.KEY_DESTINATION to destination.toString(),
                    OperationWorker.KEY_SUMMARY to summary,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("tekitana")
            .build()
        val wm = WorkManager.getInstance(context)
        // KEEP would silently drop this request behind an unfinished one; queue it instead.
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        _active.value = ActiveOperation(type, summary, OperationProgress.Working(null, "Starting"))
        job?.cancel()
        job = scope.launch {
            wm.getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { infos ->
                val info = infos.firstOrNull { it.id == request.id } ?: infos.lastOrNull() ?: return@collect
                val progress: OperationProgress = when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> OperationProgress.Working(null, "Waiting")
                    WorkInfo.State.RUNNING -> {
                        val f = info.progress.getFloat(OperationWorker.KEY_FRACTION, -1f)
                        val ocrPage = info.progress.getInt(OperationWorker.KEY_OCR_PAGE, 0)
                        val ocrTotal = info.progress.getInt(OperationWorker.KEY_OCR_TOTAL, 0)
                        OperationProgress.Working(
                            if (f < 0f) null else f,
                            info.progress.getString(OperationWorker.KEY_LABEL) ?: "Working",
                            if (ocrPage > 0 && ocrTotal > 0) OperationProgress.Recognising(ocrPage, ocrTotal) else null,
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val uris = info.outputData.getStringArray(OperationWorker.KEY_OUTPUT_URIS).orEmpty()
                        val names = info.outputData.getStringArray(OperationWorker.KEY_OUTPUT_NAMES).orEmpty()
                        val pages = info.outputData.getIntArray(OperationWorker.KEY_OUTPUT_PAGES) ?: IntArray(uris.size)
                        OperationProgress.Done(
                            uris.indices.map { i -> OperationOutput(Uri.parse(uris[i]), names.getOrElse(i) { "" }, pages.getOrElse(i) { 0 }, 0L) },
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        val temp = info.outputData.getString(OperationWorker.KEY_ERROR_TEMP)?.let(::File)
                        if (temp != null && temp.exists()) {
                            OperationProgress.Failed(OperationError.DestinationWriteFailed(temp, info.outputData.getInt(OperationWorker.KEY_ERROR_PAGES, 0)))
                        } else {
                            OperationProgress.Failed(OperationError.Unknown(info.outputData.getString(OperationWorker.KEY_ERROR)))
                        }
                    }
                    WorkInfo.State.CANCELLED -> OperationProgress.Failed(OperationError.Cancelled)
                }
                _active.value = ActiveOperation(type, summary, progress, pages = pagesOf(progress))
                if (progress is OperationProgress.Failed) discardOutput(progress.reason)
                // Nothing more will come for this request; stop watching instead of lingering.
                if (info.state.isFinished) coroutineContext.cancel()
            }
        }
    }

    companion object {
        /** Under roughly this many input pages the job runs in a plain coroutine with an in-UI bar. */
        const val LONG_JOB_PAGES = 20
        const val WORK_NAME = "leaf-operation"
    }
}
