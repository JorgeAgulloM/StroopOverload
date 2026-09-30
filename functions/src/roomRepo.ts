import { CollectionReference, DocumentReference } from "firebase-admin/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { RoomBombDoc, RoomDoc } from "./types";

export const ROOMS_COLLECTION = "rooms";
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const MAX_CODE_GENERATION_ATTEMPTS = 5;

export function roomsCol(): CollectionReference<RoomDoc> {
  return getFirestore().collection(ROOMS_COLLECTION) as CollectionReference<RoomDoc>;
}

// Only reachable via the Admin SDK -- firestore.rules denies all client
// access to rooms/{roomId}/private/**. Use this for anything a player must
// never be able to read ahead of time (e.g. Patata Caliente's bomb deadline).
export function privateBombDoc(roomId: string): DocumentReference<RoomBombDoc> {
  return roomsCol().doc(roomId).collection("private").doc("bomb") as DocumentReference<RoomBombDoc>;
}

export function generateRoomCode(): string {
  let code = "";
  for (let i = 0; i < 5; i++) {
    code += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  }
  return code;
}

/**
 * Looks up a room that is still joinable by its short code.
 * Filters to status "waiting" so a stale/finished room reusing an old
 * code (codes are never deleted) can never be matched by mistake.
 */
export async function findJoinableRoomByCode(code: string) {
  const snap = await roomsCol().where("code", "==", code).where("status", "==", "waiting").limit(1).get();
  return snap.empty ? null : snap.docs[0];
}

/**
 * Generates a room code that isn't currently in use by another joinable
 * ("waiting") room. Retries a bounded number of times on collision rather
 * than trusting generateRoomCode()'s output blindly -- codes are short
 * (5 chars from a 32-char alphabet) so collisions against the small set
 * of concurrently-open rooms are rare but not impossible.
 *
 * Check-then-write, not atomic: two createRoom calls drawing the same code
 * within the same instant would both pass. Accepted -- that is about 1 in
 * 33.5M per concurrent pair, and the worst case is some joiners landing in
 * the other waiting room. A transactional roomCodes/{code} reservation would
 * cost extra writes and cleanup on every room for that.
 */
export async function generateUniqueRoomCode(): Promise<string> {
  for (let attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
    const candidate = generateRoomCode();
    const existing = await findJoinableRoomByCode(candidate);
    if (!existing) return candidate;
  }
  throw new Error("Could not generate a unique room code after multiple attempts");
}
