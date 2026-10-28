import 'dart:async';
import 'dart:io';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:transparent_image/transparent_image.dart';

class CachedFeedChannel extends FLauncherChannel {
  bool allowed = true;
  bool unavailable = false;
  bool brokenBackdrop = false;
  String title = 'Cached program';
  List<Map<dynamic, dynamic>>? entries;
  Uint8List posterBytes = kTransparentImage;
  final requested = <String>[];
  final firstPosterRequest = Completer<String>();
  Completer<List<Map<dynamic, dynamic>>>? refreshGate;
  @override
  Future<bool> checkWatchNextPermission() async => allowed;
  @override
  Future<List<Map<dynamic, dynamic>>> getWatchNextItems() async {
    if (unavailable) throw StateError('Provider unavailable');
    if (refreshGate != null) return refreshGate!.future;
    if (entries != null) return entries!;
    return [
      {
        'id': 7,
        'title': title,
        'packageName': 'app.wuplay.androidtv',
        'posterUri': 'content://wuplay/poster/7',
        if (brokenBackdrop) 'backdropUri': 'content://wuplay/broken/7',
        'progressPercent': 45,
        'intentUri': 'wuplay://watch/7',
      },
    ];
  }

  @override
  Future<Uint8List> loadContentUriImage(String uri) async {
    requested.add(uri);
    if (!firstPosterRequest.isCompleted) firstPosterRequest.complete(uri);
    if (unavailable) throw StateError('Provider unavailable');
    if (uri.contains('/broken/')) return Uint8List.fromList([1, 2, 3]);
    return posterBytes;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late CachedFeedChannel channel;
  final services = <WatchNextService>[];
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('media-watch-startup');
    channel = CachedFeedChannel();
  });
  tearDown(() async {
    for (final service in services) {
      service.dispose();
    }
    services.clear();
    if (channel.refreshGate?.isCompleted == false)
      channel.refreshGate!.complete([]);
    // A cache prune already in flight may remove a child while the recursive
    // delete is enumerating it. Retry that missing-child race after disposal.
    for (var attempt = 0; attempt < 5 && await directory.exists(); attempt++) {
      try {
        await directory.delete(recursive: true);
      } on PathNotFoundException {
        // Retry only ENOENT; permission and other filesystem failures still fail.
      }
    }
    expect(await directory.exists(), isFalse);
  });
  WatchNextService start({bool autoRefresh = true}) {
    final testDirectory = directory;
    final service = WatchNextService(
      channel,
      cache: LocalMediaCache('watch', directory: () async => testDirectory),
      autoRefresh: autoRefresh,
    );
    services.add(service);
    return service;
  }

  Future<void> populate() async {
    final service = start();
    final loaded = Completer<void>();
    void listener() {
      if (service.getCachedPoster('content://wuplay/poster/7') != null &&
          !loaded.isCompleted)
        loaded.complete();
    }

    service.addListener(listener);
    await service.ready;
    await service.refreshPermissionAndItems();
    listener();
    await loaded.future.timeout(const Duration(seconds: 5));
    service.removeListener(listener);
    // Memory population precedes the atomic disk write. A restart fixture must
    // wait for the persisted poster, not just the memory notification.
    final testDirectory = directory;
    final disk = LocalMediaCache('watch', directory: () async => testDirectory);
    final deadline = DateTime.now().add(const Duration(seconds: 5));
    while (await disk.readBytes('content://wuplay/poster/7') == null) {
      if (DateTime.now().isAfter(deadline)) {
        throw TimeoutException('Poster was not persisted');
      }
      await Future<void>.delayed(const Duration(milliseconds: 2));
    }
  }

  test(
    'cached Continue Watching and poster are ready while provider refresh is blocked',
    () async {
      await populate();
      channel.refreshGate = Completer();
      final restarted = start();
      await restarted.ready;
      expect(restarted.items.single.id, 7);
      expect(restarted.items.single.progressPercent, 45);
      expect(restarted.items.single.intentUri, 'wuplay://watch/7');
      expect(
        restarted.getCachedPoster('content://wuplay/poster/7'),
        kTransparentImage,
      );
    },
  );

  test(
    'a failed provider refresh retains restored Continue Watching',
    () async {
      await populate();
      channel.unavailable = true;
      final restarted = start();
      await restarted.ready;
      await restarted.refreshPermissionAndItems();
      expect(restarted.items.single.title, 'Cached program');
      expect(
        restarted.getCachedPoster('content://wuplay/poster/7'),
        kTransparentImage,
      );
    },
  );

  test('revoked permission does not reveal cached programs', () async {
    await populate();
    channel.allowed = false;
    final restarted = start();
    await restarted.ready;
    expect(restarted.hasPermission, isFalse);
    expect(restarted.items, isEmpty);
    expect(restarted.getCachedPoster('content://wuplay/poster/7'), isNull);
  });

  test(
    'prefetch follows restored selection and protects both visible posters within the cache budget',
    () async {
      final entries = List.generate(
        14,
        (i) => <String, dynamic>{
          'id': i,
          'title': 'Program $i',
          'posterUri': 'content://wuplay/poster/$i',
        },
      );
      channel.entries = entries;
      channel.posterBytes = Uint8List(2500000)
        ..setRange(0, kTransparentImage.length, kTransparentImage);
      final testDirectory = directory;
      final cache = LocalMediaCache(
        'watch',
        directory: () async => testDirectory,
      );
      await cache.writeJson('feed', {'items': entries, 'selected': 7});
      await cache.writeBytes('content://wuplay/poster/7', channel.posterBytes);
      await cache.writeBytes('content://wuplay/poster/8', channel.posterBytes);
      final service = start(autoRefresh: false);
      await service.ready;
      await service.startBackgroundRefresh();
      expect(
        await channel.firstPosterRequest.future,
        isIn([
          'content://wuplay/poster/9',
          'content://wuplay/poster/10',
          'content://wuplay/poster/11',
          'content://wuplay/poster/12',
        ]),
      );
      final deadline = DateTime.now().add(const Duration(seconds: 5));
      while (service.getCachedPoster('content://wuplay/poster/12') == null) {
        if (DateTime.now().isAfter(deadline))
          throw TimeoutException('Prefetch did not finish');
        await Future<void>.delayed(const Duration(milliseconds: 2));
      }
      expect(service.getCachedPoster('content://wuplay/poster/7'), isNotNull);
      expect(service.getCachedPoster('content://wuplay/poster/8'), isNotNull);
    },
  );

  test(
    'fresh provider data cannot replace restored content before startup is released',
    () async {
      await populate();
      channel.title = 'Fresh program';
      final restarted = start(autoRefresh: false);
      await restarted.ready;
      await Future<void>.delayed(Duration.zero);
      expect(restarted.items.single.title, 'Cached program');
      await restarted.startBackgroundRefresh();
      expect(restarted.items.single.title, 'Fresh program');
    },
  );

  test(
    'a corrupt backdrop falls back to its poster and restores that poster offline',
    () async {
      channel.brokenBackdrop = true;
      await populate();
      channel.unavailable = true;
      final restarted = start(autoRefresh: false);
      await restarted.ready;
      expect(
        restarted.getPosterUriForItem(restarted.items.single),
        'content://wuplay/poster/7',
      );
      expect(
        restarted.getCachedPoster('content://wuplay/poster/7'),
        kTransparentImage,
      );
    },
  );
}
