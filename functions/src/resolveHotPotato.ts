import { DocumentReference, getFirestore, Transaction } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc } from "./types";
import { roomsCol, privateBombDoc } from "./roomRepo";
import { scheduleBombExplosion } from "./taskQueue";
import { applyCorrectAnswer, rankMistakeOrHotPotatoPlayers } from "./scoring";

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
  await scheduleBombExplosion(roomId, delayMs, bombAtMs);
}

/**
 * Patata Caliente's answer resolution: unlike resolveRound (the "mistake" mode),
 * a wrong answer never eliminates anyone and never advances the turn -- the
 * same player just gets a fresh stimulus and keeps holding it. Only a correct
 * answer passes the turn forward. Elimination only ever happens via the
 * hidden bomb (see explodeBomb below).
 *
 * No timeout task is scheduled for this mode (see beginRound/submitAnswer):
 * the current holder can take as long as they want between stimuli -- the
 * only real pressure is the bomb, which is independent of how fast anyone
 * answers. `reason` can still arrive as "disconnect" for a bystander.
 */
export async function resolveHotPotatoTurn(
  roomId: string,
  actingUid: string,
  reason: ResolutionReason,
  roundExpected: number
): Promise<boolean> {
  const roomRef = roomsCol().doc(roomId);

  return getFirestore().runTransaction<boolean>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return false;
    return applyHotPotatoTurn(tx, roomRef, doc.data() as RoomDoc, actingUid, reason, roundExpected);
  });
}

/**
 * The transactional body of [resolveHotPotatoTurn], for a caller that already
 * read the room in its own transaction. Nothing to do after commit: this mode
 * schedules no per-stimulus timeout.
 */
export function applyHotPotatoTurn(
  tx: Transaction,
  roomRef: DocumentReference<RoomDoc>,
  room: RoomDoc,
  actingUid: string,
  reason: ResolutionReason,
  roundExpected: number
): boolean {
  if (room.status !== "playing" || room.round !== roundExpected) return false;
  if (room.turnOrder[room.turnIndex] !== actingUid) return false;

  const nextRound = room.round + 1;
  const stimulus = generateStimulus();
  const deadlineAtMs = Date.now() + timeLimitMsForRound(nextRound);
  const me = room.players[actingUid];

  if (reason === "correct") {
    const { score, streak } = applyCorrectAnswer(me.matchScore ?? 0, me.matchStreak ?? 0);
    const players = { ...room.players, [actingUid]: { ...me, matchScore: score, matchStreak: streak } };
    const nextIndex = nextAliveIndex(room.turnOrder, room.players, room.turnIndex);
    tx.update(roomRef, { players, turnIndex: nextIndex, round: nextRound, stimulus, deadlineAtMs });
  } else {
    // Wrong (or a stale timeout/disconnect call): no elimination, no turn
    // change -- the current holder just gets re-prompted. The bomb clock is
    // unaffected either way. Still breaks their scoring streak, same as a
    // local-mode miss does.
    const players = { ...room.players, [actingUid]: { ...me, matchStreak: 0 } };
    tx.update(roomRef, { players, round: nextRound, stimulus, deadlineAtMs });
  }
  return true;
}

/**
 * Fired by the Cloud Task scheduled in armBomb. Eliminates whoever holds the
 * turn at this exact instant. If that leaves a sole survivor, the match ends;
 * otherwise a fresh bomb is armed for the remaining players and play continues.
 *
 * Idempotent per bomb: the task carries the bombAtMs it was armed for, and it
 * only detonates while rooms/{id}/private/bomb still holds that exact value.
 * The bomb doc is deleted in the same transaction, so a duplicate delivery (Cloud
 * Tasks is at-least-once) or a task for a bomb that was since replaced no-ops
 * instead of eliminating a second player. `expectedBombAtMs` is optional only
 * for tasks enqueued before this field existed; those only detonate a bomb that
 * is already due, never a freshly re-armed one (always 15-30s in the future).
 */
export async function explodeBomb(roomId: string, expectedBombAtMs?: number): Promise<void> {
  const roomRef = roomsCol().doc(roomId);
  const bombRef = privateBombDoc(roomId);

  const outcome = await getFirestore().runTransaction<"finished" | "continued" | "stale">(async (tx) => {
    const [doc, bombDoc] = await Promise.all([tx.get(roomRef), tx.get(bombRef)]);
    if (!doc.exists || !bombDoc.exists) return "stale";
    const { bombAtMs } = bombDoc.data()!;
    if (expectedBombAtMs !== undefined ? bombAtMs !== expectedBombAtMs : bombAtMs > Date.now()) return "stale";
    const room = doc.data() as RoomDoc;
    if (room.status !== "playing" || room.mode !== "hot_potato") return "stale";

    tx.delete(bombRef);
    const holderUid = room.turnOrder[room.turnIndex];
    const players = { ...room.players, [holderUid]: { ...room.players[holderUid], alive: false, eliminatedAtMs: Date.now() } };

    const survivor = soleSurvivor(players);
    if (survivor) {
      const finishedPlayers = { ...players };
      for (const r of rankMistakeOrHotPotatoPlayers(players, survivor)) {
        finishedPlayers[r.uid] = { ...finishedPlayers[r.uid], placement: r.placement, finalScore: r.finalScore };
      }
      tx.update(roomRef, { players: finishedPlayers, status: "finished", winnerUid: survivor, stimulus: null, deadlineAtMs: null });
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
    // Deliberately not rethrown: the elimination above is already committed, and
    // a Cloud Tasks retry of this handler can't re-arm anything (the bomb doc is
    // gone, so it would no-op). A room left without a bomb is picked up and
    // re-armed by the roomWatchdog sweep instead.
    try {
      await armBomb(roomId);
    } catch (err) {
      console.error(`explodeBomb: failed to arm the next bomb for room ${roomId}; watchdog will re-arm`, err);
    }
  }
}
