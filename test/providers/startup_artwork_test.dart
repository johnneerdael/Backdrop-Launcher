import 'dart:io';
import 'package:flauncher/database.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flutter/painting.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:transparent_image/transparent_image.dart';
import '../application_rows_test_support.dart';

class ArtworkChannel extends RowsChannel {
  bool offline = false;
  Uint8List banner = kTransparentImage;
  ArtworkChannel() : super([installedApp('tv.one')]);
  @override
  Future<Uint8List> getApplicationBanner(String package) async {
    if (offline) throw StateError('Native artwork unavailable');
    return banner;
  }

  @override
  Future<Uint8List> getApplicationIcon(String package) async {
    if (offline) throw StateError('Native artwork unavailable');
    return kTransparentImage;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late FLauncherDatabase db;
  late ArtworkChannel channel;
  final services = <AppsService>[];
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    directory = await Directory.systemTemp.createTemp('media-app-startup');
    db = FLauncherDatabase.inMemory();
    channel = ArtworkChannel();
  });
  tearDown(() async {
    for (final service in services) {
      service.dispose();
    }
    services.clear();
    await db.close();
    await directory.delete(recursive: true);
  });
  Future<AppsService> start() async {
    final service = AppsService(
      channel,
      db,
      imageCache: LocalMediaCache('apps', directory: () async => directory),
    );
    services.add(service);
    await service.ready;
    return service;
  }

  test(
    'restart restores native artwork and a decoded home provider locally',
    () async {
      final first = await start();
      expect(await first.getAppBanner('tv.one'), kTransparentImage);
      channel.offline = true;
      final restarted = await start();
      expect(await restarted.getAppBanner('tv.one'), kTransparentImage);
      final image = restarted.cachedAppImage('tv.one');
      expect(image, isNotNull);
      final key = await image!.image.obtainKey(ImageConfiguration.empty);
      expect(
        PaintingBinding.instance.imageCache.statusForKey(key).keepAlive,
        isTrue,
      );
    },
  );

  test(
    'corrupt native artwork is replaced rather than surviving every restart',
    () async {
      await LocalMediaCache(
        'apps',
        directory: () async => directory,
      ).writeBytes('tv.one|1|banner', Uint8List.fromList([1, 2, 3]));
      final service = await start();
      expect(await service.getAppBanner('tv.one'), kTransparentImage);
      expect(service.cachedAppImage('tv.one'), isNotNull);
    },
  );

  test('a package version change replaces its disk artwork', () async {
    await start();
    channel.installed.single['version'] = '2';
    final data = await rootBundle.load('appicons/appletv.png');
    channel.banner = data.buffer.asUint8List(
      data.offsetInBytes,
      data.lengthInBytes,
    );
    final updated = await start();
    expect(await updated.getAppBanner('tv.one'), channel.banner);
  });

  test(
    'an app update invalidates artwork even when its displayed version is unchanged',
    () async {
      await start();
      channel.installed.single['artworkVersion'] = 'updated-install-time';
      final data = await rootBundle.load('appicons/appletv.png');
      channel.banner = data.buffer.asUint8List(
        data.offsetInBytes,
        data.lengthInBytes,
      );
      final updated = await start();
      expect(await updated.getAppBanner('tv.one'), channel.banner);
    },
  );

  test(
    'absent banners are cached too and icons remain available offline',
    () async {
      channel.banner = Uint8List(0);
      await start();
      channel.offline = true;
      final restarted = await start();
      expect(await restarted.getAppBanner('tv.one'), isEmpty);
      expect(await restarted.getAppIcon('tv.one'), kTransparentImage);
    },
  );
}
