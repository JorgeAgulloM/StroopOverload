import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData } from "firebase-admin/firestore";
import { purgeExpiredRooms, ROOM_TTL_MS, sweepStuckRooms, WATCHDOG_GRACE_MS } from "./roomWatchdog";
import { scheduleBombExplosion, scheduleTimeoutCheck } from "./taskQueue";

// Same reasoning as resolveRound.test.ts: no Cloud Tasks emulator here, so the
// scheduling side effects are mocked and only the Firestore logic is exercised.
jest.mock("./taskQueue", () => ({
  scheduleTimeoutCheck: jest.fn().mockResolvedValue(undefined),
  scheduleGameStart: jest.fn().mockResolvedValue(undefined),
  scheduleBombExplosion: jest.fn().mockResolvedValue(undefined),
  scheduleSoloPlayerTimeoutCheck: jest.fn().mockResolvedValue(undefined),
}));

// No RTDB emulator either -- purgeExpiredRooms also drops each room's presence node.
const mockDbRemove = jest.fn().mockResolvedValue(undefined);
jest.mock("firebase-admin/database", () => ({
  getDatabase: () => ({ ref: () => ({ remove: mockDbRemove }) }),
}));

const PROJECT_ID = "stroopoverload-test";
const OVERDUE = WATCHDOG_GRACE_MS + 5_000;

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

const TWO_PLAYERS = {
  a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAtMs: 0 },
  b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAtMs: 0 },
};
const THREE_PLAYERS = {
  ...TWO_PLAYERS,
  c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAtMs: 0 },
};
const STIMULUS = { wordLabel: "RED", inkColor: "BLUE", options: ["RED", "GREEN", "BLUE", "YELLOW"] };

async function seedRoom(roomId: string, overrides: Record<string, unknown> = {}): Promise<void> {
  const base = {
    code: "ABCDE",
    status: "playing",
    mode: "mistake",
    hostUid: "a",
    players: THREE_PLAYERS,
    turnOrder: ["a", "b", "c"],
    turnIndex: 0,
    round: 1,
    stimulus: STIMULUS,
    deadlineAtMs: Date.now() + 3000,
    winnerUid: null,
    createdAtMs: Date.now(),
    startsAtMs: null,
    ...overrides,
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc(roomId).set(base);
  });
}

async function seedBomb(roomId: string, bombAtMs: number): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc(roomId).collection("private").doc("bomb").set({ bombAtMs });
  });
}

async function getRoom(roomId: string): Promise<DocumentData | undefined> {
  let data: DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc(roomId).get();
    data = snap.data();
  });
  return data;
}

async function getBomb(roomId: string): Promise<DocumentData | undefined> {
  let data: DocumentData | undefined;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const snap = await context.firestore().collection("rooms").doc(roomId).collection("private").doc("bomb").get();
    data = snap.data();
  });
  return data;
}

