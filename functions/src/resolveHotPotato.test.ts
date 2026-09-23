import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import * as admin from "firebase-admin";
import { armBomb, BOMB_MAX_DELAY_MS, BOMB_MIN_DELAY_MS, explodeBomb, randomBombDelayMs, resolveHotPotatoTurn } from "./resolveHotPotato";
import { scheduleBombExplosion, scheduleTimeoutCheck } from "./taskQueue";

// Both resolveHotPotatoTurn (via scheduleTimeoutCheck) and armBomb (via
// scheduleBombExplosion) reach Cloud Tasks. There's no Cloud Tasks emulator
// here (only Firestore, per package.json's `firebase emulators:exec --only
// firestore`), and the real call fails outright without credentials -- mocked
// the same way resolveRound.test.ts does it, so these tests exercise the
// Firestore transaction logic in isolation.
jest.mock("./taskQueue", () => ({
  scheduleTimeoutCheck: jest.fn().mockResolvedValue(undefined),
  scheduleBombExplosion: jest.fn().mockResolvedValue(undefined),
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

  if (admin.apps.length === 0) {
    admin.initializeApp({ projectId: PROJECT_ID });
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
    status: "playing",
    mode: "hot_potato",
    hostUid: "a",
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
    },
    turnOrder: ["a", "b", "c"],
    turnIndex: 0,
    round: 1,
    stimulus: { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
    deadlineAtMs: Date.now() + 3000,
    winnerUid: null,
    createdAtMs: Date.now(),
    startsAtMs: null,
    ...overrides,
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room-1").set(base);
  });
}

async function getRoom(): Promise<admin.firestore.DocumentData> {
  let data: admin.firestore.DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc("room-1").get();
    data = snap.data();
  });
  return data!;
}

async function getPrivateBombDoc(): Promise<admin.firestore.DocumentData | undefined> {
  let data: admin.firestore.DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc("room-1").collection("private").doc("bomb").get();
    data = snap.data();
  });
  return data;
}

describe("randomBombDelayMs", () => {
  test("always falls within [15s, 30s]", () => {
    for (let i = 0; i < 200; i++) {
      const delay = randomBombDelayMs();
      expect(delay).toBeGreaterThanOrEqual(BOMB_MIN_DELAY_MS);
      expect(delay).toBeLessThanOrEqual(BOMB_MAX_DELAY_MS);
    }
  });
});

describe("armBomb", () => {
  test("writes a bombAtMs 15-30s out to the private subdocument and schedules the explosion task", async () => {
    const before = Date.now();
    await armBomb("room-1");

    const bombDoc = await getPrivateBombDoc();
    expect(bombDoc).toBeDefined();
    const delay = (bombDoc!.bombAtMs as number) - before;
    expect(delay).toBeGreaterThanOrEqual(BOMB_MIN_DELAY_MS);
    expect(delay).toBeLessThanOrEqual(BOMB_MAX_DELAY_MS + 50); // small slack for test execution time

    // The task carries the exact bombAtMs it was armed for, so explodeBomb can
    // tell a live bomb apart from a duplicate/stale delivery.
    expect(scheduleBombExplosion).toHaveBeenCalledWith("room-1", expect.any(Number), bombDoc!.bombAtMs);
  });
});

describe("resolveHotPotatoTurn", () => {
  test("a correct answer passes the turn forward without eliminating anyone", async () => {
    await seedRoom();
    const applied = await resolveHotPotatoTurn("room-1", "a", "correct", 1);

    expect(applied).toBe(true);
    const after = await getRoom();
    expect(after.turnIndex).toBe(1); // moved to "b"
    expect(after.round).toBe(2);
    expect(after.players.a.alive).toBe(true);
    // No timeout task is ever scheduled for this mode -- the holder can take
    // as long as they want between stimuli; only armBomb's task applies
    // real pressure.
    expect(scheduleTimeoutCheck).not.toHaveBeenCalled();
  });

  test("a wrong answer re-prompts the same holder: no elimination, no turn change, no timeout scheduled", async () => {
    await seedRoom();
    const applied = await resolveHotPotatoTurn("room-1", "a", "wrong", 1);

    expect(applied).toBe(true);
    const after = await getRoom();
    expect(after.turnIndex).toBe(0); // still "a"
    expect(after.round).toBe(2); // fresh prompt, but same holder
    expect(after.players.a.alive).toBe(true);
    expect(scheduleTimeoutCheck).not.toHaveBeenCalled();
  });

  test("a stale timeout call (defense in depth, no longer scheduled in practice) still re-prompts safely", async () => {
    await seedRoom();
    await resolveHotPotatoTurn("room-1", "a", "timeout", 1);

    const after = await getRoom();
    expect(after.turnIndex).toBe(0);
    expect(after.players.a.alive).toBe(true);
    expect(scheduleTimeoutCheck).not.toHaveBeenCalled();
  });

  test("rejects a caller who isn't the current turn-holder", async () => {
    await seedRoom();
    const applied = await resolveHotPotatoTurn("room-1", "b", "correct", 1); // "a" holds the turn

    expect(applied).toBe(false);
    const after = await getRoom();
    expect(after.turnIndex).toBe(0); // untouched
    expect(after.round).toBe(1);
  });

  test("stale round numbers are ignored", async () => {
    await seedRoom({ round: 5 });
    const applied = await resolveHotPotatoTurn("room-1", "a", "correct", 1);

    expect(applied).toBe(false);
    const after = await getRoom();
    expect(after.round).toBe(5); // untouched
  });
});

