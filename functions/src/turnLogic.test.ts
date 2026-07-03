import { aliveCount, nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";

describe("timeLimitMsForRound", () => {
  test("round 1 uses the initial time limit", () => {
    expect(timeLimitMsForRound(1)).toBe(3000);
  });

  test("time limit decays by 150ms per round", () => {
    expect(timeLimitMsForRound(2)).toBe(2850);
  });

  test("time limit never drops below the minimum", () => {
    expect(timeLimitMsForRound(100)).toBe(800);
  });
});

describe("nextAliveIndex", () => {
  const turnOrder = ["a", "b", "c", "d"];

  test("moves to the next alive player", () => {
    const players = { a: { alive: true }, b: { alive: true }, c: { alive: true }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 0)).toBe(1);
  });

  test("skips over eliminated players", () => {
    const players = { a: { alive: true }, b: { alive: false }, c: { alive: true }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 0)).toBe(2);
  });

  test("wraps around to the start", () => {
    const players = { a: { alive: true }, b: { alive: false }, c: { alive: false }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 3)).toBe(0);
  });
});

describe("aliveCount / soleSurvivor", () => {
  test("aliveCount counts only alive players", () => {
    const players = { a: { alive: true }, b: { alive: false } };
    expect(aliveCount(players)).toBe(1);
  });

  test("soleSurvivor returns the uid when exactly one player is alive", () => {
    const players = { a: { alive: false }, b: { alive: true } };
    expect(soleSurvivor(players)).toBe("b");
  });

  test("soleSurvivor returns null when more than one player is alive", () => {
    const players = { a: { alive: true }, b: { alive: true } };
    expect(soleSurvivor(players)).toBeNull();
  });
});
