import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import { getApps, initializeApp, getApp } from "firebase-admin/app";
import { DocumentData } from "firebase-admin/firestore";
import { CallableRequest } from "firebase-functions/v2/https";
import { Request as TaskRequest } from "firebase-functions/v2/tasks";
import { DatabaseEvent, DataSnapshot } from "firebase-functions/v2/database";
import { Change } from "firebase-functions/v2/core";
import {
  createRoom,
  joinRoom,
  startGame,
  beginRound,
  submitAnswer,
  resolveTimeout,
  resolveSoloPlayerTimeout,
  onPresenceChanged,
  deleteMyMultiplayerData,
} from "./index";
import * as resolveRoundModule from "./resolveRound";
import { scheduleBombExplosion, scheduleSoloPlayerTimeoutCheck, scheduleTimeoutCheck } from "./taskQueue";

// deleteMyMultiplayerData also removes each deleted room's Realtime Database
// presence node. There's no RTDB emulator in this test run (only Firestore,
// per package.json's `firebase emulators:exec --only firestore`), so
// firebase-admin/database is mocked here the same way ./taskQueue is mocked
// above -- this suite exercises the Firestore query/delete logic, not RTDB
// infrastructure that isn't under test.
const mockDbRemove = jest.fn().mockResolvedValue(undefined);
jest.mock("firebase-admin/database", () => ({
  getDatabase: () => ({ ref: () => ({ remove: mockDbRemove }) }),
}));

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
  scheduleGameStart: jest.fn().mockResolvedValue(undefined),
  scheduleBombExplosion: jest.fn().mockResolvedValue(undefined),
  scheduleSoloPlayerTimeoutCheck: jest.fn().mockResolvedValue(undefined),
}));

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  // Unlike resolveRound.test.ts, this file imports index.ts, whose module-load
  // side effect (`initializeApp()`, no args) runs before this beforeAll and
  // already creates the default admin app -- resolved against whatever
  // project the emulator environment provides (functions/.firebaserc's
  // default project), not a project ID this file picks. Calling
  // initializeApp({ projectId: ... }) again here would throw (duplicate
  // default app) and, worse, using a *different* projectId for
  // initializeTestEnvironment than the one index.ts's admin app resolved to
  // would silently point the two Firestore clients at two different
  // emulator-side projects, making every write invisible to the test's
  // read-back. So: read back the project ID the already-initialized default
  // app actually resolved to, and reuse it for the rules-unit-testing env.
  if (getApps().length === 0) {
    throw new Error("Expected index.ts's module-load initializeApp() to have already run.");
  }
  const projectId = getApp().options.projectId;
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

