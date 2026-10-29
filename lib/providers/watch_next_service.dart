/*
 * FLauncher
 * Copyright (C) 2021 Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:async';
import 'dart:collection';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/models/watch_next_item.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:http/http.dart' as http;

class WatchNextService extends ChangeNotifier {
  final FLauncherChannel _fLauncherChannel;
  final SettingsService? _settingsService;
  final http.Client _client;
  final LocalMediaCache _cache;
  final Future<bool> Function(ImageProvider) _imagePreparer;
  late final Future<void> _ready;
  Future<void> get ready => _ready;
  bool _refreshEnabled;
  List<WatchNextItem> _allRawItems = [];
  List<WatchNextItem> _items = [];
  bool _isLoading = false;
  bool _hasPermission = false;
  bool _disposed = false;
  bool _active = true;
  bool _cacheRestored = false;
  bool _wasEnabled = false;
  int _generation = 0;
  int _activeHeroIndex = 0;
  final _posterCache = LinkedHashMap<String, Uint8List>();
  final Map<String, int> _loadingPosters = {};
  final _pendingPosters = LinkedHashSet<String>();
  final Set<String> _failedPosters = {};
  Timer? _refreshTimer;
  Timer? _posterNotifyDebounce;
  Future<void>? _permissionRefresh;
  Future<void>? _itemsRefresh;
  static const maxPosterCacheBytes = 8 * 1024 * 1024;
  static const maxPosterBytes = 4 * 1024 * 1024;
  static const maxCachedPosters = 12;

  List<WatchNextItem> get items => _items;
  bool get isLoading => _isLoading;
  bool get hasPermission => _hasPermission;
  bool get hasItems => _items.isNotEmpty;
  bool get hasVisibleSection =>
      _enabled &&
      (!_isLoading || _items.isNotEmpty) &&
      (!_hasPermission || _items.isNotEmpty);
  int get activeHeroIndex => _activeHeroIndex;
  WatchNextItem? get activeHeroItem =>
      _items.isNotEmpty ? _items[_activeHeroIndex] : null;
  bool get _enabled =>
      !_disposed && (_settingsService?.showWatchNextSection ?? true);
  bool get _canLoad => _enabled && _active;
  Set<String> get availablePackageNames =>
      _allRawItems.map((i) => i.packageName).whereType<String>().toSet();

  WatchNextService(
    this._fLauncherChannel, {
    SettingsService? settingsService,
    http.Client? client,
    LocalMediaCache? cache,
    Future<bool> Function(ImageProvider)? imagePreparer,
    bool autoRefresh = true,
  }) : _settingsService = settingsService,
       _client = client ?? http.Client(),
       _cache = cache ?? LocalMediaCache('continue_watching'),
       _imagePreparer = imagePreparer ?? prepareImage,
       _refreshEnabled = autoRefresh {
    _ready = Future<void>.microtask(_restoreCache);
    _settingsService?.addListener(_onSettingsChanged);
    _onSettingsChanged();
  }

  Future<void> _restoreCache() async {
    if (!_canLoad) return;
    final generation = _generation;
    try {
      final saved = await _cache.readJson('feed');
      if (!_canLoad || generation != _generation) return;
      if (saved is! Map || saved['items'] is! List) return;
      final items = (saved['items'] as List)
          .take(50)
          .map((item) => WatchNextItem.fromMap(item as Map))
          .toList();
      final allowed = await _fLauncherChannel.checkWatchNextPermission();
      if (!_canLoad || generation != _generation) return;
      if (!allowed) {
        await _cache.prune({});
        return;
      }
      _hasPermission = true;
      _allRawItems = items;
      _applyFilterAndNotify();
      final selected = _items.indexWhere(
        (item) => item.id == saved['selected'],
      );
      if (selected >= 0) _activeHeroIndex = selected;
      final initial = [
        if (activeHeroItem != null) activeHeroItem!,
        ..._items.skip(_activeHeroIndex + 1).take(1),
      ];
      await Future.wait(
        initial.map((item) async {
          for (final uri in [
            item.backdropUri,
            item.posterUri,
          ].whereType<String>()) {
            final bytes = await _cache.readBytes(uri, maxBytes: maxPosterBytes);
            if (!_canLoad || generation != _generation) return;
            if (bytes == null || bytes.isEmpty) continue;
            final image = ResizeImage(MemoryImage(bytes), width: 540);
            final valid = await _imagePreparer(image);
            if (!_canLoad || generation != _generation) {
              unawaited(image.evict());
              return;
            }
            if (valid) {
              _posterCache[uri] = bytes;
              return;
            }
            await _cache.remove(uri);
          }
        }),
      );
      if (_enabled && generation == _generation) _notify();
    } catch (_) {
      // A stale or unavailable cache must not block the provider refresh.
    } finally {
      if (_canLoad && generation == _generation) _cacheRestored = true;
    }
  }

  Future<void> _saveFeed() async {
    if (!_enabled || !_hasPermission) return;
    await _cache.writeJson('feed', {
      'items': _allRawItems.take(50).map((item) => item.toMap()).toList(),
      'selected': activeHeroItem?.id,
    });
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _onSettingsChanged() {
    if (_enabled != _wasEnabled) {
      _wasEnabled = _enabled;
      _generation++;
      _refreshTimer?.cancel();
      if (_enabled) {
        _cacheRestored = false;
        unawaited(refreshPermissionAndItems());
        _restartRefreshTimer();
      } else {
        _allRawItems = [];
        _items = [];
        _posterCache.clear();
        _failedPosters.clear();
        _isLoading = false;
        _posterNotifyDebounce?.cancel();
        _posterNotifyDebounce = null;
        _pendingPosters.clear();
      }
    }
    _applyFilterAndNotify();
  }

  void _restartRefreshTimer() {
    _refreshTimer?.cancel();
    _refreshTimer = _canLoad
        ? Timer.periodic(
            const Duration(minutes: 5),
            (_) => unawaited(refreshPermissionAndItems()),
          )
        : null;
  }

  void setActive(bool active) {
    if (_disposed || _active == active) return;
    _active = active;
    _generation++;
    _isLoading = false;
    _pendingPosters.clear();
    _loadingPosters.clear();
    _permissionRefresh = null;
    _itemsRefresh = null;
    _posterNotifyDebounce?.cancel();
    _posterNotifyDebounce = null;
    _restartRefreshTimer();
    if (_canLoad) {
      _preloadInitialPosters(_generation);
      unawaited(refreshPermissionAndItems());
    }
  }

  String? getPosterUriForItem(WatchNextItem? item) {
    if (item == null) return null;
    for (final uri in [item.backdropUri, item.posterUri]) {
      if (_posterCache.containsKey(uri) && !_failedPosters.contains(uri))
        return uri;
    }
    for (final uri in [item.backdropUri, item.posterUri]) {
      if (uri != null && uri.trim().isNotEmpty && !_failedPosters.contains(uri))
        return uri;
    }
    return null;
  }

  bool itemHasImage(WatchNextItem item) => getPosterUriForItem(item) != null;

  void setActiveHeroIndex(int index) {
    if (_items.isEmpty) return;
    _activeHeroIndex = index.clamp(0, _items.length - 1);
    ensurePosterLoaded(getPosterUriForItem(activeHeroItem));
    unawaited(_saveFeed());
    _notify();
  }

  void nextHeroItem() {
    if (_items.isNotEmpty)
      setActiveHeroIndex((_activeHeroIndex + 1) % _items.length);
  }

  void prevHeroItem() {
    if (_items.isNotEmpty)
      setActiveHeroIndex(
        (_activeHeroIndex - 1 + _items.length) % _items.length,
      );
  }

  Future<void> checkPermission() async {
    if (!_canLoad) return;
    final generation = _generation;
    bool allowed;
    try {
      allowed = await _fLauncherChannel.checkWatchNextPermission();
    } catch (_) {
      allowed = false;
    }
    if (_disposed || generation != _generation) return;
    _hasPermission = allowed;
    _notify();
  }

  Future<void> requestPermission() async {
    await _fLauncherChannel.requestWatchNextPermission();
    await refreshPermissionAndItems();
  }

  Future<void> forceRefresh() async {
    _posterCache.clear();
    _failedPosters.clear();
    await refreshPermissionAndItems();
  }

  Future<void> startBackgroundRefresh() {
    _refreshEnabled = true;
    return refreshPermissionAndItems();
  }

  Future<void> refreshPermissionAndItems() {
    if (!_refreshEnabled || !_canLoad) return ready;
    if (_permissionRefresh != null) return _permissionRefresh!;
    late final Future<void> pending;
    pending = _refreshPermissionAndItems().whenComplete(() {
      if (identical(_permissionRefresh, pending)) _permissionRefresh = null;
    });
    return _permissionRefresh = pending;
  }

  Future<void> _refreshPermissionAndItems() async {
    final generation = _generation;
    await ready;
    if (!_canLoad || generation != _generation) return;
    if (!_cacheRestored) await _restoreCache();
    if (!_canLoad || generation != _generation) return;
    await checkPermission();
    if (!_canLoad || generation != _generation) return;
    if (_hasPermission) {
      await refreshItems();
    } else {
      _allRawItems = [];
      _posterCache.clear();
      await _cache.prune({});
      _applyFilterAndNotify();
    }
  }

  Future<void> refreshItems() {
    if (!_canLoad) return Future<void>.value();
    if (_itemsRefresh != null) return _itemsRefresh!;
    late final Future<void> pending;
    pending = _refreshItems().whenComplete(() {
      if (identical(_itemsRefresh, pending)) _itemsRefresh = null;
    });
    return _itemsRefresh = pending;
  }

  Future<void> _refreshItems() async {
    if (!_canLoad || _isLoading || !_hasPermission) return;
    final generation = _generation;
    _isLoading = true;
    _notify();
    try {
      final results = await _fLauncherChannel.getWatchNextItems();
      if (!_canLoad || generation != _generation) return;
      _allRawItems = results.take(50).map(WatchNextItem.fromMap).toList();
      _applyFilterAndNotify();
      // The first visible cards are prefetched; later cards request images on demand.
      _preloadInitialPosters(generation);
      await _saveFeed();
    } catch (_) {
      // Preserve locally restored content during a transient provider failure.
    } finally {
      if (generation == _generation) {
        _isLoading = false;
        _notify();
      }
    }
  }

  void _applyFilterAndNotify() {
    final selectedId = activeHeroItem?.id;
    final excluded = _settingsService?.watchNextExcludedPackages ?? const [];
    final hideWithoutImages =
        _settingsService?.watchNextHideProgramsWithoutImages ?? false;
    final maxItems = _settingsService?.watchNextMaxItems ?? 0;
    _items = _allRawItems
        .where(
          (i) =>
              !excluded.contains(i.packageName) &&
              (!hideWithoutImages || itemHasImage(i)),
        )
        .take(maxItems > 0 ? maxItems : 50)
        .toList();
    final preserved = _items.indexWhere((item) => item.id == selectedId);
    _activeHeroIndex = preserved >= 0
        ? preserved
        : (_items.isEmpty ? 0 : _activeHeroIndex.clamp(0, _items.length - 1));
    final retained = _allRawItems
        .expand((i) => [i.posterUri, i.backdropUri])
        .whereType<String>()
        .toSet();
    _posterCache.removeWhere((uri, _) => !retained.contains(uri));
    _failedPosters.removeWhere((uri) => !retained.contains(uri));
    _notify();
  }

  void _preloadInitialPosters(int generation) {
    if (generation != _generation) return;
    for (final item in _items.skip(_activeHeroIndex).take(2)) {
      ensurePosterLoaded(getPosterUriForItem(item));
    }
  }

  void ensurePosterLoaded(String? uri) {
    if (!_canLoad ||
        uri == null ||
        _posterCache.containsKey(uri) ||
        _failedPosters.contains(uri) ||
        _loadingPosters[uri] == _generation)
      return;
    _pendingPosters.add(uri);
    _drainPosterQueue();
  }

  void _drainPosterQueue() {
    while (_canLoad &&
        _loadingPosters.length < 2 &&
        _pendingPosters.isNotEmpty) {
      final ready = _pendingPosters.where(
        (uri) => !_loadingPosters.containsKey(uri),
      );
      if (ready.isEmpty) return;
      final uri = ready.first;
      _pendingPosters.remove(uri);
      final generation = _generation;
      _loadingPosters[uri] = generation;
      unawaited(_loadPosterImage(uri, generation));
    }
  }

  Future<void> _loadPosterImage(String uri, int generation) async {
    try {
      Uint8List bytes;
      var cached = await _cache.readBytes(uri, maxBytes: maxPosterBytes);
      if (!_canLoad || generation != _generation) return;
      if (cached != null && !await validImageBytes(cached)) {
        await _cache.remove(uri);
        cached = null;
      }
      if (!_canLoad || generation != _generation) return;
      if (cached != null && cached.isNotEmpty) {
        bytes = cached;
      } else if (uri.startsWith('content://')) {
        bytes = await _fLauncherChannel.loadContentUriImage(uri);
      } else {
        final url = Uri.parse(uri);
        if (url.scheme != 'https' && url.scheme != 'http')
          throw const FormatException('Unsupported poster URI');
        final response = await _client
            .send(http.Request('GET', url))
            .timeout(const Duration(seconds: 10));
        if (!_canLoad || generation != _generation) {
          await response.stream.listen((_) {}).cancel();
          return;
        }
        if (response.statusCode != 200 ||
            (response.contentLength ?? 0) > maxPosterBytes) {
          await response.stream.listen((_) {}).cancel();
          throw const FormatException('Poster download failed');
        }
        final builder = BytesBuilder(copy: false);
        await for (final chunk in response.stream.timeout(
          const Duration(seconds: 10),
        )) {
          if (!_canLoad || generation != _generation) return;
          if (builder.length + chunk.length > maxPosterBytes)
            throw const FormatException('Poster too large');
          builder.add(chunk);
        }
        bytes = builder.takeBytes();
      }
      if (!_canLoad || generation != _generation) return;
      if (bytes.isEmpty ||
          bytes.length > maxPosterBytes ||
          !await validImageBytes(bytes))
        throw const FormatException('Invalid poster');
      if (!_canLoad || generation != _generation) return;
      _posterCache[uri] = bytes;
      final protected = <String?>{
        getPosterUriForItem(activeHeroItem),
        if (_activeHeroIndex + 1 < _items.length)
          getPosterUriForItem(_items[_activeHeroIndex + 1]),
      };
      while (_posterCache.length > maxCachedPosters ||
          _posterCache.values.fold<int>(0, (sum, b) => sum + b.length) >
              maxPosterCacheBytes) {
        final evictable = _posterCache.keys.where(
          (key) => !protected.contains(key),
        );
        if (evictable.isEmpty) break;
        _posterCache.remove(evictable.first);
      }
      await _cache.writeBytes(uri, bytes);
      if (_enabled && generation == _generation) {
        await _cache.prune({'feed', ..._posterCache.keys});
      }
    } catch (_) {
      if (_enabled && generation == _generation) {
        _failedPosters.add(uri);
        for (final item in _items.toList()) {
          if (item.backdropUri == uri || item.posterUri == uri) {
            ensurePosterLoaded(getPosterUriForItem(item));
          }
        }
        if (_settingsService?.watchNextHideProgramsWithoutImages ?? false)
          _applyFilterAndNotify();
      }
    } finally {
      if (_loadingPosters[uri] == generation) _loadingPosters.remove(uri);
      _drainPosterQueue();
      if (_enabled && generation == _generation) {
        _posterNotifyDebounce ??= Timer(const Duration(milliseconds: 80), () {
          _posterNotifyDebounce = null;
          _notify();
        });
      }
    }
  }

  Uint8List? getCachedPoster(String? uri) {
    if (uri == null) return null;
    final bytes = _posterCache.remove(uri);
    if (bytes != null) _posterCache[uri] = bytes;
    return bytes;
  }

  bool hasPosterLoadFailed(String? uri) =>
      uri == null || _failedPosters.contains(uri);

  Future<void> launchItem(WatchNextItem item) async {
    await _fLauncherChannel.launchWatchNextItem(
      item.packageName,
      item.contentId,
      item.intentUri,
    );
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _settingsService?.removeListener(_onSettingsChanged);
    _refreshTimer?.cancel();
    _posterNotifyDebounce?.cancel();
    _client.close();
    _posterCache.clear();
    _pendingPosters.clear();
    super.dispose();
  }
}
