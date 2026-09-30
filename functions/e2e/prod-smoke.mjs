// End-to-end smoke test against a DEPLOYED Firebase project (default: the one in
// ../../app/google-services.json). It talks to the real backend over plain REST --
// Auth, callable functions, Firestore and RTDB -- exactly as the app does, so it
// checks the deployed rules and functions, not the emulator.
//
// It creates throwaway accounts (e2e+<timestamp>-<n>@stroopoverload.test), plays
// short matches in every online mode, and deletes the accounts, their rooms and
// their profile docs at the end.
//
//   node e2e/prod-smoke.mjs            # run everything
//   node e2e/prod-smoke.mjs rules solo # run only some groups
//
// Groups: rules, solo, mistake, hotpotato, survival, leave.

import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { validateSoloRun } = require("../lib/profileScoring.js");

const services = JSON.parse(readFileSync(new URL("../../app/google-services.json", import.meta.url), "utf8"));
const PROJECT = services.project_info.project_id;
const RTDB = services.project_info.firebase_url;
const API_KEY = services.client[0].api_key[0].current_key;
const FUNCTIONS = `https://us-central1-${PROJECT}.cloudfunctions.net`;
const FIRESTORE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

const RUN_ID = Date.now();
const accounts = [];
const results = [];

// ---------------------------------------------------------------- tiny harness

function check(name, ok, detail = "") {
  results.push({ name, ok });
  console.log(`${ok ? "  PASS" : "  FAIL"}  ${name}${detail ? `  -- ${detail}` : ""}`);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitFor(label, fn, timeoutMs = 60_000, everyMs = 1000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    const v = await fn();
    if (v) return v;
    await sleep(everyMs);
  }
  throw new Error(`timed out waiting for ${label}`);
}

// ---------------------------------------------------------------- auth

async function identity(path, body) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/${path}?key=${API_KEY}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${path}: ${JSON.stringify(json.error)}`);
  return json;
}

async function newAccount(kind) {
  const n = accounts.length;
  const json =
    kind === "anonymous"
      ? await identity("accounts:signUp", { returnSecureToken: true })
      : await identity("accounts:signUp", {
          email: `e2e+${RUN_ID}-${n}@stroopoverload.test`,
          // Random per account: these are live production accounts while the run lasts, so a
          // password derivable from the run timestamp would let anyone sign in to them.
          password: randomUUID(),
          returnSecureToken: true,
        });
  const account = { uid: json.localId, token: json.idToken, kind, name: `E2E_${n}` };
  accounts.push(account);
  return account;
}

// ---------------------------------------------------------------- callables

async function call(account, name, data) {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${account.token}` },
    body: JSON.stringify({ data }),
  });
  const json = await res.json().catch(() => ({}));
  if (json.error) return { error: { status: json.error.status, reason: json.error.details?.reason, message: json.error.message } };
  return { result: json.result };
}

// ---------------------------------------------------------------- firestore

function decode(value) {
  if (value == null) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("nullValue" in value) return null;
  if ("timestampValue" in value) return value.timestampValue;
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(decode);
  if ("mapValue" in value) return decodeFields(value.mapValue.fields ?? {});
  return value;
}
const decodeFields = (fields) => Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, decode(v)]));

function encode(v) {
  if (v === null) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encode) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, encode(x)])) } };
}

async function getDoc(account, path) {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: `Bearer ${account.token}` } });
  const json = await res.json();
  if (!res.ok) return { error: json.error?.status ?? res.status };
  return { data: decodeFields(json.fields ?? {}) };
}

/** Merge-write like the Android SDK's set(map, SetOptions.merge()). */
async function mergeDoc(account, path, data) {
  const mask = Object.keys(data).map((k) => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join("&");
  const res = await fetch(`${FIRESTORE}/${path}?${mask}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${account.token}` },
    body: JSON.stringify({ fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, encode(v)])) }),
  });
  const json = await res.json();
  return res.ok ? { ok: true } : { error: json.error?.status ?? res.status };
}

