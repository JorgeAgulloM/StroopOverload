package com.softyorch.stroopoverload.core

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.softyorch.stroopoverload.R

enum class StroopColor(
    @StringRes val displayNameRes: Int,
    val audioRes: String,
    val composeColor: Color,
) {
    RED(R.string.color_red, "red", Color(0xFFFF2D2D)),
    GREEN(R.string.color_green, "green", Color(0xFF39FF14)),
    BLUE(R.string.color_blue, "blue", Color(0xFF1F8FFF)),
    YELLOW(R.string.color_yellow, "yellow", Color(0xFFFFE600));
}
