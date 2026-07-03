import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import * as admin from "firebase-admin";
import { resolveRound } from "./resolveRound";
import { scheduleTimeoutCheck } from "./taskQueue";

// resolveRound() schedules a Cloud Tasks check via scheduleTimeoutCheck()
// whenever the round advances without finishing the game. There is no Cloud
// Tasks emulator here (only Firestore), and the real call fails outright
// with "Failed to determine service account email: ... ENOTFOUND
// metadata.google.internal" -- it has no credentials to resolve a task
// queue against. Mocked so these tests exercise resolveRound's Firestore
// transaction logic in isolation, without needing live Cloud Tasks infra.
jest.mock("./taskQueue", () => ({
  scheduleTimeoutCheck: jest.fn().mockResolvedValue(undefined),
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

  // resolveRound() reaches into firebase-admin's getFirestore(), which is a
  // separate client from the rules-unit-testing environment above. Point it
  // at the same emulator (FIRESTORE_EMULATOR_HOST is set by
  // `firebase emulators:exec`) so both sides observe the same data.
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

// Seeding and read-back both go through withSecurityRulesDisabled(): the
// production rules (`allow write: if false`, `allow read: if request.auth
// != null && ...`) only permit resolveRound()'s own admin-SDK writes. Test
// setup/assertions are not the thing under test here, so they bypass rules
// the same way an emulator-side admin script would.
async function seedRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  const base = {
    code: "ABCDE",
    status: "playing",
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

test("wrong answer eliminates the acting player and advances the turn", async () => {
  await seedRoom();
  await resolveRound("room-1", "a", "wrong", 1);

  const after = await getRoom();
  expect(after.players.a.alive).toBe(false);
  expect(after.turnIndex).toBe(1); // moved to "b"
  expect(after.round).toBe(2);
  expect(after.status).toBe("playing");
});

test("eliminating the second-to-last player finishes the game with a winner", async () => {
  await seedRoom({
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: false, order: 0, joinedAtMs: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
    },
    turnIndex: 1,
  });
  await resolveRound("room-1", "b", "timeout", 1);

  const after = await getRoom();
  expect(after.status).toBe("finished");
  expect(after.winnerUid).toBe("c");
});

test("stale round numbers are ignored (already resolved by a racing trigger)", async () => {
  await seedRoom({ round: 2 });
  await resolveRound("room-1", "a", "wrong", 1); // roundExpected=1, but room is already at round 2

  const after = await getRoom();
  expect(after.players.a.alive).toBe(true); // untouched
  expect(after.round).toBe(2);
});

test("a room that no longer exists is a silent no-op", async () => {
  await expect(resolveRound("does-not-exist", "a", "wrong", 1)).resolves.not.toThrow();
});

test('a room that is not status "playing" is left untouched', async () => {
  await seedRoom({ status: "finished", winnerUid: "a" });
  await resolveRound("room-1", "b", "timeout", 1);

  const after = await getRoom();
  expect(after.status).toBe("finished");
  expect(after.winnerUid).toBe("a"); // untouched, not overwritten
});

test("a correct answer never eliminates the acting player", async () => {
  await seedRoom();
  await resolveRound("room-1", "a", "correct", 1);

  const after = await getRoom();
  expect(after.players.a.alive).toBe(true);
  expect(after.round).toBe(2);
});

test("an unknown actingUid does not corrupt the players map", async () => {
  await seedRoom();
  await resolveRound("room-1", "not-a-real-player", "wrong", 1);

  const after = await getRoom();
  // "not-a-real-player" isn't in room.turnOrder, so it can never be the
  // current turn-holder -- this falls into the bystander (no-advance) path.
  // It's also not a key in players, so the elimination guard must skip it
  // entirely -- not just leave a/b/c untouched, but also not spread a stray
  // "not-a-real-player" entry into the players map.
  expect(Object.keys(after.players).sort()).toEqual(["a", "b", "c"]);
  expect(after.players.a.alive).toBe(true);
  expect(after.players.b.alive).toBe(true);
  expect(after.players.c.alive).toBe(true);
  expect(after.turnIndex).toBe(0);
  expect(after.round).toBe(1);
});

test("a bystander's disconnect does NOT advance the active player's turn", async () => {
  const seededDeadlineAtMs = Date.now() + 3000;
  await seedRoom({ deadlineAtMs: seededDeadlineAtMs });
  await resolveRound("room-1", "c", "disconnect", 1); // c is not the turn-holder (a is, at turnIndex 0)

  const after = await getRoom();
  expect(after.players.c.alive).toBe(false);
  expect(after.turnIndex).toBe(0); // unchanged, still a's turn
  expect(after.round).toBe(1); // unchanged
  expect(after.stimulus).toEqual({
    wordLabel: "RED",
    inkColor: "BLUE",
    options: ["RED", "GREEN", "BLUE", "YELLOW"],
  });
  expect(after.deadlineAtMs).toBe(seededDeadlineAtMs); // untouched from seeded value
  expect(scheduleTimeoutCheck).not.toHaveBeenCalled();
});

test("the actual turn-holder's own disconnect DOES advance the turn", async () => {
  await seedRoom();
  await resolveRound("room-1", "a", "disconnect", 1); // a IS the turn-holder at turnIndex 0

  const after = await getRoom();
  expect(after.players.a.alive).toBe(false);
  expect(after.turnIndex).toBe(1); // moved to "b"
  expect(after.round).toBe(2);
  expect(scheduleTimeoutCheck).toHaveBeenCalledWith("room-1", 2, expect.any(Number));
});

test("scheduleTimeoutCheck is not called when the win-condition finishes the game", async () => {
  await seedRoom({
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: false, order: 0, joinedAtMs: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
    },
    turnIndex: 1,
  });
  await resolveRound("room-1", "b", "timeout", 1);

  const after = await getRoom();
  expect(after.status).toBe("finished");
  expect(scheduleTimeoutCheck).not.toHaveBeenCalled();
});
