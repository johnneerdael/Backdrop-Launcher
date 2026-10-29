import 'dart:async';
import 'package:flutter/material.dart';

/// One source image plus direct shading. Retire outgoing decode keys after the fade.
class HomeBackdrop extends StatefulWidget {
  final ImageProvider image;
  final Object revision;
  final bool scrim;
  final Duration duration;

  const HomeBackdrop({
    super.key,
    required this.image,
    required this.revision,
    required this.scrim,
    required this.duration,
  });

  @override
  State<HomeBackdrop> createState() => _HomeBackdropState();
}

class _HomeBackdropState extends State<HomeBackdrop> {
  final _retired = <ImageProvider, Timer>{};

  @override
  void didUpdateWidget(HomeBackdrop oldWidget) {
    super.didUpdateWidget(oldWidget);
    _retired.remove(widget.image)?.cancel();
    if (oldWidget.image == widget.image) return;
    _retired.remove(oldWidget.image)?.cancel();
    _retired[oldWidget.image] = Timer(widget.duration, () {
      _retired.remove(oldWidget.image);
      if (mounted && oldWidget.image != widget.image)
        unawaited(oldWidget.image.evict());
    });
  }

  @override
  void dispose() {
    for (final entry in _retired.entries) {
      entry.value.cancel();
      unawaited(entry.key.evict());
    }
    _retired.clear();
    unawaited(widget.image.evict());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedSwitcher(
    duration: widget.duration,
    switchInCurve: Curves.easeOutCubic,
    switchOutCurve: Curves.easeInCubic,
    child: Stack(
      key: ValueKey(widget.revision),
      fit: StackFit.expand,
      children: [
        Image(image: widget.image, fit: BoxFit.cover, gaplessPlayback: true),
        if (widget.scrim)
          const IgnorePointer(
            child: CustomPaint(
              key: Key('cinematic_scrim'),
              painter: _CinematicScrimPainter(),
            ),
          ),
      ],
    ),
  );
}

class _CinematicScrimPainter extends CustomPainter {
  const _CinematicScrimPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas.drawRect(
      rect,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0x59000000),
            Colors.transparent,
            Color(0x80000000),
            Color(0xE0000000),
          ],
          stops: [0, .25, .65, 1],
        ).createShader(rect),
    );
    canvas.drawRect(
      rect,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [Color(0xA6000000), Color(0x40000000), Colors.transparent],
          stops: [0, .35, .7],
        ).createShader(rect),
    );
  }

  @override
  bool shouldRepaint(_CinematicScrimPainter oldDelegate) => false;
}
