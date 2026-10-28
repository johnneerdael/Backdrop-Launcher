/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const longPressableKeys = [
  LogicalKeyboardKey.select,
  LogicalKeyboardKey.enter,
  LogicalKeyboardKey.gameButtonA,
];

class FocusKeyboardListener extends StatefulWidget {
  final Widget child;
  final KeyEventResult Function(LogicalKeyboardKey)? onPressed;
  final KeyEventResult Function(LogicalKeyboardKey)? onLongPress;

  const FocusKeyboardListener({
    super.key,
    required this.child,
    this.onPressed,
    this.onLongPress,
  });

  @override
  State<FocusKeyboardListener> createState() => _FocusKeyboardListenerState();
}

class _FocusKeyboardListenerState extends State<FocusKeyboardListener> {
  LogicalKeyboardKey? _heldKey;
  Duration? _downAt;
  Timer? _holdTimer;
  bool _longPressFired = false;
  static const _holdDuration = Duration(milliseconds: 500);

  void _reset() {
    _holdTimer?.cancel();
    _holdTimer = null;
    _heldKey = null;
    _downAt = null;
    _longPressFired = false;
  }

  void _fireLongPress() {
    if (!mounted || _heldKey == null || _longPressFired) return;
    _longPressFired = true;
    _holdTimer?.cancel();
    widget.onLongPress?.call(_heldKey!);
  }

  KeyEventResult _handleKey(FocusNode _, KeyEvent event) {
    final key = event.logicalKey;
    if (!longPressableKeys.contains(key) || widget.onLongPress == null) {
      return event is KeyDownEvent || event is KeyRepeatEvent
          ? widget.onPressed?.call(key) ?? KeyEventResult.ignored
          : KeyEventResult.ignored;
    }
    if (event is KeyDownEvent) {
      _reset();
      _heldKey = key;
      _downAt = event.timeStamp;
      _holdTimer = Timer(_holdDuration, _fireLongPress);
      return KeyEventResult.handled;
    }
    if (event is KeyRepeatEvent) {
      if (_heldKey == key &&
          _downAt != null &&
          event.timeStamp - _downAt! >= _holdDuration)
        _fireLongPress();
      return KeyEventResult.handled;
    }
    if (event is KeyUpEvent && _heldKey == key) {
      final activate = !_longPressFired;
      _reset();
      return activate
          ? widget.onPressed?.call(key) ?? KeyEventResult.ignored
          : KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) => Focus(
    canRequestFocus: false,
    onKeyEvent: _handleKey,
    onFocusChange: (focused) {
      if (!focused) _reset();
    },
    child: widget.child,
  );

  @override
  void dispose() {
    _reset();
    super.dispose();
  }
}
