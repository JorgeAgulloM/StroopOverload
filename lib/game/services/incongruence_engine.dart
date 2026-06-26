import 'dart:math';
import 'package:stroop_overload/core/stroop_color.dart';
import 'package:stroop_overload/domain/stroop_stimulus.dart';

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
    if (pool.isEmpty) return StroopColor.values[_rng.nextInt(4)];
    return pool[_rng.nextInt(pool.length)];
  }
}
