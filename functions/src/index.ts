import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onValueWritten } from "firebase-functions/v2/database";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { generateUniqueRoomCode, findJoinableRoomByCode, roomsCol } from "./roomRepo";
import { GameModeId, ResolutionReason, RoomDoc, RoomPlayerDoc } from "./types";
import { applyRoundResolution, resolveRound, scheduleNextRoundTimeout } from "./resolveRound";
import { applyHotPotatoTurn, explodeBomb as explodeBombFn } from "./resolveHotPotato";
import { applySoloAnswer, finishSoloSurvivalSession, resolveSoloAnswer, scheduleNextSoloTimeout } from "./soloSurvival";
import { judgeAnswer, parseAnsweredRound } from "./answerJudge";
import { beginMatch } from "./matchStart";
import { purgeExpiredRooms as purgeExpiredRoomsFn, sweepStuckRooms as sweepStuckRoomsFn } from "./roomWatchdog";
import { scheduleGameStart } from "./taskQueue";
import {
  assertWithinRateLimit,
  CREATE_ROOM_LIMIT,
  JOIN_ROOM_LIMIT,
  RATE_LIMIT_WINDOW_MS,
  SUBMIT_SOLO_RUN_LIMIT,
} from "./rateLimit";
import { clampWinStreak, SoloMode, validateSoloRun } from "./profileScoring";
import { applyMatchAwards, applySoloRun } from "./userProfile";

initializeApp();

const MAX_PLAYERS_PER_ROOM = 4;
const MAX_DISPLAY_NAME_LENGTH = 16;
const VALID_GAME_MODES: readonly GameModeId[] = ["mistake", "hot_potato", "solo_survival"];
// Cosmetic "3, 2, 1, GO" window shown on every client while status is
// "starting". Round 1's real deadline is computed by beginRound when this
// elapses server-side -- not by startGame -- so this duration only affects
// how long the countdown animation plays, never match fairness.
const STARTING_COUNTDOWN_MS = 4000;

// App Check proves a call came from a genuine, unmodified build of this app.
// Enforcement is OFF until a build that actually sends App Check tokens is the
// one players are running: flipping it on rejects every already-installed
// client outright, which would break live matches. Steps: ship the client that
// initializes App Check (see StroopApplication), register the app in the
// Firebase console (Play Integrity), watch the "unverified requests" metric
// drop, then set this to true and redeploy.
const ENFORCE_APP_CHECK = false;

// Caps the blast radius of a traffic spike (accidental or hostile) on the
// Firestore/Cloud Tasks bill. Well above any plausible real concurrency here.
const MAX_INSTANCES = 10;

const CALLABLE_OPTIONS = { enforceAppCheck: ENFORCE_APP_CHECK, maxInstances: MAX_INSTANCES } as const;

export const createRoom = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const displayName =
    String(request.data?.displayName ?? "")
      .trim()
      .slice(0, MAX_DISPLAY_NAME_LENGTH) || "Pilot";
  const requestedMode = request.data?.mode;
  const mode: GameModeId = VALID_GAME_MODES.includes(requestedMode) ? requestedMode : "mistake";

  try {
    await assertWithinRateLimit(uid, "createRoom", CREATE_ROOM_LIMIT, RATE_LIMIT_WINDOW_MS);
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

export const joinRoom = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const code = String(request.data?.code ?? "")
    .toUpperCase()
    .trim();
  if (!/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Código de sala inválido.", { reason: "INVALID_CODE" });
  }
  const displayName =
    String(request.data?.displayName ?? "")
      .trim()
      .slice(0, MAX_DISPLAY_NAME_LENGTH) || "Pilot";

  try {
    await assertWithinRateLimit(uid, "joinRoom", JOIN_ROOM_LIMIT, RATE_LIMIT_WINDOW_MS);
    const existingDoc = await findJoinableRoomByCode(code);
    if (!existingDoc) throw new HttpsError("not-found", "Sala no encontrada o ya empezada.", { reason: "ROOM_NOT_FOUND" });
    const roomRef = existingDoc.ref;

    return await getFirestore().runTransaction(async (tx) => {
      const doc = await tx.get(roomRef);
      if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.", { reason: "ROOM_NOT_FOUND" });
      const room = doc.data() as RoomDoc;
      if (room.status !== "waiting") throw new HttpsError("failed-precondition", "La partida ya empezó.", { reason: "ALREADY_STARTED" });
      if (room.players[uid]) return { roomId: roomRef.id };
      if (Object.keys(room.players).length >= MAX_PLAYERS_PER_ROOM) {
        throw new HttpsError("resource-exhausted", "Sala llena.", { reason: "ROOM_FULL" });
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

export const startGame = onCall(CALLABLE_OPTIONS, async (request) => {
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
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 }, maxInstances: MAX_INSTANCES },
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
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 }, maxInstances: MAX_INSTANCES },
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

interface AnswerOutcome {
  reason: ResolutionReason;
  applied: boolean;
  afterCommit: () => Promise<void>;
}

/**
 * One transaction: read the room, judge the answer against that snapshot
 * (answerJudge.ts), and apply it with the engine for the room's mode. It used to
 * read the room outside the transaction first -- only to pick the engine and
 * validate -- and then the engine read it again, an extra round trip on every
 * tap of a reaction-time game.
 *
 * `round` in the request is optional so clients that predate it keep working.
 */
export const submitAnswer = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const roomId = String(request.data?.roomId ?? "").trim();
  if (!roomId) throw new HttpsError("invalid-argument", "roomId inválido.");
  const selectedColor = String(request.data?.selectedColor ?? "");
  const answeredRound = parseAnsweredRound(request.data?.round);
  if (answeredRound === "invalid") throw new HttpsError("invalid-argument", "round inválido.");

  try {
    const roomRef = roomsCol().doc(roomId);
    const outcome = await getFirestore().runTransaction<AnswerOutcome>(async (tx) => {
      const doc = await tx.get(roomRef);
      if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
      const room = doc.data() as RoomDoc;
      const verdict = judgeAnswer(room, uid, selectedColor, answeredRound, Date.now());
      if (!verdict.ok) throw verdict.error;
      const { reason, round } = verdict;

      if (room.mode === "solo_survival") {
        const result = applySoloAnswer(tx, roomRef, room, uid, reason, round);
        return {
          reason,
          applied: result.applied,
          afterCommit: () => scheduleNextSoloTimeout(roomId, uid, result.scheduled),
        };
      }
      if (room.mode === "hot_potato") {
        const applied = applyHotPotatoTurn(tx, roomRef, room, uid, reason, round);
        return { reason, applied, afterCommit: async () => undefined };
      }
      const result = applyRoundResolution(tx, roomRef, room, uid, reason, round);
      return {
        reason,
        applied: result.applied,
        afterCommit: () => scheduleNextRoundTimeout(roomId, result.scheduled),
      };
    });

    // With the judge and the engine on one snapshot this only trips when the room
    // is not "playing" (e.g. the answer landed after the match finished).
    if (!outcome.applied) throw new HttpsError("deadline-exceeded", "La ronda ya se resolvió.");
    await outcome.afterCommit();
    return { accepted: true, reason: outcome.reason };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`submitAnswer failed for uid ${uid}, room ${roomId}`, err);
    throw new HttpsError("internal", "No se pudo registrar la respuesta.");
  }
});

