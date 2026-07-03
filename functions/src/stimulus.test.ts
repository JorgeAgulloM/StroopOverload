import { generateStimulus, STROOP_COLORS } from "./stimulus";

describe("generateStimulus", () => {
  test("wordLabel and inkColor are never the same color", () => {
    const rng = sequence([0.1, 0.9]); // ink=RED(idx0), wordLabel picks from remaining pool
    const stimulus = generateStimulus(rng);
    expect(stimulus.wordLabel).not.toBe(stimulus.inkColor);
  });

  test("options contain all 4 colors exactly once", () => {
    const stimulus = generateStimulus(() => 0.5);
    expect(stimulus.options.slice().sort()).toEqual(STROOP_COLORS.slice().sort());
  });

  test("inkColor and wordLabel are always valid Stroop colors", () => {
    const stimulus = generateStimulus(() => 0.99);
    expect(STROOP_COLORS).toContain(stimulus.inkColor);
    expect(STROOP_COLORS).toContain(stimulus.wordLabel);
  });

  test("wordLabel never equals inkColor across many random draws (Stroop-effect invariant)", () => {
    for (let i = 0; i < 200; i++) {
      const stimulus = generateStimulus(Math.random);
      expect(stimulus.wordLabel).not.toBe(stimulus.inkColor);
    }
  });
});

function sequence(values: number[]): () => number {
  let i = 0;
  return () => values[Math.min(i++, values.length - 1)];
}
