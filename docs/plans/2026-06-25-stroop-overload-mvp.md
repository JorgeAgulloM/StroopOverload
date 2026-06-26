# StroopOverload MVP Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build StroopOverload MVP — Stroop effect cognitive game on Flutter + Flame with Firebase backend, leaderboards, achievements, and viral sharing.

**Architecture:** Flame game loop hosted inside `GameWidget` inside a Flutter screen. `IncongruenceEngine` singleton generates conflict stimuli with guaranteed word/ink/audio incongruence. `GameStateManager` FSM routes Menu → Playing → GameOver. `FirebaseGameRepository` handles all backend I/O with batched Firestore writes. Screenshot captured via `RepaintBoundary` at the exact game-over frame before screen transition; bytes passed to `GameOverScreen` for share intent.

**Tech Stack:** Flutter 3.29+, Dart 3.7+, Flame 1.20+, FlameAudio 2.12+, Firebase Auth 5.4+, Cloud Firestore 5.5+, share_plus 10.3+, path_provider 2.1+

**Platform:** Flutter (Android primary, iOS ready)

---

## Dependency Graph

```
Task 1 (setup) → all
Task 2 (models) → Tasks 3,4,5,6,7,8,9,10
Task 3 (colors) → Tasks 4,5,6
Task 4 (engine) → Task 5
Task 5 (FSM) → Tasks 5,9
Task 6 (Flame loop) → Tasks 7,9
Task 7 (components) → Task 9
Task 8 (Firebase auth) → Task 10
Task 9 (Firestore repo) → Task 10
Task 10 (screens + share) ← depends on all
```

Tasks 8 and 9 (Firebase) can run in parallel with Tasks 4–7 (game engine).

---

### Task 1: Project Setup & Dependencies

**Files:**
- Create: `pubspec.yaml` (full project)
- Create: `lib/main.dart`
- Create: `lib/core/constants/game_config.dart`
- Create: `assets/audio/` directory with placeholder audio

- [ ] **Step 1: Scaffold Flutter project at repo root**

```bash
flutter create . --org com.stroopoverload --project-name stroop_overload --platforms android,ios
```

Expected: Flutter project files created in current directory. `.gitignore`, `android/`, `ios/`, `lib/`, `test/` appear.

- [ ] **Step 2: Replace pubspec.yaml**

`pubspec.yaml`:
```yaml
name: stroop_overload
description: StroopOverload — cognitive speed game
version: 1.0.0+1

environment:
  sdk: ">=3.7.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  flame: ^1.20.0
  flame_audio: ^2.12.0
  firebase_core: ^3.8.0
  firebase_auth: ^5.4.0
  cloud_firestore: ^5.5.0
  share_plus: ^10.3.0
  path_provider: ^2.1.4

dev_dependencies:
  flutter_test:
    sdk: flutter
  flame_test: ^1.20.0
  flutter_lints: ^5.0.0
  mockito: ^5.4.4
  build_runner: ^2.4.13
  fake_cloud_firestore: ^3.0.0
  firebase_auth_mocks: ^0.14.0

flutter:
  uses-material-design: true
  assets:
    - assets/audio/
```

- [ ] **Step 3: Create asset directories**

```bash
mkdir -p assets/audio
# Placeholder silent files — replace with real voice recordings before production
for color in red green blue yellow; do
  touch "assets/audio/${color}.mp3"
done
```

Expected: `assets/audio/red.mp3`, `green.mp3`, `blue.mp3`, `yellow.mp3` exist.

- [ ] **Step 4: Create game config constants**

`lib/core/constants/game_config.dart`:
```dart
abstract final class GameConfig {
  static const double initialTimeLimit = 3.0;
  static const double timeLimitDecayPerLevel = 0.15;
  static const double minimumTimeLimit = 0.8;
  static const int levelsPerDifficulty = 5;
  static const int pointsPerCorrect = 100;
  static const int leaderboardLimit = 50;
}
```

- [ ] **Step 5: Get dependencies**

```bash
flutter pub get
```

Expected: All packages resolved with no version conflicts.

- [ ] **Step 6: Verify build baseline**

```bash
flutter build apk --debug
```

Expected: `Build succeeded.`

- [ ] **Step 7: Commit**

```bash
git add pubspec.yaml pubspec.lock lib/core/ assets/ .gitignore
git commit -m "feat: project setup with Flame + Firebase dependencies"
```

---

### Task 2: Stroop Color System & Domain Models

**Files:**
- Create: `lib/core/stroop_color.dart`
- Create: `lib/domain/stroop_stimulus.dart`
- Create: `lib/domain/game_result.dart`
- Create: `lib/domain/user_profile.dart`
- Create: `lib/domain/achievement.dart`
- Create: `test/domain/stroop_color_test.dart`
- Create: `test/domain/stroop_stimulus_test.dart`

- [ ] **Step 1: Write failing tests for StroopColor**

`test/domain/stroop_color_test.dart`:
```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/core/stroop_color.dart';

void main() {
  group('StroopColor', () {
    test('displayName returns uppercase color name', () {
      expect(StroopColor.red.displayName, 'RED');
      expect(StroopColor.blue.displayName, 'BLUE');
      expect(StroopColor.green.displayName, 'GREEN');
      expect(StroopColor.yellow.displayName, 'YELLOW');
    });

    test('audioAsset returns correct mp3 filename', () {
      expect(StroopColor.green.audioAsset, 'green.mp3');
      expect(StroopColor.yellow.audioAsset, 'yellow.mp3');
    });

    test('exactly four colors are defined', () {
      expect(StroopColor.values.length, 4);
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/domain/stroop_color_test.dart
```

Expected: FAIL — `stroop_color.dart` not found.

- [ ] **Step 3: Implement StroopColor**

`lib/core/stroop_color.dart`:
```dart
import 'dart:ui';

enum StroopColor {
  red,
  green,
  blue,
  yellow;

  String get displayName => name.toUpperCase();

  String get audioAsset => '$name.mp3';

  Color get flameColor => switch (this) {
        StroopColor.red    => const Color(0xFFFF2D2D),
        StroopColor.green  => const Color(0xFF39FF14),
        StroopColor.blue   => const Color(0xFF1F8FFF),
        StroopColor.yellow => const Color(0xFFFFE600),
      };
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
flutter test test/domain/stroop_color_test.dart
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Write failing tests for StroopStimulus**

`test/domain/stroop_stimulus_test.dart`:
```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/core/stroop_color.dart';
import 'package:stroop_overload/domain/stroop_stimulus.dart';

