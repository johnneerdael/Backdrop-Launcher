import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/models/watch_next_item.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';

const _imageWidth = 540.0;
const _imageHeight = 300.0;
const _cardHeight = 364.0;
const _previewScale = 1 / 3;

class WatchNextCarousel extends StatefulWidget {
  final List<WatchNextItem> items;
  final FocusNode? focusNode;
  final bool autofocus;
  final double imageHeight;
  final VoidCallback? onUp;
  final VoidCallback? onDown;
  const WatchNextCarousel({
    super.key,
    required this.items,
    this.focusNode,
    this.autofocus = false,
    this.imageHeight = _imageHeight,
    this.onUp,
    this.onDown,
  });
  @override
  State<WatchNextCarousel> createState() => _WatchNextCarouselState();
}

class _WatchNextCarouselState extends State<WatchNextCarousel>
    with SingleTickerProviderStateMixin {
  final FocusNode _localFocus = FocusNode(debugLabel: 'Continue Watching');
  late final AnimationController _motion = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 320),
  )..addStatusListener(_status);
  int _index = 0, _to = 0, _requested = 0;
  bool _moving = false, _focused = false, _launching = false;

  @override
  void initState() {
    super.initState();
    if (widget.items.isNotEmpty) {
      _index = context.read<WatchNextService>().activeHeroIndex.clamp(
        0,
        widget.items.length - 1,
      );
      _to = _requested = _index;
    }
  }

  @override
  void didUpdateWidget(WatchNextCarousel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (listEquals(
      oldWidget.items.map((item) => item.id).toList(),
      widget.items.map((item) => item.id).toList(),
    ))
      return;
    final oldIndex = (_moving ? _to : _index)
        .clamp(0, math.max(0, oldWidget.items.length - 1))
        .toInt();
    final oldId = oldWidget.items.isEmpty ? null : oldWidget.items[oldIndex].id;
    _motion.stop();
    _moving = false;
    final preserved = widget.items.indexWhere((item) => item.id == oldId);
    _index = preserved >= 0
        ? preserved
        : _index.clamp(0, math.max(0, widget.items.length - 1));
    _to = _requested = _index;
    if (widget.items.isNotEmpty) {
      final id = widget.items[_index].id;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted &&
            widget.items.isNotEmpty &&
            widget.items[_index].id == id) {
          context.read<WatchNextService>().setActiveHeroIndex(_index);
        }
      });
    }
  }

  void _status(AnimationStatus status) {
    if (status != AnimationStatus.completed || !mounted || !_moving) return;
    setState(() {
      _index = _to;
      _moving = false;
    });
    if (_requested != _index) _start();
  }

  void _request(int target) {
    if (widget.items.isEmpty) return;
    _requested = target.clamp(0, widget.items.length - 1);
    if (!_moving && _requested != _index) _start();
  }

  void _start() {
    setState(() {
      _to = _index + (_requested > _index ? 1 : -1);
      _moving = true;
    });
    context.read<WatchNextService>().setActiveHeroIndex(_to);
    if (context.read<SettingsService>().appKeyClickEnabled)
      Feedback.forTap(context);
    _motion.forward(from: 0);
  }

  Future<void> _launch() async {
    if (_launching || widget.items.isEmpty) return;
    _launching = true;
    try {
      await context.read<WatchNextService>().launchItem(
        widget.items[_moving ? _to : _index],
      );
    } finally {
      _launching = false;
    }
  }

  KeyEventResult _key(FocusNode node, KeyEvent event) {
    final key = event.logicalKey;
    if (event is! KeyDownEvent && event is! KeyRepeatEvent)
      return KeyEventResult.ignored;
    if (key == LogicalKeyboardKey.arrowRight ||
        key == LogicalKeyboardKey.arrowLeft) {
      _request(_requested + (key == LogicalKeyboardKey.arrowRight ? 1 : -1));
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowUp) {
      widget.onUp?.call();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowDown) {
      widget.onDown?.call();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.select ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.gameButtonA) {
      if (event is KeyDownEvent) _launch();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) => Focus(
    focusNode: widget.focusNode ?? _localFocus,
    autofocus: widget.autofocus,
    onKeyEvent: _key,
    onFocusChange: (value) => setState(() => _focused = value),
    child: LayoutBuilder(
      builder: (context, bounds) {
        final scale = math.max(
          0.01,
          math.min(
            1.0,
            math.min(
              widget.imageHeight / _imageHeight,
              math.min(
                bounds.maxHeight / _cardHeight,
                (bounds.maxWidth - 160) / 720,
              ),
            ),
          ),
        );
        final width = _imageWidth * scale, height = _cardHeight * scale;
        const left = 32.0;
        final top = math.max(0.0, bounds.maxHeight - height);
        final previewX = left + width + math.max(96.0, width / 3);
        final previewY = top + height * (1 - _previewScale);
        return ClipRect(
          child: AnimatedBuilder(
            animation: _motion,
            builder: (context, _) {
              final poses =
                  <
                    ({
                      int index,
                      double x,
                      double y,
                      double scale,
                      double opacity,
                    })
                  >[];
              void add(
                int index,
                double x,
                double y,
                double size,
                double opacity,
              ) {
                if (index < 0 ||
                    index >= widget.items.length ||
                    opacity <= .001 ||
                    x >= bounds.maxWidth ||
                    x + width * size <= 0)
                  return;
                poses.add((
                  index: index,
                  x: x,
                  y: y,
                  scale: size,
                  opacity: opacity,
                ));
              }

              double lerp(double a, double b, double t) => a + (b - a) * t;
              if (!_moving) {
                add(_index, left, top, 1, 1);
                add(_index + 1, previewX, previewY, _previewScale, .1);
              } else {
                final t = Curves.easeOutCubic.transform(_motion.value);
                if (_to > _index) {
                  final exit = (t * 2).clamp(0.0, 1.0);
                  add(
                    _index,
                    lerp(left, -width - 24, exit),
                    top,
                    lerp(1, _previewScale, exit),
                    1 - exit,
                  );
                  add(
                    _to,
                    lerp(previewX, left, t),
                    lerp(previewY, top, t),
                    lerp(_previewScale, 1, t),
                    lerp(.1, 1, t),
                  );
                  if (t > .5) {
                    final reveal = (t - .5) * 2;
                    add(
                      _to + 1,
                      lerp(bounds.maxWidth + width / 3, previewX, reveal),
                      previewY,
                      _previewScale,
                      .1 * reveal,
                    );
                  }
                } else {
                  // Left uses translation only. Each full-size card exits before
                  // the same item's small preview is introduced offscreen.
                  final currentX = lerp(left, bounds.maxWidth + width, t);
                  final exitAt =
                      (bounds.maxWidth - left) /
                      (bounds.maxWidth + width - left);
                  add(_index, currentX, top, 1, 1);
                  if (t < .2) {
                    add(
                      _index + 1,
                      lerp(previewX, bounds.maxWidth + width / 3, t / .2),
                      previewY,
                      _previewScale,
                      .1,
                    );
                  } else {
                    final enter = (t - .2) / .8;
                    add(_to, lerp(-width - 24, left, enter), top, 1, 1);
                  }
                  if (t >= exitAt) {
                    final reveal = ((t - exitAt) / (1 - exitAt)).clamp(
                      0.0,
                      1.0,
                    );
                    add(
                      _index,
                      lerp(bounds.maxWidth + width / 3, previewX, reveal),
                      previewY,
                      _previewScale,
                      .1 * reveal,
                    );
                  }
                }
              }
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  for (final pose in poses)
                    Positioned(
                      key: ValueKey(
                        'watch_next_pose_${widget.items[pose.index].id}',
                      ),
                      left: pose.x,
                      top: pose.y,
                      width: _imageWidth,
                      height: _cardHeight,
                      child: Transform.scale(
                        scale: scale * pose.scale,
                        alignment: Alignment.topLeft,
                        child: Opacity(
                          key: ValueKey(
                            'watch_next_opacity_${widget.items[pose.index].id}',
                          ),
                          opacity: pose.opacity,
                          child: _ContinueWatchingCard(
                            item: widget.items[pose.index],
                            focused:
                                _focused &&
                                pose.index == (_moving ? _to : _index),
                            onTap: () {
                              if (pose.index == (_moving ? _to : _index)) {
                                _launch();
                              } else {
                                _request(pose.index);
                              }
                            },
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        );
      },
    ),
  );

  @override
  void dispose() {
    _motion.dispose();
    _localFocus.dispose();
    super.dispose();
  }
}

class _ContinueWatchingCard extends StatefulWidget {
  final WatchNextItem item;
  final bool focused;
  final VoidCallback onTap;
  const _ContinueWatchingCard({
    required this.item,
    required this.focused,
    required this.onTap,
  });
  @override
  State<_ContinueWatchingCard> createState() => _ContinueWatchingCardState();
}

class _ContinueWatchingCardState extends State<_ContinueWatchingCard> {
  void _load() {
    final service = context.read<WatchNextService>();
    service.ensurePosterLoaded(service.getPosterUriForItem(widget.item));
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(_ContinueWatchingCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    final service = context.read<WatchNextService>();
    if (service.getPosterUriForItem(oldWidget.item) !=
        service.getPosterUriForItem(widget.item))
      _load();
  }

  @override
  Widget build(BuildContext context) {
    final bytes = context.select<WatchNextService, Uint8List?>(
      (service) =>
          service.getCachedPoster(service.getPosterUriForItem(widget.item)),
    );
    final settings = context.read<SettingsService>();
    return Semantics(
      button: true,
      label: widget.item.title,
      child: GestureDetector(
        onTap: widget.onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: _imageWidth,
              height: _imageHeight,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    if (bytes != null)
                      Image.memory(
                        bytes,
                        key: ValueKey('watch_next_image_${widget.item.id}'),
                        fit: BoxFit.cover,
                        cacheWidth: _imageWidth.toInt(),
                        filterQuality: FilterQuality.low,
                      )
                    else
                      const ColoredBox(
                        color: Color(0x55171717),
                        child: Icon(
                          Icons.play_circle_outline,
                          size: 56,
                          color: Colors.white70,
                        ),
                      ),
                    if ((widget.item.progressPercent ?? 0) > 0)
                      Positioned(
                        bottom: 0,
                        left: 0,
                        right: 0,
                        child: LinearProgressIndicator(
                          value: widget.item.progressPercent! / 100,
                          minHeight: 4,
                          backgroundColor: Colors.white24,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                      ),
                    if (widget.focused && settings.showFocusBorders)
                      DecoratedBox(
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: Theme.of(context).colorScheme.primary,
                            width: settings.focusBorderThickness,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              widget.item.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 24,
                height: 1.1,
                fontWeight: FontWeight.w700,
                color: Colors.white,
                shadows: [
                  Shadow(
                    color: Colors.black87,
                    blurRadius: 6,
                    offset: Offset(0, 1),
                  ),
                ],
              ),
            ),
            Text(
              AppLocalizations.of(context)!.watchNextSectionTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 20,
                height: 1.1,
                color: Colors.white70,
                shadows: [
                  Shadow(
                    color: Colors.black87,
                    blurRadius: 6,
                    offset: Offset(0, 1),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
