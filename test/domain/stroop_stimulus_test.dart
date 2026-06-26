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
