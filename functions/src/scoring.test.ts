import {
  applyCorrectAnswer,
  finalScoreForPlacement,
  finishedMatchUpdate,
  placementMultiplier,
  rankMistakeOrHotPotatoPlayers,
  rankSoloSurvivalPlayers,
} from "./scoring";
import { RoomPlayerDoc } from "./types";

function player(overrides: Partial<RoomPlayerDoc> & { uid: string }): RoomPlayerDoc {
  return {
    displayName: overrides.uid,
    avatarIndex: 0,
    alive: true,
    order: 0,
    joinedAtMs: 0,
    ...overrides,
  };
}

describe("applyCorrectAnswer", () => {
  test("first hit: 100 base + 10 streak bonus", () => {
    expect(applyCorrectAnswer(0, 0)).toEqual({ score: 110, streak: 1 });
  });

  test("streak bonus climbs by 10 per hit up to the cap", () => {
    let state = { score: 0, streak: 0 };
    for (let i = 0; i < 12; i++) state = applyCorrectAnswer(state.score, state.streak);
    // hits 1-10 add 100+streak*10 (10..100); hits 11-12 capped at 100+100
    const expected = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].reduce(
      (sum, streak) => sum + 100 + Math.min(streak * 10, 100),
      0
    );
    expect(state.score).toBe(expected);
    expect(state.streak).toBe(12);
  });
});

describe("placementMultiplier", () => {
  test("1st/2nd/3rd/4th place multipliers", () => {
    expect(placementMultiplier(1)).toBe(2);
    expect(placementMultiplier(2)).toBe(1.5);
    expect(placementMultiplier(3)).toBe(1);
    expect(placementMultiplier(4)).toBe(0.5);
  });

  test("out-of-range placement falls back to the worst multiplier", () => {
    expect(placementMultiplier(5)).toBe(0.5);
  });
});

describe("finalScoreForPlacement", () => {
  test("halves the raw score, then applies the placement multiplier", () => {
    expect(finalScoreForPlacement(1000, 1)).toBe(1000); // 1000/2 * 2
    expect(finalScoreForPlacement(1000, 2)).toBe(750); // 1000/2 * 1.5
    expect(finalScoreForPlacement(1000, 3)).toBe(500); // 1000/2 * 1
    expect(finalScoreForPlacement(1000, 4)).toBe(250); // 1000/2 * 0.5
  });

  test("rounds to the nearest integer", () => {
    expect(finalScoreForPlacement(111, 2)).toBe(83); // 55.5 * 1.5 = 83.25 -> 83
  });
});

describe("rankMistakeOrHotPotatoPlayers", () => {
  test("winner always takes placement 1 regardless of matchScore", () => {
    const players = {
      a: player({ uid: "a", order: 0, matchScore: 50, eliminatedAtMs: 5000 }),
      b: player({ uid: "b", order: 1, matchScore: 9999, alive: false, eliminatedAtMs: 1000 }),
    };
    const ranked = rankMistakeOrHotPotatoPlayers(players, "a");
    expect(ranked.find((r) => r.uid === "a")?.placement).toBe(1);
    expect(ranked.find((r) => r.uid === "b")?.placement).toBe(2);
  });

  test("non-winners are ranked by eliminatedAtMs descending -- survived longest places better", () => {
    const players = {
      a: player({ uid: "a", order: 0, alive: false, eliminatedAtMs: 1000 }), // eliminated first
      b: player({ uid: "b", order: 1, alive: false, eliminatedAtMs: 3000 }), // eliminated last (of the non-winners)
      c: player({ uid: "c", order: 2 }), // winner
    };
    const ranked = rankMistakeOrHotPotatoPlayers(players, "c");
    expect(ranked.find((r) => r.uid === "c")?.placement).toBe(1);
    expect(ranked.find((r) => r.uid === "b")?.placement).toBe(2); // eliminated later
    expect(ranked.find((r) => r.uid === "a")?.placement).toBe(3); // eliminated first
  });

  test("simultaneous eliminations tiebreak by join order (`order` ascending)", () => {
    const players = {
      a: player({ uid: "a", order: 2, alive: false, eliminatedAtMs: 2000 }),
      b: player({ uid: "b", order: 0, alive: false, eliminatedAtMs: 2000 }),
      c: player({ uid: "c", order: 1 }), // winner
    };
    const ranked = rankMistakeOrHotPotatoPlayers(players, "c");
    expect(ranked.find((r) => r.uid === "b")?.placement).toBe(2); // lower order wins the tie
    expect(ranked.find((r) => r.uid === "a")?.placement).toBe(3);
  });

  test("2-player match still uses the 1st/2nd multipliers, not the last-place one", () => {
    const players = {
      a: player({ uid: "a", order: 0, matchScore: 1000 }),
      b: player({ uid: "b", order: 1, matchScore: 1000, alive: false, eliminatedAtMs: 1000 }),
    };
    const ranked = rankMistakeOrHotPotatoPlayers(players, "a");
    expect(ranked.find((r) => r.uid === "a")?.finalScore).toBe(1000); // 1000/2 * 2
    expect(ranked.find((r) => r.uid === "b")?.finalScore).toBe(750); // 1000/2 * 1.5, NOT *0.5
  });
});

describe("finishedMatchUpdate", () => {
  test("finishes the room with the survivor, clears the board and ranks everyone", () => {
    const players = {
      a: player({ uid: "a", order: 0, matchScore: 300 }),
      b: player({ uid: "b", order: 1, matchScore: 500, alive: false, eliminatedAtMs: 2000 }),
      c: player({ uid: "c", order: 2, matchScore: 100, alive: false, eliminatedAtMs: 1000 }),
    };

    const update = finishedMatchUpdate(players, "a");

    expect(update).toMatchObject({ status: "finished", winnerUid: "a", stimulus: null, deadlineAtMs: null });
    const ranked = rankMistakeOrHotPotatoPlayers(players, "a");
    for (const r of ranked) {
      expect(update.players[r.uid]).toMatchObject({ placement: r.placement, finalScore: r.finalScore });
    }
    expect(update.players.b.matchScore).toBe(500); // everything else kept
  });

  test("does not mutate the players it was given", () => {
    const players = { a: player({ uid: "a" }), b: player({ uid: "b", alive: false, eliminatedAtMs: 1 }) };
    finishedMatchUpdate(players, "a");
    expect(players.a.placement).toBeUndefined();
  });
});

describe("rankSoloSurvivalPlayers", () => {
  test("ranks by soloScore descending", () => {
    const players = {
      a: player({ uid: "a", order: 0, soloScore: 300 }),
      b: player({ uid: "b", order: 1, soloScore: 900 }),
      c: player({ uid: "c", order: 2, soloScore: 500 }),
    };
    const ranked = rankSoloSurvivalPlayers(players);
    expect(ranked.find((r) => r.uid === "b")?.placement).toBe(1);
    expect(ranked.find((r) => r.uid === "c")?.placement).toBe(2);
    expect(ranked.find((r) => r.uid === "a")?.placement).toBe(3);
  });

  test("ties break toward whoever joined first (lowest order)", () => {
    const players = {
      a: player({ uid: "a", order: 1, soloScore: 400 }),
      b: player({ uid: "b", order: 0, soloScore: 400 }),
    };
    const ranked = rankSoloSurvivalPlayers(players);
    expect(ranked.find((r) => r.uid === "b")?.placement).toBe(1);
    expect(ranked.find((r) => r.uid === "a")?.placement).toBe(2);
  });
});
