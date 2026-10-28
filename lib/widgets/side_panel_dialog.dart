import 'package:flauncher/actions.dart';
import 'package:flutter/material.dart';

import 'settings/settings_theme.dart';

/// The same right-hand surface, geometry and typography as TV settings.
class SidePanelDialog extends StatelessWidget {
  final Widget? child;
  final WidgetBuilder? builder;
  final double width;
  final bool isRightSide;

  const SidePanelDialog({
    this.child,
    this.builder,
    this.width = SettingsStyle.panelWidth,
    this.isRightSide = true,
  }) : assert((child == null) != (builder == null));

  static Future<T?> show<T>({
    required BuildContext context,
    required WidgetBuilder builder,
    Color barrierColor = SettingsStyle.scrimColor,
  }) => showDialog<T>(
    context: context,
    barrierColor: barrierColor,
    builder: builder,
  );

  @override
  Widget build(BuildContext context) => Align(
    alignment: isRightSide ? Alignment.centerRight : Alignment.centerLeft,
    child: SizedBox(
      width: width,
      child: Theme(
        data: SettingsStyle.nativeTheme(Theme.of(context)),
        child: Builder(
          builder: (context) => Material(
            color: SettingsStyle.panelColor,
            elevation: 6,
            child: Actions(
              actions: {BackIntent: BackAction(context)},
              child: builder?.call(context) ?? child!,
            ),
          ),
        ),
      ),
    ),
  );
}
