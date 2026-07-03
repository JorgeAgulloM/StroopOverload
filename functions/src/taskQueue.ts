import { getFunctions } from "firebase-admin/functions";

export async function scheduleTimeoutCheck(roomId: string, round: number, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("resolveTimeout");
  const scheduleDelaySeconds = Math.max(1, Math.ceil((delayMs + 500) / 1000));
  await queue.enqueue({ roomId, round }, { scheduleDelaySeconds });
}
