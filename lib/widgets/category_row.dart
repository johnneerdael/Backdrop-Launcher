import 'dart:math' as math;

import 'package:flauncher/models/app.dart';
import 'package:flauncher/models/category.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/app_card.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class CategoryRow extends StatefulWidget {
  final Category category;
  final List<App> applications;
  final bool isFirstSection;
  final bool showTitle;
  final VoidCallback? onAppFocused;
  final ValueChanged<int>? onFocusIndexChanged;
  final bool ensureVerticalOnFocus;
  const CategoryRow({
    super.key,
    required this.category,
    required this.applications,
    this.isFirstSection = false,
    this.showTitle = true,
    this.onAppFocused,
    this.onFocusIndexChanged,
    this.ensureVerticalOnFocus = true,
  });
  @override
  State<CategoryRow> createState() => CategoryRowState();
}

class CategoryRowState extends State<CategoryRow> {
  final ScrollController _scroll = ScrollController();
  final Map<String, FocusNode> _focus = {};
  double _slotWidth = 1;

  void requestAppFocus(int index, {bool Function()? shouldFocus}) {
    if (widget.applications.isEmpty) return;
    final position = index.clamp(0, widget.applications.length - 1);
    if (_scroll.hasClients) {
      final target =
          (position * _slotWidth -
                  (_scroll.position.viewportDimension - _slotWidth) / 2)
              .clamp(0.0, _scroll.position.maxScrollExtent);
      _scroll.jumpTo(target);
    }
    final node = _node(widget.applications[position]);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && (shouldFocus?.call() ?? true)) node.requestFocus();
    });
    WidgetsBinding.instance.scheduleFrame();
  }

  FocusNode _node(App app) => _focus.putIfAbsent(
    app.packageName,
    () => FocusNode(debugLabel: app.packageName),
  );

  void _focused(int index) {
    widget.onFocusIndexChanged?.call(index);
    widget.onAppFocused?.call();
    final context = _node(widget.applications[index]).context;
    final render = context?.findRenderObject();
    if (context != null && render != null) {
      Scrollable.maybeOf(
        context,
        axis: Axis.horizontal,
      )?.position.ensureVisible(
        render,
        alignment: 0.5,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final showNames = context.select<SettingsService, bool>(
      (s) => s.showAppNamesBelowIcons,
    );
    final showTitle =
        widget.showTitle &&
        context.select<SettingsService, bool>((s) => s.showCategoryTitles);
    return LayoutBuilder(
      builder: (context, constraints) {
        final extra =
            (showNames ? kAppNameLabelHeight : 0) + (showTitle ? 36.0 : 0) + 16;
        final available = constraints.maxHeight.isFinite
            ? math.max(48.0, constraints.maxHeight - extra)
            : widget.category.rowHeight.toDouble();
        final cardHeight = math.min(
          widget.category.rowHeight.toDouble(),
          available,
        );
        _slotWidth = cardHeight * kAppCardAspectRatio + 24;
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (showTitle)
              Padding(
                padding: const EdgeInsets.only(left: 36, bottom: 8),
                child: Text(
                  widget.category.name,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    shadows: const [
                      Shadow(color: Colors.black54, blurRadius: 8),
                    ],
                  ),
                ),
              ),
            SizedBox(
              height: cardHeight + (showNames ? kAppNameLabelHeight : 0) + 16,
              child: ListView.builder(
                key: PageStorageKey('row_apps_${widget.category.id}'),
                controller: _scroll,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 8,
                ),
                scrollDirection: Axis.horizontal,
                itemCount: widget.applications.length,
                findChildIndexCallback: (key) {
                  if (key is! ValueKey<String>) return null;
                  final index = widget.applications.indexWhere(
                    (app) => app.packageName == key.value,
                  );
                  return index < 0 ? null : index;
                },
                itemBuilder: (context, index) {
                  final app = widget.applications[index];
                  return Padding(
                    key: ValueKey(app.packageName),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: SizedBox(
                      width: cardHeight * kAppCardAspectRatio,
                      child: AppCard(
                        focusNode: _node(app),
                        category: widget.category,
                        application: app,
                        autofocus: widget.isFirstSection && index == 0,
                        handleUpNavigationToSettings: false,
                        ensureVisibleOnFocus: widget.ensureVerticalOnFocus,
                        onFocused: () => _focused(index),
                        onMove: (direction) => _move(context, direction, index),
                        onMoveEnd: () => context
                            .read<AppsService>()
                            .saveApplicationOrderInCategory(widget.category),
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }

  void _move(BuildContext context, AxisDirection direction, int index) {
    final next =
        index +
        (direction == AxisDirection.right
            ? 1
            : direction == AxisDirection.left
            ? -1
            : 0);
    if (next == index || next < 0 || next >= widget.applications.length) return;
    final service = context.read<AppsService>();
    service.setPendingReorderFocus(
      widget.applications[index].packageName,
      widget.category.id,
    );
    service.reorderApplication(widget.category, index, next);
  }

  @override
  void dispose() {
    _scroll.dispose();
    for (final node in _focus.values) {
      node.dispose();
    }
    super.dispose();
  }
}
