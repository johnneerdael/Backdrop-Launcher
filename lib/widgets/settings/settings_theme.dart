import 'package:flutter/material.dart';

/// Values measured from the TV's native Leanback settings resources and views.
abstract final class SettingsStyle {
  static const Duration focusDuration = Duration(milliseconds: 90);
  static const double panelWidth = 360;
  static const double panelRadius = 0;
  static const double cardRadius = 0;
  static const double focusScale = 1;
  static const EdgeInsets cardMargin = EdgeInsets.zero;
  static const EdgeInsetsDirectional cardPadding = EdgeInsetsDirectional.only(
    start: 24,
    end: 56,
  );
  static const EdgeInsetsDirectional headerPadding =
      EdgeInsetsDirectional.fromSTEB(24, 27, 56, 0);
  static const Color panelColor = Color(0xFF202124);
  static const Color headerColor = Color(0xFF1B1C1E);
  static const Color accent = Color(0xFF51AAFF);
  static const Color primaryText = Color(0xFFEEEEEE);
  static const Color secondaryText = Color(0xB3EEEEEE);
  static const Color disabledText = Color(0x4DEEEEEE);
  static const Color controlNormal = Color(0xB3FFFFFF);
  static const Color rowRestFill = Colors.transparent;
  static const Color rowFocusFill = Color(0xFF2E2F32);
  static const Color scrimColor = Color(0xCC000000);
  static const double iconSize = 24;

  static const TextStyle headerTextStyle = TextStyle(
    fontFamily: 'sans-serif',
    fontSize: 20,
    fontWeight: FontWeight.w500,
    height: 26.75 / 20,
    color: Colors.white,
  );
  static const TextStyle primaryTextStyle = TextStyle(
    fontFamily: 'sans-serif',
    fontSize: 14,
    fontWeight: FontWeight.w400,
    height: 19 / 14,
    color: primaryText,
  );
  static const TextStyle summaryTextStyle = TextStyle(
    fontFamily: 'sans-serif-condensed',
    fontSize: 12,
    fontWeight: FontWeight.w400,
    height: 16.25 / 12,
    color: secondaryText,
  );
  static const TextStyle sectionTextStyle = TextStyle(
    fontFamily: 'sans-serif',
    fontSize: 12,
    fontWeight: FontWeight.w500,
    height: 16.25 / 12,
    color: accent,
  );

  static ThemeData nativeTheme(ThemeData base) {
    final text = base.textTheme
        .apply(
          fontFamily: 'sans-serif',
          bodyColor: primaryText,
          displayColor: Colors.white,
        )
        .copyWith(
          titleLarge: headerTextStyle,
          titleMedium: primaryTextStyle,
          titleSmall: sectionTextStyle,
          bodyLarge: primaryTextStyle,
          bodyMedium: primaryTextStyle,
          bodySmall: summaryTextStyle,
          labelLarge: primaryTextStyle.copyWith(fontWeight: FontWeight.w500),
          labelMedium: sectionTextStyle.copyWith(color: primaryText),
        );
    return base.copyWith(
      useMaterial3: false,
      brightness: Brightness.dark,
      applyElevationOverlayColor: false,
      primaryColor: accent,
      colorScheme: const ColorScheme.dark(
        primary: accent,
        secondary: accent,
        surface: panelColor,
        onSurface: primaryText,
        onPrimary: Colors.black,
        onSecondary: Colors.black,
        primaryContainer: headerColor,
        onPrimaryContainer: primaryText,
        secondaryContainer: rowFocusFill,
        onSecondaryContainer: primaryText,
      ),
      textTheme: text,
      primaryTextTheme: text,
      canvasColor: panelColor,
      cardColor: panelColor,
      focusColor: rowFocusFill,
      hoverColor: rowFocusFill,
      dividerColor: const Color(0x1F000000),
      disabledColor: disabledText,
      unselectedWidgetColor: controlNormal,
      iconTheme: const IconThemeData(color: Colors.white, size: iconSize),
      dialogTheme: base.dialogTheme.copyWith(
        backgroundColor: panelColor,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: headerTextStyle,
        contentTextStyle: primaryTextStyle,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(2)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: accent,
          textStyle: primaryTextStyle.copyWith(fontWeight: FontWeight.w500),
          shape: const RoundedRectangleBorder(),
        ),
      ),
      sliderTheme: base.sliderTheme.copyWith(
        trackHeight: 2,
        activeTrackColor: accent,
        thumbColor: accent,
        inactiveTrackColor: const Color(0x4DFFFFFF),
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 9),
        overlayColor: accent.withValues(alpha: .12),
      ),
      inputDecorationTheme: base.inputDecorationTheme.copyWith(
        labelStyle: summaryTextStyle,
        enabledBorder: const UnderlineInputBorder(
          borderSide: BorderSide(color: controlNormal),
        ),
        focusedBorder: const UnderlineInputBorder(
          borderSide: BorderSide(color: accent, width: 2),
        ),
      ),
    );
  }

  static ButtonStyle get actionButtonStyle => ButtonStyle(
    minimumSize: const WidgetStatePropertyAll(Size(0, 48)),
    padding: const WidgetStatePropertyAll(
      EdgeInsetsDirectional.fromSTEB(24, 12, 56, 12),
    ),
    alignment: AlignmentDirectional.centerStart,
    shape: const WidgetStatePropertyAll(RoundedRectangleBorder()),
    foregroundColor: const WidgetStatePropertyAll(primaryText),
    overlayColor: const WidgetStatePropertyAll(Colors.transparent),
    backgroundColor: WidgetStateProperty.resolveWith(
      (states) =>
          states.contains(WidgetState.focused) ||
              states.contains(WidgetState.hovered)
          ? rowFocusFill
          : Colors.transparent,
    ),
    animationDuration: focusDuration,
    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
  );

  static Duration focusDurationFor(BuildContext context) =>
      (MediaQuery.maybeOf(context)?.disableAnimations ?? false)
      ? Duration.zero
      : focusDuration;
}

