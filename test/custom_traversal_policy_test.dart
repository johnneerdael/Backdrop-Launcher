import 'package:flauncher/custom_traversal_policy.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Up from the first scrolled section can return to the toolbar', (
    tester,
  ) async {
    final toolbar = FocusNode();
    final first = FocusNode();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FocusTraversalGroup(
            policy: RowByRowTraversalPolicy(),
            child: Column(
              children: [
                TextButton(
                  focusNode: toolbar,
                  onPressed: () {},
                  child: const Text('Settings'),
                ),
                Expanded(
                  child: ListView(
                    children: [
                      TextButton(
                        focusNode: first,
                        onPressed: () {},
                        child: const Text('Grant permission'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    first.requestFocus();
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
    await tester.pump();
    expect(toolbar.hasFocus, isTrue);
    await tester.pumpWidget(const SizedBox());
    toolbar.dispose();
    first.dispose();
  });
}
