/*
 * FLauncher
 * Copyright (C) 2021 Étienne Fesser
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import 'package:flauncher/database.dart';
import 'package:flauncher/models/category.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../application_rows_test_support.dart';

class RecordingRowsChannel extends RowsChannel {
  final List<String> calls = [];
  RecordingRowsChannel(super.installed);

  @override
  Future<void> launchApp(String packageName) async =>
      calls.add('launch:$packageName');
  @override
  Future<void> launchActivityFromAction(String action) async =>
      calls.add('action:$action');
  @override
  Future<void> openAppInfo(String packageName) async =>
      calls.add('info:$packageName');
  @override
  Future<void> uninstallApp(String packageName) async =>
      calls.add('uninstall:$packageName');
  @override
  Future<void> openSettings() async => calls.add('settings');
  @override
  Future<bool> isDefaultLauncher() async => true;
  @override
  Future<void> startAmbientMode() async => calls.add('ambient');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FLauncherDatabase database;
  late RecordingRowsChannel channel;
  late AppsService service;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    database = FLauncherDatabase.inMemory();
    channel = RecordingRowsChannel([
      installedApp('tv.one'),
      installedApp('tv.two'),
    ]);
    service = await initializedService(channel, database);
  });

  tearDown(() async {
    service.dispose();
    await database.close();
  });

  test(
    'launch records last use persistently and sends the selected native action',
    () async {
      final first = service.getApplication('tv.one')!;
      final second = service.getApplication('tv.two')!;
      second.action = 'android.settings.TEST';
      await service.launchApp(first);
      await service.launchApp(second);
      expect(channel.calls, ['launch:tv.one', 'action:android.settings.TEST']);
      expect(first.lastLaunchedAt, isNotNull);
      expect(
        (await database.getApplications()).every(
          (app) => app.lastLaunchedAt != null,
        ),
        isTrue,
      );
    },
  );

  test('native app management remains available', () async {
    final app = service.applications.first;
    await service.openAppInfo(app);
    await service.uninstallApp(app);
    await service.openSettings();
    await service.startAmbientMode();
    expect(channel.calls, [
      'info:tv.one',
      'uninstall:tv.one',
      'settings',
      'ambient',
    ]);
    expect(await service.isDefaultLauncher(), isTrue);
  });

  test('row order is coherent in the notification before persistence', () {
    var observed = <String>[];
    service.addListener(() {
      final rows = service.categories.toList()
        ..sort((a, b) => a.order.compareTo(b.order));
      observed = rows.map((row) => row.name).toList();
    });
    service.moveSectionInMemory(0, 1);
    expect(observed, ['Android Apps', 'TV Apps']);
  });

  test('manual app order persists across restart', () async {
    final row = service.categories.first;
    service.reorderApplication(row, 1, 0);
    await service.saveApplicationOrderInCategory(row);
    service.dispose();
    service = await initializedService(channel, database);
    expect(
      service.categories.first.applications.map((app) => app.packageName),
      ['tv.two', 'tv.one'],
    );
  });

  test('saved manual app order survives switching sort modes', () async {
    final row = service.categories.first;
    service.reorderApplication(row, 1, 0);
    await service.saveApplicationOrderInCategory(row);
    await service.setCategorySort(row, CategorySort.alphabetical);
    await service.setCategorySort(row, CategorySort.manual);
    expect(
      service.categories.first.applications.map((app) => app.packageName),
      ['tv.two', 'tv.one'],
    );
  });

  test(
    'new rows append and reordering persists the visible row sequence',
    () async {
      final firstId = await service.addCategory('First custom');
      final secondId = await service.addCategory('Second custom');
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'First custom',
        'Second custom',
      ]);
      await service.moveCategory(3, 2);
      service.dispose();
      service = await initializedService(channel, database);
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'Second custom',
        'First custom',
      ]);
      expect(service.categories[2].id, secondId);
      expect(service.categories[3].id, firstId);
      expect(service.categories.map((row) => row.order), [0, 1, 2, 3]);
    },
  );

  test(
    'deleting a custom row retains apps and membership of other rows',
    () async {
      final id = await service.addCategory('Delete me');
      final custom = service.categories.last;
      await service.setCategoryApplications(custom, {'tv.one', 'tv.two'});
      await service.deleteCategory(custom);
      expect(service.categories, hasLength(2));
      expect(service.applications, hasLength(2));
      expect(service.categories.first.applications, hasLength(2));
      expect(
        service.applications.every(
          (app) => !app.categoryOrders.containsKey(id),
        ),
        isTrue,
      );
      expect(
        (await database.getAppsCategories()).every(
          (entry) => entry.categoryId != id,
        ),
        isTrue,
      );
    },
  );

  test(
    'sort and row size changes persist through service operations',
    () async {
      final id = await service.addCategory('Custom');
      final custom = service.categories.last;
      await service.setCategoryApplications(custom, {'tv.one', 'tv.two'});
      await service.renameCategory(custom, 'Renamed');
      await service.setCategorySort(custom, CategorySort.alphabetical);
      await service.setCategoryRowHeight(custom, 145);
      final stored = (await database.getCategories()).singleWhere(
        (row) => row.id == id,
      );
      expect(stored.name, 'Renamed');
      expect(stored.sort, CategorySort.alphabetical);
      expect(stored.rowHeight, 145);
      expect(stored.source, CategorySource.custom);
    },
  );

  test('last-used rows reorder when an app launches', () async {
    final row = service.categories.first;
    await service.setCategorySort(row, CategorySort.lastUsed);
    await service.launchApp(service.getApplication('tv.two')!);
    expect(
      service.categories.first.applications.map((app) => app.packageName),
      ['tv.two', 'tv.one'],
    );
  });

  test(
    'moving to an adjacent row preserves one membership and pending focus',
    () async {
      final app = service.getApplication('tv.one')!;
      final source = service.categories.first;
      await service.moveAppToAdjacentCategory(app, source, AxisDirection.down);
      expect(service.isAppInCategory(app, source), isFalse);
      expect(service.isAppInCategory(app, service.categories[1]), isTrue);
      expect(service.pendingReorderFocusPackage, 'tv.one');
      expect(service.pendingReorderFocusCategoryId, service.categories[1].id);
      service.clearPendingReorderFocusPackage();
      expect(service.pendingReorderFocusPackage, isNull);
    },
  );
}
