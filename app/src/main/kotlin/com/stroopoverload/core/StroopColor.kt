package com.stroopoverload.core

import androidx.compose.ui.graphics.Color

enum class StroopColor(
    val displayName: String,
    val audioRes: String,
    val composeColor: Color,
) {
    RED("RED", "red", Color(0xFFFF2D2D)),
    GREEN("GREEN", "green", Color(0xFF39FF14)),
    BLUE("BLUE", "blue", Color(0xFF1F8FFF)),
    YELLOW("YELLOW", "yellow", Color(0xFFFFE600));
}
