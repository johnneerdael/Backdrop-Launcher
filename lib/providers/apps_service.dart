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

import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'package:collection/collection.dart' as collection;
import 'package:shared_preferences/shared_preferences.dart';

import 'package:drift/drift.dart';
import 'package:flauncher/database.dart';
import 'package:flauncher/bundled_artwork.dart';
import 'package:flauncher/app_image_type.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/local_media_cache.dart';
import 'package:flauncher/prepared_app_image.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flutter/foundation.dart' hide Category;
import 'package:flutter/widgets.dart';

import '../models/app.dart';
import '../models/category.dart';

class AppsService extends ChangeNotifier {
  final FLauncherChannel _fLauncherChannel;
  final FLauncherDatabase _database;
  final LocalMediaCache _imageCache;
  late final Future<void> ready;
  final _preparedImages = <String, PreparedAppImage>{};
  final _nativeArtworkVersions = <String, String>{};

  PreparedAppImage? cachedAppImage(String package) => _preparedImages[package];

  bool _initialized = false;
  int _layoutVersion = 0;

  List<Category> _launcherSections = List.empty(growable: true);
  Map<String, App> _applications = Map();
  Map<String, Uint8List> _iconCache = Map();
  Map<String, Uint8List> _bannerCache = Map();

  Map<int, Category> _categoriesById = Map();

  // Cached SharedPreferences instance to avoid repeated disk I/O
  SharedPreferences? _prefs;
  Future<SharedPreferences> get _prefsAsync async {
    _prefs ??= await SharedPreferences.getInstance();
    return _prefs!;
  }

  bool get initialized => _initialized;
  int get layoutVersion => _layoutVersion;

  @override
  void notifyListeners() {
    _layoutVersion++;
    super.notifyListeners();
  }

  String? _pendingReorderFocusPackage;
  int? _pendingReorderFocusCategoryId;
  String? get pendingReorderFocusPackage => _pendingReorderFocusPackage;
  int? get pendingReorderFocusCategoryId => _pendingReorderFocusCategoryId;
  void clearPendingReorderFocusPackage() {
    _pendingReorderFocusPackage = null;
    _pendingReorderFocusCategoryId = null;
  }

  void setPendingReorderFocus(String packageName, int categoryId) {
    _pendingReorderFocusPackage = packageName;
    _pendingReorderFocusCategoryId = categoryId;
  }

  final Set<String> _dirtyImagePackages = {};
  bool consumeDirtyImage(String packageName) =>
      _dirtyImagePackages.remove(packageName);

  List<App> get applications => UnmodifiableListView(
    _applications.values.sortedBy((application) => application.name),
  );

  App? getApplication(String? packageName) =>
      packageName != null ? _applications[packageName] : null;

  List<Category> get launcherSections => List.unmodifiable(_launcherSections);
  List<Category> get categories => _categoriesById.values
      .map((category) => category.unmodifiable())
      .toList(growable: false);
  List<Category> get categoriesWithApps => categories;

  AppsService(
    this._fLauncherChannel,
    this._database, {
    LocalMediaCache? imageCache,
  }) : _imageCache = imageCache ?? LocalMediaCache('app_artwork') {
    ready = _init();
  }

  static const rowsLayoutVersionKey = 'application_rows_layout_version';
  static const _nativeClassificationsKey =
      'application_rows_native_classifications';
  final Map<String, bool> _nativeClassifications = {};
  Set<String> _enumeratedPackages = {};

  Future<void> _persistNativeClassifications() async {
    await (await _prefsAsync).setString(
      _nativeClassificationsKey,
      jsonEncode(_nativeClassifications),
    );
  }

