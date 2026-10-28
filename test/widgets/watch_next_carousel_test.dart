import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flauncher/widgets/watch_next_row.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';
import '../mocks.mocks.dart';
import '../watch_next_test_support.dart';

void main() {
  Future<WatchNextTestFeed> pump(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final feed = WatchNextTestFeed();
    addTearDown(feed.dispose);
    final settings = MockSettingsService();
    when(settings.showWatchNextSection).thenReturn(true);
    when(settings.showFocusBorders).thenReturn(true);
    when(settings.focusBorderThickness).thenReturn(2);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<SettingsService>.value(value: settings),
          ChangeNotifierProvider<WatchNextService>.value(value: feed),
        ],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: 1100,
                height: 400,
                child: WatchNextRow(isFirstSection: true),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return feed;
  }

  Rect poster(WidgetTester tester, int id) {
    final box = tester.renderObject<RenderBox>(
      find.byKey(ValueKey('watch_next_image_$id')),
    );
    return MatrixUtils.transformRect(
      box.getTransformTo(null),
      Offset.zero & box.size,
    );
  }

  Future<void> press(WidgetTester tester, LogicalKeyboardKey key) async {
    await tester.sendKeyEvent(key);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'Only the primary item and a faint one-third preview are visible',
    (tester) async {
      await pump(tester);
      expect(find.text('Program 1'), findsOneWidget);
      expect(find.text('Program 2'), findsOneWidget);
      expect(find.text('Program 3'), findsNothing);
      final primary = poster(tester, 1), preview = poster(tester, 2);
      expect(primary.size, const Size(540, 300));
      expect(preview.width, closeTo(primary.width / 3, .01));
      expect(preview.height, closeTo(primary.height / 3, .01));
      expect(preview.left - primary.right, greaterThanOrEqualTo(96));
      expect(
        tester
            .widget<Opacity>(find.byKey(const ValueKey('watch_next_opacity_2')))
            .opacity,
        .1,
      );
      expect(tester.widget<Text>(find.text('Program 1')).style!.fontSize, 24);
    },
  );

  testWidgets(
    'Right glides and grows the preview with at most two visible items',
    (tester) async {
      await pump(tester);
      final initial = poster(tester, 2);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 80));
      final moving = poster(tester, 2);
      expect(moving.width, greaterThan(initial.width));
      expect(moving.width, lessThan(540));
      expect(moving.left, lessThan(initial.left));
      for (var i = 0; i < 12; i++) {
        expect(find.byType(Image).evaluate().length, lessThanOrEqualTo(2));
        await tester.pump(const Duration(milliseconds: 20));
      }
      await tester.pumpAndSettle();
      expect(poster(tester, 2).width, closeTo(540, .01));
      expect(find.text('Program 1'), findsNothing);
      expect(find.text('Program 3'), findsOneWidget);
    },
  );

  testWidgets('Left slides items without any intermediate resizing', (
    tester,
  ) async {
    await pump(tester);
    await press(tester, LogicalKeyboardKey.arrowRight);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
    await tester.pump();
    for (var i = 0; i < 17; i++) {
      final images = find.byType(Image).evaluate();
      expect(images.length, lessThanOrEqualTo(2));
      for (final element in images) {
        final box = element.renderObject! as RenderBox;
        final rect = MatrixUtils.transformRect(
          box.getTransformTo(null),
          Offset.zero & box.size,
        );
        expect(
          (rect.width - 540).abs() < .01 || (rect.width - 180).abs() < .01,
          isTrue,
          reason: 'Left must preserve full or preview width, got ${rect.width}',
        );
      }
      await tester.pump(const Duration(milliseconds: 20));
    }
    await tester.pumpAndSettle();
    expect(poster(tester, 1).width, closeTo(540, .01));
    expect(poster(tester, 2).width, closeTo(180, .01));
  });

  testWidgets(
    'Rapid reversal settles on the requested item and Select launches immediately',
    (tester) async {
      final feed = await pump(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump(const Duration(milliseconds: 40));
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pumpAndSettle();
      expect(poster(tester, 2).width, closeTo(540, .01));
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      expect(feed.launched?.id, 2);
    },
  );

  testWidgets(
    'Feed replacement preserves the selected ID and removal stays navigable',
    (tester) async {
      final feed = await pump(tester);
      await press(tester, LogicalKeyboardKey.arrowRight);
      final selected = feed.values[1];
      feed.replace([selected, feed.values[0]]);
      await tester.pumpAndSettle();
      expect(poster(tester, 2).width, closeTo(540, .01));
      feed.replace([feed.values[1]]);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      expect(feed.launched?.id, 1);
    },
  );

  testWidgets('One item and both navigation boundaries do not wrap', (
    tester,
  ) async {
    final feed = await pump(tester);
    feed.replace([feed.values.first]);
    await tester.pumpAndSettle();
    await press(tester, LogicalKeyboardKey.arrowLeft);
    await press(tester, LogicalKeyboardKey.arrowRight);
    expect(find.byType(Image), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    expect(feed.launched?.id, 1);
    feed.replace([]);
    await tester.pumpAndSettle();
    expect(find.byType(Image), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'Feed replacement during a glide retains the incoming item identity',
    (tester) async {
      final feed = await pump(tester);
      final incoming = feed.values[1];
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump(const Duration(milliseconds: 70));
      feed.replace([feed.values[4], incoming, feed.values[0]]);
      await tester.pumpAndSettle();
      expect(poster(tester, incoming.id).width, closeTo(540, .01));
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      expect(feed.launched?.id, incoming.id);
    },
  );
}
