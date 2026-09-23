import { DocumentReference, getFirestore, Transaction } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc, RoomPlayerDoc } from "./types";
import { roomsCol } from "./roomRepo";
import { scheduleSoloPlayerTimeoutCheck } from "./taskQueue";
import { rankSoloSurvivalPlayers } from "./scoring";

function withFinalScores(players: Readonly<Record<string, RoomPlayerDoc>>): Record<string, RoomPlayerDoc> {
  const ranked = { ...players };
  for (const r of rankSoloSurvivalPlayers(players)) {
    ranked[r.uid] = { ...ranked[r.uid], placement: r.placement, finalScore: r.finalScore };
  }
  return ranked;
}

// Mirrors the Android client's single-player ENDLESS mode (GameConfig.kt):
// every 5 rounds is one "level", and the per-stimulus time limit decays per
// level using the exact same formula turnLogic.ts already uses per-round for
// the turn-based modes (INITIAL_TIME_LIMIT_MS - (n-1)*TIME_LIMIT_DECAY_MS,
// floored at MINIMUM_TIME_LIMIT_MS) -- just fed a level number instead of a
// raw round number, since solo_survival's difficulty ramps slower.
export const SOLO_LEVELS_PER_DIFFICULTY = 5;
// Shared session clock every player in the room plays against -- mirrors
// GameConfig.TIME_MODE_DURATION_MS. Room.deadlineAtMs holds this mode's
// session-end instant (repurposed from its turn-based-mode meaning).
export const SOLO_SESSION_DURATION_MS = 60_000;
export const SOLO_POINTS_PER_CORRECT = 100;

export function soloLevelForRound(round: number): number {
  return Math.floor(round / SOLO_LEVELS_PER_DIFFICULTY) + 1;
}

export function soloTimeLimitMs(round: number): number {
  return timeLimitMsForRound(soloLevelForRound(round));
}

interface PlayerDeadline {
  uid: string;
  round: number;
  deadlineAtMs: number;
}

/**
 * Fired once, from beginRound, when a solo_survival room's "starting"
 * countdown elapses. Seeds every player with their own round-0 stimulus and
 * deadline, and starts the shared room-level session clock.
 */
export async function beginSoloSurvivalMatch(roomId: string): Promise<PlayerDeadline[]> {
  const roomRef = roomsCol().doc(roomId);
  const now = Date.now();
  const sessionDeadlineAtMs = now + SOLO_SESSION_DURATION_MS;

  const playerDeadlines = await getFirestore().runTransaction<PlayerDeadline[]>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return [];
    const room = doc.data() as RoomDoc;
    if (room.status !== "starting") return []; // stale/already handled

    const deadlines: PlayerDeadline[] = [];
    const players: Record<string, RoomPlayerDoc> = {};
    for (const uid of Object.keys(room.players)) {
      const deadlineAtMs = now + soloTimeLimitMs(0);
      players[uid] = {
        ...room.players[uid],
        alive: true,
        soloScore: 0,
        soloRound: 0,
        soloStreak: 0,
        soloStimulus: generateStimulus(),
        soloDeadlineAtMs: deadlineAtMs,
      };
      deadlines.push({ uid, round: 0, deadlineAtMs });
    }

    tx.update(roomRef, {
      status: "playing",
      players,
      stimulus: null,
      deadlineAtMs: sessionDeadlineAtMs,
    });
    return deadlines;
  });

  return playerDeadlines;
}

function highestScoreWinner(players: Readonly<Record<string, RoomPlayerDoc>>): string | null {
  const entries = Object.values(players);
  if (entries.length === 0) return null;
  // Tie-break: whoever joined first (lowest `order`) -- deterministic, and
  // matches the "host was here first" intuition players already have from
  // how turnOrder/order work in the other modes.
  const winner = entries.reduce((best, p) => {
    const pScore = p.soloScore ?? 0;
    const bestScore = best.soloScore ?? 0;
    if (pScore > bestScore) return p;
    if (pScore === bestScore && p.order < best.order) return p;
    return best;
  });
  return winner.uid;
}

/**
 * Resolves one player's answer (or timeout, via the same "wrong" reason) in
 * solo_survival. Unlike resolveRound/resolveHotPotatoTurn there is no shared
 * turn to pass -- a correct answer only ever affects the acting player's own
 * state. If this bust leaves at most one player still alive, the match
 * finishes early instead of waiting out the rest of the session clock --
 * there's no point making a sole survivor keep playing alone once everyone
 * else has fallen (startGame already guarantees >= 2 players, so "one left"
 * always means a real win, not a degenerate single-player room).
 */
