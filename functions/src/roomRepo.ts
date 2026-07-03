import { getFirestore } from "firebase-admin/firestore";

export const ROOMS_COLLECTION = "rooms";
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function roomsCol() {
  return getFirestore().collection(ROOMS_COLLECTION);
}

export function generateRoomCode(): string {
  let code = "";
  for (let i = 0; i < 5; i++) {
    code += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  }
  return code;
}
