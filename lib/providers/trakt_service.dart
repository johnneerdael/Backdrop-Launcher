import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class TraktConfig {
  final String clientId;
  final String tmdbApiKey;
  final String redirectUri;

  const TraktConfig({
    this.clientId = const String.fromEnvironment('TRAKT_CLIENT_ID'),
    this.tmdbApiKey = const String.fromEnvironment('TMDB_API_KEY'),
    this.redirectUri = const String.fromEnvironment(
      'TRAKT_REDIRECT_URI',
      // Existing Trakt registration value; device login has no redirect.
      defaultValue: 'https://github.com/johnneerdael/arclauncher',
    ),
  });

  bool get isConfigured => clientId.isNotEmpty && tmdbApiKey.isNotEmpty;
}

enum TraktPollResult {
  pending,
  approved,
  slowDown,
  denied,
  expired,
  invalid,
  alreadyUsed,
  cancelled,
}

class TraktDeviceAuthorization {
  final String deviceCode;
  final String userCode;
  final Uri verificationUrl;
  final DateTime expiresAt;
  Duration interval;

  TraktDeviceAuthorization({
    required this.deviceCode,
    required this.userCode,
    required this.verificationUrl,
    required this.expiresAt,
    required this.interval,
  });

  Uri get activationUrl => verificationUrl;
}

class TraktListOption {
  final String owner;
  final String id;
  final String title;
  final int itemCount;

  const TraktListOption({
    required this.owner,
    required this.id,
    required this.title,
    this.itemCount = 0,
  });
  String get key => 'popular_list:$owner:$id';
  Map<String, dynamic> toJson() => {
    'owner': owner,
    'id': id,
    'title': title,
    'itemCount': itemCount,
  };
}

class TraktCandidate {
  final String title;
  final int tmdbId;
  final String mediaType;

  const TraktCandidate({
    required this.title,
    required this.tmdbId,
    required this.mediaType,
  });
  String get key => '$mediaType:$tmdbId';
}

class TraktException implements Exception {
  final String message;
  const TraktException(this.message);
  @override
  String toString() => message;
}

/// Device login and raw Trakt/TMDB catalog access. No wallpaper compositing.
class TraktService extends ChangeNotifier {
  static const builtInCatalogs = {
    'anticipated_movies': 'Anticipated movies',
    'anticipated_shows': 'Anticipated shows',
    'trending_movies': 'Trending movies',
    'trending_shows': 'Trending shows',
  };
  final SharedPreferences _prefs;
  final TraktConfig config;
  final http.Client _client;
  final DateTime Function() _now;
  bool _disposed = false;
  int _authGeneration = 0;
  bool _polling = false;
  Future<String?>? _refreshing;
  TraktDeviceAuthorization? _device;

  TraktService(
    this._prefs, {
    this.config = const TraktConfig(),
    http.Client? client,
    DateTime Function()? now,
  }) : _client = client ?? http.Client(),
       _now = now ?? DateTime.now;

  bool get isConfigured => config.isConfigured;
  bool get isConnected =>
      (_token['access_token'] as String?)?.isNotEmpty ?? false;
  TraktDeviceAuthorization? get deviceAuthorization => _device;
  List<String> get selectedCatalogs =>
      _prefs.getStringList('trakt_catalogs') ?? builtInCatalogs.keys.toList();

  Map<String, dynamic> get _token {
    try {
      return (jsonDecode(_prefs.getString('trakt_token') ?? '{}') as Map)
          .cast<String, dynamic>();
    } catch (_) {
      return {};
    }
  }

  List<TraktListOption> get popularLists {
    try {
      return (jsonDecode(_prefs.getString('trakt_lists') ?? '[]') as List)
          .map(
            (item) => TraktListOption(
              owner: item['owner'] as String,
              id: item['id'] as String,
              title: item['title'] as String,
              itemCount: item['itemCount'] as int? ?? 0,
            ),
          )
          .toList();
    } catch (_) {
      return [];
    }
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'User-Agent': 'MediaLauncher/1.2',
    'trakt-api-version': '2',
    'trakt-api-key': config.clientId,
  };

  Future<http.Response> _post(String path, Map<String, dynamic> body) => _client
      .post(
        Uri.https('api.trakt.tv', path),
        headers: _headers,
        body: jsonEncode(body),
      )
      .timeout(const Duration(seconds: 20));