  Future<void> _init() async {
    final saved = (await _prefsAsync).getString(_nativeClassificationsKey);
    if (saved != null) {
      final decoded = jsonDecode(saved) as Map<String, dynamic>;
      _nativeClassifications.addAll(
        decoded.map((package, android) => MapEntry(package, android as bool)),
      );
    }
    final newlyInstalled = await _refreshState(shouldNotifyListeners: false);
    await _migrateApplicationRows();
    await _assignNewApplications(newlyInstalled);
    // Decode only the first visible row's initial viewport. Other cards load on demand.
    final firstRow = _launcherSections.where((row) => !row.hidden).firstOrNull;
    final initial = firstRow?.applications.take(7) ?? const <App>[];
    for (final app in initial) {
      await _prepareAppImage(app.packageName);
    }
    await _imageCache.prune({
      for (final app in _applications.values)
        for (final kind in ['banner', 'icon']) _imageKey(app.packageName, kind),
    });

    _fLauncherChannel.addAppsChangedListener((event) async {
      final action = event['action'];
      if (action == 'PACKAGE_REMOVED') {
        final packageName = event['packageName'] as String;
        await _database.deleteApps([packageName]);
        await _invalidatePackage(packageName);
        final removed = _applications.remove(packageName);
        for (final category in _launcherSections) {
          category.applications.removeWhere(
            (app) => app.packageName == packageName,
          );
        }
        removed?.categoryOrders.clear();
        _nativeClassifications.remove(packageName);
        _enumeratedPackages.remove(packageName);
        await _persistNativeClassifications();
        _nativeArtworkVersions.remove(packageName);
      } else if (action == 'PACKAGE_ADDED' ||
          action == 'PACKAGE_CHANGED' ||
          action == 'PACKAGES_AVAILABLE') {
        final infos = action == 'PACKAGES_AVAILABLE'
            ? event['activitiesInfo'] as List<dynamic>
            : [event['activityInfo']];
        final newlyInstalled = <String>{};
        for (final info in infos) {
          final packageName = info['packageName'] as String;
          final existing = _applications[packageName];
          _enumeratedPackages.add(packageName);
          final artworkVersion = info['artworkVersion']?.toString();
          if (artworkVersion != null)
            _nativeArtworkVersions[packageName] = artworkVersion;
          await _database.persistApps([_buildAppCompanion(info)]);
          final app = App(
            packageName: packageName,
            name: info['name'],
            version: info['version'] ?? '',
            hidden: existing?.hidden ?? false,
            action: info['action'],
          )..sideloaded = info['sideloaded'] ?? false;
          app.lastLaunchedAt = existing?.lastLaunchedAt;
          app.categoryOrders = Map.from(existing?.categoryOrders ?? {});
          _applications[packageName] = app;
          for (final category in _launcherSections) {
            category.applications.removeWhere(
              (member) => member.packageName == packageName,
            );
            if (!app.hidden && app.categoryOrders.containsKey(category.id)) {
              category.applications.add(app);
              sortCategory(category);
            }
          }
          if (existing == null) newlyInstalled.add(packageName);
          await _invalidatePackage(packageName);
        }
        await _assignNewApplications(newlyInstalled);
      }
      notifyListeners();
    });
    _initialized = true;
    notifyListeners();
  }

  Future<void> _invalidatePackage(String packageName) async {
    await Future.wait([
      _imageCache.remove(_imageKey(packageName, 'banner')),
      _imageCache.remove(_imageKey(packageName, 'icon')),
    ]);
    _iconCache.remove(packageName);
    _bannerCache.remove(packageName);
    _preparedImages.remove(packageName);
    _dirtyImagePackages.add(packageName);
  }

  AppsCompanion _buildAppCompanion(dynamic data) {
    String? version = data["version"];
    if (version == null) {
      version = "";
    }

    return AppsCompanion(
      packageName: Value(data["packageName"]),
      name: Value(data["name"]),
      version: Value(version),
      hidden: const Value.absent(),
    );
  }