void main() {
  group('StroopStimulus', () {
    test('correctAnswer always equals inkColor', () {
      final s = StroopStimulus(
        wordLabel: StroopColor.red,
        inkColor: StroopColor.blue,
      );
      expect(s.correctAnswer, StroopColor.blue);
    });

    test('isIncongruent is true when wordLabel differs from inkColor', () {
      final s = StroopStimulus(
        wordLabel: StroopColor.red,
        inkColor: StroopColor.green,
      );
      expect(s.isIncongruent, isTrue);
    });

    test('isIncongruent is false when wordLabel equals inkColor', () {
      final s = StroopStimulus(
        wordLabel: StroopColor.red,
        inkColor: StroopColor.red,
      );
      expect(s.isIncongruent, isFalse);
    });
  });
}
```

- [ ] **Step 6: Run test to verify it fails**

```bash
flutter test test/domain/stroop_stimulus_test.dart
```

Expected: FAIL — `stroop_stimulus.dart` not found.

- [ ] **Step 7: Implement StroopStimulus and remaining domain models**

`lib/domain/stroop_stimulus.dart`:
```dart
import '../core/stroop_color.dart';

class StroopStimulus {
  const StroopStimulus({
    required this.wordLabel,
    required this.inkColor,
    this.audioColor,
    this.bgDistractor,
  });

  final StroopColor wordLabel;
  final StroopColor inkColor;
  final StroopColor? audioColor;
  final StroopColor? bgDistractor;

  StroopColor get correctAnswer => inkColor;
  bool get isIncongruent => wordLabel != inkColor;
}
```

`lib/domain/game_result.dart`:
```dart
class GameResult {
  const GameResult({
    required this.finalScore,
    required this.correctHits,
    required this.totalRounds,
    required this.survivalSeconds,
    required this.previousHighScore,
  });

  final int finalScore;
  final int correctHits;
  final int totalRounds;
  final double survivalSeconds;
  final int previousHighScore;

  int get accuracy =>
      totalRounds == 0 ? 0 : ((correctHits / totalRounds) * 100).round();

  int get xpEarned => (survivalSeconds * 10).round();

  bool get isNewHighScore => finalScore > previousHighScore;
}
```

`lib/domain/achievement.dart`:
```dart
class Achievement {
  const Achievement({required this.id, required this.unlockedAt});

  final String id;
  final DateTime unlockedAt;

  Map<String, dynamic> toMap() => {
        'id': id,
        'unlockedAt': unlockedAt.toIso8601String(),
      };

  factory Achievement.fromMap(Map<String, dynamic> map) => Achievement(
        id: map['id'] as String,
        unlockedAt: DateTime.parse(map['unlockedAt'] as String),
      );
}
```

`lib/domain/user_profile.dart`:
```dart
import 'achievement.dart';

class UserProfile {
  const UserProfile({
    required this.uid,
    required this.displayName,
    required this.isAnonymous,
    required this.highScore,
    required this.totalXP,
    required this.level,
    required this.unlockedPalettes,
    required this.achievements,
  });

  final String uid;
  final String displayName;
  final bool isAnonymous;
  final int highScore;
  final int totalXP;
  final int level;
  final List<String> unlockedPalettes;
  final List<Achievement> achievements;

  factory UserProfile.initial(String uid) => UserProfile(
        uid: uid,
        displayName: 'Guest_${uid.substring(0, 4).toUpperCase()}',
        isAnonymous: true,
        highScore: 0,
        totalXP: 0,
        level: 1,
        unlockedPalettes: const ['default'],
        achievements: const [],
      );
}
```

- [ ] **Step 8: Run all domain tests**

```bash
flutter test test/domain/
```

Expected: PASS — 6 tests.

- [ ] **Step 9: Commit**

```bash
git add lib/core/stroop_color.dart lib/domain/ test/domain/
git commit -m "feat: stroop color system and domain models"
```

---

### Task 3: Incongruence Engine (TASK-102)

**Files:**
- Create: `lib/game/services/incongruence_engine.dart`
- Create: `test/game/incongruence_engine_test.dart`

- [ ] **Step 1: Write failing tests**

`test/game/incongruence_engine_test.dart`:
```dart
import 'dart:math';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/core/stroop_color.dart';
import 'package:stroop_overload/game/services/incongruence_engine.dart';

