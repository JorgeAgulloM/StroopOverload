import { HttpsError } from "firebase-functions/v2/https";
import { ResolutionReason, RoomDoc } from "./types";

export type AnswerVerdict =
  | { ok: true; reason: ResolutionReason; round: number }
  | { ok: false; error: HttpsError };

function reject(code: HttpsError["code"], message: string, reason?: string): AnswerVerdict {
  return { ok: false, error: new HttpsError(code, message, reason ? { reason } : undefined) };
}

/**
 * Parses the optional `round` a client says it is answering. Absent means an older
 * client that doesn't send it; present but malformed is a bad request.
 */
export function parseAnsweredRound(raw: unknown): number | null | "invalid" {
  if (raw === undefined || raw === null) return null;
  return typeof raw === "number" && Number.isInteger(raw) && raw >= 0 ? raw : "invalid";
}

/**
 * Decides whether `uid` may answer right now and whether the answer is right,
 * against the room as read inside submitAnswer's transaction -- so the check
 * and the write see the same snapshot, and the room is read once, not twice.
 *
 * `answeredRound` is the round the client was looking at when the player tapped.
 * Without it, an answer is judged against whatever stimulus is current when the
 * request lands: a quick double tap would send a second answer that the server
 * scores against the *next* stimulus, one the player never saw -- in
 * solo_survival that busts them three times out of four. With it, a tap aimed at
 * a round that has already moved on is rejected as stale instead of scored.
 *
 * Returns the round to resolve (RoomDoc.round, or the player's own soloRound in
 * solo_survival) for the engine's own staleness guard.
 */
export function judgeAnswer(
  room: RoomDoc,
  uid: string,
  selectedColor: string,
  answeredRound: number | null,
  nowMs: number
): AnswerVerdict {
  if (room.mode === "solo_survival") {
    const me = room.players[uid];
    if (!me || !me.alive) return reject("failed-precondition", "Ya estás eliminado.");
    if (!me.soloStimulus || me.soloDeadlineAtMs == null) {
      return reject("failed-precondition", "No hay ronda activa.");
    }
    const round = me.soloRound ?? 0;
    if (answeredRound !== null && answeredRound !== round) {
      return reject("deadline-exceeded", "Esa ronda ya se resolvió.", "STALE_ROUND");
    }
    if (nowMs > me.soloDeadlineAtMs) return reject("deadline-exceeded", "Se acabó el tiempo.");
    // Any value that isn't an exact ink-color match -- empty, missing or malformed
    // included -- is simply a wrong answer, which is the safe outcome.
    return { ok: true, reason: selectedColor === me.soloStimulus.inkColor ? "correct" : "wrong", round };
  }

  if (room.turnOrder[room.turnIndex] !== uid) return reject("permission-denied", "No es tu turno.");
  if (!room.stimulus || !room.deadlineAtMs) return reject("failed-precondition", "No hay ronda activa.");
  if (answeredRound !== null && answeredRound !== room.round) {
    return reject("deadline-exceeded", "Esa ronda ya se resolvió.", "STALE_ROUND");
  }
  // hot_potato has no enforced per-stimulus deadline (see resolveHotPotato.ts) --
  // the holder can answer whenever, so a late answer there is never stale.
  if (room.mode !== "hot_potato" && nowMs > room.deadlineAtMs) {
    return reject("deadline-exceeded", "Se acabó el tiempo.");
  }
  return { ok: true, reason: selectedColor === room.stimulus.inkColor ? "correct" : "wrong", round: room.round };
}
