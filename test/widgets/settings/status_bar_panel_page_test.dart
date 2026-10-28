import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/settings/status_bar_panel_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';
import '../../mocks.mocks.dart';

void main() {
  testWidgets('Status bar retains auto-hide without a grid control', (
    tester,
  ) async {
    final settings = MockSettingsService();
    await tester.pumpWidget(
      ChangeNotifierProvider<SettingsService>.value(
        value: settings,
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: StatusBarPanelPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Hide status bar in grid'), findsNothing);
    expect(find.text('WiFi Usage'), findsNothing);
    await tester.tap(find.text('Automatically hide status bar'));
    await tester.pumpAndSettle();
    verify(settings.setAutoHideAppBarEnabled(true)).called(1);
  });
}
