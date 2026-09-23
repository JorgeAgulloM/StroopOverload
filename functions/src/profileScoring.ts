/**
 * Server-side port of the Android client's scoring rules (XpSystem.kt,
 * GameConfig.kt, GameViewModel.kt), plus the plausibility checks that bound what
 * a submitted solo run is allowed to claim.
 *
 * Why the port exists: the client used to write points/experience/level straight
 * into its own users/{uid} document, so the leaderboard was whatever a client
 * said it was. These numbers are now computed here. Anything changed in
 * XpSystem.kt has to change here too, or a player's progress would jump the
 * first time the server recomputed it -- profileScoring.test.ts pins the values.
 */

export type SoloMode = "ENDLESS" | "LIVES" | "TIME";

export interface SoloRunReport {
  mode: SoloMode;
  correctHits: number;
  totalRounds: number;
  survivalMs: number;
  finalScore: number;
}

// GameConfig.kt
const POINTS_PER_CORRECT = 100;
const INITIAL_TIME_LIMIT_MS = 3000;
const TIME_MODE_DURATION_MS = 60_000;

// Bounds for a single submitted run. Generous on purpose: they exist to reject
// the impossible, not to second-guess a good player.
const MAX_ROUNDS_PER_RUN = 2000;
// Fastest credible reaction to a fresh stimulus. Anything under this for every
// round of a run did not come from a person tapping a screen.
const MIN_MS_PER_ROUND = 120;
// Slack over the theoretical maximum, for countdowns, pauses and clock skew.
const DURATION_SLACK_MS = 30_000;

export function xpForLevel(level: number): number {
  return Math.trunc((level * (400 + 16 * level)) / 5);
}

export function levelFromTotalXp(totalXp: number): number {
  let level = 1;
  let remaining = totalXp;
  for (;;) {
    const need = xpForLevel(level);
    if (remaining < need) return level;
    remaining -= need;
    level++;
  }
}

/** Mirrors GameResult.won: a run counts as won at >= 70% accuracy over at least 5 rounds. */
export function isWon(run: SoloRunReport): boolean {
  return run.finalScore > 0 && run.totalRounds >= 5 && run.correctHits / run.totalRounds >= 0.7;
}

/** Mirrors GameResult.isFlawless. */
export function isFlawless(run: SoloRunReport): boolean {
  return run.totalRounds >= 5 && run.correctHits === run.totalRounds;
}

/** Mirrors FirebaseGameRepository.recordGameResult's ranking-point delta. */
export function pointsDeltaFor(won: boolean): number {
  return won ? 100 : -25;
}

/** Mirrors XpSystem.calculateGameXp, minus the UI labels. */
export function calculateRunXp(
  run: SoloRunReport,
  dailyStreak: number,
  currentWinStreak: number,
  isNewHighScore: boolean
): number {
  if (run.correctHits === 0 || run.finalScore <= 0) return 0;

  const won = isWon(run);
  const base = won ? run.correctHits * 15 + 50 : run.correctHits * 5;
  const perfectBonus = isFlawless(run) ? 100 : 0;
  // TIME mode's survivalMs is a fixed countdown, not a skill signal.
  const timeBonus =
    run.mode === "TIME" ? 0 : run.survivalMs >= 20_000 ? 100 : run.survivalMs >= 10_000 ? 50 : 0;
  const streakBonus = Math.min(currentWinStreak * 15, 150);
  const dailyBonus = Math.min(dailyStreak * 20, 200);

  const multiplier = isNewHighScore ? 1.5 : 1.0;
  const sum = base + perfectBonus + timeBonus + streakBonus + dailyBonus;
  return Math.max(Math.trunc(sum * multiplier), 0);
}

/**
 * The highest score `correctHits` correct answers could possibly produce: every
 * one of them landing in an unbroken streak (GameViewModel: 100 per correct plus
 * min(streak * 10, 100)). The exact score depends on where the misses fell, which
 * the server can't know, so this is the ceiling rather than an equality check.
 */
export function maxPlausibleScore(correctHits: number): number {
  let score = 0;
  for (let streak = 1; streak <= correctHits; streak++) {
    score += POINTS_PER_CORRECT + Math.min(streak * 10, 100);
  }
  return score;
}

/** XP rewards by achievement id, ported from AchievementDefinitions.kt. */
export const ACHIEVEMENT_XP: Readonly<Record<string, number>> = {
  first_blood: 250,
  ten_games: 500,
  twenty_five_games: 1000,
  fifty_games: 1500,
  hundred_games: 3000,
  two_hundred_games: 6000,
  five_hundred_games: 15000,
  five_wins: 500,
  fifteen_wins: 1200,
  thirty_wins: 2000,
  fifty_wins: 4000,
  hundred_wins: 8000,
  two_hundred_wins: 20000,
  centurion: 1000,
  score_3k: 2500,
  score_titan: 4500,
  score_overlord: 8000,
  score_god: 25000,
  flawless: 1000,
  flawless_tier2: 3000,
  flawless_tier3: 7000,
  flawless_tier4: 18000,
  survival_expert: 1200,
  survival_master: 3000,
  survival_legend: 7000,
  survival_god: 18000,
  streak_master: 1500,
  streak_legend: 4000,
  streak_god: 9000,
  cyber_veteran: 8000,
};

/** Total XP for a set of claimed achievement ids; unknown ids and duplicates are ignored. */
export function achievementXpFor(ids: readonly string[]): number {
  const seen = new Set<string>();
  let total = 0;
  for (const id of ids) {
    if (seen.has(id)) continue;
    seen.add(id);
    total += ACHIEVEMENT_XP[id] ?? 0;
  }
  return total;
}

/**
 * Rejects runs that could not have happened. This bounds a forged submission; it
 * cannot verify one, because the stimuli of a solo run are generated on the device.
 * Returns null when the run is acceptable, or a short reason when it is not.
 */
export function validateSoloRun(run: SoloRunReport): string | null {
  if (run.mode !== "ENDLESS" && run.mode !== "LIVES" && run.mode !== "TIME") return "unknown mode";
  if (!Number.isInteger(run.correctHits) || run.correctHits < 0) return "invalid correctHits";
  if (!Number.isInteger(run.totalRounds) || run.totalRounds < 0) return "invalid totalRounds";
  if (!Number.isInteger(run.finalScore) || run.finalScore < 0) return "invalid finalScore";
  if (!Number.isFinite(run.survivalMs) || run.survivalMs < 0) return "invalid survivalMs";

  if (run.totalRounds > MAX_ROUNDS_PER_RUN) return "totalRounds above the per-run maximum";
  if (run.correctHits > run.totalRounds) return "correctHits exceeds totalRounds";
  if (run.finalScore > maxPlausibleScore(run.correctHits)) return "finalScore exceeds what correctHits allows";

  if (run.survivalMs < run.totalRounds * MIN_MS_PER_ROUND) return "survivalMs too short for totalRounds";
  if (run.mode === "TIME") {
    if (run.survivalMs > TIME_MODE_DURATION_MS + DURATION_SLACK_MS) return "survivalMs too long for mode";
  } else if (run.survivalMs > run.totalRounds * INITIAL_TIME_LIMIT_MS + DURATION_SLACK_MS) {
    return "survivalMs too long for totalRounds";
  }

  return null;
}
