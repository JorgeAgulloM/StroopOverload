package com.softyorch.stroopoverload.domain

import androidx.annotation.StringRes
import com.softyorch.stroopoverload.R

/**
 * ENDLESS: current behavior, one miss ends the run. Accuracy % is near-meaningless here
 * (it always trends toward "just lost"), so it's hidden in the results screen.
 * LIVES: 3 lives, a miss pauses briefly and flashes the correct color instead of ending the run.
 * TIME: fixed countdown, a miss just flashes feedback and play continues; accuracy % is
 * meaningful since many stimuli are attempted over the window.
 */
enum class GameMode(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    ENDLESS(R.string.game_mode_endless_title, R.string.game_mode_endless_desc),
    LIVES(R.string.game_mode_lives_title, R.string.game_mode_lives_desc),
    TIME(R.string.game_mode_time_title, R.string.game_mode_time_desc),
}
