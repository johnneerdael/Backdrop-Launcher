import 'dart:async';

import 'package:flauncher/database.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/providers/apps_service.dart';

class RowsChannel extends FLauncherChannel {
  List<Map<dynamic, dynamic>> installed;
  void Function(Map<String, dynamic>)? listener;
  RowsChannel(this.installed);

  @override
  Future<List<Map<dynamic, dynamic>>> getApplications() async => installed;

  @override
  Future<bool> applicationExists(String packageName) async =>
      installed.any((app) => app['packageName'] == packageName);

  @override
  void addAppsChangedListener(void Function(Map<String, dynamic>) callback) {
    listener = callback;
  }
}

Map<dynamic, dynamic> installedApp(
  String packageName, {
  bool android = false,
}) => {
  'packageName': packageName,
  'name': packageName,
  'version': '1',
  'sideloaded': android,
};

Future<AppsService> initializedService(
  RowsChannel channel,
  FLauncherDatabase database,
) async {
  final service = AppsService(channel, database);
  if (!service.initialized) {
    final ready = Completer<void>();
    void listener() {
      if (service.initialized && !ready.isCompleted) ready.complete();
    }

    service.addListener(listener);
    await ready.future.timeout(const Duration(seconds: 5));
    service.removeListener(listener);
  }
  return service;
}

Future<void> emitEvent(
  AppsService service,
  RowsChannel channel,
  Map<String, dynamic> event,
) async {
  final completed = Completer<void>();
  void listener() {
    if (!completed.isCompleted) completed.complete();
  }

  service.addListener(listener);
  channel.listener!(event);
  await completed.future.timeout(const Duration(seconds: 5));
  service.removeListener(listener);
}
