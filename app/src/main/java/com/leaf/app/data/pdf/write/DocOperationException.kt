package com.leaf.app.data.pdf.write

/** Thrown by the engine; the runner maps it straight to [OperationProgress.Failed]. */
class DocOperationException(val error: OperationError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
