import 'package:flauncher/providers/trakt_service.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class TraktCatalogsPage extends StatefulWidget {
  static const routeName = 'trakt_catalogs';
  const TraktCatalogsPage({super.key});
  @override
  State<TraktCatalogsPage> createState() => _TraktCatalogsPageState();
}

class _TraktCatalogsPageState extends State<TraktCatalogsPage> {
  bool _loading = false;
  String? _error;

  Future<void> _fetch() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await context.read<TraktService>().fetchPopularLists();
    } catch (_) {
      if (mounted)
        setState(
          () => _error = 'Unable to fetch lists. Check your Trakt connection.',
        );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final trakt = context.watch<TraktService>();
    final selected = trakt.selectedCatalogs.toSet();
    Widget option(String key, String title, [String? subtitle]) =>
        FocusableSettingsTile(
          autofocus: key == TraktService.builtInCatalogs.keys.first,
          title: Text(title),
          subtitle: subtitle == null ? null : Text(subtitle),
          leading: Icon(
            selected.contains(key)
                ? Icons.check_box
                : Icons.check_box_outline_blank,
          ),
          onPressed: () {
            if (!selected.add(key)) selected.remove(key);
            trakt.setSelectedCatalogs(selected.toList());
          },
        );
    return Column(
      children: [
        const SettingsPageHeader(title: 'Trakt catalogs & lists'),
        Expanded(
          child: ListView(
            children: [
              for (final entry in TraktService.builtInCatalogs.entries)
                option(entry.key, entry.value),
              const SettingsSectionDivider(),
              FocusableSettingsTile(
                title: Text(
                  _loading ? 'Fetching lists…' : 'Fetch popular Trakt lists',
                ),
                leading: const Icon(Icons.refresh),
                onPressed: _loading || !trakt.isConnected ? null : _fetch,
              ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(_error!),
                ),
              for (final list in trakt.popularLists)
                option(
                  list.key,
                  list.title,
                  '${list.owner} · ${list.itemCount} items',
                ),
              if (selected.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(12),
                  child: Text(
                    'Select at least one catalog or list to use Trakt backdrops.',
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
