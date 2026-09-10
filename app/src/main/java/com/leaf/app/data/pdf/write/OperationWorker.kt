package com.leaf.app.data.pdf.write

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.leaf.app.R
import com.leaf.app.appContainer
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach

/**
 * Runs a long operation under a foreground notification so the OS does not kill a
 * 200-page merge when the user switches apps. Input never contains passwords; encrypted
 * sources take the in-process path instead.
 */
class OperationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val spec = inputData.getString(KEY_SPEC) ?: return Result.failure()
        val destination = inputData.getString(KEY_DESTINATION)?.let(Uri::parse) ?: return Result.failure()
        val summary = inputData.getString(KEY_SUMMARY) ?: ""
        val op = OperationCodec.decode(spec)
        val type = OperationCodec.typeOf(op)
        val container = applicationContext.appContainer

        runCatching { setForeground(foregroundInfo(detail = null)) }

        val result = container.operationRunner.run(op, destination)
            .onEach { progress ->
                if (progress is OperationProgress.Working) {
                    val recognising = progress.recognising
                    setProgress(
                        workDataOf(
                            KEY_FRACTION to (progress.fraction ?: -1f),
                            KEY_LABEL to progress.label,
                            KEY_OCR_PAGE to (recognising?.page ?: 0),
                            KEY_OCR_TOTAL to (recognising?.total ?: 0),
                        ),
                    )
                    val detail = recognising?.let { applicationContext.getString(R.string.scan_recognising, it.page, it.total) }
                        ?: progress.label.takeIf { it.isNotBlank() }
                    runCatching { setForeground(foregroundInfo(detail, progress.fraction)) }
                }
            }
            .last()

        return when (result) {
            is OperationProgress.Done -> {
                container.operations.recordSuccess(type, summary, result.outputs)
                Result.success(
                    workDataOf(
                        KEY_OUTPUT_URIS to result.outputs.map { it.uri.toString() }.toTypedArray(),
                        KEY_OUTPUT_NAMES to result.outputs.map { it.displayName }.toTypedArray(),
                        KEY_OUTPUT_PAGES to result.outputs.map { it.pageCount }.toIntArray(),
                    ),
                )
            }
            is OperationProgress.Failed -> {
                val message = OperationErrors.describe(applicationContext, result.reason)
                container.operations.recordFailure(type, summary, message)
                Result.failure(
                    workDataOf(
                        KEY_ERROR to message,
                        KEY_ERROR_TEMP to (result.reason as? OperationError.DestinationWriteFailed)?.tempFile?.path,
                        KEY_ERROR_PAGES to ((result.reason as? OperationError.DestinationWriteFailed)?.pages ?: 0),
                    ),
                )
            }
            is OperationProgress.Working -> Result.failure(workDataOf(KEY_ERROR to "Operation ended unexpectedly"))
        }
    }

    /**
     * The title says what is happening; the app name already sits in the notification header,
     * and Android drops a title that repeats it, which left only a bare progress bar. [detail]
     * is the page counter or the operation label, when there is one.
     */
    private fun foregroundInfo(detail: String?, fraction: Float? = null): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tools)
            .setContentTitle(applicationContext.getString(R.string.ops_notification_working))
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_SPEC = "spec"
        const val KEY_DESTINATION = "destination"
        const val KEY_SUMMARY = "summary"
        const val KEY_FRACTION = "fraction"
        const val KEY_LABEL = "label"
        const val KEY_OCR_PAGE = "ocrPage"
        const val KEY_OCR_TOTAL = "ocrTotal"
        const val KEY_OUTPUT_URIS = "outputUris"
        const val KEY_OUTPUT_NAMES = "outputNames"
        const val KEY_OUTPUT_PAGES = "outputPages"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_TEMP = "errorTemp"
        const val KEY_ERROR_PAGES = "errorPages"
        const val CHANNEL_ID = "operations"
        const val NOTIFICATION_ID = 41

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.ops_channel_name), NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