void main() {
  group('IncongruenceEngine', () {
    late IncongruenceEngine engine;

    setUp(() => engine = IncongruenceEngine(rng: Random(42)));

    test('level 1 — inkColor always differs from wordLabel (100 runs)', () {
      for (var i = 0; i < 100; i++) {
        final s = engine.generate(difficultyLevel: 1);
        expect(s.inkColor, isNot(s.wordLabel),
            reason: 'Stimulus must be incongruent at level 1');
      }
    });

    test('level 1 — no audio or bg distractor', () {
      final s = engine.generate(difficultyLevel: 1);
      expect(s.audioColor, isNull);
      expect(s.bgDistractor, isNull);
    });

    test('level 2 — audioColor present and differs from inkColor (100 runs)', () {
      for (var i = 0; i < 100; i++) {
        final s = engine.generate(difficultyLevel: 2);
        expect(s.audioColor, isNotNull);
        expect(s.audioColor, isNot(s.inkColor));
      }
    });

    test('level 3 — bgDistractor present and differs from inkColor (100 runs)', () {
      for (var i = 0; i < 100; i++) {
        final s = engine.generate(difficultyLevel: 3);
        expect(s.bgDistractor, isNotNull);
        expect(s.bgDistractor, isNot(s.inkColor));
      }
    });

    test('correctAnswer is always inkColor across all levels', () {
      for (var level = 1; level <= 3; level++) {
        for (var i = 0; i < 50; i++) {
          final s = engine.generate(difficultyLevel: level);
          expect(s.correctAnswer, s.inkColor);
        }
      }
    });

    test('all four colors appear as inkColor across 200 stimuli', () {
      final seen = <StroopColor>{};
      for (var i = 0; i < 200; i++) {
        seen.add(engine.generate(difficultyLevel: 1).inkColor);
      }
      expect(seen, containsAll(StroopColor.values));
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/game/incongruence_engine_test.dart
```

Expected: FAIL — `incongruence_engine.dart` not found.

- [ ] **Step 3: Implement IncongruenceEngine**

`lib/game/services/incongruence_engine.dart`:
```dart
import 'dart:math';
import '../../core/stroop_color.dart';
import '../../domain/stroop_stimulus.dart';

class IncongruenceEngine {
  IncongruenceEngine({Random? rng}) : _rng = rng ?? Random();

  final Random _rng;

  StroopStimulus generate({required int difficultyLevel}) {
    final inkColor = _pick(exclude: []);
    final wordLabel = _pick(exclude: [inkColor]);

    final audioColor =
        difficultyLevel >= 2 ? _pick(exclude: [inkColor, wordLabel]) : null;

    final bgDistractor =
        difficultyLevel >= 3 ? _pick(exclude: [inkColor, wordLabel]) : null;

    return StroopStimulus(
      inkColor: inkColor,
      wordLabel: wordLabel,
      audioColor: audioColor,
      bgDistractor: bgDistractor,
    );
  }

  StroopColor _pick({required List<StroopColor> exclude}) {
    final pool =
        StroopColor.values.where((c) => !exclude.contains(c)).toList();
    // Fallback should never trigger in normal usage (exclude can be at most 3 of 4 colors)
    if (pool.isEmpty) return StroopColor.values[_rng.nextInt(4)];
    return pool[_rng.nextInt(pool.length)];
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
flutter test test/game/incongruence_engine_test.dart
```

Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add lib/game/services/ test/game/incongruence_engine_test.dart
git commit -m "feat: incongruence engine with deterministic conflict matrix (TASK-102)"
```

---

### Task 4: Game State Machine (TASK-104)

**Files:**
- Create: `lib/game/states/game_state.dart`
- Create: `lib/game/game_state_manager.dart`
- Create: `test/game/game_state_manager_test.dart`

- [ ] **Step 1: Write failing tests**

`test/game/game_state_manager_test.dart`:
```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/domain/game_result.dart';
import 'package:stroop_overload/game/game_state_manager.dart';
import 'package:stroop_overload/game/states/game_state.dart';

const _dummyResult = GameResult(
  finalScore: 500,
  correctHits: 5,
  totalRounds: 6,
  survivalSeconds: 12.0,
  previousHighScore: 0,
);

void main() {
  late GameStateManager manager;

  setUp(() => manager = GameStateManager());
  tearDown(() => manager.dispose());

  group('GameStateManager', () {
    test('starts in MenuState', () {
      expect(manager.current, isA<MenuState>());
    });

    test('startGame transitions to PlayingState', () {
      manager.startGame();
      expect(manager.current, isA<PlayingState>());
    });

    test('PlayingState initial score is 0 and level is 1', () {
      manager.startGame();
      final s = manager.current as PlayingState;
      expect(s.score, 0);
      expect(s.level, 1);
    });

    test('endGame transitions to GameOverState with correct result', () {
      manager.startGame();
      manager.endGame(result: _dummyResult);
      final s = manager.current as GameOverState;
      expect(s.result.finalScore, 500);
    });

    test('returnToMenu transitions back to MenuState', () {
      manager.startGame();
      manager.endGame(result: _dummyResult);
      manager.returnToMenu();
      expect(manager.current, isA<MenuState>());
    });

    test('stateStream emits each transition in order', () async {
      expectLater(
        manager.stateStream,
        emitsInOrder([isA<PlayingState>(), isA<GameOverState>()]),
      );
      manager.startGame();
      manager.endGame(result: _dummyResult);
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/game/game_state_manager_test.dart
```

Expected: FAIL — files not found.

- [ ] **Step 3: Implement game states**

`lib/game/states/game_state.dart`:
```dart
import '../../domain/game_result.dart';

sealed class GameState {
  const GameState();
}

final class MenuState extends GameState {
  const MenuState();
}

final class PlayingState extends GameState {
  const PlayingState({
    this.score = 0,
    this.level = 1,
    this.correctHits = 0,
    this.totalRounds = 0,
    this.survivalSeconds = 0.0,
  });

  final int score;
  final int level;
  final int correctHits;
  final int totalRounds;
  final double survivalSeconds;

  PlayingState copyWith({
    int? score,
    int? level,
    int? correctHits,
    int? totalRounds,
    double? survivalSeconds,
  }) =>
      PlayingState(
        score: score ?? this.score,
        level: level ?? this.level,
        correctHits: correctHits ?? this.correctHits,
        totalRounds: totalRounds ?? this.totalRounds,
        survivalSeconds: survivalSeconds ?? this.survivalSeconds,
      );
}

final class GameOverState extends GameState {
  const GameOverState({required this.result});
  final GameResult result;
}
```

- [ ] **Step 4: Implement GameStateManager**

`lib/game/game_state_manager.dart`:
```dart
import 'dart:async';
import 'states/game_state.dart';
import '../domain/game_result.dart';

class GameStateManager {
  GameStateManager() : _state = const MenuState();

  GameState _state;
  final _controller = StreamController<GameState>.broadcast();

  GameState get current => _state;
  Stream<GameState> get stateStream => _controller.stream;

  void startGame() => _emit(const PlayingState());

  void endGame({required GameResult result}) =>
      _emit(GameOverState(result: result));

  void returnToMenu() => _emit(const MenuState());

  void _emit(GameState next) {
    _state = next;
    _controller.add(next);
  }

  void dispose() => _controller.close();
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
flutter test test/game/game_state_manager_test.dart
```

Expected: PASS — 6 tests.

- [ ] **Step 6: Commit**

```bash
git add lib/game/states/ lib/game/game_state_manager.dart test/game/game_state_manager_test.dart
git commit -m "feat: game state machine Menu→Playing→GameOver (TASK-104)"
```

---

### Task 5: Flame Game Loop (TASK-101)

**Files:**
- Create: `lib/game/stroop_game.dart`
- Create: `test/game/stroop_game_test.dart`

- [ ] **Step 1: Write failing tests**

`test/game/stroop_game_test.dart`:
```dart
import 'package:flame_test/flame_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/core/constants/game_config.dart';
import 'package:stroop_overload/game/stroop_game.dart';

void main() {
  group('StroopGame', () {
    testWithGame<StroopGame>(
      'starts not playing before explicit startPlay call',
      StroopGame.new,
      (game) async {
        expect(game.isPlaying, isFalse);
      },
    );

    testWithGame<StroopGame>(
      'startPlay sets isPlaying to true and resets timeElapsed',
      StroopGame.new,
      (game) async {
        game.startPlay();
        expect(game.isPlaying, isTrue);
        expect(game.timeElapsed, 0.0);
      },
    );

    testWithGame<StroopGame>(
      'timeLimitForLevel decays per level but never drops below minimum',
      StroopGame.new,
      (game) async {
        expect(game.timeLimitForLevel(1), GameConfig.initialTimeLimit);
        expect(
          game.timeLimitForLevel(100),
          greaterThanOrEqualTo(GameConfig.minimumTimeLimit),
        );
      },
    );
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/game/stroop_game_test.dart
```

Expected: FAIL — `stroop_game.dart` not found.

- [ ] **Step 3: Implement StroopGame**

`lib/game/stroop_game.dart`:
```dart
import 'package:flame/game.dart';
import 'package:flame_audio/flame_audio.dart';
import '../core/constants/game_config.dart';
import '../core/stroop_color.dart';
import '../domain/game_result.dart';
import '../domain/stroop_stimulus.dart';
import 'components/quadrant_component.dart';
import 'components/stimulus_component.dart';
import 'components/timer_bar_component.dart';
import 'game_state_manager.dart';
import 'services/incongruence_engine.dart';

class StroopGame extends FlameGame {
  StroopGame({
    IncongruenceEngine? engine,
    GameStateManager? stateManager,
  })  : _engine = engine ?? IncongruenceEngine(),
        _stateManager = stateManager ?? GameStateManager();

  final IncongruenceEngine _engine;
  final GameStateManager _stateManager;

  StroopStimulus? _currentStimulus;
  double _timeElapsed = 0.0;
  int _score = 0;
  int _level = 1;
  int _correctHits = 0;
  int _totalRounds = 0;
  double _totalSurvivalTime = 0.0;
  int _previousHighScore = 0;
  bool _isPlaying = false;

  bool get isPlaying => _isPlaying;
  double get timeElapsed => _timeElapsed;
  GameStateManager get stateManager => _stateManager;

  void Function(GameResult)? onGameOver;

  late StimulusComponent _stimulusComponent;
  late TimerBarComponent _timerBar;
  final List<QuadrantComponent> _quadrants = [];

  @override
  Future<void> onLoad() async {
    await super.onLoad();
    await FlameAudio.audioCache.loadAll([
      for (final c in StroopColor.values) c.audioAsset,
    ]);
    _buildComponents();
  }

  void _buildComponents() {
    final half = size / 2;
    final positions = [
      Vector2.zero(),
      Vector2(half.x, 0),
      Vector2(0, half.y),
      Vector2(half.x, half.y),
    ];

    for (var i = 0; i < StroopColor.values.length; i++) {
      final color = StroopColor.values[i];
      final quad = QuadrantComponent(
        color: color,
        position: positions[i],
        size: half,
        onTap: () => _handleTap(color),
      );
      _quadrants.add(quad);
      add(quad);
    }

    _stimulusComponent = StimulusComponent(position: size / 2);
    add(_stimulusComponent);

    _timerBar = TimerBarComponent(position: Vector2(0, size.y - 10), barWidth: size.x);
    add(_timerBar);
  }

  void startPlay({int previousHighScore = 0}) {
    _previousHighScore = previousHighScore;
    _isPlaying = true;
    _score = 0;
    _level = 1;
    _correctHits = 0;
    _totalRounds = 0;
    _totalSurvivalTime = 0.0;
    _stateManager.startGame();
    _nextStimulus();
  }

  @override
  void update(double dt) {
    super.update(dt);
    if (!_isPlaying) return;

    _timeElapsed += dt;
    _totalSurvivalTime += dt;
    _timerBar.progress = 1.0 - (_timeElapsed / timeLimitForLevel(_level));

    if (_timeElapsed >= timeLimitForLevel(_level)) {
      _endGame();
    }
  }

  double timeLimitForLevel(int level) {
    final t = GameConfig.initialTimeLimit -
        (level - 1) * GameConfig.timeLimitDecayPerLevel;
    return t.clamp(GameConfig.minimumTimeLimit, GameConfig.initialTimeLimit);
  }

  int get _difficultyTier {
    if (_level >= 10) return 3;
    if (_level >= 5) return 2;
    return 1;
  }

  void _nextStimulus() {
    _currentStimulus = _engine.generate(difficultyLevel: _difficultyTier);
    _timeElapsed = 0.0;
    _totalRounds++;
    _stimulusComponent.setStimulus(_currentStimulus!);

    final audio = _currentStimulus!.audioColor;
    if (audio != null) FlameAudio.play(audio.audioAsset);
  }

  void _handleTap(StroopColor tapped) {
    if (!_isPlaying || _currentStimulus == null) return;
    if (tapped == _currentStimulus!.correctAnswer) {
      _correctHits++;
      _score += GameConfig.pointsPerCorrect;
      _level = (_totalRounds ~/ GameConfig.levelsPerDifficulty) + 1;
      _nextStimulus();
    } else {
      _endGame();
    }
  }

  void _endGame() {
    _isPlaying = false;
    final result = GameResult(
      finalScore: _score,
      correctHits: _correctHits,
      totalRounds: _totalRounds,
      survivalSeconds: _totalSurvivalTime,
      previousHighScore: _previousHighScore,
    );
    _stateManager.endGame(result: result);
    onGameOver?.call(result);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
flutter test test/game/stroop_game_test.dart
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add lib/game/stroop_game.dart test/game/stroop_game_test.dart
git commit -m "feat: Flame game loop with delta-time timer decay (TASK-101)"
```

---

### Task 6: Game Components (TASK-103)

**Files:**
- Create: `lib/game/components/quadrant_component.dart`
- Create: `lib/game/components/stimulus_component.dart`
- Create: `lib/game/components/timer_bar_component.dart`
- Create: `test/game/components/quadrant_component_test.dart`

- [ ] **Step 1: Write failing test for QuadrantComponent**

`test/game/components/quadrant_component_test.dart`:
```dart
import 'package:flame/components.dart';
import 'package:flame_test/flame_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/core/stroop_color.dart';
import 'package:stroop_overload/game/components/quadrant_component.dart';
import 'package:stroop_overload/game/stroop_game.dart';

void main() {
  testWithGame<StroopGame>(
    'QuadrantComponent stores correct color and size',
    StroopGame.new,
    (game) async {
      var tapCount = 0;
      final quad = QuadrantComponent(
        color: StroopColor.red,
        position: Vector2.zero(),
        size: Vector2(200, 300),
        onTap: () => tapCount++,
      );
      await game.add(quad);
      await game.ready();

      expect(quad.color, StroopColor.red);
      expect(quad.size, Vector2(200, 300));
    },
  );
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/game/components/quadrant_component_test.dart
```

Expected: FAIL — component files not found.

- [ ] **Step 3: Implement QuadrantComponent**

`lib/game/components/quadrant_component.dart`:
```dart
import 'dart:ui';
import 'package:flame/components.dart';
import 'package:flame/events.dart';
import '../../core/stroop_color.dart';

class QuadrantComponent extends PositionComponent with TapCallbacks {
  QuadrantComponent({
    required this.color,
    required super.position,
    required super.size,
    required this.onTap,
  });

  final StroopColor color;
  final void Function() onTap;

  static const double _fontSize = 28.0;

  @override
  void render(Canvas canvas) {
    canvas.drawRect(size.toRect(), Paint()..color = color.flameColor);

    canvas.drawRect(
      size.toRect(),
      Paint()
        ..color = const Color(0x33000000)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.0,
    );

    final tp = TextPainter(
      text: TextSpan(
        text: color.displayName,
        style: TextStyle(
          fontSize: _fontSize,
          fontWeight: FontWeight.bold,
          color: _contrastFor(color.flameColor),
          letterSpacing: 2.0,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: size.x);

    tp.paint(
      canvas,
      Offset((size.x - tp.width) / 2, (size.y - tp.height) / 2),
    );
  }

  @override
  bool onTapDown(TapDownEvent event) {
    onTap();
    return true;
  }

  Color _contrastFor(Color bg) =>
      bg.computeLuminance() > 0.4
          ? const Color(0xFF0A0A0A)
          : const Color(0xFFF0F0F0);
}
```

- [ ] **Step 4: Implement StimulusComponent**

`lib/game/components/stimulus_component.dart`:
```dart
import 'dart:ui';
import 'package:flame/components.dart';
import '../../core/stroop_color.dart';
import '../../domain/stroop_stimulus.dart';

class StimulusComponent extends PositionComponent {
  StimulusComponent({required super.position}) : super(anchor: Anchor.center);

  StroopStimulus? _stimulus;
  Color _bgTint = const Color(0x00000000);

  static const double _fontSize = 52.0;

  void setStimulus(StroopStimulus stimulus) {
    _stimulus = stimulus;
    _bgTint = const Color(0x00000000);
  }

  void setBackground(StroopColor distractor) {
    // 15% opacity tint — visible distraction without obscuring text
    _bgTint = Color.fromARGB(38, distractor.flameColor.red,
        distractor.flameColor.green, distractor.flameColor.blue);
  }

  @override
  void render(Canvas canvas) {
    if (_stimulus == null) return;

    const rect = Rect.fromLTWH(-130, -45, 260, 90);
    canvas.drawRRect(
      RRect.fromRectAndRadius(rect, const Radius.circular(12)),
      Paint()..color = _bgTint,
    );

    final tp = TextPainter(
      text: TextSpan(
        text: _stimulus!.wordLabel.displayName,
        style: TextStyle(
          fontSize: _fontSize,
          fontWeight: FontWeight.w900,
          color: _stimulus!.inkColor.flameColor,
          letterSpacing: 3.0,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();

    tp.paint(canvas, Offset(-tp.width / 2, -tp.height / 2));
  }
}
```

- [ ] **Step 5: Implement TimerBarComponent**

`lib/game/components/timer_bar_component.dart`:
```dart
import 'dart:ui';
import 'package:flame/components.dart';

class TimerBarComponent extends PositionComponent {
  TimerBarComponent({required super.position, required this.barWidth});

  final double barWidth;
  double progress = 1.0;

  static const double _height = 8.0;
  static const _colorFull = Color(0xFF39FF14);
  static const _colorUrgent = Color(0xFFFF2D2D);
  static const _colorTrack = Color(0xFF1A1A1A);

  @override
  void render(Canvas canvas) {
    canvas.drawRect(Rect.fromLTWH(0, 0, barWidth, _height),
        Paint()..color = _colorTrack);

    canvas.drawRect(
      Rect.fromLTWH(0, 0, barWidth * progress.clamp(0.0, 1.0), _height),
      Paint()..color = Color.lerp(_colorUrgent, _colorFull, progress)!,
    );
  }
}
```

- [ ] **Step 6: Run tests**

```bash
flutter test test/game/components/
```

Expected: PASS — 1 test.

- [ ] **Step 7: Build verify**

```bash
flutter build apk --debug
```

Expected: `Build succeeded.`

- [ ] **Step 8: Commit**

```bash
git add lib/game/components/ test/game/components/
git commit -m "feat: quadrant, stimulus, timer bar components with TapCallbacks (TASK-103)"
```

---

### Task 7: Firebase Auth Service

**Files:**
- Create: `lib/firebase/auth_service.dart`
- Create: `test/firebase/auth_service_test.dart`
- Modify: `android/build.gradle` (project-level classpath)
- Modify: `android/app/build.gradle` (apply plugin)

- [ ] **Step 1: Configure Firebase Android**

In Firebase console: add Android app with package `com.stroopoverload.stroop_overload`, download `google-services.json`, copy to `android/app/google-services.json`.

Add to `android/build.gradle` inside `buildscript > dependencies`:
```groovy
classpath 'com.google.gms:google-services:4.4.2'
```

Add to bottom of `android/app/build.gradle`:
```groovy
apply plugin: 'com.google.gms.google-services'
```

- [ ] **Step 2: Write failing tests**

`test/firebase/auth_service_test.dart`:
```dart
import 'package:firebase_auth_mocks/firebase_auth_mocks.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/firebase/auth_service.dart';

void main() {
  group('AuthService', () {
    late AuthService service;
    late MockFirebaseAuth mockAuth;

    setUp(() {
      mockAuth = MockFirebaseAuth();
      service = AuthService(auth: mockAuth);
    });

    test('signInAnonymously returns a non-empty uid', () async {
      final uid = await service.signInAnonymously();
      expect(uid, isNotEmpty);
    });

    test('currentUid is null before sign in', () {
      expect(service.currentUid, isNull);
    });

    test('currentUid is non-null after sign in', () async {
      await service.signInAnonymously();
      expect(service.currentUid, isNotNull);
    });
  });
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
flutter test test/firebase/auth_service_test.dart
```

Expected: FAIL — `auth_service.dart` not found.

- [ ] **Step 4: Implement AuthService**

`lib/firebase/auth_service.dart`:
```dart
import 'package:firebase_auth/firebase_auth.dart';

class AuthService {
  AuthService({FirebaseAuth? auth}) : _auth = auth ?? FirebaseAuth.instance;

  final FirebaseAuth _auth;

  String? get currentUid => _auth.currentUser?.uid;

  Future<String> signInAnonymously() async {
    final credential = await _auth.signInAnonymously();
    return credential.user!.uid;
  }
}
```

- [ ] **Step 5: Initialize Firebase in main.dart**

`lib/main.dart`:
```dart
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  runApp(const StroopApp());
}
```

- [ ] **Step 6: Run tests**

```bash
flutter test test/firebase/auth_service_test.dart
```

Expected: PASS — 3 tests.

- [ ] **Step 7: Commit**

```bash
git add lib/firebase/ lib/main.dart test/firebase/ android/
git commit -m "feat: Firebase anonymous auth service (TASK-105 auth)"
```

---

### Task 8: Firestore Repository (TASK-105)

**Files:**
- Create: `lib/domain/repositories/game_repository.dart`
- Create: `lib/data/repositories/firebase_game_repository.dart`
- Create: `test/data/firebase_game_repository_test.dart`

- [ ] **Step 1: Write failing tests**

`test/data/firebase_game_repository_test.dart`:
```dart
import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/data/repositories/firebase_game_repository.dart';
import 'package:stroop_overload/domain/game_result.dart';

void main() {
  late FakeFirebaseFirestore fakeDb;
  late FirebaseGameRepository repo;

  setUp(() {
    fakeDb = FakeFirebaseFirestore();
    repo = FirebaseGameRepository(firestore: fakeDb);
  });

  group('FirebaseGameRepository', () {
    test('createUser writes initial profile with highScore 0', () async {
      await repo.createUser(uid: 'u1', displayName: 'Guest_TEST');
      final doc = await fakeDb.collection('users').doc('u1').get();
      expect(doc.exists, isTrue);
      expect(doc.data()!['highScore'], 0);
      expect(doc.data()!['displayName'], 'Guest_TEST');
    });

    test('saveGameResult updates highScore when new score is higher', () async {
      await repo.createUser(uid: 'u2', displayName: 'Guest_XYZW');
      await repo.saveGameResult(
        uid: 'u2',
        result: const GameResult(
          finalScore: 800,
          correctHits: 8,
          totalRounds: 10,
          survivalSeconds: 20.0,
          previousHighScore: 300,
        ),
      );
      final doc = await fakeDb.collection('users').doc('u2').get();
      expect(doc.data()!['highScore'], 800);
    });

    test('saveGameResult does NOT downgrade an existing highScore', () async {
      await repo.createUser(uid: 'u3', displayName: 'Guest_ABCD');
      await fakeDb
          .collection('users')
          .doc('u3')
          .update({'highScore': 1000});

      await repo.saveGameResult(
        uid: 'u3',
        result: const GameResult(
          finalScore: 200,
          correctHits: 2,
          totalRounds: 3,
          survivalSeconds: 5.0,
          previousHighScore: 1000,
        ),
      );
      final doc = await fakeDb.collection('users').doc('u3').get();
      expect(doc.data()!['highScore'], 1000);
    });

    test('getLeaderboard returns entries ordered highest first', () async {
      for (var i = 0; i < 5; i++) {
        await fakeDb.collection('users').doc('user_$i').set({
          'displayName': 'User_$i',
          'highScore': i * 100,
          'totalXP': 0,
          'level': 1,
          'isAnonymous': true,
          'unlockedPalettes': ['default'],
          'achievements': <dynamic>[],
        });
      }
      final board = await repo.getLeaderboard();
      expect(board.length, 5);
      expect(board.first.highScore, 400);
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
flutter test test/data/firebase_game_repository_test.dart
```

Expected: FAIL — repository files not found.

- [ ] **Step 3: Define abstract GameRepository**

`lib/domain/repositories/game_repository.dart`:
```dart
import '../game_result.dart';
import '../user_profile.dart';

abstract interface class GameRepository {
  Future<void> createUser({required String uid, required String displayName});
  Future<void> saveGameResult({required String uid, required GameResult result});
  Future<List<UserProfile>> getLeaderboard();
}
```

- [ ] **Step 4: Implement FirebaseGameRepository**

`lib/data/repositories/firebase_game_repository.dart`:
```dart
import 'package:cloud_firestore/cloud_firestore.dart';
import '../../core/constants/game_config.dart';
import '../../domain/achievement.dart';
import '../../domain/game_result.dart';
import '../../domain/repositories/game_repository.dart';
import '../../domain/user_profile.dart';

class FirebaseGameRepository implements GameRepository {
  FirebaseGameRepository({FirebaseFirestore? firestore})
      : _db = firestore ?? FirebaseFirestore.instance;

  final FirebaseFirestore _db;

  CollectionReference<Map<String, dynamic>> get _users =>
      _db.collection('users');

  @override
  Future<void> createUser({
    required String uid,
    required String displayName,
  }) async {
    await _users.doc(uid).set({
      'displayName': displayName,
      'isAnonymous': true,
      'highScore': 0,
      'totalXP': 0,
      'level': 1,
      'unlockedPalettes': ['default'],
      'achievements': <dynamic>[],
      'createdAt': FieldValue.serverTimestamp(),
      'lastLogin': FieldValue.serverTimestamp(),
    });
  }

  @override
  Future<void> saveGameResult({
    required String uid,
    required GameResult result,
  }) async {
    final snap = await _users.doc(uid).get();
    final currentHigh = (snap.data()?['highScore'] as int?) ?? 0;
    final batch = _db.batch();
    final ref = _users.doc(uid);

    final updates = <String, dynamic>{
      'totalXP': FieldValue.increment(result.xpEarned),
      'lastLogin': FieldValue.serverTimestamp(),
    };

    if (result.finalScore > currentHigh) {
      updates['highScore'] = result.finalScore;
    }

    batch.update(ref, updates);

    for (final a in _evaluateAchievements(result, snap.data())) {
      batch.update(ref, {
        'achievements': FieldValue.arrayUnion([a.toMap()]),
      });
    }

    await batch.commit();
  }

  @override
  Future<List<UserProfile>> getLeaderboard() async {
    final snap = await _users
        .orderBy('highScore', descending: true)
        .limit(GameConfig.leaderboardLimit)
        .get();

    return snap.docs.map((doc) {
      final d = doc.data();
      return UserProfile(
        uid: doc.id,
        displayName: d['displayName'] as String,
        isAnonymous: d['isAnonymous'] as bool? ?? true,
        highScore: d['highScore'] as int? ?? 0,
        totalXP: d['totalXP'] as int? ?? 0,
        level: d['level'] as int? ?? 1,
        unlockedPalettes:
            List<String>.from(d['unlockedPalettes'] as List? ?? ['default']),
        achievements: (d['achievements'] as List? ?? [])
            .map((e) => Achievement.fromMap(e as Map<String, dynamic>))
            .toList(),
      );
    }).toList();
  }

  List<Achievement> _evaluateAchievements(
    GameResult result,
    Map<String, dynamic>? data,
  ) {
    final existing = (data?['achievements'] as List? ?? [])
        .map((e) => (e as Map<String, dynamic>)['id'] as String)
        .toSet();
    final now = DateTime.now();
    final earned = <Achievement>[];

    if (!existing.contains('first_blood') && result.finalScore > 0) {
      earned.add(Achievement(id: 'first_blood', unlockedAt: now));
    }
    if (!existing.contains('flawless') &&
        result.totalRounds >= 5 &&
        result.correctHits == result.totalRounds) {
      earned.add(Achievement(id: 'flawless', unlockedAt: now));
    }
    if (!existing.contains('centurion') && result.finalScore >= 1000) {
      earned.add(Achievement(id: 'centurion', unlockedAt: now));
    }

    return earned;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
flutter test test/data/firebase_game_repository_test.dart
```

Expected: PASS — 4 tests.

- [ ] **Step 6: Commit**

```bash
git add lib/domain/repositories/ lib/data/ test/data/
git commit -m "feat: Firestore repository with batched writes and leaderboard (TASK-105)"
```

---

### Task 9: Game Screens & Widget Integration

**Files:**
- Create: `lib/app.dart`
- Create: `lib/ui/screens/home_screen.dart`
- Create: `lib/ui/screens/game_screen.dart`
- Create: `lib/ui/screens/leaderboard_screen.dart`

- [ ] **Step 1: Implement app shell**

`lib/app.dart`:
```dart
import 'package:flutter/material.dart';
import 'ui/screens/home_screen.dart';

class StroopApp extends StatelessWidget {
  const StroopApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Stroop Overload',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0D0D0D),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF39FF14),
          surface: Color(0xFF141414),
        ),
        fontFamily: 'monospace',
      ),
      home: const HomeScreen(),
    );
  }
}
```

- [ ] **Step 2: Implement HomeScreen**

`lib/ui/screens/home_screen.dart`:
```dart
import 'package:flutter/material.dart';
import '../../data/repositories/firebase_game_repository.dart';
import '../../firebase/auth_service.dart';
import 'game_screen.dart';
import 'leaderboard_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _auth = AuthService();
  final _repo = FirebaseGameRepository();
  String? _uid;
  int _highScore = 0;

  @override
  void initState() {
    super.initState();
    _initUser();
  }

  Future<void> _initUser() async {
    final uid = await _auth.signInAnonymously();
    await _repo.createUser(
      uid: uid,
      displayName: 'Guest_${uid.substring(0, 4).toUpperCase()}',
    );
    if (mounted) setState(() => _uid = uid);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Spacer(),
            const Text(
              'STROOP',
              style: TextStyle(
                fontSize: 64,
                fontWeight: FontWeight.w900,
                color: Color(0xFF39FF14),
                letterSpacing: 8,
              ),
            ),
            const Text(
              'OVERLOAD',
              style: TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.w300,
                color: Color(0xFFF0F0F0),
                letterSpacing: 12,
              ),
            ),
            const SizedBox(height: 64),
            _PlayButton(
              enabled: _uid != null,
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => GameScreen(
                    uid: _uid!,
                    previousHighScore: _highScore,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const LeaderboardScreen()),
              ),
              child: const Text(
                'LEADERBOARD',
                style: TextStyle(
                    color: Color(0xFF666666), letterSpacing: 3, fontSize: 14),
              ),
            ),
            const Spacer(),
          ],
        ),
      ),
    );
  }
}

class _PlayButton extends StatelessWidget {
  const _PlayButton({required this.enabled, required this.onTap});
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: enabled ? onTap : null,
      child: Container(
        width: 240,
        height: 64,
        decoration: BoxDecoration(
          color: enabled ? const Color(0xFF39FF14) : const Color(0xFF222222),
          borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        child: Text(
          enabled ? 'PLAY' : 'LOADING...',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w900,
            color: enabled ? const Color(0xFF0A0A0A) : const Color(0xFF444444),
            letterSpacing: 4,
          ),
        ),
      ),
    );
  }
}
```

- [ ] **Step 3: Implement GameScreen**

`lib/ui/screens/game_screen.dart`:
```dart
import 'dart:typed_data';
import 'package:flame/game.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'dart:ui' as ui;
import '../../domain/game_result.dart';
import '../../game/stroop_game.dart';
import 'game_over_screen.dart';

class GameScreen extends StatefulWidget {
  const GameScreen({
    super.key,
    required this.uid,
    required this.previousHighScore,
  });

  final String uid;
  final int previousHighScore;

  @override
  State<GameScreen> createState() => _GameScreenState();
}

class _GameScreenState extends State<GameScreen> {
  late final StroopGame _game;
  final GlobalKey _repaintKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _game = StroopGame();
    _game.onGameOver = _handleGameOver;
  }

  Future<void> _handleGameOver(GameResult result) async {
    // Capture screenshot on the game-over frame before leaving this route
    Uint8List? screenshotBytes;
    try {
      final boundary = _repaintKey.currentContext?.findRenderObject()
          as RenderRepaintBoundary?;
      if (boundary != null) {
        final image = await boundary.toImage(pixelRatio: 2.0);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        screenshotBytes = data?.buffer.asUint8List();
      }
    } catch (_) {
      // Screenshot is best-effort — game over proceeds regardless
    }

    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => GameOverScreen(
          result: result,
          uid: widget.uid,
          screenshotBytes: screenshotBytes,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      key: _repaintKey,
      child: GameWidget(
        game: _game,
        loadingBuilder: (_) => const Scaffold(
          backgroundColor: Color(0xFF0D0D0D),
          body: Center(
            child: CircularProgressIndicator(color: Color(0xFF39FF14)),
          ),
        ),
      ),
    );
  }
}
```

- [ ] **Step 4: Implement LeaderboardScreen**

`lib/ui/screens/leaderboard_screen.dart`:
```dart
import 'package:flutter/material.dart';
import '../../data/repositories/firebase_game_repository.dart';
import '../../domain/user_profile.dart';

class LeaderboardScreen extends StatefulWidget {
  const LeaderboardScreen({super.key});

  @override
  State<LeaderboardScreen> createState() => _LeaderboardScreenState();
}

class _LeaderboardScreenState extends State<LeaderboardScreen> {
  late final Future<List<UserProfile>> _future;

  @override
  void initState() {
    super.initState();
    _future = FirebaseGameRepository().getLeaderboard();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D0D),
        title: const Text(
          'LEADERBOARD',
          style: TextStyle(
            letterSpacing: 4,
            fontWeight: FontWeight.w900,
            color: Color(0xFF39FF14),
          ),
        ),
      ),
      body: FutureBuilder<List<UserProfile>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(
                child: CircularProgressIndicator(color: Color(0xFF39FF14)));
          }
          final entries = snap.data ?? [];
          if (entries.isEmpty) {
            return const Center(
              child: Text('No scores yet.',
                  style: TextStyle(color: Color(0xFF555555))),
            );
          }
          return ListView.separated(
            itemCount: entries.length,
            separatorBuilder: (_, __) =>
                const Divider(color: Color(0xFF1A1A1A), height: 1),
            itemBuilder: (_, i) {
              final u = entries[i];
              return ListTile(
                leading: Text('${i + 1}',
                    style: const TextStyle(
                        color: Color(0xFF444444),
                        fontWeight: FontWeight.bold,
                        fontSize: 16)),
                title: Text(u.displayName,
                    style: const TextStyle(color: Color(0xFFF0F0F0))),
                trailing: Text(
                  u.highScore.toString(),
                  style: const TextStyle(
                      color: Color(0xFF39FF14),
                      fontWeight: FontWeight.w900,
                      fontSize: 18),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
```

- [ ] **Step 5: Build verify**

```bash
flutter build apk --debug
```

Expected: `Build succeeded.`

- [ ] **Step 6: Commit**

```bash
git add lib/app.dart lib/ui/ lib/main.dart
git commit -m "feat: home, game, and leaderboard screens wired to Flame + Firebase"
```

---

### Task 10: Game Over Screen & Viral Share

**Files:**
- Create: `lib/ui/screens/game_over_screen.dart`

- [ ] **Step 1: Implement GameOverScreen with share**

`lib/ui/screens/game_over_screen.dart`:
```dart
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../data/repositories/firebase_game_repository.dart';
import '../../domain/game_result.dart';
import 'home_screen.dart';

class GameOverScreen extends StatefulWidget {
  const GameOverScreen({
    super.key,
    required this.result,
    required this.uid,
    this.screenshotBytes,
  });

  final GameResult result;
  final String uid;
  final Uint8List? screenshotBytes;

  @override
  State<GameOverScreen> createState() => _GameOverScreenState();
}

class _GameOverScreenState extends State<GameOverScreen> {
  bool _saving = true;

  @override
  void initState() {
    super.initState();
    _persist();
  }

  Future<void> _persist() async {
    await FirebaseGameRepository()
        .saveGameResult(uid: widget.uid, result: widget.result);
    if (mounted) setState(() => _saving = false);
  }

  Future<void> _share() async {
    final bytes = widget.screenshotBytes;
    if (bytes == null) {
      // No screenshot — share text only
      await SharePlus.instance.share(
        ShareParams(
          text:
              'I scored ${widget.result.finalScore} on Stroop Overload! Can you beat me? 🧠⚡',
        ),
      );
      return;
    }

    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/stroop_score.png');
    await file.writeAsBytes(bytes);

    await SharePlus.instance.share(
      ShareParams(
        files: [XFile(file.path, mimeType: 'image/png')],
        text:
            'I scored ${widget.result.finalScore} on Stroop Overload! Can you beat me? 🧠⚡',
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D0D),
      body: SafeArea(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Spacer(),
            const Text(
              'GAME OVER',
              style: TextStyle(
                fontSize: 48,
                fontWeight: FontWeight.w900,
                color: Color(0xFFFF2D2D),
                letterSpacing: 6,
              ),
            ),
            const SizedBox(height: 32),
            _StatRow(label: 'SCORE', value: widget.result.finalScore.toString()),
            _StatRow(label: 'ACCURACY', value: '${widget.result.accuracy}%'),
            _StatRow(label: 'ROUNDS', value: widget.result.totalRounds.toString()),
            if (widget.result.isNewHighScore) ...[
              const SizedBox(height: 12),
              const Text(
                '★  NEW HIGH SCORE  ★',
                style: TextStyle(
                  color: Color(0xFFFFE600),
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 3,
                ),
              ),
            ],
            const Spacer(),
            GestureDetector(
              onTap: _saving ? null : _share,
              child: Container(
                width: 240,
                height: 56,
                decoration: BoxDecoration(
                  border: Border.all(color: const Color(0xFF39FF14), width: 2),
                  borderRadius: BorderRadius.circular(4),
                ),
                alignment: Alignment.center,
                child: Text(
                  _saving ? 'SAVING...' : 'SHARE SCORE',
                  style: const TextStyle(
                    color: Color(0xFF39FF14),
                    fontWeight: FontWeight.w900,
                    letterSpacing: 3,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            TextButton(
              onPressed: () => Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const HomeScreen()),
                (_) => false,
              ),
              child: const Text(
                'MAIN MENU',
                style: TextStyle(color: Color(0xFF555555), letterSpacing: 3),
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _StatRow extends StatelessWidget {
  const _StatRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 40),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: const TextStyle(
                  color: Color(0xFF666666), letterSpacing: 2, fontSize: 13)),
          Text(value,
              style: const TextStyle(
                  color: Color(0xFFF0F0F0),
                  fontSize: 24,
                  fontWeight: FontWeight.w900)),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Run full test suite**

```bash
flutter test
```

Expected: All tests PASS.

- [ ] **Step 3: Final build verification**

```bash
flutter build apk --debug
```

Expected: `Build succeeded.`

- [ ] **Step 4: Commit**

```bash
git add lib/ui/screens/game_over_screen.dart
git commit -m "feat: game over screen with viral share via RepaintBoundary + share_plus"
```

---

## Self-Review

### Spec Coverage

| PRD Requirement | Task |
|---|---|
| Stroop core loop — incongruence RNG | Task 3 — `IncongruenceEngine.generate()` |
| Flame game loop with delta time | Task 5 — `StroopGame.update(dt)` |
| Four quadrant hitboxes | Task 6 — `QuadrantComponent` |
| Timer decay per level | Task 5 — `timeLimitForLevel()` |
| Background distractor (advanced mode) | Tasks 3+6 — `bgDistractor` |
| Audio per turn (flame_audio) | Task 5 — `FlameAudio.play()` in `_nextStimulus` |
| FSM Menu → Playing → GameOver | Task 4 — `GameStateManager` |
| Anonymous Firebase Auth | Task 7 — `AuthService.signInAnonymously()` |
| Firestore batched writes | Task 8 — `batch.commit()` in `saveGameResult` |
| XP accumulation | Task 8 — `result.xpEarned` written to Firestore |
| Achievements (first_blood, flawless, centurion) | Task 8 — `_evaluateAchievements()` |
| Leaderboard top 50 | Task 8 — `getLeaderboard()` with `.limit(50)` |
| Screenshot at game-over frame | Task 9 — `RenderRepaintBoundary.toImage()` in `_handleGameOver` |
| Viral share intent | Task 10 — `SharePlus.instance.share(ShareParams(...))` |

### Known Gaps (Post-MVP)

1. **Audio assets**: `assets/audio/*.mp3` are placeholders. Real voice recordings required before production.
2. **`google-services.json`**: Manual step — cannot be automated. Task 7 Step 1 describes it.
3. **Palette unlocking**: `unlockedPalettes` is stored on UserProfile and written to Firestore, but not yet applied to game color themes. Deferred to v1.1.
4. **Firestore indexes**: The `orderBy('highScore', descending: true)` query requires a Firestore composite index if combined with other filters. Create in Firebase console before production.
5. **`createUser` is not idempotent for returning users**: Currently calls `set()` which overwrites. In v1.1, use `set(data, SetOptions(merge: true))`.

### Type Consistency

- `StroopColor` flows: `IncongruenceEngine` → `StroopStimulus` → `QuadrantComponent` (tap match) + `StimulusComponent` (render)
- `GameResult` flows: `StroopGame._endGame()` → `onGameOver` callback → `GameScreen._handleGameOver()` → `GameOverScreen` → `FirebaseGameRepository.saveGameResult()`
- `uid: String` flows: `AuthService.signInAnonymously()` → `HomeScreen._uid` → `GameScreen.uid` → `GameOverScreen.uid` → `FirebaseGameRepository`
- All types consistent across tasks. No placeholders in code.
