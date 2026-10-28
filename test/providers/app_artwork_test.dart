import 'package:flauncher/bundled_artwork.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
  });

  test(
    'selected packaged artwork survives restart and clearing restores default',
    () async {
      final artwork = AppArtwork(prefs);
      await artwork.set('app.wuplay.androidtv', 'appicons/wuplay.png');
      final restarted = AppArtwork(prefs);
      expect(await restarted.has('app.wuplay.androidtv'), isTrue);
      expect(
        await restarted.bytes('app.wuplay.androidtv'),
        (await rootBundle.load('appicons/wuplay.png')).buffer.asUint8List(),
      );
      await restarted.clear('app.wuplay.androidtv');
      expect(await restarted.bytes('app.wuplay.androidtv'), isNull);
    },
  );

  test(
    'external files and legacy custom files cannot become app artwork',
    () async {
      final artwork = AppArtwork(prefs);
      for (final path in [
        '/sdcard/icon.png',
        'assets/icon.png',
        'appicons/../assets/icon.png',
        'appicons/missing.png',
      ]) {
        await expectLater(artwork.set('test.app', path), throwsArgumentError);
      }
      await prefs.setString('custom_banner_test.app', '/sdcard/old.png');
      expect(await artwork.has('test.app'), isFalse);
      expect(await artwork.bytes('test.app'), isNull);
    },
  );
}