  Future<void> _migrateApplicationRows() async {
    final prefs = await _prefsAsync;
    if ((prefs.getInt(rowsLayoutVersionKey) ?? 0) >= 1) return;

    await _database.transaction(() async {
      final rows = _launcherSections.toList();
      final memberships = await _database.getAppsCategories();
      Set<String> members(Category row) => memberships
          .where((entry) => entry.categoryId == row.id)
          .map((entry) => entry.appPackageName)
          .toSet();
      bool defaultOptions(Category row) =>
          row.source == CategorySource.custom &&
          row.sort == Category.Sort &&
          row.rowHeight == Category.RowHeight;
      final generatedFavorites = rows.where((row) {
        if (row.name != 'Favorites' || !defaultOptions(row)) return false;
        final packages = members(row);
        return packages.isEmpty ||
            (packages.length == 1 && packages.contains('app.wuplay.androidtv'));
      }).toList();
      final expected = _applications.values
          .where((app) => !app.hidden)
          .map((app) => app.packageName)
          .toSet();
      if (generatedFavorites.any(
        (row) => members(row).contains('app.wuplay.androidtv'),
      )) {
        expected.remove('app.wuplay.androidtv');
      }
      final generatedAllApps = rows
          .where(
            (row) =>
                row.name == 'All Apps' &&
                defaultOptions(row) &&
                (_applications.values.any((app) => !app.hidden) ||
                    members(row).isNotEmpty) &&
                members(row).containsAll(expected),
          )
          .toList();
      for (final row in [...generatedFavorites, ...generatedAllApps]) {
        await _database.deleteCategory(row.id);
        rows.remove(row);
      }

      final builtIns = <Category>[];
      for (final source in [
        CategorySource.tvApps,
        CategorySource.androidApps,
      ]) {
        var row = rows.firstWhereOrNull((row) => row.source == source);
        if (row == null) {
          final name = source == CategorySource.tvApps
              ? 'TV Apps'
              : 'Android Apps';
          final id = await _database.insertCategory(
            CategoriesCompanion.insert(
              name: name,
              order: builtIns.length,
              source: Value(source),
            ),
          );
          row = Category(id: id, name: name, source: source);
          final packages = _applications.values.where(
            (app) => app.sideloaded == (source == CategorySource.androidApps),
          );
          await _database.insertAppsCategories([
            for (final (index, app) in packages.indexed)
              AppsCategoriesCompanion.insert(
                categoryId: id,
                appPackageName: app.packageName,
                order: index,
              ),
          ]);
        }
        builtIns.add(row);
      }
      final ordered = [
        ...builtIns,
        ...rows.where((row) => !builtIns.contains(row)),
      ];
      await _database.updateCategories([
        for (final (index, row) in ordered.indexed)
          CategoriesCompanion(id: Value(row.id), order: Value(index)),
      ]);
    });
    // The marker follows the committed migration. Source-marked rows make a
    // retry safe if writing preferences is interrupted.
    await prefs.setInt(rowsLayoutVersionKey, 1);
    await _reloadStoredRows();
  }

  Future<Set<String>> _refreshState({bool shouldNotifyListeners = true}) async {
    final persistedApps = await _database.getApplications();
    final installed = await _fLauncherChannel.getApplications();
    final installedByPackage = {
      for (final info in installed) info['packageName'] as String: info,
    };
    _enumeratedPackages = installedByPackage.keys.toSet();
    final knownPackages = persistedApps.map((app) => app.packageName).toSet();
    final newlyInstalled = installedByPackage.keys.toSet().difference(
      knownPackages,
    );
    final removed = <String>[];
    for (final app in persistedApps) {
      if (!installedByPackage.containsKey(app.packageName) &&
          !await _fLauncherChannel.applicationExists(app.packageName)) {
        removed.add(app.packageName);
      }
    }
    await _database.transaction(() async {
      await _database.persistApps(installed.map(_buildAppCompanion));
      await _database.deleteApps(removed);
    });
    _applications = {
      for (final app in await _database.getApplications()) app.packageName: app,
    };
    for (final app in _applications.values) {
      final info = installedByPackage[app.packageName];
      app.sideloaded = info?['sideloaded'] ?? false;
      app.action = info?['action'];
      final artworkVersion = info?['artworkVersion']?.toString();
      if (artworkVersion != null)
        _nativeArtworkVersions[app.packageName] = artworkVersion;
    }
    await _reloadStoredRows();
    if (shouldNotifyListeners) notifyListeners();
    return newlyInstalled;
  }

  Future<void> _reloadStoredRows() async {
    final rows = await _database.getCategories();
    final memberships = await _database.getAppsCategories();
    _categoriesById = {for (final row in rows) row.id: row};
    _launcherSections = rows;
    for (final app in _applications.values) {
      app.categoryOrders.clear();
    }
    for (final entry in memberships) {
      final app = _applications[entry.appPackageName];
      final row = _categoriesById[entry.categoryId];
      if (app == null || row == null) continue;
      app.categoryOrders[row.id] = entry.order;
      if (!app.hidden) row.applications.add(app);
    }
    for (final row in rows) {
      sortCategory(row);
    }
  }

