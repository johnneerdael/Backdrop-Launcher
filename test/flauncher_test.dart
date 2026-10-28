/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:async';

import 'package:flauncher/flauncher.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/launcher_state.dart';
import 'package:flauncher/providers/network_service.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flauncher/models/watch_next_item.dart';
import 'package:flauncher/widgets/watch_next_row.dart';
import 'package:flauncher/widgets/application_info_panel.dart';
import 'package:flauncher/widgets/settings/settings_panel.dart';
import 'package:flauncher/widgets/home_rows_pager.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';

import 'helpers.dart';
import 'mocks.dart';
import 'mocks.mocks.dart';
import 'watch_next_test_support.dart';

void main() {
  setUpAll(() {
    final binding = TestWidgetsFlutterBinding.ensureInitialized();
    binding.window.physicalSizeTestValue = const Size(1280, 720);
    binding.window.devicePixelRatioTestValue = 1;
  });

  AppsService rows([int count = 3]) {
    final service = mkAppService();
    when(service.categoriesWithApps).thenReturn(
      List.generate(
        count,
        (i) => CategoryWithApps(
          fakeCategory(
            id: i + 1,
            name: ['TV Apps', 'Android Apps', 'Custom row'][i],
            order: i,
          ),
          List.generate(
            4,
            (j) => fakeApp(packageName: 'row.$i.app.$j', name: 'App $i.$j'),
          ),
        ),
      ),
    );
    return service;
  }

  Future<void> key(WidgetTester tester, LogicalKeyboardKey key) async {
    await tester.sendKeyEvent(key);
    await tester.pumpAndSettle();
  }

  testWidgets('Startup remains empty until apps are ready', (tester) async {
    final service = rows();
    when(service.initialized).thenReturn(false);
    await _pumpWidgetWith(tester, service);
    expect(find.byType(Image), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('Home is row-only with a faint next-row preview', (tester) async {
    await _pumpWidgetWith(tester, rows());
    expect(find.byType(HomeRowsPager), findsOneWidget);
    expect(find.byType(SliverGrid), findsNothing);
    final viewport = tester.getRect(
      find.byKey(const Key('home_sections_viewport')),
    );
    expect(viewport.top, greaterThan(360));
    expect(
      tester
          .widget<Opacity>(find.byKey(const ValueKey('home_row_opacity_1')))
          .opacity,
      1,
    );
    expect(
      tester
          .widget<Opacity>(find.byKey(const ValueKey('home_row_opacity_2')))
          .opacity,
      closeTo(.18, .001),
    );
    expect(
      tester.getRect(findAppCard('row.1.app.0')).overlaps(viewport),
      isTrue,
    );
    expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
  });

  testWidgets(
    'Hidden rows stay off home and the next visible row receives focus',
    (tester) async {
      final service = rows();
      service.categoriesWithApps.first.hidden = true;
      await _pumpWidgetWith(tester, service);
      final pager = tester.widget<HomeRowsPager>(find.byType(HomeRowsPager));
      expect(pager.categories.map((row) => row.name), [
        'Android Apps',
        'Custom row',
      ]);
      expect(findAppCard('row.0.app.0'), findsNothing);
      expect(isAppCardFocused(tester, 'row.1.app.0'), isTrue);
    },
  );

  testWidgets('A row glides fully out of view when moving to the next row', (
    tester,
  ) async {
    await _pumpWidgetWith(tester, rows());
    final viewport = tester.getRect(
      find.byKey(const Key('home_sections_viewport')),
    );
    final outgoing = findAppCard('row.0.app.0');
    final start = tester.getRect(outgoing);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.getRect(outgoing).top, lessThan(start.top));
    await tester.pumpAndSettle();
    expect(isAppCardFocused(tester, 'row.1.app.0'), isTrue);
    expect(
      outgoing.evaluate().isEmpty ||
          !tester.getRect(outgoing).overlaps(viewport),
      isTrue,
    );
    expect(
      tester.getRect(find.byKey(const Key('home_sections_viewport'))),
      viewport,
    );
    await key(tester, LogicalKeyboardKey.arrowUp);
    expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
  });

  testWidgets('Each row remembers its horizontal focus', (tester) async {
    await _pumpWidgetWith(tester, rows());
    await key(tester, LogicalKeyboardKey.arrowRight);
    await key(tester, LogicalKeyboardKey.arrowRight);
    expect(isAppCardFocused(tester, 'row.0.app.2'), isTrue);
    await key(tester, LogicalKeyboardKey.arrowDown);
    await key(tester, LogicalKeyboardKey.arrowRight);
    expect(isAppCardFocused(tester, 'row.1.app.1'), isTrue);
    await key(tester, LogicalKeyboardKey.arrowUp);
    expect(isAppCardFocused(tester, 'row.0.app.2'), isTrue);
    await key(tester, LogicalKeyboardKey.arrowDown);
    expect(isAppCardFocused(tester, 'row.1.app.1'), isTrue);
  });

  testWidgets('Rapid Down and Up reverses the glide without losing focus', (
    tester,
  ) async {
    await _pumpWidgetWith(tester, rows());
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pump(const Duration(milliseconds: 70));
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
    await tester.pumpAndSettle();
    expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
    expect(
      tester
          .widget<Opacity>(find.byKey(const ValueKey('home_row_opacity_1')))
          .opacity,
      1,
    );
  });

  testWidgets('The last row aligns fully and can return to Settings', (
    tester,
  ) async {
    await _pumpWidgetWith(tester, rows());
    await key(tester, LogicalKeyboardKey.arrowDown);
    await key(tester, LogicalKeyboardKey.arrowDown);
    expect(isAppCardFocused(tester, 'row.2.app.0'), isTrue);
    final viewport = tester.getRect(
      find.byKey(const Key('home_sections_viewport')),
    );
    final previous = findAppCard('row.1.app.0');
    expect(
      previous.evaluate().isEmpty ||
          !tester.getRect(previous).overlaps(viewport),
      isTrue,
    );
    await key(tester, LogicalKeyboardKey.arrowUp);
    await key(tester, LogicalKeyboardKey.arrowUp);
    await key(tester, LogicalKeyboardKey.arrowUp);
    expect(isSettingsIconFocused(tester), isTrue);
    await key(tester, LogicalKeyboardKey.arrowDown);
    expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
  });

  testWidgets(
    'Empty rows stay off home and an empty home has a Settings action',
    (tester) async {
      final service = mkAppService();
      when(
        service.categoriesWithApps,
      ).thenReturn([fakeCategory(name: 'Empty row')]);
      await _pumpWidgetWith(tester, service);
      expect(find.byType(HomeRowsPager), findsNothing);
      expect(find.text('Add apps to a row in Settings'), findsOneWidget);
      await key(tester, LogicalKeyboardKey.enter);
      expect(isSettingsIconFocused(tester), isTrue);
    },
  );

  testWidgets('App activation and remote hold remain distinct', (tester) async {
    final service = rows();
    await _pumpWidgetWith(tester, service);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.select);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.byType(ApplicationInfoPanel), findsOneWidget);
    await tester.sendKeyRepeatEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    await tester.sendKeyUpEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    expect(find.byType(ApplicationInfoPanel), findsOneWidget);
    verifyNever((service as MockAppsService).launchApp(any));
  });

  for (final keyCode in [LogicalKeyboardKey.select, LogicalKeyboardKey.enter]) {
    testWidgets('Short $keyCode launches the app without opening its menu', (
      tester,
    ) async {
      final service = rows();
      await _pumpWidgetWith(tester, service);
      await tester.sendKeyDownEvent(keyCode);
      await tester.pump(const Duration(milliseconds: 50));
      await tester.sendKeyUpEvent(keyCode);
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.byType(ApplicationInfoPanel), findsNothing);
      verify(
        (service as MockAppsService).launchApp(
          argThat(predicate<App>((app) => app.packageName == 'row.0.app.0')),
        ),
      ).called(1);
      await tester.pump(const Duration(milliseconds: 600));
    });
  }

  testWidgets('Reordering an app does not shift its row vertically', (
    tester,
  ) async {
    await _pumpWidgetWith(tester, rows());
    await key(tester, LogicalKeyboardKey.arrowDown);
    final vertical = tester
        .widgetList<ListView>(
          find.descendant(
            of: find.byType(HomeRowsPager),
            matching: find.byType(ListView),
          ),
        )
        .singleWhere((list) => list.scrollDirection == Axis.vertical)
        .controller!;
    final before = vertical.offset;
    await tester.sendKeyDownEvent(LogicalKeyboardKey.select);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.sendKeyUpEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reorder'));
    await tester.pumpAndSettle();
    await key(tester, LogicalKeyboardKey.arrowRight);
    expect(vertical.offset, closeTo(before, .01));
  });

  testWidgets('Apps remain identifiable and navigable while artwork loads', (
    tester,
  ) async {
    final service = rows();
    final artwork = Completer<Uint8List>();
    when(
      (service as MockAppsService).getAppBanner(any),
    ).thenAnswer((_) => artwork.future);
    await _pumpWidgetWith(tester, service);
    expect(find.text('App 0.0'), findsWidgets);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await key(tester, LogicalKeyboardKey.arrowRight);
    expect(isAppCardFocused(tester, 'row.0.app.1'), isTrue);
    await tester.pumpWidget(const SizedBox());
    artwork.complete(Uint8List(0));
  });

  testWidgets('Updating rows behind Settings does not steal menu focus', (
    tester,
  ) async {
    final service = rows();
    await _pumpWidgetWith(tester, service);
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    expect(
      FocusManager.instance.primaryFocus!.context!
          .findAncestorWidgetOfExactType<SettingsPanel>(),
      isNotNull,
    );
    when(service.categoriesWithApps).thenReturn([
      CategoryWithApps(fakeCategory(id: 2, name: 'Android Apps'), [
        fakeApp(packageName: 'row.1.app.0'),
      ]),
    ]);
    when(service.layoutVersion).thenReturn(1);
    await _pumpWidgetWith(tester, service);
    await tester.pumpAndSettle();
    expect(
      FocusManager.instance.primaryFocus!.context!
          .findAncestorWidgetOfExactType<SettingsPanel>(),
      isNotNull,
    );
    Navigator.of(tester.element(find.byType(SettingsPanel))).pop();
    await tester.pumpAndSettle();
    expect(isAppCardFocused(tester, 'row.1.app.0'), isTrue);
  });

  for (final hero in [false, true]) {
    testWidgets(
      'Continue Watching stays above app rows and hands focus between zones (hero=$hero)',
      (tester) async {
        final settings = mkSettingsService();
        when(settings.showWatchNextSection).thenReturn(true);
        when(settings.watchNextBigPictureMode).thenReturn(hero);
        final item = WatchNextItem(id: 1, title: 'Resume item');
        final watch = MockWatchNextService();
        when(watch.hasPermission).thenReturn(true);
        when(watch.hasItems).thenReturn(true);
        when(watch.hasVisibleSection).thenReturn(true);
        when(watch.items).thenReturn([item]);
        when(watch.activeHeroItem).thenReturn(item);
        await _pumpWidgetWithProviders(
          tester,
          mkWallpaperService(),
          rows(),
          settings,
          watch,
        );
        final pinned = tester.getRect(find.byType(WatchNextRow));
        final viewport = tester.getRect(
          find.byKey(const Key('home_sections_viewport')),
        );
        expect(pinned.bottom, lessThanOrEqualTo(viewport.top - 24));
        expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
        await key(tester, LogicalKeyboardKey.arrowUp);
        expect(
          FocusManager.instance.primaryFocus!.context!
              .findAncestorWidgetOfExactType<WatchNextRow>(),
          isNotNull,
        );
        await key(tester, LogicalKeyboardKey.arrowDown);
        expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
        await key(tester, LogicalKeyboardKey.arrowDown);
        expect(isAppCardFocused(tester, 'row.1.app.0'), isTrue);
        expect(tester.getRect(find.byType(WatchNextRow)), pinned);
        await key(tester, LogicalKeyboardKey.arrowUp);
        await key(tester, LogicalKeyboardKey.arrowUp);
        expect(
          FocusManager.instance.primaryFocus!.context!
              .findAncestorWidgetOfExactType<WatchNextRow>(),
          isNotNull,
        );
        await key(tester, LogicalKeyboardKey.arrowUp);
        expect(isSettingsIconFocused(tester), isTrue);
        await key(tester, LogicalKeyboardKey.arrowDown);
        expect(
          FocusManager.instance.primaryFocus!.context!
              .findAncestorWidgetOfExactType<WatchNextRow>(),
          isNotNull,
        );
      },
    );
  }

  testWidgets(
    'Pinned feed removal returns focus to apps and no-app home supports both zones',
    (tester) async {
      final watch = WatchNextTestFeed();
      addTearDown(watch.dispose);
      final settings = mkSettingsService();
      when(settings.showWatchNextSection).thenReturn(true);
      await _pumpWidgetWithProviders(
        tester,
        mkWallpaperService(),
        rows(),
        settings,
        watch,
      );
      await key(tester, LogicalKeyboardKey.arrowUp);
      expect(
        FocusManager.instance.primaryFocus!.context!
            .findAncestorWidgetOfExactType<WatchNextRow>(),
        isNotNull,
      );
      watch.replace([]);
      await tester.pumpAndSettle();
      expect(find.byType(WatchNextRow), findsNothing);
      expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
      await tester.pumpWidget(const SizedBox());
      watch.replace([WatchNextItem(id: 99, title: 'Only item')]);
      await _pumpWidgetWithProviders(
        tester,
        mkWallpaperService(),
        rows(0),
        settings,
        watch,
      );
      expect(
        FocusManager.instance.primaryFocus!.context!
            .findAncestorWidgetOfExactType<WatchNextRow>(),
        isNotNull,
      );
      await key(tester, LogicalKeyboardKey.arrowDown);
      expect(
        FocusManager.instance.primaryFocus!.context!
            .findAncestorWidgetOfExactType<WatchNextRow>(),
        isNull,
      );
      await key(tester, LogicalKeyboardKey.arrowUp);
      expect(
        FocusManager.instance.primaryFocus!.context!
            .findAncestorWidgetOfExactType<WatchNextRow>(),
        isNotNull,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Pinned layout fits TV dimensions and Settings restores toolbar focus',
    (tester) async {
      tester.view.physicalSize = const Size(960, 540);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final watch = WatchNextTestFeed();
      addTearDown(watch.dispose);
      watch.values[0] = WatchNextItem(
        id: 1,
        title:
            'A very long programme title that stays on one line without changing the pinned area height',
      );
      final settings = mkSettingsService();
      when(settings.showWatchNextSection).thenReturn(true);
      await _pumpWidgetWithProviders(
        tester,
        mkWallpaperService(),
        rows(),
        settings,
        watch,
      );
      await key(tester, LogicalKeyboardKey.arrowUp);
      final pinned = tester.getRect(find.byType(WatchNextRow));
      final apps = tester.getRect(
        find.byKey(const Key('home_sections_viewport')),
      );
      expect(pinned.bottom, lessThanOrEqualTo(apps.top - 24));
      await key(tester, LogicalKeyboardKey.arrowUp);
      await key(tester, LogicalKeyboardKey.enter);
      expect(find.byType(SettingsPanel), findsOneWidget);
      Navigator.of(tester.element(find.byType(SettingsPanel))).pop();
      await tester.pumpAndSettle();
      expect(isSettingsIconFocused(tester), isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Permission navigation reaches Check then exits to apps', (
    tester,
  ) async {
    final watch = WatchNextTestFeed()..allowed = false;
    addTearDown(watch.dispose);
    final settings = mkSettingsService();
    when(settings.showWatchNextSection).thenReturn(true);
    await _pumpWidgetWithProviders(
      tester,
      mkWallpaperService(),
      rows(),
      settings,
      watch,
    );
    await key(tester, LogicalKeyboardKey.arrowUp);
    await key(tester, LogicalKeyboardKey.arrowDown);
    expect(
      FocusManager.instance.primaryFocus!.context!
          .findAncestorWidgetOfExactType<WatchNextRow>(),
      isNotNull,
    );
    await key(tester, LogicalKeyboardKey.enter);
    expect(watch.permissionRechecks, 1);
    expect(watch.permissionRequests, 0);
    await key(tester, LogicalKeyboardKey.arrowDown);
    expect(isAppCardFocused(tester, 'row.0.app.0'), isTrue);
    expect(tester.takeException(), isNull);
  });
}

SettingsService mkSettingsService() {
  final settingsService = MockSettingsService();
  when(
    settingsService.dateFormat,
  ).thenReturn(SettingsService.defaultDateFormat);
  when(
    settingsService.timeFormat,
  ).thenReturn(SettingsService.defaultTimeFormat);
  when(settingsService.appHighlightAnimationEnabled).thenReturn(false);
  when(settingsService.showAppNamesBelowIcons).thenReturn(true);
  when(settingsService.showWatchNextSection).thenReturn(false);
  when(settingsService.watchNextBigPictureMode).thenReturn(false);
  when(settingsService.watchNextHideProgramsWithoutImages).thenReturn(false);
  when(settingsService.watchNextMaxItems).thenReturn(0);
  when(settingsService.watchNextExcludedPackages).thenReturn([]);
  when(settingsService.showCategoryTitles).thenReturn(true);
  when(settingsService.autoHideAppBarEnabled).thenReturn(false);
  when(settingsService.showNetworkIndicatorInStatusBar).thenReturn(false);
  when(settingsService.showDateInStatusBar).thenReturn(false);
  when(settingsService.showTimeInStatusBar).thenReturn(false);
  when(settingsService.focusBorderThickness).thenReturn(3.0);
  when(settingsService.screensaverClockStyle).thenReturn('minimal');
  when(settingsService.accentColorHex).thenReturn(ACCENT_COLOR_WHITE);
  return settingsService;
}

WatchNextService mkWatchNextService() {
  final watchNextService = MockWatchNextService();
  when(watchNextService.hasPermission).thenReturn(false);
  when(watchNextService.hasItems).thenReturn(false);
  when(watchNextService.hasVisibleSection).thenReturn(false);
  when(watchNextService.items).thenReturn([]);
  when(watchNextService.activeHeroItem).thenReturn(null);
  when(watchNextService.refreshPermissionAndItems()).thenAnswer((_) async {});
  return watchNextService;
}

WallpaperService mkWallpaperService([bool wallpaper = true]) {
  final wallpaperService = MockWallpaperService();
  when(
    wallpaperService.wallpaper,
  ).thenReturn(Image.asset('appicons/wuplay.png').image);
  when(wallpaperService.wallpaperRevision).thenReturn(0);
  return wallpaperService;
}

AppsService mkAppService() {
  final appsService = MockAppsService();
  when(appsService.initialized).thenReturn(true);
  when(appsService.layoutVersion).thenReturn(0);
  when(appsService.getAppIcon(any)).thenAnswer(
    (_) => Future.value(
      Uint8List.fromList([
        0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x06,
        0x00,
        0x00,
        0x00,
        0x1F,
        0x15,
        0xC4,
        0x89,
        0x00,
        0x00,
        0x00,
        0x0A,
        0x49,
        0x44,
        0x41,
        0x54,
        0x78,
        0x9C,
        0x63,
        0x00,
        0x01,
        0x00,
        0x00,
        0x05,
        0x00,
        0x01,
        0x0D,
        0x0A,
        0x2D,
        0xB4,
        0x00,
        0x00,
        0x00,
        0x00,
        0x49,
        0x45,
        0x4E,
        0x44,
        0xAE,
        0x42,
        0x60,
        0x82,
      ]),
    ),
  );
  when(
    appsService.getAppBanner(any),
  ).thenAnswer((_) => Future.value(Uint8List(0)));
  when(appsService.hasCustomBanner(any)).thenAnswer((_) => Future.value(false));
  return appsService;
}

Future<void> _pumpWidgetWith(
  WidgetTester tester,
  AppsService appsService,
) async {
  return _pumpWidgetWithProviders(
    tester,
    mkWallpaperService(),
    appsService,
    mkSettingsService(),
    mkWatchNextService(),
  );
}

Future<void> _pumpWidgetWithProviders(
  WidgetTester tester,
  WallpaperService wallpaperService,
  AppsService appsService,
  SettingsService settingsService, [
  WatchNextService? watchNextService,
]) async {
  try {
    final cwa = appsService.categoriesWithApps;
    when(appsService.categories).thenReturn(cwa);
    when(appsService.launcherSections).thenReturn(cwa);
  } catch (_) {}

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<WallpaperService>.value(value: wallpaperService),
        ChangeNotifierProvider<AppsService>.value(value: appsService),
        ChangeNotifierProvider<SettingsService>.value(value: settingsService),
        ChangeNotifierProvider<WatchNextService>.value(
          value: watchNextService ?? mkWatchNextService(),
        ),
        ChangeNotifierProvider(create: (_) => LauncherState()),
        ChangeNotifierProvider(
          create: (_) => NetworkService(FLauncherChannel()),
        ),
      ],
      builder: (_, __) => MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: FLauncher(),
      ),
    ),
  );
  await tester.pump(Duration(seconds: 30), EnginePhase.sendSemanticsUpdate);
}
