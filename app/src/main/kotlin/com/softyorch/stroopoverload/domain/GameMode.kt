package com.softyorch.stroopoverload.domain

import androidx.annotation.StringRes
import com.softyorch.stroopoverload.R

/**
 * ENDLESS: current behavior, one miss ends the run. Accuracy % is near-meaningless here
 * (it always trends toward "just lost"), so it's hidden in the results screen.
 * LIVES: 3 lives, a miss pauses briefly and flashes the correct color instead of ending the run.
 * TIME: fixed countdown, a miss just flashes feedback and play continues; accuracy % is
 * meaningful since many stimuli are attempted over the window.
 * OVERTIME: like TIME, but the clock starts shorter, every right answer adds time (less as
 * the level rises) and a miss takes some away, so the run lasts as long as the player keeps up.
 *
 * [hasSessionClock]: the run is timed by one shared clock rather than per stimulus, so its
 * length isn't a survival skill signal (TIME and OVERTIME hand the player time up front).
 */
enum class GameMode(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val hasSessionClock: Boolean = false,
) {
    ENDLESS(R.string.game_mode_endless_title, R.string.game_mode_endless_desc),
    LIVES(R.string.game_mode_lives_title, R.string.game_mode_lives_desc),
    TIME(R.string.game_mode_time_title, R.string.game_mode_time_desc, hasSessionClock = true),
    OVERTIME(R.string.game_mode_overtime_title, R.string.game_mode_overtime_desc, hasSessionClock = true),
}