  Future<void> _assignNewApplications(Set<String> newlyInstalled) async {
    for (final app in _applications.values.where(
      (app) => _enumeratedPackages.contains(app.packageName),
    )) {
      final targetSource = app.sideloaded
          ? CategorySource.androidApps
          : CategorySource.tvApps;
      final target = _launcherSections.firstWhereOrNull(
        (row) => row.source == targetSource,
      );
      final previousNative = _nativeClassifications[app.packageName];
      // Membership may be chosen freely, including cross-kind selections. Move
      // it only when the native classification actually changed.
      final previous =
          previousNative != null && previousNative != app.sideloaded
          ? _launcherSections
                .where(
                  (row) =>
                      row.source ==
                          (previousNative
                              ? CategorySource.androidApps
                              : CategorySource.tvApps) &&
                      isAppInCategory(app, row),
                )
                .toList()
          : <Category>[];
      if (target != null &&
          (newlyInstalled.contains(app.packageName) || previous.isNotEmpty)) {
        await addToCategory(app, target, shouldNotifyListeners: false);
        for (final row in previous) {
          await removeFromCategory(app, row, shouldNotifyListeners: false);
        }
      }
      _nativeClassifications[app.packageName] = app.sideloaded;
    }
    _nativeClassifications.removeWhere(
      (package, _) => !_applications.containsKey(package),
    );
    await _persistNativeClassifications();
  }

  void sortCategory(Category category) {
    if (category.sort == CategorySort.alphabetical) {
      category.applications.sortBy((application) => application.name);
    } else if (category.sort == CategorySort.lastUsed) {
      category.applications.sort((a, b) {
        final aTime =
            a.lastLaunchedAt ?? DateTime.fromMillisecondsSinceEpoch(0);
        final bTime =
            b.lastLaunchedAt ?? DateTime.fromMillisecondsSinceEpoch(0);
        return bTime.compareTo(aTime); // Descending (newest first)
      });
    } else {
      category.applications.sortBy<num>(
        (application) => application.categoryOrders[category.id] ?? 0,
      );
    }
  }

  Future<Uint8List> getAppBanner(String packageName) async {
    if (_bannerCache.containsKey(packageName)) {
      return _bannerCache[packageName]!;
    }

    final custom = await (await _artwork).bytes(packageName);
    if (custom != null) {
      _bannerCache[packageName] = custom;
      return custom;
    }

    final bytes = await _nativeImage(
      packageName,
      'banner',
      () => _fLauncherChannel.getApplicationBanner(packageName),
    );
    _bannerCache[packageName] = bytes;
    return bytes;
  }

  Future<AppArtwork>? _artworkStore;
  Future<AppArtwork> get _artwork =>
      _artworkStore ??= _prefsAsync.then(AppArtwork.new);

  Future<void> setCustomAppBanner(String packageName, String assetPath) async {
    await (await _artwork).set(packageName, assetPath);
    await _invalidateArtwork(packageName);
  }

  Future<void> removeCustomAppBanner(String packageName) async {
    await (await _artwork).clear(packageName);
    await _invalidateArtwork(packageName);
  }

  Future<void> _invalidateArtwork(String packageName) async {
    _bannerCache.remove(packageName);
    _iconCache.remove(packageName);
    _preparedImages.remove(packageName);
    await _prepareAppImage(packageName);
    _dirtyImagePackages.add(packageName);
    notifyListeners();
  }

  Future<bool> hasCustomBanner(String packageName) async =>
      (await _artwork).has(packageName);

  Future<Uint8List> getAppIcon(String packageName) async {
    if (_iconCache.containsKey(packageName)) {
      return _iconCache[packageName]!;
    }
    final bytes =
        await (await _artwork).bytes(packageName) ??
        await _nativeImage(
          packageName,
          'icon',
          () => _fLauncherChannel.getApplicationIcon(packageName),
        );
    if (bytes.isNotEmpty) {
      _iconCache[packageName] = bytes;
    }
    return bytes;
  }

  String _imageKey(String package, String kind) =>
      '$package|${_applications[package]?.version ?? ''}${_nativeArtworkVersions.containsKey(package) ? '@${_nativeArtworkVersions[package]}' : ''}|$kind';

