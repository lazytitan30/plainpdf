package com.leaf.app.data.ops

import com.leaf.app.data.db.OperationDao
import com.leaf.app.data.db.entities.OperationEntity
import com.leaf.app.data.pdf.write.OperationOutput
import kotlinx.coroutines.flow.Flow

/** The "Recent output" strip. One row per produced file, capped at 30 rows. */
class OperationsRepository(
    private val dao: OperationDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeRecent(limit: Int = 10): Flow<List<OperationEntity>> = dao.observeRecent(limit)

    suspend fun recordSuccess(type: String, sourceSummary: String, outputs: List<OperationOutput>) {
        val now = clock()
        outputs.forEachIndexed { i, out ->
            dao.insert(
                OperationEntity(
                    type = type,
                    sourceSummary = sourceSummary,
                    outputUri = out.uri.toString(),
                    outputName = out.displayName,
                    completedAt = now + i,
                    succeeded = true,
                    errorMessage = null,
                ),
            )
        }
        dao.prune(KEEP)
    }

    suspend fun recordFailure(type: String, sourceSummary: String, message: String) {
        dao.insert(
            OperationEntity(
                type = type,
                sourceSummary = sourceSummary,
                outputUri = null,
                outputName = null,
                completedAt = clock(),
                succeeded = false,
                errorMessage = message,
            ),
        )
        dao.prune(KEEP)
    }

    suspend fun remove(id: Long) = dao.delete(id)

    companion object {
        const val KEEP = 30
    }
}
