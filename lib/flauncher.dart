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
import 'dart:math';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flauncher/actions.dart';
import 'package:flauncher/image_preparation.dart';
import 'package:flauncher/custom_traversal_policy.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/launcher_state.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flauncher/widgets/home_rows_pager.dart';
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
  late final _HeroBackdropBaker _heroBackdropBaker = _HeroBackdropBaker();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _heroBackdropBaker.dispose();
    _emptyRowsFocus.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    context.read<WallpaperService>().setActive(
      state == AppLifecycleState.resumed,
    );
    if (state == AppLifecycleState.resumed) {
      context.read<WatchNextService>().refreshPermissionAndItems();
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
            RepaintBoundary(
              child: Consumer3<WallpaperService, SettingsService, WatchNextService>(
                builder: (_, wallpaperService, settingsService, watchNextService, __) {
                  final isBigPicture =
                      settingsService.showWatchNextSection &&
                      settingsService.watchNextBigPictureMode;
                  final activeItem = isBigPicture
                      ? watchNextService.activeHeroItem
                      : null;
                  final heroUri = isBigPicture
                      ? watchNextService.getPosterUriForItem(activeItem)
                      : null;
                  final heroBytes = isBigPicture
                      ? watchNextService.getCachedPoster(heroUri)
                      : null;
                  final hasHeroBackdrop = isBigPicture && heroBytes != null;
                  final screenSize = MediaQuery.sizeOf(context);
                  final dpr = MediaQuery.devicePixelRatioOf(context);
                  _heroBackdropBaker.update(
                    bytes: hasHeroBackdrop ? heroBytes : null,
                    size: screenSize,
                    dpr: dpr,
                  );
                  return Stack(
                    children: [
                      // Crossfade between wallpapers (each one has a revision-based key).
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 1500),
                        child: hasHeroBackdrop
                            ? const SizedBox.shrink(
                                key: ValueKey('occluded_wallpaper'),
                              )
                            : _wallpaper(context, wallpaperService),
                      ),
                      // Hero items crossfade once baked simultaneously with dock blur.
                      AnimatedSwitcher(
                        duration: _kHeroFadeDuration,
                        child: hasHeroBackdrop
                            ? RepaintBoundary(
                                key: const ValueKey('hero_bg'),
                                child: ListenableBuilder(
                                  listenable: _heroBackdropBaker,
                                  builder: (context, _) {
                                    final heroImage =
                                        _heroBackdropBaker.textures?.hero;
                                    return AnimatedSwitcher(
                                      duration: _kHeroFadeDuration,
                                      switchInCurve: Curves.easeOutCubic,
                                      switchOutCurve: Curves.easeInCubic,
                                      child: heroImage == null
                                          ? const SizedBox.expand(
                                              key: ValueKey('empty_hero'),
                                            )
                                          : RawImage(
                                              key: ObjectKey(heroImage),
                                              image: heroImage,
                                              width: screenSize.width,
                                              height: screenSize.height,
                                              fit: BoxFit.fill,
                                            ),
                                    );
                                  },
                                ),
                              )
                            : const SizedBox.shrink(
                                key: ValueKey('no_hero_bg'),
                              ),
                      ),
                    ],
                  );
                },
              ),
            ),
            Consumer<LauncherState>(
              builder: (_, state, child) => Visibility(
                replacement: const Center(child: AlternativeLauncherView()),
                visible: state.launcherVisible,
                child: child!,
              ),
              child: Scaffold(
                backgroundColor: Colors.transparent,
                appBar: FocusAwareAppBar(key: _appBarKey),
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

  Widget _wallpaper(BuildContext context, WallpaperService wallpaperService) {
    final screenSize = MediaQuery.sizeOf(context);
    return Image(
      image: wallpaperImage(wallpaperService.wallpaper),
      key: ValueKey("background_${wallpaperService.wallpaperRevision}"),
      fit: BoxFit.cover,
      height: screenSize.height,
      width: screenSize.width,
    );
  }

  Widget _emptyState(BuildContext context) => const SizedBox.shrink();
}

const _kHeroFadeDuration = Duration(milliseconds: 600);

class _HeroBackdropTextures {
  final ui.Image hero;
  final Uint8List bytes;
  bool _disposed = false;

  _HeroBackdropTextures({required this.hero, required this.bytes});

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    hero.dispose();
  }
}

