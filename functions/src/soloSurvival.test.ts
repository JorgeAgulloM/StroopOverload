import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData } from "firebase-admin/firestore";
import {
  beginSoloSurvivalMatch,
  finishSoloSurvivalSession,
  resolveSoloAnswer,
  soloLevelForRound,
  soloTimeLimitMs,
} from "./soloSurvival";
import { scheduleSoloPlayerTimeoutCheck } from "./taskQueue";

// beginSoloSurvivalMatch/resolveSoloAnswer reach Cloud Tasks via
// scheduleSoloPlayerTimeoutCheck. There's no Cloud Tasks emulator here (only
// Firestore), and the real call fails outright without credentials -- mocked
// the same way resolveHotPotato.test.ts does it.
jest.mock("./taskQueue", () => ({
  scheduleSoloPlayerTimeoutCheck: jest.fn().mockResolvedValue(undefined),
}));

const PROJECT_ID = "stroopoverload-test";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
    },
  });

  if (getApps().length === 0) {
    initializeApp({ projectId: PROJECT_ID });
  }
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
  jest.clearAllMocks();
});

async function seedRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  const base = {
    code: "ABCDE",
    status: "starting",
    mode: "solo_survival",
    hostUid: "a",
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
    },
    turnOrder: ["a", "b", "c"],
    turnIndex: 0,
    round: 0,
    stimulus: null,
    deadlineAtMs: null,
    winnerUid: null,
    createdAtMs: Date.now(),
    startsAtMs: null,
    ...overrides,
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room-1").set(base);
  });
}

async function getRoom(): Promise<DocumentData> {
  let data: DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc("room-1").get();
    data = snap.data();
  });
  return data!;
}

describe("soloLevelForRound / soloTimeLimitMs", () => {
  test("levels up every 5 rounds, starting at level 1", () => {
    expect(soloLevelForRound(0)).toBe(1);
    expect(soloLevelForRound(4)).toBe(1);
    expect(soloLevelForRound(5)).toBe(2);
    expect(soloLevelForRound(9)).toBe(2);
    expect(soloLevelForRound(10)).toBe(3);
  });

  test("time limit decays with level and floors at the minimum", () => {
    expect(soloTimeLimitMs(0)).toBe(3000); // level 1: INITIAL_TIME_LIMIT_MS
    expect(soloTimeLimitMs(5)).toBe(2850); // level 2: 3000 - 150
    expect(soloTimeLimitMs(100000)).toBe(800); // deep into the run: floored at MINIMUM_TIME_LIMIT_MS
  });
});

describe("beginSoloSurvivalMatch", () => {
  test("seeds every player with their own round-0 stimulus, deadline and zeroed score", async () => {
    const before = Date.now();
    await seedRoom();
    const deadlines = await beginSoloSurvivalMatch("room-1");

    expect(deadlines).toHaveLength(3);
    expect(deadlines.map((d) => d.uid).sort()).toEqual(["a", "b", "c"]);
    deadlines.forEach((d) => expect(d.round).toBe(0));

    const after = await getRoom();
    expect(after.status).toBe("playing");
    expect(after.stimulus).toBeNull(); // room-level stimulus stays unused for this mode
    expect(after.deadlineAtMs).toBeGreaterThanOrEqual(before + 60_000); // shared session clock armed
    for (const uid of ["a", "b", "c"]) {
      const p = after.players[uid];
      expect(p.alive).toBe(true);
      expect(p.soloScore).toBe(0);
      expect(p.soloRound).toBe(0);
      expect(p.soloStreak).toBe(0);
      expect(p.soloStimulus).toBeTruthy();
      expect(p.soloDeadlineAtMs).toBeGreaterThanOrEqual(before + 3000);
    }
  });

  test("no-ops on a room that isn't in starting status (stale/duplicate task run)", async () => {
    await seedRoom({ status: "playing" });
    const deadlines = await beginSoloSurvivalMatch("room-1");

    expect(deadlines).toEqual([]);
  });
});

