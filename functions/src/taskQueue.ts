import { getFunctions } from "firebase-admin/functions";

export async function scheduleTimeoutCheck(roomId: string, round: number, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("resolveTimeout");
  const scheduleDelaySeconds = Math.max(1, Math.ceil((delayMs + 500) / 1000));
  await queue.enqueue({ roomId, round }, { scheduleDelaySeconds });
}

export async function scheduleGameStart(roomId: string, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("beginRound");
  const scheduleDelaySeconds = Math.max(1, Math.ceil(delayMs / 1000));
  await queue.enqueue({ roomId }, { scheduleDelaySeconds });
}

// bombAtMs identifies which bomb this task is for, so a duplicate delivery or a
// task for a bomb that was since replaced can be recognised and ignored.
export async function scheduleBombExplosion(roomId: string, delayMs: number, bombAtMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("explodeBomb");
  const scheduleDelaySeconds = Math.max(1, Math.ceil(delayMs / 1000));
  await queue.enqueue({ roomId, bombAtMs }, { scheduleDelaySeconds });
}

// solo_survival only -- unlike the turn-based modes' single per-round timeout,
// every player has their own independent stimulus/deadline, so each needs its
// own scheduled check keyed by (roomId, uid, round).
export async function scheduleSoloPlayerTimeoutCheck(roomId: string, uid: string, round: number, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("resolveSoloPlayerTimeout");
  const scheduleDelaySeconds = Math.max(1, Math.ceil((delayMs + 500) / 1000));
  await queue.enqueue({ roomId, uid, round }, { scheduleDelaySeconds });
}
