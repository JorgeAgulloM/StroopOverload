package com.softyorch.stroopoverload.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.softyorch.stroopoverload.R

/**
 * Handle shown for a profile whose uniqueName hasn't been generated yet. Used by
 * both the profile header and the leaderboard rows, which each had their own
 * copy with the word "pilot" hardcoded in English.
 */
@Composable
fun pilotHandleFallback(userId: String): String =
    stringResource(R.string.common_pilot_handle_fallback, userId.takeLast(4))