async function getRoom(roomId: string): Promise<DocumentData> {
  let data: DocumentData | undefined;
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
// Only `data` is meaningful to the handlers under test (resolveTimeout,
// beginRound); the TaskContext fields are required by the type but unused by
// the handler logic, so they are stubbed with plausible values.
function buildTaskRequest<T>(data: T): TaskRequest<T> {
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
    // Required since firebase-functions v7; onPresenceChanged ignores it.
    authType: "unauthenticated",
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

// solo_survival's per-player equivalent of seedPlayingRoom: no shared
// turn/stimulus, each player carries their own soloRound/soloStimulus/
// soloDeadlineAtMs instead.
async function seedSoloPlayingRoom(overrides: Record<string, unknown> = {}): Promise<void> {
  await seedRoom({
    mode: "solo_survival",
    status: "playing",
    hostUid: "a",
    players: {
      a: {
        uid: "a",
        displayName: "A",
        avatarIndex: 0,
        alive: true,
        order: 0,
        joinedAtMs: 0,
        soloScore: 0,
        soloRound: 0,
        soloStreak: 0,
        soloStimulus: { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
        soloDeadlineAtMs: Date.now() + 3000,
      },
      b: {
        uid: "b",
        displayName: "B",
        avatarIndex: 0,
        alive: true,
        order: 1,
        joinedAtMs: 0,
        soloScore: 0,
        soloRound: 0,
        soloStreak: 0,
        soloStimulus: { wordLabel: "GREEN", inkColor: "YELLOW", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
        soloDeadlineAtMs: Date.now() + 3000,
      },
    },
    turnOrder: ["a", "b"],
    turnIndex: 0,
    round: 0,
    stimulus: null,
    deadlineAtMs: Date.now() + 60_000,
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

  test("defaults to 'mistake' mode when none is requested", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "Ace" }, "host-uid"));
    const room = await getRoom(result.roomId);
    expect(room.mode).toBe("mistake");
  });

  test("honors an explicitly requested valid mode", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "Ace", mode: "hot_potato" }, "host-uid"));
    const room = await getRoom(result.roomId);
    expect(room.mode).toBe("hot_potato");
  });

  test("honors solo_survival as a requested mode", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "Ace", mode: "solo_survival" }, "host-uid"));
    const room = await getRoom(result.roomId);
    expect(room.mode).toBe("solo_survival");
  });

  test("falls back to 'mistake' for an unrecognized mode value instead of trusting client input", async () => {
    const result = await createRoom.run(buildRequest({ displayName: "Ace", mode: "cheat_mode" }, "host-uid"));
    const room = await getRoom(result.roomId);
    expect(room.mode).toBe("mistake");
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

  test("moves the room to 'starting' with a future startsAtMs, not straight to 'playing'", async () => {
    await seedTwoPlayerWaitingRoom();
    const beforeCall = Date.now();

    const result = await startGame.run(buildRequest({ roomId: "room-1" }, "host-uid"));
    expect(result).toEqual({ startsAtMs: expect.any(Number) });
    expect((result as { startsAtMs: number }).startsAtMs).toBeGreaterThan(beforeCall);

    // The real fix under test: round 1's stimulus/deadline must NOT exist yet.
    // They're only computed by beginRound once startsAtMs is actually reached,
    // so a slow-rendering client can never lose time off round 1's window.
    const room = await getRoom("room-1");
    expect(room.status).toBe("starting");
    expect(room.startsAtMs).toBe((result as { startsAtMs: number }).startsAtMs);
    expect(room.stimulus).toBeNull();
    expect(room.deadlineAtMs).toBeNull();
  });
});