  Future<TraktDeviceAuthorization> startDeviceAuth() async {
    if (!isConfigured)
      throw const TraktException('Trakt is not configured in this build.');
    cancelDeviceAuth();
    final generation = _authGeneration;
    final response = await _post('/oauth/device/code', {
      'client_id': config.clientId,
    });
    if (_disposed || generation != _authGeneration)
      throw const TraktException('Login cancelled.');
    if (response.statusCode != 200)
      throw TraktException(
        'Unable to connect to Trakt (HTTP ${response.statusCode}).',
      );
    final body = jsonDecode(response.body) as Map;
    final verification = Uri.parse(body['verification_url'] as String);
    if (verification.scheme != 'https' ||
        !{
          'trakt.tv',
          'auth.trakt.tv',
          'app.trakt.tv',
        }.contains(verification.host)) {
      throw const TraktException('Trakt returned an invalid activation URL.');
    }
    _device = TraktDeviceAuthorization(
      deviceCode: body['device_code'] as String,
      userCode: body['user_code'] as String,
      verificationUrl: verification,
      expiresAt: _now().add(Duration(seconds: body['expires_in'] as int)),
      interval: Duration(seconds: (body['interval'] as int).clamp(1, 300)),
    );
    _notify();
    return _device!;
  }

  void cancelDeviceAuth() {
    _authGeneration++;
    _device = null;
  }

  Future<TraktPollResult> pollDeviceAuth() async {
    final device = _device;
    if (_disposed || device == null) return TraktPollResult.cancelled;
    if (!_now().isBefore(device.expiresAt)) {
      cancelDeviceAuth();
      return TraktPollResult.expired;
    }
    if (_polling) return TraktPollResult.pending;
    final generation = _authGeneration;
    _polling = true;
    try {
      final response = await _post('/oauth/device/token', {
        'code': device.deviceCode,
        'client_id': config.clientId,
      });
      if (_disposed || generation != _authGeneration)
        return TraktPollResult.cancelled;
      final result = switch (response.statusCode) {
        200 => TraktPollResult.approved,
        400 => TraktPollResult.pending,
        404 => TraktPollResult.invalid,
        409 => TraktPollResult.alreadyUsed,
        410 => TraktPollResult.expired,
        418 => TraktPollResult.denied,
        429 => TraktPollResult.slowDown,
        _ => throw TraktException(
          'Trakt login failed (HTTP ${response.statusCode}).',
        ),
      };
      if (result == TraktPollResult.approved) {
        await _saveToken(jsonDecode(response.body) as Map);
        _device = null;
        _notify();
      } else if (result == TraktPollResult.slowDown) {
        final retryAfter =
            int.tryParse(response.headers['retry-after'] ?? '') ?? 0;
        device.interval = Duration(
          seconds: (device.interval.inSeconds + 5).clamp(retryAfter, 600),
        );
      } else if (result != TraktPollResult.pending) {
        cancelDeviceAuth();
      }
      return result;
    } finally {
      _polling = false;
    }
  }

  Future<void> _saveToken(Map body) async {
    if (body['access_token'] is! String ||
        body['refresh_token'] is! String ||
        body['expires_in'] is! int) {
      throw const TraktException('Trakt returned an invalid token.');
    }
    await _prefs.setString(
      'trakt_token',
      jsonEncode({
        'access_token': body['access_token'],
        'refresh_token': body['refresh_token'],
        'expires_at':
            _now().millisecondsSinceEpoch ~/ 1000 + (body['expires_in'] as int),
      }),
    );
  }

  Future<void> disconnect() async {
    cancelDeviceAuth();
    await _prefs.remove('trakt_token');
    _notify();
  }

  Future<String?> _authorization() async {
    final token = _token;
    final expires = token['expires_at'] as int? ?? 0;
    if (expires - 60 > _now().millisecondsSinceEpoch ~/ 1000 &&
        token['access_token'] is String) {
      return 'Bearer ${token['access_token']}';
    }
    if (token['refresh_token'] is! String) return null;
    if (_refreshing != null) return _refreshing;
    _refreshing = _refreshToken(token['refresh_token'] as String);
    try {
      return await _refreshing;
    } finally {
      _refreshing = null;
    }
  }

  Future<String?> _refreshToken(String refreshToken) async {
    final generation = _authGeneration;
    final response = await _post('/oauth/token', {
      'refresh_token': refreshToken,
      'client_id': config.clientId,
      'grant_type': 'refresh_token',
      'redirect_uri': config.redirectUri,
    });
    if (_disposed || generation != _authGeneration) return null;
    if ([400, 401, 403].contains(response.statusCode)) {
      await disconnect();
      return null;
    }
    if (response.statusCode != 200)
      throw TraktException(
        'Trakt token refresh failed (HTTP ${response.statusCode}).',
      );
    final body = jsonDecode(response.body) as Map;
    await _saveToken(body);
    return 'Bearer ${body['access_token']}';
  }

