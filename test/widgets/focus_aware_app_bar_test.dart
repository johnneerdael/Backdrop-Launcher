import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/focus_aware_app_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';
import '../mocks.mocks.dart';

void main() {
  for (final autoHide in [false, true]) {
    testWidgets('Status bar auto-hide=$autoHide and Settings focus reveal', (
      tester,
    ) async {
      final settings = MockSettingsService();
      when(settings.autoHideAppBarEnabled).thenReturn(autoHide);
      when(settings.accentColorHex).thenReturn('FFFFFF');
      final key = GlobalKey<FocusAwareAppBarState>();
      await tester.pumpWidget(
        ChangeNotifierProvider<SettingsService>.value(
          value: settings,
          child: MaterialApp(
            home: Scaffold(appBar: FocusAwareAppBar(key: key)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final opacity = find.descendant(
        of: find.byType(FocusAwareAppBar),
        matching: find.byType(AnimatedOpacity),
      );
      expect(tester.widget<AnimatedOpacity>(opacity).opacity, autoHide ? 0 : 1);
      key.currentState!.focusSettings();
      await tester.pumpAndSettle();
      expect(tester.widget<AnimatedOpacity>(opacity).opacity, 1);
    });
  }
}
