import 'package:flauncher/providers/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'Upgrading discards retired device-control preferences and preserves launcher data',
    () async {
      SharedPreferences.setMockInitialValues({
        'brightness_scheduler_enabled': true,
        'brightness_morning': 60,
        'brightness_day': 100,
        'brightness_afternoon': 80,
        'brightness_evening': 50,
        'brightness_night': 20,
        'wifi_usage_period': 'monthly',
        'show_wifi_widget_in_status_bar': true,
        'accent_color': '00BFA5',
        'app_icon_background_opacity': .3,
        'trakt_token': 'fixture-session',
        'wallpaper_source': 'fixture-wallpaper',
      });
      final preferences = await SharedPreferences.getInstance();
      final settings = SettingsService(preferences);
      addTearDown(settings.dispose);
      await settings.ready;
      await preferences.reload();
      expect(preferences.getKeys(), {
        'accent_color',
        'app_icon_background_opacity',
        'trakt_token',
        'wallpaper_source',
      });
      expect(preferences.getString('accent_color'), '00BFA5');
      expect(preferences.getDouble('app_icon_background_opacity'), .3);
      expect(preferences.getString('trakt_token'), 'fixture-session');
      expect(preferences.getString('wallpaper_source'), 'fixture-wallpaper');
    },
  );
}
