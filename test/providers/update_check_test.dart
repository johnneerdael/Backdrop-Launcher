import 'dart:convert';
import 'dart:io';

import 'package:flauncher/providers/update_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';

Uri? _requestedReleaseUri;

// Exercise the real HTTP/JSON path while routing GitHub's URL to a local server.
class _LocalReleaseClient implements HttpClient {
  final HttpClient delegate;
  final Uri endpoint;
  _LocalReleaseClient(this.delegate, this.endpoint);
  @override
  Future<HttpClientRequest> getUrl(Uri url) {
    _requestedReleaseUri = url;
    return delegate.getUrl(endpoint);
  }

  @override
  void close({bool force = false}) => delegate.close(force: force);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  setUp(() {
    PackageInfo.setMockInitialValues(
      appName: 'Media Launcher',
      packageName: 'nl.neerdael.backdrop_launcher',
      version: '1.2.0',
      buildNumber: '4126',
      buildSignature: '',
    );
  });

  Future<UpdateResult> check(Object response, {int status = 200}) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final client = HttpClient();
    server.listen((request) async {
      request.response.statusCode = status;
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode(response));
      await request.response.close();
    });
    try {
      return await HttpOverrides.runZoned(
        () => UpdateService().checkForUpdate(),
        createHttpClient: (_) => _LocalReleaseClient(
          client,
          Uri.parse('http://127.0.0.1:${server.port}/releases'),
        ),
      );
    } finally {
      client.close(force: true);
      await server.close(force: true);
    }
  }

  Map<String, dynamic> release(
    String tag, {
    bool draft = false,
    bool prerelease = false,
    bool apk = true,
  }) => {
    'tag_name': tag,
    'draft': draft,
    'prerelease': prerelease,
    'body': 'A release',
    'assets': apk
        ? [
            {
              'name': 'media-launcher-$tag.apk',
              'browser_download_url': 'https://example.com/$tag.apk',
            },
          ]
        : [],
  };

  test(
    'Empty published releases is a successful check with no offered version',
    () async {
      final result = await check([]);
      expect(result.currentVersion, '1.2.0');
      expect(
        _requestedReleaseUri.toString(),
        'https://api.github.com/repos/johnneerdael/Backdrop-Launcher/releases',
      );
      expect(result.updateAvailable, isFalse);
      expect(result.latestVersion, isNull);
      expect(result.apkUrl, isNull);
    },
  );

  test(
    'Draft, prerelease and APK-less releases do not offer an update',
    () async {
      final result = await check([
        release('v2.0.0', draft: true),
        release('v1.4.0', prerelease: true),
        release('v1.3.0', apk: false),
      ]);
      expect(result.updateAvailable, isFalse);
      expect(result.latestVersion, isNull);
    },
  );

  test('A newer stable release offers its APK', () async {
    final result = await check([
      release('v2.0.0', prerelease: true),
      release('v1.3.0'),
    ]);
    expect(result.updateAvailable, isTrue);
    expect(result.latestVersion, '1.3.0');
    expect(result.apkUrl, 'https://example.com/v1.3.0.apk');
  });

  test('An older stable release does not offer a downgrade', () async {
    final result = await check([release('v1.1.0')]);
    expect(result.updateAvailable, isFalse);
    expect(result.latestVersion, '1.1.0');
  });

  test('HTTP errors remain check failures', () async {
    await expectLater(
      check({'message': 'API rate limit exceeded'}, status: 403),
      throwsA(isA<HttpException>()),
    );
  });

  test('A malformed API response remains a check failure', () async {
    await expectLater(
      check({'message': 'Unexpected response'}),
      throwsA(isA<FormatException>()),
    );
  });
}
