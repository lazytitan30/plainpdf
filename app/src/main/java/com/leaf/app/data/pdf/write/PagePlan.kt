package com.leaf.app.data.pdf.write

/**
 * One page of output: which source page it comes from and how much extra rotation to apply.
 * Deleting is omission, reordering is list order, extracting is a shorter list into a new file.
 */
data class PageOp(val sourceIndex: Int, val rotationDelta: Int = 0) {
    init {
        require(sourceIndex >= 0) { "sourceIndex must be >= 0" }
        require(rotationDelta in ALLOWED_ROTATIONS) { "rotationDelta must be one of $ALLOWED_ROTATIONS" }
    }

    fun rotatedBy(degrees: Int): PageOp = copy(rotationDelta = ((rotationDelta + degrees) % 360 + 360) % 360)

    companion object {
        val ALLOWED_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

/** Ordered description of an output document built from a single source. */
data class PagePlan(val pages: List<PageOp>) {

    val isEmpty: Boolean get() = pages.isEmpty()
    val size: Int get() = pages.size

    /** True when the plan reproduces the source unchanged. */
    fun isIdentity(sourcePageCount: Int): Boolean =
        pages.size == sourcePageCount && pages.withIndex().all { (i, op) -> op.sourceIndex == i && op.rotationDelta == 0 }

    companion object {
        fun identity(pageCount: Int): PagePlan = PagePlan(List(pageCount) { PageOp(it) })
    }
}
