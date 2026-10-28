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

import 'package:flauncher/widgets/settings/back_button_actions.dart';
import 'package:flutter/material.dart';

import 'package:shared_preferences/shared_preferences.dart';

const _appHighlightAnimationEnabledKey = "app_highlight_animation_enabled";
const _appKeyClickEnabledKey = "app_key_click_enabled";
const _autoHideAppBar = "auto_hide_app_bar";
const _backButtonAction = "back_button_action";
const _dateFormat = "date_format";
const _showCategoryTitles = "show_category_titles";
const _showAppNamesBelowIcons = "show_app_names_below_icons";
const _showDateInStatusBar = "show_date_in_status_bar";
const _showTimeInStatusBar = "show_time_in_status_bar";
const _timeFormat = "time_format";
const String _showNetworkIndicatorInStatusBar =
    "show_network_indicator_in_status_bar";
const String _accentColor = "accent_color";
const String _screensaverClockStyle = "screensaver_clock_style";
const String _showWatchNextSection = "show_watch_next_section";
const String _watchNextBigPictureMode = "watch_next_big_picture_mode";
const String _watchNextHideProgramsWithoutImages =
    "watch_next_hide_programs_without_images";
const String _watchNextMaxItems = "watch_next_max_items";
const String _watchNextExcludedPackages = "watch_next_excluded_packages";

const List<int> WATCH_NEXT_MAX_ITEMS_OPTIONS = [0, 5, 10, 15, 20, 25, 30, 50];
const String _showFocusBorders = "show_focus_borders";
const String _focusBorderThickness = "focus_border_thickness";
const String _appIconBackgroundOpacity = 'app_icon_background_opacity';

// Focus border thickness options (in dp)
const double FOCUS_BORDER_THIN = 1.0;
const double FOCUS_BORDER_DEFAULT = 1.5;
const double FOCUS_BORDER_MEDIUM = 2.0;
const double FOCUS_BORDER_BOLD = 3.0;

const List<double> FOCUS_BORDER_THICKNESS_OPTIONS = [
  FOCUS_BORDER_THIN,
  FOCUS_BORDER_DEFAULT,
  FOCUS_BORDER_MEDIUM,
  FOCUS_BORDER_BOLD,
];

// Accent color presets (hex values)
const String ACCENT_COLOR_PURPLE = "7C4DFF";
const String ACCENT_COLOR_TEAL = "00BFA5";
const String ACCENT_COLOR_BLUE = "2979FF";
const String ACCENT_COLOR_ORANGE = "FF6D00";
const String ACCENT_COLOR_PINK = "F50057";
const String ACCENT_COLOR_GREEN = "00C853";
const String ACCENT_COLOR_WHITE = "FFFFFF";
const String ACCENT_COLOR_YELLOW = "FFD600";
const String ACCENT_COLOR_RED = "D50000";
const String ACCENT_COLOR_CYAN = "00E5FF";
const String ACCENT_COLOR_INDIGO = "536DFE";
const String ACCENT_COLOR_LIME = "AEEA00";
const String ACCENT_COLOR_AMBER = "FFAB00";
const String ACCENT_COLOR_ROSE = "FF4081";
const String ACCENT_COLOR_ICE_BLUE = "80D8FF";

class SettingsService extends ChangeNotifier {
  static final defaultDateFormat = "EEEE d";
  static final defaultTimeFormat = "H:mm";
  final SharedPreferences _sharedPreferences;

  bool get appHighlightAnimationEnabled =>
      showFocusBorders &&
      (_sharedPreferences.getBool(_appHighlightAnimationEnabledKey) ?? false);

  bool get appKeyClickEnabled =>
      _sharedPreferences.getBool(_appKeyClickEnabledKey) ?? true;

  bool get autoHideAppBarEnabled =>
      _sharedPreferences.getBool(_autoHideAppBar) ?? false;

  bool get showCategoryTitles =>
      _sharedPreferences.getBool(_showCategoryTitles) ?? false;

