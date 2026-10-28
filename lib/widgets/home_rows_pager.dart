import 'package:flauncher/models/category.dart';
import 'package:flauncher/widgets/category_row.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Rows glide past a fixed upper boundary; the next row stays as a faint preview.
class HomeRowsPager extends StatefulWidget {
  final List<Category> categories;
  final VoidCallback onUpToSettings;
  const HomeRowsPager({
    super.key,
    required this.categories,
    required this.onUpToSettings,
  });
  @override
  State<HomeRowsPager> createState() => HomeRowsPagerState();
}

class HomeRowsPagerState extends State<HomeRowsPager> {
  final ScrollController _scroll = ScrollController();
  final FocusNode _pagerFocus = FocusNode(debugLabel: 'Home rows');
  final Map<int, GlobalKey<CategoryRowState>> _rowKeys = {};
  final Map<int, String> _focusedPackages = {};
  final Map<Object, FocusScopeNode> _scopes = {};
  int _target = 0;
  int _focusGeneration = 0;
  double _extent = 1;
  bool _routeActive = true;
  bool _restoreFocus = false;
  List<int> get _ids => [...widget.categories.map((row) => row.id)];

  void requestActiveRowFocus() {
    _pagerFocus.requestFocus();
    _focusTarget(++_focusGeneration, force: true);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final active = ModalRoute.of(context)?.isCurrent ?? true;
    if (!active && _routeActive) _restoreFocus = _pagerFocus.hasFocus;
    if (active && !_routeActive && _restoreFocus) {
      final generation = ++_focusGeneration;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _focusTarget(generation, force: true),
      );
    }
    _routeActive = active;
  }

  @override
  void didUpdateWidget(HomeRowsPager oldWidget) {
    super.didUpdateWidget(oldWidget);
    final previous = <int>[...oldWidget.categories.map((row) => row.id)];
    final current = _ids;
    final oldId = previous.isEmpty
        ? null
        : previous[_target.clamp(0, previous.length - 1)];
    final preserved = oldId == null ? -1 : current.indexOf(oldId);
    final next = preserved >= 0
        ? preserved
        : _target.clamp(0, (current.length - 1).clamp(0, current.length));
    if (next != _target ||
        previous.length != current.length ||
        (oldId == null ? -1 : previous.indexOf(oldId)) != preserved) {
      _target = next;
      final wasFocused = _pagerFocus.hasFocus;
      final generation = ++_focusGeneration;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || generation != _focusGeneration || !_scroll.hasClients)
          return;
        _scroll.jumpTo(_target * _extent);
        if (wasFocused) _focusTarget(generation, force: true);
      });
    }
  }

  void _focusTarget(int generation, {bool force = false}) {
    if (!mounted || generation != _focusGeneration || _ids.isEmpty) return;
    if (ModalRoute.of(context)?.isCurrent == false) return;
    if (!force && !_pagerFocus.hasFocus) return;
    if (force) _pagerFocus.requestFocus();
    final id = _ids[_target];
    final row = widget.categories.firstWhere((row) => row.id == id);
    final position = row.applications.indexWhere(
      (app) => app.packageName == _focusedPackages[id],
    );
    _rowKeys[id]?.currentState?.requestAppFocus(
      position < 0 ? 0 : position,
      shouldFocus: () =>
          mounted &&
          generation == _focusGeneration &&
          ModalRoute.of(context)?.isCurrent != false &&
          _pagerFocus.hasFocus,
    );
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent)
      return KeyEventResult.ignored;
    if (event.logicalKey != LogicalKeyboardKey.arrowUp &&
        event.logicalKey != LogicalKeyboardKey.arrowDown)
      return KeyEventResult.ignored;
    if (event.logicalKey == LogicalKeyboardKey.arrowUp && _target == 0) {
      ++_focusGeneration;
      widget.onUpToSettings();
      return KeyEventResult.handled;
    }
    if (_ids.isEmpty) return KeyEventResult.ignored;
    final next =
        (_target + (event.logicalKey == LogicalKeyboardKey.arrowDown ? 1 : -1))
            .clamp(0, _ids.length - 1);
    if (next == _target) return KeyEventResult.handled;
    _pagerFocus.requestFocus();
    setState(() => _target = next);
    final generation = ++_focusGeneration;
    _scroll
        .animateTo(
          _target * _extent,
          duration: const Duration(milliseconds: 280),
          curve: Curves.easeOutCubic,
        )
        .then((_) => _focusTarget(generation));
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final ids = _ids;
      _extent = constraints.maxHeight * 0.72;
      return Focus(
        focusNode: _pagerFocus,
        onKeyEvent: _onKey,
        child: ListView.builder(
          controller: _scroll,
          physics: const NeverScrollableScrollPhysics(),
          clipBehavior: Clip.hardEdge,
          itemExtent: _extent,
          padding: EdgeInsets.only(bottom: constraints.maxHeight - _extent),
          itemCount: ids.length,
          itemBuilder: (context, index) {
            final id = ids[index];
            final scope = _scopes.putIfAbsent(
              id,
              () => FocusScopeNode(debugLabel: 'Row $id'),
            );
            final row = widget.categories.firstWhere((row) => row.id == id);
            final child = CategoryRow(
              key: _rowKeys.putIfAbsent(
                id,
                () => GlobalKey<CategoryRowState>(),
              ),
              category: row,
              applications: row.applications,
              isFirstSection: index == 0,
              ensureVerticalOnFocus: false,
              onFocusIndexChanged: (position) {
                if (position >= 0 && position < row.applications.length) {
                  _focusedPackages[id] = row.applications[position].packageName;
                }
              },
            );
            return FocusScope(
              key: ValueKey(id),
              node: scope,
              canRequestFocus: index == _target && _routeActive,
              descendantsAreFocusable: index == _target && _routeActive,
              // Inactive scopes already block focus. Keeping traversal enabled
              // avoids descendants inheriting a sticky skipTraversal flag.
              descendantsAreTraversable: true,
              child: IgnorePointer(
                ignoring: index != _target,
                child: AnimatedBuilder(
                  animation: _scroll,
                  child: RepaintBoundary(
                    child: Align(
                      alignment: Alignment.topCenter,
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: child,
                      ),
                    ),
                  ),
                  builder: (context, child) {
                    final page = _scroll.hasClients
                        ? _scroll.offset / _extent
                        : 0.0;
                    final distance = (index - page).abs().clamp(0.0, 1.0);
                    return Opacity(
                      key: ValueKey('home_row_opacity_$id'),
                      opacity: 1.0 - distance * 0.82,
                      child: child,
                    );
                  },
                ),
              ),
            );
          },
        ),
      );
    },
  );

  @override
  void dispose() {
    _focusGeneration++;
    _scroll.dispose();
    _pagerFocus.dispose();
    for (final scope in _scopes.values) {
      scope.dispose();
    }
    super.dispose();
  }
}