class _HeroBackdropBaker extends ChangeNotifier {
  _HeroBackdropTextures? _current;
  final List<_HeroBackdropTextures> _disposedTextures = [];
  Uint8List? _requestedBytes;
  Size _requestedSize = Size.zero;
  double _requestedDpr = 1.0;
  Size _currentSize = Size.zero;
  double _currentDpr = 1.0;
  bool _baking = false;

  _HeroBackdropTextures? get textures => _current;

  void update({
    required Uint8List? bytes,
    required Size size,
    required double dpr,
  }) {
    if (identical(bytes, _requestedBytes) &&
        size == _requestedSize &&
        dpr == _requestedDpr) {
      return;
    }
    _requestedBytes = bytes;
    _requestedSize = size;
    _requestedDpr = dpr;

    if (bytes == null || size.width <= 0 || size.height <= 0) {
      if (_current != null) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (_requestedBytes == null && _current != null) {
            _scheduleDispose(_current!);
            _current = null;
            notifyListeners();
          }
        });
      }
      return;
    }

    if (!_baking) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!_baking && _requestedBytes != null) {
          _bake();
        }
      });
    }
  }

  void _scheduleDispose(_HeroBackdropTextures textures) {
    _disposedTextures.add(textures);
    Timer(const Duration(milliseconds: 1000), () {
      _disposedTextures.remove(textures);
      textures.dispose();
    });
  }

  Future<void> _bake() async {
    if (_baking) return;
    _baking = true;
    try {
      while (_requestedBytes != null &&
          (_current == null ||
              !identical(_requestedBytes, _current!.bytes) ||
              _requestedSize != _currentSize ||
              _requestedDpr != _currentDpr)) {
        final bytes = _requestedBytes!;
        final size = _requestedSize;
        final dpr = _requestedDpr;

        final pxSize = Size(
          (size.width * dpr).roundToDouble(),
          (size.height * dpr).roundToDouble(),
        );
        final codec = await ui.instantiateImageCodecWithSize(
          await ui.ImmutableBuffer.fromUint8List(bytes),
          getTargetSize: (w, h) {
            final scale = min(1.0, max(pxSize.width / w, pxSize.height / h));
            return ui.TargetImageSize(
              width: (w * scale).round(),
              height: (h * scale).round(),
            );
          },
        );
        final source = (await codec.getNextFrame()).image;
        codec.dispose();

        if (!identical(bytes, _requestedBytes)) {
          source.dispose();
          continue;
        }

        // 1. Full-screen hero background with cinematic scrim
        final heroRecorder = ui.PictureRecorder();
        final heroCanvas = Canvas(heroRecorder);
        paintImage(
          canvas: heroCanvas,
          rect: Offset.zero & pxSize,
          image: source,
          fit: BoxFit.cover,
        );
        const _CinematicScrimPainter().paint(heroCanvas, pxSize);
        final heroPicture = heroRecorder.endRecording();
        final heroImage = heroPicture.toImageSync(
          pxSize.width.toInt(),
          pxSize.height.toInt(),
        );
        heroPicture.dispose();

        source.dispose();

        if (!identical(bytes, _requestedBytes)) {
          heroImage.dispose();
          continue;
        }

        final old = _current;
        _current = _HeroBackdropTextures(hero: heroImage, bytes: bytes);
        _currentSize = size;
        _currentDpr = dpr;

        if (old != null) {
          _scheduleDispose(old);
        }

        notifyListeners();
      }
    } finally {
      _baking = false;
    }
  }

  @override
  void dispose() {
    for (final t in _disposedTextures) {
      t.dispose();
    }
    _disposedTextures.clear();
    _current?.dispose();
    _current = null;
    super.dispose();
  }
}

class _CinematicScrimPainter extends CustomPainter {
  const _CinematicScrimPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    // Top-to-bottom cinematic scrim
    final vPaint = Paint()
      ..shader = ui.Gradient.linear(
        Offset.zero,
        Offset(0, size.height),
        const [
          Color(0x59000000), // 0.35
          Colors.transparent,
          Color(0x80000000), // 0.50
          Color(0xE0000000), // 0.88
        ],
        const [0.0, 0.25, 0.65, 1.0],
      );
    canvas.drawRect(rect, vPaint);

    // Left-to-right cinematic scrim
    final hPaint = Paint()
      ..shader = ui.Gradient.linear(
        Offset(0, size.height / 2),
        Offset(size.width, size.height / 2),
        const [
          Color(0xA6000000), // 0.65
          Color(0x40000000), // 0.25
          Colors.transparent,
        ],
        const [0.0, 0.35, 0.7],
      );
    canvas.drawRect(rect, hPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