  bool get showAppNamesBelowIcons =>
      _sharedPreferences.getBool(_showAppNamesBelowIcons) ?? false;

  bool get showDateInStatusBar =>
      _sharedPreferences.getBool(_showDateInStatusBar) ?? false;

  bool get showTimeInStatusBar =>
      _sharedPreferences.getBool(_showTimeInStatusBar) ?? true;

  String get backButtonAction =>
      _sharedPreferences.getString(_backButtonAction) ??
      BACK_BUTTON_ACTION_NOTHING;

  String get dateFormat =>
      _sharedPreferences.getString(_dateFormat) ?? defaultDateFormat;

  String get timeFormat =>
      _sharedPreferences.getString(_timeFormat) ?? defaultTimeFormat;

  bool get showNetworkIndicatorInStatusBar =>
      _sharedPreferences.getBool(_showNetworkIndicatorInStatusBar) ?? true;

  String get accentColorHex =>
      _sharedPreferences.getString(_accentColor) ?? ACCENT_COLOR_WHITE;

  String get screensaverClockStyle =>
      _sharedPreferences.getString(_screensaverClockStyle) ?? "minimal";

  bool get showWatchNextSection =>
      _sharedPreferences.getBool(_showWatchNextSection) ?? false;

  bool get watchNextBigPictureMode =>
      _sharedPreferences.getBool(_watchNextBigPictureMode) ?? false;

  bool get watchNextHideProgramsWithoutImages =>
      _sharedPreferences.getBool(_watchNextHideProgramsWithoutImages) ?? false;

  int get watchNextMaxItems =>
      _sharedPreferences.getInt(_watchNextMaxItems) ?? 0;

  bool get showFocusBorders =>
      _sharedPreferences.getBool(_showFocusBorders) ?? true;

  double get focusBorderThickness =>
      _sharedPreferences.getDouble(_focusBorderThickness) ??
      FOCUS_BORDER_DEFAULT;

  double get appIconBackgroundOpacity =>
      ((_sharedPreferences.getDouble(_appIconBackgroundOpacity) ?? .1).clamp(
                0.0,
                1.0,
              ) *
              10)
          .round() /
      10;

  Future<void> setAppIconBackgroundOpacity(double value) async {
    await _sharedPreferences.setDouble(
      _appIconBackgroundOpacity,
      (value.clamp(0.0, 1.0) * 10).round() / 10,
    );
    notifyListeners();
  }

  Future<void> setFocusBorderThickness(double value) async {
    await _sharedPreferences.setDouble(_focusBorderThickness, value);
    notifyListeners();
  }

  Color get accentColor {
    final hex = accentColorHex;
    return Color(int.parse("0xFF$hex"));
  }

  late final Future<void> ready;

  SettingsService(this._sharedPreferences) {
    ready = _discardRetiredPreferences();
  }

  Future<void> _discardRetiredPreferences() async {
    // Upgrade cleanup only. These keys have no settings, readers or controllers.
    // Removing app configuration never writes to the TV's system settings.
    await Future.wait([
      for (final key in const [
        'brightness_scheduler_enabled',
        'brightness_morning',
        'brightness_day',
        'brightness_afternoon',
        'brightness_evening',
        'brightness_night',
        'wifi_usage_period',
        'show_wifi_widget_in_status_bar',
      ])
        if (_sharedPreferences.containsKey(key)) _sharedPreferences.remove(key),
    ]);
  }

  Future<void> set(String key, bool value) async {
    await _sharedPreferences.setBool(key, value);
    notifyListeners();
  }

  Future<void> setAppHighlightAnimationEnabled(bool value) async {
    if (value && !showFocusBorders) {
      return;
    }
    return set(_appHighlightAnimationEnabledKey, value);
  }

  Future<void> setAppKeyClickEnabled(bool value) async {
    return set(_appKeyClickEnabledKey, value);
  }

  Future<void> setAutoHideAppBarEnabled(bool value) async {
    return set(_autoHideAppBar, value);
  }

