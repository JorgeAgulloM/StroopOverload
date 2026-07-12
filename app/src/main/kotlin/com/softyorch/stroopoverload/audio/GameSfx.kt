package com.softyorch.stroopoverload.audio

/** Short one-shot event sounds, distinct from the per-color Stroop audio cues. */
enum class GameSfx(val rawName: String) {
    TAP_CORRECT("sfx_tap_correct"),
    TAP_WRONG("sfx_tap_wrong"),
    MATCH_START("sfx_match_start"),
    MATCH_WIN("sfx_match_win"),
    MATCH_LOSE("sfx_match_lose"),
    RESULT_VICTORY("sfx_result_victory"),
    RESULT_DEFEAT("sfx_result_defeat"),
}
