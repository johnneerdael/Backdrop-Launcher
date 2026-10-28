import 'package:flauncher/models/category.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flauncher/widgets/focus_keyboard_listener.dart';
import 'package:flauncher/widgets/settings/app_inventory_page.dart';
import 'package:flauncher/widgets/settings/application_row_editor_page.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

class ApplicationsPanelPage extends StatefulWidget {
  static const routeName = 'applications_panel';
  const ApplicationsPanelPage({super.key});
  @override
  State<ApplicationsPanelPage> createState() => _ApplicationsPanelPageState();
}

class _ApplicationsPanelPageState extends State<ApplicationsPanelPage> {
  bool _opening = false;
  int? _moving;
  void _toggleMoving(Category row) {
    setState(() => _moving = _moving == row.id ? null : row.id);
  }

  Future<void> _open(Category row) async {
    if (_opening) return;
    _opening = true;
    if (_moving != null) setState(() => _moving = null);
    try {
      await Navigator.of(
        context,
      ).pushNamed(ApplicationRowEditorPage.routeName, arguments: row.id);
    } finally {
      _opening = false;
    }
  }

  Future<void> _add() async {
    final name = await askRowName(context, 'Add row');
    if (!mounted || name == null) return;
    final id = await context.read<AppsService>().addCategory(name);
    if (mounted && id >= 0)
      Navigator.of(
        context,
      ).pushNamed(ApplicationRowEditorPage.routeName, arguments: id);
  }

  @override
  Widget build(BuildContext context) {
    final service = context.watch<AppsService>();
    final rows = service.categories.toList()
      ..sort((a, b) => a.order.compareTo(b.order));
    return Column(
      children: [
        const SettingsPageHeader(title: 'Applications'),
        Expanded(
          child: SingleChildScrollView(
            child: Column(
              children: [
                for (var index = 0; index < rows.length; index++)
                  Builder(
                    key: ValueKey(rows[index].id),
                    builder: (rowContext) => FocusKeyboardListener(
                      onPressed: (key) {
                        if (longPressableKeys.contains(key)) {
                          _toggleMoving(rows[index]);
                          return KeyEventResult.handled;
                        }
                        if (_moving == rows[index].id &&
                            (key == LogicalKeyboardKey.arrowUp ||
                                key == LogicalKeyboardKey.arrowDown)) {
                          final next =
                              index +
                              (key == LogicalKeyboardKey.arrowUp ? -1 : 1);
                          if (next >= 0 && next < rows.length) {
                            service.moveSection(index, next);
                            WidgetsBinding.instance.addPostFrameCallback((_) {
                              if (!rowContext.mounted ||
                                  _moving != rows[index].id)
                                return;
                              Scrollable.ensureVisible(
                                rowContext,
                                duration: const Duration(milliseconds: 120),
                                alignmentPolicy:
                                    key == LogicalKeyboardKey.arrowUp
                                    ? ScrollPositionAlignmentPolicy
                                          .keepVisibleAtStart
                                    : ScrollPositionAlignmentPolicy
                                          .keepVisibleAtEnd,
                              );
                            });
                          }
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      onLongPress: (_) {
                        _open(rows[index]);
                        return KeyEventResult.handled;
                      },
                      child: FocusableSettingsTile(
                        key: ValueKey(rows[index].id),
                        autofocus: index == 0,
                        title: Text(rows[index].name),
                        subtitle: Text(
                          _moving == rows[index].id
                              ? 'Up/Down to move · Select to finish'
                              : '${rows[index].applications.length} apps · ${rows[index].hidden ? 'Hidden on home screen · ' : ''}Select to move · Hold/Right to edit',
                        ),
                        leading: Icon(
                          _moving == rows[index].id
                              ? Icons.swap_vert
                              : Icons.view_carousel_outlined,
                        ),
                        showChevron: true,
                        onPressed: () => _toggleMoving(rows[index]),
                        onRightPressed: () => _open(rows[index]),
                        onLongPress: () => _open(rows[index]),
                      ),
                    ),
                  ),
                FocusableSettingsTile(
                  autofocus: rows.isEmpty,
                  title: const Text('Add row'),
                  leading: const Icon(Icons.add),
                  onPressed: _add,
                ),
                const SettingsSectionDivider(),
                FocusableSettingsTile(
                  title: const Text('Installed apps'),
                  subtitle: const Text(
                    'Show, hide and manage installed applications',
                  ),
                  leading: const Icon(Icons.apps),
                  showChevron: true,
                  onPressed: () => Navigator.of(
                    context,
                  ).pushNamed(AppInventoryPage.routeName),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