async function deleteDoc(account, path) {
  await fetch(`${FIRESTORE}/${path}`, { method: "DELETE", headers: { Authorization: `Bearer ${account.token}` } });
}

// ---------------------------------------------------------------- rtdb presence

async function setPresence(account, roomId, state) {
  const res = await fetch(`${RTDB}/presence/${roomId}/${account.uid}.json?auth=${account.token}`, {
    method: "PUT",
    body: JSON.stringify({ state, lastChanged: { ".sv": "timestamp" } }),
  });
  return res.ok;
}

// ---------------------------------------------------------------- room helpers

const room = async (account, roomId) => (await getDoc(account, `rooms/${roomId}`)).data;

async function openRoom(mode, host, guests) {
  const created = await call(host, "createRoom", { displayName: host.name, mode });
  if (created.error) throw new Error(`createRoom(${mode}): ${JSON.stringify(created.error)}`);
  const { roomId, code } = created.result;
  for (const g of guests) {
    const joined = await call(g, "joinRoom", { code, displayName: g.name });
    if (joined.error) throw new Error(`joinRoom: ${JSON.stringify(joined.error)}`);
  }
  for (const p of [host, ...guests]) await setPresence(p, roomId, "online");
  const started = await call(host, "startGame", { roomId });
  if (started.error) throw new Error(`startGame: ${JSON.stringify(started.error)}`);
  const playing = await waitFor(`${mode} room playing`, async () => {
    const r = await room(host, roomId);
    return r?.status === "playing" ? r : null;
  }, 30_000);
  return { roomId, code, playing };
}

const byUid = (list) => Object.fromEntries(list.map((a) => [a.uid, a]));

// ---------------------------------------------------------------- groups

async function rulesGroup() {
  console.log("\n[rules] users/{uid} write rules");
  const me = await newAccount("email");
  const other = await newAccount("email");

  const allowed = await mergeDoc(me, `users/${me.uid}`, {
    userId: me.uid, nickname: me.name, uniqueName: `@e2e-${me.uid.slice(-4)}`, isAnonymous: false, avatarIndex: 0, profileCreated: true,
  });
  check("own profile create with non-scoring fields is allowed", allowed.ok === true, JSON.stringify(allowed));

  const points = await mergeDoc(me, `users/${me.uid}`, { points: 999999 });
  check("client write to points is denied", points.error === "PERMISSION_DENIED", JSON.stringify(points));

  const adFree = await mergeDoc(me, `users/${me.uid}`, { isAdFree: true });
  check("client write to isAdFree is denied", adFree.error === "PERMISSION_DENIED", JSON.stringify(adFree));

  const mixed = await mergeDoc(me, `users/${me.uid}`, { nickname: "x", experience: 5 });
  check("a merge mixing allowed + scoring fields is denied as a whole", mixed.error === "PERMISSION_DENIED", JSON.stringify(mixed));

  const foreign = await mergeDoc(me, `users/${other.uid}`, { nickname: "hijack" });
  check("writing another user's profile is denied", foreign.error === "PERMISSION_DENIED", JSON.stringify(foreign));

  const career = await mergeDoc(me, `users/${me.uid}`, { careerStats: { totalGamesPlayed: 1 }, achievements: {} });
  check("careerStats/achievements sync (syncProgressToCloud) is still allowed", career.ok === true, JSON.stringify(career));
}

