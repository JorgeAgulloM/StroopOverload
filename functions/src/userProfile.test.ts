import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData } from "firebase-admin/firestore";
import { applyMatchAwards, applySoloRun, nextDailyStreak, RECENT_RUN_IDS_KEPT } from "./userProfile";
import { SoloRunReport } from "./profileScoring";

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
});

const WON_RUN: SoloRunReport = {
  mode: "ENDLESS",
  correctHits: 10,
  totalRounds: 12,
  survivalMs: 25_000,
  finalScore: 1500,
};
const LOST_RUN: SoloRunReport = { ...WON_RUN, correctHits: 2, totalRounds: 12, finalScore: 230 };

async function seedUser(uid: string, fields: Record<string, unknown>): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(uid).set(fields);
  });
}

async function getUser(uid: string): Promise<DocumentData | undefined> {
  let data: DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    data = (await context.firestore().collection("users").doc(uid).get()).data();
  });
  return data;
}

async function seedFinishedRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  const base = {
    code: "ABCDE",
    status: "finished",
    mode: "mistake",
    hostUid: "a",
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, placement: 1, finalScore: 600 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0, placement: 2, finalScore: 225 },
    },
    turnOrder: ["a", "b"],
    turnIndex: 0,
    round: 7,
    stimulus: null,
    deadlineAtMs: null,
    winnerUid: "a",
    createdAtMs: Date.now(),
    startsAtMs: null,
    ...overrides,
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room-1").set(base);
  });
}

async function getRoom(): Promise<DocumentData | undefined> {
  let data: DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    data = (await context.firestore().collection("rooms").doc("room-1").get()).data();
  });
  return data;
}

describe("nextDailyStreak", () => {
  const day = 24 * 60 * 60 * 1000;

  test("stays put for a second run on the same day", () => {
    expect(nextDailyStreak(3, 5 * day + 100, 5 * day + 9_000)).toBe(3);
  });

  test("increments the day after", () => {
    expect(nextDailyStreak(3, 5 * day, 6 * day)).toBe(4);
  });

  test("resets after a skipped day", () => {
    expect(nextDailyStreak(3, 5 * day, 9 * day)).toBe(1);
  });
});

describe("applySoloRun", () => {
  test("creates the scoring fields for a profile that has none yet", async () => {
    const applied = await applySoloRun("uid-1", WON_RUN, []);

    expect(applied.points).toBe(100);
    expect(applied.highScore).toBe(1500);
    expect(applied.matchesPlayed).toBe(1);
    expect(applied.matchesWon).toBe(1);

    const stored = await getUser("uid-1");
    expect(stored).toMatchObject({ points: 100, highScore: 1500, matchesWon: 1, level: applied.level });
    expect(stored!.experience).toBe(applied.xpAwarded);
  });

  test("subtracts ranking points for a lost run but never below zero", async () => {
    await seedUser("uid-1", { points: 10 });

    const applied = await applySoloRun("uid-1", LOST_RUN, []);

    expect(applied.points).toBe(0); // 10 - 25 floored at 0
    expect(applied.matchesLost).toBe(1);
    expect(applied.matchesWon).toBe(0);
  });

  test("keeps the existing high score when the run didn't beat it", async () => {
    await seedUser("uid-1", { highScore: 9000, points: 500 });

    const applied = await applySoloRun("uid-1", WON_RUN, []);

    expect(applied.highScore).toBe(9000);
  });

  test("adds to the fields already stored instead of overwriting them", async () => {
    await seedUser("uid-1", { points: 500, experience: 1000, matchesPlayed: 7, matchesWon: 4, matchesLost: 3 });

    const applied = await applySoloRun("uid-1", WON_RUN, []);

    expect(applied.points).toBe(600);
    expect(applied.experience).toBe(1000 + applied.xpAwarded);
    expect(applied.matchesPlayed).toBe(8);
    expect(applied.matchesWon).toBe(5);
  });

  test("pays an achievement's XP the first time it is claimed and never again", async () => {
    const first = await applySoloRun("uid-1", WON_RUN, ["first_blood"]);
    const second = await applySoloRun("uid-1", WON_RUN, ["first_blood"]);

    const runXpOnly = await applySoloRun("uid-2", WON_RUN, []);
    expect(first.xpAwarded).toBe(runXpOnly.xpAwarded + 250);
    // The second claim of the same achievement adds nothing beyond the run itself.
    expect(second.xpAwarded).toBeLessThan(first.xpAwarded);
    expect((await getUser("uid-1"))!.awardedAchievements).toHaveProperty("first_blood");
  });

  test("ignores achievement ids that don't exist", async () => {
    const applied = await applySoloRun("uid-1", WON_RUN, ["not_a_real_achievement"]);
    const withoutClaim = await applySoloRun("uid-2", WON_RUN, []);

    expect(applied.xpAwarded).toBe(withoutClaim.xpAwarded);
  });

  test("leaves the player's client-owned fields untouched", async () => {
    await seedUser("uid-1", { nickname: "Neo", uniqueName: "@neo-1234", unlockedPalettes: ["default"] });

    await applySoloRun("uid-1", WON_RUN, []);

    expect(await getUser("uid-1")).toMatchObject({ nickname: "Neo", uniqueName: "@neo-1234" });
  });

  test("a run submitted twice with the same runId is applied once", async () => {
    // A retry after a lost response must not pay the same run again.
    const first = await applySoloRun("uid-1", WON_RUN, [], 0, Date.now(), "run-0001-aaaa");
    const retry = await applySoloRun("uid-1", WON_RUN, [], 0, Date.now(), "run-0001-aaaa");

    expect(retry.xpAwarded).toBe(0);
    expect(retry.matchesPlayed).toBe(1);
    expect(retry.points).toBe(first.points);
    expect(await getUser("uid-1")).toMatchObject({ matchesPlayed: 1, points: first.points, experience: first.experience });
  });

  test("different runIds are both applied", async () => {
    await applySoloRun("uid-1", WON_RUN, [], 0, Date.now(), "run-0001-aaaa");
    const second = await applySoloRun("uid-1", WON_RUN, [], 0, Date.now(), "run-0002-bbbb");

    expect(second.matchesPlayed).toBe(2);
  });

  test("only the most recent runIds are remembered", async () => {
    for (let i = 0; i < RECENT_RUN_IDS_KEPT + 5; i++) {
      await applySoloRun("uid-1", WON_RUN, [], 0, Date.now(), `run-${String(i).padStart(4, "0")}-keep`);
    }

    const stored = (await getUser("uid-1"))!.recentRunIds as string[];
    expect(stored).toHaveLength(RECENT_RUN_IDS_KEPT);
    expect(stored[stored.length - 1]).toBe(`run-${String(RECENT_RUN_IDS_KEPT + 4).padStart(4, "0")}-keep`);
  });

  test("a run without a runId (older clients) is still applied every time", async () => {
    await applySoloRun("uid-1", WON_RUN, []);
    const second = await applySoloRun("uid-1", WON_RUN, []);

    expect(second.matchesPlayed).toBe(2);
  });

  test("recomputes the level from total experience", async () => {
    await seedUser("uid-1", { experience: 100_000, level: 1 });

    const applied = await applySoloRun("uid-1", WON_RUN, []);

    expect(applied.level).toBeGreaterThan(1);
    expect((await getUser("uid-1"))!.level).toBe(applied.level);
  });
});