  Future<List<dynamic>> _get(List<String> segments, {int limit = 20}) async {
    final authorization = await _authorization();
    if (authorization == null)
      throw const TraktException('Connect your Trakt account first.');
    final response = await _client
        .get(
          Uri(
            scheme: 'https',
            host: 'api.trakt.tv',
            pathSegments: segments,
            queryParameters: {'limit': '$limit'},
          ),
          headers: {..._headers, 'Authorization': authorization},
        )
        .timeout(const Duration(seconds: 20));
    if (response.statusCode == 401) {
      await disconnect();
      throw const TraktException(
        'Your Trakt connection expired. Connect again.',
      );
    }
    if (response.statusCode != 200)
      throw TraktException(
        'Unable to load Trakt catalog (HTTP ${response.statusCode}).',
      );
    return jsonDecode(response.body) as List;
  }

  Future<List<TraktListOption>> fetchPopularLists() async {
    final generation = _authGeneration;
    final rows = await _get(['lists', 'popular'], limit: 30);
    final options = <TraktListOption>[];
    for (final row in rows.whereType<Map>()) {
      final list = row['list'] as Map?;
      final user = (row['user'] ?? list?['user']) as Map?;
      final owner = user?['ids']?['slug'] ?? user?['username'];
      final id = list?['ids']?['slug'] ?? list?['ids']?['trakt']?.toString();
      if (owner is! String || id is! String || list?['name'] is! String)
        continue;
      options.add(
        TraktListOption(
          owner: owner,
          id: id,
          title: list!['name'],
          itemCount: list['item_count'] as int? ?? 0,
        ),
      );
    }
    if (!_disposed && generation == _authGeneration) {
      // Selected lists stay available even if they drop out of the popular page.
      final merged = {
        for (final list in popularLists.where(
          (l) => selectedCatalogs.contains(l.key),
        ))
          list.key: list,
        for (final list in options) list.key: list,
      };
      await _prefs.setString(
        'trakt_lists',
        jsonEncode(merged.values.map((l) => l.toJson()).toList()),
      );
      _notify();
    }
    return options;
  }

  Future<void> setSelectedCatalogs(List<String> keys) async {
    final valid = keys
        .where(
          (key) =>
              builtInCatalogs.containsKey(key) ||
              (key.startsWith('popular_list:') && key.split(':').length == 3),
        )
        .toSet()
        .toList();
    await _prefs.setStringList('trakt_catalogs', valid);
    _notify();
  }

  Future<List<TraktCandidate>> fetchCandidates() async {
    final candidates = <String, TraktCandidate>{};
    for (final source in selectedCatalogs) {
      final parts = source.split(':');
      final isList = parts.length == 3 && parts.first == 'popular_list';
      for (final movie in [true, false]) {
        final type = movie ? 'movies' : 'shows';
        if (!isList && !source.endsWith('_$type')) continue;
        final path = isList
            ? ['users', parts[1], 'lists', parts[2], 'items', type]
            : [
                type,
                source.startsWith('anticipated_') ? 'anticipated' : 'trending',
              ];
        final rows = await _get(path);
        for (final row in rows.whereType<Map>()) {
          final media = row[movie ? 'movie' : 'show'] as Map?;
          final id = media?['ids']?['tmdb'];
          if (id is! int || media?['title'] is! String) continue;
          final candidate = TraktCandidate(
            title: media!['title'],
            tmdbId: id,
            mediaType: movie ? 'movie' : 'tv',
          );
          candidates[candidate.key] = candidate;
        }
      }
    }
    return candidates.values.toList();
  }

  Future<Uri?> backdropFor(TraktCandidate candidate) async {
    final response = await _client
        .get(
          Uri.https(
            'api.themoviedb.org',
            '/3/${candidate.mediaType}/${candidate.tmdbId}',
            {'api_key': config.tmdbApiKey},
          ),
        )
        .timeout(const Duration(seconds: 20));
    if (response.statusCode == 404) return null;
    if (response.statusCode != 200)
      throw TraktException(
        'Unable to load backdrop (HTTP ${response.statusCode}).',
      );
    final path = (jsonDecode(response.body) as Map)['backdrop_path'];
    if (path is! String || !path.startsWith('/')) return null;
    return Uri.https('image.tmdb.org', '/t/p/w1280$path');
  }

  @override
  void dispose() {
    _disposed = true;
    cancelDeviceAuth();
    _client.close();
    super.dispose();
  }
}
