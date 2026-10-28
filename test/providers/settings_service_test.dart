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

//import 'dart:html';

import 'package:flauncher/providers/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

//import '../mocks.mocks.dart';

void main() async {
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final sharedPreferences = await SharedPreferences.getInstance();
  final settingsService = SettingsService(sharedPreferences);

  setUp(() async {
    await sharedPreferences.clear();
  });

  test(
    'icon background opacity is clamped and persisted in ten percent steps',
    () async {
      expect(settingsService.appIconBackgroundOpacity, .1);
      await settingsService.setAppIconBackgroundOpacity(.26);
      expect(SettingsService(sharedPreferences).appIconBackgroundOpacity, .3);
      await settingsService.setAppIconBackgroundOpacity(-1);
      expect(settingsService.appIconBackgroundOpacity, 0);
      await settingsService.setAppIconBackgroundOpacity(2);
      expect(settingsService.appIconBackgroundOpacity, 1);
    },
  );

  test("watchNextBigPictureMode default and update", () async {
    final sharedPreferences = await SharedPreferences.getInstance();
    final settingsService = SettingsService(sharedPreferences);

    expect(settingsService.watchNextBigPictureMode, isFalse);

    await settingsService.setWatchNextBigPictureMode(true);
    expect(settingsService.watchNextBigPictureMode, isTrue);

    await settingsService.setWatchNextBigPictureMode(false);
    expect(settingsService.watchNextBigPictureMode, isFalse);
  });

  test("watchNextMaxItems default and update", () async {
    final sharedPreferences = await SharedPreferences.getInstance();
    final settingsService = SettingsService(sharedPreferences);

    expect(settingsService.watchNextMaxItems, equals(0));

    await settingsService.setWatchNextMaxItems(10);
    expect(settingsService.watchNextMaxItems, equals(10));

    await settingsService.setWatchNextMaxItems(0);
    expect(settingsService.watchNextMaxItems, equals(0));
  });

  test("watchNextExcludedPackages default and toggle", () async {
    final sharedPreferences = await SharedPreferences.getInstance();
    final settingsService = SettingsService(sharedPreferences);

    expect(settingsService.watchNextExcludedPackages, isEmpty);
    expect(
      settingsService.isWatchNextPackageExcluded("com.plexapp.android"),
      isFalse,
    );

    await settingsService.toggleWatchNextExcludedPackage("com.plexapp.android");
    expect(settingsService.watchNextExcludedPackages, ["com.plexapp.android"]);
    expect(
      settingsService.isWatchNextPackageExcluded("com.plexapp.android"),
      isTrue,
    );

    await settingsService.toggleWatchNextExcludedPackage("com.netflix.ninja");
    expect(settingsService.watchNextExcludedPackages, [
      "com.plexapp.android",
      "com.netflix.ninja",
    ]);

    await settingsService.toggleWatchNextExcludedPackage("com.plexapp.android");
    expect(settingsService.watchNextExcludedPackages, ["com.netflix.ninja"]);
    expect(
      settingsService.isWatchNextPackageExcluded("com.plexapp.android"),
      isFalse,
    );
  });

  test("setUse24HourTimeFormat", () async {
    final sharedPreferences = await SharedPreferences.getInstance();
    final settingsService = SettingsService(sharedPreferences);
    final expected = "XYZ";

    await settingsService.setDateTimeFormat("", expected);

    expect(settingsService.timeFormat, expected);
  });

  group("getDateFormat", () {
    test("with default", () async {
      expect(settingsService.dateFormat, SettingsService.defaultDateFormat);
    });

    test("with value set", () async {
      final expected = "XYZ";

      settingsService.setDateTimeFormat(expected, "");

      expect(settingsService.dateFormat, expected);
    });
  });

  group("watchNextHideProgramsWithoutImages", () {
    test("defaults to false", () {
      expect(settingsService.watchNextHideProgramsWithoutImages, isFalse);
    });

    test("persists value", () async {
      await settingsService.setWatchNextHideProgramsWithoutImages(true);
      expect(settingsService.watchNextHideProgramsWithoutImages, isTrue);
      expect(
        sharedPreferences.getBool("watch_next_hide_programs_without_images"),
        isTrue,
      );

      await settingsService.setWatchNextHideProgramsWithoutImages(false);
      expect(settingsService.watchNextHideProgramsWithoutImages, isFalse);
      expect(
        sharedPreferences.getBool("watch_next_hide_programs_without_images"),
        isFalse,
      );
    });
  });
}
