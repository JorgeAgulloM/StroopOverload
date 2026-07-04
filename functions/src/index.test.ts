import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import * as admin from "firebase-admin";
import { CallableRequest } from "firebase-functions/v2/https";
import { Request as TaskRequest } from "firebase-functions/v2/tasks";
import { DatabaseEvent, DataSnapshot } from "firebase-functions/v2/database";
import { Change } from "firebase-functions/v2/core";
import { createRoom, joinRoom, startGame, submitAnswer, resolveTimeout, onPresenceChanged } from "./index";
import * as resolveRoundModule from "./resolveRound";

// startGame() and (via resolveRound()) submitAnswer()/resolveTimeout()/
// onPresenceChanged() all schedule a next-round timeout check through Cloud
// Tasks. There is no Cloud Tasks emulator in this test run (only Firestore,
// per package.json's `firebase emulators:exec --only firestore`), and the
// real call fails with "Failed to determine service account email: ...
// ENOTFOUND metadata.google.internal". Inside resolveRound() that failure is
// swallowed (try/catch around the schedule call), but inside startGame() it
// is NOT caught separately -- it would bubble up through startGame's outer
// try/catch and turn a should-succeed call into an "internal" HttpsError.
// Mocked here, the same way resolveRound.test.ts does it, so these tests
// exercise this file's own transaction/business logic instead of failing on
// unrelated infrastructure that isn't under test.
jest.mock("./taskQueue", () => ({
  scheduleTimeoutCheck: jest.fn().mockResolvedValue(undefined),
}));

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  // Unlike resolveRound.test.ts, this file imports index.ts, whose module-load
  // side effect (`initializeApp()`, no args) runs before this beforeAll and
  // already creates the default admin app -- resolved against whatever
  // project the emulator environment provides (functions/.firebaserc's
  // default project), not a project ID this file picks. Calling
  // admin.initializeApp({ projectId: ... }) again here would throw (duplicate
  // default app) and, worse, using a *different* projectId for
  // initializeTestEnvironment than the one index.ts's admin app resolved to
  // would silently point the two Firestore clients at two different
  // emulator-side projects, making every write invisible to the test's
  // read-back. So: read back the project ID the already-initialized default
  // app actually resolved to, and reuse it for the rules-unit-testing env.
  if (admin.apps.length === 0) {
    throw new Error("Expected index.ts's module-load initializeApp() to have already run.");
  }
  const projectId = admin.app().options.projectId;
  if (!projectId) {
    throw new Error("Default admin app has no resolved projectId; cannot align the Firestore emulator project.");
  }

  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
  jest.clearAllMocks();
});

// Builds a minimal CallableRequest-shaped object for invoking the onCall
// handler directly via CloudFunction.run(). rawRequest/acceptsStreaming are
// required by the CallableRequest type but unused by these handlers, so they
// are stubbed just enough to satisfy the type checker.
function buildRequest<T>(data: T, uid: string | undefined): CallableRequest<T> {
  return {
    data,
    auth: uid ? ({ uid, token: {} } as CallableRequest<T>["auth"]) : undefined,
    rawRequest: {} as CallableRequest<T>["rawRequest"],
    acceptsStreaming: false,
  };
}

async function seedRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  const base = {
    code: "ABCDE",
    status: "waiting",
    hostUid: "host-uid",
    players: {
      "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
    },
    turnOrder: ["host-uid"],
    turnIndex: 0,
    round: 0,
    stimulus: null,
    deadlineAtMs: null,
    winnerUid: null,
    createdAtMs: Date.now(),
    ...overrides,
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room-1").set(base);
  });
}

async function getRoom(roomId: string): Promise<admin.firestore.DocumentData> {
  let data: admin.firestore.DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc(roomId).get();
    data = snap.data();
  });
  return data!;
}

interface ResolveTimeoutTaskData {
  roomId: string;
  round: number;
}

// Builds a minimal Request<T>-shaped object (TaskContext & { data: T }) for
// invoking an onTaskDispatched handler directly via TaskQueueFunction.run().
// Only `data` is meaningful to resolveTimeout's handler; the TaskContext
// fields are required by the type but unused by the handler logic, so they
// are stubbed with plausible values.
function buildTaskRequest(data: ResolveTimeoutTaskData): TaskRequest<ResolveTimeoutTaskData> {
  return {
    data,
    queueName: "resolveTimeout",
    id: "task-1",
    retryCount: 0,
    executionCount: 0,
    scheduledTime: new Date().toISOString(),
  };
}

