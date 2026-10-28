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
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flauncher/bundled_artwork.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/trakt_service.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

/// One still-image source: bundled backdrops, or raw artwork selected via Trakt.
class WallpaperService extends ChangeNotifier {
  final SettingsService _settings;
  final TraktService? _trakt;
  final http.Client _client;
  final Future<Directory> Function() _cacheDirectory;
  final Random _random;
  late final Future<void> ready;
  Timer? _timer;
  Directory? _cache;
  late final LocalMediaCache _startupCache = LocalMediaCache(
    'wallpaper',
    directory: _cacheDirectory,
  );
  int _displayGeneration = 0;
  bool _disposed = false;
  bool _busy = false;
  bool _pending = false;
  bool _initialized = false;
  bool _active = true;
  int _generation = 0;
  int _revision = 0;
  int _bundledIndex = -1;
  ImageProvider _wallpaper = const AssetImage(BundledArtwork.defaultBackdrop);
  List<String> _bundled = [BundledArtwork.defaultBackdrop];
  List<TraktCandidate> _candidates = [];
  DateTime? _catalogLoaded;
  String? _lastCandidate;
  String? _error;
  String? _title;
  Object? _lastConfig;

  static const maxCachedBackdrops = 3;
  static const maxDownloadBytes = 8 * 1024 * 1024;
  ImageProvider get wallpaper => _wallpaper;
  int get wallpaperRevision => _revision;
  bool get isInitialized => _initialized;
  bool get isLoading => _busy;
  String? get error => _error;
  String? get title => _title;

  WallpaperService(
    this._settings, {
    TraktService? trakt,
    http.Client? client,
    Future<Directory> Function()? cacheDirectory,
    Random? random,
  }) : _trakt = trakt,
       _client = client ?? http.Client(),
       _cacheDirectory = cacheDirectory ?? getApplicationCacheDirectory,
       _random = random ?? Random() {
    _settings.addListener(_configure);
    _trakt?.addListener(_configure);
    ready = _init();
  }

  bool get _useTrakt =>
      _settings.traktBackdropsEnabled && (_trakt?.isConnected ?? false);
  Object get _config => (
    _useTrakt,
    _settings.backdropIntervalMinutes,
    _trakt?.selectedCatalogs.join('|') ?? '',
  );

  Future<void> _init() async {
    try {
      final images = await BundledArtwork.backdrops();
      if (images.isNotEmpty) _bundled = images;
    } catch (_) {
      /* The default asset is also available during startup. */
    }
    if (_disposed) return;
    _initialized = true;
    _lastConfig = _config;
    if (!await _restoreLastWallpaper() && !await _restoreExistingTraktCache())
      await _showBundled();
    _restartTimer();
  }