async function soloGroup() {
  console.log("\n[solo] submitSoloRun");
  const guest = await newAccount("anonymous");
  const player = await newAccount("email");

  const run = { mode: "ENDLESS", correctHits: 10, totalRounds: 11, survivalMs: 15_000, finalScore: 100, achievementIds: [], winStreak: 3 };
  const rejection = validateSoloRun(run);
  if (rejection) throw new Error(`test run is not valid for this build: ${rejection}`);

  const anon = await call(guest, "submitSoloRun", run);
  check("anonymous run is rejected (ANONYMOUS)", anon.error?.reason === "ANONYMOUS", JSON.stringify(anon));

  const bogus = await call(player, "submitSoloRun", { ...run, correctHits: 50, totalRounds: 11 });
  check("impossible run is rejected (INVALID_RUN)", bogus.error?.reason === "INVALID_RUN", JSON.stringify(bogus));

  const ok = await call(player, "submitSoloRun", run);
  check("valid run is accepted and returns scoring", ok.result?.matchesPlayed === 1 && ok.result?.highScore === 100, JSON.stringify(ok));

  const profile = (await getDoc(player, `users/${player.uid}`)).data;
  check("server wrote the profile (matchesPlayed=1, highScore=100)", profile?.matchesPlayed === 1 && profile?.highScore === 100, JSON.stringify(profile));

  // The first run also paid the new-high-score bonus, so compare two later runs
  // that are identical except for the forged achievement id.
  const control = await call(player, "submitSoloRun", run);
  const bogusAch = await call(player, "submitSoloRun", { ...run, achievementIds: ["not_a_real_achievement"] });
  check("unknown achievement id pays no achievement XP", bogusAch.result && bogusAch.result.xpAwarded === control.result?.xpAwarded,
    `control=${control.result?.xpAwarded} forged=${bogusAch.result?.xpAwarded}`);

  // The client retries a queued run until it gets an answer; a repeat must pay nothing.
  const runId = `e2e-${RUN_ID}-retry`;
  const once = await call(player, "submitSoloRun", { ...run, runId });
  const twice = await call(player, "submitSoloRun", { ...run, runId });
  check("a run retried with the same runId is applied once",
    once.result && twice.result?.xpAwarded === 0 && twice.result?.matchesPlayed === once.result.matchesPlayed,
    `once=${once.result?.matchesPlayed} twice=${twice.result?.matchesPlayed} xp=${twice.result?.xpAwarded}`);

  const badId = await call(player, "submitSoloRun", { ...run, runId: "bad id!" });
  check("a malformed runId is rejected (INVALID_RUN)", badId.error?.reason === "INVALID_RUN", JSON.stringify(badId));

  const cleared = await mergeDoc(player, `users/${player.uid}`, { recentRunIds: [] });
  check("client cannot clear recentRunIds", cleared.error === "PERMISSION_DENIED", JSON.stringify(cleared));
}

