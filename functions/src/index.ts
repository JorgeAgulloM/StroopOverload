import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onValueWritten } from "firebase-functions/v2/database";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { generateUniqueRoomCode, findJoinableRoomByCode, roomsCol } from "./roomRepo";
import { GameModeId, ResolutionReason, RoomDoc, RoomPlayerDoc } from "./types";
import { resolveRound } from "./resolveRound";
import { explodeBomb as explodeBombFn, resolveHotPotatoTurn } from "./resolveHotPotato";
import { finishSoloSurvivalSession, resolveSoloAnswer } from "./soloSurvival";
import { beginMatch } from "./matchStart";
import { purgeExpiredRooms as purgeExpiredRoomsFn, sweepStuckRooms as sweepStuckRoomsFn } from "./roomWatchdog";
import { scheduleGameStart } from "./taskQueue";

initializeApp();

const MAX_PLAYERS_PER_ROOM = 4;
const MAX_DISPLAY_NAME_LENGTH = 16;
const VALID_GAME_MODES: readonly GameModeId[] = ["mistake", "hot_potato", "solo_survival"];
// Cosmetic "3, 2, 1, GO" window shown on every client while status is
// "starting". Round 1's real deadline is computed by beginRound when this
// elapses server-side -- not by startGame -- so this duration only affects
// how long the countdown animation plays, never match fairness.
const STARTING_COUNTDOWN_MS = 4000;

export const createRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const displayName =
    String(request.data?.displayName ?? "")
      .trim()
      .slice(0, MAX_DISPLAY_NAME_LENGTH) || "Pilot";
  const requestedMode = request.data?.mode;
  const mode: GameModeId = VALID_GAME_MODES.includes(requestedMode) ? requestedMode : "mistake";

  try {
    const code = await generateUniqueRoomCode();
    const roomRef = roomsCol().doc();
    const hostPlayer: RoomPlayerDoc = {
      uid,
      displayName,
      avatarIndex: 0,
      alive: true,
      order: 0,
      joinedAtMs: Date.now(),
    };
    const room: RoomDoc = {
      code,
      status: "waiting",
      mode,
      hostUid: uid,
      players: { [uid]: hostPlayer },
      turnOrder: [uid],
      turnIndex: 0,
      round: 0,
      stimulus: null,
      deadlineAtMs: null,
      winnerUid: null,
      createdAtMs: Date.now(),
      startsAtMs: null,
    };
    await roomRef.set(room);
    return { roomId: roomRef.id, code };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`createRoom failed for uid ${uid}`, err);
    throw new HttpsError("internal", "No se pudo crear la sala.");
  }
});

export const joinRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const code = String(request.data?.code ?? "")
    .toUpperCase()
    .trim();
  if (!/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Código de sala inválido.");
  }
  const displayName =
    String(request.data?.displayName ?? "")
      .trim()
      .slice(0, MAX_DISPLAY_NAME_LENGTH) || "Pilot";

  try {
    const existingDoc = await findJoinableRoomByCode(code);
    if (!existingDoc) throw new HttpsError("not-found", "Sala no encontrada o ya empezada.");
    const roomRef = existingDoc.ref;

    return await getFirestore().runTransaction(async (tx) => {
      const doc = await tx.get(roomRef);
      if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
      const room = doc.data() as RoomDoc;
      if (room.status !== "waiting") throw new HttpsError("failed-precondition", "La partida ya empezó.");
      if (room.players[uid]) return { roomId: roomRef.id };
      if (Object.keys(room.players).length >= MAX_PLAYERS_PER_ROOM) {
        throw new HttpsError("resource-exhausted", "Sala llena.");
      }

      const order = Object.keys(room.players).length;
      const newPlayer: RoomPlayerDoc = {
        uid,
        displayName,
        avatarIndex: 0,
        alive: true,
        order,
        joinedAtMs: Date.now(),
      };
      const updatedPlayers = { ...room.players, [uid]: newPlayer };
      tx.update(roomRef, { players: updatedPlayers, turnOrder: [...room.turnOrder, uid] });
      return { roomId: roomRef.id };
    });
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`joinRoom failed for uid ${uid}, code ${code}`, err);
    throw new HttpsError("internal", "No se pudo unir a la sala.");
  }
});

export const startGame = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const roomId = String(request.data?.roomId ?? "").trim();
  if (!roomId) throw new HttpsError("invalid-argument", "roomId inválido.");

  try {
    const roomRef = roomsCol().doc(roomId);
    const startsAtMs = Date.now() + STARTING_COUNTDOWN_MS;
    await getFirestore().runTransaction(async (tx) => {
      const doc = await tx.get(roomRef);
      if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
      const room = doc.data() as RoomDoc;
      if (room.hostUid !== uid) throw new HttpsError("permission-denied", "Solo el host puede empezar.");
      if (room.status !== "waiting") throw new HttpsError("failed-precondition", "La partida ya empezó.");
      if (room.turnOrder.length < 2) throw new HttpsError("failed-precondition", "Se necesitan al menos 2 jugadores.");

      tx.update(roomRef, { status: "starting", startsAtMs, stimulus: null, deadlineAtMs: null });
    });

    await scheduleGameStart(roomId, STARTING_COUNTDOWN_MS);
    return { startsAtMs };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`startGame failed for uid ${uid}, room ${roomId}`, err);
    throw new HttpsError("internal", "No se pudo iniciar la partida.");
  }
});