// Builds a minimal DatabaseEvent-shaped object for invoking an
// onValueWritten handler directly via CloudFunction.run(). DataSnapshot is a
// concrete class (not an interface) whose constructor is explicitly
// documented to support this kind of direct unit-test construction --
// `new DataSnapshot(data)` with no path/app/instance makes `.val()` return
// `data` as-is. The CloudEvent/DatabaseEvent envelope fields beyond
// `data`/`params` are unused by onPresenceChanged's handler logic, so they
// are stubbed with plausible values.
function buildPresenceEvent(
  roomId: string,
  uid: string,
  afterState: { state: string } | null
): DatabaseEvent<Change<DataSnapshot>, { roomId: string; uid: string }> {
  return {
    specversion: "1.0",
    id: "presence-event-1",
    source: "//firebasedatabase.googleapis.com/projects/_/locations/_/instances/test-instance",
    type: "google.firebase.database.ref.v1.written",
    time: new Date().toISOString(),
    data: new Change(new DataSnapshot(null), new DataSnapshot(afterState)),
    firebaseDatabaseHost: "https://test-instance.firebaseio.com",
    instance: "test-instance",
    ref: `presence/${roomId}/${uid}`,
    location: "us-central1",
    params: { roomId, uid },
  };
}

// Mirrors resolveRound.test.ts's 3-player "playing" room, since submitAnswer,
// resolveTimeout, and onPresenceChanged all operate on rooms in that shape.
async function seedPlayingRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  await seedRoom({
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
    ...overrides,
  });
}

describe("createRoom", () => {
  test("rejects an unauthenticated call", async () => {
    await expect(createRoom.run(buildRequest({ displayName: "Ace" }, undefined))).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  test("succeeds for an authenticated caller and creates a waiting room with the host as sole player", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "Ace" }, "host-uid"));

    expect(result.roomId).toBeTruthy();
    expect(result.code).toMatch(/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}$/);

    const room = await getRoom(result.roomId);
    expect(room.status).toBe("waiting");
    expect(room.hostUid).toBe("host-uid");
    expect(room.turnOrder).toEqual(["host-uid"]);
    expect(Object.keys(room.players)).toEqual(["host-uid"]);
  });

  test("falls back to 'Pilot' when displayName is whitespace-only", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "   " }, "host-uid"));
    const room = await getRoom(result.roomId);
    expect(room.players["host-uid"].displayName).toBe("Pilot");
  });
});