async function mistakeGroup() {
  console.log("\n[mistake] turn order, STALE_ROUND, disconnect, payout");
  const a = await newAccount("email");
  const b = await newAccount("email");
  const players = byUid([a, b]);
  const { roomId, playing } = await openRoom("mistake", a, [b]);
  check("room reached playing via beginRound task", playing.status === "playing" && playing.round >= 1, `round=${playing.round}`);

  let r = playing;
  const holder = players[r.turnOrder[r.turnIndex]];
  const idle = holder === a ? b : a;

  const notMine = await call(idle, "submitAnswer", { roomId, selectedColor: "RED", round: r.round });
  check("answer out of turn is rejected", notMine.error?.status === "PERMISSION_DENIED", JSON.stringify(notMine));

  const stale = await call(holder, "submitAnswer", { roomId, selectedColor: r.stimulus.inkColor, round: r.round + 1 });
  check("answer for the wrong round is rejected (STALE_ROUND)", stale.error?.reason === "STALE_ROUND", JSON.stringify(stale));

  const good = await call(holder, "submitAnswer", { roomId, selectedColor: r.stimulus.inkColor, round: r.round });
  check("correct answer with round is accepted", good.result?.reason === "correct", JSON.stringify(good));

  const again = await call(holder, "submitAnswer", { roomId, selectedColor: r.stimulus.inkColor, round: r.round });
  check("second tap on the same round is rejected, not scored on the next stimulus",
    again.error != null && again.result == null, JSON.stringify(again));

  r = await room(a, roomId);
  check("round advanced and turn passed", r.round === playing.round + 1, `round=${r.round} turnIndex=${r.turnIndex}`);

  // The idle player drops: onPresenceChanged eliminates them, leaving a sole survivor.
  await setPresence(idle, roomId, "offline");
  const finished = await waitFor("mistake room finished after disconnect", async () => {
    const x = await room(a, roomId);
    return x?.status === "finished" ? x : null;
  }, 30_000);
  check("disconnect eliminated the idle player, other one wins", finished.winnerUid === holder.uid, `winner=${finished.winnerUid}`);
  check("placements computed", finished.players[holder.uid].placement === 1 && finished.players[idle.uid].placement === 2);

  const settled = await waitFor("onRoomFinished payout", async () => {
    const x = await room(a, roomId);
    return x?.awardsAppliedAtMs != null ? x : null;
  }, 30_000);
  check("onRoomFinished marked the room as paid (awardsAppliedAtMs)", settled.awardsAppliedAtMs != null);

  const winnerProfile = (await getDoc(holder, `users/${holder.uid}`)).data;
  check("winner profile: matchesWon=1, points>0",
    winnerProfile?.matchesWon === 1 && (winnerProfile?.points ?? 0) > 0, JSON.stringify(winnerProfile));

  const loserProfile = (await getDoc(idle, `users/${idle.uid}`)).data;
  check("a 0-score loser still gets the match counted (matchesPlayed=1, matchesLost=1)",
    loserProfile?.matchesPlayed === 1 && loserProfile?.matchesLost === 1, JSON.stringify(loserProfile ?? null));
}

async function hotPotatoGroup() {
  console.log("\n[hot_potato] hidden bomb, re-prompt on wrong answer");
  const a = await newAccount("email");
  const b = await newAccount("email");
  const players = byUid([a, b]);
  const { roomId, playing } = await openRoom("hot_potato", a, [b]);

  const bomb = await getDoc(a, `rooms/${roomId}/private/bomb`);
  check("bomb doc is not readable by players", bomb.error === "PERMISSION_DENIED", JSON.stringify(bomb));

  const holder = players[playing.turnOrder[playing.turnIndex]];
  const wrongColor = playing.stimulus.options.find((c) => c !== playing.stimulus.inkColor);
  const wrong = await call(holder, "submitAnswer", { roomId, selectedColor: wrongColor, round: playing.round });
  const afterWrong = await room(a, roomId);
  check("wrong answer re-prompts the same holder with a new round",
    wrong.result?.reason === "wrong" && afterWrong.turnOrder[afterWrong.turnIndex] === holder.uid && afterWrong.round === playing.round + 1,
    `reason=${wrong.result?.reason} round=${afterWrong.round}`);

  const retry = await call(holder, "submitAnswer", { roomId, selectedColor: afterWrong.stimulus.inkColor, round: afterWrong.round });
  check("the same player can answer again on the new round", retry.result?.reason === "correct", JSON.stringify(retry));

  const finished = await waitFor("bomb explosion (15-30 s)", async () => {
    const x = await room(a, roomId);
    return x?.status === "finished" ? x : null;
  }, 60_000, 2000);
  check("bomb went off and the match finished with a winner", finished.winnerUid != null, `winner=${finished.winnerUid}`);
}