// Fired by a Cloud Task scheduled from startGame, timed to run at the room's
// startsAtMs. This is what actually computes round 1's stimulus/deadlineAtMs
// -- at the real synchronized start instant, not whenever startGame's
// synchronous call happened -- so every client's answer window is the full
// configured duration regardless of how long their local countdown/render took.
export const beginRound = onTaskDispatched<{ roomId: string }>(
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 } },
  async (req) => {
    const { roomId } = req.data;
    try {
      await beginMatch(roomId);
    } catch (err) {
      console.error(`beginRound failed for room ${roomId}`, err);
      throw err;
    }
  }
);

// Fired by the Cloud Task armBomb schedules. See resolveHotPotato.ts for the logic.
export const explodeBomb = onTaskDispatched<{ roomId: string; bombAtMs?: number }>(
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 } },
  async (req) => {
    const { roomId, bombAtMs } = req.data;
    try {
      await explodeBombFn(roomId, bombAtMs);
    } catch (err) {
      console.error(`explodeBomb failed for room ${roomId}`, err);
      throw err;
    }
  }
);

export const submitAnswer = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const roomId = String(request.data?.roomId ?? "").trim();
  if (!roomId) throw new HttpsError("invalid-argument", "roomId inválido.");
  const selectedColor = String(request.data?.selectedColor ?? "");

  try {
    const roomRef = roomsCol().doc(roomId);
    const doc = await roomRef.get();
    if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
    const room = doc.data() as RoomDoc;

    if (room.mode === "solo_survival") {
      const me = room.players[uid];
      if (!me || !me.alive) throw new HttpsError("failed-precondition", "Ya estás eliminado.");
      if (!me.soloStimulus || me.soloDeadlineAtMs == null) throw new HttpsError("failed-precondition", "No hay ronda activa.");
      if (Date.now() > me.soloDeadlineAtMs) throw new HttpsError("deadline-exceeded", "Se acabó el tiempo.");

      const reason: ResolutionReason = selectedColor === me.soloStimulus.inkColor ? "correct" : "wrong";
      const applied = await resolveSoloAnswer(roomId, uid, reason, me.soloRound ?? 0);
      if (!applied) throw new HttpsError("deadline-exceeded", "La ronda ya se resolvió (probablemente por timeout).");
      return { accepted: true, reason };
    }

    const currentTurnUid = room.turnOrder[room.turnIndex];
    if (currentTurnUid !== uid) throw new HttpsError("permission-denied", "No es tu turno.");
    if (!room.stimulus || !room.deadlineAtMs) throw new HttpsError("failed-precondition", "No hay ronda activa.");
    // hot_potato has no enforced per-stimulus deadline (see resolveHotPotato.ts) --
    // the holder can answer whenever, so a late answer here is never stale.
    if (room.mode !== "hot_potato" && Date.now() > room.deadlineAtMs) {
      throw new HttpsError("deadline-exceeded", "Se acabó el tiempo.");
    }

    // `selectedColor` is compared against the stimulus's ink color as a plain
    // string equality check. Any value that isn't an exact match --
    // including empty, missing, or otherwise malformed input -- naturally
    // falls through to "wrong", which is already the correct, safe
    // semantics for an invalid answer. No separate allow-list validation
    // against StroopColorId is needed here.
    const reason: ResolutionReason = selectedColor === room.stimulus.inkColor ? "correct" : "wrong";
    const applied =
      room.mode === "hot_potato"
        ? await resolveHotPotatoTurn(roomId, uid, reason, room.round)
        : await resolveRound(roomId, uid, reason, room.round);
    if (!applied) throw new HttpsError("deadline-exceeded", "La ronda ya se resolvió (probablemente por timeout).");
    return { accepted: true, reason };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`submitAnswer failed for uid ${uid}, room ${roomId}`, err);
    throw new HttpsError("internal", "No se pudo registrar la respuesta.");
  }
});

// Deletes every room this account ever played in (host or guest --
// createRoom always seeds the host into `players` too, so one field-path
// query against the map catches both) plus that room's presence node, as
// part of account deletion. Rooms are ephemeral match sessions, not
// retained history, so wiping the whole doc is safe: it removes this
// user's uid/nickname without needing to special-case "redact vs delete"
// for the other player's copy of a finished match.
export const deleteMyMultiplayerData = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");

  try {
    const snap = await roomsCol().where(`players.${uid}.uid`, "==", uid).get();
    const db = getDatabase();
    await Promise.all(
      snap.docs.map(async (doc) => {
        await doc.ref.delete();
        try {
          await db.ref(`presence/${doc.id}`).remove();
        } catch (err) {
          console.error(`deleteMyMultiplayerData: failed to remove presence for room ${doc.id}`, err);
        }
      })
    );
    return { roomsDeleted: snap.size };
  } catch (err) {
    console.error(`deleteMyMultiplayerData failed for uid ${uid}`, err);
    throw new HttpsError("internal", "No se pudieron eliminar los datos multijugador.");
  }
});