const MAX_CLAIMED_ACHIEVEMENTS_PER_RUN = 30;

/**
 * Records a finished single-player run and returns the profile scoring fields the
 * server computed from it. This is the only way those fields change: clients can
 * no longer write points/experience/level/highScore themselves (firestore.rules),
 * because the leaderboard used to be whatever a client claimed it was.
 *
 * Honest about its limits: the stimuli of a solo run are generated on the device,
 * so the server cannot verify that a run happened -- it recomputes the score and XP
 * from the reported counts and rejects what is impossible (profileScoring.ts).
 * A run submitted while offline simply never reaches here, which is the intended
 * behavior: offline play counts locally, not on the leaderboard.
 */
export const submitSoloRun = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  // Anonymous accounts never appear on the leaderboard, so nothing should be written for them.
  if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("permission-denied", "Las cuentas de invitado no puntúan.", { reason: "ANONYMOUS" });
  }

  const run = {
    mode: String(request.data?.mode ?? "") as SoloMode,
    correctHits: Number(request.data?.correctHits),
    totalRounds: Number(request.data?.totalRounds),
    survivalMs: Number(request.data?.survivalMs),
    finalScore: Number(request.data?.finalScore),
  };
  const rejection = validateSoloRun(run);
  if (rejection) {
    console.warn(`submitSoloRun rejected for uid ${uid}: ${rejection}`, run);
    throw new HttpsError("invalid-argument", "Partida no válida.", { reason: "INVALID_RUN" });
  }

  const claimedAchievementIds = Array.isArray(request.data?.achievementIds)
    ? (request.data.achievementIds as unknown[]).slice(0, MAX_CLAIMED_ACHIEVEMENTS_PER_RUN).map(String)
    : [];

  const winStreak = clampWinStreak(Number(request.data?.winStreak), run.correctHits);

  try {
    await assertWithinRateLimit(uid, "submitSoloRun", SUBMIT_SOLO_RUN_LIMIT, RATE_LIMIT_WINDOW_MS);
    return await applySoloRun(uid, run, claimedAchievementIds, winStreak);
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    console.error(`submitSoloRun failed for uid ${uid}`, err);
    throw new HttpsError("internal", "No se pudo registrar la partida.");
  }
});

// Deletes every room this account ever played in (host or guest --
// createRoom always seeds the host into `players` too, so one field-path
// query against the map catches both) plus that room's presence node, as
// part of account deletion. Rooms are ephemeral match sessions, not
// retained history, so wiping the whole doc is safe: it removes this
// user's uid/nickname without needing to special-case "redact vs delete"
// for the other player's copy of a finished match.
export const deleteMyMultiplayerData = onCall(CALLABLE_OPTIONS, async (request) => {
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
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 }, maxInstances: MAX_INSTANCES },
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
  { retryConfig: { maxAttempts: 5, minBackoffSeconds: 1 }, maxInstances: MAX_INSTANCES },
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

export const onPresenceChanged = onValueWritten(
  { ref: "presence/{roomId}/{uid}", maxInstances: MAX_INSTANCES },
  async (event) => {
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

// Pays out a finished match to every player's profile. Unlike a solo run this is
// fully verified -- the backend generated the stimuli, checked every answer against
// its deadline and ranked the players itself. Idempotent per room, so a retried
// delivery cannot pay twice (see userProfile.applyMatchAwards).
export const onRoomFinished = onDocumentUpdated(
  { document: "rooms/{roomId}", maxInstances: MAX_INSTANCES },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!after || before?.status === "finished" || after.status !== "finished") return;

    const roomId = event.params.roomId;
    try {
      const awarded = await applyMatchAwards(roomId);
      if (awarded > 0) console.log(`onRoomFinished: awarded ${awarded} player(s) in room ${roomId}`);
    } catch (err) {
      console.error(`onRoomFinished failed for room ${roomId}`, err);
      throw err; // let the trigger retry; applyMatchAwards is idempotent
    }
  }
);
