import 'package:flauncher/widgets/focus_keyboard_listener.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'holding remote select opens context actions without a repeated key event',
    (tester) async {
      var presses = 0;
      var longPresses = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: FocusKeyboardListener(
            onPressed: (_) {
              presses++;
              return KeyEventResult.handled;
            },
            onLongPress: (_) {
              longPresses++;
              return KeyEventResult.handled;
            },
            child: const Focus(autofocus: true, child: SizedBox()),
          ),
        ),
      );
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.select);
      await tester.pump(const Duration(milliseconds: 600));
      expect(longPresses, 1);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.select);
      expect(presses, 0);
    },
  );

  testWidgets('short select press activates once', (tester) async {
    var presses = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: FocusKeyboardListener(
          onPressed: (_) {
            presses++;
            return KeyEventResult.handled;
          },
          onLongPress: (_) => KeyEventResult.handled,
          child: const Focus(autofocus: true, child: SizedBox()),
        ),
      ),
    );
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    expect(presses, 1);
  });
}
