import 'package:flauncher/providers/apps_service.dart';
import 'package:flutter/material.dart';
import 'settings/settings_theme.dart';
import 'settings/focusable_settings_tile.dart';
import 'package:provider/provider.dart';
import 'package:flauncher/l10n/app_localizations.dart';

import '../models/app.dart';
import '../models/category.dart';
import 'package:flauncher/widgets/side_panel_dialog.dart';

class AddToCategoryDialog extends StatelessWidget {
  final App selectedApplication;

  AddToCategoryDialog(this.selectedApplication);

  @override
  Widget build(BuildContext context) => Selector<AppsService, List<Category>>(
    selector: (_, appsService) => appsService.categories
        .where(
          (category) => !category.applications.any(
            (application) =>
                application.packageName == selectedApplication.packageName,
          ),
        )
        .toList(),
    builder: (context, categories, _) {
      AppLocalizations localizations = AppLocalizations.of(context)!;

      return SidePanelDialog(
        builder: (context) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            SettingsPageHeader(title: localizations.withEllipsisAddTo),
            Expanded(
              child: ListView.builder(
                itemCount: categories.length,
                itemBuilder: (context, index) {
                  final category = categories[index];
                  return FocusableSettingsTile(
                    autofocus: index == 0,
                    title: Text(category.name),
                    onPressed: () async {
                      await context.read<AppsService>().addToCategory(
                        selectedApplication,
                        category,
                      );
                      if (context.mounted) Navigator.of(context).pop();
                    },
                  );
                },
              ),
            ),
          ],
        ),
      );
    },
  );
}
