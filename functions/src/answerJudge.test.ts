import { judgeAnswer, parseAnsweredRound } from "./answerJudge";
import { RoomDoc } from "./types";

const NOW = 1_000_000;
const stimulus = { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] } as RoomDoc["stimulus"];

function turnRoom(overrides: Partial<RoomDoc> = {}): RoomDoc {
  return {
    code: "ABCDE",
    status: "playing",
    mode: "mistake",
    hostUid: "a",
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
    },
    turnOrder: ["a", "b"],
    turnIndex: 0,
    round: 3,
    stimulus,
    deadlineAtMs: NOW + 1000,
    winnerUid: null,
    createdAtMs: 0,
    startsAtMs: null,
    ...overrides,
  } as RoomDoc;
}

function soloRoom(me: Record<string, unknown> = {}): RoomDoc {
  return turnRoom({
    mode: "solo_survival",
    players: {
      a: {
        uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0,
        soloRound: 4, soloStimulus: stimulus, soloDeadlineAtMs: NOW + 1000,
        ...me,
      },
    } as RoomDoc["players"],
  });
}

describe("parseAnsweredRound", () => {
  test("absent means an older client", () => {
    expect(parseAnsweredRound(undefined)).toBeNull();
    expect(parseAnsweredRound(null)).toBeNull();
  });

  test("accepts non-negative integers only", () => {
    expect(parseAnsweredRound(0)).toBe(0);
    expect(parseAnsweredRound(7)).toBe(7);
    for (const bad of [-1, 1.5, "3", NaN, {}]) expect(parseAnsweredRound(bad)).toBe("invalid");
  });
});

describe("judgeAnswer, turn-based modes", () => {
  test("scores the current stimulus for the turn holder", () => {
    expect(judgeAnswer(turnRoom(), "a", "BLUE", 3, NOW)).toEqual({ ok: true, reason: "correct", round: 3 });
    expect(judgeAnswer(turnRoom(), "a", "RED", null, NOW)).toEqual({ ok: true, reason: "wrong", round: 3 });
  });

  test("malformed colors are just wrong answers", () => {
    expect(judgeAnswer(turnRoom(), "a", "", null, NOW)).toMatchObject({ ok: true, reason: "wrong" });
  });

  test("rejects out of turn, no stimulus, stale round and a missed deadline", () => {
    const code = (v: ReturnType<typeof judgeAnswer>) => (v.ok ? "ok" : v.error.code);
    expect(code(judgeAnswer(turnRoom(), "b", "BLUE", null, NOW))).toBe("permission-denied");
    expect(code(judgeAnswer(turnRoom({ stimulus: null }), "a", "BLUE", null, NOW))).toBe("failed-precondition");
    expect(code(judgeAnswer(turnRoom(), "a", "BLUE", 2, NOW))).toBe("deadline-exceeded");
    expect(code(judgeAnswer(turnRoom(), "a", "BLUE", null, NOW + 1001))).toBe("deadline-exceeded");
  });

  test("hot_potato ignores the per-stimulus deadline but not a stale round", () => {
    const late = judgeAnswer(turnRoom({ mode: "hot_potato" }), "a", "BLUE", 3, NOW + 60_000);
    expect(late).toMatchObject({ ok: true, reason: "correct" });
    const stale = judgeAnswer(turnRoom({ mode: "hot_potato" }), "a", "BLUE", 2, NOW);
    expect(stale.ok ? null : stale.error.details).toEqual({ reason: "STALE_ROUND" });
  });
});

describe("judgeAnswer, solo_survival", () => {
  test("judges against the player's own stimulus and round", () => {
    expect(judgeAnswer(soloRoom(), "a", "BLUE", 4, NOW)).toEqual({ ok: true, reason: "correct", round: 4 });
  });

  test("rejects busted players, stale rounds and a missed deadline", () => {
    const code = (v: ReturnType<typeof judgeAnswer>) => (v.ok ? "ok" : v.error.code);
    expect(code(judgeAnswer(soloRoom({ alive: false }), "a", "BLUE", null, NOW))).toBe("failed-precondition");
    expect(code(judgeAnswer(soloRoom({ soloStimulus: null }), "a", "BLUE", null, NOW))).toBe("failed-precondition");
    expect(code(judgeAnswer(soloRoom(), "a", "BLUE", 3, NOW))).toBe("deadline-exceeded");
    expect(code(judgeAnswer(soloRoom(), "a", "BLUE", null, NOW + 1001))).toBe("deadline-exceeded");
  });
});
