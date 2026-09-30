import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";
import { getApps, initializeApp } from "firebase-admin/app";
import { HttpsError } from "firebase-functions/v2/https";
import { assertWithinRateLimit } from "./rateLimit";

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

const ACTION = "createRoom";
const LIMIT = 3;
const WINDOW_MS = 60_000;

describe("assertWithinRateLimit", () => {
  test("allows calls up to the limit", async () => {
    for (let i = 0; i < LIMIT; i++) {
      await expect(assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS)).resolves.toBeUndefined();
    }
  });

  test("rejects the call past the limit with a RATE_LIMITED HttpsError", async () => {
    for (let i = 0; i < LIMIT; i++) {
      await assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS);
    }

    await expect(assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS)).rejects.toMatchObject({
      code: "resource-exhausted",
      details: { reason: "RATE_LIMITED" },
    });
    await expect(assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS)).rejects.toBeInstanceOf(HttpsError);
  });

  test("counts each uid separately", async () => {
    for (let i = 0; i < LIMIT; i++) {
      await assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS);
    }

    await expect(assertWithinRateLimit("uid-2", ACTION, LIMIT, WINDOW_MS)).resolves.toBeUndefined();
  });

  test("counts each action separately", async () => {
    for (let i = 0; i < LIMIT; i++) {
      await assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS);
    }

    await expect(assertWithinRateLimit("uid-1", "joinRoom", LIMIT, WINDOW_MS)).resolves.toBeUndefined();
  });

  test("starts a fresh window once the previous one has elapsed", async () => {
    const start = Date.now() - WINDOW_MS - 1;
    for (let i = 0; i < LIMIT; i++) {
      await assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS, start);
    }
    await expect(assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS, start)).rejects.toBeInstanceOf(HttpsError);

    await expect(assertWithinRateLimit("uid-1", ACTION, LIMIT, WINDOW_MS)).resolves.toBeUndefined();
  });
});
