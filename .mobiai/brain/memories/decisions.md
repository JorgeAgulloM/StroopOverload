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
- status: active
- platform: android
- area: scoring
- date: 2026-09-23

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
- status: active
- platform: shared
- area: project_status
- date: 2026-09-23

13 commits en develop SIN push y SIN desplegar (git log --oneline 70fc64f..HEAD). Árbol limpio. functions 191/191, Kotlin 88/88, assembleRelease verificado.
Hecho: #1 salas atascadas, #2 puntuación autoritativa en servidor, #3 App Check + límites de uso, #4 Node 22, #5 CancellationException, #6 i18n + guard de paridad, #7/#8 recomposición de temporizadores, #9 confirmación al salir, #10 I/O fuera de composición, #11 firma opcional + reglas R8 verificadas.
Siguiente: #12 — extraer interfaces de AuthService y FirebaseGameRepository para poder testear AuthViewModel y ProfileViewModel (login, registro, cambio de contraseña, borrado de cuenta).
ANTES DE DESPLEGAR: activar Cloud Scheduler, registrar Play Integrity (SHA-256 ed456dc64112d436b6c77ae3ecc6f2bad50dc75400be33c8cdd24ddcdd9f1105), desplegar reglas+functions+app JUNTAS, y dejar ENFORCE_APP_CHECK en false hasta que la base instalada tenga el cliente nuevo. Checklist completa en .claude/TASK_PROGRESS.md.

### Files
- .claude/TASK_PROGRESS.md
