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

import 'package:flauncher/actions.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/custom_traversal_policy.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/launcher_state.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flauncher/widgets/home_rows_pager.dart';
import 'package:flauncher/widgets/home_backdrop.dart';
import 'package:flauncher/widgets/launcher_alternative_view.dart';
import 'package:flauncher/widgets/focus_aware_app_bar.dart';
import 'package:flauncher/widgets/watch_next_row.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

class FLauncher extends StatefulWidget {
  const FLauncher({super.key});

  @override
  State<FLauncher> createState() => _FLauncherState();
}

class _FLauncherState extends State<FLauncher> with WidgetsBindingObserver {
  final GlobalKey<FocusAwareAppBarState> _appBarKey = GlobalKey();
  final GlobalKey<HomeRowsPagerState> _appRowsKey = GlobalKey();
  final GlobalKey<WatchNextRowState> _watchNextKey = GlobalKey();
  final FocusNode _emptyRowsFocus = FocusNode(debugLabel: 'Add apps');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _emptyRowsFocus.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    context.read<WallpaperService>().setActive(
      state == AppLifecycleState.resumed,
    );
    context.read<WatchNextService>().setActive(
      state == AppLifecycleState.resumed,
    );
  }

  void _focusHomeFromToolbar() {
    final watchNext = _watchNextKey.currentState;
    final appRows = _appRowsKey.currentState;
    if (watchNext != null) {
      watchNext.requestFocus();
    } else if (appRows != null) {
      appRows.requestActiveRowFocus();
    } else {
      _emptyRowsFocus.requestFocus();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!context.select<AppsService, bool>((apps) => apps.initialized)) {
      return const ColoredBox(color: Colors.black);
    }
    return Actions(
      actions: <Type, Action<Intent>>{
        MoveFocusToSettingsIntent: CallbackAction<MoveFocusToSettingsIntent>(
          onInvoke: (_) => _appBarKey.currentState?.focusSettings(),
        ),
      },
      child: FocusTraversalGroup(
        policy: RowByRowTraversalPolicy(),
        child: Stack(
          children: [
            Consumer3<WallpaperService, SettingsService, WatchNextService>(
              builder: (_, wallpaper, settings, watchNext, __) {
                final hero =
                    settings.showWatchNextSection &&
                    settings.watchNextBigPictureMode;
                final uri = hero
                    ? watchNext.getPosterUriForItem(watchNext.activeHeroItem)
                    : null;
                final bytes = hero ? watchNext.getCachedPoster(uri) : null;
                return HomeBackdrop(
                  image: wallpaperImage(
                    bytes == null ? wallpaper.wallpaper : MemoryImage(bytes),
                  ),
                  revision: bytes == null
                      ? 'wallpaper_${wallpaper.wallpaperRevision}'
                      : 'hero_$uri',
                  scrim: bytes != null,
                  duration: Duration(milliseconds: bytes == null ? 1500 : 600),
                );
              },
            ),
            Consumer<LauncherState>(
              builder: (_, state, child) => Visibility(
                replacement: const Center(child: AlternativeLauncherView()),
                visible: state.launcherVisible,
                child: child!,
              ),
              child: Scaffold(
                backgroundColor: Colors.transparent,
                appBar: FocusAwareAppBar(
                  key: _appBarKey,
                  onDown: _focusHomeFromToolbar,
                ),
                // (initialized (data.$1), layoutVersion (data.$2), showAppNamesBelowIcons (data.$3))
                body:
                    Selector2<AppsService, SettingsService, (bool, int, bool)>(
                      selector: (_, appsService, settingsService) => (
                        appsService.initialized,
                        appsService.layoutVersion,
                        settingsService.showAppNamesBelowIcons,
                      ),
                      builder: (context, data, _) {
                        if (data.$1) {
                          return _homeRowsLayout(
                            context,
                            context.read<AppsService>(),
                            showAppNames: data.$3,
                          );
                        } else {
                          return _emptyState(context);
                        }
                      },
                    ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _homeRowsLayout(
    BuildContext context,
    AppsService service, {
    required bool showAppNames,
  }) {
    final rows =
        service.categories
            .where((row) => !row.hidden && row.applications.isNotEmpty)
            .toList()
          ..sort((a, b) => a.order.compareTo(b.order));
    final showWatchNext = context.select<SettingsService, bool>(
      (s) => s.showWatchNextSection,
    );
    final hasWatchNext =
        showWatchNext &&
        context.select<WatchNextService, bool>((s) => s.hasVisibleSection);
    if (!hasWatchNext && (_watchNextKey.currentState?.hasFocus ?? false)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || ModalRoute.of(context)?.isCurrent == false) return;
        if (rows.isNotEmpty) {
          _appRowsKey.currentState?.requestActiveRowFocus();
        } else {
          _appBarKey.currentState?.focusSettings();
        }
      });
    }
    if (rows.isEmpty && !hasWatchNext) {
      return Align(
        alignment: Alignment.bottomCenter,
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: TextButton.icon(
            focusNode: _emptyRowsFocus,
            autofocus: true,
            onPressed: () => _appBarKey.currentState?.focusSettings(),
            icon: const Icon(Icons.add),
            label: const Text('Add apps to a row in Settings'),
          ),
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => Stack(
        children: [
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: constraints.maxHeight * .42,
            child: SizedBox(
              key: const Key('home_sections_viewport'),
              child: rows.isEmpty
                  ? Center(
                      child: Focus(
                        onKeyEvent: (_, event) {
                          if (event is KeyDownEvent &&
                              event.logicalKey == LogicalKeyboardKey.arrowUp &&
                              hasWatchNext) {
                            _watchNextKey.currentState?.requestFocus();
                            return KeyEventResult.handled;
                          }
                          return KeyEventResult.ignored;
                        },
                        child: TextButton.icon(
                          focusNode: _emptyRowsFocus,
                          onPressed: () =>
                              _appBarKey.currentState?.focusSettings(),
                          icon: const Icon(Icons.add),
                          label: const Text('Add apps to a row in Settings'),
                        ),
                      ),
                    )
                  : HomeRowsPager(
                      key: _appRowsKey,
                      categories: rows,
                      onUpToSettings: () {
                        if (hasWatchNext) {
                          _watchNextKey.currentState?.requestFocus();
                        } else {
                          _appBarKey.currentState?.focusSettings();
                        }
                      },
                    ),
            ),
          ),
          if (hasWatchNext)
            Positioned(
              left: 0,
              right: 0,
              top: 16,
              bottom: constraints.maxHeight * .42 + 32,
              child: WatchNextRow(
                key: _watchNextKey,
                isFirstSection: rows.isEmpty,
                imageHeight: (constraints.maxHeight * .34).clamp(0, 300),
                onUp: () => _appBarKey.currentState?.focusSettings(),
                onDown: () {
                  if (rows.isNotEmpty) {
                    _appRowsKey.currentState?.requestActiveRowFocus();
                  } else {
                    _emptyRowsFocus.requestFocus();
                  }
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget _emptyState(BuildContext context) => const SizedBox.shrink();
}
