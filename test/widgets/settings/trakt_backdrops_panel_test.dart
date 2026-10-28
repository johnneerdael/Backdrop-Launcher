import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/widgets/settings/trakt_catalogs_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'catalog selections remain available with an empty popular list response',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        'trakt_token':
            '{"access_token":"access","refresh_token":"refresh","expires_at":9999999999}',
      });
      final prefs = await SharedPreferences.getInstance();
      final trakt = TraktService(
        prefs,
        client: MockClient((_) async => http.Response('[]', 200)),
      );
      addTearDown(trakt.dispose);
      await tester.pumpWidget(
        ChangeNotifierProvider<TraktService>.value(
          value: trakt,
          child: const MaterialApp(home: Scaffold(body: TraktCatalogsPage())),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Anticipated movies'), findsOneWidget);
      // The TV remote must work immediately after opening the catalog page.
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(trakt.selectedCatalogs, isNot(contains('anticipated_movies')));
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(trakt.selectedCatalogs, isNot(contains('anticipated_shows')));
      await tester.tap(find.text('Fetch popular Trakt lists'));
      await tester.pumpAndSettle();
      expect(find.text('Trending shows'), findsOneWidget);
    },
  );
}
