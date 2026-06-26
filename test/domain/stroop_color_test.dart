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
