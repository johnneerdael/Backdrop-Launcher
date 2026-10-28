import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'settings_theme.dart';

/// A native-style preference row with one remote activation target.
class FocusableSettingsTile extends StatelessWidget {
  final Widget title;
  final Widget? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onPressed;
  final VoidCallback? onRightPressed;
  final VoidCallback? onLongPress;
  final ValueChanged<bool>? onFocusChange;
  final bool autofocus;
  final bool showChevron;
  final bool frameLeading;

  const FocusableSettingsTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onPressed,
    this.onRightPressed,
    this.onLongPress,
    this.onFocusChange,
    this.autofocus = false,
    this.showChevron = false,
    this.frameLeading = true,
  });

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final radio =
        leading is Icon &&
        !frameLeading &&
        const [
          Icons.radio_button_checked,
          Icons.radio_button_unchecked,
        ].contains((leading as Icon).icon);
    Widget? icon = leading;
    if (radio) {
      final selected = (leading as Icon).icon == Icons.radio_button_checked;
      icon = SizedBox(
        width: 32,
        height: 32,
        child: Center(
          child: Icon(
            selected
                ? Icons.radio_button_checked
                : Icons.radio_button_unchecked,
            size: 24,
            color: selected
                ? SettingsStyle.accent
                : SettingsStyle.controlNormal,
          ),
        ),
      );
    } else {
      icon = _nativeCheckbox(icon);
    }
    final trailingControl = _nativeCheckbox(trailing);

    return Focus(
      canRequestFocus: false,
      onKeyEvent: (_, event) {
        if (event is KeyRepeatEvent &&
            const [
              LogicalKeyboardKey.select,
              LogicalKeyboardKey.enter,
              LogicalKeyboardKey.gameButtonA,
              LogicalKeyboardKey.space,
            ].contains(event.logicalKey))
          return KeyEventResult.handled;
        final open = onRightPressed ?? onPressed;
        if (!showChevron ||
            open == null ||
            event.logicalKey != LogicalKeyboardKey.arrowRight) {
          return KeyEventResult.ignored;
        }
        if (event is KeyDownEvent) open();
        return KeyEventResult.handled;
      },
      child: SettingsFocusCard(
        autofocus: autofocus,
        onPressed: onPressed,
        onLongPress: onLongPress,
        onFocusChange: onFocusChange,
        builder: (context, highlighted) => Row(
          children: [
            if (icon != null)
              SizedBox(
                width: radio ? 48 : 40,
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: IconTheme.merge(
                    data: const IconThemeData(size: 24, color: Colors.white),
                    child: icon,
                  ),
                ),
              ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(top: 14, bottom: 13),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DefaultTextStyle.merge(
                      style: text.bodyMedium ?? SettingsStyle.primaryTextStyle,
                      child: title,
                    ),
                    const SizedBox(height: 2),
                    if (subtitle != null)
                      DefaultTextStyle.merge(
                        style: text.bodySmall ?? SettingsStyle.summaryTextStyle,
                        maxLines: 4,
                        overflow: TextOverflow.ellipsis,
                        child: subtitle!,
                      ),
                  ],
                ),
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: 16),
              trailingControl!,
            ] else if (showChevron && onRightPressed != null) ...[
              const SizedBox(width: 8),
              const Icon(
                Icons.chevron_right,
                size: 20,
                color: SettingsStyle.controlNormal,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget? _nativeCheckbox(Widget? widget) {
    if (widget is! Icon ||
        !const [
          Icons.check_box,
          Icons.check_box_outline_blank,
        ].contains(widget.icon)) {
      return widget;
    }
    return Icon(
      widget.icon,
      size: 24,
      color: widget.icon == Icons.check_box
          ? SettingsStyle.accent
          : SettingsStyle.controlNormal,
    );
  }
}
