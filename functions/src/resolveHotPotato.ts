import { getFirestore } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc } from "./types";
import { roomsCol, privateBombDoc } from "./roomRepo";
import { scheduleTimeoutCheck, scheduleBombExplosion } from "./taskQueue";

export const BOMB_MIN_DELAY_MS = 15_000;
export const BOMB_MAX_DELAY_MS = 30_000;

export function randomBombDelayMs(): number {
  return BOMB_MIN_DELAY_MS + Math.floor(Math.random() * (BOMB_MAX_DELAY_MS - BOMB_MIN_DELAY_MS + 1));
}

/** Rolls a fresh hidden bomb deadline and schedules the Cloud Task that fires when it hits. */
export async function armBomb(roomId: string): Promise<void> {
  const delayMs = randomBombDelayMs();
  const bombAtMs = Date.now() + delayMs;
  await privateBombDoc(roomId).set({ bombAtMs });
  await scheduleBombExplosion(roomId, delayMs);
}

/**
 * Patata Caliente's answer resolution: unlike resolveRound (the "mistake" mode),
 * a wrong/timeout answer never eliminates anyone and never advances the turn --
 * the same player just gets a fresh stimulus and keeps holding it. Only a
 * correct answer passes the turn forward. Elimination only ever happens via
 * the hidden bomb (see explodeBomb below).
 */
export async function resolveHotPotatoTurn(
  roomId: string,
  actingUid: string,
  reason: ResolutionReason,
  roundExpected: number
): Promise<boolean> {
  const roomRef = roomsCol().doc(roomId);

  type Scheduled = { round: number; deadlineAtMs: number } | null;
  const { applied, scheduled } = await getFirestore().runTransaction<{ applied: boolean; scheduled: Scheduled }>(
    async (tx) => {
      const doc = await tx.get(roomRef);
      if (!doc.exists) return { applied: false, scheduled: null };
      const room = doc.data() as RoomDoc;

      if (room.status !== "playing" || room.round !== roundExpected) return { applied: false, scheduled: null };
      if (room.turnOrder[room.turnIndex] !== actingUid) return { applied: false, scheduled: null };

      const nextRound = room.round + 1;
      const stimulus = generateStimulus();
      const deadlineAtMs = Date.now() + timeLimitMsForRound(nextRound);

      if (reason === "correct") {
        const nextIndex = nextAliveIndex(room.turnOrder, room.players, room.turnIndex);
        tx.update(roomRef, { turnIndex: nextIndex, round: nextRound, stimulus, deadlineAtMs });
      } else {
        // Wrong/timeout: no elimination, no turn change -- the current holder
        // just gets re-prompted. The bomb clock is unaffected either way.
        tx.update(roomRef, { round: nextRound, stimulus, deadlineAtMs });
      }
      return { applied: true, scheduled: { round: nextRound, deadlineAtMs } };
    }
  );

  if (scheduled) {
    try {
      await scheduleTimeoutCheck(roomId, scheduled.round, scheduled.deadlineAtMs - Date.now());
    } catch (err) {
      console.error(`resolveHotPotatoTurn: failed to schedule timeout for room ${roomId} round ${scheduled.round}`, err);
    }
  }

  return applied;
}

/**
 * Fired by the Cloud Task scheduled in armBomb. Eliminates whoever holds the
 * turn at this exact instant. If that leaves a sole survivor, the match ends;
 * otherwise a fresh bomb is armed for the remaining players and play continues.
 */
export async function explodeBomb(roomId: string): Promise<void> {
  const roomRef = roomsCol().doc(roomId);

  const outcome = await getFirestore().runTransaction<"finished" | "continued" | "stale">(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return "stale";
    const room = doc.data() as RoomDoc;
    if (room.status !== "playing" || room.mode !== "hot_potato") return "stale";

    const holderUid = room.turnOrder[room.turnIndex];
    const players = { ...room.players, [holderUid]: { ...room.players[holderUid], alive: false } };

    const survivor = soleSurvivor(players);
    if (survivor) {
      tx.update(roomRef, { players, status: "finished", winnerUid: survivor, stimulus: null, deadlineAtMs: null });
      return "finished";
    }

    const nextIndex = nextAliveIndex(room.turnOrder, players, room.turnIndex);
    const nextRound = room.round + 1;
    const stimulus = generateStimulus();
    const deadlineAtMs = Date.now() + timeLimitMsForRound(nextRound);
    tx.update(roomRef, { players, turnIndex: nextIndex, round: nextRound, stimulus, deadlineAtMs });
    return "continued";
  });

  if (outcome === "continued") {
    try {
      await armBomb(roomId);
    } catch (err) {
      console.error(`explodeBomb: failed to arm the next bomb for room ${roomId}`, err);
    }
  }
}