class SettingsPageHeader extends StatelessWidget {
  final String title;
  const SettingsPageHeader({super.key, required this.title});
  @override
  Widget build(BuildContext context) => Material(
    color: SettingsStyle.headerColor,
    elevation: 2,
    child: Padding(
      padding: SettingsStyle.headerPadding,
      child: SizedBox(
        width: double.infinity,
        height: 64,
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: SettingsStyle.headerTextStyle,
          ),
        ),
      ),
    ),
  );
}

class SettingsSectionHeader extends StatelessWidget {
  final String title;
  const SettingsSectionHeader({super.key, required this.title});
  @override
  Widget build(BuildContext context) => SizedBox(
    height: 38,
    child: Padding(
      padding: SettingsStyle.cardPadding,
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        child: Text(title, style: SettingsStyle.sectionTextStyle),
      ),
    ),
  );
}

class SettingsHairline extends StatelessWidget {
  final double opacity;
  const SettingsHairline({super.key, this.opacity = 1});
  @override
  Widget build(BuildContext context) => Container(
    height: 1,
    color: Colors.black.withValues(alpha: .12 * opacity),
  );
}

class SettingsSectionDivider extends StatelessWidget {
  final EdgeInsetsGeometry margin;
  const SettingsSectionDivider({
    super.key,
    this.margin = const EdgeInsets.symmetric(vertical: 6),
  });
  @override
  Widget build(BuildContext context) =>
      Padding(padding: margin, child: const SizedBox.shrink());
}

/// One focus target per native preference row. Activation and focus restoration
/// retain the existing remote/pointer behaviour; only the surface is painted.
class SettingsFocusCard extends StatefulWidget {
  final Widget Function(BuildContext context, bool highlighted) builder;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final ValueChanged<bool>? onFocusChange;
  final bool autofocus;
  final EdgeInsetsGeometry margin;
  final EdgeInsetsGeometry padding;

  const SettingsFocusCard({
    super.key,
    required this.builder,
    this.onPressed,
    this.onLongPress,
    this.onFocusChange,
    this.autofocus = false,
    this.margin = SettingsStyle.cardMargin,
    this.padding = SettingsStyle.cardPadding,
  });

  @override
  State<SettingsFocusCard> createState() => _SettingsFocusCardState();
}

class _SettingsFocusCardState extends State<SettingsFocusCard>
    with SingleTickerProviderStateMixin {
  final _focusNode = FocusNode();
  late final AnimationController _highlight = AnimationController(
    vsync: this,
    duration: SettingsStyle.focusDuration,
  );
  Duration _duration = SettingsStyle.focusDuration;
  bool _focused = false;
  bool _hovered = false;

  bool get _highlighted => _focused || _hovered;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Respect the platform "reduce motion" setting.
    _duration = SettingsStyle.focusDurationFor(context);
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _highlight.dispose();
    super.dispose();
  }

  void _syncHighlight() {
    final target = _highlighted ? 1.0 : 0.0;
    if (_duration == Duration.zero) {
      _highlight.value = target;
    } else {
      _highlight.animateTo(
        target,
        duration: _duration,
        curve: Curves.easeOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final content = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 48),
      child: Padding(
        padding: widget.padding,
        child: widget.builder(context, _highlighted),
      ),
    );

    return Padding(
      padding: widget.margin,
      child: RepaintBoundary(
        child: Actions(
          actions: <Type, Action<Intent>>{
            ActivateIntent: CallbackAction<ActivateIntent>(
              onInvoke: (_) => widget.onPressed?.call(),
            ),
            ButtonActivateIntent: CallbackAction<ButtonActivateIntent>(
              onInvoke: (_) => widget.onPressed?.call(),
            ),
          },
          child: MouseRegion(
            cursor: widget.onPressed == null
                ? MouseCursor.defer
                : SystemMouseCursors.click,
            onEnter: (_) {
              if (!_hovered) {
                setState(() => _hovered = true);
                _syncHighlight();
              }
            },
            onExit: (_) {
              if (_hovered) {
                setState(() => _hovered = false);
                _syncHighlight();
              }
            },
            child: Focus(
              focusNode: _focusNode,
              autofocus: widget.autofocus,
              onFocusChange: (focused) {
                if (_focused != focused) {
                  setState(() => _focused = focused);
                  _syncHighlight();
                  widget.onFocusChange?.call(focused);
                }
              },
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: widget.onPressed == null
                    ? null
                    : () {
                        _focusNode.requestFocus();
                        widget.onPressed!();
                      },
                onLongPress: widget.onLongPress == null
                    ? null
                    : () {
                        _focusNode.requestFocus();
                        widget.onLongPress!();
                      },
                child: AnimatedBuilder(
                  animation: _highlight,
                  child: content,
                  builder: (context, child) => CustomPaint(
                    painter: SettingsSurfacePainter(progress: _highlight.value),
                    child: child,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A flat native focus highlight, with no scale or per-row border.
class SettingsSurfacePainter extends CustomPainter {
  final double progress;
  const SettingsSurfacePainter({required this.progress});
  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty || progress <= 0) return;
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..color = SettingsStyle.rowFocusFill.withValues(
          alpha: progress.clamp(0, 1),
        ),
    );
  }

  @override
  bool shouldRepaint(covariant SettingsSurfacePainter oldDelegate) =>
      oldDelegate.progress != progress;
}
