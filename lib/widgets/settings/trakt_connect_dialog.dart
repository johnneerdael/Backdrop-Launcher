import 'dart:async';

import 'package:flauncher/providers/trakt_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

class TraktConnectDialog extends StatefulWidget {
  const TraktConnectDialog({super.key});

  @override
  State<TraktConnectDialog> createState() => _TraktConnectDialogState();
}

class _TraktConnectDialogState extends State<TraktConnectDialog> {
  late final TraktService _trakt;
  TraktDeviceAuthorization? _device;
  Timer? _pollTimer;
  Timer? _expiryTimer;
  String? _message;
  bool _terminal = false;

  @override
  void initState() {
    super.initState();
    _trakt = context.read<TraktService>();
    _start();
  }

  Future<void> _start() async {
    _pollTimer?.cancel();
    _expiryTimer?.cancel();
    setState(() {
      _device = null;
      _message = null;
      _terminal = false;
    });
    try {
      final device = await _trakt.startDeviceAuth();
      if (!mounted) return;
      setState(() => _device = device);
      _expiryTimer = Timer(
        device.expiresAt.difference(DateTime.now()),
        () => _finish('This code expired. Try connecting again.'),
      );
      _schedulePoll();
    } catch (_) {
      if (mounted)
        _finish(
          'Unable to connect to Trakt. Check your connection and try again.',
        );
    }
  }

  void _schedulePoll() {
    if (!mounted || _terminal || _device == null) return;
    _pollTimer = Timer(_device!.interval, _poll);
  }

  Future<void> _poll() async {
    try {
      final result = await _trakt.pollDeviceAuth();
      if (!mounted || _terminal) return;
      switch (result) {
        case TraktPollResult.approved:
          Navigator.of(context).pop(true);
          return;
        case TraktPollResult.pending:
        case TraktPollResult.slowDown:
          setState(() => _message = null);
        case TraktPollResult.denied:
          _finish('Connection was declined.');
          return;
        case TraktPollResult.expired:
          _finish('This code expired. Try connecting again.');
          return;
        case TraktPollResult.invalid:
        case TraktPollResult.alreadyUsed:
          _finish('This code is no longer valid. Try connecting again.');
          return;
        case TraktPollResult.cancelled:
          return;
      }
    } catch (_) {
      if (!mounted || _terminal) return;
      setState(() => _message = 'Waiting for Trakt. Check your connection.');
    }
    _schedulePoll();
  }

  void _finish(String message) {
    _pollTimer?.cancel();
    _expiryTimer?.cancel();
    _trakt.cancelDeviceAuth();
    if (mounted)
      setState(() {
        _terminal = true;
        _message = message;
      });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _expiryTimer?.cancel();
    _trakt.cancelDeviceAuth();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Connect Trakt'),
    scrollable: true,
    content: SizedBox(
      width: 420,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_device == null && !_terminal) const CircularProgressIndicator(),
          if (_device != null && !_terminal) ...[
            const Text(
              'Scan on your phone, enter the code below, and approve the connection.',
            ),
            const SizedBox(height: 12),
            SizedBox.square(
              dimension: 180,
              child: QrImageView(
                data: _device!.activationUrl.toString(),
                size: 180,
                backgroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            Text('Or visit ${_device!.verificationUrl} and enter:'),
            SelectableText(
              _device!.userCode,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
          if (_message != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_message!),
            ),
        ],
      ),
    ),
    actions: [
      if (_terminal)
        TextButton(
          autofocus: true,
          onPressed: _start,
          child: const Text('Try again'),
        ),
      TextButton(
        autofocus: !_terminal,
        onPressed: () => Navigator.of(context).pop(false),
        child: const Text('Cancel'),
      ),
    ],
  );
}
