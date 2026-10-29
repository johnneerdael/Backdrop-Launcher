import 'dart:async';
import 'dart:typed_data';

import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:transparent_image/transparent_image.dart';

import '../mocks.mocks.dart';

LocalMediaCache noDiskCache() => LocalMediaCache(
  'test',
  directory: () async => throw StateError('Storage unavailable'),
);

class DeferredCache extends LocalMediaCache {
  final Completer<Uint8List?>? poster;
  int feedReads = 0;
  DeferredCache({this.poster})
    : super('deferred', directory: () async => throw StateError('No disk'));
  @override
  Future<dynamic> readJson(String key) async {
    feedReads++;
    return null;
  }

  @override
  Future<Uint8List?> readBytes(String key, {int maxBytes = 16 * 1024 * 1024}) =>
      poster?.future ?? Future.value(null);
}

Future<void> waitUntil(bool Function() ready) async {
  final deadline = DateTime.now().add(const Duration(seconds: 3));
  while (!ready()) {
    if (DateTime.now().isAfter(deadline))
      throw TimeoutException('Poster did not load');
    await Future<void>.delayed(const Duration(milliseconds: 1));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'refresh retains visible content and selected identity while loading and after insertion',
    () async {
      final channel = MockFLauncherChannel();
      when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
      when(channel.getWatchNextItems()).thenAnswer(
        (_) async => [
          {'id': 1, 'title': 'A'},
          {'id': 2, 'title': 'B'},
          {'id': 3, 'title': 'C'},
        ],
      );
      final service = WatchNextService(channel, cache: noDiskCache());
      addTearDown(service.dispose);
      await service.refreshPermissionAndItems();
      service.setActiveHeroIndex(1);
      final gate = Completer<List<Map<dynamic, dynamic>>>();
      when(channel.getWatchNextItems()).thenAnswer((_) => gate.future);
      final refreshed = service.refreshItems();
      expect(service.isLoading, isTrue);
      expect(service.hasVisibleSection, isTrue);
      expect(service.activeHeroItem!.id, 2);
      gate.complete([
        {'id': 4, 'title': 'New'},
        {'id': 1, 'title': 'A'},
        {'id': 2, 'title': 'B'},
        {'id': 3, 'title': 'C'},
      ]);
      await refreshed;
      expect(service.activeHeroItem!.id, 2);
      expect(service.activeHeroIndex, 2);
    },
  );

  test('disabled continue watching does not poll or load images', () async {
    SharedPreferences.setMockInitialValues({});
    final settings = SettingsService(await SharedPreferences.getInstance());
    final channel = MockFLauncherChannel();
    final service = WatchNextService(
      channel,
      settingsService: settings,
      cache: noDiskCache(),
    );
    addTearDown(service.dispose);
    await Future<void>.delayed(Duration.zero);
    await service.refreshPermissionAndItems();
    verifyNever(channel.checkWatchNextPermission());
    verifyNever(channel.getWatchNextItems());
    expect(service.items, isEmpty);
  });

  test('hidden startup does not restore the disk feed', () async {
    final cache = DeferredCache();
    final service = WatchNextService(
      MockFLauncherChannel(),
      cache: cache,
      autoRefresh: false,
    );
    addTearDown(service.dispose);
    service.setActive(false);
    await service.ready;
    expect(cache.feedReads, 0);
  });

  test('a disk miss completing hidden cannot start an HTTP request', () async {
    final disk = Completer<Uint8List?>();
    var requests = 0;
    final service = WatchNextService(
      MockFLauncherChannel(),
      autoRefresh: false,
      cache: DeferredCache(poster: disk),
      client: MockClient((_) async {
        requests++;
        return http.Response.bytes(kTransparentImage, 200);
      }),
    );
    addTearDown(service.dispose);
    await service.ready;
    service.ensurePosterLoaded('https://poster.test/late');
    await Future<void>.delayed(Duration.zero);
    service.setActive(false);
    disk.complete(null);
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(requests, 0);
  });

  test(
    'return restarts missing selected artwork even when the provider is unavailable',
    () async {
      final channel = MockFLauncherChannel();
      when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
      when(channel.getWatchNextItems()).thenAnswer(
        (_) async => [
          {'id': 1, 'title': 'One', 'posterUri': 'https://poster.test/one'},
          {'id': 2, 'title': 'Two', 'posterUri': 'https://poster.test/two'},
        ],
      );
      final pending = Completer<http.Response>();
      var resumed = false;
      final requested = <String>[];
      final service = WatchNextService(
        channel,
        cache: noDiskCache(),
        client: MockClient((request) async {
          requested.add(request.url.path);
          return resumed
              ? http.Response.bytes(kTransparentImage, 200)
              : pending.future;
        }),
      );
      addTearDown(service.dispose);
      try {
        await service.refreshPermissionAndItems();
        await waitUntil(() => requested.length == 2);
        service.setActive(false);
        when(
          channel.getWatchNextItems(),
        ).thenThrow(StateError('Provider offline'));
        resumed = true;
        service.setActive(true);
        await waitUntil(
          () =>
              service.getCachedPoster('https://poster.test/one') != null &&
              service.getCachedPoster('https://poster.test/two') != null,
        );
        expect(service.items.length, 2);
      } finally {
        pending.complete(http.Response.bytes(kTransparentImage, 200));
      }
    },
  );

  test(
    'hidden Home does not refresh or start poster work and resumes the selected pair',
    () async {
      final channel = MockFLauncherChannel();
      when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
      when(channel.getWatchNextItems()).thenAnswer(
        (_) async => [
          for (var i = 0; i < 8; i++)
            {'id': i, 'title': '$i', 'posterUri': 'https://poster.test/$i'},
        ],
      );
      final requested = <String>[];
      final service = WatchNextService(
        channel,
        cache: noDiskCache(),
        client: MockClient((request) async {
          requested.add(request.url.path);
          return http.Response.bytes(kTransparentImage, 200);
        }),
      );
      addTearDown(service.dispose);
      service.setActive(false);
      await service.refreshPermissionAndItems();
      service.ensurePosterLoaded('https://poster.test/hidden');
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(requested, isEmpty);
      verifyNever(channel.getWatchNextItems());
      service.setActive(true);
      await service.refreshPermissionAndItems();
      await waitUntil(() => requested.length >= 2);
      expect(requested, ['/0', '/1']);
    },
  );

  test(
    'a poster finishing after Home is hidden cannot refill its cache',
    () async {
      final gate = Completer<http.Response>();
      final service = WatchNextService(
        MockFLauncherChannel(),
        cache: noDiskCache(),
        client: MockClient((_) => gate.future),
      );
      addTearDown(service.dispose);
      service.ensurePosterLoaded('https://poster.test/pending');
      await Future<void>.delayed(Duration.zero);
      service.setActive(false);
      gate.complete(http.Response.bytes(kTransparentImage, 200));
      await Future<void>.delayed(const Duration(milliseconds: 30));
      expect(service.getCachedPoster('https://poster.test/pending'), isNull);
    },
  );

  test('enabling loads WuPlay entries and disabling releases them', () async {
    SharedPreferences.setMockInitialValues({});
    final settings = SettingsService(await SharedPreferences.getInstance());
    final channel = MockFLauncherChannel();
    when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
    when(channel.getWatchNextItems()).thenAnswer(
      (_) async => [
        {
          'id': 1,
          'title': 'Movie',
          'packageName': 'app.wuplay.androidtv',
          'intentUri': 'wuplay://play/movie/tt0137523',
          'progressPercent': 45,
          'posterUri': 'content://test/poster',
        },
      ],
    );
    final service = WatchNextService(
      channel,
      settingsService: settings,
      cache: noDiskCache(),
    );
    addTearDown(service.dispose);
    await settings.setShowWatchNextSection(true);
    await service.refreshPermissionAndItems();
    expect(service.items.single.progressPercent, 45);
    expect(service.items.single.intentUri, 'wuplay://play/movie/tt0137523');
    await service.launchItem(service.items.single);
    verify(
      channel.launchWatchNextItem(
        'app.wuplay.androidtv',
        null,
        'wuplay://play/movie/tt0137523',
      ),
    );
    await settings.setShowWatchNextSection(false);
    expect(service.items, isEmpty);
    expect(service.getCachedPoster('content://test/poster'), isNull);
  });

  test('poster requests wait for capacity instead of being dropped', () async {
    final gates = List.generate(3, (_) => Completer<http.Response>());
    final requested = <String>[];
    final service = WatchNextService(
      MockFLauncherChannel(),
      cache: noDiskCache(),
      client: MockClient((request) async {
        requested.add(request.url.path);
        return gates[int.parse(request.url.path.substring(1)) - 1].future;
      }),
    );
    addTearDown(service.dispose);
    for (var i = 1; i <= 3; i++) {
      service.ensurePosterLoaded('https://poster.test/$i');
    }
    await Future<void>.delayed(Duration.zero);
    expect(requested, ['/1', '/2']);
    gates[0].complete(http.Response.bytes(kTransparentImage, 200));
    await Future<void>.delayed(Duration.zero);
    gates[1].complete(http.Response.bytes(kTransparentImage, 200));
    gates[2].complete(http.Response.bytes(kTransparentImage, 200));
    await waitUntil(
      () => service.getCachedPoster('https://poster.test/3') != null,
    );
    expect(requested, contains('/3'));
    expect(service.getCachedPoster('https://poster.test/3'), kTransparentImage);
  });

  test('posters notify again after disabling during a debounce', () async {
    SharedPreferences.setMockInitialValues({'show_watch_next_section': true});
    final settings = SettingsService(await SharedPreferences.getInstance());
    var uri = 'content://poster/first';
    final channel = MockFLauncherChannel();
    when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
    when(channel.getWatchNextItems()).thenAnswer(
      (_) async => [
        {'id': 1, 'title': 'Movie', 'posterUri': uri},
      ],
    );
    when(
      channel.loadContentUriImage(any),
    ).thenAnswer((_) async => kTransparentImage);
    final service = WatchNextService(
      channel,
      settingsService: settings,
      cache: noDiskCache(),
    );
    try {
      await service.refreshPermissionAndItems();
      await waitUntil(() => service.getCachedPoster(uri) != null);
      expect(service.getCachedPoster(uri), isNotNull);
      await settings.setShowWatchNextSection(false);
      uri = 'content://poster/second';
      await settings.setShowWatchNextSection(true);
      await service.refreshPermissionAndItems();
      await waitUntil(() => service.getCachedPoster(uri) != null);
      var notifications = 0;
      service.addListener(() => notifications++);
      service.ensurePosterLoaded('content://poster/second');
      await waitUntil(
        () => service.getCachedPoster('content://poster/second') != null,
      );
      expect(service.getCachedPoster('content://poster/second'), isNotNull);
      final before = notifications;
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(notifications, greaterThan(before));
    } finally {
      service.dispose();
    }
  });

  test('failed backdrops automatically request the available poster', () async {
    final channel = MockFLauncherChannel();
    when(channel.checkWatchNextPermission()).thenAnswer((_) async => true);
    when(channel.getWatchNextItems()).thenAnswer(
      (_) async => [
        {
          'id': 1,
          'title': 'Movie',
          'posterUri': 'https://poster.test/good',
          'backdropUri': 'https://poster.test/missing',
        },
      ],
    );
    final service = WatchNextService(
      channel,
      cache: noDiskCache(),
      client: MockClient(
        (request) async => request.url.path == '/missing'
            ? http.Response('', 404)
            : http.Response.bytes(kTransparentImage, 200),
      ),
    );
    addTearDown(service.dispose);
    await service.refreshPermissionAndItems();
    await waitUntil(
      () => service.getCachedPoster('https://poster.test/good') != null,
    );
    expect(
      service.getPosterUriForItem(service.items.single),
      'https://poster.test/good',
    );
    expect(service.getCachedPoster('https://poster.test/good'), isNotNull);
  });
}
