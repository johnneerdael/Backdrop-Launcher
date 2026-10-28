import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/general_settings_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'System settings expose launcher controls and focus the first remaining row',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: GeneralSettingsPage()),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Brightness Scheduler'), findsNothing);
      expect(find.text('WiFi Usage Period'), findsNothing);
      expect(find.text('Screensaver Settings'), findsOneWidget);
      expect(find.text('Screensaver Clock Style'), findsOneWidget);
      expect(find.text('Date and time format'), findsOneWidget);
      expect(find.text('Back button action'), findsOneWidget);
      final tile = FocusManager.instance.primaryFocus?.context
          ?.findAncestorWidgetOfExactType<FocusableSettingsTile>();
      expect((tile?.title as Text?)?.data, 'Screensaver Settings');
    },
  );
}
