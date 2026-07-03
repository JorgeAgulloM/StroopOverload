import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import * as admin from "firebase-admin";
import { CallableRequest } from "firebase-functions/v2/https";
import { createRoom, joinRoom } from "./index";

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