  Future<Uint8List> _nativeImage(
    String package,
    String kind,
    Future<Uint8List> Function() load,
  ) async {
    final key = _imageKey(package, kind);
    final cached = await _imageCache.readBytes(key);
    if (cached != null && (cached.isEmpty || await validImageBytes(cached)))
      return cached;
    if (cached != null) await _imageCache.remove(key);
    final bytes = await load();
    if (bytes.isNotEmpty && !await validImageBytes(bytes))
      throw const FormatException('Invalid native artwork');
    await _imageCache.writeBytes(key, bytes);
    return bytes;
  }

  Future<void> _prepareAppImage(String package) async {
    try {
      var bytes = await getAppBanner(package);
      var type = AppImageType.Banner;
      if (bytes.isEmpty) {
        type = AppImageType.Icon;
        bytes = await getAppIcon(package);
      }
      if (bytes.isEmpty) return;
      final image = ResizeImage(MemoryImage(bytes), width: 480);
      if (await prepareImage(image))
        _preparedImages[package] = PreparedAppImage(type, image);
    } catch (_) {
      // Artwork failure must leave the app launchable by its name.
    }
  }

  Future<void> launchApp(App app) async {
    app.lastLaunchedAt = DateTime.now();
    await _database.updateApp(
      app.packageName,
      AppsCompanion(lastLaunchedAt: Value(app.lastLaunchedAt)),
    );
    for (final row in _launcherSections.where(
      (row) => row.sort == CategorySort.lastUsed,
    )) {
      sortCategory(row);
    }
    notifyListeners();

    Future<void> future;
    if (app.action == null) {
      future = _fLauncherChannel.launchApp(app.packageName);
    } else {
      future = _fLauncherChannel.launchActivityFromAction(app.action!);
    }

    return future;
  }

  Future<void> openAppInfo(App app) =>
      _fLauncherChannel.openAppInfo(app.packageName);

  Future<void> uninstallApp(App app) =>
      _fLauncherChannel.uninstallApp(app.packageName);

  Future<void> openSettings() => _fLauncherChannel.openSettings();

  Future<bool> isDefaultLauncher() => _fLauncherChannel.isDefaultLauncher();

  Future<void> startAmbientMode() => _fLauncherChannel.startAmbientMode();

  bool isAppInCategory(App app, Category category) =>
      (_applications[app.packageName] ?? app).categoryOrders.containsKey(
        category.id,
      );

  Future<void> addToCategory(
    App app,
    Category category, {
    bool shouldNotifyListeners = true,
  }) async {
    if (isAppInCategory(app, category)) return;
    final index = await _database.nextAppCategoryOrder(category.id) ?? 0;
    await _database.insertAppsCategories([
      AppsCategoriesCompanion.insert(
        categoryId: category.id,
        appPackageName: app.packageName,
        order: index,
      ),
    ]);
    final actualApp = _applications[app.packageName] ?? app;
    // Another add can finish while the database write is in flight.
    if (actualApp.categoryOrders.containsKey(category.id)) return;
    actualApp.categoryOrders[category.id] = index;
    final row = _categoriesById[category.id];
    if (row != null && !actualApp.hidden) {
      row.applications.add(actualApp);
      sortCategory(row);
    }
    if (shouldNotifyListeners) notifyListeners();
  }

  Future<void> removeFromCategory(
    App app,
    Category category, {
    bool shouldNotifyListeners = true,
  }) async {
    await _database.deleteAppCategory(category.id, app.packageName);
    (_applications[app.packageName] ?? app).categoryOrders.remove(category.id);
    _categoriesById[category.id]?.applications.removeWhere(
      (member) => member.packageName == app.packageName,
    );
    if (shouldNotifyListeners) notifyListeners();
  }

