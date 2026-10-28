import 'package:flauncher/widgets/app_artwork_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('D-pad can select packaged artwork without a file browser', (
    tester,
  ) async {
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              child: const Text('Choose artwork'),
              onPressed: () async =>
                  selected = await AppArtworkPicker.show(context),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Choose artwork'));
    await tester.pumpAndSettle();
    expect(find.byType(AppArtworkPicker), findsOneWidget);
    expect(find.text('amazonprime'), findsNothing);
    expect(find.text('appletv'), findsNothing);
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    expect(selected, startsWith('appicons/'));
    expect(find.byType(AppArtworkPicker), findsNothing);
  });
}
