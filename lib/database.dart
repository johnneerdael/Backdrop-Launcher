/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter/foundation.dart' as foundation;
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:flauncher/models/app.dart';
import 'package:flauncher/models/category.dart';

part 'database.drift.dart';

@UseRowClass(App)
class Apps extends Table {
  TextColumn get packageName => text()();

  TextColumn get name => text()();

  TextColumn get version => text()();

  BoolColumn get hidden => boolean().withDefault(const Constant(false))();

  DateTimeColumn get lastLaunchedAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {packageName};
}

@UseRowClass(Category)
class Categories extends Table {
  IntColumn get id => integer().autoIncrement()();

  TextColumn get name => text()();

  BoolColumn get hidden => boolean().withDefault(const Constant(false))();

  IntColumn get sort =>
      intEnum<CategorySort>().withDefault(Constant(Category.Sort.index))();

  IntColumn get source =>
      intEnum<CategorySource>().withDefault(const Constant(0))();

  IntColumn get rowHeight =>
      integer().withDefault(const Constant(Category.RowHeight))();

  IntColumn get order => integer()();
}

@DataClassName("AppCategory")
class AppsCategories extends Table {
  IntColumn get categoryId => integer().customConstraint(
    "REFERENCES categories(id) ON DELETE CASCADE",
  )();

  TextColumn get appPackageName => text().customConstraint(
    "REFERENCES apps(package_name) ON DELETE CASCADE",
  )();

  IntColumn get order => integer()();

  @override
  Set<Column> get primaryKey => {categoryId, appPackageName};
}

@DriftDatabase(tables: [Apps, Categories, AppsCategories])
class FLauncherDatabase extends _$FLauncherDatabase {
  late final bool wasCreated;

  FLauncherDatabase(DatabaseConnection super.databaseConnection);

  FLauncherDatabase.inMemory()
    : super(LazyDatabase(() => NativeDatabase.memory()));

  @override
  int get schemaVersion => 12;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await migrator.createAll();
    },
    onUpgrade: (migrator, from, to) async {
      if (from < 3) {
        await customStatement(
          'ALTER TABLE apps ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0',
        );
      }
      if (from < 4) {
        await customStatement(
          'ALTER TABLE categories ADD COLUMN sort INTEGER NOT NULL DEFAULT 0',
        );
        await customStatement(
          'ALTER TABLE categories ADD COLUMN type INTEGER NOT NULL DEFAULT 1',
        );
        await customStatement(
          'ALTER TABLE categories ADD COLUMN row_height INTEGER NOT NULL DEFAULT 110',
        );
        await customStatement(
          'ALTER TABLE categories ADD COLUMN columns_count INTEGER NOT NULL DEFAULT 6',
        );
      }
      if (from < 6 && to >= 6) {
        try {
          await customStatement("ALTER TABLE apps DROP COLUMN banner;");
        } catch (_) {}
        try {
          await customStatement("ALTER TABLE apps DROP COLUMN icon;");
        } catch (_) {}
      }
      if (from < 7 && to >= 7) {
        await customStatement(
          'CREATE TABLE IF NOT EXISTS launcher_spacers (id INTEGER PRIMARY KEY AUTOINCREMENT, height INTEGER NOT NULL, "order" INTEGER NOT NULL)',
        );
        try {
          await migrator.dropColumn(apps, "sideloaded");
        } catch (_) {}
      }
      if (from < 8 && to >= 8) {
        await migrator.addColumn(apps, apps.lastLaunchedAt);
      }
      // Row migration performs conservative detection. The old name-only
      // transforms could destroy customized memberships on a direct upgrade.
      if (from < 11) {
        // Rebuild rather than leaving obsolete layout columns on disk.
        await customStatement(
          'CREATE TABLE categories_rows ('
          'id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, '
          'sort INTEGER NOT NULL DEFAULT 0, source INTEGER NOT NULL DEFAULT 0, '
          'row_height INTEGER NOT NULL DEFAULT 110, "order" INTEGER NOT NULL)',
        );
        await customStatement(
          'INSERT INTO categories_rows (id, name, sort, row_height, "order") '
          'SELECT id, name, sort, row_height, "order" FROM categories',
        );
        await customStatement('DROP TABLE categories');
        await customStatement(
          'ALTER TABLE categories_rows RENAME TO categories',
        );
        await customStatement('DROP TABLE IF EXISTS launcher_spacers');
      }
      if (from < 12) {
        await migrator.addColumn(categories, categories.hidden);
      }
    },
    beforeOpen: (openingDetails) async {
      await customStatement('PRAGMA foreign_keys = ON;');
      await customStatement('PRAGMA journal_mode = WAL;');
      wasCreated = openingDetails.wasCreated;
    },
  );

  Future<void> persistApps(Iterable<AppsCompanion> applications) =>
      batch((batch) => batch.insertAllOnConflictUpdate(apps, applications));

  Future<void> updateApp(String packageName, AppsCompanion value) => (update(
    apps,
  )..where((tbl) => tbl.packageName.equals(packageName))).write(value);

  Future<void> deleteApps(List<String> packageNames) =>
      (delete(apps)..where((tbl) => tbl.packageName.isIn(packageNames))).go();

  Future<int> insertCategory(Insertable<Category> category) =>
      into(categories).insert(category);

  Future<void> deleteCategory(int id) =>
      (delete(categories)..where((tbl) => tbl.id.equals(id))).go();

  Future<void> updateCategories(List<CategoriesCompanion> values) =>
      batch((batch) {
        for (final value in values) {
          batch.update<$CategoriesTable, Category>(
            categories,
            value,
            where: (table) => (table.id.equals(value.id.value)),
          );
        }
      });

  Future<void> updateCategory(int id, CategoriesCompanion value) =>
      (update(categories)..where((tbl) => tbl.id.equals(id))).write(value);

  Future<void> deleteAppCategory(int categoryId, String packageName) =>
      (delete(appsCategories)..where(
            (tbl) =>
                tbl.categoryId.equals(categoryId) &
                tbl.appPackageName.equals(packageName),
          ))
          .go();

  Future<void> insertAppsCategories(List<AppsCategoriesCompanion> value) =>
      batch(
        (batch) => batch.insertAll(
          appsCategories,
          value,
          mode: InsertMode.insertOrIgnore,
        ),
      );

  Future<void> replaceAppsCategories(List<AppsCategoriesCompanion> value) =>
      batch((batch) => batch.replaceAll(appsCategories, value));

  Future<List<Category>> getCategories() {
    final query = select(categories);
    query.orderBy([(c) => OrderingTerm.asc(c.order)]);

    return query.get();
  }

  Future<List<AppCategory>> getAppsCategories() {
    final query = select(appsCategories);
    query.orderBy([(c) => OrderingTerm.asc(c.appPackageName)]);

    return query.get();
  }

  Future<List<App>> getApplications() {
    return select(apps).get();
  }

  Future<int?> nextAppCategoryOrder(int categoryId) async {
    final query = selectOnly(appsCategories);
    final maxExpression =
        coalesce([appsCategories.order.max(), const Constant(-1)]) +
        const Constant(1);
    query.addColumns([maxExpression]);
    query.where(appsCategories.categoryId.equals(categoryId));
    final result = await query.getSingle();
    return result.read(maxExpression);
  }
}

DatabaseConnection connect() => DatabaseConnection.delayed(() async {
  final dbFolder = await getApplicationDocumentsDirectory();
  final file = File(path.join(dbFolder.path, 'db.sqlite'));
  return DatabaseConnection(
    NativeDatabase(file, logStatements: foundation.kDebugMode),
  );
}());
