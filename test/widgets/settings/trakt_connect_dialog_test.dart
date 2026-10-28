import 'dart:convert';

import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/widgets/settings/trakt_connect_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  for (final approve in [false, true]) {
    testWidgets(
      'device dialog shows the QR and stops polling after ${approve ? 'approval' : 'cancel'}',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        final prefs = await SharedPreferences.getInstance();
        var polls = 0;
        final trakt = TraktService(
          prefs,
          config: const TraktConfig(clientId: 'client', tmdbApiKey: 'tmdb'),
          client: MockClient((request) async {
            if (request.url.path.endsWith('/code')) {
              expect(jsonDecode(request.body), {'client_id': 'client'});
              return http.Response(
                jsonEncode({
                  'device_code': 'device',
                  'user_code': 'TV123456',
                  'verification_url': 'https://auth.trakt.tv/activate',
                  'expires_in': 600,
                  'interval': 5,
                }),
                200,
              );
            }
            polls++;
            expect(jsonDecode(request.body), {
              'client_id': 'client',
              'code': 'device',
            });
            return approve
                ? http.Response(
                    jsonEncode({
                      'access_token': 'access',
                      'refresh_token': 'refresh',
                      'expires_in': 7200,
                    }),
                    200,
                  )
                : http.Response('', 400);
          }),
        );
        addTearDown(trakt.dispose);
        bool? result;
        await tester.pumpWidget(
          ChangeNotifierProvider<TraktService>.value(
            value: trakt,
            child: MaterialApp(
              home: Builder(
                builder: (context) => Scaffold(
                  body: TextButton(
                    onPressed: () async {
                      result = await showDialog<bool>(
                        context: context,
                        builder: (_) => const TraktConnectDialog(),
                      );
                    },
                    child: const Text('Connect'),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Connect'));
        await tester.pumpAndSettle();
        expect(find.text('TV123456'), findsOneWidget);
        expect(find.byType(QrImageView), findsOneWidget);
        expect(
          trakt.deviceAuthorization!.activationUrl.toString(),
          'https://auth.trakt.tv/activate',
        );
        expect(polls, 0);
        if (approve) {
          await tester.pump(const Duration(seconds: 5));
          await tester.pumpAndSettle();
          expect(polls, 1);
          expect(result, isTrue);
          expect(trakt.isConnected, isTrue);
        } else {
          await tester.pump(const Duration(seconds: 5));
          await tester.pump();
          expect(polls, 1);
          expect(result, isNull);
          await tester.tap(find.text('Cancel'));
          await tester.pumpAndSettle();
          expect(result, isFalse);
          expect(trakt.isConnected, isFalse);
        }
        await tester.pump(const Duration(seconds: 30));
        expect(polls, 1);
        expect(find.byType(TraktConnectDialog), findsNothing);
        expect(trakt.deviceAuthorization, isNull);
      },
    );
  }
}
