import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { generateUniqueRoomCode, findJoinableRoomByCode, roomsCol } from "./roomRepo";
import { RoomDoc, RoomPlayerDoc } from "./types";

initializeApp();

const MAX_PLAYERS_PER_ROOM = 4;
const MAX_DISPLAY_NAME_LENGTH = 16;

export const createRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const displayName = String(request.data?.displayName ?? "Pilot").slice(0, MAX_DISPLAY_NAME_LENGTH);

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
    hostUid: uid,
    players: { [uid]: hostPlayer },
    turnOrder: [uid],
    turnIndex: 0,
    round: 0,
    stimulus: null,
    deadlineAtMs: null,
    winnerUid: null,
    createdAtMs: Date.now(),
  };
  await roomRef.set(room);
  return { roomId: roomRef.id, code };
});

export const joinRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const code = String(request.data?.code ?? "")
    .toUpperCase()
    .trim();
  const displayName = String(request.data?.displayName ?? "Pilot").slice(0, MAX_DISPLAY_NAME_LENGTH);

  const existingDoc = await findJoinableRoomByCode(code);
  if (!existingDoc) throw new HttpsError("not-found", "Sala no encontrada o ya empezada.");
  const roomRef = existingDoc.ref;

  return getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
    const room = doc.data() as RoomDoc;
    if (room.status !== "waiting") throw new HttpsError("failed-precondition", "La partida ya empezó.");
    if (room.players[uid]) return { roomId: roomRef.id };
    if (Object.keys(room.players).length >= MAX_PLAYERS_PER_ROOM) {
      throw new HttpsError("resource-exhausted", "Sala llena.");
    }

    const order = Object.keys(room.players).length;
    const newPlayer: RoomPlayerDoc = { uid, displayName, avatarIndex: 0, alive: true, order, joinedAtMs: Date.now() };
    const updatedPlayers = { ...room.players, [uid]: newPlayer };
    tx.update(roomRef, { players: updatedPlayers, turnOrder: [...room.turnOrder, uid] });
    return { roomId: roomRef.id };
  });
});