  Future<void> setBackButtonAction(String value) async {
    await _sharedPreferences.setString(_backButtonAction, value);
    notifyListeners();
  }

  Future<void> setDateTimeFormat(
    String dateFormatString,
    String timeFormatString,
  ) async {
    await Future.wait([
      _sharedPreferences.setString(_dateFormat, dateFormatString),
      _sharedPreferences.setString(_timeFormat, timeFormatString),
    ]);
    notifyListeners();
  }

  Future<void> setShowCategoryTitles(bool show) async {
    return set(_showCategoryTitles, show);
  }

  Future<void> setShowAppNamesBelowIcons(bool show) async {
    return set(_showAppNamesBelowIcons, show);
  }

  Future<void> setShowDateInStatusBar(bool show) async {
    return set(_showDateInStatusBar, show);
  }

  Future<void> setShowTimeInStatusBar(bool show) async {
    return set(_showTimeInStatusBar, show);
  }

  Future<void> setShowNetworkIndicatorInStatusBar(bool show) async {
    return set(_showNetworkIndicatorInStatusBar, show);
  }

  Future<void> setAccentColor(String colorHex) async {
    await _sharedPreferences.setString(_accentColor, colorHex);
    notifyListeners();
  }

  Future<void> setScreensaverClockStyle(String style) async {
    await _sharedPreferences.setString(_screensaverClockStyle, style);
    notifyListeners();
  }

  Future<void> setShowWatchNextSection(bool value) async {
    return set(_showWatchNextSection, value);
  }

  Future<void> setWatchNextBigPictureMode(bool value) async {
    return set(_watchNextBigPictureMode, value);
  }

  Future<void> setWatchNextHideProgramsWithoutImages(bool value) async {
    return set(_watchNextHideProgramsWithoutImages, value);
  }

  Future<void> setWatchNextMaxItems(int value) async {
    await _sharedPreferences.setInt(_watchNextMaxItems, value);
    notifyListeners();
  }

  List<String> get watchNextExcludedPackages =>
      _sharedPreferences.getStringList(_watchNextExcludedPackages) ?? const [];

  Future<void> setWatchNextExcludedPackages(List<String> packages) async {
    await _sharedPreferences.setStringList(
      _watchNextExcludedPackages,
      packages,
    );
    notifyListeners();
  }

  Future<void> toggleWatchNextExcludedPackage(String packageName) async {
    final current = List<String>.from(watchNextExcludedPackages);
    if (current.contains(packageName)) {
      current.remove(packageName);
    } else {
      current.add(packageName);
    }
    await setWatchNextExcludedPackages(current);
  }

  bool isWatchNextPackageExcluded(String packageName) =>
      watchNextExcludedPackages.contains(packageName);

  Future<void> setShowFocusBorders(bool value) async {
    await set(_showFocusBorders, value);
    if (!value) {
      await setAppHighlightAnimationEnabled(false);
    }
  }

  bool get traktBackdropsEnabled =>
      _sharedPreferences.getBool('trakt_backdrops_enabled') ?? false;
  bool get refreshBackdropOnResume =>
      _sharedPreferences.getBool('backdrop_refresh_on_resume') ?? true;
  static const backdropIntervalChoices = [5, 10, 30, 60];
  int get backdropIntervalMinutes {
    final saved = _sharedPreferences.getInt('backdrop_interval_minutes') ?? 10;
    return backdropIntervalChoices.contains(saved) ? saved : 10;
  }

  Future<void> setTraktBackdropsEnabled(bool enabled) =>
      set('trakt_backdrops_enabled', enabled);
  Future<void> setRefreshBackdropOnResume(bool enabled) =>
      set('backdrop_refresh_on_resume', enabled);
  Future<void> setBackdropIntervalMinutes(int minutes) async {
    if (!backdropIntervalChoices.contains(minutes))
      throw ArgumentError.value(minutes, 'minutes');
    await _sharedPreferences.setInt('backdrop_interval_minutes', minutes);
    notifyListeners();
  }
}
