import 'package:flauncher/l10n/app_localizations.dart';
import 'package:flauncher/providers/settings_service.dart';
import 'package:flauncher/widgets/settings/focus_border_thickness_page.dart';
import 'package:flauncher/widgets/settings/focusable_settings_tile.dart';
import 'package:flauncher/widgets/settings/settings_theme.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class AppearancePanelPage extends StatelessWidget {
  static const String routeName = "appearance_panel";

  const AppearancePanelPage({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final settingsService = Provider.of<SettingsService>(context);

    return Column(
      children: [
        SettingsPageHeader(title: localizations.appearanceSettings),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              FocusableSettingsTile(
                autofocus: true,
                showChevron: true,
                leading: const Icon(Icons.border_style),
                title: Text(
                  localizations.focusBorderThickness,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                subtitle: Text(
                  _thicknessLabel(
                    localizations,
                    settingsService.focusBorderThickness,
                  ),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                onPressed: () => Navigator.of(
                  context,
                ).pushNamed(FocusBorderThicknessPage.routeName),
              ),
              const SettingsSectionDivider(),
              const _AppIconOpacitySlider(),
            ],
          ),
        ),
      ],
    );
  }

  String _thicknessLabel(AppLocalizations localizations, double thickness) {
    if ((thickness - FOCUS_BORDER_THIN).abs() < 0.05)
      return localizations.focusBorderThin;
    if ((thickness - FOCUS_BORDER_DEFAULT).abs() < 0.05)
      return localizations.focusBorderDefault;
    if ((thickness - FOCUS_BORDER_MEDIUM).abs() < 0.05)
      return localizations.focusBorderMedium;
    if ((thickness - FOCUS_BORDER_BOLD).abs() < 0.05)
      return localizations.focusBorderBold;
    return "${thickness.toStringAsFixed(1)} dp";
  }
}

class _AppIconOpacitySlider extends StatefulWidget {
  const _AppIconOpacitySlider();

  @override
  State<_AppIconOpacitySlider> createState() => _AppIconOpacitySliderState();
}

class _AppIconOpacitySliderState extends State<_AppIconOpacitySlider> {
  final _focusNode = FocusNode(debugLabel: 'App icon background opacity');

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final percent = (settings.appIconBackgroundOpacity * 100).round();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'App icon background · $percent%',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          Slider(
            focusNode: _focusNode,
            value: settings.appIconBackgroundOpacity,
            divisions: 10,
            label: '$percent%',
            semanticFormatterCallback: (value) => '${(value * 100).round()}%',
            onChanged: settings.setAppIconBackgroundOpacity,
          ),
        ],
      ),
    );
  }
}
