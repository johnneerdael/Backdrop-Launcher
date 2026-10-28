/*
 * FLauncher
 * Copyright (C) 2024 LeanBitLab
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

import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:flauncher/l10n/app_localizations.dart';
import '../../providers/settings_service.dart';

class ScreensaverClockStylePage extends StatelessWidget {
  static const String routeName = "screensaver_clock_style_panel";

  const ScreensaverClockStylePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Selector<SettingsService, String>(
      selector: (_, settingsService) => settingsService.screensaverClockStyle,
      builder: (context, currentStyle, _) {
        final settingsService = context.read<SettingsService>();
        final localizations = AppLocalizations.of(context)!;

        return Column(
          children: [
            SettingsPageHeader(
              title: AppLocalizations.of(context)!.screensaverClockStyle,
            ),
            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  children: [
                    _StyleRadioTile(
                      title: localizations.clockStyleMinimal,
                      subtitle: localizations.clockStyleMinimalDescription,
                      value: 'minimal',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'minimal',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStyleBold,
                      subtitle: localizations.clockStyleBoldDescription,
                      value: 'bold',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'bold',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStyleRetro,
                      subtitle: localizations.clockStyleRetroDescription,
                      value: 'retro',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'retro',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStyleElegant,
                      subtitle: localizations.clockStyleElegantDescription,
                      value: 'elegant',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'elegant',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStyleNeon,
                      subtitle: localizations.clockStyleNeonDescription,
                      value: 'neon',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'neon',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStylePixel,
                      subtitle: localizations.clockStylePixelDescription,
                      value: 'pixel',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'pixel',
                    ),
                    _StyleRadioTile(
                      title: localizations.clockStyleDigital,
                      subtitle: localizations.clockStyleDigitalDescription,
                      value: 'digital',
                      groupValue: currentStyle,
                      onChanged: (value) =>
                          settingsService.setScreensaverClockStyle(value!),
                      autofocus: currentStyle == 'digital',
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _StyleRadioTile extends StatelessWidget {
  final String title;
  final String subtitle;
  final String value;
  final String groupValue;
  final ValueChanged<String?> onChanged;
  final bool autofocus;

  const _StyleRadioTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.groupValue,
    required this.onChanged,
    this.autofocus = false,
  });

  @override
  Widget build(BuildContext context) {
    final isSelected = value == groupValue;
    return FocusableSettingsTile(
      autofocus: autofocus,
      frameLeading: false,
      leading: Icon(
        isSelected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
      ),
      title: Text(title),
      subtitle: Text(subtitle),
      onPressed: () => onChanged(value),
      onFocusChange: (focused) {
        if (focused) {
          Scrollable.ensureVisible(
            context,
            alignment: 0.5,
            duration: const Duration(milliseconds: 150),
            curve: Curves.easeOutCubic,
          );
        }
      },
    );
  }
}