async function seedBomb(bombAtMs: number): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room-1").collection("private").doc("bomb").set({ bombAtMs });
  });
}

const ARMED_BOMB_AT_MS = 1_000;

describe("explodeBomb", () => {
  test("eliminates the current turn-holder and arms a fresh bomb when players remain", async () => {
    await seedRoom(); // "a" holds the turn
    await seedBomb(ARMED_BOMB_AT_MS);
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(false);
    expect(after.status).toBe("playing"); // b and c remain -- match continues
    expect(after.turnIndex).toBe(1); // advanced past the eliminated holder

    expect(scheduleBombExplosion).toHaveBeenCalledWith("room-1", expect.any(Number), expect.any(Number));
    const bombDoc = await getPrivateBombDoc();
    expect(bombDoc).toBeDefined(); // a new bomb was armed for the remaining players
    expect(bombDoc!.bombAtMs).not.toBe(ARMED_BOMB_AT_MS);
  });

  test("finishes the match with the sole survivor once only one player remains", async () => {
    await seedRoom({
      players: {
        a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        b: { uid: "b", displayName: "B", avatarIndex: 0, alive: false, order: 1, joinedAtMs: 0 },
        c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
      },
      turnIndex: 0, // "a" holds the turn, "b" already eliminated
    });
    await seedBomb(ARMED_BOMB_AT_MS);
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.status).toBe("finished");
    expect(after.winnerUid).toBe("c");
    expect(after.stimulus).toBeNull();
    expect(after.deadlineAtMs).toBeNull();
    expect(scheduleBombExplosion).not.toHaveBeenCalled(); // no next bomb once the match is over
    expect(await getPrivateBombDoc()).toBeUndefined(); // spent bomb is cleared
  });

  test("a duplicate delivery of the same bomb task eliminates only one player", async () => {
    await seedRoom(); // "a" holds the turn, b and c alive
    await seedBomb(ARMED_BOMB_AT_MS);
    (scheduleBombExplosion as jest.Mock).mockRejectedValueOnce(new Error("queue down")); // next bomb fails to schedule

    await explodeBomb("room-1", ARMED_BOMB_AT_MS);
    await explodeBomb("room-1", ARMED_BOMB_AT_MS); // Cloud Tasks at-least-once redelivery

    const after = await getRoom();
    expect(after.players.a.alive).toBe(false);
    expect(after.players.b.alive).toBe(true); // NOT eliminated by the duplicate
    expect(after.status).toBe("playing");
  });

  test("a stale task for a bomb that was already replaced is ignored", async () => {
    await seedRoom();
    await seedBomb(ARMED_BOMB_AT_MS + 5_000); // a newer bomb is armed
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(true); // untouched
    expect((await getPrivateBombDoc())!.bombAtMs).toBe(ARMED_BOMB_AT_MS + 5_000);
  });

  test("a legacy task without bombAtMs never detonates a freshly re-armed bomb", async () => {
    await seedRoom();
    await seedBomb(Date.now() + 20_000);
    await explodeBomb("room-1");

    expect((await getRoom()).players.a.alive).toBe(true);
  });

  test("a legacy task without bombAtMs still detonates a bomb that is due", async () => {
    await seedRoom();
    await seedBomb(Date.now() - 1_000);
    await explodeBomb("room-1");

    expect((await getRoom()).players.a.alive).toBe(false);
  });

  test("no-ops when no bomb is armed at all", async () => {
    await seedRoom();
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(true);
  });

  test("does not throw when arming the next bomb fails (the watchdog re-arms it)", async () => {
    await seedRoom();
    await seedBomb(ARMED_BOMB_AT_MS);
    (scheduleBombExplosion as jest.Mock).mockRejectedValueOnce(new Error("queue down"));

    await expect(explodeBomb("room-1", ARMED_BOMB_AT_MS)).resolves.toBeUndefined();
    expect((await getRoom()).players.a.alive).toBe(false);
  });

  test("no-ops on a room that already finished (stale/duplicate task run)", async () => {
    await seedRoom({ status: "finished", winnerUid: "c" });
    await seedBomb(ARMED_BOMB_AT_MS);
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.status).toBe("finished");
    expect(after.winnerUid).toBe("c"); // untouched
    expect(scheduleBombExplosion).not.toHaveBeenCalled();
  });

  test("no-ops on a room that isn't hot_potato mode (defense in depth)", async () => {
    await seedRoom({ mode: "mistake" });
    await seedBomb(ARMED_BOMB_AT_MS);
    await explodeBomb("room-1", ARMED_BOMB_AT_MS);

    const after = await getRoom();
    expect(after.players.a.alive).toBe(true); // untouched
    expect(after.status).toBe("playing");
  });
});
