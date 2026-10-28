import 'package:flutter/material.dart';

import 'settings_theme.dart';

/// Native TV switch geometry, painted locally without a second focus target.
class SettingsSwitch extends StatelessWidget {
  final bool value;
  final Color accentColor;
  final Duration duration;

  const SettingsSwitch({
    super.key,
    required this.value,
    required this.accentColor,
    this.duration = SettingsStyle.focusDuration,
  });

  @override
  Widget build(BuildContext context) => Semantics(
    toggled: value,
    child: TweenAnimationBuilder<double>(
      tween: Tween(begin: value ? 1 : 0, end: value ? 1 : 0),
      duration: MediaQuery.maybeOf(context)?.disableAnimations == true
          ? Duration.zero
          : duration,
      curve: Curves.easeOutCubic,
      builder: (context, position, _) => SizedBox(
        width: 48,
        height: 27.25,
        child: CustomPaint(
          painter: _NativeSwitchPainter(position, accentColor),
        ),
      ),
    ),
  );
}

class _NativeSwitchPainter extends CustomPainter {
  final double position;
  final Color accent;
  const _NativeSwitchPainter(this.position, this.accent);

  @override
  void paint(Canvas canvas, Size size) {
    final t = position.clamp(0.0, 1.0);
    final track = Rect.fromLTWH(10.5, (size.height - 14) / 2, 27, 14);
    canvas.drawRRect(
      RRect.fromRectAndRadius(track, const Radius.circular(7)),
      Paint()
        ..color = Color.lerp(Colors.white, accent, t)!.withValues(alpha: .3),
    );
    final centre = Offset(13.625 + 20.75 * t, size.height / 2);
    canvas.drawCircle(
      centre.translate(0, .5),
      11,
      Paint()..color = const Color(0x20000000),
    );
    canvas.drawCircle(
      centre,
      10,
      Paint()..color = Color.lerp(const Color(0xFFB9B9B9), accent, t)!,
    );
  }

  @override
  bool shouldRepaint(covariant _NativeSwitchPainter oldDelegate) =>
      oldDelegate.position != position || oldDelegate.accent != accent;
}
