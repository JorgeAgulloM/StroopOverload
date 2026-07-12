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

  test("wraps back to fromIndex when it is the only player alive", () => {
    const players = { a: { alive: true }, b: { alive: false }, c: { alive: false }, d: { alive: false } };
    expect(nextAliveIndex(turnOrder, players, 0)).toBe(0);
  });

  test("returns fromIndex unchanged when every player is dead", () => {
    const players = { a: { alive: false }, b: { alive: false }, c: { alive: false }, d: { alive: false } };
    expect(nextAliveIndex(turnOrder, players, 2)).toBe(2);
  });

  test("returns 0 without throwing when turnOrder is empty", () => {
    expect(nextAliveIndex([], {}, 0)).toBe(0);
  });

  test("wraps back to itself when there is only a single alive player", () => {
    const singlePlayers = { a: { alive: true } };
    expect(nextAliveIndex(["a"], singlePlayers, 0)).toBe(0);
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
