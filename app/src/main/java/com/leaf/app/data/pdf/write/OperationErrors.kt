package com.leaf.app.data.pdf.write

import android.content.Context
import com.leaf.app.R

/** Copy for every error in the section 6 table: what happened and what to do. */
object OperationErrors {
    fun describe(context: Context, error: OperationError): String = when (error) {
        OperationError.PasswordRequired -> context.getString(R.string.ops_error_password)
        OperationError.AssemblyRestricted -> context.getString(R.string.ops_error_assembly)
        OperationError.Corrupt -> context.getString(R.string.ops_error_corrupt)
        OperationError.OutOfMemory -> context.getString(R.string.ops_error_oom)
        is OperationError.DestinationWriteFailed -> context.getString(R.string.ops_error_destination)
        OperationError.Cancelled -> context.getString(R.string.ops_error_cancelled)
        is OperationError.Unknown -> error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ops_error_unknown)
    }
}
