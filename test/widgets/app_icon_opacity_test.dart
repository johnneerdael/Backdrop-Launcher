import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/app_card.dart';
import 'package:flauncher/widgets/settings/appearance_panel_page.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:transparent_image/transparent_image.dart';

import '../mocks.dart';
import '../mocks.mocks.dart';

void main() {
  testWidgets(
    'remote opacity steps update transparent tile tint and survive restart',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final settings = SettingsService(prefs);
      final apps = MockAppsService();
      when(apps.getAppBanner(any)).thenAnswer((_) async => kTransparentImage);
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<SettingsService>.value(value: settings),
            ChangeNotifierProvider<AppsService>.value(value: apps),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: Row(
                children: [
                  const SizedBox(width: 350, child: AppearancePanelPage()),
                  SizedBox(
                    width: 250,
                    height: 150,
                    child: AppCard(
                      application: fakeApp(),
                      category: fakeCategory(),
                      autofocus: false,
                      onMove: (_) {},
                      onMoveEnd: () {},
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final slider = find.byType(Slider);
      expect(slider, findsOneWidget);
      tester.widget<Slider>(slider).focusNode!.requestFocus();
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pumpAndSettle();
      expect(settings.appIconBackgroundOpacity, .2);
      expect(SettingsService(prefs).appIconBackgroundOpacity, .2);

      Material tile() => tester.widget<Material>(
        find
            .descendant(
              of: find.byType(AppCard),
              matching: find.byType(Material),
            )
            .first,
      );
      expect(tile().color, const Color(0x33FFFFFF));
      expect(tile().elevation, 0);
      await settings.setAccentColor('2979FF');
      await tester.pumpAndSettle();
      expect(tile().color, const Color(0x332979FF));
      await settings.setAppIconBackgroundOpacity(0);
      await tester.pumpAndSettle();
      expect(tile().color!.a, 0);
      await settings.setAppIconBackgroundOpacity(1);
      await tester.pumpAndSettle();
      expect(tile().color, const Color(0xFF2979FF));
    },
  );
}
