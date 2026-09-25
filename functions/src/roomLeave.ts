import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";
import { roomsCol } from "./roomRepo";
import { RoomPlayerDoc } from "./types";

/**
 * Takes `uid` out of a room that hasn't started yet. Leaving used to only mark the player
 * offline, which does nothing before a match starts: a guest who left kept their slot, and
 * a host who left stranded everyone else (only the host can start) until the 6 h purge.
 *
 * The host role passes to the next player in join order, and the room is deleted when the
 * last player leaves -- with its RTDB presence node, which nothing could find again once
 * the doc is gone. Player orders are compacted so joinRoom's `order = player count` stays
 * unique.
 *
 * Once a match has started this is a no-op: leaving then is a forfeit, handled by the
 * presence/disconnect path.
 *
 * @returns whether the player was removed.
 */
export async function leaveWaitingRoom(roomId: string, uid: string): Promise<boolean> {
  const roomRef = roomsCol().doc(roomId);
  const outcome = await getFirestore().runTransaction<"none" | "left" | "deleted">(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return "none";
    const room = doc.data()!;
    if (room.status !== "waiting" || !room.players[uid]) return "none";

    const remaining = room.turnOrder.filter((u) => u !== uid);
    if (remaining.length === 0) {
      tx.delete(roomRef);
      return "deleted";
    }

    const players: Record<string, RoomPlayerDoc> = {};
    remaining.forEach((u, order) => {
      players[u] = { ...room.players[u], order };
    });
    tx.update(roomRef, {
      players,
      turnOrder: remaining,
      hostUid: room.hostUid === uid ? remaining[0] : room.hostUid,
    });
    return "left";
  });

  if (outcome === "deleted") {
    try {
      await getDatabase().ref(`presence/${roomId}`).remove();
    } catch (err) {
      console.error(`leaveWaitingRoom: failed to remove presence for room ${roomId}`, err);
    }
  }
  return outcome !== "none";
}
