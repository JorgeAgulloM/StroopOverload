export const INITIAL_TIME_LIMIT_MS = 3000;
export const TIME_LIMIT_DECAY_MS = 150;
export const MINIMUM_TIME_LIMIT_MS = 800;

export interface AliveState {
  alive: boolean;
}

export function timeLimitMsForRound(round: number): number {
  const t = INITIAL_TIME_LIMIT_MS - (round - 1) * TIME_LIMIT_DECAY_MS;
  return Math.max(t, MINIMUM_TIME_LIMIT_MS);
}

export function nextAliveIndex(
  turnOrder: readonly string[],
  players: Record<string, AliveState>,
  fromIndex: number
): number {
  const n = turnOrder.length;
  if (n === 0) return fromIndex;
  for (let step = 1; step <= n; step++) {
    const idx = (((fromIndex + step) % n) + n) % n;
    if (players[turnOrder[idx]]?.alive) return idx;
  }
  return fromIndex;
}

export function aliveCount(players: Record<string, AliveState>): number {
  return Object.values(players).filter((p) => p.alive).length;
}

export function soleSurvivor(players: Record<string, AliveState>): string | null {
  const alive = Object.entries(players).filter(([, p]) => p.alive);
  return alive.length === 1 ? alive[0][0] : null;
}
