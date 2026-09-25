# Bugfixes

<!--
Bugfixes and workarounds worth remembering for this project.
Append entries with: mobiai brain save bugfix (coming in Phase 2).
Mark temporary workarounds as status: temporary so the agent does not
treat them as permanent decisions.
-->

## LobbyScreen se remontaba en cada cambio de estado

- id: lobbyscreen-se-remontaba-en-cada-cambio-de-estado-20260922-113257
- type: bug_fix
- status: active
- platform: android
- area: compose
- date: 2026-09-22

77adf4f: LobbyScreen se llamaba desde 3 ramas del when (Idle/Connecting/Error), así que Compose la remontaba y el modo elegido volvía a Mistake justo al pulsar Crear. Solución: un único punto de llamada. Regla: mantener un solo call site para los composables con estado interno aunque cambie el estado padre.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/MultiplayerScreen.kt

## Carrera en la carga asíncrona de SoundPool

- id: carrera-en-la-carga-as-ncrona-de-soundpool-20260922-113257
- type: bug_fix
- status: active
- platform: android
- area: audio
- date: 2026-09-22

58b8a85: los sonidos se reproducían antes de que terminara load() y sonaban tarde o no sonaban. Hay que esperar a OnLoadCompleteListener antes de reproducir.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/audio/AudioPlayer.kt

## Hot potato: el timeout programado cambiaba el estímulo en silencio

- id: hot-potato-el-timeout-programado-cambiaba-el-est-mulo-en-sil-20260922-113257
- type: bug_fix
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-22

7950391: se eliminó la task de timeout por turno en hot_potato; la rama hot_potato de resolveTimeout es un no-op defensivo; submitAnswer se salta la comprobación de deadline en este modo.

### Files
- functions/src/resolveHotPotato.ts
- functions/src/index.ts

## Los fallos contaban como no jugados, precisión siempre al 100%

- id: los-fallos-contaban-como-no-jugados-precisi-n-siempre-al-100-20260922-113257
- type: bug_fix
- status: active
- platform: android
- area: game_logic
- date: 2026-09-22

bd8468e: un toque fallido ahora cuenta como ronda jugada. Se revirtieron los umbrales de logros que se habían inflado para compensar el bug.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/game/GameViewModel.kt

## Faltaba la regla users/{uid} en Firestore

- id: faltaba-la-regla-users-uid-en-firestore-20260922-113257
- type: bug_fix
- status: active
- platform: shared
- area: firestore_rules
- date: 2026-09-22

ef7c843: users/{uid} no tenía regla y se denegaba por defecto, así que la sincronización del perfil y el leaderboard fallaban en silencio en producción. Se añadió read=auth, write=propio uid. NOTA 2026-09-22: la escritura sigue SIN validar (anti-trampas pendiente; ver auditoría).

### Files
- firestore.rules

## Auditoría 2026-09-22: problemas abiertos priorizados

- id: auditor-a-2026-09-22-problemas-abiertos-priorizados-20260922-113740
- type: platform_workaround
- status: temporary
- platform: shared
- area: audit
- date: 2026-09-22
- review_after: 2026-10-30

SIN RESOLVER (a fecha 2026-09-22):
- CRÍTICO: users/{uid} lo escribe el cliente sin validar -> falsificación de leaderboard/puntos (también los puntos online, que aplica el cliente). Hay que mover los campos de puntuación a Functions y dejar las rules con hasOnly() sobre los campos cosméticos.
- ALTO: explodeBomb (resolveHotPotato.ts ~l.109) se traga el fallo de armBomb -> room hot_potato atascada para siempre. En beginRound, el reintento no hace nada porque status ya es 'playing'. Mismo patrón de swallow en resolveRound.ts ~l.88 y soloSurvival.ts ~l.178. No hay barrido onSchedule ni purga de rooms viejas.
- ALTO: ningún callable tiene App Check / maxInstances / límite de uso.
- MEDIO: runtime nodejs20 (EOL 2026-04-30; la desactivación en GCF está cerca). firebase-admin 12->14 y firebase-functions 6->7 atrasados.
- App: CancellationException tragada (AuthService, FirebaseGameRepository, runCatching en FirebaseMultiplayerRepository); los errores del listener de room nunca cierran el flow (la UI se queda colgada); LobbyScreen muestra el texto crudo de la excepción (rompe i18n); '@pilot-' hardcodeado + Rarity.name sin traducir; timer de 16ms recolectado en el scope raíz de GameScreen; collectAsState en vez de WithLifecycle; sin DI (AuthViewModel/ProfileViewModel no se pueden testear); sin BackHandler a mitad de partida; I/O de AsoDemoSeeder dentro de remember{}; 7 claves de strings sin usar; la firma de release se carga sin condición.