describe("joinRoom", () => {
  test("rejects an invalid-shaped code without touching Firestore", async () => {
    await expect(joinRoom.run(buildRequest({ code: "abc", displayName: "Joiner" }, "joiner-uid"))).rejects.toMatchObject(
      { code: "invalid-argument" }
    );
    await expect(
      joinRoom.run(buildRequest({ code: "12345678", displayName: "Joiner" }, "joiner-uid"))
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });

  test("rejects a code that doesn't match any room", async () => {
    await expect(
      joinRoom.run(buildRequest({ code: "ZZZZZ", displayName: "Joiner" }, "joiner-uid"))
    ).rejects.toMatchObject({ code: "not-found" });
  });

  test("succeeds when joining a real waiting room", async () => {
    await seedRoom();

    const result = await joinRoom.run(buildRequest({ code: "ABCDE", displayName: "Joiner" }, "joiner-uid"));
    expect(result.roomId).toBe("room-1");

    const room = await getRoom("room-1");
    expect(Object.keys(room.players).sort()).toEqual(["host-uid", "joiner-uid"]);
    expect(room.turnOrder).toEqual(["host-uid", "joiner-uid"]);
  });

  test("is idempotent -- joining twice with the same uid does not duplicate the player", async () => {
    await seedRoom();

    await joinRoom.run(buildRequest({ code: "ABCDE", displayName: "Joiner" }, "joiner-uid"));
    await joinRoom.run(buildRequest({ code: "ABCDE", displayName: "Joiner" }, "joiner-uid"));

    const room = await getRoom("room-1");
    expect(Object.keys(room.players).sort()).toEqual(["host-uid", "joiner-uid"]);
    expect(room.turnOrder).toEqual(["host-uid", "joiner-uid"]);
  });
});

describe("startGame", () => {
  async function seedTwoPlayerWaitingRoom(overrides: Record<string, unknown> = {}): Promise<void> {
    await seedRoom({
      players: {
        "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        "joiner-uid": { uid: "joiner-uid", displayName: "Joiner", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      },
      turnOrder: ["host-uid", "joiner-uid"],
      ...overrides,
    });
  }

  test("rejects a non-host caller", async () => {
    await seedTwoPlayerWaitingRoom();
    await expect(
      startGame.run(buildRequest({ roomId: "room-1" }, "joiner-uid"))
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  test("rejects starting with fewer than 2 players", async () => {
    await seedRoom(); // default: single host-uid player, turnOrder ["host-uid"]
    await expect(
      startGame.run(buildRequest({ roomId: "room-1" }, "host-uid"))
    ).rejects.toMatchObject({ code: "failed-precondition" });
  });

  test("succeeds for the host with 2+ players", async () => {
    await seedTwoPlayerWaitingRoom();
    const beforeCall = Date.now();

    const result = await startGame.run(buildRequest({ roomId: "room-1" }, "host-uid"));
    expect(result).toEqual({ started: true });

    const room = await getRoom("room-1");
    expect(room.status).toBe("playing");
    expect(room.round).toBe(1);
    expect(room.turnIndex).toBe(0);
    expect(room.stimulus).not.toBeNull();
    expect(room.stimulus).toEqual(
      expect.objectContaining({
        wordLabel: expect.any(String),
        inkColor: expect.any(String),
        options: expect.any(Array),
      })
    );
    expect(room.deadlineAtMs).toBeGreaterThan(beforeCall);
  });
});

describe("submitAnswer", () => {
  test("rejects a caller who isn't the current turn-holder", async () => {
    await seedPlayingRoom(); // turnIndex 0 -> "a" holds the turn
    await expect(
      submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "b"))
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  test("rejects when the room has no active stimulus", async () => {
    await seedRoom(); // default: status "waiting" -> stimulus/deadlineAtMs are null
    await expect(
      submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "host-uid"))
    ).rejects.toMatchObject({ code: "failed-precondition" });
  });

  test("a correct answer is accepted and advances the round", async () => {
    await seedPlayingRoom(); // stimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "correct" });
    const room = await getRoom("room-1");
    expect(room.round).toBe(2);
    expect(room.players.a.alive).toBe(true);
  });

  test("a wrong answer is accepted with reason 'wrong' and eliminates the answering player", async () => {
    await seedPlayingRoom(); // stimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "RED" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "wrong" });
    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(false);
  });

  test("returns deadline-exceeded when resolveRound reports the round was already resolved by a racing timeout", async () => {
    await seedPlayingRoom();
    // Simulates "someone else's timeout already resolved this round between
    // submitAnswer's own read and resolveRound's transactional read" without
    // needing a real concurrent writer: resolveRound is spied on (not
    // replaced wholesale via jest.mock, since other tests here need its real
    // transactional behavior) and forced to report applied:false for exactly
    // this one call.
    const spy = jest.spyOn(resolveRoundModule, "resolveRound").mockResolvedValueOnce(false);

    await expect(
      submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "a"))
    ).rejects.toMatchObject({ code: "deadline-exceeded" });

    spy.mockRestore();
  });
});

describe("resolveTimeout", () => {
  test("no-ops when called with a round that's already stale", async () => {
    await seedPlayingRoom({ round: 2 }); // room already advanced past round 1
    const spy = jest.spyOn(resolveRoundModule, "resolveRound");

    await resolveTimeout.run(buildTaskRequest({ roomId: "room-1", round: 1 }));

    expect(spy).not.toHaveBeenCalled();
    const room = await getRoom("room-1");
    expect(room.round).toBe(2);
    expect(room.players.a.alive).toBe(true);
    expect(room.turnIndex).toBe(0);

    spy.mockRestore();
  });

  test("eliminates the current turn-holder and advances the round when the deadline has genuinely expired", async () => {
    await seedPlayingRoom({ round: 1, turnIndex: 0, deadlineAtMs: Date.now() - 1000 });

    await resolveTimeout.run(buildTaskRequest({ roomId: "room-1", round: 1 }));

    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(false); // "a" held the turn at turnIndex 0
    expect(room.turnIndex).toBe(1);
    expect(room.round).toBe(2);
  });
});

describe("onPresenceChanged", () => {
  test("no-ops when the new presence value has state 'online'", async () => {
    await seedPlayingRoom();

    await onPresenceChanged.run(buildPresenceEvent("room-1", "a", { state: "online" }));

    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(true);
    expect(room.turnIndex).toBe(0);
    expect(room.round).toBe(1);
  });

  test("eliminates the given uid when state is 'offline' and the room is playing", async () => {
    await seedPlayingRoom(); // "c" is a bystander -- "a" holds the turn at turnIndex 0

    await onPresenceChanged.run(buildPresenceEvent("room-1", "c", { state: "offline" }));

    const room = await getRoom("room-1");
    expect(room.players.c.alive).toBe(false);
    // A bystander's disconnect must not disturb the active player's turn.
    expect(room.turnIndex).toBe(0);
    expect(room.round).toBe(1);
  });
});
