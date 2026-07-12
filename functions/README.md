# StroopOverload multiplayer Cloud Functions

Backend for the online multiplayer feature: Firestore holds room state, Cloud Tasks resolves per-round timeouts, Realtime Database presence detects disconnects. See `../docs/plans/2026-07-03-online-multiplayer.md` for the full design.

## Requirements

- Node 20 (see `engines` in `package.json`)
- **JDK 21 or newer on PATH** — `firebase-tools` uses the Firestore emulator (Java-based) to run `npm test`, and refuses to start under JDK 17 or older with `firebase-tools no longer supports Java version before 21`. If `java -version` reports something older, point `JAVA_HOME`/`PATH` at a JDK 21+ install before running tests.

## Commands

- `npm run build` — compile TypeScript to `lib/`
- `npm test` — runs the full Jest suite wrapped in `firebase emulators:exec --only firestore`, since `resolveRound.test.ts` needs a live Firestore emulator
- `npm run serve` — build then start the functions/firestore/database emulators together for manual testing
