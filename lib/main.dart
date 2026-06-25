import 'package:flutter/material.dart';

// Task 7 will replace this with Firebase.initializeApp() + StroopApp
void main() {
  runApp(const _PlaceholderApp());
}

class _PlaceholderApp extends StatelessWidget {
  const _PlaceholderApp();

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: Scaffold(
        backgroundColor: Color(0xFF0D0D0D),
        body: Center(
          child: Text(
            'STROOP OVERLOAD',
            style: TextStyle(
              color: Color(0xFF39FF14),
              fontSize: 24,
              fontWeight: FontWeight.w900,
              letterSpacing: 6,
            ),
          ),
        ),
      ),
    );
  }
}
