import {
  achievementXpFor,
  calculateRunXp,
  levelFromTotalXp,
  maxPlausibleScore,
  pointsDeltaFor,
  validateSoloRun,
  xpForLevel,
} from "./profileScoring";

// These must stay identical to the Android client's XpSystem.kt -- the server is
// the one that writes the numbers now, and a mismatch would make a player's
// visible progress jump the moment the server recomputed it.
describe("xp curve (mirrors XpSystem.kt)", () => {
  test("xpForLevel matches level * (400 + 16 * level) / 5, integer division", () => {
    expect(xpForLevel(1)).toBe(83); // 1 * 416 / 5 = 83.2 -> 83
    expect(xpForLevel(2)).toBe(172);
    expect(xpForLevel(10)).toBe(1120);
  });

  test("levelFromTotalXp consumes one level's cost at a time", () => {
    expect(levelFromTotalXp(0)).toBe(1);
    expect(levelFromTotalXp(82)).toBe(1);
    expect(levelFromTotalXp(83)).toBe(2);
    expect(levelFromTotalXp(83 + 172)).toBe(3);
  });

  test("leveling is uncapped", () => {
    expect(levelFromTotalXp(10_000_000)).toBeGreaterThan(99);
  });
});

describe("calculateRunXp (mirrors XpSystem.calculateGameXp)", () => {
  const run = {
    mode: "ENDLESS" as const,
    correctHits: 10,
    totalRounds: 12,
    survivalMs: 25_000,
    finalScore: 1500,
  };

  test("awards nothing for a run with no correct hits or no score", () => {
    expect(calculateRunXp({ ...run, correctHits: 0 }, 0, 0, false)).toBe(0);
    expect(calculateRunXp({ ...run, finalScore: 0 }, 0, 0, false)).toBe(0);
  });

  test("a won run scores hits * 15 + 50, a lost one hits * 5", () => {
    // won: 10 correct of 12 rounds is >= 70% accuracy
    expect(calculateRunXp(run, 0, 0, false)).toBe(10 * 15 + 50 + 100); // + 20s survival bonus
    const lost = { ...run, correctHits: 5, totalRounds: 12 };
    expect(calculateRunXp(lost, 0, 0, false)).toBe(5 * 5 + 100);
  });

  test("a flawless run adds the perfect bonus", () => {
    const flawless = { ...run, correctHits: 12, totalRounds: 12 };
    expect(calculateRunXp(flawless, 0, 0, false)).toBe(12 * 15 + 50 + 100 + 100);
  });

  test("TIME mode gets no survival bonus, since its clock is fixed", () => {
    expect(calculateRunXp({ ...run, mode: "TIME" }, 0, 0, false)).toBe(10 * 15 + 50);
  });

  test("daily streak and win streak bonuses are capped", () => {
    const base = 10 * 15 + 50 + 100;
    expect(calculateRunXp(run, 100, 0, false)).toBe(base + 200); // daily capped at 200
    expect(calculateRunXp(run, 0, 100, false)).toBe(base + 150); // win streak capped at 150
  });

  test("a new high score multiplies the total by 1.5", () => {
    const base = 10 * 15 + 50 + 100;
    expect(calculateRunXp(run, 0, 0, true)).toBe(Math.trunc(base * 1.5));
  });
});

describe("pointsDeltaFor", () => {
  test("a win is +100, a loss is -25", () => {
    expect(pointsDeltaFor(true)).toBe(100);
    expect(pointsDeltaFor(false)).toBe(-25);
  });
});

describe("achievementXpFor", () => {
  test("sums the rewards of known ids", () => {
    expect(achievementXpFor(["first_blood", "ten_games"])).toBe(750);
  });

  test("ignores ids that aren't real achievements", () => {
    expect(achievementXpFor(["first_blood", "invented_by_a_cheater"])).toBe(250);
  });

  test("ignores duplicates of the same id inside one submission", () => {
    expect(achievementXpFor(["first_blood", "first_blood"])).toBe(250);
  });
});

describe("maxPlausibleScore", () => {
  // Local scoring is 100 per correct answer plus min(streak * 10, 100), so an
  // unbroken streak is the ceiling for a given number of correct answers.
  test("is the all-correct-in-a-row score", () => {
    expect(maxPlausibleScore(1)).toBe(110);
    expect(maxPlausibleScore(2)).toBe(110 + 120);
    expect(maxPlausibleScore(0)).toBe(0);
  });

  test("the per-answer streak bonus stops growing at 100", () => {
    expect(maxPlausibleScore(11) - maxPlausibleScore(10)).toBe(200);
    expect(maxPlausibleScore(20) - maxPlausibleScore(19)).toBe(200);
  });
});

describe("validateSoloRun", () => {
  const valid = { mode: "ENDLESS" as const, correctHits: 10, totalRounds: 12, survivalMs: 25_000, finalScore: 1500 };

  test("accepts a plausible run", () => {
    expect(validateSoloRun(valid)).toBeNull();
  });

  test("rejects more correct answers than rounds played", () => {
    expect(validateSoloRun({ ...valid, correctHits: 13 })).toBe("correctHits exceeds totalRounds");
  });

  test("rejects a score no sequence of that many correct answers could produce", () => {
    expect(validateSoloRun({ ...valid, finalScore: maxPlausibleScore(10) + 1 })).toBe("finalScore exceeds what correctHits allows");
  });

  test("accepts exactly the maximum score for its correct answers", () => {
    expect(validateSoloRun({ ...valid, finalScore: maxPlausibleScore(10) })).toBeNull();
  });

  test("rejects a run answered faster than a human can react", () => {
    expect(validateSoloRun({ ...valid, survivalMs: 100 })).toBe("survivalMs too short for totalRounds");
  });

  test("rejects a run longer than its mode allows", () => {
    // ENDLESS/LIVES cap: every round has at most INITIAL_TIME_LIMIT_MS (3s) to answer.
    expect(validateSoloRun({ ...valid, survivalMs: 12 * 3000 + 60_000 })).toBe("survivalMs too long for totalRounds");
    // TIME mode is a fixed 60s clock.
    expect(validateSoloRun({ ...valid, mode: "TIME", survivalMs: 120_000 })).toBe("survivalMs too long for mode");
  });

  test("rejects negative or absurd counts", () => {
    expect(validateSoloRun({ ...valid, correctHits: -1 })).not.toBeNull();
    expect(validateSoloRun({ ...valid, totalRounds: 100_000 })).not.toBeNull();
    expect(validateSoloRun({ ...valid, finalScore: -5 })).not.toBeNull();
  });

  test("rejects a mode that doesn't exist", () => {
    expect(validateSoloRun({ ...valid, mode: "GODMODE" as never })).toBe("unknown mode");
  });
});
