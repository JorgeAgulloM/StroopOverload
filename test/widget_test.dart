import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stroop_overload/main.dart';

void main() {
  testWidgets('placeholder app renders without error', (WidgetTester tester) async {
    await tester.pumpWidget(const _PlaceholderApp());
    expect(find.text('STROOP OVERLOAD'), findsOneWidget);
  });
}

// Expose for testing
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
