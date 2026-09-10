package com.leaf.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radii carry hierarchy: sheets 20, cards and buttons 10, chips round, dialogs 24. */
val QuireShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object QuireShape {
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Card = RoundedCornerShape(10.dp)
    val Thumbnail = RoundedCornerShape(10.dp)
    val Button = RoundedCornerShape(10.dp)
    val Chip = CircleShape
    val Dialog = RoundedCornerShape(24.dp)
}

object Spacing {
    val unit = 4.dp
    val screenHorizontal = 16.dp
    val rowVertical = 12.dp
    val section = 24.dp
}
