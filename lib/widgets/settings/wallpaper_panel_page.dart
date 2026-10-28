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

import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/providers/wallpaper_service.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flauncher/widgets/settings/trakt_catalogs_page.dart';
import 'package:flauncher/widgets/settings/trakt_connect_dialog.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class WallpaperPanelPage extends StatelessWidget {
  static const routeName = 'wallpaper_panel';
  const WallpaperPanelPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final wallpaper = context.watch<WallpaperService>();
    final trakt = context.watch<TraktService>();
    return Column(
      children: [
        const SettingsPageHeader(title: 'Backdrops'),
        Expanded(
          child: ListView(
            children: [
              Padding(
                padding: const EdgeInsets.all(12),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image(
                    image: ResizeImage(wallpaper.wallpaper, width: 640),
                    height: 150,
                    fit: BoxFit.cover,
                  ),
                ),
              ),
              FocusableSettingsTile(
                autofocus: true,
                leading: const Icon(Icons.skip_next),
                title: Text(
                  wallpaper.isLoading ? 'Loading backdrop…' : 'Next backdrop',
                ),
                subtitle: Text(
                  wallpaper.title ?? '10 included cinematic backdrops',
                ),
                onPressed: wallpaper.isLoading
                    ? null
                    : () => wallpaper.nextBackdrop(),
              ),
              FocusableSettingsTile(
                leading: const Icon(Icons.timer_outlined),
                title: const Text('Change backdrop every'),
                trailing: Text('${settings.backdropIntervalMinutes} min'),
                onPressed: () {
                  final choices = SettingsService.backdropIntervalChoices;
                  settings.setBackdropIntervalMinutes(
                    choices[(choices.indexOf(settings.backdropIntervalMinutes) +
                            1) %
                        choices.length],
                  );
                },
              ),
              const SettingsSectionDivider(),
              FocusableSettingsTile(
                leading: Icon(trakt.isConnected ? Icons.link : Icons.link_off),
                title: Text(
                  trakt.isConnected ? 'Trakt connected' : 'Connect Trakt',
                ),
                subtitle: Text(
                  trakt.isConfigured
                      ? 'Choose movie and TV backdrops from Trakt.'
                      : 'Trakt is not configured in this build.',
                ),
                onPressed: trakt.isConnected || !trakt.isConfigured
                    ? null
                    : () async {
                        final connected = await showDialog<bool>(
                          context: context,
                          builder: (_) => const TraktConnectDialog(),
                        );
                        if (connected == true)
                          await settings.setTraktBackdropsEnabled(true);
                      },
              ),
              FocusableSettingsTile(
                title: const Text('Use Trakt backdrops'),
                subtitle: const Text(
                  'Included backdrops are used when offline.',
                ),
                leading: Icon(
                  settings.traktBackdropsEnabled && trakt.isConnected
                      ? Icons.check_box
                      : Icons.check_box_outline_blank,
                ),
                onPressed: !trakt.isConnected
                    ? null
                    : () => settings.setTraktBackdropsEnabled(
                        !settings.traktBackdropsEnabled,
                      ),
              ),
              FocusableSettingsTile(
                title: const Text('Catalogs & lists'),
                leading: const Icon(Icons.video_library_outlined),
                onPressed: !trakt.isConnected
                    ? null
                    : () => Navigator.of(
                        context,
                      ).pushNamed(TraktCatalogsPage.routeName),
              ),
              if (wallpaper.error != null)
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(wallpaper.error!),
                ),
              if (trakt.isConnected)
                FocusableSettingsTile(
                  title: const Text('Disconnect Trakt'),
                  leading: const Icon(Icons.logout),
                  onPressed: () async {
                    await settings.setTraktBackdropsEnabled(false);
                    await trakt.disconnect();
                  },
                ),
              const Padding(
                padding: EdgeInsets.all(12),
                child: Text(
                  'Backdrops provided by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.',
                  style: TextStyle(fontSize: 11, color: Colors.white60),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
