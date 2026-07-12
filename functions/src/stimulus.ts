export const STROOP_COLORS = ["RED", "GREEN", "BLUE", "YELLOW"] as const;
export type StroopColorId = (typeof STROOP_COLORS)[number];

export interface Stimulus {
  wordLabel: StroopColorId;
  inkColor: StroopColorId;
  options: StroopColorId[];
}

export function generateStimulus(rng: () => number = Math.random): Stimulus {
  const inkColor = pick(STROOP_COLORS, [], rng);
  const wordLabel = pick(STROOP_COLORS, [inkColor], rng);
  const options = shuffle([...STROOP_COLORS], rng);
  return { wordLabel, inkColor, options };
}

function pick(pool: readonly StroopColorId[], exclude: StroopColorId[], rng: () => number): StroopColorId {
  const filtered = pool.filter((c) => !exclude.includes(c));
  if (filtered.length === 0) {
    throw new Error("pick() has no candidates left after applying the exclude list");
  }
  return filtered[Math.floor(rng() * filtered.length)];
}

function shuffle<T>(arr: T[], rng: () => number): T[] {
  const copy = [...arr];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}
