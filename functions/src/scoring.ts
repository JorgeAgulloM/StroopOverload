import { RoomDoc, RoomPlayerDoc } from "./types";

// Mirrors the Android client's local single-player formula (GameConfig.kt):
// 100 pts per correct answer + a streak bonus (streak*10, capped at 100).
// Used to accumulate each player's raw in-match score for mistake/hot_potato
// (solo_survival already accumulates its own soloScore/soloStreak the same
// way, independently -- see soloSurvival.ts).
export const MATCH_POINTS_PER_CORRECT = 100;
export const MATCH_STREAK_BONUS_PER_HIT = 10;
export const MATCH_STREAK_BONUS_CAP = 100;

export function applyCorrectAnswer(score: number, streak: number): { score: number; streak: number } {
  const nextStreak = streak + 1;
  const bonus = Math.min(nextStreak * MATCH_STREAK_BONUS_PER_HIT, MATCH_STREAK_BONUS_CAP);
  return { score: score + MATCH_POINTS_PER_CORRECT + bonus, streak: nextStreak };
}

// 1st x2, 2nd x1.5, 3rd x1, 4th x0.5. With fewer than 4 players the table is
// NOT compressed -- e.g. a 2-player match still awards x2/x1.5 (not x2/x0.5),
// so a smaller room isn't penalized relative to a full one.
const PLACEMENT_MULTIPLIERS: readonly number[] = [2, 1.5, 1, 0.5];

export function placementMultiplier(placement: number): number {
  return PLACEMENT_MULTIPLIERS[placement - 1] ?? 0.5;
}

// Points actually awarded to a player's profile: their raw accumulated match
// score, halved, then scaled by how they placed.
export function finalScoreForPlacement(rawScore: number, placement: number): number {
  return Math.round((rawScore / 2) * placementMultiplier(placement));
}

export interface RankedPlayer {
  uid: string;
  placement: number;
  finalScore: number;
}

/**
 * Ranks a finished mistake/hot_potato room and computes each player's
 * finalScore. The winner always takes placement 1; everyone else is ordered
 * by eliminatedAtMs descending (survived longest = better placement), with
 * `order` (join order) as a deterministic tiebreak for simultaneous
 * eliminations (e.g. two players caught by the same disconnect sweep, or two
 * players who never got a single turn before the match ended).
 */
export function rankMistakeOrHotPotatoPlayers(
  players: Readonly<Record<string, RoomPlayerDoc>>,
  winnerUid: string | null
): RankedPlayer[] {
  const entries = Object.values(players);
  const winner = entries.find((p) => p.uid === winnerUid) ?? null;
  const rest = entries
    .filter((p) => p.uid !== winnerUid)
    .sort((a, b) => (b.eliminatedAtMs ?? 0) - (a.eliminatedAtMs ?? 0) || a.order - b.order);

  const ordered = winner ? [winner, ...rest] : rest;
  return ordered.map((p, i) => {
    const placement = i + 1;
    return { uid: p.uid, placement, finalScore: finalScoreForPlacement(p.matchScore ?? 0, placement) };
  });
}

/**
 * Ranks a finished solo_survival room by soloScore descending (no shared
 * turn/elimination-order to rank by -- every player's own run is independent).
 * Tiebreak matches soloSurvival.ts's own winner tiebreak: lowest `order`
 * (earliest joiner) wins ties.
 *
 * `winnerUid`, when given, takes placement 1 whatever their score: a sole survivor
 * wins for still standing, and must not be paid less than a player who busted.
 */
export function rankSoloSurvivalPlayers(
  players: Readonly<Record<string, RoomPlayerDoc>>,
  winnerUid: string | null = null
): RankedPlayer[] {
  const ordered = Object.values(players).sort(
    (a, b) =>
      Number(b.uid === winnerUid) - Number(a.uid === winnerUid) ||
      (b.soloScore ?? 0) - (a.soloScore ?? 0) ||
      a.order - b.order
  );
  return ordered.map((p, i) => {
    const placement = i + 1;
    return { uid: p.uid, placement, finalScore: finalScoreForPlacement(p.soloScore ?? 0, placement) };
  });
}

/**
 * The room update that ends a mistake/hot_potato match with `survivor` as the
 * sole player left: every player ranked and scored, the board cleared. Shared by
 * an answer that eliminates the second-to-last player (resolveRound) and a bomb
 * that does (explodeBomb).
 */
export function finishedMatchUpdate(
  players: Readonly<Record<string, RoomPlayerDoc>>,
  survivor: string
): Pick<RoomDoc, "players" | "status" | "winnerUid" | "stimulus" | "deadlineAtMs"> {
  const finishedPlayers = { ...players };
  for (const r of rankMistakeOrHotPotatoPlayers(players, survivor)) {
    finishedPlayers[r.uid] = { ...finishedPlayers[r.uid], placement: r.placement, finalScore: r.finalScore };
  }
  return { players: finishedPlayers, status: "finished", winnerUid: survivor, stimulus: null, deadlineAtMs: null };
}
