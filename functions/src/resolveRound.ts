import { getFirestore } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc } from "./types";
import { roomsCol } from "./roomRepo";
import { scheduleTimeoutCheck } from "./taskQueue";
import { applyCorrectAnswer, rankMistakeOrHotPotatoPlayers } from "./scoring";

export async function resolveRound(
  roomId: string,
  actingUid: string | null,
  reason: ResolutionReason,
  roundExpected: number
): Promise<boolean> {
  const roomRef = roomsCol().doc(roomId);

  type ScheduledTimeout = { round: number; deadlineAtMs: number } | null;
  interface ResolveRoundTxResult {
    // Whether this call actually changed the room's state (advanced the
    // round, finished the game, or updated a bystander's alive status), as
    // opposed to being a no-op because the room was missing, not playing, or
    // the round had already moved on. Distinct from `scheduled` below: a
    // no-advance bystander update or a game-finishing update both count as
    // "applied" even though neither schedules a next-round timeout.
    applied: boolean;
    scheduled: ScheduledTimeout;
  }

  const { applied, scheduled } = await getFirestore().runTransaction<ResolveRoundTxResult>(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return { applied: false, scheduled: null };
    const room = doc.data() as RoomDoc;

    // Stale call: the round already moved on (a race between timeout/answer/disconnect).
    if (room.status !== "playing" || room.round !== roundExpected) return { applied: false, scheduled: null };

    const players = { ...room.players };
    if (reason === "correct" && actingUid && players[actingUid]) {
      const me = players[actingUid];
      const { score, streak } = applyCorrectAnswer(me.matchScore ?? 0, me.matchStreak ?? 0);
      players[actingUid] = { ...me, matchScore: score, matchStreak: streak };
    } else if (reason !== "correct" && actingUid && players[actingUid]) {
      players[actingUid] = { ...players[actingUid], alive: false, eliminatedAtMs: Date.now() };
    }

    const survivor = soleSurvivor(players);
    if (survivor) {
      const finishedPlayers = { ...players };
      for (const r of rankMistakeOrHotPotatoPlayers(players, survivor)) {
        finishedPlayers[r.uid] = { ...finishedPlayers[r.uid], placement: r.placement, finalScore: r.finalScore };
      }
      tx.update(roomRef, {
        players: finishedPlayers,
        status: "finished",
        winnerUid: survivor,
        stimulus: null,
        deadlineAtMs: null,
      });
      return { applied: true, scheduled: null };
    }

    const wasCurrentTurnPlayer = actingUid === null || room.turnOrder[room.turnIndex] === actingUid;
    if (!wasCurrentTurnPlayer) {
      // A bystander (not the current turn-holder) was eliminated, but the
      // active player's round is still in progress: only persist the
      // updated players map. Do not touch turnIndex/round/stimulus/deadline,
      // and do not schedule a new timeout -- the current round's
      // already-scheduled timeout task still governs the active player.
      tx.update(roomRef, { players });
      return { applied: true, scheduled: null };
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
    return { applied: true, scheduled: { round: nextRound, deadlineAtMs } };
  });

  if (scheduled) {
    try {
      await scheduleTimeoutCheck(roomId, scheduled.round, scheduled.deadlineAtMs - Date.now());
    } catch (err) {
      console.error(`resolveRound: failed to schedule timeout for room ${roomId} round ${scheduled.round}`, err);
    }
  }

  return applied;
}
