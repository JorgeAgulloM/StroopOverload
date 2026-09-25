# StroopOverload MVP — Task Progress

**Stack:** Android (Kotlin + Jetpack Compose + Firebase)
**Previous stack (deleted):** Flutter + Flame — wrong platform, scrapped

## Resume instructions
If session interrupted: read this file, check current file tree, resume from first unchecked task.
Working directory: `C:\Users\Jorge\Proyectos\StroopOverload`

---

## Task Status

- [x] **Task 1** — Android Compose project scaffold (Gradle KTS, AGP 8.10, Compose BOM 2025.06)
- [x] **Task 2** — Domain models: StroopColor, StroopStimulus, GameResult, UserProfile, Achievement
- [x] **Task 3** — IncongruenceEngine (deterministic conflict matrix, seeded Random for tests)
- [x] **Task 4** — GameState sealed interface + GameViewModel (StateFlow, coroutine timer)
- [x] **Task 5** — Compose UI: HomeScreen, GameScreen (4-quadrant), GameOverScreen, LeaderboardScreen
- [x] **Task 6** — Firebase: AuthService, FirebaseGameRepository (batched writes, leaderboard)
- [x] **Task 7** — Navigation: StroopNavGraph (NavController, route constants)
- [x] **Task 8** — Unit tests: IncongruenceEngine, StroopStimulus, GameResult
- [ ] **Task 9** — Download gradle-wrapper.jar + run `./gradlew build` to verify compilation
- [ ] **Task 10** — Add google-services.json from Firebase console
- [ ] **Task 11** — Share intent (Intent.ACTION_SEND with score text)
- [ ] **Task 12** — Audio playback (SoundPool or MediaPlayer on correct/wrong tap)

---

## Design Decisions

