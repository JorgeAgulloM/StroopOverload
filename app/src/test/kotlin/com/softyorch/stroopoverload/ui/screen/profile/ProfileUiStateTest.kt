package com.softyorch.stroopoverload.ui.screen.profile

import com.softyorch.stroopoverload.domain.AchievementDefinitions
import com.softyorch.stroopoverload.domain.AchievementProgress
import com.softyorch.stroopoverload.domain.CareerStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUiStateTest {

    @Test
    fun `a trophy's progress comes from the career stats`() {
        val state = ProfileUiState(careerStats = CareerStats(totalGamesPlayed = 4))
        val tenGames = AchievementDefinitions.all.first { it.id == "ten_games" }

        val progress = state.progressOf(tenGames) as AchievementProgress.Count

        assertEquals(4, progress.current)
        assertEquals(10, progress.target)
    }
}
