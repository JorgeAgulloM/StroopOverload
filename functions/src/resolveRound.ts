import { getFirestore } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc } from "./types";
import { roomsCol } from "./roomRepo";
import { scheduleTimeoutCheck } from "./taskQueue";

export async function resolveRound(
  roomId: string,
  actingUid: string | null,
  reason: ResolutionReason,
  roundExpected: number
): Promise<void> {
  const roomRef = roomsCol().doc(roomId);

  type ScheduledTimeout = { round: number; deadlineAtMs: number } | null;

  const scheduled = await getFirestore().runTransaction<ScheduledTimeout>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return null;
    const room = doc.data() as RoomDoc;

    // Stale call: the round already moved on (a race between timeout/answer/disconnect).
    if (room.status !== "playing" || room.round !== roundExpected) return null;

    const players = { ...room.players };
    if (reason !== "correct" && actingUid) {
      players[actingUid] = { ...players[actingUid], alive: false };
    }

    const survivor = soleSurvivor(players);
    if (survivor) {
      tx.update(roomRef, {
        players,
        status: "finished",
        winnerUid: survivor,
        stimulus: null,
        deadlineAtMs: null,
      });
      return null;
    }

    const nextIndex = nextAliveIndex(room.turnOrder, players, room.turnIndex);
    const nextRound = room.round + 1;
    const stimulus = generateStimulus();
    const deadlineAtMs = Date.now() + timeLimitMsForRound(nextRound);

    tx.update(roomRef, {
      players,
      turnIndex: nextIndex,
      round: nextRound,
      stimulus,
      deadlineAtMs,
    });
    return { round: nextRound, deadlineAtMs };
  });

  if (scheduled) {
    await scheduleTimeoutCheck(roomId, scheduled.round, scheduled.deadlineAtMs - Date.now());
  }
}
