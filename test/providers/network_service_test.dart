import 'dart:async';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flutter/services.dart';
import 'package:flauncher/providers/network_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';

import '../mocks.mocks.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'missing Android network capabilities leave a valid offline state',
    () async {
      final channel = MockFLauncherChannel();
      when(
        channel.getActiveNetworkInformation(),
      ).thenAnswer((_) async => {'wirelessSignalLevel': 0});
      final network = NetworkService(channel);
      addTearDown(network.dispose);
      await Future<void>.delayed(Duration.zero);
      expect(network.networkType, NetworkType.Unknown);
      expect(network.hasInternetAccess, isFalse);
    },
  );
  test(
    'The initial network reply publishes connection state to listeners',
    () async {
      final channel = MockFLauncherChannel();
      final initial = Completer<Map<String, dynamic>>();
      when(
        channel.getActiveNetworkInformation(),
      ).thenAnswer((_) => initial.future);
      final network = NetworkService(channel);
      addTearDown(network.dispose);
      final observed = <NetworkType>[];
      network.addListener(() => observed.add(network.networkType));
      initial.complete({
        'internetAccess': true,
        'networkType': 1,
        'wirelessSignalLevel': 3,
      });
      await Future<void>.delayed(Duration.zero);
      expect(observed, [NetworkType.Wifi]);
      expect(network.hasInternetAccess, isTrue);
    },
  );

  test('A late initial network reply is ignored after disposal', () async {
    final channel = MockFLauncherChannel();
    final initial = Completer<Map<String, dynamic>>();
    when(
      channel.getActiveNetworkInformation(),
    ).thenAnswer((_) => initial.future);
    final network = NetworkService(channel);
    network.dispose();
    initial.complete({
      'internetAccess': true,
      'networkType': 1,
      'wirelessSignalLevel': 3,
    });
    await Future<void>.delayed(Duration.zero);
    expect(network.networkType, NetworkType.Unknown);
  });

  testWidgets(
    'Connection status startup and idle never request usage access or counters',
    (tester) async {
      const channel = MethodChannel('nl.neerdael.backdrop_launcher/method');
      const events = MethodChannel('nl.neerdael.backdrop_launcher/event_network');
      final messenger = tester.binding.defaultBinaryMessenger;
      final calls = <String>[];
      messenger.setMockMethodCallHandler(events, (_) async => null);
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        switch (call.method) {
          case 'getActiveNetworkInformation':
            return {
              'internetAccess': true,
              'networkType': 1,
              'wirelessSignalLevel': 3,
            };
          case 'checkUsageStatsPermission':
            return true;
          case 'getDailyWifiUsage':
            return 1024;
          default:
            throw StateError('Unexpected platform call: ${call.method}');
        }
      });
      final network = NetworkService(FLauncherChannel());
      try {
        await tester.pump();
        await tester.pump(const Duration(minutes: 11));
        expect(network.networkType, NetworkType.Wifi);
        expect(network.hasInternetAccess, isTrue);
        expect(network.wirelessNetworkSignalLevel, 3);
        expect(calls, ['getActiveNetworkInformation']);
      } finally {
        network.dispose();
        messenger.setMockMethodCallHandler(channel, null);
        messenger.setMockMethodCallHandler(events, null);
      }
    },
  );
}
