/*
 * FLauncher
 * Copyright (C) 2021 Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:flauncher/widgets/watch_next_carousel.dart';
import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

class WatchNextRow extends StatefulWidget {
  final bool isFirstSection;
  final VoidCallback? onUp;
  final VoidCallback? onDown;
  final double imageHeight;
  const WatchNextRow({
    super.key,
    this.isFirstSection = false,
    this.onUp,
    this.onDown,
    this.imageHeight = 300,
  });
  @override
  State<WatchNextRow> createState() => WatchNextRowState();
}

class WatchNextRowState extends State<WatchNextRow> {
  final FocusScopeNode _scope = FocusScopeNode(
    debugLabel: 'Continue Watching zone',
  );
  final FocusNode _primary = FocusNode(debugLabel: 'Continue Watching');
  final FocusNode _checkPermission = FocusNode(debugLabel: 'Check permission');
  bool? _lastPermission;
  bool get hasFocus => _scope.hasFocus;
  void requestFocus() => _primary.requestFocus();

  @override
  Widget build(BuildContext context) {
    if (!context.select<SettingsService, bool>((s) => s.showWatchNextSection))
      return const SizedBox.shrink();
    final service = context.watch<WatchNextService>();
    if (_lastPermission != null &&
        _lastPermission != service.hasPermission &&
        hasFocus) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && ModalRoute.of(context)?.isCurrent != false)
          requestFocus();
      });
    }
    _lastPermission = service.hasPermission;
    return FocusScope(
      node: _scope,
      child: !service.hasPermission
          ? Focus(
              canRequestFocus: false,
              onKeyEvent: (_, event) {
                if (event is! KeyDownEvent && event is! KeyRepeatEvent)
                  return KeyEventResult.ignored;
                if (event.logicalKey == LogicalKeyboardKey.arrowUp &&
                    _primary.hasPrimaryFocus) {
                  widget.onUp?.call();
                  return KeyEventResult.handled;
                }
                if (event.logicalKey == LogicalKeyboardKey.arrowDown &&
                    _checkPermission.hasPrimaryFocus) {
                  widget.onDown?.call();
                  return KeyEventResult.handled;
                }
                return KeyEventResult.ignored;
              },
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  _WatchNextSectionTitle(
                    title: AppLocalizations.of(context)!.watchNextSectionTitle,
                  ),
                  _WatchNextPermissionBanner(
                    onGrant: service.requestPermission,
                    onRecheck: service.refreshPermissionAndItems,
                    grantFocus: _primary,
                    checkFocus: _checkPermission,
                    autofocus: widget.isFirstSection,
                  ),
                ],
              ),
            )
          : service.items.isEmpty
          ? const SizedBox.shrink()
          : WatchNextCarousel(
              items: service.items,
              focusNode: _primary,
              autofocus: widget.isFirstSection,
              onUp: widget.onUp,
              onDown: widget.onDown,
              imageHeight: widget.imageHeight,
            ),
    );
  }

  @override
  void dispose() {
    _scope.dispose();
    _primary.dispose();
    _checkPermission.dispose();
    super.dispose();
  }
}

class _WatchNextSectionTitle extends StatelessWidget {
  final String title;

  const _WatchNextSectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 40, bottom: 8, top: 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleLarge!.copyWith(
          shadows: const [
            Shadow(color: Colors.black54, offset: Offset(1, 1), blurRadius: 8),
          ],
        ),
      ),
    );
  }
}

class _WatchNextPermissionBanner extends StatelessWidget {
  final VoidCallback onGrant;
  final VoidCallback onRecheck;
  final FocusNode grantFocus;
  final FocusNode checkFocus;
  final bool autofocus;

  const _WatchNextPermissionBanner({
    required this.onGrant,
    required this.onRecheck,
    required this.grantFocus,
    required this.checkFocus,
    required this.autofocus,
  });

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              const Icon(Icons.tv, size: 36),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      localizations.watchNextPermissionTitle,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      localizations.watchNextPermissionBody,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  ElevatedButton.icon(
                    focusNode: grantFocus,
                    autofocus: autofocus,
                    onPressed: onGrant,
                    icon: const Icon(Icons.lock_open),
                    label: Text(localizations.watchNextGrantPermission),
                  ),
                  const SizedBox(height: 8),
                  TextButton.icon(
                    focusNode: checkFocus,
                    onPressed: onRecheck,
                    icon: const Icon(Icons.refresh),
                    label: Text(localizations.watchNextCheckPermission),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
