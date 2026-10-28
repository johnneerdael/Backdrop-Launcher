import 'dart:typed_data';
import 'dart:async';
import 'package:flauncher/database.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/widgets/settings/applications_panel_page.dart';
import 'package:flauncher/widgets/settings/application_row_editor_page.dart';
import 'package:flauncher/widgets/settings/interface_settings_page.dart';
import 'package:flauncher/widgets/settings/settings_panel.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../application_rows_test_support.dart';

class UiRowsChannel extends RowsChannel {
  UiRowsChannel()
    : super([
        installedApp('tv.one'),
        installedApp('android.one', android: true),
      ]);
  @override
  Future<Uint8List> getApplicationIcon(String packageName) async =>
      Uint8List(0);
}

void main() {
  Future<AppsService> service(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    final db = FLauncherDatabase.inMemory();
    addTearDown(db.close);
    final value = await tester.runAsync(
      () => initializedService(UiRowsChannel(), db),
    );
    addTearDown(value!.dispose);
    return value;
  }

  Future<void> pump(WidgetTester tester, AppsService value) async {
    await tester.pumpWidget(
      ChangeNotifierProvider<AppsService>.value(
        value: value,
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const SettingsPanel(
            initialRoute: ApplicationsPanelPage.routeName,
          ),
          onGenerateRoute: (settings) => MaterialPageRoute(
            builder: (_) => Scaffold(
              body: ApplicationRowEditorPage(
                categoryId: settings.arguments as int,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapAndSave(
    WidgetTester tester,
    AppsService value,
    Finder finder,
  ) async {
    await tester.runAsync(() async {
      final saved = Completer<void>();
      void listener() {
        if (!saved.isCompleted) saved.complete();
      }

      value.addListener(listener);
      try {
        await tester.tap(finder);
        await saved.future.timeout(const Duration(seconds: 3));
      } finally {
        value.removeListener(listener);
      }
    });
    await tester.pumpAndSettle();
  }

  testWidgets('Applications opens directly on rows and offers Add row', (
    tester,
  ) async {
    final value = await service(tester);
    await pump(tester, value);
    expect(find.text('TV Apps'), findsOneWidget);
    expect(find.text('Android Apps'), findsOneWidget);
    expect(find.text('Add row'), findsOneWidget);
    expect(find.text('Installed apps'), findsOneWidget);
    expect(find.text('Favorites'), findsNothing);
  });

  testWidgets(
    'Holding Select opens row controls instead of entering reorder mode',
    (tester) async {
      final value = await service(tester);
      await pump(tester, value);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.select);
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pumpAndSettle();
      await tester.sendKeyRepeatEvent(LogicalKeyboardKey.select);
      await tester.pumpAndSettle();
      await tester.sendKeyUpEvent(LogicalKeyboardKey.select);
      await tester.pumpAndSettle();
      expect(find.byType(ApplicationRowEditorPage), findsOneWidget);
      expect(find.text('Row name'), findsOneWidget);
      expect(find.byType(TextField), findsNothing);
    },
  );

  testWidgets('Right opens the row editor indicated by the chevron', (
    tester,
  ) async {
    final value = await service(tester);
    await pump(tester, value);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pumpAndSettle();
    expect(find.byType(ApplicationRowEditorPage), findsOneWidget);
    expect(find.text('Row name'), findsOneWidget);
  });

  testWidgets('Row visibility is editable without changing membership', (
    tester,
  ) async {
    final value = await service(tester);
    await pump(tester, value);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Show on home screen'));
    await tapAndSave(tester, value, find.text('Show on home screen'));
    expect(find.text('Move row down'), findsNothing);
    expect(find.text('Move row up'), findsNothing);
    expect(value.categories.map((row) => row.name), [
      'TV Apps',
      'Android Apps',
    ]);
    expect(value.categories.first.applications.single.packageName, 'tv.one');
    Navigator.of(tester.element(find.byType(ApplicationRowEditorPage))).pop();
    await tester.pumpAndSettle();
    expect(
      find.textContaining('1 apps · Hidden on home screen'),
      findsOneWidget,
    );
  });

  testWidgets(
    'Remote Right opens a row and Select toggles installed membership',
    (tester) async {
      final value = await service(tester);
      await pump(tester, value);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pumpAndSettle();
      expect(find.byType(ApplicationRowEditorPage), findsOneWidget);
      for (var i = 0; i < 5; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pumpAndSettle();
      }
      await tester.runAsync(() async {
        final saved = Completer<void>();
        void listener() {
          if (!saved.isCompleted) saved.complete();
        }

        value.addListener(listener);
        try {
          await tester.sendKeyEvent(LogicalKeyboardKey.enter);
          await saved.future.timeout(const Duration(seconds: 3));
        } finally {
          value.removeListener(listener);
        }
      });
      await tester.pumpAndSettle();
      expect(
        value.isAppInCategory(
          value.getApplication('tv.one')!,
          value.categories.first,
        ),
        isFalse,
      );
    },
  );

  testWidgets(
    'Select reorders in the row list and retains focus until Select finishes',
    (tester) async {
      final value = await service(tester);
      for (var i = 0; i < 8; i++) {
        await tester.runAsync(() => value.addCategory('Custom $i'));
      }
      await pump(tester, value);
      final rowId = value.categories.first.id;
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.byType(ApplicationRowEditorPage), findsNothing);
      Key? focusedTile() => FocusManager.instance.primaryFocus?.context
          ?.findAncestorWidgetOfExactType<FocusableSettingsTile>()
          ?.key;
      expect(focusedTile(), ValueKey(rowId));
      for (var i = 0; i < 9; i++) {
        await tester.runAsync(() async {
          final moved = Completer<void>();
          void listener() {
            if (!moved.isCompleted) moved.complete();
          }

          value.addListener(listener);
          try {
            await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
            await moved.future.timeout(const Duration(seconds: 3));
          } finally {
            value.removeListener(listener);
          }
        });
        await tester.pumpAndSettle();
        expect(focusedTile(), ValueKey(rowId));
        final viewport = tester.getRect(
          find
              .ancestor(
                of: find.text('TV Apps'),
                matching: find.byType(SingleChildScrollView),
              )
              .first,
        );
        final tile = tester.getRect(
          find.byWidgetPredicate(
            (widget) =>
                widget is FocusableSettingsTile &&
                widget.key == ValueKey(rowId),
          ),
        );
        expect(tile.top, greaterThanOrEqualTo(viewport.top));
        expect(tile.bottom, lessThanOrEqualTo(viewport.bottom));
      }
      expect(value.categories.last.name, 'TV Apps');
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pumpAndSettle();
      expect(focusedTile(), isNot(ValueKey(rowId)));
      expect(value.categories.last.name, 'TV Apps');
    },
  );

  testWidgets(
    'Pointer activation reorders the chosen row with subsequent remote input',
    (tester) async {
      final value = await service(tester);
      await pump(tester, value);
      await tester.tap(find.text('Android Apps'));
      await tester.pumpAndSettle();
      expect(
        FocusManager.instance.primaryFocus?.context
            ?.findAncestorWidgetOfExactType<FocusableSettingsTile>()
            ?.key,
        ValueKey(value.categories[1].id),
      );
      await tester.runAsync(() async {
        final moved = Completer<void>();
        void listener() {
          if (!moved.isCompleted) moved.complete();
        }

        value.addListener(listener);
        try {
          await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
          await moved.future.timeout(const Duration(seconds: 3));
        } finally {
          value.removeListener(listener);
        }
      });
      await tester.pumpAndSettle();
      expect(value.categories.map((row) => row.name), [
        'Android Apps',
        'TV Apps',
      ]);
      await tester.sendKeyEvent(LogicalKeyboardKey.select);
      await tester.pumpAndSettle();
      expect(find.text('Up/Down to move · Select to finish'), findsNothing);
    },
  );

  testWidgets('A row editor directly adds and removes installed apps', (
    tester,
  ) async {
    final value = await service(tester);
    await pump(tester, value);
    await tester.longPress(find.text('TV Apps'));
    await tester.pumpAndSettle();
    expect(find.byType(ApplicationRowEditorPage), findsOneWidget);
    await tester.ensureVisible(find.text('Delete row'));
    expect(find.text('Delete row'), findsOneWidget);
    final row = value.categories.first;
    final tv = value.applications.firstWhere((a) => a.packageName == 'tv.one');
    final android = value.applications.firstWhere(
      (a) => a.packageName == 'android.one',
    );
    await tester.ensureVisible(find.byKey(const ValueKey('row_member_tv.one')));
    await tapAndSave(
      tester,
      value,
      find.byKey(const ValueKey('row_member_tv.one')),
    );
    expect(value.isAppInCategory(tv, row), isFalse);
    await tester.ensureVisible(
      find.byKey(const ValueKey('row_member_android.one')),
    );
    await tapAndSave(
      tester,
      value,
      find.byKey(const ValueKey('row_member_android.one')),
    );
    expect(value.isAppInCategory(android, row), isTrue);
    expect(value.isAppInCategory(android, value.categories[1]), isTrue);
  });

  testWidgets(
    'Add row opens its membership editor and preserves default rows',
    (tester) async {
      final value = await service(tester);
      await pump(tester, value);
      await tester.runAsync(() => tester.tap(find.text('Add row')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Streaming');
      await tapAndSave(tester, value, find.text('Save'));
      expect(find.byType(ApplicationRowEditorPage), findsOneWidget);
      expect(
        FocusManager.instance.primaryFocus!.context!
            .findAncestorWidgetOfExactType<ApplicationRowEditorPage>(),
        isNotNull,
      );
      final row = value.categories.singleWhere((r) => r.name == 'Streaming');
      expect(row.applications, isEmpty);
      await tester.ensureVisible(
        find.byKey(const ValueKey('row_member_tv.one')),
      );
      await tapAndSave(
        tester,
        value,
        find.byKey(const ValueKey('row_member_tv.one')),
      );
      expect(
        value.categories
            .singleWhere((r) => r.id == row.id)
            .applications
            .single
            .packageName,
        'tv.one',
      );
      expect(value.categories.first.applications.single.packageName, 'tv.one');
    },
  );

  testWidgets('Interface no longer offers separate Sections', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: InterfaceSettingsPage()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Launcher sections'), findsNothing);
    expect(find.byIcon(Icons.category), findsNothing);
    expect(find.text('Watch Next'), findsOneWidget);
  });
}
