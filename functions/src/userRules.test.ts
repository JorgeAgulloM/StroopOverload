import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import * as path from "path";

// Rules-only suite: no firebase-admin app, no Cloud Functions imports. It asserts
// what a *client* can and cannot write to its own profile, which is the whole
// anti-cheat boundary -- the Admin SDK used by Cloud Functions bypasses rules.
const PROJECT_ID = "stroopoverload-rules-test";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
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

function myProfile(uid = "me") {
  return testEnv.authenticatedContext(uid).firestore().collection("users").doc(uid);
}

async function seedExistingProfile(uid = "me"): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(uid).set({
      nickname: "Neo",
      uniqueName: "@neo-1234",
      points: 500,
      highScore: 1200,
      experience: 3000,
      level: 5,
      isPremium: false,
    });
  });
}

describe("users/{uid} rules", () => {
  test("a signed-in player can create their own profile with cosmetic fields", async () => {
    await assertSucceeds(
      myProfile().set({ nickname: "Neo", uniqueName: "@neo-1234", profileCreated: true, unlockedPalettes: ["default"] })
    );
  });

  test("a new profile cannot seed its own scoring fields", async () => {
    await assertFails(myProfile().set({ nickname: "Neo", points: 999_999 }));
    await assertFails(myProfile().set({ nickname: "Neo", experience: 999_999, level: 99 }));
  });

  test("a player can still update their own cosmetic fields", async () => {
    await seedExistingProfile();

    await assertSucceeds(myProfile().update({ nickname: "Trinity" }));
    await assertSucceeds(myProfile().update({ unlockedPalettes: ["default", "neon"] }));
  });

  test("a player cannot write their own leaderboard score", async () => {
    await seedExistingProfile();

    await assertFails(myProfile().update({ points: 999_999_999 }));
    await assertFails(myProfile().update({ highScore: 999_999 }));
    await assertFails(myProfile().update({ experience: 999_999, level: 99 }));
    await assertFails(myProfile().update({ matchesWon: 500 }));
    await assertFails(myProfile().update({ dailyStreak: 365 }));
    await assertFails(myProfile().update({ awardedAchievements: { first_blood: 1 } }));
    // Clearing it would let a replayed run pay out again.
    await assertFails(myProfile().update({ recentRunIds: [] }));
  });

  test("a player cannot grant themselves entitlements", async () => {
    await seedExistingProfile();

    await assertFails(myProfile().update({ isPremium: true }));
    await assertFails(myProfile().update({ isAdFree: true }));
  });

  test("a cosmetic update smuggling one scoring field is rejected whole", async () => {
    await seedExistingProfile();

    await assertFails(myProfile().update({ nickname: "Trinity", points: 999_999 }));
  });

  test("echoing a scoring field's current value is allowed, because it changes nothing", async () => {
    await seedExistingProfile();

    // diff().affectedKeys() only reports keys whose value actually changed, so a
    // write that re-sends the stored value passes. That is not a loophole -- the
    // stored number is unchanged -- and it keeps a client that merges a whole
    // profile object from being rejected outright. Any *different* value is denied,
    // which is what the tests above cover.
    await assertSucceeds(myProfile().update({ points: 500 }));
  });

  test("a player cannot write someone else's profile", async () => {
    await seedExistingProfile("rival");

    const rival = testEnv.authenticatedContext("me").firestore().collection("users").doc("rival");
    await assertFails(rival.update({ nickname: "hacked" }));
    await assertFails(rival.update({ points: 0 }));
  });

  test("any signed-in player can read every profile, which the leaderboard needs", async () => {
    await seedExistingProfile("rival");

    await assertSucceeds(testEnv.authenticatedContext("me").firestore().collection("users").doc("rival").get());
    await assertSucceeds(testEnv.authenticatedContext("me").firestore().collection("users").get());
  });

  test("a signed-out client can neither read nor write profiles", async () => {
    await seedExistingProfile();

    const anon = testEnv.unauthenticatedContext().firestore().collection("users").doc("me");
    await assertFails(anon.get());
    await assertFails(anon.update({ nickname: "hacked" }));
  });

  test("a player can delete their own profile, for account deletion", async () => {
    await seedExistingProfile();

    await assertSucceeds(myProfile().delete());
  });
});
