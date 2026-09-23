# Integrations

<!--
Notes on third-party integrations (Firebase, analytics, push, payments,
etc.) and their project-specific configuration quirks.
-->

## Firebase (Auth, Firestore, RTDB, Functions, Cloud Tasks)

- id: firebase-stack-20260922
- type: integration_note
- status: active
- platform: shared
- area: firebase
- date: 2026-09-22

- Auth: email/contraseña + anónima (los invitados no pueden jugar online). La verificación de email tiene un cooldown persistido en el perfil.
- Firestore: `users/{uid}` (perfil + leaderboard, lo escribe el cliente) y `rooms/{id}` (solo servidor; los jugadores leen) + `rooms/{id}/private/**` (solo servidor).
- RTDB: nodos de presencia (`database.rules.json`) -> el trigger `onPresenceChanged` elimina a los jugadores desconectados.
- Functions: codebase `default`, fuente en `functions/` (TS -> lib/), runtime `nodejs20` en firebase.json. Firebase Functions v2 (`onCall`, `onTaskDispatched`, `onValueWritten`).
- Cloud Tasks: beginRound / resolveTimeout / resolveSoloPlayerTimeout / bomba (hot_potato). No hay emulador local: se mockea en los tests.
- Para correr los tests del emulador hace falta JDK 21+.
- `app/google-services.json` está en .gitignore y es sensible: no leerlo.

## AdMob + UMP

- id: admob-ump-20260922
- type: integration_note
- status: active
- platform: android
- area: ads
- date: 2026-09-22

- Nativos en el dashboard y en la partida local, intersticial al crear/unirse a partida online. Consentimiento UMP (`ads/AdsConsentManager.kt`).
- Los IDs de las unidades salen de `admob/admob.properties` (en .gitignore, claves PROD_KEY_ID_*). Si faltan, la release usa como fallback el App ID de prueba de Google y unidades en blanco. Los build types debug y demo desactivan los anuncios (`ADS_ENABLED=false`).
