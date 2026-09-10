package com.leaf.app.ui.reader

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import com.leaf.app.data.prefs.PageDisplayMode

/**
 * Colour transforms applied to rendered pages, independent of the app theme.
 * Night inverts then desaturates 15% so inverted colour images are not lurid.
 * Sepia multiplies with #F4ECD8. Grayscale drops saturation to zero.
 */
object PageDisplayFilters {

    fun matrixFor(mode: PageDisplayMode): ColorMatrix? = when (mode) {
        PageDisplayMode.NORMAL -> null
        PageDisplayMode.NIGHT -> ColorMatrix().apply {
            val invert = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            val desaturate = ColorMatrix().apply { setSaturation(0.85f) }
            // postConcat: apply invert first, then desaturate the result.
            set(invert)
            postConcat(desaturate)
        }
        PageDisplayMode.SEPIA -> ColorMatrix(
            floatArrayOf(
                0xF4 / 255f, 0f, 0f, 0f, 0f,
                0f, 0xEC / 255f, 0f, 0f, 0f,
                0f, 0f, 0xD8 / 255f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        PageDisplayMode.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
    }

    fun composeFilterFor(mode: PageDisplayMode): ColorFilter? =
        matrixFor(mode)?.let { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it.array)) }
}
