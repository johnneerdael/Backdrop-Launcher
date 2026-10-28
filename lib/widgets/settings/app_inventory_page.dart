import 'dart:typed_data';
import 'package:flauncher/models/app.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/widgets/settings/app_details_page.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class AppInventoryPage extends StatelessWidget {
  static const routeName = 'installed_apps';
  const AppInventoryPage({super.key});
  @override
  Widget build(BuildContext context) {
    final apps = context.watch<AppsService>().applications;
    return Column(
      children: [
        const SettingsPageHeader(title: 'Installed apps'),
        Expanded(
          child: ListView.builder(
            itemCount: apps.length,
            itemBuilder: (context, index) {
              final app = apps[index];
              return FocusableSettingsTile(
                autofocus: index == 0,
                title: Text(app.name),
                subtitle: Text(
                  app.hidden
                      ? 'Hidden on home screen'
                      : app.sideloaded
                      ? 'Android app'
                      : 'TV app',
                ),
                leading: InstalledAppIcon(app: app),
                showChevron: true,
                onPressed: () => Navigator.of(
                  context,
                ).pushNamed(AppDetailsPage.routeName, arguments: app),
              );
            },
          ),
        ),
      ],
    );
  }
}

class InstalledAppIcon extends StatelessWidget {
  final App app;
  const InstalledAppIcon({super.key, required this.app});
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 32,
    height: 32,
    child: FutureBuilder<Uint8List>(
      future: context.read<AppsService>().getAppIcon(app.packageName),
      builder: (context, snapshot) =>
          snapshot.hasData && snapshot.data!.isNotEmpty
          ? Image.memory(
              snapshot.data!,
              cacheWidth: 64,
              errorBuilder: (_, __, ___) => const Icon(Icons.apps),
            )
          : const Icon(Icons.apps),
    ),
  );
}