describe("sweepStuckRooms", () => {
  test("starts a 'starting' room whose beginRound task never ran", async () => {
    await seedRoom("room-1", {
      status: "starting",
      players: TWO_PLAYERS,
      turnOrder: ["a", "b"],
      round: 0,
      stimulus: null,
      deadlineAtMs: null,
      startsAtMs: Date.now() - OVERDUE,
    });

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.status).toBe("playing");
    expect(room!.round).toBe(1);
    expect(scheduleTimeoutCheck).toHaveBeenCalledWith("room-1", 1, expect.any(Number));
  });

  test("leaves a 'starting' room alone while its countdown is still within the grace window", async () => {
    await seedRoom("room-1", { status: "starting", round: 0, stimulus: null, deadlineAtMs: null, startsAtMs: Date.now() });

    await sweepStuckRooms();

    expect((await getRoom("room-1"))!.status).toBe("starting");
  });

  test("times out the turn-holder of a 'mistake' round whose timeout task was lost", async () => {
    await seedRoom("room-1", { deadlineAtMs: Date.now() - OVERDUE });

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.players.a.alive).toBe(false);
    expect(room!.round).toBe(2);
  });

  test("does not touch a 'mistake' round that is still inside its deadline + grace", async () => {
    await seedRoom("room-1", { deadlineAtMs: Date.now() - 1_000 });

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.players.a.alive).toBe(true);
    expect(room!.round).toBe(1);
  });

  test("re-arms a 'hot_potato' room that is playing with no bomb armed", async () => {
    await seedRoom("room-1", { mode: "hot_potato" });

    await sweepStuckRooms();

    expect(await getBomb("room-1")).toBeDefined();
    expect(scheduleBombExplosion).toHaveBeenCalledWith("room-1", expect.any(Number), expect.any(Number));
    expect((await getRoom("room-1"))!.players.a.alive).toBe(true);
  });

  test("detonates a 'hot_potato' bomb whose explosion task was lost", async () => {
    const bombAtMs = Date.now() - OVERDUE;
    await seedRoom("room-1", { mode: "hot_potato" });
    await seedBomb("room-1", bombAtMs);

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.players.a.alive).toBe(false);
    expect(room!.status).toBe("playing");
    expect((await getBomb("room-1"))!.bombAtMs).not.toBe(bombAtMs); // a fresh bomb replaced it
  });

  test("leaves a live 'hot_potato' bomb alone", async () => {
    await seedRoom("room-1", { mode: "hot_potato" });
    await seedBomb("room-1", Date.now() + 10_000);

    await sweepStuckRooms();

    expect((await getRoom("room-1"))!.players.a.alive).toBe(true);
    expect(scheduleBombExplosion).not.toHaveBeenCalled();
  });

  test("finishes a 'solo_survival' session whose shared clock task was lost", async () => {
    await seedRoom("room-1", {
      mode: "solo_survival",
      stimulus: null,
      deadlineAtMs: Date.now() - OVERDUE,
      players: {
        a: { ...TWO_PLAYERS.a, soloScore: 300, soloRound: 3, soloDeadlineAtMs: Date.now() + 2_000 },
        b: { ...TWO_PLAYERS.b, soloScore: 100, soloRound: 1, soloDeadlineAtMs: Date.now() + 2_000 },
      },
      turnOrder: ["a", "b"],
    });

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.status).toBe("finished");
    expect(room!.winnerUid).toBe("a");
  });

  test("busts a 'solo_survival' player whose personal timeout task was lost", async () => {
    await seedRoom("room-1", {
      mode: "solo_survival",
      stimulus: null,
      deadlineAtMs: Date.now() + 30_000,
      players: {
        a: { ...THREE_PLAYERS.a, soloRound: 2, soloDeadlineAtMs: Date.now() - OVERDUE, soloStimulus: STIMULUS },
        b: { ...THREE_PLAYERS.b, soloRound: 2, soloDeadlineAtMs: Date.now() + 2_000, soloStimulus: STIMULUS },
        c: { ...THREE_PLAYERS.c, soloRound: 2, soloDeadlineAtMs: Date.now() + 2_000, soloStimulus: STIMULUS },
      },
    });

    await sweepStuckRooms();

    const room = await getRoom("room-1");
    expect(room!.players.a.alive).toBe(false);
    expect(room!.players.b.alive).toBe(true);
    expect(room!.status).toBe("playing");
  });

  test("ignores waiting and finished rooms", async () => {
    await seedRoom("room-w", { status: "waiting", deadlineAtMs: null, stimulus: null });
    await seedRoom("room-f", { status: "finished", winnerUid: "a", deadlineAtMs: Date.now() - OVERDUE });

    await sweepStuckRooms();

    expect((await getRoom("room-w"))!.status).toBe("waiting");
    expect((await getRoom("room-f"))!.players.a.alive).toBe(true);
  });

  test("one broken room does not stop the sweep from repairing the others", async () => {
    await seedRoom("room-bad", { status: "starting", startsAtMs: Date.now() - OVERDUE, round: 0, stimulus: null });
    (scheduleTimeoutCheck as jest.Mock).mockRejectedValueOnce(new Error("queue down"));
    await seedRoom("room-ok", { mode: "hot_potato" });

    await expect(sweepStuckRooms()).resolves.toBeGreaterThanOrEqual(1);

    expect(await getBomb("room-ok")).toBeDefined();
  });
});

describe("purgeExpiredRooms", () => {
  test("deletes rooms (any status) older than the TTL, plus their presence and private data", async () => {
    await seedRoom("room-old", { status: "waiting", createdAtMs: Date.now() - ROOM_TTL_MS - 1_000 });
    await seedBomb("room-old", Date.now());
    await seedRoom("room-new", { status: "finished", createdAtMs: Date.now() - 60_000 });

    const deleted = await purgeExpiredRooms();

    expect(deleted).toBe(1);
    expect(await getRoom("room-old")).toBeUndefined();
    expect(await getBomb("room-old")).toBeUndefined();
    expect(await getRoom("room-new")).toBeDefined();
    expect(mockDbRemove).toHaveBeenCalledTimes(1);
  });
});