async function survivalGroup() {
  console.log("\n[solo_survival] own stimulus per player, STALE_ROUND, bust");
  const a = await newAccount("email");
  const b = await newAccount("email");
  const { roomId, playing } = await openRoom("solo_survival", a, [b]);

  const meA = playing.players[a.uid];
  check("each player has their own stimulus", meA.soloStimulus != null && playing.players[b.uid].soloStimulus != null);

  const stale = await call(a, "submitAnswer", { roomId, selectedColor: meA.soloStimulus.inkColor, round: (meA.soloRound ?? 0) + 1 });
  check("answer for the wrong soloRound is rejected (STALE_ROUND)", stale.error?.reason === "STALE_ROUND", JSON.stringify(stale));

  const good = await call(a, "submitAnswer", { roomId, selectedColor: meA.soloStimulus.inkColor, round: meA.soloRound ?? 0 });
  check("correct answer accepted", good.result?.reason === "correct", JSON.stringify(good));

  const dup = await call(a, "submitAnswer", { roomId, selectedColor: meA.soloStimulus.inkColor, round: meA.soloRound ?? 0 });
  const afterDup = await room(a, roomId);
  check("double tap does not bust the player (the old 3-in-4 bug)", dup.error != null && afterDup.players[a.uid].alive === true, JSON.stringify(dup));

  // A leads on points, then busts: B, still standing with 0 points, is the winner and must
  // be ranked first (it used to be ranked by score alone, paying the loser more).
  const aStim = afterDup.players[a.uid].soloStimulus;
  const wrong = aStim.options.find((c) => c !== aStim.inkColor);
  await call(a, "submitAnswer", { roomId, selectedColor: wrong, round: afterDup.players[a.uid].soloRound ?? 0 });
  const finished = await waitFor("solo_survival finished (last survivor)", async () => {
    const x = await room(a, roomId);
    return x?.status === "finished" ? x : null;
  }, 30_000);
  check("wrong answer busted A and B wins as last survivor", finished.winnerUid === b.uid, `winner=${finished.winnerUid}`);
  check("the survivor is placed first even with fewer points",
    finished.players[b.uid].placement === 1 && finished.players[a.uid].placement === 2,
    `b=${finished.players[b.uid].placement} a=${finished.players[a.uid].placement}`);
}

async function leaveGroup() {
  console.log("\n[leave] leaving a waiting room");
  const host = await newAccount("email");
  const guest = await newAccount("email");
  const created = await call(host, "createRoom", { displayName: host.name, mode: "mistake" });
  const { roomId, code } = created.result;
  await call(guest, "joinRoom", { code, displayName: guest.name });

  const hostLeft = await call(host, "leaveRoom", { roomId });
  const afterHost = await room(guest, roomId);
  check("the host leaving hands the room to the guest",
    hostLeft.result?.left === true && afterHost?.hostUid === guest.uid && afterHost?.turnOrder?.length === 1,
    JSON.stringify({ hostUid: afterHost?.hostUid, turnOrder: afterHost?.turnOrder }));

  await call(guest, "leaveRoom", { roomId });
  const probe = await newAccount("anonymous");
  const rejoin = await call(probe, "joinRoom", { code, displayName: "probe" });
  check("the last player leaving deletes the room", rejoin.error?.reason === "ROOM_NOT_FOUND", JSON.stringify(rejoin));
}

// ---------------------------------------------------------------- cleanup

async function cleanup() {
  console.log("\n[cleanup]");
  for (const acc of accounts) {
    try {
      await call(acc, "deleteMyMultiplayerData", {});
      await deleteDoc(acc, `users/${acc.uid}`);
      await identity("accounts:delete", { idToken: acc.token });
    } catch (err) {
      console.log(`  WARN  cleanup failed for ${acc.uid}: ${err.message}`);
    }
  }
  console.log(`  deleted ${accounts.length} test accounts`);
}

// ---------------------------------------------------------------- main

const GROUPS = { rules: rulesGroup, solo: soloGroup, mistake: mistakeGroup, hotpotato: hotPotatoGroup, survival: survivalGroup, leave: leaveGroup };
const selected = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(GROUPS);

console.log(`prod smoke against ${PROJECT} (run ${RUN_ID}): ${selected.join(", ")}`);
try {
  for (const g of selected) {
    try {
      await GROUPS[g]();
    } catch (err) {
      check(`[${g}] group crashed`, false, err.message);
    }
  }
} finally {
  await cleanup();
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
