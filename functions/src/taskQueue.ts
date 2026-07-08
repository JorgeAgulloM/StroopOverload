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

export async function scheduleBombExplosion(roomId: string, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("explodeBomb");
  const scheduleDelaySeconds = Math.max(1, Math.ceil(delayMs / 1000));
  await queue.enqueue({ roomId }, { scheduleDelaySeconds });
}
