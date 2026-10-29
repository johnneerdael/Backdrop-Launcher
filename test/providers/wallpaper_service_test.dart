import 'dart:io';
import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flauncher/bundled_artwork.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late Directory directory;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    directory = await Directory.systemTemp.createTemp('arc-backdrops-test');
  });
  tearDown(() async {
    // An in-flight atomic-cache cleanup can remove a child during recursive deletion.
    for (var attempt = 0; attempt < 5 && await directory.exists(); attempt++) {
      try {
        await directory.delete(recursive: true);
      } on PathNotFoundException {
        // Retry only the missing-child race; other filesystem errors still fail.
      }
    }
    expect(await directory.exists(), isFalse);
  });

  testWidgets('activation waits a fresh configured interval before rotating', (
    tester,
  ) async {
    late WallpaperService wallpaper;
    late String original;
    late String next;
    late SettingsService settings;
    await tester.runAsync(() async {
      settings = SettingsService(prefs);
      wallpaper = WallpaperService(
        settings,
        cacheDirectory: () async => directory,
      );
      wallpaper.setActive(false);
      await wallpaper.ready;
      original = (wallpaper.wallpaper as AssetImage).assetName;
      final images = await BundledArtwork.backdrops();
      next = images[(images.indexOf(original) + 1) % images.length];
      await prepareImage(wallpaperImage(AssetImage(next)));
    });
    try {
      wallpaper.setActive(true);
      await tester.pump();
      expect((wallpaper.wallpaper as AssetImage).assetName, original);
      await settings.setBackdropIntervalMinutes(5);
      await tester.pump();
      expect((wallpaper.wallpaper as AssetImage).assetName, original);
      await tester.pump(const Duration(minutes: 4, seconds: 59));
      expect((wallpaper.wallpaper as AssetImage).assetName, original);
      await tester.pump(const Duration(seconds: 1));
      expect((wallpaper.wallpaper as AssetImage).assetName, next);
    } finally {
      wallpaper.dispose();
    }
  });

  test(
    'restart restores the last bundled backdrop without a network request',
    () async {
      final first = WallpaperService(
        SettingsService(prefs),
        cacheDirectory: () async => directory,
      );
      await first.ready;
      await first.nextBackdrop();
      await first.nextBackdrop();
      final previous = (first.wallpaper as AssetImage).assetName;
      first.dispose();
      final restarted = WallpaperService(
        SettingsService(prefs),
        cacheDirectory: () async => directory,
        client: MockClient(
          (_) async => throw StateError('Startup must stay local'),
        ),
      );
      addTearDown(restarted.dispose);
      await restarted.ready;
      expect((restarted.wallpaper as AssetImage).assetName, previous);
    },
  );

  test('app packages exactly the ten supplied still backdrops', () async {
    final backdrops = await BundledArtwork.backdrops();
    expect(backdrops, hasLength(10));
    expect(
      backdrops.every((p) => p.startsWith('backdrops/') && p.endsWith('.jpg')),
      isTrue,
    );
    for (final path in backdrops) {
      expect(await File(path).length(), greaterThan(0));
    }
  });

  test(
    'default rotation visits all ten bundled backdrops without network',
    () async {
      final wallpaper = WallpaperService(
        SettingsService(prefs),
        cacheDirectory: () async => directory,
        client: MockClient(
          (_) async => throw StateError('Offline defaults must not use HTTP'),
        ),
      );
      addTearDown(wallpaper.dispose);
      await wallpaper.ready;
      final visited = <String>{};
      for (var i = 0; i < 10; i++) {
        visited.add((wallpaper.wallpaper as AssetImage).assetName);
        await wallpaper.nextBackdrop();
      }
      expect(visited, hasLength(10));
      expect(wallpaper.error, isNull);
    },
  );

  test('network failure leaves a bundled backdrop available', () async {
    final settings = SettingsService(prefs);
    await settings.setTraktBackdropsEnabled(true);
    await prefs.setString(
      'trakt_token',
      '{"access_token":"access","refresh_token":"refresh","expires_at":9999999999}',
    );
    final trakt = TraktService(
      prefs,
      config: const TraktConfig(clientId: 'client', tmdbApiKey: 'tmdb'),
      client: MockClient((_) async => throw const SocketException('offline')),
    );
    addTearDown(trakt.dispose);
    final wallpaper = WallpaperService(
      settings,
      trakt: trakt,
      cacheDirectory: () async => directory,
    );
    addTearDown(wallpaper.dispose);
    await wallpaper.ready;
    await wallpaper.nextBackdrop();
    expect(wallpaper.wallpaper, isA<AssetImage>());
    expect(wallpaper.error, isNotNull);
  });

  test('legacy local wallpaper and provider preferences are ignored', () async {
    await prefs.setBool('reddit_wallpaper_enabled', true);
    await prefs.setBool('unsplash_slideshow_enabled', true);
    await File('${directory.path}/wallpaper').writeAsString('legacy wallpaper');
    final wallpaper = WallpaperService(
      SettingsService(prefs),
      cacheDirectory: () async => directory,
    );
    addTearDown(wallpaper.dispose);
    await wallpaper.ready;
    expect(wallpaper.wallpaper, isA<AssetImage>());
  });

  Future<TraktService> onlineTrakt() async {
    await prefs.setString(
      'trakt_token',
      '{"access_token":"access","refresh_token":"refresh","expires_at":9999999999}',
    );
    final trakt = TraktService(
      prefs,
      config: const TraktConfig(clientId: 'client', tmdbApiKey: 'tmdb'),
      client: MockClient(
        (request) async => request.url.host == 'api.themoviedb.org'
            ? http.Response(
                jsonEncode({
                  'backdrop_path': '/${request.url.pathSegments.last}.png',
                }),
                200,
              )
            : http.Response(
                jsonEncode(
                  List.generate(
                    4,
                    (i) => {
                      'movie': {
                        'title': 'Movie $i',
                        'ids': {'tmdb': 550 + i},
                      },
                    },
                  ),
                ),
                200,
              ),
      ),
    );
    await trakt.setSelectedCatalogs(['anticipated_movies']);
    addTearDown(trakt.dispose);
    return trakt;
  }

  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==',
  );

  Future<(File, String?)> cachedTraktBackdrop() async {
    final trakt = await onlineTrakt();
    final settings = SettingsService(prefs);
    await settings.setTraktBackdropsEnabled(true);
    final wallpaper = WallpaperService(
      settings,
      trakt: trakt,
      cacheDirectory: () async => directory,
      client: MockClient(
        (_) async => http.Response.bytes(
          png,
          200,
          headers: {'content-type': 'image/png'},
        ),
      ),
    );
    await wallpaper.ready;
    await wallpaper.nextBackdrop();
    final result = ((wallpaper.wallpaper as FileImage).file, wallpaper.title);
    wallpaper.dispose();
    return result;
  }

  test(
    'offline startup restores the exact cached Trakt wallpaper and title',
    () async {
      final (file, title) = await cachedTraktBackdrop();
      final offline = TraktService(
        prefs,
        client: MockClient((_) async => throw StateError('No startup HTTP')),
      );
      addTearDown(offline.dispose);
      final restarted = WallpaperService(
        SettingsService(prefs),
        trakt: offline,
        cacheDirectory: () async => directory,
        client: MockClient((_) async => throw StateError('No startup HTTP')),
      );
      addTearDown(restarted.dispose);
      await restarted.ready;
      expect((restarted.wallpaper as FileImage).file.path, file.path);
      expect(restarted.title, title);
      await restarted.nextBackdrop();
      expect((restarted.wallpaper as FileImage).file.path, file.path);
    },
  );

  test(
    'a removed cached Trakt image falls back locally without waiting for HTTP',
    () async {
      final (file, _) = await cachedTraktBackdrop();
      await file.delete();
      final offline = TraktService(
        prefs,
        client: MockClient((_) async => throw StateError('No startup HTTP')),
      );
      addTearDown(offline.dispose);
      final restarted = WallpaperService(
        SettingsService(prefs),
        trakt: offline,
        cacheDirectory: () async => directory,
        client: MockClient((_) async => throw StateError('No startup HTTP')),
      );
      addTearDown(restarted.dispose);
      await restarted.ready;
      expect(restarted.wallpaper, isA<AssetImage>());
    },
  );

  test(
    'existing Trakt artwork is reused when upgrading without last-wallpaper metadata',
    () async {
      final (file, _) = await cachedTraktBackdrop();
      await LocalMediaCache(
        'wallpaper',
        directory: () async => directory,
      ).remove('last');
      final offline = TraktService(
        prefs,
        client: MockClient((_) async => throw StateError('No startup HTTP')),
      );
      addTearDown(offline.dispose);
      final restarted = WallpaperService(
        SettingsService(prefs),
        trakt: offline,
        cacheDirectory: () async => directory,
      );
      addTearDown(restarted.dispose);
      await restarted.ready;
      expect((restarted.wallpaper as FileImage).file.path, file.path);
    },
  );

  test('Trakt rotation caches at most three valid backdrops', () async {
    final trakt = await onlineTrakt();
    final settings = SettingsService(prefs);
    await settings.setTraktBackdropsEnabled(true);
    final requested = <String>{};
    final wallpaper = WallpaperService(
      settings,
      trakt: trakt,
      cacheDirectory: () async => directory,
      random: Random(1),
      client: MockClient((request) async {
        requested.add(request.url.path);
        return http.Response.bytes(
          png,
          200,
          headers: {'content-type': 'image/png'},
        );
      }),
    );
    addTearDown(wallpaper.dispose);
    await wallpaper.ready;
    await wallpaper.nextBackdrop();
    for (var i = 0; i < 20; i++) {
      await wallpaper.nextBackdrop();
    }
    expect(wallpaper.wallpaper, isA<FileImage>());
    expect(wallpaper.error, isNull);
    expect(requested.length, 4);
    final files = await Directory(
      '${directory.path}/trakt_backdrops',
    ).list().toList();
    expect(files.length, WallpaperService.maxCachedBackdrops);
  });

  test('disabling Trakt discards a backdrop download in flight', () async {
    final trakt = await onlineTrakt();
    final settings = SettingsService(prefs);
    await settings.setTraktBackdropsEnabled(true);
    final started = Completer<void>();
    final download = Completer<http.Response>();
    final wallpaper = WallpaperService(
      settings,
      trakt: trakt,
      cacheDirectory: () async => directory,
      client: MockClient((request) async {
        started.complete();
        return download.future;
      }),
    );
    addTearDown(wallpaper.dispose);
    await wallpaper.ready;
    final advance = wallpaper.nextBackdrop();
    await started.future;
    await settings.setTraktBackdropsEnabled(false);
    download.complete(
      http.Response.bytes(png, 200, headers: {'content-type': 'image/png'}),
    );
    await advance;
    expect(wallpaper.wallpaper, isA<AssetImage>());
  });

  test('oversized backdrop downloads keep the offline fallback', () async {
    final trakt = await onlineTrakt();
    final settings = SettingsService(prefs);
    await settings.setTraktBackdropsEnabled(true);
    final wallpaper = WallpaperService(
      settings,
      trakt: trakt,
      cacheDirectory: () async => directory,
      client: MockClient.streaming(
        (request, _) async => http.StreamedResponse(
          const Stream.empty(),
          200,
          contentLength: WallpaperService.maxDownloadBytes + 1,
          headers: {'content-type': 'image/jpeg'},
        ),
      ),
    );
    addTearDown(wallpaper.dispose);
    await wallpaper.ready;
    await wallpaper.nextBackdrop();
    expect(wallpaper.wallpaper, isA<AssetImage>());
    expect(wallpaper.error, isNotNull);
  });
}
