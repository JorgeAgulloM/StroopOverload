import 'package:stroop_overload/domain/achievement.dart';

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
