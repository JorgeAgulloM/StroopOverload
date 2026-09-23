import { getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export const RATE_LIMITS_COLLECTION = "rateLimits";

// Deliberately generous: these exist to stop automated abuse (room-creation
// spam that costs Firestore/Cloud Tasks quota, and brute-forcing the 5-char
// room code), not to get in a real player's way.
export const CREATE_ROOM_LIMIT = 10;
export const JOIN_ROOM_LIMIT = 20;
// A solo run takes at least a few seconds to play, so this only trips for
// automated submission.
export const SUBMIT_SOLO_RUN_LIMIT = 10;
export const RATE_LIMIT_WINDOW_MS = 60_000;

interface ActionWindow {
  windowStartMs: number;
  count: number;
}

/**
 * Fixed-window per-uid counter, stored in rateLimits/{uid} (server-only; see
 * firestore.rules). Throws resource-exhausted with `details.reason ===
 * "RATE_LIMITED"` once `limit` calls of `action` happened inside `windowMs`.
 *
 * `nowMs` is injectable for tests only; production callers use the default.
 */
export async function assertWithinRateLimit(
  uid: string,
  action: string,
  limit: number,
  windowMs: number,
  nowMs: number = Date.now()
): Promise<void> {
  const ref = getFirestore().collection(RATE_LIMITS_COLLECTION).doc(uid);

  const allowed = await getFirestore().runTransaction<boolean>(async (tx) => {
    const doc = await tx.get(ref);
    const current = doc.exists ? (doc.data()?.[action] as ActionWindow | undefined) : undefined;

    const inWindow = current !== undefined && nowMs - current.windowStartMs < windowMs;
    if (!inWindow) {
      tx.set(ref, { [action]: { windowStartMs: nowMs, count: 1 } }, { merge: true });
      return true;
    }
    if (current.count >= limit) return false;

    tx.set(ref, { [action]: { windowStartMs: current.windowStartMs, count: current.count + 1 } }, { merge: true });
    return true;
  });

  if (!allowed) {
    throw new HttpsError("resource-exhausted", "Demasiados intentos. Espera un momento.", { reason: "RATE_LIMITED" });
  }
}
