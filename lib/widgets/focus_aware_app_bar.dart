import 'package:flauncher/widgets/settings/settings_panel.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../providers/network_service.dart';
import '../providers/settings_service.dart';
import 'app_bar_focus_ring.dart';
import 'date_time_widget.dart';
import 'network_widget.dart';

class FocusAwareAppBar extends StatefulWidget implements PreferredSizeWidget {
  final VoidCallback? onDown;

  const FocusAwareAppBar({Key? key, this.onDown}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return FocusAwareAppBarState();
  }

  @override
  Size get preferredSize => Size.fromHeight(kToolbarHeight);
}

class FocusAwareAppBarState extends State<FocusAwareAppBar> {
  bool focused = false;
  late FocusNode _settingsFocusNode;

  @override
  void initState() {
    super.initState();
    _settingsFocusNode = FocusNode();
  }

  @override
  void dispose() {
    _settingsFocusNode.dispose();
    super.dispose();
  }

  void focusSettings() {
    _settingsFocusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    return Selector<SettingsService, bool>(
      selector: (_, settings) => settings.autoHideAppBarEnabled,
      builder: (context, autoHide, child) {
        final isVisible = focused || !autoHide;

        return Focus(
          canRequestFocus: false,
          onKeyEvent: (_, event) {
            if (widget.onDown != null &&
                event is KeyDownEvent &&
                event.logicalKey == LogicalKeyboardKey.arrowDown) {
              widget.onDown!();
              return KeyEventResult.handled;
            }
            return KeyEventResult.ignored;
          },
          onFocusChange: (hasFocus) {
            setState(() {
              focused = hasFocus;
            });
          },
          child: IgnorePointer(
            ignoring: !isVisible,
            child: AnimatedOpacity(
              curve: Curves.easeInOut,
              duration: const Duration(milliseconds: 200),
              opacity: isVisible ? 1.0 : 0.0,
              child: ClipRect(
                child: AnimatedContainer(
                  curve: Curves.decelerate,
                  duration: const Duration(milliseconds: 200),
                  height: isVisible ? kToolbarHeight : 0,
                  child: OverflowBox(
                    minHeight: 0,
                    maxHeight: kToolbarHeight,
                    alignment: Alignment.topCenter,
                    child: child!,
                  ),
                ),
              ),
            ),
          ),
        );
      },
      child: RepaintBoundary(
        child: AppBar(
          elevation: 0,
          scrolledUnderElevation: 0,
          backgroundColor: Colors.transparent,
          // Left side: optional network connection indicator.
          title: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Network indicator (conditionally shown)
              Selector<SettingsService, bool>(
                selector: (_, settings) =>
                    settings.showNetworkIndicatorInStatusBar,
                builder: (context, showNetwork, _) => showNetwork
                    ? Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: AppBarFocusRing(
                          onPressed: () =>
                              context.read<NetworkService>().openWifiSettings(),
                          semanticLabel: 'Network',
                          child: const NetworkWidget(),
                        ),
                      )
                    : const SizedBox.shrink(),
              ),
            ],
          ),
          // Right side: optional date, then Settings beside the clock.
          actions: [
            Padding(
              padding: const EdgeInsets.only(left: 16, right: 32),
              child:
                  Selector<
                    SettingsService,
                    ({
                      bool showDateInStatusBar,
                      bool showTimeInStatusBar,
                      String dateFormat,
                      String timeFormat,
                    })
                  >(
                    selector: (context, service) => (
                      showDateInStatusBar: service.showDateInStatusBar,
                      showTimeInStatusBar: service.showTimeInStatusBar,
                      dateFormat: service.dateFormat,
                      timeFormat: service.timeFormat,
                    ),
                    builder: (context, dateTimeSettings, _) {
                      // Define standard text style
                      const textStyle = TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w400,
                        color: Colors.white,
                        shadows: [
                          Shadow(
                            color: Colors.black54,
                            offset: Offset(0, 2),
                            blurRadius: 4,
                          ),
                        ],
                      );

                      return Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          // Date
                          if (dateTimeSettings.showDateInStatusBar)
                            DateTimeWidget(
                              dateTimeSettings.dateFormat,
                              key: const Key("statusbar_date"),
                              updateInterval: const Duration(minutes: 1),
                              textStyle: textStyle,
                            ),
                          if (dateTimeSettings.showDateInStatusBar)
                            const SizedBox(width: 16),

                          // Settings button
                          AppBarFocusRing(
                            focusNode: _settingsFocusNode,
                            onPressed: () => SettingsPanel.show(context),
                            semanticLabel: 'Settings',
                            child: const Icon(
                              Icons.settings_outlined,
                              size: 20,
                              color: Colors.white,
                              shadows: [
                                Shadow(
                                  color: Colors.black54,
                                  blurRadius: 8,
                                  offset: Offset(0, 2),
                                ),
                              ],
                            ),
                          ),
                          if (dateTimeSettings.showTimeInStatusBar)
                            const SizedBox(width: 12),

                          // Clock
                          if (dateTimeSettings.showTimeInStatusBar)
                            DateTimeWidget(
                              dateTimeSettings.timeFormat,
                              key: const Key("statusbar_clock"),
                              updateInterval: const Duration(minutes: 1),
                              textStyle: textStyle.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                        ],
                      );
                    },
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
