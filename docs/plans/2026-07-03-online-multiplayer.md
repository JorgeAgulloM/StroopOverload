# Online Multiplayer (1-4 players) Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Let up to 4 players join a Firebase-backed room, see the same Stroop stimulus at the same time, and take turns answering — one wrong answer, timeout, or disconnect eliminates a player; last one alive wins.

**Architecture:** Firestore holds authoritative room state (`rooms/{roomId}`); all state transitions (turn resolution, elimination, win detection) happen only inside Cloud Functions — clients never write room documents directly, only call callable functions and read via a snapshot listener. Cloud Tasks resolves per-round timeouts server-side. Realtime Database presence (`onDisconnect`) feeds a Cloud Functions trigger that eliminates a player the instant they drop, through the exact same `resolveRound` code path used for wrong answers and timeouts — so a room can never get stuck waiting on a player who is gone.

**Tech Stack:** Firebase Cloud Functions v2 (Node 20 / TypeScript), Cloud Firestore, Cloud Tasks (via `onTaskDispatched`), Firebase Realtime Database (presence only), Jest. Android: Kotlin/Compose, `firebase-functions`/`firebase-database` client SDKs, Turbine for Flow tests.

**Platform:** Android (client) + Firebase backend (Cloud Functions/Firestore/RTDB).

---

## Manual prerequisites (not automated by this plan — do these yourself before deploying)

1. **Upgrade the Firebase project to the Blaze plan.** Project id is `stroopoverload-softyorch` (read from `app/google-services.json`). Cloud Functions v2 + Cloud Tasks require Blaze; Spark will refuse to deploy. Firebase Console → Usage and billing → Modify plan.
2. **Provision Realtime Database** for this project (Firebase Console → Build → Realtime Database → Create Database). It's a separate product from Firestore and does not exist automatically. Note the database URL — it needs to land in `google-services.json` (re-download it after creating the RTDB instance) for the Android client's `FirebaseDatabase.getInstance()` to resolve.
3. Nothing in this plan runs `firebase deploy`. That is a separate, explicit step you run yourself once 1 and 2 are done.

---

## PART A — Backend (Cloud Functions, Firestore, Realtime Database)

### Task A1: Scaffold the Functions project

**Files:**
- Create: `functions/package.json`
- Create: `functions/tsconfig.json`
- Create: `functions/jest.config.js`
- Create: `functions/src/index.ts`
- Create: `firebase.json`
- Create: `.firebaserc`
- Create: `firestore.rules`
- Create: `firestore.indexes.json`
- Create: `database.rules.json`
- Modify: `.gitignore` (append `functions/node_modules/`, `functions/lib/`)

- [ ] **Step 1: Create `.firebaserc`**

```json
{
  "projects": {
    "default": "stroopoverload-softyorch"
  }
}
```

- [ ] **Step 2: Create `firebase.json`**

```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "database": {
    "rules": "database.rules.json"
  },
  "functions": [
    {
      "source": "functions",
      "codebase": "default",
      "runtime": "nodejs20"
    }
  ]
}
```

- [ ] **Step 3: Create `firestore.indexes.json`**

```json
{
  "indexes": [],
  "fieldOverrides": []
}
```

- [ ] **Step 4: Create `firestore.rules`**

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /rooms/{roomId} {
      allow read: if request.auth != null && request.auth.uid in resource.data.players;
      allow write: if false;
    }
  }
}
```

- [ ] **Step 5: Create `database.rules.json`**

```json
{
  "rules": {
    "presence": {
      "$roomId": {
        "$uid": {
          ".read": "auth != null",
          ".write": "auth != null && auth.uid == $uid",
          ".validate": "newData.hasChildren(['state', 'lastChanged'])"
        }
      }
    }
  }
}
```

- [ ] **Step 6: Create `functions/package.json`**

```json
{
  "name": "functions",
  "engines": { "node": "20" },
  "main": "lib/index.js",
  "scripts": {
    "build": "tsc",
    "test": "jest",
    "serve": "npm run build && firebase emulators:start --only functions,firestore,database"
  },
  "dependencies": {
    "firebase-admin": "^12.7.0",
    "firebase-functions": "^6.3.1"
  },
  "devDependencies": {
    "typescript": "^5.6.3",
    "jest": "^29.7.0",
    "ts-jest": "^29.2.5",
    "@types/jest": "^29.5.13",
    "@types/node": "^20.16.11"
  }
}
```

- [ ] **Step 7: Create `functions/tsconfig.json`**

```json
{
  "compilerOptions": {
    "module": "commonjs",
    "target": "es2020",
    "lib": ["es2020"],
    "outDir": "lib",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "resolveJsonModule": true
  },
  "compileOnSave": true,
  "include": ["src"]
}
```

- [ ] **Step 8: Create `functions/jest.config.js`**

```js
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
};
```

- [ ] **Step 9: Create empty entrypoint `functions/src/index.ts`**

```ts
// Cloud Functions entrypoints are added incrementally in later tasks.
export {};
```

- [ ] **Step 10: Append to `.gitignore`**

```
functions/node_modules/
functions/lib/
```

- [ ] **Step 11: Install dependencies**

Run: `cd functions && npm install`
Expected: `node_modules/` created, no errors.

- [ ] **Step 12: Verify empty project builds**

Run: `cd functions && npm run build`
Expected: `lib/index.js` created, no TypeScript errors.

- [ ] **Step 13: Commit**

```bash
git add functions/package.json functions/tsconfig.json functions/jest.config.js functions/src/index.ts firebase.json .firebaserc firestore.rules firestore.indexes.json database.rules.json .gitignore
git commit -m "chore: scaffold Firebase Functions project for online multiplayer"
```

---

### Task A2: Pure turn/elimination logic (TDD)

**Files:**
- Create: `functions/src/turnLogic.ts`
- Test: `functions/src/turnLogic.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
// functions/src/turnLogic.test.ts
import { aliveCount, nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";

describe("timeLimitMsForRound", () => {
  test("round 1 uses the initial time limit", () => {
    expect(timeLimitMsForRound(1)).toBe(3000);
  });

  test("time limit decays by 150ms per round", () => {
    expect(timeLimitMsForRound(2)).toBe(2850);
  });

  test("time limit never drops below the minimum", () => {
    expect(timeLimitMsForRound(100)).toBe(800);
  });
});

describe("nextAliveIndex", () => {
  const turnOrder = ["a", "b", "c", "d"];

  test("moves to the next alive player", () => {
    const players = { a: { alive: true }, b: { alive: true }, c: { alive: true }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 0)).toBe(1);
  });

  test("skips over eliminated players", () => {
    const players = { a: { alive: true }, b: { alive: false }, c: { alive: true }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 0)).toBe(2);
  });

  test("wraps around to the start", () => {
    const players = { a: { alive: true }, b: { alive: false }, c: { alive: false }, d: { alive: true } };
    expect(nextAliveIndex(turnOrder, players, 3)).toBe(0);
  });
});