describe("resolveSoloAnswer", () => {
  test("a correct answer advances only the acting player's own round/streak/score", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");

    const applied = await resolveSoloAnswer("room-1", "a", "correct", 0);
    expect(applied).toBe(true);

    const after = await getRoom();
    expect(after.players.a.soloRound).toBe(1);
    expect(after.players.a.soloStreak).toBe(1);
    expect(after.players.a.soloScore).toBe(110); // 100 + min(1*10, 100)
    expect(after.players.a.alive).toBe(true);
    // b and c are untouched -- no shared turn to pass in this mode.
    expect(after.players.b.soloRound).toBe(0);
    expect(after.players.c.soloRound).toBe(0);

    expect(scheduleSoloPlayerTimeoutCheck).toHaveBeenCalledWith("room-1", "a", 1, expect.any(Number));
  });

  test("streak bonus is capped at 100", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    for (let round = 0; round < 12; round++) {
      await resolveSoloAnswer("room-1", "a", "correct", round);
    }

    const after = await getRoom();
    expect(after.players.a.soloRound).toBe(12);
    expect(after.players.a.soloStreak).toBe(12);
    // hits 1-10 each add 100 + streak*10 (10..100); hits 11-12 are capped at 100 + 100
    const expectedScore = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].reduce(
      (sum, streak) => sum + 100 + Math.min(streak * 10, 100),
      0
    );
    expect(after.players.a.soloScore).toBe(expectedScore);
  });

  test("a wrong answer busts only the acting player while others remain alive", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");

    const applied = await resolveSoloAnswer("room-1", "a", "wrong", 0);
    expect(applied).toBe(true);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(false);
    expect(after.players.a.soloStimulus).toBeNull();
    expect(after.players.a.soloDeadlineAtMs).toBeNull();
    expect(after.status).toBe("playing"); // b and c are still in it
    expect(after.players.b.alive).toBe(true);
    expect(after.players.c.alive).toBe(true);
    expect(scheduleSoloPlayerTimeoutCheck).not.toHaveBeenCalled(); // no next round for a busted player
  });

  test("a timeout busts the acting player the same way a wrong answer does", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    await resolveSoloAnswer("room-1", "a", "timeout", 0);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(false);
  });

  test("busting every player finishes the match with the highest scorer as winner", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    // Give "c" a real lead before everyone else busts.
    await resolveSoloAnswer("room-1", "c", "correct", 0);
    await resolveSoloAnswer("room-1", "a", "wrong", 0);
    await resolveSoloAnswer("room-1", "b", "wrong", 0);
    // The match already finished once "b" busted (leaving only "c" alive --
    // see the sole-survivor test below), so this 4th call lands on an
    // already-finished room and is a no-op. Kept to confirm that's safe.
    const appliedAfterFinish = await resolveSoloAnswer("room-1", "c", "wrong", 1);

    expect(appliedAfterFinish).toBe(false);
    const after = await getRoom();
    expect(after.status).toBe("finished");
    expect(after.winnerUid).toBe("c"); // only player with a nonzero score
    expect(after.deadlineAtMs).toBeNull();
  });

  test("busting down to exactly one survivor finishes the match immediately -- no playing on alone", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    await resolveSoloAnswer("room-1", "c", "correct", 0); // c takes an early lead
    await resolveSoloAnswer("room-1", "a", "wrong", 0); // a busts, b and c still alive -> continues

    const midway = await getRoom();
    expect(midway.status).toBe("playing");

    await resolveSoloAnswer("room-1", "b", "wrong", 0); // b busts -> only c remains alive

    const after = await getRoom();
    expect(after.status).toBe("finished");
    expect(after.winnerUid).toBe("c");
    expect(after.players.c.alive).toBe(true); // the sole survivor, not busted themselves
    expect(after.deadlineAtMs).toBeNull();
  });

  test("stale round numbers are ignored", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    const applied = await resolveSoloAnswer("room-1", "a", "correct", 5); // player is actually at round 0

    expect(applied).toBe(false);
    const after = await getRoom();
    expect(after.players.a.soloRound).toBe(0); // untouched
  });

  test("a call for an already-busted player is rejected", async () => {
    await seedRoom();
    await beginSoloSurvivalMatch("room-1");
    await resolveSoloAnswer("room-1", "a", "wrong", 0);

    const applied = await resolveSoloAnswer("room-1", "a", "correct", 0);
    expect(applied).toBe(false);
  });
});

describe("finishSoloSurvivalSession", () => {
  test("finishes the room and picks the highest scorer as winner", async () => {
    await seedRoom({
      status: "playing",
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, soloScore: 500 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0, soloScore: 900 },
        c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0, soloScore: 300 },
      },
    });
    await finishSoloSurvivalSession("room-1");

    const after = await getRoom();
    expect(after.status).toBe("finished");
    expect(after.winnerUid).toBe("b"); // highest score wins even though busted earlier
    expect(after.deadlineAtMs).toBeNull();
  });

  test("ties break toward whoever joined first (lowest order)", async () => {
    await seedRoom({
      status: "playing",
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, soloScore: 400 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0, soloScore: 400 },
      },
    });
    await finishSoloSurvivalSession("room-1");

    const after = await getRoom();
    expect(after.winnerUid).toBe("a");
  });

  test("no-ops on a room that already finished (stale/duplicate task run)", async () => {
    await seedRoom({ status: "finished", winnerUid: "c" });
    await finishSoloSurvivalSession("room-1");

    const after = await getRoom();
    expect(after.winnerUid).toBe("c"); // untouched
  });
});
