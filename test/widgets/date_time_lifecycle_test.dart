import 'package:flauncher/widgets/date_time_widget.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('hidden clock stops rebuilding and resumes immediately', (
    tester,
  ) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpWidget(
      const MaterialApp(home: DateTimeWidget('HH:mm:ss')),
    );
    final before = tester.widget<Text>(find.byType(Text).first);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump(const Duration(seconds: 2));
    expect(
      identical(tester.widget<Text>(find.byType(Text).first), before),
      isTrue,
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(
      identical(tester.widget<Text>(find.byType(Text).first), before),
      isFalse,
    );
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('changing to seconds reconfigures the existing minute timer', (
    tester,
  ) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpWidget(const MaterialApp(home: DateTimeWidget('HH:mm')));
    await tester.pumpWidget(
      const MaterialApp(home: DateTimeWidget('HH:mm:ss')),
    );
    final before = tester.widget<Text>(find.byType(Text).first);
    await tester.pump(const Duration(seconds: 1));
    expect(
      identical(tester.widget<Text>(find.byType(Text).first), before),
      isFalse,
    );
    await tester.pumpWidget(const SizedBox());
  });
}
