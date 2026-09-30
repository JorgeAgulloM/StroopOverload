# Decisions

<!--
Architecture decisions specific to this project.
Append entries with: mobiai brain save decision (coming in Phase 2).
Each entry should record: title, status (active|deprecated), platform,
area, date, decision, reason, files.
-->

## Android nativo Kotlin/Compose, sin motor de juego

- id: android-nativo-kotlin-compose-sin-motor-de-juego-20260922-113241
- type: architecture_decision
- status: active
- platform: android
- area: architecture
- date: 2026-09-22

Migrado desde Flutter/Flame el 2026-06-26 (048ca7c). Sin Flame: el bucle de juego es un timer de coroutine en viewModelScope (delay ~16ms); GameViewModel es dueño de la FSM vía StateFlow<GameState>. Cuadrantes = Box+clickable, sin Canvas propio.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/game/GameViewModel.kt

## Servidor autoritativo para multijugador online

- id: servidor-autoritativo-para-multijugador-online-20260922-113241
- type: architecture_decision
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-22

Los clientes NO escriben en rooms/* (rules: write false). Todo pasa por callables (createRoom, joinRoom, startGame, submitAnswer, deleteMyMultiplayerData) y Cloud Tasks (beginRound, resolveTimeout, resolveSoloPlayerTimeout, bomba). resolveRound es la autoridad única para eliminación/avance de turno/victoria, con transacciones Firestore y guardia de ronda obsoleta. Presencia en RTDB -> onPresenceChanged elimina por desconexión (salvo hot_potato).

### Files
- functions/src/index.ts
- functions/src/resolveRound.ts
- firestore.rules

## Inicio de partida sincronizado vía estado STARTING + startsAtMs

- id: inicio-de-partida-sincronizado-v-a-estado-starting-startsatm-20260922-113241
- type: architecture_decision
- status: active
- platform: shared
- area: multiplayer
- date: 2026-09-22

b77ac58: startGame solo pasa la room a 'starting' con startsAtMs; una Cloud Task beginRound calcula estímulo y deadline de la ronda 1 en ese instante. La cuenta atrás del cliente es cosmética y está cronometrada para caer en startsAtMs. NO calcular deadlines en el momento de la llamada: el cliente tardío perdía la ronda 1.

### Files
- functions/src/index.ts
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/screen/multiplayer/PreloadWaitingRoom.kt

## Datos secretos del servidor en rooms/{id}/private/**

- id: datos-secretos-del-servidor-en-rooms-id-private-20260922-113241
- type: architecture_decision
- status: active
- platform: shared
- area: firestore_rules
- date: 2026-09-22

Las rules de Firestore no pueden ocultar un campo de un doc legible, así que los secretos (deadline de la bomba en Patata Caliente) viven en una subcolección con read/write false. Solo el Admin SDK accede.

### Files
- firestore.rules
- functions/src/roomRepo.ts

## Tres modos online: mistake, hot_potato, solo_survival

- id: tres-modos-online-mistake-hot-potato-solo-survival-20260922-113241
- type: architecture_decision
- status: active
- platform: shared
- area: multiplayer
- date: 2026-09-22

mistake: por turnos, un fallo/timeout elimina. hot_potato: el turno solo pasa con acierto, sin timeout por respuesta; bomba oculta de 15-30s (Cloud Task) elimina al que tenga el turno; las desconexiones no hacen nada. solo_survival: sesiones independientes por jugador con reloj de room compartido; un fallo solo te elimina a ti; la partida acaba cuando quedan <=1 vivos (el superviviente gana directamente) o se agota el reloj (gana la mayor puntuación). createRoom cae a 'mistake' si el modo no se reconoce.

### Files
- functions/src/resolveRound.ts
- functions/src/resolveHotPotato.ts
- functions/src/soloSurvival.ts

## Puntuación online basada en posición

- id: puntuaci-n-online-basada-en-posici-n-20260922-113241
- type: architecture_decision
- status: active
- platform: shared
- area: scoring
- date: 2026-09-22

7950391: puntuación bruta (100/acierto + bonus de racha) dividida entre 2 y multiplicada por posición (x2, x1.5, x1, x0.5; no se comprime en rooms pequeñas). Los no ganadores se ordenan por eliminatedAtMs. El cliente aplica los puntos al perfil una sola vez mediante applyMultiplayerScore + MultiplayerAwardStore (idempotente ante FINISHED reemitido).

### Files
- functions/src/scoring.ts
- app/src/main/kotlin/com/softyorch/stroopoverload/data/local/MultiplayerAwardStore.kt

## i18n obligatorio en 6 idiomas, strings fuera de dominio/datos

- id: i18n-obligatorio-en-6-idiomas-strings-fuera-de-dominio-datos-20260922-113241
- type: architecture_decision
- status: active
- platform: android
- area: i18n
- date: 2026-09-22

en (por defecto), es, ja, fr, de, pt-rBR. Los modelos de dominio llevan @StringRes; las capas sin Context devuelven errores sellados (RegistrationError, LoginError, MultiplayerErrorReason). Ver CLAUDE.md.

### Files
- CLAUDE.md

## Flavors dev/prod/demo; AsoDemoSeeder solo en demo

- id: flavors-dev-prod-demo-asodemoseeder-solo-en-demo-20260922-113241
- type: architecture_decision
- status: active
- platform: android
- area: build
- date: 2026-09-22

d2ee245: el flavor demo aísla el perfil VIP sembrado para capturas ASO. IDs de AdMob por flavor desde admob.properties (en .gitignore); IDs de prueba hasta configurar los reales.

### Files
- app/build.gradle.kts
- app/src/main/kotlin/com/softyorch/stroopoverload/data/AsoDemoSeeder.kt

## Invitados anónimos bloqueados en multijugador

- id: invitados-an-nimos-bloqueados-en-multijugador-20260922-113241
- type: architecture_decision
- status: active
- platform: android
- area: auth
- date: 2026-09-22

Los perfiles anónimos nunca se sincronizan a Firestore, así que nunca podrían puntuar online. Se bloquean en la entrada de HomeScreen con un diálogo explicativo.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/components/AnonymousGateDialog.kt

## El logout no borra el progreso local

- id: el-logout-no-borra-el-progreso-local-20260922-113242
- type: architecture_decision
- status: active
- platform: android
- area: auth
- date: 2026-09-22

ef7c843: signOut conserva el progreso local (podría haber partidas offline sin sincronizar); la limpieza ocurre en el siguiente login solo si la cuenta entrante es distinta. syncUserProfile distingue instalación nueva de cambio de cuenta. El cooldown de reenvío de verificación se persiste en el perfil. deleteAccount: reauth -> wipeUserData (con la auth aún válida) -> user.delete().

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/data/AuthService.kt

## El modo TIME no alimenta estadísticas de supervivencia ni XP

- id: el-modo-time-no-alimenta-estad-sticas-de-supervivencia-ni-xp-20260922-113242
- type: architecture_decision
- status: active
- platform: android
- area: game_balance
- date: 2026-09-22

885164a: la duración de TIME es un reloj fijo, no una señal de habilidad. En TIME se ignora survivalMs para maxSurvivalTimeMs y para el bonus de XP. Un toque fallido cuenta como ronda jugada (bd8468e), así que la precisión y 'flawless' son honestos.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/domain/XpSystem.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/domain/AchievementEngine.kt

## App Check en despliegue por fases + límite de uso por usuario

- id: app-check-en-despliegue-por-fases-l-mite-de-uso-por-usuario-20260923-052239
- type: architecture_decision
- status: active
- platform: shared
- area: security
- date: 2026-09-23

2026-09-23. El cliente instala App Check (Play Integrity en release; proveedor de depuración en debug y demo, separado por source set para que el de depuración NO viaje en la release). En el servidor, ENFORCE_APP_CHECK está en false a propósito: activarlo antes de que la base instalada envíe tokens rechaza a todos los clientes antiguos en mitad de la partida. Pasos para activarlo: publicar el cliente -> registrar la app en la consola de Firebase (Play Integrity) -> vigilar la métrica de peticiones no verificadas -> poner true y redesplegar.
Límite de uso: rateLimit.ts, ventana fija por uid en rateLimits/{uid} (solo servidor). createRoom 10/min, joinRoom 20/min (los intentos fallidos también cuentan, que es lo que frena la fuerza bruta del código de 5 caracteres). Todas las functions llevan maxInstances=10 para acotar el gasto.
Los errores de las callables ahora llevan details.reason (ROOM_NOT_FOUND, ROOM_FULL, ALREADY_STARTED, INVALID_CODE, RATE_LIMITED) porque los códigos por sí solos son ambiguos: resource-exhausted significaba a la vez 'sala llena' y 'demasiados intentos'. El cliente prefiere el reason y cae al código si el backend es antiguo.

### Files
- functions/src/index.ts
- functions/src/rateLimit.ts
- app/src/main/kotlin/com/softyorch/stroopoverload/StroopApplication.kt
- app/src/debug/kotlin/com/softyorch/stroopoverload/AppCheckProviderFactory.kt

## Runtime Node 22 en Cloud Functions

- id: runtime-node-22-en-cloud-functions-20260923-052239
- type: architecture_decision
- status: active
- platform: shared
- area: firebase_functions
- date: 2026-09-23

2026-09-23. nodejs22 + firebase-admin 14 + firebase-functions 7 (Node 20 salió de LTS en abril de 2026 y firebase-admin 14 exige Node>=22). Los tests usan la API modular (getApps/getApp/initializeApp, DocumentData); DatabaseEvent exige authType; jose (solo ESM, vía jwks-rsa) está mapeado a un stub en jest.config.js porque Jest corre en CommonJS y ninguna suite verifica tokens reales.

### Files
- firebase.json
- functions/package.json
- functions/test-support/jose-stub.js

## Las partidas offline NO suman al leaderboard

- id: las-partidas-offline-no-suman-al-leaderboard-20260923-053336
- type: architecture_decision
- status: deprecated
- platform: android
- area: scoring
- date: 2026-09-23

> **Deprecated 2026-09-29:** sustituida por "Las partidas offline puntúan si llegan al servidor en menos de 24 h". Desde 7b9ae61 (2026-09-24) las partidas offline se meten en una cola y puntúan al llegar. Lo que sigue vigente de esta entrada es que el servidor es el único que escribe la puntuación.

Decidido por el usuario el 2026-09-23, para el trabajo anti-trampas pendiente (#2). Solo puntúa lo que se envía estando online y el servidor puede validar; una partida jugada sin conexión cuenta para el progreso local, pero no para el ranking público. Esto permite validación estricta en el servidor (nada de topes de plausibilidad) y que las reglas de Firestore dejen users/{uid} de solo lectura para el cliente en los campos de puntuación.

## El servidor es el único que escribe la puntuación del leaderboard

- id: el-servidor-es-el-nico-que-escribe-la-puntuaci-n-del-leaderb-20260923-055420
- type: architecture_decision
- status: active
- platform: shared
- area: scoring
- date: 2026-09-23

2026-09-23. Antes cualquier usuario autenticado podía escribir points/experience/level/highScore en su propio users/{uid} con una llamada REST. Ahora firestore.rules deniega al cliente esos campos más los contadores de partidas, dailyStreak, awardedAchievements e isAdFree/isPremium (son derechos, no preferencias); solo los escribe el Admin SDK.
- submitSoloRun (callable): valida el informe de la partida y recalcula puntuación y XP con profileScoring.ts, que es un port de XpSystem.kt. Si se cambia XpSystem.kt hay que cambiar el port o el progreso visible del jugador dará un salto (profileScoring.test.ts fija los números).
- onRoomFinished (trigger): reparte el finalScore que ya calculó el servidor; idempotente por sala vía awardsAppliedAtMs.
- Cliente: recordGameResult sigue aplicando el resultado en local (para jugar sin conexión) y luego lo envía; lo que responda el servidor sustituye a los números locales. En multijugador espera awardsAppliedAtMs y relee el perfil.
LÍMITE REAL: una partida en solitario NO se puede verificar (los estímulos los genera el móvil); solo se acota lo imposible y se limita el ritmo. El multijugador sí es verificable. Si algún día se quiere un ranking 100% fiable, hay que rankear solo por resultados de multijugador.

### Files
- functions/src/userProfile.ts
- functions/src/profileScoring.ts
- firestore.rules
- app/src/main/kotlin/com/softyorch/stroopoverload/domain/ServerScoring.kt

## Los temporizadores de juego se recolectan dentro del propio componente

- id: los-temporizadores-de-juego-se-recolectan-dentro-del-propio-20260923-061504
- type: architecture_decision
- status: active
- platform: android
- area: compose
- date: 2026-09-23

2026-09-23 (343786c). GameScreen leía timerProgress (emite cada 16ms) en el cuerpo raíz del composable, así que recomponía toda la pantalla ~60 veces por segundo para mover una barra; las pantallas online hacían lo mismo a 10Hz con su propio nowMs. Ahora TimerBarHost recolecta el StateFlow dentro y DeadlineTimerBar tiene su propio tick contra el deadline del servidor. Regla: si un valor cambia a ritmo de frame, quien lo lee debe ser el composable más pequeño posible, nunca la pantalla. Todo collectAsState pasó a collectAsStateWithLifecycle (antes se seguía recomponiendo en segundo plano). NO medido en dispositivo.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/ui/components/TimerBar.kt

## Firma de release opcional y reglas reales de R8

- id: firma-de-release-opcional-y-reglas-reales-de-r8-20260923-062715
- type: architecture_decision
- status: active
- platform: android
- area: build
- date: 2026-09-23

2026-09-23 (2373129). signing/signing.properties es opcional: si falta, la release sale sin firmar pero el proyecto configura (antes petaba la fase de configuración entera, así que ni los tests ni assembleDebug corrían en un clon limpio o en CI). proguard-rules.pro ya no está vacío: pocas reglas a propósito, porque la app mapea Firestore a mano (sin toObject ni reflexión) y las librerías traen sus consumer rules; se conservan SourceFile y LineNumberTable porque no hay Crashlytics y el mapping local es la única vía para leer un stack trace de Play Console.
VERIFICADO el 2026-09-23: assembleRelease pasa limpio (R8 + lintVital), APK de 11MB firmada con el keystore real. Huella SHA-256 del certificado de firma (para registrar Play Integrity en App Check): ed456dc64112d436b6c77ae3ecc6f2bad50dc75400be33c8cdd24ddcdd9f1105. El bloqueo antiguo de 'no se puede generar release' quedó obsoleto.

### Files
- app/build.gradle.kts
- app/proguard-rules.pro

## Estado al cerrar sesión 2026-09-23

- id: estado-al-cerrar-sesi-n-2026-09-23-20260923-071218
- type: architecture_decision
- status: deprecated
- platform: shared
- area: project_status
- date: 2026-09-23

> **Deprecated 2026-09-29:** era el estado de una sesión, no una decisión. Todo se hizo push y se desplegó (PR #2, fusionado el 2026-09-25, ef4694d). El punto #12 también está hecho: existen AuthViewModelTest y ProfileViewModelTest.

13 commits en develop SIN push y SIN desplegar (git log --oneline 70fc64f..HEAD). Árbol limpio. functions 191/191, Kotlin 88/88, assembleRelease verificado.
Hecho: #1 salas atascadas, #2 puntuación autoritativa en servidor, #3 App Check + límites de uso, #4 Node 22, #5 CancellationException, #6 i18n + guard de paridad, #7/#8 recomposición de temporizadores, #9 confirmación al salir, #10 I/O fuera de composición, #11 firma opcional + reglas R8 verificadas.
Siguiente: #12 — extraer interfaces de AuthService y FirebaseGameRepository para poder testear AuthViewModel y ProfileViewModel (login, registro, cambio de contraseña, borrado de cuenta).
ANTES DE DESPLEGAR: activar Cloud Scheduler, registrar Play Integrity (SHA-256 ed456dc64112d436b6c77ae3ecc6f2bad50dc75400be33c8cdd24ddcdd9f1105), desplegar reglas+functions+app JUNTAS, y dejar ENFORCE_APP_CHECK en false hasta que la base instalada tenga el cliente nuevo. Checklist completa en .claude/TASK_PROGRESS.md.

### Files
- .claude/TASK_PROGRESS.md

## Las partidas offline puntúan si llegan al servidor en menos de 24 h

- id: las-partidas-offline-punt-an-si-llegan-al-servidor-en-menos-20260929-142904
- type: architecture_decision
- status: active
- platform: shared
- area: scoring
- date: 2026-09-29

### Decision
Sustituye a "Las partidas offline NO suman al leaderboard" (2026-09-23). Desde 7b9ae61 (2026-09-24) cada partida solo terminada se guarda con un runId UUID en PendingRunStore (máx. 20) y PendingRunSync la reintenta contra submitSoloRun hasta que el servidor responde: se aplica, se rechaza (partida imposible o invitado) o se reintenta más tarde. Una partida jugada sin conexión sí puntúa en el ranking cuando llega.
Desde 74751a7 (PR #7, 2026-09-28) se descartan sin enviar las partidas en cola con más de 24 h (MAX_PENDING_RUN_AGE_MS). Si al descartar o rechazar se vacía la cola sin puntuación del servidor, onResync vuelve a leer la puntuación del servidor.

### Reason
Antes, un corte de red al terminar la partida la perdía para siempre. El runId hace que el reintento sea idempotente (applySoloRun).

### Límite conocido
Las 24 h se miden con el reloj del dispositivo: el servidor NO puede hacerlas cumplir. El servidor solo recalcula puntos y XP a partir de los contadores y rechaza lo imposible; no puede verificar que la partida existió.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/data/PendingRunSync.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/data/PendingSoloRun.kt
- app/src/main/kotlin/com/softyorch/stroopoverload/data/local/PendingRunStore.kt
- functions/src/index.ts
- functions/src/profileScoring.ts

## Modo solo OVERTIME (contrarreloj que se amplía)

- id: modo-solo-overtime-contrarreloj-que-se-ampl-a-20260929-142904
- type: architecture_decision
- status: active
- platform: shared
- area: game_modes
- date: 2026-09-29

### Decision
PR #5 (8294142 + 9e16ebd, 2026-09-27). Empieza con 30 s; un acierto suma 1 s en el nivel 1, 0,1 s menos por nivel y 0,3 s como mínimo; un fallo resta 2 s. Los casos especiales de TIME pasan por GameMode.hasSessionClock, así que OVERTIME tampoco usa temporizador por estímulo y queda fuera de los trofeos de supervivencia y del bonus de XP de supervivencia.
Servidor: la duración se acota a 30 s + correctHits * 1 s (+ margen); sin bonus de supervivencia.

### Reason
Con +1 s fijo, cualquiera que respondiera en menos de un segundo jugaría para siempre. Los 30 s iniciales son gratis: si dieran XP o trofeos de supervivencia, bastaría con dejar pasar el tiempo sin jugar.

### Files
- app/src/main/kotlin/com/softyorch/stroopoverload/game/GameState.kt
- functions/src/profileScoring.ts

## El distractor hablado empieza en el nivel 15

- id: el-distractor-hablado-empieza-en-el-nivel-15-20260929-142904
- type: architecture_decision
- status: active
- platform: android
- area: gameplay
- date: 2026-09-29

### Decision
015586e (PR #5, 2026-09-27). El nombre de color distractor que se dice en voz alta (efecto Stroop auditivo) empieza en el nivel 15; antes lo hacía en el 5. Ya no se pide el distractor de fondo de nivel 3, que no se usaba.

### Reason
En el nivel 5 se percibía como ruido aleatorio.

## CI en GitHub Actions para los PR a develop/main

- id: ci-en-github-actions-para-los-pr-a-develop-main-20260929-142904
- type: architecture_decision
- status: active
- platform: shared
- area: ci
- date: 2026-09-29

### Decision
9a5c0e5 (PR #6, 2026-09-28). Dos jobs en paralelo, en los PR y en los push a develop/main:
- android: testDebugUnitTest lintDebug assembleDebug --continue, con un google-services.json de relleno (el real está en .gitignore; no hace falta ningún secreto).
- functions: eslint, tsc y jest sobre el emulador de Firestore con un proyecto demo-, así que no busca credenciales.
Las acciones van fijadas por SHA y las mantiene Dependabot. La caché de Gradle es de solo lectura en los PR; se escribe en develop/main. gradlew es ejecutable en git.
develop todavía NO tiene reglas de protección de rama (2026-09-29).

### Files
- .github/workflows/ci.yml
- .github/ci/google-services.placeholder.json
