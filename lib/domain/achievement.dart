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