describe("applyMatchAwards", () => {
  test("pays every ranked player their server-computed finalScore", async () => {
    await seedFinishedRoom();

    const awarded = await applyMatchAwards("room-1");

    expect(awarded).toBe(2);
    expect(await getUser("a")).toMatchObject({ points: 600, matchesWon: 1, matchesLost: 0, matchesPlayed: 1 });
    expect(await getUser("b")).toMatchObject({ points: 225, matchesWon: 0, matchesLost: 1, matchesPlayed: 1 });
  });

  test("adds to whatever the players already had", async () => {
    await seedUser("a", { points: 1000, experience: 500, matchesPlayed: 3 });
    await seedFinishedRoom();

    await applyMatchAwards("room-1");

    expect(await getUser("a")).toMatchObject({ points: 1600, experience: 1100, matchesPlayed: 4 });
  });

  test("is idempotent: a re-delivered trigger pays nothing extra", async () => {
    await seedFinishedRoom();

    await applyMatchAwards("room-1");
    const second = await applyMatchAwards("room-1");

    expect(second).toBe(0);
    expect(await getUser("a")).toMatchObject({ points: 600 });
    expect((await getRoom())!.awardsAppliedAtMs).toEqual(expect.any(Number));
  });

  test("ignores a room that hasn't finished", async () => {
    await seedFinishedRoom({ status: "playing" });

    expect(await applyMatchAwards("room-1")).toBe(0);
    expect(await getUser("a")).toBeUndefined();
  });

  test("skips players the match never scored", async () => {
    await seedFinishedRoom({
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, placement: 1, finalScore: 600 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0 },
      },
    });

    expect(await applyMatchAwards("room-1")).toBe(1);
    expect(await getUser("b")).toBeUndefined();
  });

  test("a ranked player who scored 0 still gets the match counted", async () => {
    // The loser eliminated before scoring used to be skipped: no matchesPlayed/matchesLost.
    await seedFinishedRoom({
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, placement: 1, finalScore: 600 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0, placement: 2, finalScore: 0 },
      },
    });

    expect(await applyMatchAwards("room-1")).toBe(2);
    expect(await getUser("b")).toMatchObject({ points: 0, matchesPlayed: 1, matchesLost: 1, matchesWon: 0 });
  });

  test("a winner who scored 0 still gets the win", async () => {
    await seedFinishedRoom({
      winnerUid: "a",
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0, placement: 1, finalScore: 0 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0, placement: 2, finalScore: 0 },
      },
    });

    await applyMatchAwards("room-1");

    expect(await getUser("a")).toMatchObject({ matchesPlayed: 1, matchesWon: 1 });
  });

  test("no-ops on a room that no longer exists", async () => {
    expect(await applyMatchAwards("room-1")).toBe(0);
  });
});
