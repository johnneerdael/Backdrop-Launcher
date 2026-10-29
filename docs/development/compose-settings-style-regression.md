# Compose settings style regression

The 1.4.0 migration replaced the previously measured native settings design with
TV Material card defaults: a 540dp panel, inset rounded rows, larger type and
border/scale focus effects. The reference remains
`docs/superpowers/specs/2026-10-28-native-tv-settings-design.md` and the original
`lib/widgets/settings/settings_theme.dart` / `settings_toggle.dart` implementation.

The correction ports that design into the shared Compose settings components:
360dp right pane, 91dp header, native sans-serif/condensed 20/14/12sp typography,
opaque measured colours, flat full-width focus highlights, blue native controls,
6dp pane elevation and one 80% dim layer. Home retains its own typography, accent
and card styling. Secondary prompts use matching colours/type, while long values
are bounded to keep labels readable. Membership rows retain their artwork beside
checkboxes. All controls remain one remote focus/activation target per row.

## Verification

- The new device geometry test failed on the old implementation with actual
  width 540dp versus expected 360dp before the fix.
- 65 production unit tests and release lint pass. Five TV instrumentation checks
  pass, covering width/pixels/flat focus, isolated native typography, blue controls
  and long values, held/short Select and mutation input blocking.
- Captured the actual 1.4.0 panel and the corrected preview on the same TV and
  visually checked the root panel. Raw device captures remain local.
- No new dependency, preference migration, startup task or image-cache expansion
  is introduced. The shared Home button surface is unchanged.
