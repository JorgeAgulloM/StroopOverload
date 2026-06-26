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