interface ResolveTimeoutTaskData {
  roomId: string;
  round: number;
}

export const resolveTimeout = onTaskDispatched<ResolveTimeoutTaskData>(
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 } },
  async (req) => {
    const { roomId, round } = req.data;
    try {
      const roomRef = roomsCol().doc(roomId);
      const doc = await roomRef.get();
      if (!doc.exists) return;
      const room = doc.data() as RoomDoc;
      if (room.status !== "playing" || room.round !== round) return; // stale/already-resolved round
      if (!room.deadlineAtMs || Date.now() < room.deadlineAtMs) return; // hasn't reached its deadline yet

      // solo_survival never advances RoomDoc.round (each player tracks their
      // own soloRound instead), so it's always scheduled/checked with round 0
      // -- this is the shared session-end clock, not a per-turn timeout.
      if (room.mode === "solo_survival") {
        await finishSoloSurvivalSession(roomId);
        return;
      }

      if (room.mode === "hot_potato") {
        // No timeout task is scheduled for this mode anymore (see beginRound) --
        // guard kept only in case an already-scheduled task from before this
        // change ever fires; resolveRound's elimination logic must never run
        // against a hot_potato room.
        return;
      }

      const timedOutUid = room.turnOrder[room.turnIndex];
      await resolveRound(roomId, timedOutUid, "timeout", round);
    } catch (err) {
      console.error(`resolveTimeout failed for room ${roomId}, round ${round}`, err);
      throw err;
    }
  }
);

interface ResolveSoloPlayerTimeoutTaskData {
  roomId: string;
  uid: string;
  round: number;
}

// solo_survival's per-player equivalent of resolveTimeout: each player has
// their own stimulus/deadline, so each gets its own scheduled check instead
// of sharing one room-level timeout.
export const resolveSoloPlayerTimeout = onTaskDispatched<ResolveSoloPlayerTimeoutTaskData>(
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 } },
  async (req) => {
    const { roomId, uid, round } = req.data;
    try {
      const roomRef = roomsCol().doc(roomId);
      const doc = await roomRef.get();
      if (!doc.exists) return;
      const room = doc.data() as RoomDoc;
      if (room.status !== "playing") return;
      const player = room.players[uid];
      if (!player || !player.alive || (player.soloRound ?? 0) !== round) return; // stale/already-resolved
      if (!player.soloDeadlineAtMs || Date.now() < player.soloDeadlineAtMs) return; // hasn't reached its deadline yet

      await resolveSoloAnswer(roomId, uid, "timeout", round);
    } catch (err) {
      console.error(`resolveSoloPlayerTimeout failed for room ${roomId}, uid ${uid}, round ${round}`, err);
      throw err;
    }
  }
);

export const onPresenceChanged = onValueWritten("presence/{roomId}/{uid}", async (event) => {
  const roomId = event.params.roomId;
  const uid = event.params.uid;
  try {
    const after = event.data.after.val() as { state: string } | null;
    if (!after || after.state !== "offline") return;

    const roomRef = roomsCol().doc(roomId);
    const doc = await roomRef.get();
    if (!doc.exists) return;
    const room = doc.data() as RoomDoc;
    if (room.status !== "playing" || !room.players[uid]?.alive) return;
    // Patata Caliente's only elimination path is the hidden bomb -- a
    // disconnect here doesn't eliminate anyone. If the disconnected player is
    // holding the turn, play just stalls on them until either they reconnect
    // or the bomb goes off (which resolves correctly either way, since the
    // bomb only checks who currently holds the turn).
    if (room.mode === "hot_potato") return;

    if (room.mode === "solo_survival") {
      await resolveSoloAnswer(roomId, uid, "disconnect", room.players[uid].soloRound ?? 0);
      return;
    }

    await resolveRound(roomId, uid, "disconnect", room.round);
  } catch (err) {
    console.error(`onPresenceChanged failed for room ${roomId}, uid ${uid}`, err);
  }
});

// Safety net for rooms whose next Cloud Task was never enqueued (see
// roomWatchdog.ts). A stuck room is recovered within about a minute.
export const sweepStuckRooms = onSchedule("every 1 minutes", async () => {
  const repaired = await sweepStuckRoomsFn();
  if (repaired > 0) console.warn(`sweepStuckRooms: repaired ${repaired} stuck room(s)`);
});

export const purgeExpiredRooms = onSchedule("every 60 minutes", async () => {
  const deleted = await purgeExpiredRoomsFn();
  if (deleted > 0) console.log(`purgeExpiredRooms: deleted ${deleted} expired room(s)`);
});
