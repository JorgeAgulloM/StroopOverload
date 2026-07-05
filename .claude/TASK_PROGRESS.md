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

### Design decisions locked in
- Registration confirm fields (F8) are pure client-side validation, never transmitted — matches
  blueprint §2 exactly.
- Password-reset (F7) never reveals in the UI whether the email exists or not — same
  no-enumeration principle as login's `WrongPassword` bucket already applies.
- F9's Firestore rule intentionally allows broad *read* on `users` (needed for the existing
  leaderboard query across all users) while restricting *write* to the owning uid — not a blanket
  `allow read, write: if true`.