  Future<bool> _restoreLastWallpaper() async {
    final saved = await _startupCache.readJson('last');
    if (saved is! Map) return false;
    final asset = saved['asset'];
    if (asset is String && _bundled.contains(asset)) {
      _bundledIndex = _bundled.indexOf(asset);
      await _show(AssetImage(asset));
      return true;
    }
    final name = saved['file'];
    if (!_useTrakt ||
        name is! String ||
        !RegExp(r'^[a-zA-Z0-9_-]+\.jpg$').hasMatch(name))
      return false;
    try {
      final file = File('${(await _cacheDir()).path}/$name');
      if (!await file.exists()) return false;
      if (!await prepareImage(wallpaperImage(FileImage(file)))) return false;
      await _show(FileImage(file), title: saved['title'] as String?);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _restoreExistingTraktCache() async {
    if (!_useTrakt) return false;
    try {
      final files = await (await _cacheDir())
          .list()
          .where((file) => file is File && file.path.endsWith('.jpg'))
          .cast<File>()
          .toList();
      files.sort(
        (a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()),
      );
      for (final file in files.take(maxCachedBackdrops)) {
        if (await prepareImage(wallpaperImage(FileImage(file)))) {
          await _show(FileImage(file));
          return true;
        }
      }
    } catch (_) {}
    return false;
  }

  void _configure() {
    if (_disposed || !_initialized || _lastConfig == _config) return;
    final previous = _lastConfig as (bool, int, String);
    final current = _config as (bool, int, String);
    _lastConfig = _config;
    _restartTimer();
    if (previous.$1 == current.$1 && previous.$3 == current.$3) return;
    _generation++;
    _displayGeneration++;
    _candidates = [];
    _catalogLoaded = null;
    if (!_useTrakt) {
      _error = null;
      unawaited(_showBundled());
    } else {
      unawaited(nextBackdrop());
    }
  }

  void setActive(bool active) {
    if (_disposed || _active == active) return;
    _active = active;
    _restartTimer();
  }

  void _restartTimer() {
    _timer?.cancel();
    _timer = null;
    if (_active)
      _timer = Timer.periodic(
        Duration(minutes: _settings.backdropIntervalMinutes),
        (_) => unawaited(nextBackdrop()),
      );
  }

  Future<void> _show(ImageProvider image, {String? title}) async {
    if (_disposed) return;
    final displayGeneration = ++_displayGeneration;
    if (!await prepareImage(wallpaperImage(image)) ||
        _disposed ||
        displayGeneration != _displayGeneration)
      return;
    final old = _wallpaper;
    _wallpaper = image;
    _title = title;
    _revision++;
    if (old != image) unawaited(wallpaperImage(old).evict());
    notifyListeners();
    await _startupCache.writeJson('last', {
      if (image is AssetImage) 'asset': image.assetName,
      if (image is FileImage) 'file': image.file.uri.pathSegments.last,
      'title': title,
    });
  }

  Future<void> _showBundled() async {
    _bundledIndex = (_bundledIndex + 1) % _bundled.length;
    await _show(AssetImage(_bundled[_bundledIndex]));
  }

  Future<void> nextBackdrop() async {
    if (_disposed || !_initialized) return;
    if (_busy) {
      _pending = true;
      return;
    }
    if (!_useTrakt) {
      await _showBundled();
      return;
    }
    _busy = true;
    notifyListeners();
    final generation = _generation;
    try {
      if (_catalogLoaded == null ||
          DateTime.now().difference(_catalogLoaded!) >=
              const Duration(hours: 6)) {
        final candidates = await _trakt!.fetchCandidates();
        if (_disposed || generation != _generation) return;
        _candidates = candidates;
        _catalogLoaded = DateTime.now();
      }
      final candidates = List<TraktCandidate>.of(_candidates)..shuffle(_random);
      // Prefer a different title, but allow a list with just one usable backdrop.
      candidates.sort(
        (a, b) => (a.key == _lastCandidate ? 1 : 0).compareTo(
          b.key == _lastCandidate ? 1 : 0,
        ),
      );
      for (final candidate in candidates.take(10)) {
        final url = await _trakt!.backdropFor(candidate);
        if (_disposed || generation != _generation) return;
        if (url == null) continue;
        final file = await _download(
          url,
          candidate.key.replaceAll(':', '_'),
          generation,
        );
        if (file == null || _disposed || generation != _generation) return;
        _lastCandidate = candidate.key;
        _error = null;
        await _show(FileImage(file), title: candidate.title);
        await _pruneCache(file);
        return;
      }
      _error = candidates.isEmpty
          ? 'Select a catalog or list with movies or shows.'
          : 'No backdrops found in the selected catalogs.';
      await _showBundled();
    } catch (_) {
      if (!_disposed && generation == _generation) {
        _error = 'Trakt backdrops are unavailable. Showing included backdrops.';
        // Keep the last locally available wallpaper on a failed refresh.
        if (_wallpaper is! FileImage) await _showBundled();
      }
    } finally {
      _busy = false;
      if (!_disposed) {
        notifyListeners();
        if (_pending) {
          _pending = false;
          unawaited(nextBackdrop());
        }
      }
    }
  }

  Future<Directory> _cacheDir() async {
    return _cache ??= await Directory(
      '${(await _cacheDirectory()).path}/trakt_backdrops',
    ).create(recursive: true);
  }

  Future<File?> _download(Uri url, String key, int generation) async {
    final dir = await _cacheDir();
    if (_disposed || generation != _generation) return null;
    final file = File('${dir.path}/$key.jpg');
    if (await file.exists()) return file;
    final response = await _client
        .send(http.Request('GET', url))
        .timeout(const Duration(seconds: 20));
    if (response.statusCode != 200 ||
        !(response.headers['content-type'] ?? '').startsWith('image/') ||
        (response.contentLength ?? 0) > maxDownloadBytes) {
      await response.stream.listen((_) {}).cancel();
      throw const TraktException('Backdrop download failed.');
    }
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in response.stream.timeout(
      const Duration(seconds: 20),
    )) {
      if (_disposed || generation != _generation) return null;
      if (bytes.length + chunk.length > maxDownloadBytes)
        throw const TraktException('Backdrop is too large.');
      bytes.add(chunk);
    }
    if (bytes.isEmpty) throw const TraktException('Backdrop is empty.');
    // Validate before persisting or replacing the currently visible wallpaper.
    if (!await validImageBytes(bytes.toBytes()))
      throw const TraktException('Invalid backdrop image.');
    if (_disposed || generation != _generation) return null;
    await file.writeAsBytes(bytes.takeBytes(), flush: true);
    return file;
  }

  Future<void> _pruneCache(File current) async {
    final dir = _cache;
    if (dir == null || !await dir.exists()) return;
    final files = await dir
        .list()
        .where((f) => f is File)
        .cast<File>()
        .toList();
    files.sort((a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()));
    final others = files.where((f) => f.path != current.path).toList();
    for (final file in others.skip(maxCachedBackdrops - 1)) {
      await FileImage(file).evict();
      if (await file.exists()) await file.delete();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _displayGeneration++;
    _timer?.cancel();
    _settings.removeListener(_configure);
    _trakt?.removeListener(_configure);
    _client.close();
    super.dispose();
  }
}
