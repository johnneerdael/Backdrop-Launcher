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

import 'package:flutter/material.dart';
import 'settings/settings_theme.dart';
import 'package:flutter/services.dart';
import 'package:flauncher/widgets/focus_keyboard_listener.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/widgets/add_to_category_dialog.dart';
import 'package:flauncher/widgets/side_panel_dialog.dart';
import 'package:flauncher/widgets/app_artwork_picker.dart';
import 'package:provider/provider.dart';
import 'package:flauncher/l10n/app_localizations.dart';

import '../models/app.dart';
import '../models/category.dart';

class ApplicationInfoPanel extends StatefulWidget {
  final Category? category;
  final App application;
  final ImageProvider? applicationIcon;

  const ApplicationInfoPanel({
    required this.category,
    required this.application,
    this.applicationIcon,
  });

  @override
  State<ApplicationInfoPanel> createState() => _ApplicationInfoPanelState();
}

class _ApplicationInfoPanelState extends State<ApplicationInfoPanel> {
  late Future<bool> _hasCustomBannerFuture;

  @override
  void initState() {
    super.initState();
    _hasCustomBannerFuture = context.read<AppsService>().hasCustomBanner(
      widget.application.packageName,
    );
  }

  @override
  Widget build(BuildContext context) {
    AppLocalizations localizations = AppLocalizations.of(context)!;

    return Focus(
      canRequestFocus: false,
      onKeyEvent: (_, event) {
        if (event is KeyRepeatEvent &&
            longPressableKeys.contains(event.logicalKey))
          return KeyEventResult.handled;
        return KeyEventResult.ignored;
      },
      child: SidePanelDialog(
        builder: (context) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SettingsPageHeader(title: widget.application.name),
            Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(24, 14, 56, 13),
              child: Row(
                children: [
                  if (widget.applicationIcon != null)
                    Image(image: widget.applicationIcon!, width: 32, height: 32)
                  else
                    const Icon(Icons.image_not_supported_outlined, size: 24),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.application.packageName,
                          style: Theme.of(context).textTheme.bodySmall,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          'v' + widget.application.version,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  children: [
                    // Add to Category button (First as requested)
                    TextButton(
                      style: SettingsStyle.actionButtonStyle,
                      autofocus: true,
                      child: Row(
                        children: [
                          const Icon(Icons.add_box_outlined),
                          const SizedBox(width: 16),
                          Flexible(
                            child: Text(
                              localizations.addToCategory,
                              style: Theme.of(context).textTheme.bodyMedium,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      onPressed: () async {
                        Navigator.of(
                          context,
                        ).pop(ApplicationInfoPanelResult.none);
                        await SidePanelDialog.show(
                          context: context,
                          builder: (context) =>
                              AddToCategoryDialog(widget.application),
                        );
                      },
                    ),
                    // Reorder button (Second as requested)
                    if (widget.category?.sort == CategorySort.manual)
                      TextButton(
                        style: SettingsStyle.actionButtonStyle,
                        child: Row(
                          children: [
                            const Icon(Icons.open_with),
                            const SizedBox(width: 16),
                            Flexible(
                              child: Text(
                                localizations.reorder,
                                style: Theme.of(context).textTheme.bodyMedium,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        onPressed: () => Navigator.of(
                          context,
                        ).pop(ApplicationInfoPanelResult.reorderApp),
                      ),
                    TextButton(
                      style: SettingsStyle.actionButtonStyle,
                      child: Row(
                        children: [
                          const Icon(Icons.open_in_new),
                          const SizedBox(width: 16),
                          Flexible(
                            child: Text(
                              localizations.open,
                              style: Theme.of(context).textTheme.bodyMedium,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      onPressed: () async {
                        await context.read<AppsService>().launchApp(
                          widget.application,
                        );
                        Navigator.of(
                          context,
                        ).pop(ApplicationInfoPanelResult.none);
                      },
                    ),
                    TextButton(
                      style: SettingsStyle.actionButtonStyle,
                      child: Row(
                        children: [
                          Icon(
                            widget.application.hidden
                                ? Icons.visibility
                                : Icons.visibility_off_outlined,
                          ),
                          const SizedBox(width: 16),
                          Text(
                            widget.application.hidden
                                ? localizations.show
                                : localizations.hide,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                      onPressed: () async {
                        if (widget.application.hidden) {
                          await context.read<AppsService>().showApplication(
                            widget.application,
                          );
                        } else {
                          await context.read<AppsService>().hideApplication(
                            widget.application,
                          );
                        }
                        Navigator.of(
                          context,
                        ).pop(ApplicationInfoPanelResult.none);
                      },
                    ),
                    if (widget.category != null)
                      TextButton(
                        style: SettingsStyle.actionButtonStyle,
                        child: Row(
                          children: [
                            const Icon(Icons.delete_sweep_outlined),
                            const SizedBox(width: 16),
                            Flexible(
                              child: Text(
                                localizations.removeFrom(widget.category!.name),
                                style: Theme.of(context).textTheme.bodyMedium,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                        onPressed: () async {
                          await context.read<AppsService>().removeFromCategory(
                            widget.application,
                            widget.category!,
                          );
                          Navigator.of(
                            context,
                          ).pop(ApplicationInfoPanelResult.none);
                        },
                      ),
                    const SettingsSectionDivider(),
                    FutureBuilder<bool>(
                      future: _hasCustomBannerFuture,
                      builder: (context, snapshot) {
                        final hasCustom = snapshot.data ?? false;
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            TextButton(
                              style: SettingsStyle.actionButtonStyle,
                              child: Row(
                                children: [
                                  const Icon(Icons.image_search),
                                  const SizedBox(width: 16),
                                  Flexible(
                                    child: Text(
                                      localizations.setCustomBanner,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodyMedium,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                              onPressed: () async {
                                try {
                                  final asset = await AppArtworkPicker.show(
                                    context,
                                  );
                                  if (asset != null && context.mounted) {
                                    await context
                                        .read<AppsService>()
                                        .setCustomAppBanner(
                                          widget.application.packageName,
                                          asset,
                                        );
                                    // Refresh the future to reflect the change
                                    setState(() {
                                      _hasCustomBannerFuture = context
                                          .read<AppsService>()
                                          .hasCustomBanner(
                                            widget.application.packageName,
                                          );
                                    });
                                  }
                                } catch (e) {
                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text(
                                          localizations.customBannerSetFailed(
                                            e.toString(),
                                          ),
                                        ),
                                      ),
                                    );
                                  }
                                }
                                if (context.mounted)
                                  Navigator.of(
                                    context,
                                  ).pop(ApplicationInfoPanelResult.none);
                              },
                            ),
                            if (hasCustom)
                              TextButton(
                                style: SettingsStyle.actionButtonStyle,
                                child: Row(
                                  children: [
                                    const Icon(Icons.hide_image_outlined),
                                    const SizedBox(width: 16),
                                    Flexible(
                                      child: Text(
                                        localizations.clearCustomBanner,
                                        style: Theme.of(
                                          context,
                                        ).textTheme.bodyMedium,
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                                onPressed: () async {
                                  try {
                                    await context
                                        .read<AppsService>()
                                        .removeCustomAppBanner(
                                          widget.application.packageName,
                                        );
                                    // Refresh the future to reflect the change
                                    setState(() {
                                      _hasCustomBannerFuture = context
                                          .read<AppsService>()
                                          .hasCustomBanner(
                                            widget.application.packageName,
                                          );
                                    });
                                  } catch (e) {
                                    if (context.mounted) {
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            localizations
                                                .customBannerClearFailed(
                                                  e.toString(),
                                                ),
                                          ),
                                        ),
                                      );
                                    }
                                  }
                                  if (context.mounted)
                                    Navigator.of(
                                      context,
                                    ).pop(ApplicationInfoPanelResult.none);
                                },
                              ),
                          ],
                        );
                      },
                    ),
                    const SettingsSectionDivider(),
                    TextButton(
                      style: SettingsStyle.actionButtonStyle,
                      child: Row(
                        children: [
                          const Icon(Icons.info_outlined),
                          const SizedBox(width: 16),
                          Flexible(
                            child: Text(
                              localizations.appInfo,
                              style: Theme.of(context).textTheme.bodyMedium,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      onPressed: () => context.read<AppsService>().openAppInfo(
                        widget.application,
                      ),
                    ),
                    TextButton(
                      style: SettingsStyle.actionButtonStyle,
                      child: Row(
                        children: [
                          const Icon(Icons.delete_outlined),
                          const SizedBox(width: 16),
                          Flexible(
                            child: Text(
                              localizations.uninstall,
                              style: Theme.of(context).textTheme.bodyMedium,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      onPressed: () async {
                        await context.read<AppsService>().uninstallApp(
                          widget.application,
                        );
                        Navigator.of(
                          context,
                        ).pop(ApplicationInfoPanelResult.none);
                      },
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum ApplicationInfoPanelResult { none, reorderApp }
