# Testing Patterns

<!--
Reusable testing patterns discovered for this project.
Append entries with: mobiai brain save testing (coming in Phase 2).
Include the problem, the pattern that solved it and a minimal example.
-->

## Suites de Jest del emulador de Firestore deben ir en serie

- id: suites-de-jest-del-emulador-de-firestore-deben-ir-en-serie-20260922-113257
- type: testing_pattern
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-22

8421f7e: todas las suites del emulador comparten un emulador y el projectId; los workers en paralelo se pisan con clearFirestore(). maxWorkers: 1. El emulador necesita JDK 21+. Los tests que importan ./index heredan el projectId de initializeApp: no separarlos en varios ficheros que corran en paralelo.

### Files
- functions/jest.config.js

## Invocar Cloud Functions directamente con .run()

- id: invocar-cloud-functions-directamente-con-run-20260922-113257
- type: testing_pattern
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-22

ab144a7: onCall/onTaskDispatched/onValueWritten exponen .run(); se prueban con CallableRequest/Request/DatabaseEvent construidos a mano. Se mockea ./taskQueue (no hay emulador de Cloud Tasks) y firebase-admin/database (RTDB no se puede probar en esta suite).

### Files
- functions/src/index.test.ts

## Tests de ViewModel con FakeMultiplayerRepository + Turbine

- id: tests-de-viewmodel-con-fakemultiplayerrepository-turbine-20260922-113257
- type: testing_pattern
- status: active
- platform: android
- area: testing
- date: 2026-09-22

MultiplayerViewModel depende de la interfaz MultiplayerRepository; los tests usan un fake en memoria y Turbine para las emisiones de StateFlow. Seguir el mismo patrón en otros ViewModels (hoy la mayoría construyen sus dependencias de Firebase directamente).

### Files
- app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/FakeMultiplayerRepository.kt
- app/src/test/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerViewModelTest.kt
