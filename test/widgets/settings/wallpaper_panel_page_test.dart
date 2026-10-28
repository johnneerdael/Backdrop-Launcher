import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/widgets/settings/wallpaper_panel_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../mocks.mocks.dart';

void main() {
  testWidgets('offers included backdrops and Trakt without a media browser', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final settings = SettingsService(prefs);
    final trakt = TraktService(prefs);
    addTearDown(trakt.dispose);
    final wallpaper = MockWallpaperService();
    when(
      wallpaper.wallpaper,
    ).thenReturn(const AssetImage('appicons/wuplay.png'));
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<SettingsService>.value(value: settings),
          ChangeNotifierProvider<TraktService>.value(value: trakt),
          ChangeNotifierProvider<WallpaperService>.value(value: wallpaper),
        ],
        child: const MaterialApp(home: Scaffold(body: WallpaperPanelPage())),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('10 included cinematic backdrops'), findsOneWidget);
    expect(find.text('Connect Trakt'), findsOneWidget);
    expect(find.text('Next backdrop'), findsOneWidget);
    expect(find.textContaining('Reddit'), findsNothing);
    expect(find.textContaining('Unsplash'), findsNothing);
    expect(find.byIcon(Icons.videocam_outlined), findsNothing);
  });
}
