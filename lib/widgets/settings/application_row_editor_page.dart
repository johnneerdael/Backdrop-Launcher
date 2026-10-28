import 'package:flauncher/models/app.dart';
import 'package:flauncher/models/category.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/widgets/settings/app_inventory_page.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flauncher/widgets/settings/settings_toggle.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

Future<String?> askRowName(
  BuildContext context,
  String title, [
  String initial = '',
]) => showDialog<String>(
  context: context,
  builder: (_) => _RowNameDialog(title: title, initial: initial),
);

class _RowNameDialog extends StatefulWidget {
  final String title;
  final String initial;
  const _RowNameDialog({required this.title, required this.initial});
  @override
  State<_RowNameDialog> createState() => _RowNameDialogState();
}

class _RowNameDialogState extends State<_RowNameDialog> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initial,
  );
  void _save() {
    final name = _controller.text.trim();
    if (name.isNotEmpty) Navigator.pop(context, name);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: TextField(
      autofocus: true,
      controller: _controller,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _save(),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      TextButton(onPressed: _save, child: const Text('Save')),
    ],
  );
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class ApplicationRowEditorPage extends StatefulWidget {
  static const routeName = 'application_row';
  final int categoryId;
  const ApplicationRowEditorPage({super.key, required this.categoryId});
  @override
  State<ApplicationRowEditorPage> createState() =>
      _ApplicationRowEditorPageState();
}

class _ApplicationRowEditorPageState extends State<ApplicationRowEditorPage> {
  bool _saving = false;
  Set<String>? _pending;
  Future<void> _toggle(App app, Category row, Set<String> current) async {
    if (_saving) return;
    final next = {...current};
    if (!next.add(app.packageName)) next.remove(app.packageName);
    setState(() {
      _saving = true;
      _pending = next;
    });
    try {
      await context.read<AppsService>().setCategoryApplications(row, next);
    } catch (_) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Unable to update this row. Try again.'),
          ),
        );
    } finally {
      if (mounted)
        setState(() {
          _saving = false;
          _pending = null;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    final service = context.watch<AppsService>();
    final matches = service.categories.where(
      (row) => row.id == widget.categoryId,
    );
    if (matches.isEmpty)
      return const Center(child: Text('This row no longer exists.'));
    final row = matches.first;
    final apps = service.applications;
    final selected =
        _pending ??
        apps
            .where((app) => service.isAppInCategory(app, row))
            .map((app) => app.packageName)
            .toSet();
    final entries = <Widget Function()>[
      () => FocusableSettingsTile(
        autofocus: true,
        title: const Text('Row name'),
        trailing: Flexible(
          child: Text(row.name, overflow: TextOverflow.ellipsis),
        ),
        leading: const Icon(Icons.edit_outlined),
        onPressed: () async {
          final name = await askRowName(context, 'Row name', row.name);
          if (name != null) await service.renameCategory(row, name);
        },
      ),
      () => FocusableSettingsTile(
        title: const Text('App order'),
        trailing: Text(switch (row.sort) {
          CategorySort.manual => 'Manual',
          CategorySort.alphabetical => 'Alphabetical',
          CategorySort.lastUsed => 'Last used',
        }),
        leading: const Icon(Icons.sort),
        onPressed: () => service.setCategorySort(
          row,
          CategorySort.values[(row.sort.index + 1) %
              CategorySort.values.length],
        ),
      ),
      () => FocusableSettingsTile(
        title: const Text('Row size'),
        trailing: Text('${row.rowHeight}'),
        leading: const Icon(Icons.height),
        onPressed: () {
          const sizes = [82, 110, 140];
          service.setCategoryRowHeight(
            row,
            sizes[(sizes.indexOf(row.rowHeight) + 1) % sizes.length],
          );
        },
      ),
      () => FocusableSettingsTile(
        title: const Text('Show on home screen'),
        leading: const Icon(Icons.visibility_outlined),
        trailing: SettingsSwitch(
          value: !row.hidden,
          accentColor: Theme.of(context).colorScheme.primary,
        ),
        onPressed: () => service.setCategoryHidden(row, !row.hidden),
      ),
      () => FocusableSettingsTile(
        title: const Text('Delete row'),
        leading: const Icon(Icons.delete_outline),
        onPressed: () async {
          await service.deleteCategory(row);
          if (context.mounted) Navigator.pop(context);
        },
      ),
    ];
    for (final android in [false, true]) {
      final group = apps.where((app) => app.sideloaded == android).toList();
      if (group.isEmpty) continue;
      entries.add(
        () =>
            SettingsSectionHeader(title: android ? 'Android apps' : 'TV apps'),
      );
      for (final app in group) {
        entries.add(
          () => FocusableSettingsTile(
            key: ValueKey('row_member_${app.packageName}'),
            title: Text(app.name),
            subtitle: app.hidden ? const Text('Hidden on home screen') : null,
            leading: InstalledAppIcon(app: app),
            trailing: Icon(
              selected.contains(app.packageName)
                  ? Icons.check_box
                  : Icons.check_box_outline_blank,
            ),
            onPressed: _saving ? null : () => _toggle(app, row, selected),
          ),
        );
      }
    }
    if (apps.isEmpty)
      entries.add(
        () => const Padding(
          padding: EdgeInsetsDirectional.fromSTEB(24, 14, 56, 13),
          child: Text('No installed apps.'),
        ),
      );
    return Column(
      children: [
        SettingsPageHeader(title: row.name),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.only(bottom: 34),
            itemCount: entries.length,
            itemBuilder: (_, index) => entries[index](),
          ),
        ),
      ],
    );
  }
}