export type SoloScheduled = { round: number; deadlineAtMs: number } | null;

export interface SoloAnswerTxResult {
  applied: boolean;
  scheduled: SoloScheduled;
}

export async function resolveSoloAnswer(
  roomId: string,
  actingUid: string,
  reason: ResolutionReason,
  roundExpected: number
): Promise<boolean> {
  const roomRef = roomsCol().doc(roomId);
  const { applied, scheduled } = await getFirestore().runTransaction<SoloAnswerTxResult>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return { applied: false, scheduled: null };
    return applySoloAnswer(tx, roomRef, doc.data() as RoomDoc, actingUid, reason, roundExpected);
  });
  await scheduleNextSoloTimeout(roomId, actingUid, scheduled);
  return applied;
}

/**
 * The transactional half of [resolveSoloAnswer], for a caller that already read
 * the room in its own transaction.
 */
export function applySoloAnswer(
  tx: Transaction,
  roomRef: DocumentReference<RoomDoc>,
  room: RoomDoc,
  actingUid: string,
  reason: ResolutionReason,
  roundExpected: number
): SoloAnswerTxResult {
  if (room.status !== "playing") return { applied: false, scheduled: null };

  const me = room.players[actingUid];
  if (!me || !me.alive || (me.soloRound ?? 0) !== roundExpected) {
    return { applied: false, scheduled: null }; // stale or already busted
  }

  if (reason !== "correct") {
    const players = { ...room.players, [actingUid]: { ...me, alive: false, soloStimulus: null, soloDeadlineAtMs: null } };
    const survivors = Object.values(players).filter((p) => p.alive);
    if (survivors.length <= 1) {
      // Sole survivor wins outright for actually surviving -- unlike the
      // all-busted or session-timeout finishes below (where nobody's left
      // standing, or several still are, so score is the only fair
      // tiebreak), there's no ambiguity here about who "won": whoever's
      // still alive did, regardless of their score.
      const winnerUid = survivors.length === 1 ? survivors[0].uid : highestScoreWinner(players);
      tx.update(roomRef, {
        players: withFinalScores(players),
        status: "finished",
        winnerUid,
        deadlineAtMs: null,
      });
    } else {
      tx.update(roomRef, { players });
    }
    return { applied: true, scheduled: null };
  }

  const nextRound = (me.soloRound ?? 0) + 1;
  const nextStreak = (me.soloStreak ?? 0) + 1;
  const streakBonus = Math.min(nextStreak * 10, 100);
  const nextScore = (me.soloScore ?? 0) + SOLO_POINTS_PER_CORRECT + streakBonus;
  const deadlineAtMs = Date.now() + soloTimeLimitMs(nextRound);
  const players = {
    ...room.players,
    [actingUid]: {
      ...me,
      soloRound: nextRound,
      soloStreak: nextStreak,
      soloScore: nextScore,
      soloStimulus: generateStimulus(),
      soloDeadlineAtMs: deadlineAtMs,
    },
  };
  tx.update(roomRef, { players });
  return { applied: true, scheduled: { round: nextRound, deadlineAtMs } };
}

/** The post-commit half of [resolveSoloAnswer]: arms this player's next timeout, if any. */
export async function scheduleNextSoloTimeout(
  roomId: string,
  actingUid: string,
  scheduled: SoloScheduled
): Promise<void> {
  if (!scheduled) return;
  try {
    await scheduleSoloPlayerTimeoutCheck(roomId, actingUid, scheduled.round, scheduled.deadlineAtMs - Date.now());
  } catch (err) {
    console.error(`resolveSoloAnswer: failed to schedule timeout for room ${roomId}, uid ${actingUid}`, err);
  }
}

/**
 * Fired by the Cloud Task scheduled off the room-level session clock
 * (beginSoloSurvivalMatch / scheduleTimeoutCheck with round 0, since
 * solo_survival never advances RoomDoc.round). No-ops if the match already
 * finished early because every player busted before the clock ran out.
 */
export async function finishSoloSurvivalSession(roomId: string): Promise<void> {
  const roomRef = roomsCol().doc(roomId);
  await getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return;
    const room = doc.data() as RoomDoc;
    if (room.status !== "playing") return; // already finished (all-busted early finish, or stale)

    tx.update(roomRef, {
      players: withFinalScores(room.players),
      status: "finished",
      winnerUid: highestScoreWinner(room.players),
      deadlineAtMs: null,
    });
  });
}
