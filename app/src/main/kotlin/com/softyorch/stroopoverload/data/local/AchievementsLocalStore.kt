package com.softyorch.stroopoverload.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.softyorch.stroopoverload.domain.Achievement
import com.softyorch.stroopoverload.domain.AchievementDefinitions
import com.softyorch.stroopoverload.domain.CareerStats

class AchievementsLocalStore(context: Context) : SQLiteOpenHelper(
    context, "stroop_achievements.db", null, 1
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE achievements (
                id TEXT PRIMARY KEY,
                unlockedAtEpochMs INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE career_stats (
                id INTEGER PRIMARY KEY,
                totalGamesPlayed INTEGER,
                totalGamesWon INTEGER,
                totalCorrectHits INTEGER,
                totalRoundsPlayed INTEGER,
                maxScoreEver INTEGER,
                maxSurvivalTimeMs INTEGER,
                flawlessGamesCount INTEGER,
                currentWinStreak INTEGER,
                maxWinStreak INTEGER,
                cyberDifficultyWins INTEGER,
                speedRunWins INTEGER,
                lastPlayedEpochMs INTEGER
            )
            """.trimIndent()
        )
        seedCatalog(db)
        initCareerStats(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migraciones aditivas (§5.4) - para versión 1 no se requiere acción
    }

    fun seedIfNeeded() {
        val db = writableDatabase
        seedCatalog(db)
        initCareerStats(db)
    }

    private fun seedCatalog(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (def in AchievementDefinitions.all) {
                db.execSQL(
                    "INSERT OR IGNORE INTO achievements (id, unlockedAtEpochMs) VALUES (?, NULL)",
                    arrayOf(def.id)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun initCareerStats(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO career_stats (
                id, totalGamesPlayed, totalGamesWon, totalCorrectHits, totalRoundsPlayed,
                maxScoreEver, maxSurvivalTimeMs, flawlessGamesCount, currentWinStreak,
                maxWinStreak, cyberDifficultyWins, speedRunWins, lastPlayedEpochMs
            ) VALUES (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            """.trimIndent()
        )
    }

    fun unlock(id: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("unlockedAtEpochMs", timestamp)
        }
        val rows = db.update(
            "achievements",
            cv,
            "id = ? AND unlockedAtEpochMs IS NULL",
            arrayOf(id)
        )
        return rows > 0
    }

    fun getUnlockedIds(): Set<String> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id FROM achievements WHERE unlockedAtEpochMs IS NOT NULL", null)
        val ids = mutableSetOf<String>()
        cursor.use {
            val idx = it.getColumnIndex("id")
            while (it.moveToNext()) {
                ids.add(it.getString(idx))
            }
        }
        return ids
    }

    fun getAllAchievements(): List<Achievement> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, unlockedAtEpochMs FROM achievements", null)
        val unlockedMap = mutableMapOf<String, Long>()
        cursor.use {
            val idIdx = it.getColumnIndex("id")
            val timeIdx = it.getColumnIndex("unlockedAtEpochMs")
            while (it.moveToNext()) {
                if (!it.isNull(timeIdx)) {
                    unlockedMap[it.getString(idIdx)] = it.getLong(timeIdx)
                }
            }
        }
        return AchievementDefinitions.all.map { def ->
            def.copy(unlockedAtEpochMs = unlockedMap[def.id])
        }
    }

    fun getCareerStats(): CareerStats {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM career_stats WHERE id = 0", null)
        cursor.use {
            if (it.moveToFirst()) {
                return CareerStats(
                    id = 0,
                    totalGamesPlayed = it.getInt(it.getColumnIndexOrThrow("totalGamesPlayed")),
                    totalGamesWon = it.getInt(it.getColumnIndexOrThrow("totalGamesWon")),
                    totalCorrectHits = it.getInt(it.getColumnIndexOrThrow("totalCorrectHits")),
                    totalRoundsPlayed = it.getInt(it.getColumnIndexOrThrow("totalRoundsPlayed")),
                    maxScoreEver = it.getInt(it.getColumnIndexOrThrow("maxScoreEver")),
                    maxSurvivalTimeMs = it.getLong(it.getColumnIndexOrThrow("maxSurvivalTimeMs")),
                    flawlessGamesCount = it.getInt(it.getColumnIndexOrThrow("flawlessGamesCount")),
                    currentWinStreak = it.getInt(it.getColumnIndexOrThrow("currentWinStreak")),
                    maxWinStreak = it.getInt(it.getColumnIndexOrThrow("maxWinStreak")),
                    cyberDifficultyWins = it.getInt(it.getColumnIndexOrThrow("cyberDifficultyWins")),
                    speedRunWins = it.getInt(it.getColumnIndexOrThrow("speedRunWins")),
                    lastPlayedEpochMs = it.getLong(it.getColumnIndexOrThrow("lastPlayedEpochMs")),
                )
            }
        }
        return CareerStats()
    }

    fun updateCareerStats(stats: CareerStats) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("totalGamesPlayed", stats.totalGamesPlayed)
            put("totalGamesWon", stats.totalGamesWon)
            put("totalCorrectHits", stats.totalCorrectHits)
            put("totalRoundsPlayed", stats.totalRoundsPlayed)
            put("maxScoreEver", stats.maxScoreEver)
            put("maxSurvivalTimeMs", stats.maxSurvivalTimeMs)
            put("flawlessGamesCount", stats.flawlessGamesCount)
            put("currentWinStreak", stats.currentWinStreak)
            put("maxWinStreak", stats.maxWinStreak)
            put("cyberDifficultyWins", stats.cyberDifficultyWins)
            put("speedRunWins", stats.speedRunWins)
            put("lastPlayedEpochMs", stats.lastPlayedEpochMs)
        }
        db.update("career_stats", cv, "id = 0", null)
    }

    fun clearProgress() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE achievements SET unlockedAtEpochMs = NULL")
            db.execSQL(
                """
                UPDATE career_stats SET
                    totalGamesPlayed = 0, totalGamesWon = 0, totalCorrectHits = 0, totalRoundsPlayed = 0,
                    maxScoreEver = 0, maxSurvivalTimeMs = 0, flawlessGamesCount = 0, currentWinStreak = 0,
                    maxWinStreak = 0, cyberDifficultyWins = 0, speedRunWins = 0, lastPlayedEpochMs = 0
                WHERE id = 0
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