- **No Flame** — Compose Canvas + coroutine timer replaces game loop. Timer ticks every 16ms via `delay()` in viewModelScope.
- **GameViewModel owns FSM** — StateFlow<GameState> replaces Flutter's StreamController pattern.
- **Audio in res/raw/** — Android resource system, not assets/audio/. Files: red.mp3, green.mp3, blue.mp3, yellow.mp3 (placeholders).
- **Quadrants as Box+clickable** — No custom Canvas component needed; Compose layout handles 2×2 grid.
- **TimerBar as lerp** — `lerp(NeonRed, NeonGreen, progress)` replaces Flame's manual paint.

---

## Blockers / Manual Steps

- **CRITICAL — Task 10**: `app/google-services.json` must be downloaded from Firebase console (package: `com.softyorch.stroopoverload`) and placed at `app/google-services.json`.
- **Task 9**: Need `gradle/wrapper/gradle-wrapper.jar` to run gradlew. Either download Android Studio project or `./gradlew wrapper` from a machine with Gradle installed.
- **Audio placeholders**: `app/src/main/res/raw/*.mp3` are placeholder files — real voice recordings needed before ship.

---

## Last completed step

Full scaffold created (Tasks 1–8). Not compiled yet — gradle-wrapper.jar missing.

---

## Sub-task: /impeccable audit → fix all findings (2026-07-02)

Score 13/20 (Acceptable). Anti-patterns clean (4/4) — no AI slop tells.

- **P1** Muted (#686880) text fails WCAG AA: 3.76:1 vs Background, 3.44:1 vs Surface (need 4.5:1). Used ~15+ places across every screen.
- **P2** HomeScreen profile badge (top-left, clickable) touch target ~32dp, below 44dp minimum.
- **P2** Fixed-height buttons (`.height(Xdp)`) across Home/Auth/GameOver/Profile screens risk clipping text under large system font scale.
- **P2** Hardcoded Neon* color constants → routed through `MaterialTheme.colorScheme.*` where a real M3 role exists (primary=Cyan, secondary=Magenta, tertiary=Green, error=Red, background/surface/surfaceVariant/onBackground/onSurface, outline=TechBorder added to Theme.kt). Left raw where no M3 role fits: NeonYellow, TechAccent, CyberPurple, Muted, CyberDark (M3 ColorScheme has no "warning/gold/muted" slot — forcing them in would be wrong, not fixed). Also left raw: `color = Background` used as text-on-filled-button foreground (value-equal to onPrimary but wrong role when container is secondary/tertiary — semantically misleading to alias). Done across all 6 screens + Theme.kt. Build green.
- **P3** `ProfileScreen`: `state.achievements.chunked(2)` — fixed, hoisted `remember` above LazyColumn.
- P3 still skipped: GameScreen hardcoded `fontSize = 46.sp` on stimulus text — no existing typography token matches (closest is headlineLarge 40sp), changing it alters the core gameplay screen's visual weight for a cosmetic token-consistency nit. Declined, said why.

### Steps
- [x] Audit: read theme + 6 screens, ran detect.mjs (0 hits — web detector, not Kotlin-aware)
- [x] Fix Muted color value → Color.kt
- [x] Fix HomeScreen profile badge touch target
- [x] Fix fixed-height buttons → heightIn(min=) in HomeScreen, AuthScreen, GameOverScreen, ProfileScreen (also bumped ProfileScreen Save/Discard from 40dp→44dp, same class of issue)
- [x] Fix ProfileScreen chunked() memoization (hoisted `remember` above LazyColumn — first attempt placed it inside LazyListScope which isn't a composable context, caught by build)
- [x] Build verify — `compileDevDebugKotlin` BUILD SUCCESSFUL
- [x] Report to user

DONE. All P1/P2 (except hardcoded-color-tokens, deliberately skipped) and cheap P3 applied. Nothing uncommitted-risk left open. Safe to delete this section once user confirms.

---

## Sub-task: rebalance achievements (2026-07-03)

User: achievements given away too easily, wants real progression + minimum difficulty floor.

**Root cause found**: `GameViewModel.onColorTapped` never counts the round that ends the run (the miss) into `totalRounds`/`correctHits` — only correct taps increment them. So `GameResult.won` (needs ≥70% accuracy) and `isFlawless` (needs 100%) are structurally guaranteed true whenever a run reaches 5+ correct hits. Accuracy in this game is currently always 100%. NOT fixed — asked user via AskUserQuestion (core-fix vs thresholds-only), got no response after 60s, proceeded with the safe unambiguous half (threshold rebalance) and left the core bug flagged for explicit go-ahead (it ripples into leaderboard wins stat + GameOverScreen "VICTORY/OVERLOAD" copy).

**What changed** (`AchievementDefinitions.kt` + `AchievementEngine.kt`):
- `first_blood`: was `career.totalCorrectHits >= 1` (one lucky tap, ever) → now `game.correctHits >= 5` in a single run.
- Wins track (5/15/30/50/100/200) → doubled (10/30/60/100/200/400) — compensates for "win" being cheap under the core bug.
- Score track (single-run, real signal): centurion 1000→2500, 3k→5000, titan→7500, overlord→10500, god→14000 pts. Based on actual scoring formula (100/hit + streak bonus capped at 100/hit): cumulative score after n hits ≈ 1550+(n-10)×200 for n>10. New god tier ≈73 consecutive correct hits, near the 800ms timer floor.
- Flawless track (1/5/15/30 reps) → (3/10/30/60) — individual bar still easy (core bug), compensated with far more career reps required.
- Survival times (30/50/75/100s) — untouched, already properly hard given timer decay to 800ms floor.
- Win streaks (5/10/20) → (8/15/25). `cyber_veteran` (25→35 hardest-difficulty wins).
- Games-played track (10/25/50/100/200/500) — untouched, already a sane curve.

Build + `testDevDebugUnitTest` green, no test file changes needed (existing tests only assert on the untouched games-played track).

DONE, not committed. Open item: whether to fix the core round-counting bug (user's call, asked, no answer yet).

---

## Sub-task: Online multiplayer 1-4 players (2026-07-03, IN PROGRESS)

**Branch:** `feature/online-multiplayer` (checked out from `master`; master has lots of unrelated uncommitted work from the sub-tasks above — never touched, never `git add -A`, only exact paths per commit).

**Plan:** `docs/plans/2026-07-03-online-multiplayer.md` — full spec, all task code blocks, "what this plan does NOT do" section.

**Manual prereqs the user must do before deploy (not automated):** upgrade Firebase project `stroopoverload-softyorch` to Blaze plan; provision Realtime Database (doesn't exist yet); re-download `google-services.json` after RTDB provisioning. Nothing in this work runs `firebase deploy`.

**Execution mode:** subagent-driven (`mobiai-mobile-executing-plans-with-subagents`) — fresh implementer subagent per task, then spec-compliance reviewer, then code-quality reviewer, fix loop between each. Task tool IDs #1-#15 track A1-A8 (backend)/B1-B7 (Android).

### Completed (implemented + spec-reviewed + quality-reviewed + fixes applied)
- [x] **A1** — Functions scaffold: firebase.json, .firebaserc (project stroopoverload-softyorch), firestore.rules (server-authoritative, `allow write: if false`), firestore.indexes.json, database.rules.json, functions/{package.json,tsconfig.json,jest.config.js,src/index.ts}. Fix round: added predeploy build hook, tightened RTDB presence `.read` rule (was readable by any authenticated user for any room — now owner-only, matches the fact nothing in the client actually reads others' presence), added `private:true`, ignored emulator debug logs. Commits: 3b2f662, 8d86ef9, e615455.
- [x] **A2** — `functions/src/turnLogic.ts` (timeLimitMsForRound, nextAliveIndex, aliveCount, soleSurvivor) + tests. Fix round: negative-fromIndex modulo bug (JS `%` isn't true modulo — normalized), added missing all-dead-fallback/empty/single-player test coverage (now 100% branch coverage). Commits: b09dc96, 78c4e06, afd24e2.
- [x] **A3** — `functions/src/stimulus.ts` (generateStimulus, STROOP_COLORS, mirrors Kotlin IncongruenceEngine/StroopStimulus invariants) + tests incl. 200-iter property test. Fix round: `pick()`'s silent fallback (would produce a congruent word/ink stimulus on misuse) now throws instead; added seeded-reproducibility test (critical since same stimulus broadcasts to up to 4 devices). Commits: 95396ef, 498c081.
- [x] **A4** — `functions/src/types.ts` (RoomDoc, RoomPlayerDoc, StimulusDoc, ResolutionReason) + `functions/src/roomRepo.ts` (roomsCol, generateRoomCode). Fix round: `StimulusDoc` aliased to `Stimulus` (was hand-duplicated, would silently drift), added `readonly` per project immutability convention, renamed `joinedAt`→`joinedAtMs`, typed `roomsCol()` return as `CollectionReference<RoomDoc>`, **added `findJoinableRoomByCode`/`generateUniqueRoomCode`** (original plan's naive `where("code","==",code)` had no `status=="waiting"` filter and no collision guard — real bug caught before A6 used it). Commits: 6efb004, 54af45c.

- [x] **A5** — `functions/src/resolveRound.ts` (single authority for elimination/turn-advance/win-detection, reused by submitAnswer/timeout/presence-disconnect) + `functions/src/taskQueue.ts` (scheduleTimeoutCheck via Cloud Tasks). Return type later changed `Promise<void>` → `Promise<boolean>` (`applied`) so callers can tell a no-op from a real state change (see A7). Two real bugs caught+fixed by quality review before this closed: (1) unvalidated `actingUid` could corrupt a player's Firestore record via spreading `undefined` — now guarded; (2) a bystander (non-turn-holder) disconnecting was incorrectly stealing the active player's in-progress turn — now only advances the round when the eliminated/scoring player was actually the current turn-holder (`wasCurrentTurnPlayer`), confirmed via mutation-testing (reverted the guard, watched the test fail, restored it). Commits: f9986c6, 35d1b9f, fe03302, 41baf04 (return-type change).
- [x] **A6** — `createRoom`/`joinRoom` callables in functions/src/index.ts, using A4's `generateUniqueRoomCode()`/`findJoinableRoomByCode()` helpers (not a hand-rolled query). Fix round: empty/whitespace `displayName` now trims+falls back to "Pilot"; `code` format-validated (5-char alphabet regex) before touching Firestore; unexpected errors logged via `console.error` while expected `HttpsError`s pass through unchanged; added `functions/src/index.test.ts` with real coverage (auth rejection, success shape, idempotent rejoin). Commits: 2322650, c519638.
- [x] **A7** — `startGame`, `submitAnswer`, `resolveTimeout` (onTaskDispatched, Cloud Tasks), `onPresenceChanged` (RTDB `onValueWritten` trigger). Fix round: `submitAnswer` now checks `resolveRound`'s `applied` return and throws `deadline-exceeded` instead of falsely telling a client "accepted:true" when their answer raced against a timeout that resolved first; `resolveTimeout` logs+rethrows (with `retryConfig: maxAttempts 5`) so Cloud Tasks retries a real failure; `onPresenceChanged` logs but swallows (RTDB triggers don't auto-retry, and the redundant per-round timeout self-heals). All of this now has real test coverage in `index.test.ts`, including two tests added specifically to catch a revert of the try/catch/rethrow-vs-swallow behavior (mutation-tested). Commits: a6d4d85, 41baf04, ab144a7, d3b5c47.
- [x] **A8** — `resolveRound.test.ts` against the real Firestore emulator (`@firebase/rules-unit-testing` + `firebase-functions-test`). Required `functions/package.json`'s `"test"` script to wrap `firebase emulators:exec --only firestore "jest"` (plain `jest` doesn't start the emulator) and JDK 21+ on PATH (documented in `functions/README.md`) since `firebase-tools` refuses JDK 17. Commits: 63e71de, da6f54c.

**Backend (Part A) is fully done.** `functions/` test suite: 49/49 passing across 4 suites (turnLogic, stimulus, resolveRound, index). `npm run build` clean.

- [x] **B1** — Gradle deps: firebase-database, firebase-functions, turbine. `libs.versions.toml` had no pre-existing uncommitted changes; `app/build.gradle.kts` DID (product flavors/buildConfig from unrelated prior work) — first commit accidentally swept those in, caught by spec review, fixed via `git reset --soft` + reconstructing a deps-only version, then restoring the flavor changes as still-uncommitted. Commit: 84cfe7e.
- [x] **B2** — `domain/multiplayer/MultiplayerRoom.kt` (RoomStatus, RoomPlayer, MultiplayerStimulus, MultiplayerRoom) + test. Fix round: `RoomStatus` enum values are uppercase (WAITING/PLAYING/FINISHED) but Firestore sends lowercase strings — added `fromFirestoreValue(raw)` safe factory so the next task's mapper can't crash on this (exact bug class already bit this codebase once). Added `player(uid)` lookup helper. Commits: 0756218, 912a33b.
- [x] **B3** — `data/MultiplayerRepository.kt` (interface) + `data/FirebaseMultiplayerRepository.kt` (Firebase Functions/Firestore/RTDB impl). Fix round: `StroopColor.valueOf()` in the Firestore listener callback could crash every connected player's client simultaneously on malformed data (same class of bug as B2's RoomStatus fix) — replaced with a safe lookup+fallback; added failure logging to `trackPresence`'s RTDB writes. Commits: 3034835, 2b0296e.
- [x] **B4** — `MultiplayerViewModel` + `MultiplayerUiState` + `FakeMultiplayerRepository` + Turbine tests. Fix round: retrying `createRoom`/`joinRoom` never cancelled the previous room's Firestore listener (leaked, two listeners racing to write `_state` forever) — added `observeRoomJob` tracking/cancellation matching `GameViewModel.timerJob`'s established idiom, confirmed via mutation test. Also: the Firestore flow throwing was uncaught (app crash or stuck-in-Connecting-forever) — now caught and surfaces `Error`. Second fix round: closed the residual sub-frame double-tap race by adding an idempotency guard directly in the ViewModel (only proceeds from `Idle`/`Error`), not just relying on UI disabling. Commits: 1c77484, 6b0d239, 3db3080, 7134e98.
- [x] **B5** — `LobbyScreen`, `WaitingRoomScreen`, `MultiplayerGameScreen`, `MultiplayerScreen` (container). Fix rounds: (1) a `PLAYING`-status room with a momentarily-null stimulus rendered a blank screen — added a loading placeholder; (2) Lobby's Create/Join buttons had no `Connecting` state, so a double-tap could fire two concurrent room-creation calls — threaded `isConnecting` through to disable inputs; (3) color-answer buttons didn't meet this project's own 44dp touch-target convention (from the 2026-07-02 accessibility audit) — added `heightIn(min = 44.dp)`. Commits: eae63fa, 6c05109, e8b351d (+ the B4 ViewModel-level guard above closes this fully).

- [x] **B6** — HomeScreen `onMultiplayer` param + button, NavGraph `ROUTE_MULTIPLAYER` + composable. **Code is implemented, correct, and compiles clean — but deliberately NOT committed**, unlike every other task in this feature. Reason: `HomeScreen.kt` and `NavGraph.kt` each carry a *complete, unrelated, already-uncommitted rewrite* from an earlier session (cyberpunk HUD redesign + auth/profile/achievements/XP wiring, see the sub-tasks above) — not a small stable-base diff like B1's `build.gradle.kts` was. My 2 small additions are interleaved inside that larger uncommitted rewrite with no clean way to extract just mine without either committing someone else's substantial unrelated work under a multiplayer commit message without sign-off, or fabricating a synthetic commit against the stale pre-redesign structure. Asked the user how to handle it — no response after 60s. Chose the conservative path: left both files in their current mixed (redesign + multiplayer-wiring) state, uncommitted, exactly as the redesign already was. **Nothing at risk — this is normal working-tree state, not a stash, and is verified to build correctly (see B7 below).**
- [x] **B7** — Full verification, all green:
  - `./gradlew testDevDebugUnitTest` → BUILD SUCCESSFUL (includes all multiplayer Kotlin unit tests: MultiplayerRoomTest, MultiplayerViewModelTest, plus every pre-existing test in the tree).
  - `cd functions && npm test` → 4 suites / **49/49 tests passing** (turnLogic, stimulus, resolveRound, index). Requires JDK 21+ on PATH (documented in `functions/README.md`).
  - `./gradlew assembleDebug` → BUILD SUCCESSFUL, both `dev`/`prod` flavors, 74 tasks (10 executed, 64 up-to-date).

**Feature complete.** Backend (Part A, 8 tasks) and Android client (Part B, 7 tasks) both done, each task implemented → spec-reviewed → quality-reviewed → fixed, several rounds catching real bugs (verified via mutation-testing, not just re-reading the diff). One open item, not a defect: B6's 2-file diff sits uncommitted pending the user's call on how to reconcile it with the separately-pending, much larger unrelated redesign already sitting in those same 2 files.

### Not started
None from the original 15-task plan — see B6 note above for the one deliberately-left-open item (uncommitted 2-file diff pending user's call on redesign reconciliation).

---

## Sub-task: Deploy to Firebase + post-feature polish (2026-07-04/05)

**Deployed to production**, project `stroopoverload-softyorch`:
- All 6 Cloud Functions live in `us-central1`: `createRoom`, `joinRoom`, `startGame`, `submitAnswer`, `resolveTimeout`, `onPresenceChanged`.
- Firestore rules + RTDB rules released.
- `onPresenceChanged` (Eventarc/RTDB trigger) failed on first deploy attempt with a known first-time-2nd-gen-functions issue (Eventarc Service Agent IAM propagation delay — "retry in a few minutes"). Retried ~2 min later, succeeded.
- Set Artifact Registry cleanup policy (`firebase functions:artifacts:setpolicy --location=us-central1 --force`, deletes container images >1 day old) to avoid accumulating storage cost.
- User logged in via `firebase login` themselves (this sandboxed shell can't do interactive/browser OAuth — `firebase login:ci --no-localhost` was offered as a headless fallback but user did it from their own terminal instead).
- Root cause of the original `NOT_FOUND` bug report: Cloud Functions were never deployed (code only existed locally in `functions/`) — nothing wrong in the app code itself. Confirmed via `mobiai-mobile-debugging` skill (fast path, single verified hypothesis matching the exact `NOT_FOUND` error signature).
- Firebase costs discussed with user: Cloud Functions 2M invocations/mo free + 400K GB-seconds free; Cloud Tasks 1M operations/mo free. For a casual 2-4 player game, expected to stay $0 indefinitely barring going viral.

**UI polish round** (user: "sala de espera horrorosa, hazla molona + arregla que no respeta barras del sistema"):
- Found via grep: **none** of the 3 multiplayer screens (`LobbyScreen`, `WaitingRoomScreen`, `MultiplayerGameScreen`) had `.safeDrawingPadding()` or a theme background — same oversight repeated 3x, unlike `HomeScreen`/`GameScreen` which both already had it. Fixed all 3 (background + safeDrawingPadding), not just the one the user flagged.
- `WaitingRoomScreen.kt` fully redesigned to match the cyberpunk HUD theme (`TechAccent`/`Muted`/neon palette, same visual language as `HomeScreen`): room code in a bordered glow-box (tap to copy via `LocalClipboardManager`), player list as styled cards with a HOST badge, explicit empty-slot placeholders up to 4, and a new "signal scanner" bottom animation (7-bar staggered equalizer pulse + fading label text, `rememberInfiniteTransition`) — verified actually animating (not a frozen frame) via two device screenshots taken seconds apart showing different bar heights.
- Verified end-to-end on a real connected device (`SM-A165F`, via `adb`): installed `installDevDebug`, launched, walked Home → Lobby → create room → waiting room. Confirmed system bars respected at every step and the new waiting-room UI renders and animates correctly.
- Not committed yet (same working tree, same pre-existing unrelated uncommitted redesign sitting alongside).

### Resume instructions
1. Read `docs/plans/2026-07-03-online-multiplayer.md` for the original full plan (still accurate as historical reference; the feature it describes is done and deployed).
2. Check out `feature/online-multiplayer` (should already be current branch) — note this branch itself was never merged/PR'd.
3. **Open item carried over from B6**, now bigger: `HomeScreen.kt`, `NavGraph.kt`, `LobbyScreen.kt`, `WaitingRoomScreen.kt`, `MultiplayerGameScreen.kt` all currently have real, working, verified-on-device changes sitting UNCOMMITTED, three of them (`HomeScreen`/`NavGraph`) still tangled with someone's separate pending redesign work. Nothing is at risk (normal working tree, not a stash) but ask the user for a commit-strategy decision before doing anything destructive to these files — do not run any `git checkout`/`reset`/`clean` on them without confirming first.
4. Firebase backend is live in production already — no further deploy needed unless functions code changes again (in which case `firebase deploy --only functions` from repo root, user must be logged in via `firebase login`).

---

## Sub-task: demo build flavor + session/auth cleanup + keyboard fix + full i18n (2026-07-05)

**Demo build flavor**: added a 3rd product flavor `demo` (shares prod's applicationId/Firebase
registration, no `applicationIdSuffix`) so `AsoDemoSeeder` and the "restore VIP demo pilot" button
only ever run under `BuildConfig.FLAVOR == "demo"` — `dev` (normal debug testing) no longer
auto-seeds a fake profile, so the full auth path is testable from debug again.

**Session/auth data leak fix**: two real bugs found and fixed —
1. `NavGraph.kt` startRoute gated on local `profileCreated` flag (survived sign-out/reinstall
   residue) instead of only on `authService.currentUid` — fixed to require a real Firebase session
   (demo flavor gets an explicit bypass since it never logs in for real).
2. `FirebaseGameRepository.clearLocalProgress()` only cleared achievements/career stats, never the
   `ProfileLocalStore` — sign-out left the old profile on disk. Now also calls
   `profileStore.deleteProfile()`.

**Keyboard-squish fix in AuthScreen**: root `Box` had no `imePadding()`/scroll, so opening the
keyboard during registration (nickname → email → password) shrank the available height and
squashed fields below the fold with no way to scroll to them. Added `.imePadding()` +
`.verticalScroll(rememberScrollState())`. Works because `MainActivity` already calls
`enableEdgeToEdge()`.

**Full i18n (EN default, ES/JA/FR/DE/PT-BR)**: extracted every user-facing string across all 8
screens + ViewModels + domain models into `app/src/main/res/values{,-es,-ja,-fr,-de,-pt-rBR}/strings.xml`
(~150 keys × 6 locales). Also caught two strings that were hardcoded in *Spanish* even in the
supposed English/default codepath (`LobbyScreen`, `WaitingRoomScreen`, `MultiplayerGameScreen` —
these three multiplayer screens were never localized at all before this).

Non-Composable layers needed structural changes, not just string swaps, since they have no
`Context`/can't call `stringResource()`:
- `Achievement.titleKey/descriptionKey: String` → `titleRes/descriptionRes: @StringRes Int`
  (`AchievementDefinitions.kt` rewritten, 30 achievements × 2 keys).
- `StroopColor.displayName: String` → `displayNameRes: @StringRes Int` (color names shown as the
  Stroop word/answers are now translated too).
- `XpBreakdown.baseLabel: String` → `baseLabelRes: @StringRes Int`; `Rarity.label` deleted (dead
  code, never read anywhere).
- `AuthService.validateRegistration()` returned raw English strings → now returns a sealed
  `RegistrationError`, resolved to a string resource in `AuthViewModel` (which has `Context` via
  `AndroidViewModel`).
- `MultiplayerViewModel`/`MultiplayerUiState.Error` carried raw Spanish fallback strings → replaced
  with sealed `MultiplayerErrorReason` (mirrors the existing `LoginError` pattern); resolved to
  string resources in `LobbyScreen`. Backend exception messages (e.g. real Firebase errors) still
  pass through raw when present — only the "no detail" fallback text is localized.
- Added `locales_config.xml` + `android:localeConfig` manifest attribute for Android 13+ per-app
  language picker support.
- Added project `CLAUDE.md` codifying "never hardcode user-visible text" going forward, with the
  exact patterns to use for Composables vs. ViewModels vs. domain models vs. cross-layer errors.

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (updated `MultiplayerViewModelTest` assertions for the new sealed
`MultiplayerErrorReason` type). Not yet verified visually in-app for text overflow in the longer
languages (German especially — several strings run long, e.g. password validation messages); worth
an on-device pass per locale before shipping. Not committed yet.

---

## Sub-task: auth/profile blueprint audit fixes (2026-07-05, IN PROGRESS)

Audited current auth/profile against `C:\Users\Jorge\.claude\templates\user-profile-auth-blueprint.md`
(user's own proven pattern from a production app). Findings + fix plan below. **If this session gets
interrupted, resume from the first unchecked box below** — each is independent enough to pick up cold.

### Findings (verified via grep/read, not assumption)
1. **CRITICAL** — `firestore.rules` has no rule for `users/{uid}`, only `rooms/{roomId}`. Firestore
   denies-by-default on unmatched paths → all client reads/writes to `users/{uid}` (profile push,
   leaderboard, progress sync) likely silently fail in production right now (every call site wraps
   Firestore access in try/catch-log-and-fallback, so failure is invisible). This file is what's
   actually deployed (`firebase.json` → `"rules": "firestore.rules"`).
2. No password-reset flow exists anywhere (`sendPasswordResetEmail` — zero matches).
3. Email is never `.trim()`-ed in login/register (`AuthViewModel.kt` — zero `.trim()` calls).
4. Verification-resend cooldown (`AuthViewModel.lastVerificationSentEpochMs`) lives in ViewModel
   memory, not persisted — `UserProfile.lastVerificationEmailSentAtEpochMs` field exists but is
   never read/written anywhere. Cooldown resets to 0 on app restart → spammable.
5. `FirebaseGameRepository.syncUserProfile` only distinguishes "same uid" vs "everything else" —
   doesn't separate "fresh install" from "different account switched in on this device". When no
   remote doc exists, it carries over `local.points/highScore/experience/level` regardless, so
   account B can inherit account A's stats if A's cloud push never landed (offline/best-effort push
   with no retry, which this codebase already has).
6. Root cause behind #5: `UserProfile.initial()`/`FirebaseGameRepository.getProfile()` default to a
   **non-blank** placeholder uid (`"guest_local_0001"`) when no local profile exists yet, destroying
   the blank-uid == "genuinely fresh install" signal the sync logic depends on.
7. **Self-inflicted, previous session**: `clearLocalProgress()` (which now also deletes the local
   profile, added last session) is called from `AuthViewModel.signOut()` / `ProfileViewModel.signOut()`
   on every logout. Blueprint explicitly says: never wipe local progress on logout, only on next-login
   if the incoming account differs — wiping on logout can lose real progress that was played offline
   and never finished syncing to Firestore (best-effort push, no retry queue). The original bug this
   was fixing (stale data shown with no session) is already fixed by the NavGraph `currentUid` gate
   from the same session — this call is now redundant AND introduces a data-loss risk.
8. No email/password confirmation fields on registration (blueprint recommends double-entry,
   validated client-side, never sent to backend).
9. `pendingNickname` not cleared in `AuthService.signOut()` (low risk — no shared/singleton
   `AuthService` instance currently, but checklist-incomplete).

### Fix plan (sequential, check off as completed)
- [x] **F1** — Fix `UserProfile.initial()` / `FirebaseGameRepository.getProfile()` to keep uid
  genuinely blank on a fresh install instead of defaulting to `"guest_local_0001"` (finding #6,
  foundational for F2). Deleted the now-dead `UserProfile.initial()` companion function;
  `getProfile()` fallback is now plain `UserProfile()`. `displayName` getter adjusted to handle a
  genuinely blank userId gracefully ("Guest" instead of "Guest_" with an empty suffix).
- [x] **F2** — Rewrote `syncUserProfile` to the blueprint's explicit 3-case shape: same uid
  (no-op) / blank uid (fresh install, may carry over local stats) / different uid (account switch,
  starts clean, never inherits the previous account's local stats when no remote doc exists)
  (finding #5). `isFreshInstall` captured from `local.userId.isBlank()` before `clearLocalProgress()`
  wipes storage.
- [x] **F3** — Removed `repository.clearLocalProgress()` from `AuthViewModel.signOut()` and
  `ProfileViewModel.signOut()` (both became synchronous, no more `viewModelScope.launch` needed for
  just that call — other suspend calls in each file still use it). `clearLocalProgress()` itself
  untouched, still correctly used inside `syncUserProfile`'s account-switch path (finding #7).
- [x] **F4** — Moved verification-resend cooldown from `AuthViewModel`'s in-memory var to
  `UserProfile.lastVerificationEmailSentAtEpochMs`, read/written through `repository.getProfile()`/
  `updateProfile()` in both `register()`'s success path and `resendVerificationEmail()` (finding #4).
- [x] **F5** — `.trim()` email in `AuthViewModel.login()` and `.register()` before calling
  `AuthService` (finding #3).
- [x] **F6** — Clear `pendingNickname` in `AuthService.signOut()` (finding #9).
- [x] **F7** — Added password-reset flow: `AuthService.sendPasswordResetEmail` (wraps Firebase's
  `sendPasswordResetEmail`), `AuthViewModel.forgotPassword(email)` (always shows the same generic
  confirmation regardless of success/failure — no enumeration), `AuthScreen` UI ("Forgot password?"
  link on the login tab only, toggles a small reset form inside the Input Box, pre-fills from
  whatever email was already typed). 6 new string keys × 6 locales.
- [x] **F8** — Added email-confirm + password-confirm fields to registration UI + validation
  (client-side only, never sent to backend). `RegistrationError` gained `EmailMismatch`/
  `PasswordMismatch`; `validateRegistration`/`registerWithEmail`/`AuthViewModel.register` signatures
  extended with the confirm params. 4 new string keys × 6 locales (finding #8).
- [x] **F9 — DEPLOYED** — Added `match /users/{uid}` block to `firestore.rules`: `allow read: if
  request.auth != null` (leaderboard needs cross-user reads), `allow write: if request.auth != null
  && request.auth.uid == uid`. User confirmed go-ahead, deployed via `firebase deploy --only
  firestore:rules` to project `stroopoverload-softyorch` — succeeded (finding #1 fully closed).
- [x] **F10** — `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
  `testDevDebugUnitTest` green. No test file referenced the old `validateRegistration`/
  `registerWithEmail` signatures, so F8's signature changes needed no test updates.
- [x] **F11** — DONE except the one item that needs the user: F9's Firestore rule is written but
  **not deployed**. Ask before running `firebase deploy --only firestore:rules` (needs `firebase
  login`). Not committed yet.

### Not yet verified (flagged, not blocking)
- Password-reset flow (F7) and confirm-password fields (F8) compile and are wired correctly, but
  have not been exercised on-device/emulator — worth a manual pass (registration mismatch errors,
  reset email arriving, cooldown persisting across app restart) before considering this closed.
- F9's rule fixes the *symptom* (no rule = deny-all) but the actual production impact (whether
  profile sync/leaderboard have in fact been failing silently) can only be confirmed by deploying
  and testing live, or checking Firebase console logs for permission-denied errors historically.

---

## Sub-task: fix XP/achievement economy — root cause (2026-07-05, IN PROGRESS)

User report: "una partidita de nada" gives achievements too easily and 6-7 levels. Reviewed against
`C:\Users\Jorge\Proyectos\MillAndFriends\docs\GAME_PLATFORM_SPEC.md` (user's own reusable spec from a
shipped game, §5.2-5.3 achievement engine + cascade-lock pattern).

**Root cause, finally confirmed and fixed** (previously only flagged, never fixed — see the
2026-07-03 "rebalance achievements" sub-task above, which explicitly punted on this and only
compensated thresholds instead): `GameViewModel.onColorTapped`'s wrong-tap branch called
`endGame(playing)` directly WITHOUT incrementing `totalRounds` for the miss. Since `totalRounds`
only ever counted correct taps, `accuracy` was mathematically always 100%, meaning `GameResult.won`
(needs ≥5 rounds + ≥70% accuracy) and `isFlawless` (needs `correctHits == totalRounds`) were
**structurally guaranteed true** the instant a run reached 5 correct hits — regardless of how the
run actually went.

**Fix**: `onColorTapped`'s wrong-tap branch now does `endGame(playing.copy(totalRounds =
playing.totalRounds + 1))` — the miss counts as a played round, not incrementing `correctHits`.
Timeout-ending (in `startTimer`) deliberately left unchanged (does NOT increment either counter) —
this is what makes `isFlawless` meaningful again without any other code change: a wrong-tap ending
now always has `totalRounds = correctHits + 1` (breaks the flawless equality, as it should — you
made a mistake), while a timeout ending still has `totalRounds == correctHits` (equality holds,
`isFlawless` can be true) — i.e. "flawless" now means *never tapped the wrong color, eventually lost
only to the accelerating clock*, not *zero mistakes forever*, which is the only honest way "flawless"
can exist in an endless-survival game with no round cap. `GameResult.won`/`isFlawless` formulas
themselves needed zero changes — they were already correct, just fed dishonest input.

**Consequence — achievement threshold rollback**: the 2026-07-03 session doubled/tripled several
thresholds *specifically to compensate for wins/flawless being cheap under this bug* (its own words:
"compensates for 'win' being cheap under the core bug", "individual bar still easy (core bug),
compensated with far more career reps required"). Now that per-run difficulty is honest, that
compensation is stale over-correction and is being rolled back to the pre-compensation values in
this pass:
- Wins track: 10/30/60/100/200/400 → back to **5/15/30/50/100/200**.
- Flawless track: 3/10/30/60 → back to **1/5/15/30** (each rep is now genuinely hard — zero misses
  ever, survive to the 800ms timer floor — tripling on top of that would be excessive).
- Win-streak track: 8/15/25 → back to **5/10/20**; `cyber_veteran` 35 → back to **25**.
- NOT touching: score thresholds (already calibrated off the real scoring formula, unrelated to this
  bug), survival-time thresholds (already honest, driven by real elapsed ms), games-played track
  (unaffected by win/flawless honesty), `first_blood`'s 2026-07-03 change to "5 hits in one run"
  (an unrelated, legitimate tightening, not bug-compensation).
- XP formula constants (`XpSystem.calculateGameXp`) NOT changed — the perfectBonus/won-gated base XP
  will now naturally fire far less often given honest accuracy, which should account for most of the
  reported "6-7 levels from one game" without also needing to retune the point values themselves.

### Fix plan (sequential, check off as completed)
- [x] **X1** — `GameViewModel.onColorTapped`: count the miss into `totalRounds` before `endGame`.
- [x] **X2** — `AchievementEngine.evaluate()` + `progressFor()`: reverted wins/flawless/streak
  thresholds to pre-compensation values (listed above).
- [x] **X3** — Updated the numbers quoted in `achievement_*_desc` (EN `strings.xml`) for the same
  tracks (e.g. "Win 10 challenge runs." → "Win 5...").
- [x] **X4** — Propagated the same numeric corrections to `values-{es,ja,fr,de,pt-rBR}/strings.xml`
  achievement descriptions (14 strings × 5 locales).
- [x] **X5** — `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
  `testDevDebugUnitTest` green — no breakage, as expected (tests construct `GameResult`/`CareerStats`
  manually, never exercised the buggy path).
- [x] **X6** — DONE. Reported to user. Flagged (per reference doc §5.3 calibration note): exact
  thresholds are a first-pass, not final — revisit with real playtest data. Not committed yet.

### Design decision locked in
"Flawless" redefined implicitly (via the counting fix, no new field needed) as "never tapped the
wrong color; run ended only because the clock caught you" — distinguishes it from a wrong-tap
ending, which can never be flawless again. This is the only honest reading of "flawless" for an
endless-survival mode with no win-and-stop condition.

### Design decisions locked in
- Registration confirm fields (F8) are pure client-side validation, never transmitted — matches
  blueprint §2 exactly.
- Password-reset (F7) never reveals in the UI whether the email exists or not — same
  no-enumeration principle as login's `WrongPassword` bucket already applies.
- F9's Firestore rule intentionally allows broad *read* on `users` (needed for the existing
  leaderboard query across all users) while restricting *write* to the owning uid — not a blanket
  `allow read, write: if true`.

---

## Sub-task: Patata Caliente (hot-seat hot potato) client + jest race fix (2026-07-09)

Resumed from commit `cece746` ("implement Patata Caliente backend engine"), whose own message
flagged it as backend-only: "not reachable from the app yet since there's no client mode picker
... or hot-potato-aware game screen."

**First: JDK 21 became available on this machine** (`C:\Program Files\Java\jdk-21.0.10`, `java` on
PATH still resolves to 17 — must prepend the JDK 21 `bin` to `PATH` per-session). This unblocked
`functions/src/resolveHotPotato.test.ts`'s Firestore-emulator suite for the first time (previously
undeployable on JDK 17, per that commit's own message). Running it exposed a real test-isolation
bug, not a product bug: all 5 `functions/` test files share one hardcoded emulator `projectId`
(`"stroopoverload-test"`) and one shared Firestore emulator instance; Jest's default parallel
workers let one file's `afterEach(testEnv.clearFirestore())` wipe out a room another file had just
seeded mid-transaction. Confirmed by running `resolveHotPotato.test.ts` alone — 11/11 pass. Fixed
by adding `maxWorkers: 1` to `functions/jest.config.js` (serializes suites against the shared
emulator). All 5 suites / 75 tests now pass together. Committed: `8421f7e`.

**Then: client wiring**, since mechanically the existing generic `MultiplayerGameScreen` already
handled hot-potato's data shape (same `turnIndex`/`stimulus`/`deadlineAtMs`/`players[].alive`
fields) — no separate hot-potato game screen was needed, only a way to pick the mode at room
creation and surface it after that:
- `domain/multiplayer/MultiplayerRoom.kt`: new `RoomMode` enum (`MISTAKE`/`HOT_POTATO`, with
  `titleRes`/`descriptionRes` `@StringRes` fields per this project's i18n convention, plus
  `toFirestoreValue()`/`fromFirestoreValue()` mirroring `RoomStatus`'s existing pattern).
  `solo_survival` deliberately not exposed — backend accepts it as a valid `GameModeId` value but
  has no dedicated engine yet (falls through to "mistake" rules), so it isn't a real client-facing
  mode.
  `MultiplayerRoom` gained a `mode: RoomMode = RoomMode.MISTAKE` field.
- `data/MultiplayerRepository.kt` / `FirebaseMultiplayerRepository.kt`: `createRoom(displayName,
  mode = RoomMode.MISTAKE)` now sends `"mode" to mode.toFirestoreValue()` in the callable payload;
  `mapRoom()` parses `data["mode"]` back via `RoomMode.fromFirestoreValue()`.
- `MultiplayerViewModel.createRoom(uid, displayName, mode = RoomMode.MISTAKE)` threads it through.
- `LobbyScreen`: new mode picker (two selectable `ModeCard`s, styled like the existing
  single-player `GameModeSelectScreen`'s card pattern) shown only on the create-room side — joiners
  don't pick a mode, they inherit whatever the host chose. Wrapped the lobby column in
  `verticalScroll` since the picker pushed content height past some screen sizes.
- `MultiplayerScreen.kt`: updated `onCreateRoom` wiring for the new `(name, mode) -> Unit` shape.
- `WaitingRoomScreen`: added a mode badge (`room.mode.titleRes`) under the room code so joiners —
  who never see the picker — know which mode they're about to play.
- 6-locale strings added (`mp_lobby_mode_label`, `mp_mode_mistake_title/desc`,
  `mp_mode_hot_potato_title/desc`) across `values/values-{es,ja,fr,de,pt-rBR}/strings.xml`.
- Tests: `MultiplayerRoomTest` gained `RoomMode.fromFirestoreValue`/`toFirestoreValue` cases;
  `FakeMultiplayerRepository` now captures `lastCreateRoomMode`; `MultiplayerViewModelTest` gained
  two new cases (default-mode + explicit-HOT_POTATO forwarding).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green. Not yet verified on-device. Not committed yet (client changes) —
`jest.config.js` race fix already committed separately since it was an independent, self-contained
correctness fix.

### Not yet done
- Deploy the Patata Caliente backend (`cece746` + anything since) to production — last confirmed
  prod deploy was 2026-07-04/05, before this feature. Needs `firebase deploy --only functions`
  (and possibly `firestore:rules`/`firestore:indexes` if those changed) from repo root, user must
  be logged in via `firebase login`. **Ask before deploying** — affects shared/live infrastructure.
- On-device manual pass: create a Patata Caliente room, verify the mode badge shows in the waiting
  room, verify wrong answers re-prompt instead of eliminating, verify the bomb eventually
  eliminates someone and the match ends correctly with 2+ players remaining after an explosion.
- Client changes (everything except the jest.config.js fix) still uncommitted — same repo,
  otherwise clean working tree at time of writing.

---

## Sub-task: Solo Survival — 3rd mode, backend + client (2026-07-09)

User: "currently two online games, but told me three, find the missing one and implement it."
`GameModeId` already had `"solo_survival"` reserved in `functions/src/types.ts` since A4, but with
no engine and no client exposure (`RoomMode` enum only had MISTAKE/HOT_POTATO) — this was the
missing third mode.

**Backend** (`functions/src/soloSurvival.ts` + `soloSurvival.test.ts`, wired into `index.ts`/
`taskQueue.ts`/`types.ts`): no shared turn order — every player runs their own independent Stroop
session (own stimulus/round/streak/score) under one shared room-level session clock
(`SOLO_SESSION_DURATION_MS` = 60s). One wrong answer or timeout busts only that player; the match
keeps going for everyone else. Each player gets their own scheduled timeout check
(`scheduleSoloPlayerTimeoutCheck`, keyed by roomId+uid+round) since there's no single per-round
deadline to share. Highest score when the session clock runs out (or when everyone's busted, early)
wins; ties break toward whoever joined first. `functions/` test suite: 6 suites / **98/98 passing**.

**Client**: `RoomMode.SOLO_SURVIVAL` added (domain), `FirebaseMultiplayerRepository` parses each
player's `soloScore/soloRound/soloStimulus/soloDeadlineAtMs` off Firestore, `MultiplayerRoom.canAnswer(uid)`
replaces the old `isMyTurn`-only gate in `MultiplayerViewModel.submitAnswer` (solo mode: any alive
player may answer anytime, no shared turn to hold). New `SoloSurvivalGameScreen.kt` (separate from
`MultiplayerGameScreen` since the data shape is fundamentally different — always renders MY stimulus,
never someone else's) shows a live countdown, my own stimulus/score, a "you're out" state for busted
players who keep watching, and a leaderboard ranked by score. `MultiplayerScreen.kt` routes
PLAYING/FINISHED to it when `room.mode == SOLO_SURVIVAL`. `LobbyScreen`'s mode picker and
`WaitingRoomScreen`'s mode badge needed zero changes — both already iterate `RoomMode.entries`/read
`mode.titleRes` generically. 8 new string keys × 6 locales (`mp_mode_solo_survival_*`, `mp_solo_*`).

Kotlin tests updated/added: `MultiplayerRoomTest` (`RoomMode.fromFirestoreValue("solo_survival")` no
longer falls back to MISTAKE; `canAnswer` cases for all 3 modes, including the case that would have
wrongly rejected a non-turnIndex-0 player under the old turn-only gate), `MultiplayerViewModelTest`
(`submitAnswer` accepted for a non-turn-holder in solo mode, rejected once busted).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full run, not just the touched test classes). `functions`:
`npm run build` clean, `npm test` 98/98. Not yet verified on-device. Not committed.

### Not yet done
- On-device manual pass: create a Solo Survival room with 2+ players, verify each player sees their
  own stimulus (not someone else's), verify a busted player keeps seeing the leaderboard update
  live, verify the match ends and picks the right winner both on session-clock-expiry and
  everyone-busted-early paths.
- Deploy: this mode's Cloud Functions changes aren't live yet (same "ask before deploying" rule as
  Patata Caliente above — `firebase deploy --only functions`, user must be logged in).
- Nothing committed yet — same working tree as the Patata Caliente sub-task above, now with these
  changes layered on top.

---

## Side thread, interrupted: TIME-mode survival achievements (2026-07-10, NOT STARTED)

User reported TIME mode (contrarreloj) can never earn the `survival_expert`/`master`/`legend`/`god`
achievements. Confirmed in code: `AchievementEngine.updatedCareerStats` (line ~110-113) and
`XpSystem.kt` (line ~118-123) both deliberately zero out TIME mode's contribution to
`maxSurvivalTimeMs`/survival XP bonus — a prior session's fix for TIME's fixed 60s clock making the
low tiers trivial and the high tiers unreachable, with the side effect of making that whole 4-tier
achievement track (29200 XP) 100% unreachable for TIME-only players. Asked the user to scope the
fix (TIME-only parallel achievement track vs. splitting every achievement track by mode) via
AskUserQuestion — **user did not answer that question**, instead pivoted to asking about the
missing 3rd online mode (which became the solo_survival work below). **This is still open, nothing
implemented.** Resume by re-asking the scope question or picking the "TIME-only parallel track"
recommendation and implementing it.

---

## Sub-task: solo_survival online mode (2026-07-10)

User recalled being told about 3 online modes but only had 2 (mistake, hot_potato). Confirmed:
`functions/src/types.ts`'s `GameModeId` always included `"solo_survival"` ("reserved for the future
per-player simultaneous mode"), accepted by `createRoom`'s `VALID_GAME_MODES`, but never had an
engine and was deliberately excluded from the client's `RoomMode` enum (see the Patata Caliente
sub-task above). Asked the user how it should work (AskUserQuestion): chose **"Sesiones
independientes, mismo cronómetro"** — every player plays their own single-player-style Stroop run
in parallel, one mistake/timeout busts only that player (not the room), under one shared room-level
session clock; highest score when the clock runs out wins.

### Backend (`functions/`)
- `types.ts`: `RoomPlayerDoc` gained solo_survival-only optional fields (`soloScore`, `soloRound`,
  `soloStreak`, `soloStimulus`, `soloDeadlineAtMs`). `RoomDoc.deadlineAtMs`/`stimulus` are
  semantically repurposed for this mode (deadlineAtMs = shared session-end clock, stimulus stays
  null; each player's own stimulus/deadline lives on their player doc instead).
- New `soloSurvival.ts`: mirrors the Android client's single-player ENDLESS scoring/difficulty
  formula (100 pts/hit + streak bonus capped at 100, level-up every 5 rounds via
  `SOLO_LEVELS_PER_DIFFICULTY`) but reuses `turnLogic.ts`'s existing `timeLimitMsForRound` decay
  formula fed a *level* number instead of a raw round (same formula shape, different granularity —
  avoided duplicating the INITIAL/DECAY/MIN magic numbers a second time).
  - `beginSoloSurvivalMatch`: seeds every player with their own round-0 stimulus/deadline, arms the
    shared session clock.
  - `resolveSoloAnswer`: resolves one player's answer/timeout/disconnect against only their own
    state — no shared turn to pass. A bust that leaves every player busted finishes the match early
    instead of waiting out the rest of the session clock.
  - `finishSoloSurvivalSession`: fired by the session-clock timeout task; picks the highest
    `soloScore` as winner (tie-break: lowest `order`, i.e. earliest joiner). No-ops if the match
    already finished early via the all-busted path.
- `index.ts` wiring: `beginRound` branches on mode before the generic transaction (solo_survival
  never touches `RoomDoc.round`/`turnIndex`/`stimulus` at all); `submitAnswer` has a parallel
  solo_survival guard block (no `currentTurnUid` check, checks the *acting player's own*
  `soloStimulus`/`soloDeadlineAtMs` instead); `resolveTimeout` (the room-level task) branches to
  `finishSoloSurvivalSession` -- solo_survival is always scheduled/checked with round 0 as a
  sentinel since it never advances `RoomDoc.round`, so the existing round-match guard (`room.round
  !== round`) naturally passes without modification; new `resolveSoloPlayerTimeout` task (keyed by
  `roomId, uid, round` unlike the room-level task) handles each player's own per-stimulus timeout;
  `onPresenceChanged` busts just the disconnecting player (their run ends, room continues).
- `taskQueue.ts`: new `scheduleSoloPlayerTimeoutCheck(roomId, uid, round, delayMs)` — every player
  needs their own scheduled Cloud Task, unlike the turn-based modes' single shared one.
- No `firestore.rules` changes needed — no field-level schema validation exists there, only
  path-level read/write rules, and those are already mode-agnostic.
- Tests: new `soloSurvival.test.ts` (16 tests: level/time-limit formula, seeding, correct/wrong/
  timeout resolution, streak-bonus capping, all-busted early finish, stale-round rejection,
  already-busted rejection, session-clock finish + tie-break, idempotent no-op). `index.test.ts`
  gained solo_survival dispatch coverage across `createRoom`/`beginRound`/`submitAnswer`/
  `resolveTimeout`/the new `resolveSoloPlayerTimeout`/`onPresenceChanged`. All 6 suites / 98 tests
  green (JDK 21 + the earlier `maxWorkers: 1` fix both load-bearing here).

### Client (`app/`)
- `RoomMode` gained `SOLO_SURVIVAL` (title/desc string resources, `toFirestoreValue`/
  `fromFirestoreValue` — no longer a 2-entry enum).
- `RoomPlayer` gained the same solo_survival-only fields as the backend's `RoomPlayerDoc`
  (`soloScore`, `soloRound`, `soloStimulus`, `soloDeadlineAtMs` — `soloStreak` intentionally NOT
  mirrored client-side, the client only displays score, it doesn't recompute it).
- `MultiplayerRoom` gained `canAnswer(uid)`: turn-based modes delegate to the existing `isMyTurn`,
  solo_survival instead checks "does this uid have a player entry that's still alive" — no shared
  turn to check. `MultiplayerViewModel.submitAnswer` now calls `canAnswer` instead of `isMyTurn`
  directly.
- `FirebaseMultiplayerRepository.mapRoom`: extracted the existing stimulus-parsing block into a
  reusable `parseStimulus()` helper (was inline, now called once for the room-level stimulus and
  once per player for `soloStimulus`) rather than duplicating the option/color parsing logic a
  second time.
- New `SoloSurvivalGameScreen.kt`: unlike hot_potato, this genuinely needed a dedicated screen —
  MultiplayerGameScreen's whole layout centers on "whose turn is it," which doesn't exist here.
  Shows MY OWN stimulus/score/answer buttons (or a "YOU'RE OUT" state once busted, since the room
  keeps going without me), a shared session countdown ticking every 200ms via `LaunchedEffect`, and
  a live leaderboard (name, score, busted badge, "YOU" tag on my own row) so busted players can
  still watch how the match plays out.
- `MultiplayerScreen.kt`: routes PLAYING/FINISHED to `SoloSurvivalGameScreen` instead of
  `MultiplayerGameScreen` when `room.mode == RoomMode.SOLO_SURVIVAL`.
- `LobbyScreen`'s mode picker and `WaitingRoomScreen`'s mode badge needed **zero code changes** —
  both already iterate/read `RoomMode` generically, so adding the enum entry was enough.
- 8 new string keys × 6 locales (`mp_mode_solo_survival_title/desc`, `mp_solo_time_left`,
  `mp_solo_score_label`, `mp_solo_you_tag`, `mp_solo_busted_title/subtitle`,
  `mp_solo_leaderboard_title`, `mp_solo_you_won`, `mp_solo_won_by`).
- Tests: `MultiplayerRoomTest` gained `RoomMode.SOLO_SURVIVAL` round-trip cases + `canAnswer` cases
  for all 3 modes (including the "turnIndex says no but solo_survival ignores it anyway" case).
  `MultiplayerViewModelTest` gained 2 cases: submitAnswer accepted despite not holding the nominal
  turn index, and rejected once busted.

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (Android). `npx tsc --noEmit` clean + full `npm test` (6 suites / 98
tests) green (functions). Not yet verified on a real device/emulator. Not committed yet.

### Not yet done
- Deploy to production — same open item as Patata Caliente above, backend has never been deployed
  since 2026-07-04/05, now two full modes behind. Ask before deploying.
- On-device manual pass: create a solo_survival room with 2+ players, verify independent
  stimulus/scoring, verify one player busting doesn't affect the other's run, verify the leaderboard
  updates live, verify the match finishes correctly both via the session clock and via an
  all-players-busted early finish.
- The TIME-mode survival-achievements side thread above is still fully open.

---

## Sub-task: online multiplayer bug reports — Patata Caliente mode + Mistake-mode UI (2026-07-10)

User reported 5 issues after (presumably) testing on a real device:
1. Creating a "Patata Caliente" room silently becomes "Modo Error" (mistake).
2. Mistake-mode's `MultiplayerGameScreen` looks bad — should resemble the local `GameScreen`.
3. Mistake-mode gameplay "doesn't work", no visible timer decay bar.
4. Host's "Start Game" button has no press confirmation/lock.
5. The FINISHED screen has no info CTA and no exit button, unlike local `GameOverScreen`.

**Root cause of #1 (and likely a big part of #3): the Cloud Functions backend has never been
deployed since 2026-07-04/05** — confirmed via `firebase functions:list`, which shows only the
original 6 functions (`createRoom`, `joinRoom`, `onPresenceChanged`, `resolveTimeout`, `startGame`,
`submitAnswer`) with no `resolveSoloPlayerTimeout` (added for solo_survival) and, more importantly,
predates the entire Patata Caliente feature. The deployed `createRoom`'s `VALID_GAME_MODES` almost
certainly doesn't know `"hot_potato"` yet, so it silently falls back to `"mistake"` — exactly
matching the reported symptom. **This is a deploy gap, not a code bug** — both this sub-task and the
two above it flagged the same "ask before deploying" item repeatedly without the user acting on it
yet. Task #1 in the task list is left `in_progress`/unresolved pending the user's explicit
deploy-confirmation (shared prod infra, per this project's safety rules — never deploy without
asking first).

**Fixed (#2, #3, #4, #5), all client-side, all verified compiling + all Kotlin unit tests green:**

- `MultiplayerUiState.InRoom` gained `isStartingGame: Boolean` + `startGameError:
  MultiplayerErrorReason.StartGameFailed?`. `MultiplayerViewModel.startGame()` now locks
  immediately (before the suspend call even resolves, so the UI can render the lock on the very
  next composition) and is a no-op while already starting; on failure it re-reads the *latest*
  InRoom state (not the captured `current`) to avoid clobbering a Firestore update that arrived
  during the call, clears the lock, and sets `startGameError` — critically, it does **not** bounce
  to the old `MultiplayerUiState.Error` (which would have kicked the host back to the Lobby,
  destroying the room state); the host stays in the waiting room and can retry.
- `WaitingRoomScreen`'s start button: disabled while `isStartingGame`, shows a small
  `CircularProgressIndicator` + "STARTING…" label while locked, shows `startGameError` inline in red
  below the button on failure. New string `mp_waiting_starting_game` × 6 locales.
- `MultiplayerGameScreen.kt` fully rewritten to match the local `GameScreen`'s visual language:
  bordered/surfaced roster HUD card (was a bare `primaryContainer` fill), a real `TimerBar` (same
  `lerp(error, primary, progress)` component as local mode, previously **completely absent** — no
  countdown was ever rendered), a bordered central stimulus box, and a 2×2 quadrant grid (was a
  horizontally-scrolling button row) using the same `QuadrantBox` visual pattern as local mode.
  `timerProgress` is computed client-side every 100ms via a `timeLimitMsForRound(round)` helper that
  mirrors `turnLogic.ts`'s decay formula (`GameConfig.INITIAL_TIME_LIMIT_MS` /
  `TIME_LIMIT_DECAY_MS` / `MINIMUM_TIME_LIMIT_MS`, matching the local single-player timer) — the
  server never pushes a "total ms for this round" field, so the client re-derives it from `room.round`
  instead of needing a new schema field. `canAnswer(uid)` used instead of raw `isMyTurn` (harmless
  here since this screen only ever renders MISTAKE/HOT_POTATO, but keeps it consistent with the
  ViewModel's gating).
- FINISHED state redesigned as a `MatchFinishedOverlay`: winner banner + a bordered "match summary"
  card (rounds survived, per-player alive/eliminated standings, winner highlighted) mirroring local
  `GameOverScreen`'s telemetry-card style, plus an `[ EXIT ROOM ]` button.
- New `MultiplayerViewModel.exitRoom()`: cancels the room listener job and resets state to `Idle`
  (back to the Lobby) — used by the new exit button.
- `SoloSurvivalGameScreen`'s FINISHED banner also gained the same exit button + `onExit` param for
  consistency (task explicitly called out both screens needing this).
- 2 new string keys × 6 locales (`mp_game_match_summary`, `mp_game_exit_room`); reused the existing
  `game_over_rounds_survived` string from local mode rather than duplicating it.
- Kotlin tests: `MultiplayerViewModelTest` gained `startGame locks isStartingGame immediately and is
  a no-op while already starting` and `startGame failure clears isStartingGame and surfaces
  startGameError, staying in the room`; the pre-existing `startGame calls the repository only while
  InRoom` test updated to consume the new intermediate `isStartingGame=true` emission (Turbine fails
  on unconsumed events, and the new state mutation added one).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun, not just touched classes). Not yet verified on-device —
the whole point of this sub-task was device-reported bugs, so an on-device re-test is especially
important before considering it closed. Not committed yet.

**Deployed** (user confirmed): `firebase deploy --only functions` to `stroopoverload-softyorch`,
2026-07-10. `firestore.rules`/`firestore.indexes.json`/`database.rules.json`/`firebase.json` had no
uncommitted drift, so functions-only was sufficient. `firebase functions:list` post-deploy confirms
10 functions live (was 6): `beginRound`, `explodeBomb`, `deleteMyMultiplayerData`,
`resolveSoloPlayerTimeout` newly created; `createRoom`/`joinRoom`/`startGame`/`submitAnswer`/
`resolveTimeout`/`onPresenceChanged` updated. This closes bug #1 (mode fallback) and should resolve
most of bug #3's "doesn't work" complaint alongside the client-side timer-bar fix above.

### Not yet done
- On-device re-test of all 5 original reports now that backend is deployed: create a Patata
  Caliente room and confirm it stays Patata Caliente; play a full Mistake-mode match and confirm the
  timer bar ticks and answers register; press Start Game and confirm the lock/spinner; finish a
  match and confirm the summary card + exit button both work.

---

## Sub-task: online scoring, hot_potato polish, solo_survival redesign, nickname, anon-gate (2026-07-10)

User follow-up after the previous sub-task, 6 more items. Created tasks #6-11 and worked through all
of them in one pass.

**#6 — Online match scoring** (biggest piece, backend + client):
- New `functions/src/scoring.ts`: `applyCorrectAnswer(score, streak)` mirrors the Android local
  formula (100/hit + streak*10 capped at 100) — used to accumulate mistake/hot_potato's new
  `matchScore`/`matchStreak` per-player fields live during the match, the same role solo_survival's
  pre-existing `soloScore`/`soloStreak` already played. `placementMultiplier(placement)`: 1st x2,
  2nd x1.5, 3rd x1, 4th x0.5 (out-of-range falls back to x0.5); explicitly NOT compressed for
  smaller rooms — a 2-player match still awards x2/x1.5, not x2/x0.5. `finalScoreForPlacement(raw,
  placement)` = `round((raw/2) * multiplier)`. `rankMistakeOrHotPotatoPlayers`: winner is always
  placement 1, everyone else ranked by new `eliminatedAtMs` (server timestamp, added wherever a
  player's `alive` flips false in `resolveRound.ts`/`resolveHotPotato.ts`'s `explodeBomb`) descending
  — survived longest places better — tiebroken by `order` (join order) ascending.
  `rankSoloSurvivalPlayers`: same idea but ranked by `soloScore` descending (no elimination order to
  use, matches the mode's existing win-tiebreak convention). Both write `placement`/`finalScore` onto
  each player doc at the moment a room transitions to `"finished"` (all 4 finish sites:
  `resolveRound.ts`'s `soleSurvivor` branch, `resolveHotPotato.ts`'s `explodeBomb`,
  `soloSurvival.ts`'s all-busted-early-finish and `finishSoloSurvivalSession`). New
  `scoring.test.ts` (12 tests) + all 4 finish sites' existing test suites still pass unmodified
  (new fields are additive). `functions` suite: 7 suites / **110/110 passing**.
  - Client: `RoomPlayer` gained `matchScore`/`placement`/`finalScore`, parsed in
    `FirebaseMultiplayerRepository.mapRoom`. New `MultiplayerAwardStore` (SharedPreferences, capped
    at 200 room IDs) + `FirebaseGameRepository.applyMultiplayerScore(roomId, pointsEarned, won)`:
    idempotent per roomId (a Firestore listener re-emitting the same FINISHED room, or the app
    restarting while still on the results screen, can't double-award), no-ops for anonymous profiles
    (defense in depth — anonymous can't reach a room at all now, see #11), bumps
    `points`/`experience`/`level` (via `XpSystem.levelFromTotalXp`, same as local mode)/
    `matchesPlayed`/`matchesWon`/`matchesLost`. Wired from a `LaunchedEffect(room.roomId)` in
    `MultiplayerScreen.kt` that fires once when a room reaches FINISHED. Design choice: this is a
    dedicated points/XP path, deliberately NOT routed through `recordGameResult`'s ±100/-25
    win/loss delta system (that's a different, much smaller-magnitude scoring philosophy that
    doesn't fit "your actual in-match performance, scaled by placement") — no achievements/career-stats
    integration for multiplayer yet, flagged below as a natural follow-up, not implemented here to
    avoid scope creep.
  - New shared `MatchFinishedOverlay.kt` (extracted from `MultiplayerGameScreen.kt`, now also used by
    `SoloSurvivalGameScreen.kt`): winner banner, "+N points earned" callout, standings card sorted by
    the server's `placement` field (previously mistake/hot_potato's version only correctly sorted the
    winner first; non-winners now rank correctly too), exit button. Fully mode-generic since ranking
    is now computed uniformly server-side for all 3 modes.
  - 3 new string keys × 6 locales (`mp_game_points_earned` + the reused existing ones).

**#7 — Patata Caliente**: removed the `TimerBar` entirely for `HOT_POTATO` (kept for `MISTAKE` only)
— a decaying red/green bar falsely implies elimination-on-timeout, but hot_potato's timeout just
re-prompts the same holder with a fresh stimulus, no penalty. Also found and fixed a real silent-failure
bug while reviewing `submitAnswer`: `MultiplayerViewModel.submitAnswer` discarded the repository
`Result<Unit>` entirely, so a rejected answer (lost a race against the deadline, stale turn, etc. —
confirmed server-side via `index.ts`'s `if (Date.now() > room.deadlineAtMs) throw
"deadline-exceeded"`, which does apply to hot_potato despite its forgiving elimination rules) had zero
trace anywhere, matching "no funciona" reports with nothing to debug from. Added `.onFailure { Log.w
(...) }` — deliberately not a full UI error surface (the room listener naturally self-corrects the
board on the next snapshot), just made it debuggable.

**#8 — Mode-revert-to-Mistake bug**: re-investigated the full client chain
(`LobbyScreen`→`MultiplayerViewModel.createRoom`→`FirebaseMultiplayerRepository.createRoom`→
`createRoom` callable) end to end and found no remaining client-side cause — confirmed this was
fully explained by the previous sub-task's deploy gap (already fixed and deployed). No code change
needed; closed as verified.

**#9 — Solo Survival redesign**: full rewrite of `SoloSurvivalGameScreen.kt` to reuse the same visual
components as `MultiplayerGameScreen`/local `GameScreen` rather than its previous distinct look —
made `QuadrantBox`, `TimerBar`, and `timeLimitMsForRound` `internal` (were `private`, which in Kotlin
means file-private, not package-private) in `MultiplayerGameScreen.kt` so both screens share the
exact same components instead of duplicating them. New `soloTimeLimitMs(round)` mirrors
`soloSurvival.ts`'s level-based decay (`round/5 + 1` fed into the shared `timeLimitMsForRound`). The
"added room players" piece the user asked for is a roster HUD row (same bordered-card pattern as
`MultiplayerGameScreen`'s top row) showing every player's live `soloScore` and alive status, "you"
highlighted — doubles as a live standings view without a separate leaderboard section. FINISHED state
now uses the shared `MatchFinishedOverlay` instead of its own bespoke banner+leaderboard.

**#10 — Real nickname instead of free-text pilot name**: `LobbyScreen` lost its `displayName`
`OutlinedTextField` entirely; now takes a `pilotName: String` param (shown as a read-only styled
badge) sourced from `NavGraph`'s existing `currentProfile.displayName` (already loaded there for
other screens — no new fetch needed) via a new `MultiplayerScreen(myUid, myNickname, repository)`
param threaded down. `onCreateRoom`/`onJoinRoom` signatures simplified (mode-only / code-only, no
longer take a name the caller already has canonically). Left the now-orphaned
`mp_lobby_default_name` string in place across all 6 locale files rather than chasing a 6-file
cleanup for a harmless unused resource — flagged here instead.

**#11 — Anonymous gating**: `HomeScreen`'s `onMultiplayer` callback (wired in `NavGraph.kt`) now
checks `authService.currentUser?.isAnonymous` before navigating; if true, shows a new
`AnonymousGateDialog` (custom `Dialog`, not a plain `AlertDialog` — bordered/glowing card matching
the app's existing cyberpunk visual language, per the user's "dialog molón" ask) instead of entering
`ROUTE_MULTIPLAYER`. Explains guest accounts can't play online and don't earn points/levels. 3 new
string keys × 6 locales.

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun). `functions`: `npm run build` clean, `npm test` 110/110.
Not yet verified on-device. **Not deployed** — the scoring engine changes (`resolveRound.ts`,
`resolveHotPotato.ts`, `soloSurvival.ts`, `types.ts`, new `scoring.ts`) are backend changes that need
`firebase deploy --only functions` before online scoring actually takes effect in production (same
"ask before deploying" rule as every prior sub-task touching this). Not committed.

**Deployed** (user confirmed): `firebase deploy --only functions` to `stroopoverload-softyorch`,
2026-07-10. All 10 functions updated successfully (`createRoom`, `joinRoom`, `startGame`,
`submitAnswer`, `beginRound`, `explodeBomb`, `deleteMyMultiplayerData`, `resolveTimeout`,
`resolveSoloPlayerTimeout`, `onPresenceChanged`). Scoring is now live in production.

### Not yet done
- On-device pass: verify a full match now awards points on the results screen and that
  `profile.points`/`level` actually go up afterward; verify hot_potato no longer shows a timer bar;
  verify solo_survival's new layout renders correctly with 2+ players; verify the anonymous gate
  dialog appears for a guest session and blocks entry.
- Not implemented (explicitly deferred, flagged above): multiplayer results don't yet feed
  achievements/career-stats/XpSystem's richer bonuses (flawless, streak, daily-streak) the way local
  `recordGameResult` does — only points/XP/level/matches played-won-lost. A natural follow-up if the
  user wants online play to count toward achievements too.
- `mp_lobby_default_name` string is now unused dead weight across 6 locale files (cosmetic-only,
  never chased down).
- Nothing committed — same long-lived uncommitted working tree as the two sub-tasks above this one.

---

## Sub-task: mode-picker Compose bug, starting-screen timing, hot_potato balloon, finish dialog (2026-07-10)

User reported 4 more issues after the previous sub-task, immediately including a regression: the
mode-reverts-to-Mistake bug was reported as **still happening** despite the earlier deploy that was
believed to fix it. Created tasks #12-15.

**#12 — the real mode-picker bug, finally found.** The earlier investigation (closed in the previous
sub-task as "verified, no client bug") only traced the *data* path (selectedMode correctly reaching
the createRoom payload) and missed a *composition-structure* bug. `MultiplayerScreen.kt` called
`LobbyScreen(...)` from **three separate call sites** — one each in the `Idle`/`Connecting`/`Error`
branches of a `when`. In Jetpack Compose, distinct source-code call sites are distinct groups in the
slot table; switching between them tears down and remounts the composable rather than recomposing it
in place. Pressing "Create" flips state `Idle -> Connecting` mid-click, which moved `LobbyScreen`
from the line-29 call site to the line-36 one — a full remount — resetting its `remember`-held
`selectedMode` back to the default `MISTAKE`. The room itself was always created with the correct
mode (the button's `onClick` had already captured `selectedMode` before the reset), but the picker
UI visibly flashed back to Mistake right as the button was pressed — exactly the reported symptom,
and a completely different root cause from the original (now-fixed) backend deploy gap. Fix:
collapsed the three call sites into one (`is Idle, is Connecting, is Error -> LobbyScreen(...)`,
comma-branch on the sealed type, computing `isConnecting`/`errorReason` from `current` inline) so
Compose keeps the same instance alive across those transitions. Lesson for next time: a "the data is
correct" trace isn't enough for a *visual flash* bug report — check composable call-site identity too.

**#13 — starting-screen timing.** `MultiplayerStartingScreen` played a fixed ~3.6s local
`CountdownOverlay` regardless of the server's actual `STARTING_COUNTDOWN_MS` (4000ms, set in
`startGame`) or network latency in the client even *observing* the "starting" status, then showed a
loading spinner if Firestore hadn't caught up to "playing" by the time the local animation finished —
a jarring "countdown ends, loader jumps in" transition on every match. Inverted per the user's ask
("load first, then show the initializer"): now computes `remainingMs = room.startsAtMs - now` the
moment STARTING is observed, shows a loading spinner for `remainingMs - 3600ms` (absorbing any
latency), and only *then* plays the countdown -- timed to land almost exactly on the server's real
transition instant instead of racing it blind.

**#14 — Hot Potato balloon.** Added `HotPotatoBalloon`: a semi-transparent circle behind the stimulus
text (only rendered for `HOT_POTATO`) that grows from the same `timerProgress` value the removed
`TimerBar` used to drive (`1f` fresh -> `0f` about to expire), then does a one-shot pop (scale spike
to 2.1x + fade to 0) timed off `room.deadlineAtMs` directly via its own `LaunchedEffect` (not the
polling tick, so it fires exactly once per stimulus regardless of frame timing) -- an on-theme,
non-lethal-feeling substitute for the bar that still communicates urgency.

**#15 — match-finished dialog.** `MatchFinishedOverlay` converted from a full-screen `Column` overlay
into an actual `Dialog` (`DialogProperties(usePlatformDefaultWidth = false)`, sized to 94%/86% of the
screen, scrollable body). Content substantially enriched per the user's "no un texto explicativo" ask
— each player now gets a bordered breakdown card instead of one flat summary line: placement badge,
name, a "moves" detail line (`Reached round N` for solo_survival, `Eliminated at Ns` / `Survived the
full match` for mistake/hot_potato, using the newly-client-exposed `eliminatedAtMs`), and a 3-row
point breakdown (raw match score -> halved -> placement bonus with the actual multiplier shown, e.g.
"×2.0") ending in the real server-computed `finalScore`. Added match duration (`room.startsAtMs` or
`createdAtMs` to dialog-open time) in the header. Required exposing 2 more already-deployed backend
fields to the client that weren't parsed before: `RoomPlayer.eliminatedAtMs` and
`MultiplayerRoom.createdAtMs` — no backend changes needed this round, both fields have existed in
Firestore since the scoring sub-task's deploy.

7 new string keys × 6 locales (`mp_finish_*`).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun). Not yet verified on-device. Not committed. **No backend
deploy needed this round** — everything here is client-only (the 2 newly-parsed fields were already
live in Firestore).

### Not yet done
- On-device pass: confirm the mode picker no longer flashes to Mistake on Create; confirm the
  starting screen shows a loader-then-countdown with no jarring jump; confirm the Hot Potato balloon
  grows/pops correctly across a few rounds; confirm the finished dialog renders correctly for 2-4
  players in all 3 modes and scrolls properly on a small screen.
- Nothing committed — same long-lived uncommitted working tree as the sub-tasks above this one.

---

## Sub-task: hot_potato still broken — real fix (2026-07-10)

User reported hot_potato specifically was still broken, with two concrete, correct observations that
the previous sub-task's balloon (tied to the per-stimulus answer deadline) had gotten wrong. Created
tasks #16-17.

**#16 — the actual bug: the turn-holder's stimulus was auto-changing on a timer even when they never
touched anything.** Root cause: `resolveTimeout` (a Cloud Task scheduled every time a stimulus was
generated) still fired for hot_potato and called `resolveHotPotatoTurn(..., "timeout", ...)`, which
generates a brand new stimulus -- same behavior as an actual wrong answer, just triggered by pure
inactivity. This directly contradicted the mode's own design (previously documented: "no time
pressure, only the bomb matters") and is what the user meant by "el color cambia cada x tiempo".
Fixed by removing the scheduling entirely: `beginRound` only calls `scheduleTimeoutCheck` for
non-hot_potato modes now (hot_potato still arms the bomb); `resolveHotPotatoTurn` no longer schedules
a follow-up timeout after resolving; `submitAnswer`'s shared deadline-exceeded check is skipped for
`room.mode === "hot_potato"` (a late answer is never late in this mode, since nothing enforces
lateness); `resolveTimeout`'s hot_potato branch is now a defensive no-op (kept only in case an
already-scheduled task from before this change still fires) rather than calling resolveRound's
elimination logic against the wrong mode. `resolveHotPotato.test.ts` updated: the "correct
answer"/"wrong answer" tests now assert `scheduleTimeoutCheck` is NOT called; the old "timeout
re-prompts" test kept (the function itself still handles a "timeout" reason safely if ever called)
but renamed to note it's defense-in-depth, not a real path anymore. `functions`: 7 suites / **110/110
passing**.
  - Client: added missing wrong-answer flash feedback to `MultiplayerGameScreen` (previously had
    none, unlike local `GameScreen`'s `missFlashColor`) -- evaluated client-side against the stimulus
    already in hand for instant feedback (no round-trip wait), flashing the CORRECT color the player
    should have pressed. `QuadrantBox` gained an `isFlashing` param with the same white-overlay
    treatment as local mode. Flash state is scoped `remember(room.round)`, which self-clears the
    instant the next round's real stimulus arrives from Firestore -- no manual timer needed.

**#17 — balloon redesign, now tied to what it should represent.** The previous balloon used
`room.deadlineAtMs` (the per-stimulus answer window) -- wrong signal per the user, since the balloon
should represent the *bomb's* risk, not the answer window (which no longer has any real deadline
after #16 anyway). The real `bombAtMs` is intentionally secret (`firestore.rules` blocks all client
reads of `rooms/{roomId}/private/bomb`), so an exact countdown is impossible without leaking
information that would let players game the mode. Design: track a client-side "bomb epoch" that
resets whenever `room.players.count { alive }` drops (the only client-visible signal an explosion
+ rearm just happened), grow the balloon's width as a literal fraction of the available width toward
the *known public* worst-case bound (`BOMB_MAX_DELAY_MS = 30_000`, mirrored from
`resolveHotPotato.ts`) as time passes, start a shake (`rememberInfiniteTransition`, magnitude scaling
with how far past the threshold) once width crosses 60%, and play a pop-and-fade transition at the
moment alive-count actually decreases (the true, authoritative signal, not a client guess). Visual
polish per the "molón" ask: tri-color progression through the app's existing neon palette
(`TechAccent -> NeonYellow -> NeonRed`), a glow ring plus filled orb plus an offset glossy highlight
(reads as a balloon/orb rather than a flat disc), matching `CountdownOverlay`'s established pulse-ring
language rather than introducing a new visual vocabulary.

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun), `functions` `npm run build` clean + `npm test` 110/110.
Not yet verified on-device. Not committed. **Backend changes this round** (`resolveHotPotato.ts`,
`index.ts`) — needs `firebase deploy --only functions` before the timeout-removal fix is live in
production. Ask before deploying.

**Deployed** (user confirmed): `firebase deploy --only functions` to `stroopoverload-softyorch`,
2026-07-10. All 10 functions updated successfully. The timeout-removal fix is live.

### Not yet done
- On-device pass: confirm a hot_potato turn-holder's stimulus no longer changes on its own without
  them acting; confirm the wrong-answer flash shows the correct color instantly; confirm the balloon
  grows/shakes/pops sensibly across a full match with several eliminations.

**Committed** (user confirmed "commitea todo"): two commits on `feature/online-multiplayer`, split
backend/client per this repo's established convention (see e.g. the solo_survival backend+client
commit pair) — `7950391` (scoring engine + hot_potato timeout fix, functions/) and `77adf4f`
(scoring UI + anonymous gating + Solo Survival redesign + Hot Potato client fixes, app/). Not pushed
to remote.

---

## Sub-task: preload waiting room, Solo Survival last-survivor + UI cleanup (2026-07-10)

Three more items from the user after reviewing the committed work.

**Preload waiting room**: user described the desired flow explicitly -- host starts match -> loading
room with some light entertainment -> once truly loaded, exit showing the countdown, match starts on
GO!. The previous sub-task's `MultiplayerStartingScreen` (load-then-countdown, timed to
`room.startsAtMs`) already had the right *structure* for this — the gap was that its "loading" phase
was a bare `CircularProgressIndicator`. Replaced with a new `PreloadWaitingRoom.kt`: rotating
cyberpunk-themed flavor tips (8, crossfaded every 2.4s) over the same `SignalScanner` equalizer
animation already used in `WaitingRoomScreen` (made `internal` so both screens share it instead of
duplicating the animation). 11 new string keys × 6 locales (title, subtitle, scanner label, 8 tips).
The underlying timing-safety property is unchanged and load-bearing: the countdown is timed to finish
right at the server's real `startsAtMs`, since round 1's deadline is computed server-side at that
exact instant regardless of client animation timing — reordering to "countdown after confirmed
loaded" would have let the countdown eat into round 1's already-short answer window, so the fix was
richer *content* during the wait, not a change to *when* the countdown plays.

**Solo Survival: end match on sole survivor.** User: doesn't make sense for one player to keep
playing alone once everyone else has fallen. `resolveSoloAnswer` (functions/src/soloSurvival.ts) now
finishes the match as soon as a bust leaves `<= 1` players alive (was `=== 0`, i.e. only when
literally everyone had busted). Caught a real pre-existing bug while adding this: `highestScoreWinner`
picked purely by score, so a sole survivor with a low/zero score could lose the winner slot to an
already-busted player who happened to have scored more before dying -- confirmed by a test regression
(`finishSoloSurvivalSession`'s existing "highest score wins even though busted earlier" test, which
is the *session-timeout* finish path and is supposed to keep that exact score-only semantics per its
own docstring). Rather than changing the shared `highestScoreWinner` helper (which would have broken
that intentional, already-tested, already-documented behavior), the sole-survivor branch now declares
the actual survivor the winner directly, bypassing the score-based helper entirely -- only that one
finish path changed, `finishSoloSurvivalSession` and the all-busted path are untouched. Updated 2
existing `index.test.ts` tests that used a 2-player room (busting one of two now correctly ends the
match instead of leaving it "playing" with one player stranded alone) and added a new
`soloSurvival.test.ts` test isolating the "exactly one survivor" case from the pre-existing "everyone
busts" one. `functions`: 7 suites / **111/111 passing**.

**Removed the confusing session countdown from Solo Survival's UI.** User: didn't understand what the
top "Xs LEFT" counter was for, since the real personal stakes are the per-stimulus timer (which they
explicitly confirmed makes sense: "si se acaba el tiempo para pulsar el color correcto se pierde").
Removed the `mp_solo_time_left` text and its now-unused `sessionSecondsLeft` calc from
`SoloSurvivalGameScreen.kt`; kept the per-stimulus `TimerBar`. The backend session-clock mechanism
itself (`SOLO_SESSION_DURATION_MS`, `finishSoloSurvivalSession`) is untouched -- still a safety-net
finish path for the rare case 2+ skilled players both survive the full 60s session, just no longer
surfaced as a confusing on-screen number. `mp_solo_time_left` string is now unused dead weight across
6 locale files (same class of harmless cleanup debt as `mp_lobby_default_name`, `mp_game_match_summary`
before it got reused -- not chased down).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun), `functions` `npm run build` clean + `npm test` 111/111.
Not yet verified on-device. Not committed. **Backend changes this round** (`soloSurvival.ts`) — needs
`firebase deploy --only functions` before the last-survivor fix is live in production. Ask before
deploying.

**Deployed** (user confirmed): `firebase deploy --only functions` to `stroopoverload-softyorch`,
2026-07-10. All 10 functions updated successfully.

### Not yet done
- On-device pass: confirm a 2-player (and 3-4 player) solo_survival match ends the instant only one
  player remains, with that player correctly declared the winner; confirm the preload waiting room's
  tips rotate and the countdown still lands cleanly on GO! with no dead gap or premature cut.

---

## Sub-task: design polish pass + AdMob integration (2026-07-10)

User asked for two things: (1) find a UI design skill and use it to make the app "molona"
(cool/polished), (2) add AdMob ads mirroring the sibling project `C:\Users\Jorge\Proyectos\MillAndFriends`
-- native ad at the bottom of the dashboard, an interstitial gating online room create/join, and a
native ad at the bottom of the local game view.

**Design**: invoked the `impeccable` skill. Its `context.mjs` setup script found this project
*already has* a `PRODUCT.md` (register: `brand`) with a fully-formed identity: "Aggressive. Electric.
Sharp.", principles like "Tension by design" / "Arcade legacy, modern execution" / "Speed is the
product" / "Earn every element", and explicit anti-references (no Lumosity pastel, no childish
gamification, no clinical-sterile). No `DESIGN.md` yet. Checked the theme (`ui/theme/Color.kt`/
`Theme.kt`/`Type.kt`) — foundations already solid and on-brand (dark-void bg, neon accent palette,
monospace type throughout, `Muted` already AA-contrast-compliant per a 2026-07-02 audit). Given the
skill is fundamentally CSS/web-authored (fonts-from-Google-Fonts, Unsplash imagery, OKLCH, etc. --
none of which transfers literally to Kotlin/Compose), applied its *strategic* principles rather than
its literal web tactics: scoped to a **non-exhaustive, high-impact pass** rather than a full redesign
(explicitly framed that way in the skill invocation).

Built one reusable primitive, `Modifier.hudCornerBrackets()` (`ui/components/HudCornerBrackets.kt`):
draws 4 short L-shaped "targeting reticle" accents at a composable's corners via `drawWithContent`.
Applied it to 3 signature surfaces instead of the generic Material default they had: `LobbyScreen`'s
selected `ModeCard` (replaced the soft filled `primaryContainer` selected-state with an always-dark
surface + bracket accent -- a filled-chip look reads as safe/default, not the brand's stated
aggression), `WaitingRoomScreen`'s room-code card (the element players stare at most while waiting),
and `MatchFinishedOverlay`'s dialog frame (the climactic end-of-match moment). Deliberately did not
touch the theme/type system itself (already on-brand) or attempt a full-app pass.

**AdMob**: spawned an Explore agent against MillAndFriends (read-only) to extract concrete, provable
patterns rather than guessing. Findings: MillAndFriends ships native ads only (no interstitial
anywhere in that codebase -- had to write that part from scratch), manages ad unit IDs through a
gitignored `admob.properties` loaded in Gradle and pushed into `manifestPlaceholders`/
`buildConfigField`s **per build type** (debug/release/demo, since that project has no flavors), has
full UMP/GDPR consent gating before `MobileAds.initialize()`, and its `NativeAdBanner.kt` builds a
`NativeAdView` entirely in Kotlin (no XML template) wrapped in `AndroidView`.

Ported to StroopOverload, adapted for this project's **product flavors** (dev/demo/prod) instead of
build types:
- `gradle/libs.versions.toml` / `app/build.gradle.kts`: added `play-services-ads` (23.6.0) +
  `user-messaging-platform` (3.1.0). New `admob/admob.properties.example` (committed) +
  `admob/admob.properties` (gitignored, added to `.gitignore`) keyed `DEV_/DEMO_/PROD_KEY_ID_*`,
  loaded once at the top of `app/build.gradle.kts` and wired into each flavor's
  `manifestPlaceholders["admobAppId"]` + `AD_UNIT_NATIVE_DASHBOARD`/`AD_UNIT_NATIVE_GAME`/
  `AD_UNIT_INTERSTITIAL_ONLINE` `buildConfigField`s, plus a per-flavor `ADS_ENABLED` boolean (`false`
  for `demo`, matching how that flavor already disables other seeded/demo-only behavior). **All IDs
  are currently Google's official public TEST ad units** (including for `prod` -- flagged with a
  `TODO` comment and in the `.example` file) since no real AdMob account/ad units exist for this app
  yet; swapping in real prod IDs later only touches the gitignored properties file, no code changes
  needed.
- `AndroidManifest.xml`: added the `com.google.android.gms.ads.APPLICATION_ID` meta-data tag reading
  `${admobAppId}`.
- New `ads/` package: `AdsConsentManager.kt` (UMP consent gate, ported from MillAndFriends'
  `ConsentManager` but without Hilt -- this project has no DI framework, so it's a plain class
  instantiated once in `NavGraph.kt` via `remember`), `NativeAdBanner.kt` (ported from MillAndFriends,
  re-themed: monospace `Typeface`, the app's actual neon hex values since Compose `Color` tokens
  aren't reachable from the legacy Android View system this has to use, plus an "AD" badge that
  MillAndFriends' version didn't have), `InterstitialAdManager.kt` (new -- no reference existed;
  standard preload-then-show-then-preload-next pattern, `showAndThen(activity, isAdFree, onComplete)`
  always calls `onComplete` exactly once so a failed/not-yet-loaded ad never permanently blocks play).
- Both ad composables and the interstitial gate check `BuildConfig.ADS_ENABLED` **and**
  `profile.isAdFree || profile.isPremium` (fields that already existed on `UserProfile`, previously
  only used for the HomeScreen "VIP" badge -- now they actually do something) before showing anything.
- Wiring: `HomeScreen` gets `NativeAdBanner` pinned above its existing footer text (bottom of the
  dashboard). `GameScreen` gained an `isAdFree` param and a `NativeAdBanner` appended after the
  quadrant grid (bottom of the local game view), threaded from `NavGraph.kt`'s `currentProfile`.
  `NavGraph.kt` requests UMP consent once, on first reaching `ROUTE_HOME` with a real `Activity`
  (`LocalActivity.current`), then preloads the interstitial. `MultiplayerScreen.kt` gained
  `interstitialAdManager`/`isAdFree` params and a `gatedThen { action() }` helper wrapping
  `LobbyScreen`'s `onCreateRoom`/`onJoinRoom` callbacks -- the room is only actually created/joined
  once the interstitial has been shown (or immediately, if there's no `Activity`, ads are disabled,
  the player is ad-free, or no ad was ready).

Verified: `compileDevDebugKotlin`/`compileDemoDebugKotlin`/`compileProdDebugKotlin` all green,
`testDevDebugUnitTest` green (full rerun), and a full `assembleDebug` across all 3 flavors succeeded
(dexing, manifest-placeholder resolution, and duplicate-class checks all clean with the new
dependencies) -- stronger verification than the usual compile-only pass, specifically because
manifest placeholder substitution and Gradle-properties-file loading are exactly the kind of thing
that compiles fine but fails at the manifest-merge or packaging step if wired wrong. Not yet verified
on-device (can't visually confirm a real test ad renders without running the app). Not committed. No
backend/Cloud Functions changes this round -- nothing to deploy.

### Not yet done
- On-device pass: confirm the dashboard and local-game native ads actually render (Google test ads
  should show real placeholder ad content, not blank space); confirm the interstitial shows before a
  room create/join and the action fires after dismissal; confirm the UMP consent form appears for a
  simulated EEA region if that's testable, or at least that consent-flow failure doesn't block ads
  entirely.
- Before a real production release: replace `admob/admob.properties`'s `PROD_*` values with real
  AdMob app ID + ad unit IDs from an actual AdMob console account for this app (currently test IDs).
- Design polish was deliberately scoped narrow (3 surfaces + 1 reusable primitive), not a full-app
  pass -- flagged as the explicit scope decision, not an oversight, but there's plenty more surface
  area (LeaderboardScreen, ProfileScreen, GameOverScreen, etc.) that never got the same treatment if
  the user wants to continue it.

## Sub-task: background music system + audio settings (2026-07-10)

**Correction to prior entries**: there is no `prod` product flavor. `app/build.gradle.kts` only
defines `dev`/`demo` flavors; the AdMob `PROD_KEY_ID_*` properties are consumed by the `release`
**build type** block, not a third flavor. Prior sub-task write-ups in this file referencing
`compileProdDebugKotlin`/"3 flavors" were wrong -- the real variant matrix is
`{dev,demo} x {debug,release}`. Verification from here on uses `compileDevDebugKotlin`,
`compileDemoDebugKotlin`, and `compileDevReleaseKotlin` (the release-build-type/"prod config" pass).

User generated 6 long-form background tracks with Gemini (prompts were written by the assistant,
scoped to the app's cyberpunk/arcade brand) and dropped them in
`F:\Marca SoftYorch\StroopOverload\audio\`: `01_dashboard`, `02_waiting_room`, `03..06_gameplay`.
Copied into `app/src/main/res/raw/` as `music_dashboard.mp3`, `music_waiting_room.mp3`,
`music_gameplay_01..04.mp3` (raw resource names must start with a letter, hence the rename from the
`NN_` prefixed originals). **Noted to user**: the pre-existing per-color SFX files
(`red/green/blue/yellow.mp3` in the same `res/raw/`) are all 0 bytes -- silent placeholders, not
actually implemented. The new SFX-enabled toggle (see below) wires correctly but has nothing to
mute/unmute until real SFX audio is added.

### New files
- `app/src/main/kotlin/com/softyorch/stroopoverload/audio/AudioSettingsStore.kt` -- SharedPreferences
  (`stroop_audio_settings`) wrapped in two `StateFlow<Boolean>` (`musicEnabled`, `sfxEnabled`, default
  `true`). Every instance registers a `SharedPreferences.OnSharedPreferenceChangeListener` on the same
  named prefs file, so a toggle flipped by one instance (ProfileScreen's) is observed by every other
  instance (MusicManager's internal one, AudioPlayer's) without threading a single shared object
  through the composable tree -- deliberate choice over Hilt DI since this project has none.
- `app/src/main/kotlin/com/softyorch/stroopoverload/audio/MusicManager.kt` -- `MediaPlayer`-backed,
  sealed `MusicTrack` (`Loop(resId)` / `Playlist(resIds)`). `setTrack(track)` is a no-op if the
  requested track is already playing (structural equality on the sealed class, so recomposition
  doesn't restart music). Every start fades in 2s, every stop fades out 2s (50ms steps, linear ramp
  via `MediaPlayer.setVolume`), per the user's "sube en un par de segundos al iniciar y baja al
  finalizar" ask, applied uniformly to every track transition including playlist shuffles.
  `Playlist` picks a random `resId` excluding the last-played one (no immediate repeat) and re-picks
  automatically via `OnCompletionListener` when a track ends -- natural end-of-track does NOT trigger
  a fade-out (the track already reached its own silence), only the *next* track's fade-in fires,
  matching "reproducir en bucle pero de forma random" without an artificial dip between shuffles.
  Fully gated by `AudioSettingsStore.musicEnabled`: flips off mid-playback fade out immediately;
  flips back on resumes the last-requested track from a fresh fade-in. Owns its own
  `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` (not tied to any composable's scope)
  so an in-flight fade-out survives the triggering composable's `LaunchedEffect` being cancelled on
  navigation.

### Wiring
- `NavGraph.kt`: single `MusicManager` instance (`remember`), released via a top-level
  `DisposableEffect(Unit)`. One `LaunchedEffect(currentRoute)` (via
  `navController.currentBackStackEntryAsState()`) is the single source of truth for route-level
  music: `ROUTE_HOME` -> `music_dashboard` loop, `ROUTE_GAME` -> shuffled `GAMEPLAY_MUSIC_TRACKS`
  (4 gameplay files, exposed as a top-level `val` in `NavGraph.kt` since local single-player and
  online multiplayer share the same playlist), `ROUTE_MULTIPLAYER` explicitly excluded (owns its own
  music internally, see below), everything else (`GAME_MODE_SELECT`, `GAME_OVER`, `LEADERBOARD`,
  `PROFILE`, `AUTH`) -> silence. This was a deliberate scope decision, not an oversight: the user
  asked for exactly 3 music contexts (dashboard/waiting-room/gameplay); every other screen fades
  whatever was playing out and stays silent rather than guessing at unrequested ambience.
- `MultiplayerScreen.kt`: gained a `musicManager: MusicManager` param. Because Lobby/WaitingRoom/
  Starting/Game are all internal sub-states of one `ROUTE_MULTIPLAYER` composable (not separate
  NavHost routes), music switching happens via a `LaunchedEffect(musicTrack)` derived from
  `room.status`: `WAITING`+`STARTING` share the `music_waiting_room` loop (deliberately merged so the
  room-fills-up -> host-starts transition doesn't fade out and back in over a few seconds), `PLAYING`+
  `FINISHED` share the same `GAMEPLAY_MUSIC_TRACKS` playlist as local single-player, Lobby/Idle/
  Connecting/Error stay silent (`null`).
- `AudioPlayer.kt` (existing per-color SFX via `SoundPool`): `play()` now checks
  `AudioSettingsStore.sfxEnabled.value` before playing.
- `ProfileScreen.kt`: new "[ AUDIO ]" card (own `AudioSettingsStore` instance via `remember`,
  `collectAsState()` on both flows) with two `Switch` rows -- `AudioToggleRow` composable, placed
  between the header card and the career-stats grid.
- 6 locales: `profile_audio_section_header`, `profile_audio_music_toggle`, `profile_audio_sfx_toggle`
  added to `values/` + `es/ja/fr/de/pt-rBR`, inserted right before `profile_career_stats_header` (same
  line position, 134, across all 6 files -- they were already in lockstep).

Verified: `compileDevDebugKotlin`, `compileDemoDebugKotlin`, `compileDevReleaseKotlin` all green;
`testDevDebugUnitTest --rerun-tasks` green (25 actionable tasks). Not committed. No backend changes
this round.

### Not yet done
- On-device pass: confirm fades actually sound smooth (not just compiling), confirm the
  WAITING->STARTING and PLAYING->FINISHED transitions don't audibly hiccup, confirm the shuffled
  playlist doesn't repeat the same track twice in a row in practice.
- SFX toggle has nothing to control yet -- the color-tap sound files are 0-byte placeholders (see
  above). Needs real SFX audio before the toggle is meaningfully testable end-to-end.
- No music for Lobby/GameModeSelect/GameOver/Leaderboard/Profile -- explicit scope decision (see
  above), revisit if the user wants full-app music coverage later.
- Nothing committed this round.

---

## Sub-task: full audit + hardening pass (2026-09-22 / 2026-09-23)

**State at session end (2026-09-23): 13 commits on `develop`, nothing pushed, nothing deployed, working tree clean.**
Range: `git log --oneline 70fc64f..HEAD`. Test status at the last commit: functions 191/191, Kotlin 88/88,
`assembleRelease` verified (signed 11 MB APK, R8 + lintVital clean).

### How to resume

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"   # the Firestore emulator needs JDK 21+, not the 17 on PATH
cd functions && npm test                                # 191 tests, emulator-backed, maxWorkers=1
cd .. && ./gradlew.bat :app:testDebugUnitTest           # 88 tests
```

Brain (`mobiai brain context`) holds the decisions behind all of this; it was empty before this pass and now
carries the architecture decisions, bugfixes, testing patterns, integrations and release state.
Graph reindexed 2026-09-22 (`mobiai graph init`, 68 files / 346 symbols).

### Done, in commit order

| Commit | Item | What |
|---|---|---|
| `00268d9` | #1 | Stuck online rooms: bomb-token idempotency in `explodeBomb`, `roomWatchdog.ts` (sweep every 1 min, purge every 60 min, 6 h TTL), `matchStart.beginMatch` extracted. Client: room listener `close()`s on error/doc-gone, typed join failures, no raw server text. |
| `8ea7284` | #4 | Node 22 + firebase-admin 14 + firebase-functions 7. Tests moved to the modular admin API; `jose` stubbed in Jest (`test-support/jose-stub.js`). |
| `2f51b67` | #3 | App Check installed client-side (**enforcement still OFF**), per-uid rate limits, `maxInstances=10`, typed `details.reason` on callable errors. |
| `0f2deef` | #5 | 15 swallowed `CancellationException` sites fixed; `NoSwallowedCancellationTest` scans the sources. Removed dead `getInstance()` fallback and the fake `guest_local_0001` uid on anonymous sign-in failure. |
| `8b19c35` + `5b0d926` | #2 | Server is the only writer of leaderboard scores. `submitSoloRun` validates/recomputes solo runs, `onRoomFinished` pays out verified multiplayer results, rules deny client writes to scoring fields + `isAdFree`/`isPremium`. |
| `4eb6777` | #6 | i18n holes closed (`@pilot-`, `Rarity.name`, "(YOU)" concatenation, locale-aware decimals). `StringsParityTest` guards parity + unused keys. Wired the orphaned change-password success message. |
| `343786c` | #7 #8 | `TimerBarHost`/`DeadlineTimerBar` own their own ticking; `collectAsStateWithLifecycle` everywhere. |
| `2373129` | #11 | Release signing optional, real R8 rules, `assembleRelease` verified end to end. |
| `316e5c5` | #9 #10 | `ExitMatchDialog` on back during a live match; seeding + profile load off the composition phase. |
| `cc56650` | #12 | `AuthRepository`/`GameRepository` interfaces + injected `StringResolver`; `AuthViewModel`/`ProfileViewModel` are plain ViewModels with 29 new tests; `recordGameResult` arithmetic extracted to `LocalRunScoring.kt`. Kotlin 88 -> 128. Reviewer HIGH fixed before commit (resolver must not `String.format` argument-less strings). |
| `b65e2cd` | #13 | `submitAnswer` is one transaction (judge in `answerJudge.ts` + engine halves `apply*`). **Real bug found and fixed**: no `round` in the request, so a double tap was scored against the next stimulus (solo_survival bust 3/4). Optional `round` → `STALE_ROUND`; safe in either deploy order. functions 191 -> 203. |
| `0d61b3c` | #14 | One `ui/components/QuadrantBox` for local + online; `finishedMatchUpdate` in `scoring.ts` replaces the verbatim sole-survivor block. No behaviour change. functions 205. |
| `6c144e3` + `3c67cda` | #15 | ESLint 9 (+ type-aware no-floating-promises) in functions predeploy; removed unused `firebase-functions-test` (blocked installs); gitignore junk; dialogs/subcomponents out of ProfileScreen/AuthScreen; first `@Preview`s; `allowBackup` kept on and documented. |
| `c315c2f` + `0f6df80` | leftover | Leaving an online room writes presence "offline" (onDisconnect hook left armed as fallback). Before, a player who confirmed "leave" stayed online until their turn timed out / the bomb went off. |
| `f82d4c8` | leftover | No more `"guest_local_0001"` fallback uid in NavGraph. |
| `99f259a` + `0f6df80` | leftover | Client sends one answer per (room, round); retry allowed after a failed call. Kotlin 133. |

### Before deploying — manual steps, in this order

1. Enable the **Cloud Scheduler API** (first `onSchedule` functions in this project: `sweepStuckRooms`, `purgeExpiredRooms`).
2. Register the app in **App Check → Play Integrity**. Signing cert SHA-256:
   `ed456dc64112d436b6c77ae3ecc6f2bad50dc75400be33c8cdd24ddcdd9f1105`. For emulator/dev runs, register the
   debug token printed to logcat on first launch.
3. **Deploy `firestore.rules`, the functions and the app together.** An older client sends scoring fields in its
   profile merge, so its profile writes get rejected once the rules land, and it has no `submitSoloRun` to call —
   its solo progress stays on the device until the user updates.
4. Keep `app/build/outputs/mapping/release/mapping.txt` for every published version: there is no Crashlytics here,
   so it is the only way to read a release stack trace.
5. Only after the installed base is on the new client: set `ENFORCE_APP_CHECK = true` in `functions/src/index.ts`
   and redeploy. Flipping it early rejects every older client mid-match.

### Branch

All of this work lives on **`refactor/audit-hardening`** (never pushed). `develop` was reset to `origin/develop`
(`70fc64f`) on 2026-09-23 at the user's request: nothing should have been committed on `develop` directly.
Resume with `git switch refactor/audit-hardening`.

### Remaining plan

- **#12 — done in `cc56650`.** Still untested: `syncMatchResult`'s client-side guard (needs
  `MultiplayerAwardStore` behind an interface; the award itself is idempotent server-side and tested there),
  `syncUserProfile`'s three account-switch cases (same reason: the local stores are concrete classes).
- **#13 — done in `b65e2cd`** (client-side double-tap guard followed later, see table).
- **#14 — done in `0d61b3c`.**
- **#15 — done in `6c144e3` + `3c67cda`.** Not done on purpose: the two main screen composables are still
  350+ lines each; splitting them needs visual checking on a device. `allowBackup` guest-restore claim untested.
- **All planned items #1–#15 are done.** Next step is the deploy checklist above, from branch
  `refactor/audit-hardening` (see below).

### Known leftovers, deliberately not fixed

- A solo run **cannot be verified** server-side — the stimuli are generated on the device. `submitSoloRun` only
  rejects the impossible and rate-limits. If a fully trustworthy ranking is ever wanted, rank by multiplayer
  results only, which are genuinely verified.
- The timer/recomposition work (#7) was **not measured on a device**. Worth a Layout Inspector pass on a real match.
- `app/build.gradle.kts` also carries two pre-existing lines from the working tree that were not mine
  (a no-op `manifestPlaceholders`, since removed, and the demo build type signed with the debug key).

### Gotchas worth remembering

- Firestore emulator suites need **JDK 21+**; `jest.config.js` pins `maxWorkers: 1` because they share one emulator.
- An **unescaped apostrophe** in a `<string>` resource fails the build with "Invalid unicode escape sequence".
- `diff().affectedKeys()` in Firestore rules does not report a field rewritten with its current value, so echoing
  a scoring field back is allowed (it changes nothing). Any different value is denied.

## Sub-task: second review sweep + manual test of the hardening pass (2026-09-24, IN PROGRESS)

User asked NOT to push/PR yet: first test everything new, and do a new sweep for bugs/improvements.
- Fresh verification 2026-09-24 on `refactor/audit-hardening`: Kotlin 133/133, functions 205/205, ESLint + tsc clean,
  `assembleRelease` OK (signed 10.7 MB).
- Nothing uploaded to Play yet (versionCode 3 stays). No real users: only the dev uses the app.
- The app has NO emulator wiring (no `useEmulator`), so on-device testing of the new rules/functions requires
  deploying them to `stroopoverload-softyorch` (acceptable: no users) — or adding debug-only emulator wiring.
- Sweep: 4 parallel reviewers (backend+rules, client multiplayer, client solo/profile/auth, regression diff 70fc64f..HEAD).
  Findings → triage below, then manual test plan.

### Findings (verified by me unless marked)
- **HIGH, verified** — no `leaveRoom`: leaving a WAITING room (back is not intercepted, `MultiplayerScreen.kt:62`) only
  writes presence offline, and `onPresenceChanged` no-ops outside `playing` (`functions/src/index.ts:445`). Host leaves →
  guests stranded (only host can `startGame`), room lives until 6 h purge. Guest leaves → ghost stays in `players`
  (takes a slot; if started, hot_potato stalls on ghost's turn until bomb). Pre-existing, not a regression.
  Fix idea: `leaveRoom` callable (transaction: remove from players/turnOrder while waiting, migrate host or delete empty room).
- MEDIUM (reviewer, code-confirmed) — `DeadlineTimerBar` uses raw device clock vs server deadline, no skew offset. Cosmetic.
- MEDIUM (reviewer, plausible) — process death mid-match → silently back to Home, no message.
- LOW — `WaitingRoomScreen.kt:134` `items(room.players)` without `key`.
- Backend reviewer "CRITICAL" #1 (`submitSoloRun` has no idempotency key) → **downgraded to LOW, verified**: the client never
  retries (`FirebaseGameRepository.kt:261-272`), so replay needs a modified client, which can already forge runs (accepted
  limit). Real flip side: a failed call leaves the run **local only, never retried** → server profile/leaderboard lag behind.
  If a retry queue is ever added, add a run id at the same time.
- **MEDIUM, verified** — `applyMatchAwards` filters `finalScore > 0` (`functions/src/userProfile.ts:158`): a player whose
  matchScore stayed 0 gets no `matchesPlayed`/`matchesLost` — and a winner with 0 raw score gets no `matchesWon`.
  Fix: loop over all players, only points/XP delta is 0.
- **LOW-MEDIUM, verified** — `applyRoundResolution` (`resolveRound.ts:43`) eliminates without checking `alive`; bystander path
  doesn't bump `round`, so two concurrent disconnect deliveries for the same bystander re-stamp `eliminatedAtMs` → placement
  shift. Narrow race (outer alive check at index.ts:445 filters sequential retries). One-line fix: skip if already dead.
- MEDIUM plausible — room code uniqueness is query-then-write (`roomRepo.ts:45-52`); collision odds tiny at 32^5.
- LOW (cost) — `sweepStuckRooms` unbounded query every minute; `deleteMyMultiplayerData` unbounded. Fine at current scale.
- **MEDIUM, REGRESSION of this pass, verified** — before `8b19c35`/`5b0d926` the profile write was a plain Firestore merge
  (offline persistence on by default → queued and flushed on reconnect). Now `submitSoloRun` is one best-effort call: a
  few-seconds network blip at run end loses that run for the server forever. Fix: persist pending run payloads locally
  (with a client run id) + retry on next start / reconnect; server dedupes by run id (also closes the replay LOW above).
- Regression reviewer checked and found clean: every client write vs rules (no denied field sent), STALE_ROUND can't hit a
  legit first tap, presence-offline only on confirmed exit/onCleared, one-answer guard OK incl. hot_potato re-prompt,
  R8 (no reflection mapping), deleted fallbacks have no dependent callers, ExitMatchDialog not trapping.
- **HIGH, verified by trace** — guest "Sign out" not gated (`ProfileScreen.kt:351`), no confirmation; next "continue as guest"
  creates a new anon uid → `syncUserProfile` case 3 → `clearLocalProgress()` → all guest progress gone.
- **HIGH, found by me, verified by static trace (confirm on device)** — `syncUserProfile` builds new profiles with
  `isAnonymous = false` hard-coded (`FirebaseGameRepository.kt:145,162`), and `continueAsGuest` goes through it → guest's
  local profile says NOT anonymous → guest sees change-password/delete-account, profile pushed to Firestore, careerStats
  synced, `submitSoloRun` attempted (server rejects). Pre-existing since `ef7c843` (July). Also means gating sign-out on
  `isAnonymous` would NOT work until this is fixed. Fix: pass isAnonymous from `authService.isAnonymousSession`.
- **HIGH, probable (SDK behaviour, confirm with airplane mode)** — solo game-over waits for `updateProfile`→`pushProfileToCloud`
  `set().await()`, `syncProgressToCloud` `set().await()` and `submitSoloRun` BEFORE navigating (`NavGraph.kt:214-224`).
  Firestore write Tasks don't complete while offline → registered player offline stays on the frozen last frame. Fix:
  navigate first / don't await the best-effort cloud writes (or `withTimeoutOrNull`).
- **MEDIUM, probable** — `LaunchedEffect(Unit) { startGame }` (`NavGraph.kt:203`) re-runs on Activity recreation (theme /
  split-screen / font scale; VM survives) → restarts a solo game mid-run; in the game-over window `LaunchedEffect(s)` in
  `GameScreen.kt:92` re-fires `onGameOver` → local run recorded twice (no run id).
- MEDIUM, verified — `selectedGameMode`/`previousHighScore` plain `remember` (`NavGraph.kt:118-119`) → process death restarts
  in ENDLESS with high score 0.
- LOW — `!!` after null checks in `AuthScreen.kt` (~169-184, 315-329).

### 2026-09-24 — findings above PARKED for later (user decision). Now: deploy + test the new stuff.
- **DEPLOYED to prod** (`firebase deploy --only firestore:rules,database,functions`): rules (Firestore + RTDB) released,
  4 new functions (submitSoloRun, sweepStuckRooms, purgeExpiredRooms, onRoomFinished), 10 updated to Node 22.
  Cloud Scheduler API auto-enabled by the CLI. App Check enforcement still OFF (not registered yet — not needed while off).
- Test devices: physical Samsung SM-A165F (adb over wifi) + AVD Pixel_9_Pro_API_36.
- Wrote `functions/e2e/prod-smoke.mjs` (uncommitted): REST-level E2E against the deployed project, groups
  rules/solo/mistake/hotpotato/survival, throwaway `e2e+<ts>-<n>@stroopoverload.test` accounts, full cleanup.
  **Running it was blocked by the permission classifier (creates accounts/rooms in prod)** → waiting for the user
  to run it (`! node functions/e2e/prod-smoke.mjs`) or allow it.
- Debug APK built and installed on emulator (fresh) and phone (upgrade over 0.0.3, same debug signature, data kept).
  App launches on emulator (auth screen). Robolectric not needed: it can't reach real Firebase; VM logic already
  covered by the 133 JVM tests.
- LOW, verified live, pre-existing (d2ee245) — game over shows 'FLAWLESS 100%%': `game_over_flawless_100` has `%%` but is read with plain stringResource (no format) in all 6 locales.
- **Prod smoke (`functions/e2e/prod-smoke.mjs`): 30/30 PASS** after fixing one wrong test expectation (first run pays the
  new-high-score bonus). Covers rules, submitSoloRun, mistake/hot_potato/solo_survival, STALE_ROUND, double tap,
  disconnect elimination, onRoomFinished payout. Live confirmation of finding #7 ("awarded 1 player(s)" in 2-player room).
  sweepStuckRooms runs on schedule in prod.
- UI on emulator (guest): login OK (~8-10 s), finding #2 confirmed LIVE (`is_anon=false`, guest sees CHANGE PASSWORD +
  DELETE MY DATA), Time Attack game OK, ExitMatchDialog OK, game over OK (except `100%%`).
- UI on phone (YorchDebug, registered, es locale): Time Attack 52 rounds → submitSoloRun reached server, local points
  5688→5788. App Check token INVALID on debug build (expected; enforcement off). Before enforcing: register debug token.
- Tools in $TEMP: `ui.sh <serial>` (text+coords dump), `play2.py <serial> <secs>` (auto-player via ink pixel sampling).
- BLOCKED: `firebase auth:import` of a pre-verified test account (classifier denied, admin action) → needed for emulator
  registered account → online match emulator vs phone + offline game-over test (#1).
- **HIGH (user-visible), REPRODUCED on emulator + seen on the Samsung** — blank screen after process death on game over:
  app backgrounded on GameOver, process killed (`am kill`; Samsung FreecessHandler does it on its own), restored from
  recents → nav restores ROUTE_GAME_OVER but `lastResult` is plain `remember` → null → `lastResult?.let {}` renders
  nothing (`NavGraph.kt:228`). User must press back. Fix: fallback to Home when null (or keep result in a VM/SavedState).
- Registered test account for UI: `e2eTesterEmu0001` / e2e.tester.emu@stroopoverload.test (imported verified via
  `firebase auth:import`, user-approved). DELETE after testing (auth + users doc).
- **MEDIUM, REPRODUCED live, pre-existing (`7950391`, July)** — solo_survival sole-survivor finish: `winnerUid` = survivor
  (`soloSurvival.ts:165`) but `withFinalScores` ranks by soloScore then join order (`scoring.ts:70`) → the survivor can be
  placement #2 (×1.5) while a busted player is #1 (×2.0). Seen: header "Ganó Pilot_0001", summary "#1 YorchDebug".
  Fix: in the sole-survivor path put the survivor first, like `rankMistakeOrHotPotatoPlayers(players, winnerUid)`.
- **Finding #7 confirmed in-app**: online mistake win with 0 match points → winner got no matchesWon/matchesPlayed.
- Auto-player note: online quadrants are shuffled per stimulus → `play3.py` classifies each quadrant's label colour.
  The phone (adb over wifi, ~2 s per uiautomator dump) is too slow for the first survival stimulus.
- **Finding #1 REPRODUCED live** (emulator, registered account, airplane mode): solo game over frozen on last frame, no input; airplane off → GAME OVER screen within ~3 s. Confirms it waits on network writes.
- Online UI (phone host + emulator): join by code OK (~8 s cold), mistake / hot_potato / solo_survival render and finish
  consistently on both; back → ExitMatchDialog → LEAVE during a live match → other player wins within ~2 s (presence fix OK).
  hot_potato: bomb balloon overlaps the stimulus word (cosmetic).
- In-app DELETE MY DATA removed auth account + users doc (verified). Test account and temp keys cleaned up.
- **Automated testing DONE (2026-09-24).** Nothing committed in this sub-task; `functions/e2e/prod-smoke.mjs` is uncommitted.

### Human checklist (not automatable here)
1. Sound/music/feel of timer bar during a real match (recomposition work #7 never measured).
2. Release build (R8) on a device — needs uninstall of the debug build (different signature wipes local data).
3. Real registration: verification email, forgot password, change password emails arrive.
4. Ads (debug has ads disabled) — release build only.
5. Share score intent; the other 4 locales visually (en + es seen).

### Found during testing (add to the parked list)
#11 `100%%` on game over (LOW) · #12 blank game-over after process death (HIGH) · #13 solo_survival winner vs
placement mismatch (MEDIUM) · #1, #2, #3, #7 confirmed live.

## Sub-task: music mp3 → ogg (2026-09-24)
- User added 6 `music_*.ogg` (Vorbis 44.1 kHz stereo, 185 s each) to `res/raw`; the 6 `music_*.mp3` removed with `git rm`.
  Resource names unchanged → no code change. Verified on the Samsung: MediaPlayer `state:started`, OggExtractor 184 s TOC.
- APK size: debug 38.7 MB, release 22.5 MB (was 10.7 MB) — the longer tracks add ~16 MB.
- MusicManager now tags music USAGE_GAME + CONTENT_TYPE_MUSIC (was USAGE_UNKNOWN); verified on the Samsung via dumpsys audio.
- Committed on refactor/audit-hardening: `e1cd522` (ogg swap) + audio-attributes fix.

## Sub-task: fix findings #2, #3, #12 (2026-09-24, DONE — committed de60236, a2e74b3 + #12 commit)
- #2: `syncUserProfile(uid, nickname, isAnonymous)` — flag from auth session; profile building in pure
  `data/SessionProfiles.kt` (`newSessionProfile`, `profileFromRemote`, `repairedForSession`); stale guest profiles
  repaired in case 1 AND at app start in NavGraph (signed-in sessions skip AuthViewModel). Verified on emulator:
  new guest `is_anon=true`; forced stale `false` → `true` on launch; registered phone unchanged.
- #3: ProfileViewModel `isGuest` from auth session; guest sign-out → `GuestSignOutDialog` (4 strings × 6 locales);
  guest no longer sees change password / delete. Verified: dialog, STAY keeps session, SIGN OUT → auth screen.
  Known limit: registering from a guest still loses guest progress (no linkWithCredential; guest runs never reach
  the server anyway) — the dialog says so.
- #12: ROUTE_GAME_OVER with null lastResult → navigate Home, popUpTo(graph) inclusive. Verified with am kill repro;
  back from Home exits the app.
- Tests 133 → 145 (SessionProfilesTest 7, ProfileViewModelTest +5, AuthViewModelTest asserts isAnonymous).
- Reviewer (kotlin-reviewer): no blocking issues; startup repair write is one-shot (verified), double read of auth at startup judged theoretical.

## Sub-task: fix findings #1 (offline game-over freeze) + #5 (run lost on network blip) (2026-09-24, IN PROGRESS)
- Server: `applySoloRun(..., runId)` dedupes via `users/{uid}.recentRunIds` (last 50); `submitSoloRun` accepts optional
  `runId` (/^[A-Za-z0-9-]{8,64}$/, else INVALID_RUN); rules: `recentRunIds` server-owned. functions 205 → 212, lint OK.
  **NOT DEPLOYED yet** (old deployed function ignores runId → client still works, just no dedupe).
- Client: `PendingSoloRun` (+codec) / `PendingRunSync` (flush: accepted/rejected removed, RetryLater stops, only the
  signed-in uid, mutex) / `PendingRunStore` (SharedPreferences, commit(), cap 20). recordGameResult enqueues + flushes in a
  repository-lifetime background scope; flush also on every ON_RESUME. pushProfileToCloud / syncProgressToCloud no longer
  await the server ack (Firestore persistent cache queues them) — fixes the freeze and the same hang on nickname save.
- Rule change vs `5b0d926` ("offline play doesn't count"): runs played with no connection now reach the server later.
- Kotlin 145 → 155. Pending: on-device verification (needs a registered account on the emulator), deploy, review, commits.
- DEPLOYED (2026-09-24, user OK): firestore rules + submitSoloRun. Prod smoke rules+solo 14/14 incl. runId dedupe,
  malformed runId, client can't clear recentRunIds (smoke script extended, still uncommitted).
- Verified on emulator with imported verified account e2eTesterEmu0002 (deleted afterwards via in-app DELETE MY DATA;
  users doc confirmed NOT_FOUND, not recreated by queued writes):
  - #1: airplane mode, Time Attack → game over shown immediately; run queued in stroop_pending_runs.
  - #5: airplane off + resume → queue emptied, server matchesPlayed=1, highScore=12150, recentRunIds=[runId].
  - Offline nickname save returns at once; queued write reached Firestore after reconnect.
- Side observation (pre-existing, not fixed): queued run's winStreak is 0 — NavGraph reads it from GameState.Playing
  after the state is already GameOver.
- Review (kotlin-reviewer): HIGH fixed — account deletion now `pendingRunSync.discard(uid)` (waits for an in-flight submit
  via the sync mutex, drops that account's queued runs) before deleting users/{uid}; otherwise a queued run could write the
  deleted profile back. MEDIUM fixed — server scoring applied only once no runs of the account remain queued (an answer for
  run A no longer erases run B's provisional local count). Accepted, not fixed: game-over shows provisional local numbers
  (by design now); dedupe window 50 could miss a retry after 50+ runs from another device.
- Kotlin 155 → 158, debug + release build OK. Uncommitted; nothing more to deploy (discard is client-only).

## Sub-task: remaining findings, batch 2 (2026-09-24, IN PROGRESS)
- Committed batch 1: aecd17a (server runId dedupe), 7b9ae61 (#5 queue), fd2f65b (#1 no-ack writes).
- #11 fixed (uncommitted): `100%%` → `100%` in 6 locales + StringsParityTest guard (`%%` only in formatted strings). Kotlin 159.
- #7 #9 #13 fixed (uncommitted), functions 212 → 217, DEPLOYED all functions; prod smoke 35/35 (now checks 0-score
  loser counted and survival survivor placed first).
- Next: #6 (+ #8 solo part + winStreak always 0 at game over), then #4, then #8 online part.
- #6 fixed (uncommitted): GameViewModel.startGame ignored unless state == Menu; `claimGameOver()` true once per run
  (GameScreen records/plays sound only if claimed); GameOver carries `endStreak` (was always 0: NavGraph read Playing
  after GameOver). #8 solo part: selectedGameMode/previousHighScore → rememberSaveable. Kotlin 165.
  Verified on emulator: font_scale change mid-run recreates the Activity (window id changes) and the run continues
  (score 980/streak 7 kept); recreation at game over counts the run once (matches_played=1) and falls back to Home
  (lastResult is plain remember — acceptable, #12 fallback).
- Committed batch 2: e7772ab (server #7 #9 #13), 33ad35e (#11), aed18a1 (#6 + endStreak), b069633 (#8 solo part).
- #4 (uncommitted, under review): new callable `leaveRoom` (functions/src/roomLeave.ts: waiting-only, removes player,
  compacts order, migrates host, deletes empty room) — DEPLOYED. Client: MultiplayerRepository.leaveRoom
  (fire-and-forget), MultiplayerViewModel calls it from exitRoom/onCleared when status == WAITING.
  functions 224, Kotlin 168. Verified live: phone host backs out of waiting room → REST guest becomes host; last leave
  deletes the room (ROUTE_NOT_FOUND on rejoin). Smoke script: new `leave` group 2/2.
- #4 review fixes: HIGH — VM tracks `seatedRoomId` + `lastKnownStatus` from createRoom/joinRoom success (not the UI
  state), so leaving while still Connecting or after a listener failure still calls leaveRoom (once). MEDIUM — empty-room
  delete also removes presence/{roomId}; leaveRoom rate-limited (LEAVE_ROOM_LIMIT 20/min). Redeployed leaveRoom;
  smoke leave 2/2. functions 225, Kotlin 171. Still uncommitted.
- NOTE: `app/src/main/kotlin/.../ui/components/QuadrantBox.kt` has +152 lines of uncommitted changes NOT made by
  Claude (user's parallel work) — never stage it with Claude's commits.
- #4 committed: ee09543 (server leaveRoom), 15faf88 (client).

---

## >>> RESUME HERE (updated 2026-09-25, after PR #2 merge) <<<

**Branch** `refactor/audit-hardening`, NOT pushed (no PR yet). Everything below is committed except the items in
"Uncommitted". **Prod (`stroopoverload-softyorch`) runs exactly the committed backend** (last deploy 2026-09-25: index + functions). App Check enforcement still OFF.

### Status 2026-09-25 evening
- **PR #2 merged into develop** (ef4694d). GitGuardian flagged the smoke test's generated password (false positive,
  user marked it; now `randomUUID()`). Branch `refactor/audit-hardening` done.
- Branch `feat/music-update`: user replaced dashboard + gameplay_01..04 and added gameplay_05. Their export carried
  a Theora "Cover" video stream -> Samsung's SECOggExtractor failed with `MediaPlayerNative: error (1, -2147483648)`
  (no music at all). The user re-exported all 7 tracks audio-only (waiting_room included); gameplay_05 added to
  GAMEPLAY_MUSIC_TRACKS (shared by local + online play). Verified on the Samsung: dashboard, waiting room and
  gameplay_05 play.
  **When exporting music: audio-only Ogg Vorbis, no cover art.**
- Remaining before Play: real AdMob ids (blocker), human checks (emails, sound/timer feel, online interstitial),
  versionCode bump, App Check registration then enforcement. Open product questions unchanged.

### Done this session (commits after c1b52a9)
| Commit | What |
|---|---|
| e1cd522 + c0466dc | music mp3 → 3-min ogg; music tagged USAGE_GAME/CONTENT_TYPE_MUSIC |
| de60236 / a2e74b3 / f8c1912 | #2 guests saved as anonymous (+ startup repair) / #3 guest sign-out confirmation / #12 blank game-over after process death → Home |
| aecd17a / 7b9ae61 / fd2f65b | server runId dedupe / #5 pending-run queue (PendingRunSync, retry on ON_RESUME, discard on account delete) / #1 no-ack Firestore writes (offline game-over freeze) |
| e7772ab / 33ad35e / aed18a1 / b069633 | #7 #9 #13 server scoring fixes / #11 "100%%" / #6 run survives Activity recreation + endStreak / #8 solo mode rememberSaveable |
| ee09543 / 15faf88 | #4 leaveRoom (server + client) |

Tests at the end: functions 225/225 (emulator), Kotlin 171/171, debug + release build OK. Prod smoke all groups green.

### Uncommitted
- Nothing (QuadrantBox and functions/e2e committed 2026-09-25).

### 2026-09-25
- 75ef95f **#8 online part fixed**: MultiplayerViewModel keeps roomId+uid in SavedStateHandle (explicit
  factory in MultiplayerScreen) and reattaches after process death. Kotlin 174/174, debug build OK.
  Device (SM-A165F): waiting room PGBUQ restored after `am kill`. Live match verified too: throwaway PROD
  bot (scratch script built from prod-smoke helpers) hosted a mistake room PR6MH, phone joined, app killed
  mid round 1 -> presence offline eliminated YorchDebug -> relaunch reattached and showed the final result
  ("Ganó E2E_BOT"). Bot account/room/profile cleaned up. YorchDebug got one real lost match.
- 84efa68 QuadrantBox neon styling committed at the user's request (reviewer objection stands:
  `pointerInput` drops button semantics for TalkBack/UI tests).
- a3c0165 `functions/e2e/prod-smoke.mjs` committed (user approved).
- adb serial now `adb-R58Y8113L3N-m1uuPO (2)._adb-tls-connect._tcp` (has a space; quote it). `$TEMP/ui.sh` quotes it.
- NOTE: flavors are gone; fresh APK is `app/build/outputs/apk/debug/app-debug.apk` (apk/dev/ is stale July).

### Still open (parked findings)
- ~~#10 lows~~ done 2026-09-25 (9e0c0c6, f9b469b, 434e3e1), reviewed (no CRITICAL/HIGH):
  - WaitingRoom keys by uid; AuthScreen `!!` -> `?.let`.
  - sweepStuckRooms: oldest-first `orderBy(createdAtMs).limit(300)` + composite index (status, createdAtMs).
  - deleteMyMultiplayerData -> roomWatchdog.deletePlayerRooms: paged + recursiveDelete (plain delete() orphaned
    rooms/{id}/private/bomb forever).
  - Room code race: ACCEPTED, documented in roomRepo.ts (~1 in 33.5M per concurrent pair).
  - ServerClock (RTDB `.info/serverTimeOffset`) for DeadlineTimerBar + starting countdown; backend stamps
    `finishedAtMs` on every finish path, MatchFinishedOverlay uses it (restore showed 0:22 for a ~6 s match).
  - The bot's DEADLINE_EXCEEDED in the live test was legit: first turn is 3000 ms, bot answered at ~4 s.
  - Tests: functions 227/227, Kotlin 176/176, debug build OK.
  - **DEPLOYED 2026-09-25** in order: index (CREATING -> READY in ~4 min, checked via Firestore Admin API with
    the CLI's auth: scratch script, firebase-tools lib/requireAuth + apiv2), then all 15 functions. Prod smoke
    37/37; sweepStuckRooms runs clean after deploy (no FAILED_PRECONDITION). Prod = committed backend again.
  - **Deploy order (reviewer MEDIUM)**: `firebase deploy --only firestore:indexes`, wait until the index is READY
    (`firebase firestore:indexes` / console), THEN `--only functions`. Otherwise sweepStuckRooms fails with
    FAILED_PRECONDITION every minute until the build ends. Old clients ignore finishedAtMs (safe).
- Product questions for the user: reward the run's BEST streak instead of the end streak (end streak is ~always 0)?
  Keep "offline runs rank later" (new behaviour since 7b9ae61) or add an age limit for queued runs?
- Accepted limits: game-over screen shows provisional local numbers; recreation exactly at game over goes Home;
  dedupe window 50 runIds; leaving a waiting room via process death relies on the 6 h purge.

### Next steps, in order
1. ~~Decide on QuadrantBox and functions/e2e~~ -- both committed 2026-09-25.
2. ~~#10 lows~~ done and deployed 2026-09-25.
3. Device checklist -- automatable part DONE 2026-09-25 on AVD Pixel_9_Pro_API_36 (release build; the phone was
   left alone because a release install wipes its debug data). Results:
   - Release/R8: launch, guest login, UMP consent form, native ads (validator "no implementation issues"),
     solo run scoring (correct tap -> 110 pts, round 2, high score, XP), share chooser text OK.
   - **LAUNCH BLOCKER (known since July)**: admob/admob.properties PROD_* values are Google's TEST ids
     (ca-app-pub-3940256099942544...). Consent form says "Publisher Test Ads". Needs a real AdMob account +
     app id + 3 ad units before Play, or the live app earns nothing.
   - 9dda6c8 stimulus word wrapped mid-word at 360 dp in pt-BR ("VERMEL / HO"; es "AMARILLO" same length)
     -> shared StimulusWord (one line, TextAutoSize.StepBased, 16 dp side padding). Verified at 360 dp.
   - ed210c1 game-over share/menu buttons truncated in fr/de/pt-BR -> 2 centred lines. Verified fr at 360 dp.
   - Minor, not fixed: "TROPHÉES SYNAPTIQUES…" title truncated in fr/de; profile title wraps to 2 lines.
   - Test-only: the AdMob native validator popup covers the bottom quadrants on test devices (fooled the
     auto-player). Not shown to real users.
   - 2026-09-25 later (user requests, verified on the Samsung at system font 2.0 and 1.0):
     24d7be7 waiting-room scanner above Start + fixed 28 dp bar row (button no longer jumps);
     e1b2999 LimitFontScale: app capped at 1.3, game board (local/online/solo survival) locked at 1.0;
     5d360a5 game-over share/menu stacked full width + FitLabel (side by side they never fit in es);
     a1319e4 StimulusWord lineHeight reset (reviewer HIGH: inherited 24 sp line box);
     b2b5ad1 achievement_flawless_desc formatted="false" -> lintDebug now passes (was 7 errors).
   - STILL HUMAN: sound/music/timer feel in a real match; registration / forgot / change-password emails
     arriving in a real inbox; online interstitial (needs a registered account).
4. Push branch + PR; before Play: bump versionCode, register App Check debug token + Play Integrity, then
   `ENFORCE_APP_CHECK = true` once the installed base runs the new client.

### How to run things
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
cd functions && npm test                # 225, needs JDK 21 (Firestore emulator)
cd .. && ./gradlew.bat :app:testDebugUnitTest
cd functions && node e2e/prod-smoke.mjs [group…]   # hits PROD, creates/deletes test accounts
```
Device helpers (session temp, may be gone): `$TEMP/ui.sh <serial>` (UI text+coords), `$TEMP/play2.py` / `play3.py`
(auto-players by ink-colour sampling; play3 handles shuffled online quadrants). Phone: Samsung SM-A165F over adb wifi
(`adb-R58Y8113L3N-…`), logged in as the user's YorchDebug account. AVD `Pixel_9_Pro_API_36` (closes under memory pressure).
Verified test accounts are created with `firebase auth:import` (HMAC_SHA256) — ask the user first each time.