describe("aliveCount / soleSurvivor", () => {
  test("aliveCount counts only alive players", () => {
    const players = { a: { alive: true }, b: { alive: false } };
    expect(aliveCount(players)).toBe(1);
  });

  test("soleSurvivor returns the uid when exactly one player is alive", () => {
    const players = { a: { alive: false }, b: { alive: true } };
    expect(soleSurvivor(players)).toBe("b");
  });

  test("soleSurvivor returns null when more than one player is alive", () => {
    const players = { a: { alive: true }, b: { alive: true } };
    expect(soleSurvivor(players)).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npm test`
Expected: FAIL — `Cannot find module './turnLogic'`

- [ ] **Step 3: Implement `functions/src/turnLogic.ts`**

```ts
export const INITIAL_TIME_LIMIT_MS = 3000;
export const TIME_LIMIT_DECAY_MS = 150;
export const MINIMUM_TIME_LIMIT_MS = 800;

export interface AliveState {
  alive: boolean;
}

export function timeLimitMsForRound(round: number): number {
  const t = INITIAL_TIME_LIMIT_MS - (round - 1) * TIME_LIMIT_DECAY_MS;
  return Math.max(t, MINIMUM_TIME_LIMIT_MS);
}

export function nextAliveIndex(
  turnOrder: string[],
  players: Record<string, AliveState>,
  fromIndex: number
): number {
  const n = turnOrder.length;
  for (let step = 1; step <= n; step++) {
    const idx = (fromIndex + step) % n;
    if (players[turnOrder[idx]]?.alive) return idx;
  }
  return fromIndex;
}

export function aliveCount(players: Record<string, AliveState>): number {
  return Object.values(players).filter((p) => p.alive).length;
}

export function soleSurvivor(players: Record<string, AliveState>): string | null {
  const alive = Object.entries(players).filter(([, p]) => p.alive);
  return alive.length === 1 ? alive[0][0] : null;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npm test`
Expected: PASS — 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add functions/src/turnLogic.ts functions/src/turnLogic.test.ts
git commit -m "feat: add pure turn rotation and elimination logic for multiplayer rooms"
```

---

### Task A3: Stimulus generator (TDD)

**Files:**
- Create: `functions/src/stimulus.ts`
- Test: `functions/src/stimulus.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
// functions/src/stimulus.test.ts
import { generateStimulus, STROOP_COLORS } from "./stimulus";

describe("generateStimulus", () => {
  test("wordLabel and inkColor are never the same color", () => {
    const rng = sequence([0.1, 0.9]); // ink=RED(idx0), wordLabel picks from remaining pool
    const stimulus = generateStimulus(rng);
    expect(stimulus.wordLabel).not.toBe(stimulus.inkColor);
  });

  test("options contain all 4 colors exactly once", () => {
    const stimulus = generateStimulus(() => 0.5);
    expect(stimulus.options.slice().sort()).toEqual(STROOP_COLORS.slice().sort());
  });

  test("inkColor and wordLabel are always valid Stroop colors", () => {
    const stimulus = generateStimulus(() => 0.99);
    expect(STROOP_COLORS).toContain(stimulus.inkColor);
    expect(STROOP_COLORS).toContain(stimulus.wordLabel);
  });
});

function sequence(values: number[]): () => number {
  let i = 0;
  return () => values[Math.min(i++, values.length - 1)];
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npm test`
Expected: FAIL — `Cannot find module './stimulus'`

- [ ] **Step 3: Implement `functions/src/stimulus.ts`**

```ts
export const STROOP_COLORS = ["RED", "GREEN", "BLUE", "YELLOW"] as const;
export type StroopColorId = (typeof STROOP_COLORS)[number];

export interface Stimulus {
  wordLabel: StroopColorId;
  inkColor: StroopColorId;
  options: StroopColorId[];
}

export function generateStimulus(rng: () => number = Math.random): Stimulus {
  const inkColor = pick(STROOP_COLORS, [], rng);
  const wordLabel = pick(STROOP_COLORS, [inkColor], rng);
  const options = shuffle([...STROOP_COLORS], rng);
  return { wordLabel, inkColor, options };
}

function pick(pool: readonly StroopColorId[], exclude: StroopColorId[], rng: () => number): StroopColorId {
  const filtered = pool.filter((c) => !exclude.includes(c));
  const source = filtered.length > 0 ? filtered : pool;
  return source[Math.floor(rng() * source.length)];
}

function shuffle<T>(arr: T[], rng: () => number): T[] {
  const copy = [...arr];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npm test`
Expected: PASS — 3 new tests green (11 total).

- [ ] **Step 5: Commit**

```bash
git add functions/src/stimulus.ts functions/src/stimulus.test.ts
git commit -m "feat: add shared Stroop stimulus generator for multiplayer rounds"
```

---

### Task A4: Room types and Firestore accessors

**Files:**
- Create: `functions/src/types.ts`
- Create: `functions/src/roomRepo.ts`

- [ ] **Step 1: Create `functions/src/types.ts`**

```ts
import { StroopColorId } from "./stimulus";

export type RoomStatus = "waiting" | "playing" | "finished";

export interface RoomPlayerDoc {
  uid: string;
  displayName: string;
  avatarIndex: number;
  alive: boolean;
  order: number;
  joinedAt: number;
}

export interface StimulusDoc {
  wordLabel: StroopColorId;
  inkColor: StroopColorId;
  options: StroopColorId[];
}

export interface RoomDoc {
  code: string;
  status: RoomStatus;
  hostUid: string;
  players: Record<string, RoomPlayerDoc>;
  turnOrder: string[];
  turnIndex: number;
  round: number;
  stimulus: StimulusDoc | null;
  deadlineAtMs: number | null;
  winnerUid: string | null;
  createdAtMs: number;
}

export type ResolutionReason = "correct" | "wrong" | "timeout" | "disconnect";
```

- [ ] **Step 2: Create `functions/src/roomRepo.ts`**

```ts
import { getFirestore } from "firebase-admin/firestore";

export const ROOMS_COLLECTION = "rooms";
const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function roomsCol() {
  return getFirestore().collection(ROOMS_COLLECTION);
}

export function generateRoomCode(): string {
  let code = "";
  for (let i = 0; i < 5; i++) {
    code += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  }
  return code;
}
```

- [ ] **Step 3: Verify the project still builds**

Run: `cd functions && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add functions/src/types.ts functions/src/roomRepo.ts
git commit -m "feat: add multiplayer room types and Firestore collection helpers"
```

---

### Task A5: `resolveRound` — the single authority for turn resolution

This is the core function reused by `submitAnswer`, the timeout task, and the presence trigger. All three call this — nothing else is allowed to mutate `alive`, `turnIndex`, `status`, or `winnerUid`.

**Files:**
- Create: `functions/src/resolveRound.ts`
- Create: `functions/src/taskQueue.ts`

- [ ] **Step 1: Create `functions/src/taskQueue.ts`**

```ts
import { getFunctions } from "firebase-admin/functions";

export async function scheduleTimeoutCheck(roomId: string, round: number, delayMs: number): Promise<void> {
  const queue = getFunctions().taskQueue("resolveTimeout");
  const scheduleDelaySeconds = Math.max(1, Math.ceil((delayMs + 500) / 1000));
  await queue.enqueue({ roomId, round }, { scheduleDelaySeconds });
}
```

- [ ] **Step 2: Create `functions/src/resolveRound.ts`**

```ts
import { getFirestore } from "firebase-admin/firestore";
import { generateStimulus } from "./stimulus";
import { nextAliveIndex, soleSurvivor, timeLimitMsForRound } from "./turnLogic";
import { ResolutionReason, RoomDoc } from "./types";
import { roomsCol } from "./roomRepo";
import { scheduleTimeoutCheck } from "./taskQueue";

export async function resolveRound(
  roomId: string,
  actingUid: string | null,
  reason: ResolutionReason,
  roundExpected: number
): Promise<void> {
  const roomRef = roomsCol().doc(roomId);
  let scheduled: { round: number; deadlineAtMs: number } | null = null;

  await getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) return;
    const room = doc.data() as RoomDoc;

    // Stale call: the round already moved on (a race between timeout/answer/disconnect).
    if (room.status !== "playing" || room.round !== roundExpected) return;

    const players = { ...room.players };
    if (reason !== "correct" && actingUid) {
      players[actingUid] = { ...players[actingUid], alive: false };
    }

    const survivor = soleSurvivor(players);
    if (survivor) {
      tx.update(roomRef, {
        players,
        status: "finished",
        winnerUid: survivor,
        stimulus: null,
        deadlineAtMs: null,
      });
      return;
    }

    const nextIndex = nextAliveIndex(room.turnOrder, players, room.turnIndex);
    const nextRound = room.round + 1;
    const stimulus = generateStimulus();
    const deadlineAtMs = Date.now() + timeLimitMsForRound(nextRound);

    tx.update(roomRef, {
      players,
      turnIndex: nextIndex,
      round: nextRound,
      stimulus,
      deadlineAtMs,
    });
    scheduled = { round: nextRound, deadlineAtMs };
  });

  if (scheduled) {
    await scheduleTimeoutCheck(roomId, scheduled.round, scheduled.deadlineAtMs - Date.now());
  }
}
```

- [ ] **Step 3: Verify the project builds**

Run: `cd functions && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add functions/src/resolveRound.ts functions/src/taskQueue.ts
git commit -m "feat: add resolveRound as the single authority for elimination and turn advancement"
```

---

### Task A6: `createRoom` and `joinRoom` callables

**Files:**
- Modify: `functions/src/index.ts`

- [ ] **Step 1: Implement `createRoom` and `joinRoom`**

```ts
// functions/src/index.ts
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { generateRoomCode, roomsCol } from "./roomRepo";
import { RoomDoc } from "./types";

initializeApp();

export const createRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const displayName = String(request.data?.displayName ?? "Pilot").slice(0, 16);

  const code = generateRoomCode();
  const roomRef = roomsCol().doc();
  const room: RoomDoc = {
    code,
    status: "waiting",
    hostUid: uid,
    players: {
      [uid]: { uid, displayName, avatarIndex: 0, alive: true, order: 0, joinedAt: Date.now() },
    },
    turnOrder: [uid],
    turnIndex: 0,
    round: 0,
    stimulus: null,
    deadlineAtMs: null,
    winnerUid: null,
    createdAtMs: Date.now(),
  };
  await roomRef.set(room);
  return { roomId: roomRef.id, code };
});

export const joinRoom = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const code = String(request.data?.code ?? "").toUpperCase().trim();
  const displayName = String(request.data?.displayName ?? "Pilot").slice(0, 16);

  const snap = await roomsCol().where("code", "==", code).limit(1).get();
  if (snap.empty) throw new HttpsError("not-found", "Sala no encontrada.");
  const roomRef = snap.docs[0].ref;

  return getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(roomRef);
    const room = doc.data() as RoomDoc;
    if (room.status !== "waiting") throw new HttpsError("failed-precondition", "La partida ya empezó.");
    if (room.players[uid]) return { roomId: roomRef.id };
    if (Object.keys(room.players).length >= 4) throw new HttpsError("resource-exhausted", "Sala llena.");

    const order = Object.keys(room.players).length;
    const updatedPlayers = {
      ...room.players,
      [uid]: { uid, displayName, avatarIndex: 0, alive: true, order, joinedAt: Date.now() },
    };
    tx.update(roomRef, { players: updatedPlayers, turnOrder: [...room.turnOrder, uid] });
    return { roomId: roomRef.id };
  });
});
```

- [ ] **Step 2: Verify the project builds**

Run: `cd functions && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add functions/src/index.ts
git commit -m "feat: add createRoom and joinRoom callable functions"
```

---

### Task A7: `startGame`, `submitAnswer`, `resolveTimeout`, presence trigger

**Files:**
- Modify: `functions/src/index.ts`

- [ ] **Step 1: Append the remaining functions**

```ts
// functions/src/index.ts (append below joinRoom)
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onValueWritten } from "firebase-functions/v2/database";
import { generateStimulus } from "./stimulus";
import { timeLimitMsForRound } from "./turnLogic";
import { resolveRound } from "./resolveRound";
import { scheduleTimeoutCheck } from "./taskQueue";

export const startGame = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const roomId = String(request.data?.roomId ?? "");
  const roomRef = roomsCol().doc(roomId);

  await getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(roomRef);
    if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
    const room = doc.data() as RoomDoc;
    if (room.hostUid !== uid) throw new HttpsError("permission-denied", "Solo el host puede empezar.");
    if (room.turnOrder.length < 2) throw new HttpsError("failed-precondition", "Se necesitan al menos 2 jugadores.");

    const stimulus = generateStimulus();
    const deadlineAtMs = Date.now() + timeLimitMsForRound(1);
    tx.update(roomRef, { status: "playing", round: 1, turnIndex: 0, stimulus, deadlineAtMs });
  });

  await scheduleTimeoutCheck(roomId, 1, timeLimitMsForRound(1));
  return { started: true };
});

export const submitAnswer = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const roomId = String(request.data?.roomId ?? "");
  const selected = String(request.data?.selectedColor ?? "");

  const roomRef = roomsCol().doc(roomId);
  const doc = await roomRef.get();
  if (!doc.exists) throw new HttpsError("not-found", "Sala no existe.");
  const room = doc.data() as RoomDoc;

  const currentTurnUid = room.turnOrder[room.turnIndex];
  if (currentTurnUid !== uid) throw new HttpsError("permission-denied", "No es tu turno.");
  if (!room.stimulus || !room.deadlineAtMs) throw new HttpsError("failed-precondition", "No hay ronda activa.");
  if (Date.now() > room.deadlineAtMs) throw new HttpsError("deadline-exceeded", "Se acabó el tiempo.");

  const reason = selected === room.stimulus.inkColor ? "correct" : "wrong";
  await resolveRound(roomId, uid, reason, room.round);
  return { accepted: true, reason };
});

export const resolveTimeout = onTaskDispatched(async (req) => {
  const { roomId, round } = req.data as { roomId: string; round: number };
  const roomRef = roomsCol().doc(roomId);
  const doc = await roomRef.get();
  if (!doc.exists) return;
  const room = doc.data() as RoomDoc;
  if (room.status !== "playing" || room.round !== round) return; // ya se resolvió
  if (!room.deadlineAtMs || Date.now() < room.deadlineAtMs) return; // aún no vence

  const timedOutUid = room.turnOrder[room.turnIndex];
  await resolveRound(roomId, timedOutUid, "timeout", round);
});

export const onPresenceChanged = onValueWritten("presence/{roomId}/{uid}", async (event) => {
  const roomId = event.params.roomId;
  const uid = event.params.uid;
  const after = event.data.after.val() as { state: string } | null;
  if (!after || after.state !== "offline") return;

  const roomRef = roomsCol().doc(roomId);
  const doc = await roomRef.get();
  if (!doc.exists) return;
  const room = doc.data() as RoomDoc;
  if (room.status !== "playing" || !room.players[uid]?.alive) return;

  await resolveRound(roomId, uid, "disconnect", room.round);
});
```

- [ ] **Step 2: Verify the project builds**

Run: `cd functions && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add functions/src/index.ts
git commit -m "feat: add startGame, submitAnswer, timeout task and presence-based elimination"
```

---

### Task A8: Unit test `resolveRound` against the Firestore emulator

**Files:**
- Create: `functions/src/resolveRound.test.ts`
- Modify: `functions/package.json` (add emulator test script + `firebase-functions-test` devDependency)

- [ ] **Step 1: Add test tooling**

```bash
cd functions && npm install --save-dev firebase-functions-test @firebase/rules-unit-testing
```

- [ ] **Step 2: Write the failing test**

```ts
// functions/src/resolveRound.test.ts
import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import { resolveRound } from "./resolveRound";

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "stroopoverload-test",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
});

async function seedRoom(overrides: Record<string, unknown> = {}) {
  const context = testEnv.unauthenticatedContext();
  const db = context.firestore();
  const base = {
    code: "ABCDE",
    status: "playing",
    hostUid: "a",
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: true, order: 0, joinedAt: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAt: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAt: 0 },
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
  await db.collection("rooms").doc("room-1").set(base);
  return db;
}

test("wrong answer eliminates the acting player and advances the turn", async () => {
  await seedRoom();
  await resolveRound("room-1", "a", "wrong", 1);

  const db = testEnv.unauthenticatedContext().firestore();
  const after = (await db.collection("rooms").doc("room-1").get()).data()!;
  expect(after.players.a.alive).toBe(false);
  expect(after.turnIndex).toBe(1); // moved to "b"
  expect(after.round).toBe(2);
  expect(after.status).toBe("playing");
});

test("eliminating the second-to-last player finishes the game with a winner", async () => {
  await seedRoom({
    players: {
      a: { uid: "a", displayName: "A", avatarIndex: 0, alive: false, order: 0, joinedAt: 0 },
      b: { uid: "b", displayName: "B", avatarIndex: 0, alive: true, order: 1, joinedAt: 0 },
      c: { uid: "c", displayName: "C", avatarIndex: 0, alive: true, order: 2, joinedAt: 0 },
    },
    turnIndex: 1,
  });
  await resolveRound("room-1", "b", "timeout", 1);

  const db = testEnv.unauthenticatedContext().firestore();
  const after = (await db.collection("rooms").doc("room-1").get()).data()!;
  expect(after.status).toBe("finished");
  expect(after.winnerUid).toBe("c");
});

test("stale round numbers are ignored (already resolved by a racing trigger)", async () => {
  await seedRoom({ round: 2 });
  await resolveRound("room-1", "a", "wrong", 1); // roundExpected=1, but room is already at round 2

  const db = testEnv.unauthenticatedContext().firestore();
  const after = (await db.collection("rooms").doc("room-1").get()).data()!;
  expect(after.players.a.alive).toBe(true); // untouched
  expect(after.round).toBe(2);
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd functions && firebase emulators:exec --only firestore "npm test -- resolveRound.test.ts"`
Expected: FAIL initially if the Firestore emulator isn't running standalone — this command starts it for the test run. If `firebase-tools` isn't installed globally, run `npx firebase emulators:exec ...` instead.

- [ ] **Step 4: Confirm tests pass against the already-implemented `resolveRound`**

Run: same command as Step 3
Expected: PASS — 3 tests green. (`resolveRound` was implemented in Task A5; this task adds regression coverage for it.)

- [ ] **Step 5: Commit**

```bash
git add functions/src/resolveRound.test.ts functions/package.json functions/package-lock.json
git commit -m "test: cover resolveRound elimination, win detection and stale-round guard"
```

---

## PART B — Android client

### Task B1: Add Gradle dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entries to `gradle/libs.versions.toml`**

Add under `[versions]`:
```toml
turbine = "1.1.0"
```

Add under `[libraries]`:
```toml
firebase-database = { group = "com.google.firebase", name = "firebase-database" }
firebase-functions = { group = "com.google.firebase", name = "firebase-functions" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Add dependencies to `app/build.gradle.kts`**

In the `dependencies { }` block, next to the existing `firebase-firestore` line:
```kotlin
implementation(libs.firebase.database)
implementation(libs.firebase.functions)
```

Next to the existing `testImplementation(libs.kotlinx.coroutines.test)` line:
```kotlin
testImplementation(libs.turbine)
```

- [ ] **Step 3: Verify the project syncs and builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add Firebase Database/Functions and Turbine dependencies"
```

---

### Task B2: Multiplayer domain models (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/domain/multiplayer/MultiplayerRoom.kt`
- Test: `app/src/test/kotlin/com/softyorch/stroopoverload/domain/multiplayer/MultiplayerRoomTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.softyorch.stroopoverload.domain.multiplayer

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplayerRoomTest {
    @Test
    fun `isMyTurn is true only for the uid at turnIndex`() {
        val room = MultiplayerRoom(turnOrder = listOf("a", "b", "c"), turnIndex = 1)
        assertTrue(room.isMyTurn("b"))
        assertFalse(room.isMyTurn("a"))
        assertFalse(room.isMyTurn("c"))
    }

    @Test
    fun `currentTurnUid is null when turnOrder is empty`() {
        val room = MultiplayerRoom(turnOrder = emptyList())
        assertEquals(null, room.currentTurnUid)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoomTest"`
Expected: FAIL — `Unresolved reference: MultiplayerRoom`

- [ ] **Step 3: Implement the domain models**

```kotlin
package com.softyorch.stroopoverload.domain.multiplayer

import com.softyorch.stroopoverload.core.StroopColor

enum class RoomStatus { WAITING, PLAYING, FINISHED }

data class RoomPlayer(
    val uid: String,
    val displayName: String,
    val avatarIndex: Int = 0,
    val alive: Boolean = true,
    val order: Int = 0,
)

data class MultiplayerStimulus(
    val wordLabel: StroopColor,
    val inkColor: StroopColor,
    val options: List<StroopColor>,
) {
    val correctAnswer: StroopColor get() = inkColor
}

data class MultiplayerRoom(
    val roomId: String = "",
    val code: String = "",
    val status: RoomStatus = RoomStatus.WAITING,
    val hostUid: String = "",
    val players: List<RoomPlayer> = emptyList(),
    val turnOrder: List<String> = emptyList(),
    val turnIndex: Int = 0,
    val round: Int = 0,
    val stimulus: MultiplayerStimulus? = null,
    val deadlineAtMs: Long? = null,
    val winnerUid: String? = null,
) {
    val currentTurnUid: String? get() = turnOrder.getOrNull(turnIndex)
    fun isMyTurn(uid: String): Boolean = currentTurnUid == uid
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoomTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/softyorch/stroopoverload/domain/multiplayer/MultiplayerRoom.kt app/src/test/kotlin/com/softyorch/stroopoverload/domain/multiplayer/MultiplayerRoomTest.kt
git commit -m "feat: add multiplayer room domain models"
```

---

### Task B3: `MultiplayerRepository` interface + Firebase implementation

**Files:**
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/data/MultiplayerRepository.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/data/FirebaseMultiplayerRepository.kt`

- [ ] **Step 1: Define the interface**

```kotlin
// app/src/main/kotlin/com/softyorch/stroopoverload/data/MultiplayerRepository.kt
package com.softyorch.stroopoverload.data

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import kotlinx.coroutines.flow.Flow

interface MultiplayerRepository {
    suspend fun createRoom(displayName: String): Result<Pair<String, String>>
    suspend fun joinRoom(code: String, displayName: String): Result<String>
    suspend fun startGame(roomId: String): Result<Unit>
    suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit>
    fun observeRoom(roomId: String): Flow<MultiplayerRoom>
    fun trackPresence(roomId: String, uid: String)
}
```

- [ ] **Step 2: Implement it against Firebase**

```kotlin
// app/src/main/kotlin/com/softyorch/stroopoverload/data/FirebaseMultiplayerRepository.kt
package com.softyorch.stroopoverload.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerStimulus
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseMultiplayerRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) : MultiplayerRepository {

    override suspend fun createRoom(displayName: String): Result<Pair<String, String>> = runCatching {
        val result = functions.getHttpsCallable("createRoom").call(mapOf("displayName" to displayName)).await()
        val map = result.data as Map<*, *>
        (map["roomId"] as String) to (map["code"] as String)
    }

    override suspend fun joinRoom(code: String, displayName: String): Result<String> = runCatching {
        val data = mapOf("code" to code, "displayName" to displayName)
        val result = functions.getHttpsCallable("joinRoom").call(data).await()
        (result.data as Map<*, *>)["roomId"] as String
    }

    override suspend fun startGame(roomId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("startGame").call(mapOf("roomId" to roomId)).await()
        Unit
    }

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit> = runCatching {
        val data = mapOf("roomId" to roomId, "selectedColor" to selectedColor.name)
        functions.getHttpsCallable("submitAnswer").call(data).await()
        Unit
    }

    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = callbackFlow {
        val ref = firestore.collection("rooms").document(roomId)
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("MultiplayerRepo", "Room listener error: ${error.message}")
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            trySend(mapRoom(roomId, data))
        }
        awaitClose { registration.remove() }
    }

    override fun trackPresence(roomId: String, uid: String) {
        val presenceRef = database.getReference("presence/$roomId/$uid")
        val offlineValue = mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
        val onlineValue = mapOf("state" to "online", "lastChanged" to ServerValue.TIMESTAMP)
        presenceRef.onDisconnect().setValue(offlineValue)
        presenceRef.setValue(onlineValue)
    }

    private fun mapRoom(roomId: String, data: Map<String, Any?>): MultiplayerRoom {
        @Suppress("UNCHECKED_CAST")
        val playersMap = data["players"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        val players = playersMap.values.map { p ->
            RoomPlayer(
                uid = p["uid"] as? String ?: "",
                displayName = p["displayName"] as? String ?: "Pilot",
                avatarIndex = (p["avatarIndex"] as? Long)?.toInt() ?: 0,
                alive = p["alive"] as? Boolean ?: true,
                order = (p["order"] as? Long)?.toInt() ?: 0,
            )
        }.sortedBy { it.order }

        @Suppress("UNCHECKED_CAST")
        val stimulusMap = data["stimulus"] as? Map<String, Any?>
        val stimulus = stimulusMap?.let {
            @Suppress("UNCHECKED_CAST")
            val options = it["options"] as? List<String> ?: emptyList()
            MultiplayerStimulus(
                wordLabel = StroopColor.valueOf(it["wordLabel"] as String),
                inkColor = StroopColor.valueOf(it["inkColor"] as String),
                options = options.map(StroopColor::valueOf),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val turnOrder = data["turnOrder"] as? List<String> ?: emptyList()

        return MultiplayerRoom(
            roomId = roomId,
            code = data["code"] as? String ?: "",
            status = when (data["status"] as? String) {
                "playing" -> RoomStatus.PLAYING
                "finished" -> RoomStatus.FINISHED
                else -> RoomStatus.WAITING
            },
            hostUid = data["hostUid"] as? String ?: "",
            players = players,
            turnOrder = turnOrder,
            turnIndex = (data["turnIndex"] as? Long)?.toInt() ?: 0,
            round = (data["round"] as? Long)?.toInt() ?: 0,
            stimulus = stimulus,
            deadlineAtMs = data["deadlineAtMs"] as? Long,
            winnerUid = data["winnerUid"] as? String,
        )
    }
}
```

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/softyorch/stroopoverload/data/MultiplayerRepository.kt app/src/main/kotlin/com/softyorch/stroopoverload/data/FirebaseMultiplayerRepository.kt
git commit -m "feat: add MultiplayerRepository interface and Firebase-backed implementation"
```

---

### Task B4: `MultiplayerViewModel` (TDD with a fake repository)

**Files:**
- Create: `app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/FakeMultiplayerRepository.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerUiState.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerViewModel.kt`
- Test: `app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerViewModelTest.kt`

- [ ] **Step 1: Write the fake repository (test fixture, not a mock)**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.MultiplayerRepository
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeMultiplayerRepository : MultiplayerRepository {
    private val roomFlow = MutableSharedFlow<MultiplayerRoom>(replay = 1)

    var presenceTracked = false
        private set
    var submitAnswerCallCount = 0
        private set

    suspend fun emitRoom(room: MultiplayerRoom) = roomFlow.emit(room)

    override suspend fun createRoom(displayName: String): Result<Pair<String, String>> =
        Result.success("room-1" to "ABCDE")

    override suspend fun joinRoom(code: String, displayName: String): Result<String> =
        Result.success("room-1")

    override suspend fun startGame(roomId: String): Result<Unit> = Result.success(Unit)

    override suspend fun submitAnswer(roomId: String, selectedColor: StroopColor): Result<Unit> {
        submitAnswerCallCount++
        return Result.success(Unit)
    }

    override fun observeRoom(roomId: String): Flow<MultiplayerRoom> = roomFlow

    override fun trackPresence(roomId: String, uid: String) {
        presenceTracked = true
    }
}
```

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import app.cash.turbine.test
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomPlayer
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `createRoom moves to InRoom once the repository emits the room`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.state.test {
            assertEquals(MultiplayerUiState.Idle, awaitItem())

            viewModel.createRoom(uid = "host-1", displayName = "Neo")
            assertEquals(MultiplayerUiState.Connecting, awaitItem())
            dispatcher.scheduler.advanceUntilIdle()

            fake.emitRoom(
                MultiplayerRoom(
                    roomId = "room-1",
                    code = "ABCDE",
                    status = RoomStatus.WAITING,
                    hostUid = "host-1",
                    players = listOf(RoomPlayer(uid = "host-1", displayName = "Neo")),
                    turnOrder = listOf("host-1"),
                )
            )
            val inRoom = awaitItem() as MultiplayerUiState.InRoom
            assertEquals("room-1", inRoom.room.roomId)
            assertTrue(fake.presenceTracked)
        }
    }

    @Test
    fun `submitAnswer is ignored when it is not my turn`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "player-2", displayName = "Trinity")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                players = listOf(
                    RoomPlayer(uid = "player-1", displayName = "Neo"),
                    RoomPlayer(uid = "player-2", displayName = "Trinity"),
                ),
                turnOrder = listOf("player-1", "player-2"),
                turnIndex = 0,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fake.submitAnswerCallCount)
    }

    @Test
    fun `submitAnswer calls the repository when it is my turn`() = runTest {
        val fake = FakeMultiplayerRepository()
        val viewModel = MultiplayerViewModel(fake)

        viewModel.createRoom(uid = "player-1", displayName = "Neo")
        dispatcher.scheduler.advanceUntilIdle()
        fake.emitRoom(
            MultiplayerRoom(
                roomId = "room-1",
                status = RoomStatus.PLAYING,
                players = listOf(
                    RoomPlayer(uid = "player-1", displayName = "Neo"),
                    RoomPlayer(uid = "player-2", displayName = "Trinity"),
                ),
                turnOrder = listOf("player-1", "player-2"),
                turnIndex = 0,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.submitAnswer(StroopColor.RED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fake.submitAnswerCallCount)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.softyorch.stroopoverload.ui.screen.multiplayer.MultiplayerViewModelTest"`
Expected: FAIL — `Unresolved reference: MultiplayerViewModel` / `MultiplayerUiState`

- [ ] **Step 4: Implement `MultiplayerUiState`**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

sealed interface MultiplayerUiState {
    data object Idle : MultiplayerUiState
    data object Connecting : MultiplayerUiState
    data class InRoom(val room: MultiplayerRoom, val myUid: String) : MultiplayerUiState
    data class Error(val message: String) : MultiplayerUiState
}
```

- [ ] **Step 5: Implement `MultiplayerViewModel`**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.data.FirebaseMultiplayerRepository
import com.softyorch.stroopoverload.data.MultiplayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultiplayerViewModel(
    private val repository: MultiplayerRepository = FirebaseMultiplayerRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<MultiplayerUiState>(MultiplayerUiState.Idle)
    val state: StateFlow<MultiplayerUiState> = _state.asStateFlow()

    private var myUid: String = ""

    fun createRoom(uid: String, displayName: String) {
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.createRoom(displayName)
                .onSuccess { (roomId, _) -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo crear la sala.") }
        }
    }

    fun joinRoom(uid: String, code: String, displayName: String) {
        myUid = uid
        _state.value = MultiplayerUiState.Connecting
        viewModelScope.launch {
            repository.joinRoom(code, displayName)
                .onSuccess { roomId -> observeRoom(roomId) }
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo unir a la sala.") }
        }
    }

    fun startGame() {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        viewModelScope.launch {
            repository.startGame(current.room.roomId)
                .onFailure { _state.value = MultiplayerUiState.Error(it.message ?: "No se pudo iniciar la partida.") }
        }
    }

    fun submitAnswer(color: StroopColor) {
        val current = _state.value as? MultiplayerUiState.InRoom ?: return
        if (!current.room.isMyTurn(current.myUid)) return
        viewModelScope.launch {
            repository.submitAnswer(current.room.roomId, color)
        }
    }

    private fun observeRoom(roomId: String) {
        repository.trackPresence(roomId, myUid)
        viewModelScope.launch {
            repository.observeRoom(roomId).collect { room ->
                _state.value = MultiplayerUiState.InRoom(room, myUid)
            }
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.softyorch.stroopoverload.ui.screen.multiplayer.MultiplayerViewModelTest"`
Expected: PASS — 3 tests green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerUiState.kt app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerViewModel.kt app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/FakeMultiplayerRepository.kt app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerViewModelTest.kt
git commit -m "feat: add MultiplayerViewModel with turn-gated answer submission"
```

---

### Task B5: Compose screens — Lobby, Waiting Room, Game

**Files:**
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/LobbyScreen.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/WaitingRoomScreen.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerGameScreen.kt`
- Create: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerScreen.kt`

- [ ] **Step 1: `LobbyScreen.kt`**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LobbyScreen(
    onCreateRoom: (displayName: String) -> Unit,
    onJoinRoom: (code: String, displayName: String) -> Unit,
    errorMessage: String?,
) {
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("[ PARTIDA ONLINE ]", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Tu nombre de piloto") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onCreateRoom(displayName.ifBlank { "Pilot" }) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("CREAR SALA") }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(5) },
            label = { Text("Código de sala") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onJoinRoom(code, displayName.ifBlank { "Pilot" }) },
            enabled = code.length == 5,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("UNIRSE A SALA") }
        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

- [ ] **Step 2: `WaitingRoomScreen.kt`**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom

@Composable
fun WaitingRoomScreen(
    room: MultiplayerRoom,
    myUid: String,
    onStartGame: () -> Unit,
) {
    val isHost = room.hostUid == myUid
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("CÓDIGO: ${room.code}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Jugadores (${room.players.size}/4)")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(room.players) { player ->
                Text("• ${player.displayName}${if (player.uid == room.hostUid) " (host)" else ""}")
            }
        }
        if (isHost) {
            Button(
                onClick = onStartGame,
                enabled = room.players.size in 2..4,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("EMPEZAR PARTIDA") }
        } else {
            Text("Esperando a que el host empiece...")
        }
    }
}
```

- [ ] **Step 3: `MultiplayerGameScreen.kt`**

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.softyorch.stroopoverload.core.StroopColor
import com.softyorch.stroopoverload.domain.multiplayer.MultiplayerRoom
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus

@Composable
fun MultiplayerGameScreen(
    room: MultiplayerRoom,
    myUid: String,
    onColorTapped: (StroopColor) -> Unit,
) {
    val myTurn = room.isMyTurn(myUid)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            room.players.forEach { player ->
                val isTurn = player.uid == room.currentTurnUid
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTurn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(player.displayName, style = MaterialTheme.typography.labelMedium)
                    Text(if (player.alive) "VIVO" else "ELIMINADO", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (room.status) {
            RoomStatus.PLAYING -> {
                val stimulus = room.stimulus
                if (stimulus != null) {
                    Text(
                        text = stimulus.wordLabel.displayName,
                        style = MaterialTheme.typography.displayMedium,
                        color = stimulus.inkColor.composeColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        stimulus.options.forEach { option ->
                            Button(
                                onClick = { onColorTapped(option) },
                                enabled = myTurn,
                                colors = ButtonDefaults.buttonColors(containerColor = option.composeColor),
                            ) { Text(option.displayName, color = Color.Black) }
                        }
                    }
                    if (!myTurn) {
                        Spacer(Modifier.height(16.dp))
                        Text("Turno de ${room.players.firstOrNull { it.uid == room.currentTurnUid }?.displayName ?: "..."}")
                    }
                }
            }
            RoomStatus.FINISHED -> {
                val winner = room.players.firstOrNull { it.uid == room.winnerUid }
                Text(
                    text = if (room.winnerUid == myUid) "¡GANASTE!" else "Ganó ${winner?.displayName ?: "?"}",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            RoomStatus.WAITING -> Text("Esperando...")
        }

        Spacer(Modifier.weight(1f))
    }
}
```

- [ ] **Step 4: `MultiplayerScreen.kt`** (single container — avoids splitting the ViewModel across nav routes, which would lose state between lobby/waiting/game)

```kotlin
package com.softyorch.stroopoverload.ui.screen.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softyorch.stroopoverload.domain.multiplayer.RoomStatus

@Composable
fun MultiplayerScreen(myUid: String) {
    val viewModel: MultiplayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is MultiplayerUiState.Idle, is MultiplayerUiState.Connecting -> LobbyScreen(
            onCreateRoom = { name -> viewModel.createRoom(myUid, name) },
            onJoinRoom = { code, name -> viewModel.joinRoom(myUid, code, name) },
            errorMessage = null,
        )
        is MultiplayerUiState.Error -> LobbyScreen(
            onCreateRoom = { name -> viewModel.createRoom(myUid, name) },
            onJoinRoom = { code, name -> viewModel.joinRoom(myUid, code, name) },
            errorMessage = current.message,
        )
        is MultiplayerUiState.InRoom -> when (current.room.status) {
            RoomStatus.WAITING -> WaitingRoomScreen(
                room = current.room,
                myUid = myUid,
                onStartGame = { viewModel.startGame() },
            )
            RoomStatus.PLAYING, RoomStatus.FINISHED -> MultiplayerGameScreen(
                room = current.room,
                myUid = myUid,
                onColorTapped = { viewModel.submitAnswer(it) },
            )
        }
    }
}
```

- [ ] **Step 5: Verify the project builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/LobbyScreen.kt app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/WaitingRoomScreen.kt app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerGameScreen.kt app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerScreen.kt
git commit -m "feat: add Lobby, Waiting Room and Game Compose screens for online multiplayer"
```

---

### Task B6: Wire navigation and the HomeScreen entry point

**Files:**
- Modify: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/softyorch/stroopoverload/ui/NavGraph.kt`

- [ ] **Step 1: Add `onMultiplayer` param and a button to `HomeScreen.kt`**

In the function signature (`HomeScreen.kt:25-31`), add a parameter:
```kotlin
fun HomeScreen(
    profile: UserProfile = UserProfile(),
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onMultiplayer: () -> Unit,
    isReady: Boolean,
) {
```

Right after the existing `OutlinedButton` for `🏆 LEADERBOARD` (`HomeScreen.kt:143-151`), add:
```kotlin
Spacer(Modifier.height(12.dp))

OutlinedButton(
    onClick = onMultiplayer,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    shape = RoundedCornerShape(4.dp),
    modifier = Modifier.width(200.dp).heightIn(min = 44.dp)
) {
    Text("🌐 PARTIDA ONLINE", style = MaterialTheme.typography.labelLarge)
}
```

- [ ] **Step 2: Add the route and wire it in `NavGraph.kt`**

Add the route constant near the others (`NavGraph.kt:30-35`):
```kotlin
private const val ROUTE_MULTIPLAYER = "multiplayer"
```

Add the import:
```kotlin
import com.softyorch.stroopoverload.ui.screen.multiplayer.MultiplayerScreen
```

Pass `onMultiplayer` in the `HomeScreen` call (`NavGraph.kt:81-87`):
```kotlin
HomeScreen(
    profile = currentProfile,
    isReady = true,
    onPlay = { navController.navigate(ROUTE_GAME) },
    onLeaderboard = { navController.navigate(ROUTE_LEADERBOARD) },
    onProfile = { navController.navigate(ROUTE_PROFILE) },
    onMultiplayer = { navController.navigate(ROUTE_MULTIPLAYER) },
)
```

Add the new route's composable (after the `ROUTE_LEADERBOARD` block, `NavGraph.kt:134-136`):
```kotlin
composable(ROUTE_MULTIPLAYER) {
    MultiplayerScreen(myUid = authService.currentUid ?: "guest_local_0001")
}
```

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/HomeScreen.kt app/src/main/kotlin/com/softyorch/stroopoverload/ui/NavGraph.kt
git commit -m "feat: wire online multiplayer entry point into HomeScreen and navigation"
```

---

### Task B7: Full test suite + build verification

- [ ] **Step 1: Run the full Android unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (existing + `MultiplayerRoomTest`, `MultiplayerViewModelTest`).

- [ ] **Step 2: Run the full Functions test suite**

Run: `cd functions && npm test`
Expected: all tests green (`turnLogic`, `stimulus`, `resolveRound`).

- [ ] **Step 3: Full assemble**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Update `.claude/TASK_PROGRESS.md`**

Mark the online multiplayer feature complete through code — note that deploy (Blaze confirmation, RTDB provisioning, `firebase deploy`) remains a manual step for the user.

---

## What this plan deliberately does NOT do

- **Does not run `firebase deploy`.** That requires the Blaze plan and RTDB to already exist (see Manual Prerequisites) and pushes to shared infrastructure — an explicit, separate action for you to take.
- **Does not add matchmaking/random opponent search** — only room codes (create/join). Add it later as its own plan if wanted.
- **Does not persist multiplayer match results into `UserProfile`/XP/achievements.** `recordGameResult` in `FirebaseGameRepository` is single-player-shaped (`GameResult` has no multiplayer fields). Wiring multiplayer wins into XP/leaderboard is a follow-up task once this is proven to work end-to-end.
- **Does not handle a player rejoining a room after their own disconnect** — `onPresenceChanged` eliminates them permanently; reconnection/spectator-resume is out of scope here.
