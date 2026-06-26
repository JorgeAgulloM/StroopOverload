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
