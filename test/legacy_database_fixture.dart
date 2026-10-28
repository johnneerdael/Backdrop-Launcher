import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flauncher/database.dart';

/// Opens a real historical schema so Drift must run the upgrade on first use.
FLauncherDatabase legacyDatabase({
  int version = 10,
  void Function(dynamic sqlite)? seed,
}) => FLauncherDatabase(
  DatabaseConnection(
    NativeDatabase.memory(
      setup: (sqlite) {
        sqlite.execute(
          'CREATE TABLE apps (package_name TEXT PRIMARY KEY, '
          'name TEXT NOT NULL, version TEXT NOT NULL'
          '${version >= 3 ? ', hidden INTEGER NOT NULL DEFAULT 0' : ''}'
          '${version >= 8 ? ', last_launched_at INTEGER' : ''})',
        );
        sqlite.execute(
          'CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, '
          'name TEXT NOT NULL, "order" INTEGER NOT NULL'
          '${version >= 11
              ? ', sort INTEGER NOT NULL DEFAULT 0, source INTEGER NOT NULL DEFAULT 0, row_height INTEGER NOT NULL DEFAULT 110'
              : version >= 4
              ? ', sort INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL DEFAULT 1, row_height INTEGER NOT NULL DEFAULT 110, columns_count INTEGER NOT NULL DEFAULT 6'
              : ''})',
        );
        sqlite.execute(
          'CREATE TABLE apps_categories (category_id INTEGER NOT NULL '
          'REFERENCES categories(id) ON DELETE CASCADE, app_package_name TEXT NOT NULL '
          'REFERENCES apps(package_name) ON DELETE CASCADE, "order" INTEGER NOT NULL, '
          'PRIMARY KEY (category_id, app_package_name))',
        );
        if (version >= 7 && version < 11) {
          sqlite.execute(
            'CREATE TABLE launcher_spacers (id INTEGER PRIMARY KEY AUTOINCREMENT, '
            'height INTEGER NOT NULL, "order" INTEGER NOT NULL)',
          );
        }
        seed?.call(sqlite);
        sqlite.execute('PRAGMA user_version = $version');
      },
    ),
  ),
);
