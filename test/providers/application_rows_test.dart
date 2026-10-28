import 'package:drift/drift.dart';
import 'package:flauncher/database.dart';
import 'package:flauncher/models/category.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../legacy_database_fixture.dart';
import '../application_rows_test_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'fresh database partitions native TV and Android apps into default rows',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final service = await initializedService(
        RowsChannel([
          installedApp('tv.one'),
          installedApp('android.one', android: true),
        ]),
        database,
      );
      addTearDown(service.dispose);

      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
      ]);
      expect(service.categories[0].applications.map((app) => app.packageName), [
        'tv.one',
      ]);
      expect(service.categories[1].applications.map((app) => app.packageName), [
        'android.one',
      ]);
    },
  );

  test('fresh database retains both empty built-in rows', () async {
    final database = FLauncherDatabase.inMemory();
    addTearDown(database.close);
    final service = await initializedService(RowsChannel([]), database);
    addTearDown(service.dispose);
    expect(service.categories.map((row) => row.name), [
      'TV Apps',
      'Android Apps',
    ]);
    expect(service.categories.map((row) => row.source), [
      CategorySource.tvApps,
      CategorySource.androidApps,
    ]);
  });

  test(
    'row visibility survives restart and showing restores unchanged membership',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      await first.setCategoryHidden(first.categories.first, true);
      expect(first.categories.first.hidden, isTrue);
      first.dispose();

      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      final row = restarted.categories.first;
      expect(row.hidden, isTrue);
      expect(row.applications.single.packageName, 'tv.one');
      expect(restarted.applications.single.hidden, isFalse);
      await restarted.setCategoryHidden(row, false);
      expect(restarted.categories.first.hidden, isFalse);
      expect((await database.getCategories()).first.hidden, isFalse);
      expect(
        restarted.categories.first.applications.single.packageName,
        'tv.one',
      );
    },
  );

  test(
    'removing a built-in member survives restart without repopulation',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      await first.removeFromCategory(
        first.applications.single,
        first.categories.first,
      );
      first.dispose();
      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      expect(restarted.categories.first.applications, isEmpty);
      expect(
        restarted.isAppInCategory(
          restarted.applications.single,
          restarted.categories.first,
        ),
        isFalse,
      );
      expect(await database.getAppsCategories(), isEmpty);
    },
  );

  test(
    'hidden membership survives reload and showing the app restores its row',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      await first.hideApplication(first.applications.single);
      first.dispose();
      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      final app = restarted.applications.single;
      final row = restarted.categories.first;
      expect(app.hidden, isTrue);
      expect(row.applications, isEmpty);
      expect(restarted.isAppInCategory(app, row), isTrue);
      await restarted.showApplication(app);
      expect(
        restarted.categories.first.applications.map((app) => app.packageName),
        ['tv.one'],
      );
      expect((await database.getApplications()).single.hidden, isFalse);
    },
  );

  test(
    'row membership replacement preserves global hidden state and other rows',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([
        installedApp('tv.one'),
        installedApp('tv.two'),
      ]);
      final service = await initializedService(channel, database);
      addTearDown(service.dispose);
      await service.hideApplication(service.getApplication('tv.two')!);
      final customId = await service.addCategory('My row');
      final custom = service.categories.singleWhere(
        (row) => row.id == customId,
      );
      await service.setCategoryApplications(custom, {
        'tv.one',
        'tv.two',
        'missing',
      });
      await service.addToCategory(service.getApplication('tv.one')!, custom);
      await service.addToCategory(service.getApplication('tv.one')!, custom);
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'My row',
      ]);
      expect(
        service.categories.last.applications.map((app) => app.packageName),
        ['tv.one'],
      );
      expect(
        service.isAppInCategory(service.getApplication('tv.two')!, custom),
        isTrue,
      );
      expect(service.getApplication('tv.two')!.hidden, isTrue);
      expect(
        (await database.getAppsCategories()).where(
          (entry) => entry.categoryId == customId,
        ),
        hasLength(2),
      );
      await service.setCategoryApplications(custom, {'tv.two'});
      expect(service.categories.last.applications, isEmpty);
      expect(
        service.categories.first.applications.map((app) => app.packageName),
        ['tv.one'],
      );
      expect(
        service.isAppInCategory(service.getApplication('tv.one')!, custom),
        isFalse,
      );
      expect(
        service.isAppInCategory(service.getApplication('tv.two')!, custom),
        isTrue,
      );
    },
  );

  test(
    'concurrent adds keep one app membership and one visible entry',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final service = await initializedService(
        RowsChannel([installedApp('tv.one')]),
        database,
      );
      addTearDown(service.dispose);
      await service.addCategory('Custom');
      final custom = service.categories.last;
      final app = service.applications.single;
      await Future.wait([
        service.addToCategory(app, custom),
        service.addToCategory(app, custom),
      ]);
      expect(
        service.categories.last.applications.map((app) => app.packageName),
        ['tv.one'],
      );
      expect(
        (await database.getAppsCategories()).where(
          (entry) => entry.categoryId == custom.id,
        ),
        hasLength(1),
      );
    },
  );

  test('row membership replacement rolls back on a database failure', () async {
    final database = FLauncherDatabase.inMemory();
    addTearDown(database.close);
    final service = await initializedService(
      RowsChannel([installedApp('tv.one'), installedApp('tv.two')]),
      database,
    );
    addTearDown(service.dispose);
    final row = service.categories.first;
    await database.customStatement(
      "CREATE TRIGGER reject_membership BEFORE INSERT ON apps_categories "
      "WHEN NEW.app_package_name = 'tv.two' BEGIN SELECT RAISE(ABORT, 'rejected'); END",
    );
    await expectLater(
      service.setCategoryApplications(row, {'tv.two'}),
      throwsA(anything),
    );
    expect(
      service.categories.first.applications.map((app) => app.packageName),
      ['tv.one', 'tv.two'],
    );
    expect(
      (await database.getAppsCategories()).map((entry) => entry.appPackageName),
      ['tv.one', 'tv.two'],
    );
  });

  test(
    'newly enumerated packages enter matching default without restoring old removals',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      await first.removeFromCategory(
        first.applications.single,
        first.categories.first,
      );
      first.dispose();
      channel.installed.add(installedApp('android.new', android: true));
      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      expect(restarted.categories.first.applications, isEmpty);
      expect(
        restarted.categories[1].applications.map((app) => app.packageName),
        ['android.new'],
      );
    },
  );

  test(
    'package broadcasts assign new apps once and preserve intentionally removed apps',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([]);
      final service = await initializedService(channel, database);
      addTearDown(service.dispose);
      await emitEvent(service, channel, {
        'action': 'PACKAGE_ADDED',
        'activityInfo': installedApp('tv.new'),
      });
      await service.removeFromCategory(
        service.getApplication('tv.new')!,
        service.categories.first,
      );
      await emitEvent(service, channel, {
        'action': 'PACKAGE_ADDED',
        'activityInfo': installedApp('tv.new'),
      });
      await emitEvent(service, channel, {
        'action': 'PACKAGES_AVAILABLE',
        'activitiesInfo': [
          installedApp('android.new', android: true),
          installedApp('tv.new'),
        ],
      });
      expect(service.categories.first.applications, isEmpty);
      expect(service.categories[1].applications.map((app) => app.packageName), [
        'android.new',
      ]);
      expect(await database.getAppsCategories(), hasLength(1));
    },
  );

  test(
    'changed native classification moves existing membership but respects removed membership',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([
        installedApp('tv.one'),
        installedApp('tv.removed'),
      ]);
      final service = await initializedService(channel, database);
      addTearDown(service.dispose);
      await service.removeFromCategory(
        service.getApplication('tv.removed')!,
        service.categories.first,
      );
      await emitEvent(service, channel, {
        'action': 'PACKAGE_CHANGED',
        'activityInfo': installedApp('tv.one', android: true),
      });
      await emitEvent(service, channel, {
        'action': 'PACKAGE_CHANGED',
        'activityInfo': installedApp('tv.removed', android: true),
      });
      expect(service.categories.first.applications, isEmpty);
      expect(service.categories[1].applications.map((app) => app.packageName), [
        'tv.one',
      ]);
    },
  );

  test(
    'built-in rows preserve explicitly selected apps of either native kind',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('android.one', android: true)]);
      final first = await initializedService(channel, database);
      await first.addToCategory(
        first.applications.single,
        first.categories.first,
      );
      first.dispose();
      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      expect(
        restarted.categories.first.applications.map((app) => app.packageName),
        ['android.one'],
      );
      expect(
        restarted.categories[1].applications.map((app) => app.packageName),
        ['android.one'],
      );
    },
  );

  test(
    'legacy generated rows split while custom order, hidden membership and preferences survive',
    () async {
      final database = legacyDatabase();
      addTearDown(database.close);
      final infos = [
        installedApp('tv.one'),
        installedApp('android.one', android: true),
        installedApp('hidden.one'),
        installedApp('app.wuplay.androidtv'),
      ];
      await database.persistApps(
        infos.map(
          (info) => AppsCompanion.insert(
            packageName: info['packageName'],
            name: info['name'],
            version: '1',
            hidden: Value(info['packageName'] == 'hidden.one'),
          ),
        ),
      );
      final allAppsId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'All Apps', order: 1),
      );
      final favoritesId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'Favorites', order: 0),
      );
      final customId = await database.insertCategory(
        CategoriesCompanion.insert(
          name: 'My grid',
          order: 2,
          sort: const Value(CategorySort.lastUsed),
          rowHeight: const Value(160),
        ),
      );
      final secondCustomId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'Second row', order: 3),
      );
      await database.insertAppsCategories([
        AppsCategoriesCompanion.insert(
          categoryId: allAppsId,
          appPackageName: 'tv.one',
          order: 0,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: allAppsId,
          appPackageName: 'android.one',
          order: 1,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: allAppsId,
          appPackageName: 'hidden.one',
          order: 2,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: favoritesId,
          appPackageName: 'app.wuplay.androidtv',
          order: 0,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: customId,
          appPackageName: 'hidden.one',
          order: 1,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: customId,
          appPackageName: 'tv.one',
          order: 2,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: secondCustomId,
          appPackageName: 'android.one',
          order: 0,
        ),
      ]);
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('trakt.access_token', 'preserved-token');
      await prefs.setString('custom_banner_tv.one', 'preserved-artwork');
      final service = await initializedService(RowsChannel(infos), database);
      addTearDown(service.dispose);
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'My grid',
        'Second row',
      ]);
      expect(service.categories[2].id, customId);
      expect(service.categories[2].sort, CategorySort.lastUsed);
      expect(service.categories[2].rowHeight, 160);
      expect(service.getApplication('hidden.one')!.hidden, isTrue);
      expect(
        service.isAppInCategory(
          service.getApplication('hidden.one')!,
          service.categories[2],
        ),
        isTrue,
      );
      expect(service.getApplication('hidden.one')!.categoryOrders[customId], 1);
      expect(service.getApplication('tv.one')!.categoryOrders[customId], 2);
      expect(
        service.categories.first.applications.map((app) => app.packageName),
        ['tv.one', 'app.wuplay.androidtv'],
      );
      expect(service.categories[1].applications.map((app) => app.packageName), [
        'android.one',
      ]);
      expect(prefs.getString('trakt.access_token'), 'preserved-token');
      expect(prefs.getString('custom_banner_tv.one'), 'preserved-artwork');
      expect(prefs.getInt(AppsService.rowsLayoutVersionKey), 1);
    },
  );

  test(
    'migration preserves partial All Apps and customized Favorites as custom rows',
    () async {
      final database = legacyDatabase();
      addTearDown(database.close);
      await database.persistApps([
        AppsCompanion.insert(
          packageName: 'tv.one',
          name: 'tv.one',
          version: '1',
        ),
        AppsCompanion.insert(
          packageName: 'android.one',
          name: 'android.one',
          version: '1',
        ),
      ]);
      final allId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'All Apps', order: 2),
      );
      final favId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'Favorites', order: 1),
      );
      final renamedId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'My apps', order: 0),
      );
      await database.insertAppsCategories([
        AppsCategoriesCompanion.insert(
          categoryId: allId,
          appPackageName: 'tv.one',
          order: 4,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: favId,
          appPackageName: 'android.one',
          order: 3,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: renamedId,
          appPackageName: 'tv.one',
          order: 2,
        ),
        AppsCategoriesCompanion.insert(
          categoryId: renamedId,
          appPackageName: 'android.one',
          order: 1,
        ),
      ]);
      final service = await initializedService(
        RowsChannel([
          installedApp('tv.one'),
          installedApp('android.one', android: true),
        ]),
        database,
      );
      addTearDown(service.dispose);
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'My apps',
        'Favorites',
        'All Apps',
      ]);
      expect(
        service.categories
            .skip(2)
            .every((row) => row.source == CategorySource.custom),
        isTrue,
      );
      expect(service.getApplication('tv.one')!.categoryOrders[allId], 4);
      expect(service.getApplication('android.one')!.categoryOrders[favId], 3);
      expect(service.categories[2].applications.map((app) => app.packageName), [
        'android.one',
        'tv.one',
      ]);
    },
  );

  test(
    'migration preserves an empty custom All Apps row when no apps were installed',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final customId = await database.insertCategory(
        CategoriesCompanion.insert(name: 'All Apps', order: 0),
      );
      final service = await initializedService(RowsChannel([]), database);
      addTearDown(service.dispose);
      expect(service.categories.map((row) => row.name), [
        'TV Apps',
        'Android Apps',
        'All Apps',
      ]);
      expect(service.categories.last.id, customId);
    },
  );

  test(
    'migration retry reuses source rows without restoring removed built-in members',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      final originalIds = first.categories.map((row) => row.id).toList();
      await first.removeFromCategory(
        first.applications.single,
        first.categories.first,
      );
      first.dispose();
      await (await SharedPreferences.getInstance()).remove(
        AppsService.rowsLayoutVersionKey,
      );
      final retried = await initializedService(channel, database);
      addTearDown(retried.dispose);
      expect(retried.categories.map((row) => row.id), originalIds);
      expect(retried.categories.first.applications, isEmpty);
      expect(await database.getAppsCategories(), isEmpty);
    },
  );

  test(
    'row settings survive restart and deleted default rows stay deleted',
    () async {
      final database = FLauncherDatabase.inMemory();
      addTearDown(database.close);
      final channel = RowsChannel([installedApp('tv.one')]);
      final first = await initializedService(channel, database);
      final id = await first.addCategory(
        'Custom',
        sort: CategorySort.alphabetical,
        rowHeight: 150,
      );
      await first.updateCategory(id, 'Renamed', CategorySort.lastUsed, 135);
      await first.deleteCategory(first.categories.first);
      first.dispose();
      final restarted = await initializedService(channel, database);
      addTearDown(restarted.dispose);
      expect(restarted.categories.map((row) => row.name), [
        'Android Apps',
        'Renamed',
      ]);
      expect(restarted.categories.last.sort, CategorySort.lastUsed);
      expect(restarted.categories.last.rowHeight, 135);
      expect(restarted.categories.last.source, CategorySource.custom);
      expect(restarted.categories.first.source, CategorySource.androidApps);
      expect(restarted.applications.single.packageName, 'tv.one');
      expect(restarted.applications.single.categoryOrders, isEmpty);
    },
  );
}
