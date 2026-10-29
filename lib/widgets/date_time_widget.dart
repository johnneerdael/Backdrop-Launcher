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
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'animated_character.dart';

class DateTimeWidget extends StatefulWidget {
  final Duration? updateInterval;
  final String _dateTimeFormatString;
  final TextStyle? textStyle;
  final bool animate;

  const DateTimeWidget(
    String dateTimeFormatString, {
    super.key,
    this.updateInterval,
    this.textStyle,
    this.animate = false,
  }) : _dateTimeFormatString = dateTimeFormatString;

  @override
  State<DateTimeWidget> createState() => _DateTimeWidgetState();
}

class _DateTimeWidgetState extends State<DateTimeWidget>
    with WidgetsBindingObserver {
  late DateFormat _dateFormat;
  late DateTime _now;
  Timer? _timer;

  @override
  void initState() {
    super.initState();

    _dateFormat = DateFormat(widget._dateTimeFormatString, Platform.localeName);
    _now = DateTime.now();
    WidgetsBinding.instance.addObserver(this);
    _restartTimer();
  }

  void _restartTimer() {
    _timer?.cancel();
    _timer = null;
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    if (lifecycle != null && lifecycle != AppLifecycleState.resumed) return;
    _timer = Timer.periodic(
      widget.updateInterval ?? _defaultInterval(),
      (_) => _refreshTime(),
    );
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _restartTimer();
    if (state == AppLifecycleState.resumed) _refreshTime();
  }

  /// Returns 1-second interval if format contains seconds, otherwise 1-minute.
  Duration _defaultInterval() {
    final fmt = widget._dateTimeFormatString;
    return (fmt.contains('s') || fmt.contains('S'))
        ? const Duration(seconds: 1)
        : const Duration(minutes: 1);
  }

  @override
  void didUpdateWidget(DateTimeWidget oldWidget) {
    super.didUpdateWidget(oldWidget);

    // Update format if it changed
    if (oldWidget._dateTimeFormatString != widget._dateTimeFormatString) {
      _dateFormat = DateFormat(
        widget._dateTimeFormatString,
        Platform.localeName,
      );
      setState(() {
        _now = DateTime.now();
      });
    }
    if (oldWidget._dateTimeFormatString != widget._dateTimeFormatString ||
        oldWidget.updateInterval != widget.updateInterval)
      _restartTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final formattedText = _dateFormat.format(_now);

    if (widget.animate) {
      return AnimatedTimeDisplay(
        displayText: formattedText,
        textStyle: widget.textStyle,
      );
    }

    return Text(formattedText, style: widget.textStyle);
  }

  void _refreshTime() {
    setState(() {
      _now = DateTime.now();
    });
  }
}
