import 'dart:async';
import 'dart:convert';

import 'package:flauncher/providers/trakt_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

const config = TraktConfig(clientId: 'client', tmdbApiKey: 'tmdb');
final now = DateTime.utc(2026, 10, 26);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
  });

  TraktService service(Future<http.Response> Function(http.Request) handle) =>
      TraktService(
        prefs,
        config: config,
        client: MockClient(handle),
        now: () => now,
      );

  Future<void> connected({bool expired = false}) => prefs.setString(
    'trakt_token',
    jsonEncode({
      'access_token': 'access',
      'refresh_token': 'refresh',
      'expires_at': now.millisecondsSinceEpoch ~/ 1000 + (expired ? -1 : 7200),
    }),
  );

  test('device authorization persists tokens only after approval', () async {
    var polls = 0;
    final trakt = service((request) async {
      expect(request.headers['trakt-api-key'], 'client');
      if (request.url.path == '/oauth/device/code') {
        return http.Response(
          jsonEncode({
            'device_code': 'device',
            'user_code': 'ABCD1234',
            'verification_url': 'https://auth.trakt.tv/activate',
            'interval': 5,
            'expires_in': 600,
          }),
          200,
        );
      }
      expect(jsonDecode(request.body)['code'], 'device');
      expect(
        (jsonDecode(request.body) as Map).containsKey('client_secret'),
        isFalse,
      );
      return ++polls == 1
          ? http.Response('', 400)
          : http.Response(
              jsonEncode({
                'access_token': 'access',
                'refresh_token': 'refresh',
                'expires_in': 7200,
              }),
              200,
            );
    });
    addTearDown(trakt.dispose);
    final device = await trakt.startDeviceAuth();
    expect(device.userCode, 'ABCD1234');
    expect(
      device.activationUrl.toString(),
      'https://auth.trakt.tv/activate',
    );
    expect(await trakt.pollDeviceAuth(), TraktPollResult.pending);
    expect(trakt.isConnected, isFalse);
    expect(await trakt.pollDeviceAuth(), TraktPollResult.approved);
    expect(trakt.isConnected, isTrue);
    expect(prefs.getString('trakt_token'), contains('refresh'));
  });

  test('cancelled device login cannot save a late approval', () async {
    // A completer models an HTTP request still in flight when Back is pressed.
    final completer = Completer<http.Response>();
    final trakt = service((request) async {
      if (request.url.path.endsWith('/code')) return deviceResponse();
      return completer.future;
    });
    addTearDown(trakt.dispose);
    await trakt.startDeviceAuth();
    final poll = trakt.pollDeviceAuth();
    trakt.cancelDeviceAuth();
    completer.complete(
      http.Response(
        jsonEncode({
          'access_token': 'late',
          'refresh_token': 'late',
          'expires_in': 7200,
        }),
        200,
      ),
    );
    expect(await poll, TraktPollResult.cancelled);
    expect(trakt.isConnected, isFalse);
  });

  test('slow down increases the device polling interval', () async {
    final trakt = service(
      (request) async => request.url.path.endsWith('/code')
          ? deviceResponse()
          : http.Response('', 429),
    );
    addTearDown(trakt.dispose);
    await trakt.startDeviceAuth();
    expect(await trakt.pollDeviceAuth(), TraktPollResult.slowDown);
    expect(trakt.deviceAuthorization!.interval, const Duration(seconds: 10));
  });

  test(
    'expired token is refreshed once for concurrent catalog requests',
    () async {
      await connected(expired: true);
      var refreshes = 0;
      final trakt = service((request) async {
        if (request.url.path == '/oauth/token') {
          refreshes++;
          expect(jsonDecode(request.body)['grant_type'], 'refresh_token');
          expect(
            jsonDecode(request.body)['redirect_uri'],
            'https://github.com/johnneerdael/arclauncher',
          );
          expect(
            (jsonDecode(request.body) as Map).containsKey('client_secret'),
            isFalse,
          );
          return http.Response(
            jsonEncode({
              'access_token': 'new',
              'refresh_token': 'rotated',
              'expires_in': 7200,
            }),
            200,
          );
        }
        expect(request.headers['authorization'], 'Bearer new');
        return http.Response('[]', 200);
      });
      addTearDown(trakt.dispose);
      await Future.wait([trakt.fetchPopularLists(), trakt.fetchPopularLists()]);
      expect(refreshes, 1);
      expect(prefs.getString('trakt_token'), contains('rotated'));
    },
  );

  test('popular lists preserve owner and selection across disconnect and restart', () async {
    await connected();
    final trakt = service(
      (_) async => http.Response(
        jsonEncode([
          {
            'list': {
              'name': 'Cinema',
              'ids': {'slug': 'cinema'},
              'item_count': 50,
            },
            'user': {
              'ids': {'slug': 'john'},
            },
          },
        ]),
        200,
      ),
    );
    final lists = await trakt.fetchPopularLists();
    expect(lists.single.key, 'popular_list:john:cinema');
    await trakt.setSelectedCatalogs([lists.single.key]);
    await trakt.disconnect();
    expect(trakt.popularLists.single.key, lists.single.key);
    trakt.dispose();
    final restarted = service((_) async => http.Response('[]', 200));
    addTearDown(restarted.dispose);
    expect(restarted.selectedCatalogs, ['popular_list:john:cinema']);
    expect(restarted.popularLists.single.title, 'Cinema');
  });

  test('movie and show list items resolve raw TMDB backdrops', () async {
    await connected();
    final paths = <String>[];
    final trakt = service((request) async {
      paths.add(request.url.path);
      if (request.url.host == 'api.themoviedb.org') {
        return http.Response('{"backdrop_path":"/cinema.jpg"}', 200);
      }
      final movie = request.url.path.endsWith('/movies');
      return http.Response(
        jsonEncode([
          {
            movie ? 'movie' : 'show': {
              'title': 'Cinema',
              'ids': {'tmdb': movie ? 550 : 1396},
            },
          },
        ]),
        200,
      );
    });
    addTearDown(trakt.dispose);
    await trakt.setSelectedCatalogs(['popular_list:john:cinema']);
    final candidates = await trakt.fetchCandidates();
    expect(candidates.map((c) => c.mediaType).toSet(), {'movie', 'tv'});
    for (final candidate in candidates) {
      expect(
        (await trakt.backdropFor(candidate)).toString(),
        'https://image.tmdb.org/t/p/w1280/cinema.jpg',
      );
    }
    expect(paths, containsAll(['/3/movie/550', '/3/tv/1396']));
  });

  test('empty catalog selection makes no network requests', () async {
    await connected();
    final trakt = service(
      (_) async => throw StateError('Unexpected network request'),
    );
    addTearDown(trakt.dispose);
    await trakt.setSelectedCatalogs([]);
    expect(await trakt.fetchCandidates(), isEmpty);
  });
}

http.Response deviceResponse() => http.Response(
  jsonEncode({
    'device_code': 'device',
    'user_code': 'ABCD1234',
    'verification_url': 'https://auth.trakt.tv/activate',
    'interval': 5,
    'expires_in': 600,
  }),
  200,
);