### Files
- firestore.rules
- functions/src/resolveHotPotato.ts
- functions/src/resolveRound.ts
- functions/src/soloSurvival.ts
- firebase.json
- app/src/main/kotlin/com/softyorch/stroopoverload/data/FirebaseGameRepository.kt

## Salas online atascadas + doble eliminación por bomba duplicada

- id: salas-online-atascadas-doble-eliminaci-n-por-bomba-duplicada-20260922-122725
- type: bug_fix
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-22

2026-09-22 (sin commitear ni desplegar al guardar esto). Causa raíz: si fallaba el enqueue de una Cloud Task (bomba/timeout/beginRound), la room se quedaba atascada para siempre. Además explodeBomb no era idempotente: Cloud Tasks entrega al menos una vez, así que una entrega duplicada eliminaba a un segundo jugador. OJO: 'relanzar el error de armBomb' (lo que sugería la auditoría) sería INCORRECTO: el reintento volvería a detonar.
Solución: la task de la bomba lleva bombAtMs; explodeBomb solo detona si private/bomb coincide, y borra la bomba en la misma transacción (las tasks antiguas sin bombAtMs solo detonan bombas ya vencidas). roomWatchdog.sweepStuckRooms (cada minuto, margen de 15s) repara las rooms starting/playing con retraso según el modo, reutilizando las mismas funciones protegidas (las carreras son no-ops). purgeExpiredRooms (cada hora) hace recursiveDelete de las rooms con más de 6h + su presencia. Cliente: observeRoom hace close() si hay error o el doc desaparece (antes la UI se quedaba congelada); runCatchingCancellable; JoinRoomFailure tipado; el texto crudo del servidor (solo en castellano) ya no se muestra.
Despliegue: necesita la API de Cloud Scheduler activada (primer onSchedule del proyecto).

### Files
- functions/src/roomWatchdog.ts
- functions/src/matchStart.ts
- functions/src/resolveHotPotato.ts
- functions/src/taskQueue.ts
- app/src/main/kotlin/com/softyorch/stroopoverload/data/FirebaseMultiplayerRepository.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/data/MultiplayerFailures.kt

> Actualización 2026-09-22: resuelto el punto 'explodeBomb se traga el fallo / rooms atascadas' (ver entrada 'Salas online atascadas + doble eliminación'). El resto sigue abierto.

## CancellationException tragada en toda la app

- id: cancellationexception-tragada-en-toda-la-app-20260923-053846
- type: bug_fix
- status: active
- platform: android
- area: coroutines
- date: 2026-09-23

2026-09-23 (commit 0f2deef). 15 catch de Exception tragaban la cancelación de coroutines (AuthService, FirebaseGameRepository, AuthViewModel), así que el trabajo continuaba después de cancelarse el scope. Regla del proyecto: todo bloque '} catch (e: Exception) {' va precedido de 'catch (e: CancellationException) { throw e }', o se usa runCatchingCancellable. Lo verifica NoSwallowedCancellationTest, que escanea app/src/main/kotlin (falla con la lista de fichero:línea infractores). Extras: se eliminó el fallback muerto de FirebaseAuth.getInstance() en el constructor de AuthService, y signInAnonymously ya no devuelve el uid falso 'guest_local_0001' al fallar (devuelve null; el caller ya mostraba el error). Pendiente: NavGraph.kt:239 aún usa ese uid falso como fallback para la pantalla multijugador.

### Files
- app/src/test/kotlin/com/softyorch/stroopoverload/data/NoSwallowedCancellationTest.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/core/RunCatchingCancellable.kt

## Atrás abandonaba la partida en silencio; seeding durante la composición

- id: atr-s-abandonaba-la-partida-en-silencio-seeding-durante-la-c-20260923-070533
- type: bug_fix
- status: active
- platform: android
- area: compose
- date: 2026-09-23

2026-09-23 (316e5c5). El botón atrás sacaba de una partida en curso sin avisar y sin registrar nada; ahora ExitMatchDialog confirma, solo mientras la partida está viva (en local termina sin puntuar, online es abandono y además para el listener de la sala). AsoDemoSeeder.seedIfNeeded y la carga del perfil corrían dentro de remember{}, o sea en fase de composición (Compose puede entrar, descartar y reejecutar, y bloquea el primer frame): movidos a LaunchedEffect con Dispatchers.IO.
OJO con los recursos string: un apóstrofo sin escapar (\') hace fallar el build con 'Invalid unicode escape sequence'. Los mensajes en inglés y francés se redactaron sin apóstrofo.
PENDIENTE: al salir de una partida online no se pone el nodo de presencia de RTDB en offline, así que el abandono solo se registra cuando vence el timeout de la ronda.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/components/ExitMatchDialog.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/NavGraph.kt
