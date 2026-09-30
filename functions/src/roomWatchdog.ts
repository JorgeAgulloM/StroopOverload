import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { RoomDoc } from "./types";
import { privateBombDoc, roomsCol } from "./roomRepo";
import { beginMatch } from "./matchStart";
import { resolveRound } from "./resolveRound";
import { armBomb, explodeBomb } from "./resolveHotPotato";
import { finishSoloSurvivalSession, resolveSoloAnswer } from "./soloSurvival";

// How late a scheduled task must be before the watchdog steps in. Normal
// tasks fire within a second or two of their deadline; this only needs to be
// long enough that the sweep doesn't routinely race them (a race is harmless
// anyway -- every repair below goes through the same round/status/bomb guards
// the tasks use, so whichever runs second no-ops).
export const WATCHDOG_GRACE_MS = 15_000;

// Rooms are ephemeral match sessions. Anything older than this is dead
// regardless of status (abandoned lobby, finished match, or a stuck room the
// watchdog couldn't save) and is deleted along with its private data and presence.
export const ROOM_TTL_MS = 6 * 60 * 60 * 1000;
const PURGE_BATCH_LIMIT = 300;
// Live rooms looked at per sweep. Oldest first: a stuck room stays live while healthy ones
// finish within minutes, so it reaches the front of the scan instead of being starved.
const SWEEP_BATCH_LIMIT = 300;
const DELETE_PAGE_SIZE = 100;

function isOverdue(atMs: number | null | undefined, nowMs: number): boolean {
  return atMs != null && atMs + WATCHDOG_GRACE_MS < nowMs;
}

/**
 * Every live room depends on a Cloud Task (beginRound, resolveTimeout,
 * resolveSoloPlayerTimeout, explodeBomb) to keep moving. If enqueueing one of
 * those fails, nothing else would ever advance the room -- players would be
 * stuck forever. This sweep finds live rooms whose next step is overdue and
 * performs it directly.
 *
 * @returns how many rooms were repaired.
 */
export async function sweepStuckRooms(nowMs: number = Date.now(), batchLimit: number = SWEEP_BATCH_LIMIT): Promise<number> {
  // Needs the (status, createdAtMs) composite index in firestore.indexes.json.
  const snap = await roomsCol()
    .where("status", "in", ["starting", "playing"])
    .orderBy("createdAtMs")
    .limit(batchLimit)
    .get();
  const results = await Promise.allSettled(snap.docs.map((doc) => repairRoom(doc.id, doc.data(), nowMs)));

  let repaired = 0;
  results.forEach((result, i) => {
    if (result.status === "rejected") {
      console.error(`sweepStuckRooms: failed to repair room ${snap.docs[i].id}`, result.reason);
    } else if (result.value) {
      repaired++;
    }
  });
  return repaired;
}

async function repairRoom(roomId: string, room: RoomDoc, nowMs: number): Promise<boolean> {
  if (room.status === "starting") {
    if (!isOverdue(room.startsAtMs, nowMs)) return false;
    await beginMatch(roomId);
    return true;
  }

  switch (room.mode) {
    case "hot_potato":
      return repairHotPotato(roomId, nowMs);
    case "solo_survival":
      return repairSoloSurvival(roomId, room, nowMs);
    default:
      if (!isOverdue(room.deadlineAtMs, nowMs)) return false;
      await resolveRound(roomId, room.turnOrder[room.turnIndex], "timeout", room.round);
      return true;
  }
}

async function repairHotPotato(roomId: string, nowMs: number): Promise<boolean> {
  const bombDoc = await privateBombDoc(roomId).get();
  if (!bombDoc.exists) {
    // Arming failed (or never happened) -- without a bomb nobody can ever be
    // eliminated. A second arm racing explodeBomb's own is harmless: only the
    // bomb doc's final bombAtMs can detonate, the other task no-ops.
    await armBomb(roomId);
    return true;
  }
  const { bombAtMs } = bombDoc.data()!;
  if (!isOverdue(bombAtMs, nowMs)) return false;
  await explodeBomb(roomId, bombAtMs);
  return true;
}

async function repairSoloSurvival(roomId: string, room: RoomDoc, nowMs: number): Promise<boolean> {
  // solo_survival repurposes deadlineAtMs as the shared session-end clock.
  if (isOverdue(room.deadlineAtMs, nowMs)) {
    await finishSoloSurvivalSession(roomId);
    return true;
  }

  const overduePlayers = Object.values(room.players).filter((p) => p.alive && isOverdue(p.soloDeadlineAtMs, nowMs));
  // Sequential on purpose: each resolution is a transaction on the same room doc.
  for (const player of overduePlayers) {
    await resolveSoloAnswer(roomId, player.uid, "timeout", player.soloRound ?? 0);
  }
  return overduePlayers.length > 0;
}

/**
 * Deletes rooms older than ROOM_TTL_MS (including rooms/{id}/private/**) and
 * their RTDB presence node. Bounded per run; a backlog drains over successive runs.
 *
 * @returns how many rooms were deleted.
 */
export async function purgeExpiredRooms(nowMs: number = Date.now()): Promise<number> {
  const snap = await roomsCol()
    .where("createdAtMs", "<", nowMs - ROOM_TTL_MS)
    .limit(PURGE_BATCH_LIMIT)
    .get();

  const db = getFirestore();
  const presence = getDatabase();
  await Promise.all(
    snap.docs.map(async (doc) => {
      await db.recursiveDelete(doc.ref);
      try {
        await presence.ref(`presence/${doc.id}`).remove();
      } catch (err) {
        console.error(`purgeExpiredRooms: failed to remove presence for room ${doc.id}`, err);
      }
    })
  );
  return snap.size;
}

/**
 * Deletes every room [uid] is a player in (host or guest -- createRoom seeds the host into
 * `players` too), including rooms/{id}/private/** and the room's presence node. A plain
 * delete() would orphan the private subcollection, which no later query could ever find.
 * Paged so a long history can't blow up one query; deleted rooms drop out of the next page.
 *
 * @returns how many rooms were deleted.
 */
export async function deletePlayerRooms(uid: string, pageSize: number = DELETE_PAGE_SIZE): Promise<number> {
  const db = getFirestore();
  const presence = getDatabase();
  let deleted = 0;
  for (;;) {
    const snap = await roomsCol().where(`players.${uid}.uid`, "==", uid).limit(pageSize).get();
    await Promise.all(
      snap.docs.map(async (doc) => {
        await db.recursiveDelete(doc.ref);
        try {
          await presence.ref(`presence/${doc.id}`).remove();
        } catch (err) {
          console.error(`deletePlayerRooms: failed to remove presence for room ${doc.id}`, err);
        }
      })
    );
    deleted += snap.size;
    if (snap.size < pageSize) return deleted;
  }
}
