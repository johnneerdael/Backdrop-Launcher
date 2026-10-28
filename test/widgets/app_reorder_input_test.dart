import 'dart:async';
import 'package:flauncher/database.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/launcher_state.dart';
import 'package:flauncher/widgets/category_row.dart';
import 'package:flauncher/widgets/home_rows_pager.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../application_rows_test_support.dart';
import '../helpers.dart';

class InputChannel extends RowsChannel {
  final launched = <String>[];
  InputChannel()
    : super([installedApp('a'), installedApp('b'), installedApp('c')]);
  @override
  Future<Uint8List> getApplicationIcon(String package) async => Uint8List(0);
  @override
  Future<Uint8List> getApplicationBanner(String package) async => Uint8List(0);
  @override
  Future<void> launchApp(String package) async {
    launched.add(package);
  }
}

void main() {
  Future<(AppsService, FLauncherDatabase, InputChannel)> pump(
    WidgetTester tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final db = FLauncherDatabase.inMemory();
    addTearDown(db.close);
    final channel = InputChannel();
    final service = (await tester.runAsync(
      () => initializedService(channel, db),
    ))!;
    addTearDown(service.dispose);
    final settings = SettingsService(await SharedPreferences.getInstance());
    addTearDown(settings.dispose);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<AppsService>.value(value: service),
          ChangeNotifierProvider<SettingsService>.value(value: settings),
          ChangeNotifierProvider(create: (_) => LauncherState()),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => PopScope(
              canPop: false,
              onPopInvokedWithResult: (_, _) =>
                  context.read<LauncherState>().handleBackNavigation(context),
              child: Scaffold(
                body: Consumer<AppsService>(
                  builder: (_, service, _) => SizedBox(
                    height: 300,
                    child: HomeRowsPager(
                      categories: service.categories
                          .where((row) => row.applications.isNotEmpty)
                          .toList(),
                      onUpToSettings: () {},
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return (service, db, channel);
  }

  Future<void> move(WidgetTester tester) async {
    await tester.longPress(findAppCard('a'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reorder'));
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.keyboard_arrow_left), findsOneWidget);
  }

  Future<void> saveWith(
    WidgetTester tester,
    AppsService service,
    Future<void> Function() action,
  ) async {
    await tester.runAsync(() async {
      final saved = Completer<void>();
      void listener() {
        if (!saved.isCompleted) saved.complete();
      }

      service.addListener(listener);
      try {
        await action();
        await saved.future.timeout(const Duration(seconds: 3));
      } finally {
        service.removeListener(listener);
      }
    });
  }

  testWidgets(
    'Pointer reorder moves the app that was held, not the previously focused app',
    (tester) async {
      final (service, db, channel) = await pump(tester);
      expect(isAppCardFocused(tester, 'a'), isTrue);
      await tester.longPress(findAppCard('b'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reorder'));
      await tester.pumpAndSettle();
      expect(isAppCardFocused(tester, 'b'), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
      await tester.pumpAndSettle();
      expect(
        service.categories.first.applications.map((app) => app.packageName),
        ['b', 'a', 'c'],
      );
      await saveWith(
        tester,
        service,
        () => tester.sendKeyEvent(LogicalKeyboardKey.select),
      );
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.keyboard_arrow_left), findsNothing);
      expect(channel.launched, isEmpty);
      await tester.runAsync(db.getAppsCategories);
    },
  );

  testWidgets(
    'Center finishes app movement on key down and persists the new position',
    (tester) async {
      final (service, db, channel) = await pump(tester);
      await move(tester);
      expect(
        service.categories.first.applications.map((app) => app.packageName),
        ['b', 'a', 'c'],
      );
      await saveWith(
        tester,
        service,
        () => tester.sendKeyDownEvent(LogicalKeyboardKey.select),
      );
      await tester.pump();
      expect(find.byIcon(Icons.keyboard_arrow_left), findsNothing);
      await tester.pump(const Duration(milliseconds: 600));
      await tester.sendKeyRepeatEvent(LogicalKeyboardKey.select);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.select);
      await tester.pumpAndSettle();
      final stored = await tester.runAsync(db.getAppsCategories);
      expect(
        stored!
            .where((entry) => entry.categoryId == service.categories.first.id)
            .toList()
          ..sort((a, b) => a.order.compareTo(b.order)),
        isNotEmpty,
      );
      final position = stored.singleWhere(
        (entry) =>
            entry.categoryId == service.categories.first.id &&
            entry.appPackageName == 'a',
      );
      expect(position.order, 1);
      expect(channel.launched, isEmpty);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pumpAndSettle();
      expect(
        service.categories.first.applications.map((app) => app.packageName),
        ['b', 'a', 'c'],
      );
    },
  );

  testWidgets('Android Back finishes app movement without leaving home', (
    tester,
  ) async {
    final (service, db, channel) = await pump(tester);
    await move(tester);
    await saveWith(tester, service, tester.binding.handlePopRoute);
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.keyboard_arrow_left), findsNothing);
    expect(find.byType(CategoryRow), findsOneWidget);
    expect(channel.launched, isEmpty);
    await tester.runAsync(db.getAppsCategories);
  });

  testWidgets(
    'Reopening a moved app menu keeps focus on that app and Back ends its move',
    (tester) async {
      final (service, db, _) = await pump(tester);
      await move(tester);
      await saveWith(
        tester,
        service,
        () => tester.sendKeyEvent(LogicalKeyboardKey.select),
      );
      await tester.pumpAndSettle();
      await tester.longPress(findAppCard('a'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reorder'));
      await tester.pumpAndSettle();
      expect(isAppCardFocused(tester, 'a'), isTrue);
      await saveWith(tester, service, tester.binding.handlePopRoute);
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.keyboard_arrow_left), findsNothing);
      await tester.runAsync(db.getAppsCategories);
    },
  );
}
