import { getFirestore } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { timeLimitMsForRound } from "./turnLogic";
import { RoomDoc } from "./types";
import { roomsCol } from "./roomRepo";
import { armBomb } from "./resolveHotPotato";
import { beginSoloSurvivalMatch, SOLO_SESSION_DURATION_MS } from "./soloSurvival";
import { scheduleSoloPlayerTimeoutCheck, scheduleTimeoutCheck } from "./taskQueue";

/**
 * Moves a room from "starting" to "playing": computes round 1's stimulus and
 * deadline at this instant (never at startGame's call time -- see beginRound)
 * and schedules whatever keeps the match moving for its mode. No-ops if the
 * room is no longer "starting", so a duplicate task delivery or a concurrent
 * watchdog repair is harmless.
 *
 * Throws if scheduling fails after the transition committed; the room is then
 * "playing" without its timeout/bomb, which roomWatchdog.sweepStuckRooms repairs.
 */
export async function beginMatch(roomId: string): Promise<void> {
  const roomRef = roomsCol().doc(roomId);
  const modeDoc = await roomRef.get();
  if (!modeDoc.exists) return;
  const mode = (modeDoc.data() as RoomDoc).mode;

  if (mode === "solo_survival") {
    const playerDeadlines = await beginSoloSurvivalMatch(roomId);
    if (playerDeadlines.length === 0) return; // stale/already handled
    await Promise.all(
      playerDeadlines.map((d) => scheduleSoloPlayerTimeoutCheck(roomId, d.uid, d.round, d.deadlineAtMs - Date.now()))
    );
    await scheduleTimeoutCheck(roomId, 0, SOLO_SESSION_DURATION_MS);
    return;
  }

  const scheduled = await getFirestore().runTransaction<{ round: number; deadlineAtMs: number } | null>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return null;
    const room = doc.data() as RoomDoc;
    if (room.status !== "starting") return null; // stale/already handled

    const stimulus = generateStimulus();
    const deadlineAtMs = Date.now() + timeLimitMsForRound(1);
    tx.update(roomRef, { status: "playing", round: 1, turnIndex: 0, stimulus, deadlineAtMs });
    return { round: 1, deadlineAtMs };
  });
  if (!scheduled) return;

  if (mode === "hot_potato") {
    // No timeout task for this mode -- the current holder can take as long as
    // they want between stimuli; only the hidden bomb applies real pressure.
    // See resolveHotPotato.ts.
    await armBomb(roomId);
  } else {
    await scheduleTimeoutCheck(roomId, scheduled.round, scheduled.deadlineAtMs - Date.now());
  }
}
