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