  Future<void> setCategoryApplications(
    Category category,
    Set<String> packages,
  ) async {
    final row = _categoriesById[category.id];
    if (row == null) return;
    final selected = packages.intersection(_applications.keys.toSet());
    final ordered = _applications.values
        .where(
          (app) =>
              isAppInCategory(app, row) && selected.contains(app.packageName),
        )
        .sortedBy<num>((app) => app.categoryOrders[row.id] ?? 0);
    ordered.addAll(
      _applications.values
          .where(
            (app) =>
                selected.contains(app.packageName) &&
                !isAppInCategory(app, row),
          )
          .sortedBy((app) => app.name),
    );
    await _database.transaction(() async {
      await (_database.delete(
        _database.appsCategories,
      )..where((entry) => entry.categoryId.equals(row.id))).go();
      await _database.insertAppsCategories([
        for (final (index, app) in ordered.indexed)
          AppsCategoriesCompanion.insert(
            categoryId: row.id,
            appPackageName: app.packageName,
            order: index,
          ),
      ]);
    });
    for (final app in _applications.values) {
      app.categoryOrders.remove(row.id);
    }
    row.applications.clear();
    for (final (index, app) in ordered.indexed) {
      app.categoryOrders[row.id] = index;
      if (!app.hidden) row.applications.add(app);
    }
    sortCategory(row);
    notifyListeners();
  }

  Future<void> saveApplicationOrderInCategory(Category category) async {
    if (!_categoriesById.containsKey(category.id)) {
      return;
    }

    Category categoryFound = _categoriesById[category.id]!;
    List<App> applications = categoryFound.applications;
    List<AppsCategoriesCompanion> orderedAppCategories = [];

    for (int i = 0; i < applications.length; ++i) {
      orderedAppCategories.add(
        AppsCategoriesCompanion(
          categoryId: Value(categoryFound.id),
          appPackageName: Value(applications[i].packageName),
          order: Value(i),
        ),
      );
    }
    await _database.replaceAppsCategories(orderedAppCategories);
    for (final (index, app) in applications.indexed) {
      app.categoryOrders[categoryFound.id] = index;
    }
    notifyListeners();
  }

  Future<void> moveAppToAdjacentCategory(
    App app,
    Category currentCategory,
    AxisDirection direction,
  ) async {
    final currentIndex = _launcherSections.indexWhere(
      (row) => row.id == currentCategory.id,
    );
    if (currentIndex == -1) return;
    final nextIndex = currentIndex + (direction == AxisDirection.down ? 1 : -1);
    if (nextIndex < 0 || nextIndex >= _launcherSections.length) return;
    final target = _launcherSections[nextIndex];
    await removeFromCategory(
      app,
      currentCategory,
      shouldNotifyListeners: false,
    );
    await addToCategory(app, target, shouldNotifyListeners: false);
    setPendingReorderFocus(app.packageName, target.id);
    notifyListeners();
  }

  void reorderApplication(Category category, int oldIndex, int newIndex) {
    if (!_categoriesById.containsKey(category.id)) {
      return;
    }
    Category categoryFound = _categoriesById[category.id]!;
    List<App> applications = categoryFound.applications;
    App application = applications.removeAt(oldIndex);
    applications.insert(newIndex, application);

    notifyListeners();
  }

  Future<int> addCategory(
    String categoryName, {
    CategorySort sort = Category.Sort,
    int rowHeight = Category.RowHeight,
    CategorySource source = CategorySource.custom,
    bool shouldNotifyListeners = true,
  }) async {
    final order = _launcherSections.length;
    final id = await _database.insertCategory(
      CategoriesCompanion.insert(
        name: categoryName,
        order: order,
        sort: Value(sort),
        rowHeight: Value(rowHeight),
        source: Value(source),
      ),
    );
    final row = Category(
      id: id,
      name: categoryName,
      order: order,
      sort: sort,
      rowHeight: rowHeight,
      source: source,
    );
    _categoriesById[id] = row;
    _launcherSections.add(row);
    if (shouldNotifyListeners) notifyListeners();
    return id;
  }

  Future<void> updateCategory(
    int categoryId,
    String name,
    CategorySort sort,
    int rowHeight, {
    bool shouldNotifyListeners = true,
  }) async {
    final row = _categoriesById[categoryId];
    if (row == null) return;
    await _database.updateCategory(
      categoryId,
      CategoriesCompanion(
        name: Value(name),
        sort: Value(sort),
        rowHeight: Value(rowHeight),
      ),
    );
    row.name = name;
    row.sort = sort;
    row.rowHeight = rowHeight;
    sortCategory(row);
    if (shouldNotifyListeners) notifyListeners();
  }

  Future<void> renameCategory(Category category, String categoryName) async {
    final row = _categoriesById[category.id];
    if (row == null) return;
    await updateCategory(row.id, categoryName, row.sort, row.rowHeight);
  }

