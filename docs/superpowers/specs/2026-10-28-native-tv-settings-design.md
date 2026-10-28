# Native Android TV settings design

User direction: replace the left glass settings surface with the actual native Android TV settings model on the right, matching width, fonts, sizes and colours. The user explicitly authorizes rooted inspection of the TV. This is a visual restyle of existing controls and navigation; the requested reference supplies the design.

## Approach

Restyle the shared Flutter settings components and side-panel shell using measured native values. The existing data providers, nested navigation, row editing/reordering and physical-remote handling remain the implementation. A new native preference framework would duplicate those existing controls and their state contracts; it is unnecessary for this requested appearance match.

Apply the same shell to app actions and Add to row, keeping all activation callbacks and focus restoration. Home retains its user-selected accent, card opacity and artwork. Settings uses the TV's native blue controls; app-artwork previews and the opacity slider explicitly retain the configured launcher accent. About retains the supplied identity mark.

## Seen reference

Actual TV activity: com.android.tv.settings/.MainSettings; Android9 Theme.Settings.Transparent. Full evidence is private /tmp/re/backdrop-native-settings/native-style-evidence.json. Live hierarchy and standalone rooted Paint/Switch probes corroborate resource values; native APKs, fonts and private screen data are not copied into the repository.

- Right pane360dp, square corners, native pane elevation6dp.
- Header91dp total; 27dp top offset then64dp title box; margins start24/end56dp. Header background#1B1C1E, white20sp Roboto Medium500.
- Body#202124. Rectangular full-width focused row#2E2F32, no card border or scale.
- Primary14sp sans-serif/Roboto400,#EEEEEE, native padded line19dp. Summary12sp sans-serif-condensed/RobotoCondensed400,#B3EEEEEE, line16.25dp,max4lines.
- Text top14/bottom13dp with2dp title-bottom margin. Single-line row48dp; typical two-line64.25dp. Horizontal padding start24/end56dp; icon24dp plus16dp gap.
- Root category label38dp, Roboto Medium12sp, native blue#51AAFF. Outside dim0.8; use one dim layer.
- Switch allocation48x27.25dp, track14dp/radius7, native theme tint blue/on and white/off at0.3alpha. Actual Switch probe: thumb bounds off[0,0,109,109]px/on[83,0,192,109]px atdensity4; opticalpadding13px; track bounds[26,13,166,96]px with4dp horizontal shape insets.
- Radio allocation32dp; ring20dp/stroke2dp/dot10dp;16dp gap. Seekbar track2dp, resting thumb12dp/pressed18dp.

## Constraints and validation

No startup work, remote focus redesign, new dependencies or preference migration. Use Android's system font families. Preserve Select/hold/Right row editing and Select/Back move completion, modal focus isolation and pane closure before system settings. Verify geometry and accent isolation in widget tests, existing222 tests, analysis, optimized production-key build and live TV comparisons. Update public guide/marketing screenshots for changed surfaces after visual acceptance. Continue existing signed release automation; v1.3.1(code4128) is already published for readable update notes.
