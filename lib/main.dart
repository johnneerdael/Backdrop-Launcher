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

import 'package:flauncher/database.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/launcher_state.dart';
import 'package:flauncher/providers/network_service.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'flauncher_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  initializeDateFormatting();
  // Font notices are read only when the user opens the licence page.
  LicenseRegistry.addLicense(() async* {
    for (final font in ['Inter', 'SpaceGrotesk']) {
      final notice = await rootBundle.loadString(
        'assets/branding/fonts/$font-OFL.txt',
      );
      yield LicenseEntryWithLineBreaks([font], notice);
    }
  });

  final sharedPreferences = await SharedPreferences.getInstance();
  final fLauncherChannel = FLauncherChannel();
  final fLauncherDatabase = FLauncherDatabase(connect());
  final settings = SettingsService(sharedPreferences);
  final trakt = TraktService(sharedPreferences);
  final apps = AppsService(fLauncherChannel, fLauncherDatabase);
  final wallpaper = WallpaperService(settings, trakt: trakt);
  final watchNext = WatchNextService(
    fLauncherChannel,
    settingsService: settings,
    autoRefresh: false,
  );

  // Restore independent local caches concurrently. No network request gates home.
  await Future.wait([
    settings.ready,
    apps.ready,
    wallpaper.ready,
    watchNext.ready,
  ]);

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => settings, lazy: false),
        ChangeNotifierProvider(create: (_) => trakt),
        ChangeNotifierProvider(create: (_) => apps),
        ChangeNotifierProvider(create: (_) => LauncherState()),
        ChangeNotifierProvider(create: (_) => NetworkService(fLauncherChannel)),
        ChangeNotifierProvider(create: (_) => wallpaper),
        ChangeNotifierProvider(create: (_) => watchNext, lazy: false),
      ],
      child: FLauncherApp(),
    ),
  );
  WidgetsBinding.instance.addPostFrameCallback((_) {
    unawaited(watchNext.startBackgroundRefresh());
  });
}