  Future<void> setCategoryHidden(Category category, bool hidden) async {
    final row = _categoriesById[category.id];
    if (row == null || row.hidden == hidden) return;
    await _database.updateCategory(
      row.id,
      CategoriesCompanion(hidden: Value(hidden)),
    );
    row.hidden = hidden;
    notifyListeners();
  }

  Future<void> deleteSection(int index) =>
      deleteCategory(_launcherSections[index]);

  Future<void> deleteCategory(Category category) async {
    final row = _categoriesById[category.id];
    if (row == null) return;
    await _database.deleteCategory(row.id);
    _categoriesById.remove(row.id);
    _launcherSections.removeWhere((member) => member.id == row.id);
    for (final app in _applications.values) {
      app.categoryOrders.remove(row.id);
    }
    await persistSectionsOrder();
    notifyListeners();
  }

  void moveSectionInMemory(int oldIndex, int newIndex) {
    if (oldIndex < 0 ||
        oldIndex >= _launcherSections.length ||
        newIndex < 0 ||
        newIndex >= _launcherSections.length)
      return;
    final row = _launcherSections.removeAt(oldIndex);
    _launcherSections.insert(newIndex, row);
    for (final (index, row) in _launcherSections.indexed) {
      row.order = index;
    }
    _categoriesById = {
      for (final category in _launcherSections) category.id: category,
    };
    notifyListeners();
  }

  Future<void> persistSectionsOrder() async {
    await _database.updateCategories([
      for (final (index, row) in _launcherSections.indexed)
        CategoriesCompanion(id: Value(row.id), order: Value(index)),
    ]);
    for (final (index, row) in _launcherSections.indexed) {
      row.order = index;
    }
  }

  Future<void> moveSection(int oldIndex, int newIndex) async {
    moveSectionInMemory(oldIndex, newIndex);
    await persistSectionsOrder();
  }

  Future<void> moveCategory(int oldIndex, int newIndex) =>
      moveSection(oldIndex, newIndex);

  Future<void> hideApplication(App application) async {
    await _database.updateApp(
      application.packageName,
      const AppsCompanion(hidden: Value(true)),
    );

    if (_applications.containsKey(application.packageName)) {
      App applicationFound = _applications[application.packageName]!;
      applicationFound.hidden = true;

      for (int categoryId in applicationFound.categoryOrders.keys) {
        if (_categoriesById.containsKey(categoryId)) {
          Category category = _categoriesById[categoryId]!;
          category.applications.removeWhere(
            (application0) =>
                application0.packageName == application.packageName,
          );
        }
      }

      notifyListeners();
    }
  }

  Future<void> showApplication(App application) async {
    await _database.updateApp(
      application.packageName,
      const AppsCompanion(hidden: Value(false)),
    );

    if (_applications.containsKey(application.packageName)) {
      App applicationFound = _applications[application.packageName]!;
      applicationFound.hidden = false;

      for (int categoryId in application.categoryOrders.keys) {
        if (_categoriesById.containsKey(categoryId)) {
          Category category = _categoriesById[categoryId]!;
          if (!category.applications.any(
            (member) => member.packageName == application.packageName,
          )) {
            category.applications.add(applicationFound);
          }
          sortCategory(category);
        }
      }

      notifyListeners();
    }
  }

  Future<void> unHideApplication(App application) =>
      showApplication(application);

  Future<void> setCategorySort(Category category, CategorySort sort) async {
    await _database.updateCategory(
      category.id,
      CategoriesCompanion(sort: Value(sort)),
    );
    if (_categoriesById.containsKey(category.id)) {
      Category categoryFound = _categoriesById[category.id]!;
      categoryFound.sort = sort;
      sortCategory(categoryFound);

      notifyListeners();
    }
  }

  Future<void> setCategoryRowHeight(Category category, int rowHeight) async {
    await _database.updateCategory(
      category.id,
      CategoriesCompanion(rowHeight: Value(rowHeight)),
    );

    if (_categoriesById.containsKey(category.id)) {
      Category categoryFound = _categoriesById[category.id]!;
      categoryFound.rowHeight = rowHeight;
      notifyListeners();
    }
  }
}
