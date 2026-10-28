import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/settings/appearance_panel_page.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flauncher/widgets/settings/settings_toggle.dart';
import 'package:flauncher/widgets/side_panel_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('native pane uses measured geometry and isolates its theme', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1920, 1080);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    late ThemeData homeTheme;
    late ThemeData paneTheme;
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(
          fontFamily: 'Inter',
          brightness: Brightness.dark,
          applyElevationOverlayColor: true,
          colorScheme: ColorScheme.fromSeed(
            seedColor: Colors.purple,
            brightness: Brightness.dark,
          ),
        ),
        home: Scaffold(
          body: Stack(
            children: [
              Builder(
                builder: (context) {
                  homeTheme = Theme.of(context);
                  return const Text('Home');
                },
              ),
              SidePanelDialog(
                child: Builder(
                  builder: (context) {
                    paneTheme = Theme.of(context);
                    return Column(
                      key: const Key('pane'),
                      children: [
                        const SettingsPageHeader(title: 'Settings'),
                        FocusableSettingsTile(
                          autofocus: true,
                          leading: const Icon(Icons.apps),
                          title: const Text('Apps'),
                          onPressed: () {},
                        ),
                        FocusableSettingsTile(
                          title: const Text('Network'),
                          subtitle: const Text('Connected'),
                          onPressed: () {},
                          trailing: const SettingsSwitch(
                            value: false,
                            accentColor: Colors.blue,
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final pane = tester.getRect(find.byKey(const Key('pane')));
    expect(pane, const Rect.fromLTWH(600, 0, 360, 540));
    expect(tester.getSize(find.byType(SettingsPageHeader)).height, 91);
    expect(tester.getTopLeft(find.text('Apps')).dx, closeTo(664, 0.1));
    expect(
      tester.getSize(find.byType(FocusableSettingsTile).first).height,
      closeTo(48, 0.1),
    );
    expect(
      tester.getSize(find.byType(FocusableSettingsTile).last).height,
      closeTo(64.25, 0.5),
    );
    expect(
      tester.getRect(find.byType(SettingsSwitch)).right,
      closeTo(904, 0.1),
    );
    expect(paneTheme.colorScheme.surface, const Color(0xFF202124));
    expect(paneTheme.applyElevationOverlayColor, isFalse);
    expect(paneTheme.textTheme.bodyMedium!.fontFamily, 'sans-serif');
    expect(paneTheme.textTheme.bodyMedium!.fontSize, 14);
    expect(paneTheme.textTheme.bodySmall!.fontFamily, 'sans-serif-condensed');
    expect(paneTheme.textTheme.bodySmall!.fontSize, 12);
    expect(paneTheme.textTheme.titleLarge!.fontSize, 20);
    expect(homeTheme.textTheme.bodyMedium!.fontFamily, 'Inter');
    final focusedTitle = tester.getRect(find.text('Apps'));
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    expect(tester.getRect(find.text('Apps')), focusedTitle);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'opacity slider keeps launcher accent inside native settings colours',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final settings = SettingsService(await SharedPreferences.getInstance());
      await settings.setAccentColor('7C4DFF');
      await tester.pumpWidget(
        ChangeNotifierProvider<SettingsService>.value(
          value: settings,
          child: MaterialApp(
            theme: ThemeData(
              colorScheme: const ColorScheme.dark(primary: Color(0xFF51AAFF)),
            ),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const Scaffold(
              body: SizedBox(width: 360, child: AppearancePanelPage()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final context = tester.element(find.byType(Slider));
      final slider = SliderTheme.of(context);
      expect(
        slider.activeTrackColor ?? Theme.of(context).colorScheme.primary,
        const Color(0xFF7C4DFF),
      );
      expect(
        slider.thumbColor ?? Theme.of(context).colorScheme.primary,
        const Color(0xFF7C4DFF),
      );
    },
  );
}
