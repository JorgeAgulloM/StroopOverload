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

- **CRITICAL — Task 10**: `app/google-services.json` must be downloaded from Firebase console (package: `com.stroopoverload`) and placed at `app/google-services.json`.
- **Task 9**: Need `gradle/wrapper/gradle-wrapper.jar` to run gradlew. Either download Android Studio project or `./gradlew wrapper` from a machine with Gradle installed.
- **Audio placeholders**: `app/src/main/res/raw/*.mp3` are placeholder files — real voice recordings needed before ship.

---

## Last completed step

Full scaffold created (Tasks 1–8). Not compiled yet — gradle-wrapper.jar missing.