describe("beginRound", () => {
  test("transitions 'starting' to 'playing' and computes round 1's stimulus/deadline fresh", async () => {
    await seedRoom({
      status: "starting",
      startsAtMs: Date.now() + 4000,
      players: {
        "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        "joiner-uid": { uid: "joiner-uid", displayName: "Joiner", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      },
      turnOrder: ["host-uid", "joiner-uid"],
    });
    const beforeCall = Date.now();

    await beginRound.run(buildTaskRequest<{ roomId: string }>({ roomId: "room-1" }));

    const room = await getRoom("room-1");
    expect(room.status).toBe("playing");
    expect(room.round).toBe(1);
    expect(room.turnIndex).toBe(0);
    expect(room.stimulus).toEqual(
      expect.objectContaining({ wordLabel: expect.any(String), inkColor: expect.any(String), options: expect.any(Array) })
    );
    // Computed at beginRound's execution time, not startGame's call time --
    // this is what actually fixes the desync bug.
    expect(room.deadlineAtMs).toBeGreaterThan(beforeCall);
  });

  test("arms a hidden bomb when the room's mode is 'hot_potato'", async () => {
    await seedRoom({
      status: "starting",
      mode: "hot_potato",
      startsAtMs: Date.now() + 4000,
      players: {
        "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        "joiner-uid": { uid: "joiner-uid", displayName: "Joiner", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      },
      turnOrder: ["host-uid", "joiner-uid"],
    });

    await beginRound.run(buildTaskRequest<{ roomId: string }>({ roomId: "room-1" }));

    expect(scheduleBombExplosion).toHaveBeenCalledWith("room-1", expect.any(Number), expect.any(Number));
  });

  test("does not arm a bomb for the default 'mistake' mode", async () => {
    await seedRoom({
      status: "starting",
      startsAtMs: Date.now() + 4000,
      players: {
        "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        "joiner-uid": { uid: "joiner-uid", displayName: "Joiner", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      },
      turnOrder: ["host-uid", "joiner-uid"],
    });

    await beginRound.run(buildTaskRequest<{ roomId: string }>({ roomId: "room-1" }));

    expect(scheduleBombExplosion).not.toHaveBeenCalled();
  });

  test("no-ops when the room already moved past 'starting' (stale/duplicate task run)", async () => {
    await seedPlayingRoom({ round: 3 }); // already playing, not "starting"

    await beginRound.run(buildTaskRequest<{ roomId: string }>({ roomId: "room-1" }));

    const room = await getRoom("room-1");
    expect(room.status).toBe("playing");
    expect(room.round).toBe(3); // untouched
  });

  test("for 'solo_survival' rooms, seeds every player with their own stimulus and schedules per-player + session timeouts", async () => {
    await seedRoom({
      mode: "solo_survival",
      status: "starting",
      startsAtMs: Date.now() + 4000,
      players: {
        "host-uid": { uid: "host-uid", displayName: "Host", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        "joiner-uid": { uid: "joiner-uid", displayName: "Joiner", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
      },
      turnOrder: ["host-uid", "joiner-uid"],
    });

    await beginRound.run(buildTaskRequest<{ roomId: string }>({ roomId: "room-1" }));

    const room = await getRoom("room-1");
    expect(room.status).toBe("playing");
    expect(room.stimulus).toBeNull(); // room-level stimulus stays unused for this mode
    expect(room.players["host-uid"].soloStimulus).toBeTruthy();
    expect(room.players["joiner-uid"].soloStimulus).toBeTruthy();
    expect(scheduleSoloPlayerTimeoutCheck).toHaveBeenCalledWith("room-1", "host-uid", 0, expect.any(Number));
    expect(scheduleSoloPlayerTimeoutCheck).toHaveBeenCalledWith("room-1", "joiner-uid", 0, expect.any(Number));
    expect(scheduleTimeoutCheck).toHaveBeenCalledWith("room-1", 0, 60_000); // shared session-end clock
    expect(scheduleBombExplosion).not.toHaveBeenCalled();
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

  test("dispatches to the hot-potato resolver for 'hot_potato' rooms: wrong answer does NOT eliminate", async () => {
    await seedPlayingRoom({ mode: "hot_potato" }); // stimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "RED" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "wrong" });
    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(true); // hot_potato never eliminates on a wrong answer
    expect(room.turnIndex).toBe(0); // still "a" -- turn only passes on correct in this mode
  });

  test("dispatches to the hot-potato resolver for 'hot_potato' rooms: correct answer passes the turn", async () => {
    await seedPlayingRoom({ mode: "hot_potato" }); // stimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "correct" });
    const room = await getRoom("room-1");
    expect(room.turnIndex).toBe(1); // moved to "b"
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

  test("dispatches to the solo_survival resolver: a correct answer only advances the acting player's own round", async () => {
    await seedSoloPlayingRoom(); // "a"'s soloStimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "correct" });
    const room = await getRoom("room-1");
    expect(room.players.a.soloRound).toBe(1);
    expect(room.players.b.soloRound).toBe(0); // untouched -- no shared turn in this mode
  });

  test("dispatches to the solo_survival resolver: a wrong answer busts only the acting player, and with only 2 players that ends the match", async () => {
    await seedSoloPlayingRoom(); // "a"'s soloStimulus.inkColor === "BLUE"
    const result = await submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "RED" }, "a"));

    expect(result).toEqual({ accepted: true, reason: "wrong" });
    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(false);
    expect(room.players.b.alive).toBe(true); // untouched
    // Only "b" remains alive -- no point playing on solo, so the match ends
    // immediately with "b" as the sole survivor.
    expect(room.status).toBe("finished");
    expect(room.winnerUid).toBe("b");
  });

  test("solo_survival rejects a caller who has already busted", async () => {
    await seedSoloPlayingRoom({
      players: {
        a: {
          uid: "a",
          displayName: "A",
          avatarIndex: 0,
          alive: false,
          order: 0,
          joinedAtMs: 0,
          soloScore: 100,
          soloRound: 1,
          soloStreak: 0,
          soloStimulus: null,
          soloDeadlineAtMs: null,
        },
        b: {
          uid: "b",
          displayName: "B",
          avatarIndex: 0,
          alive: true,
          order: 1,
          joinedAtMs: 0,
          soloScore: 0,
          soloRound: 0,
          soloStreak: 0,
          soloStimulus: { wordLabel: "GREEN", inkColor: "YELLOW", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
          soloDeadlineAtMs: Date.now() + 3000,
        },
      },
    });

    await expect(
      submitAnswer.run(buildRequest({ roomId: "room-1", selectedColor: "BLUE" }, "a"))
    ).rejects.toMatchObject({ code: "failed-precondition" });
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

  test("rethrows and logs when resolveRound throws for a genuinely expired deadline", async () => {
    await seedPlayingRoom({ round: 1, turnIndex: 0, deadlineAtMs: Date.now() - 1000 });
    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
    const spy = jest.spyOn(resolveRoundModule, "resolveRound").mockRejectedValueOnce(new Error("boom"));

    // Cloud Tasks' retryConfig relies on the task rejecting so it gets
    // retried -- verify the catch block in resolveTimeout rethrows rather
    // than swallowing the error.
    await expect(resolveTimeout.run(buildTaskRequest({ roomId: "room-1", round: 1 }))).rejects.toThrow();

    expect(consoleErrorSpy).toHaveBeenCalledWith(expect.stringContaining("room-1"), expect.anything());

    spy.mockRestore();
    consoleErrorSpy.mockRestore();
  });

  test("for 'solo_survival' rooms, finishes the whole match once the shared session clock expires", async () => {
    // solo_survival never advances RoomDoc.round, so it's always scheduled/checked with round 0 (see beginRound).
    await seedSoloPlayingRoom({ round: 0, deadlineAtMs: Date.now() - 1000 });

    await resolveTimeout.run(buildTaskRequest({ roomId: "room-1", round: 0 }));

    const room = await getRoom("room-1");
    expect(room.status).toBe("finished");
    expect(room.winnerUid).toBeTruthy();
  });
});

describe("resolveSoloPlayerTimeout", () => {
  test("busts a player whose own per-stimulus deadline has genuinely expired", async () => {
    await seedSoloPlayingRoom({
      players: {
        a: {
          uid: "a",
          displayName: "A",
          avatarIndex: 0,
          alive: true,
          order: 0,
          joinedAtMs: 0,
          soloScore: 0,
          soloRound: 0,
          soloStreak: 0,
          soloStimulus: { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
          soloDeadlineAtMs: Date.now() - 1000,
        },
        b: {
          uid: "b",
          displayName: "B",
          avatarIndex: 0,
          alive: true,
          order: 1,
          joinedAtMs: 0,
          soloScore: 0,
          soloRound: 0,
          soloStreak: 0,
          soloStimulus: { wordLabel: "GREEN", inkColor: "YELLOW", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
          soloDeadlineAtMs: Date.now() + 3000,
        },
      },
    });

    await resolveSoloPlayerTimeout.run(buildTaskRequest({ roomId: "room-1", uid: "a", round: 0 }));

    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(false);
    expect(room.players.b.alive).toBe(true); // untouched -- own independent deadline, not yet reached
  });

  test("no-ops when the player's round is already stale (they already answered)", async () => {
    await seedSoloPlayingRoom({
      players: {
        a: {
          uid: "a",
          displayName: "A",
          avatarIndex: 0,
          alive: true,
          order: 0,
          joinedAtMs: 0,
          soloScore: 110,
          soloRound: 1, // already advanced past round 0
          soloStreak: 1,
          soloStimulus: { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] },
          soloDeadlineAtMs: Date.now() + 3000,
        },
      },
    });

    await resolveSoloPlayerTimeout.run(buildTaskRequest({ roomId: "room-1", uid: "a", round: 0 }));

    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(true); // untouched
    expect(room.players.a.soloRound).toBe(1);
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

  test("does NOT eliminate on disconnect in 'hot_potato' mode -- the bomb is the only loss condition", async () => {
    await seedPlayingRoom({ mode: "hot_potato" });

    await onPresenceChanged.run(buildPresenceEvent("room-1", "c", { state: "offline" }));

    const room = await getRoom("room-1");
    expect(room.players.c.alive).toBe(true);
  });

  test("busts only the disconnecting player in 'solo_survival' mode, ending the match since only 2 players were in it", async () => {
    await seedSoloPlayingRoom();

    await onPresenceChanged.run(buildPresenceEvent("room-1", "a", { state: "offline" }));

    const room = await getRoom("room-1");
    expect(room.players.a.alive).toBe(false);
    expect(room.players.b.alive).toBe(true);
    expect(room.status).toBe("finished"); // "b" is the sole survivor
    expect(room.winnerUid).toBe("b");
  });

  test("swallows and logs when resolveRound throws instead of rejecting", async () => {
    await seedPlayingRoom(); // "c" is a bystander -- "a" holds the turn at turnIndex 0
    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
    const spy = jest.spyOn(resolveRoundModule, "resolveRound").mockRejectedValueOnce(new Error("boom"));

    // RTDB triggers don't get the same automatic retry semantics as Cloud
    // Tasks -- verify the catch block in onPresenceChanged swallows the
    // error (logs it) rather than letting it reject the handler.
    await expect(onPresenceChanged.run(buildPresenceEvent("room-1", "c", { state: "offline" }))).resolves.not.toThrow();

    expect(consoleErrorSpy).toHaveBeenCalledWith(expect.stringContaining("room-1"), expect.anything());

    spy.mockRestore();
    consoleErrorSpy.mockRestore();
  });
});

describe("deleteMyMultiplayerData", () => {
  test("rejects unauthenticated callers", async () => {
    await expect(deleteMyMultiplayerData.run(buildRequest({}, undefined))).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  test("deletes every room the caller is a player in and removes its presence node, leaving unrelated rooms untouched", async () => {
    await seedRoom(); // "room-1", player "host-uid"
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("rooms").doc("room-2").set({
        code: "FGHIJ",
        status: "waiting",
        hostUid: "someone-else",
        players: {
          "someone-else": { uid: "someone-else", displayName: "Other", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
        },
        turnOrder: ["someone-else"],
        turnIndex: 0,
        round: 0,
        stimulus: null,
        deadlineAtMs: null,
        winnerUid: null,
        createdAtMs: Date.now(),
      });
    });

    const result = await deleteMyMultiplayerData.run(buildRequest({}, "host-uid"));

    expect(result).toEqual({ roomsDeleted: 1 });
    expect(await getRoom("room-1")).toBeUndefined();
    expect(await getRoom("room-2")).toBeDefined();
    expect(mockDbRemove).toHaveBeenCalledTimes(1);
  });

  test("is a no-op (zero rooms deleted) when the caller never played any room", async () => {
    await seedRoom(); // "room-1", player "host-uid" only

    const result = await deleteMyMultiplayerData.run(buildRequest({}, "never-played-uid"));

    expect(result).toEqual({ roomsDeleted: 0 });
    expect(await getRoom("room-1")).toBeDefined();
  });
});

describe("firestore.rules: rooms/{roomId}/private", () => {
  async function seedPrivateBombDoc(roomId: string, bombAtMs: number): Promise<void> {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("rooms").doc(roomId).collection("private").doc("bomb").set({ bombAtMs });
    });
  }

  test("a room member can read the room doc but not the hidden bomb subdocument", async () => {
    await seedRoom(); // "room-1", player "host-uid"
    await seedPrivateBombDoc("room-1", Date.now() + 60_000);

    const memberDb = testEnv.authenticatedContext("host-uid").firestore();
    await assertSucceeds(memberDb.collection("rooms").doc("room-1").get());
    await assertFails(memberDb.collection("rooms").doc("room-1").collection("private").doc("bomb").get());
  });

  test("a non-member can read neither the room doc nor the hidden bomb subdocument", async () => {
    await seedRoom(); // "room-1", player "host-uid" only
    await seedPrivateBombDoc("room-1", Date.now() + 60_000);

    const strangerDb = testEnv.authenticatedContext("stranger-uid").firestore();
    await assertFails(strangerDb.collection("rooms").doc("room-1").get());
    await assertFails(strangerDb.collection("rooms").doc("room-1").collection("private").doc("bomb").get());
  });
});
