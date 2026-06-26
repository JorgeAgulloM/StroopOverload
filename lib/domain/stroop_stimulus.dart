import 'package:stroop_overload/core/stroop_color.dart';

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
