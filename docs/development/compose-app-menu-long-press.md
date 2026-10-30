# App menu long-press release regression

A long Select hold correctly opened the app-actions pane, but focus then moved
to its first Open action while the remote was still sending repeat DOWN events.
Foundation treated the first repeat arriving at the newly focused row as a new
press. Releasing the original hold therefore activated Open and closed the pane.
The previous card-only test counted the long-click callback but never opened the
real menu or tested repeat events after focus transfer.

NativeSettingsSurface now consumes inherited confirm-key repeat/long-press DOWN
events before combinedClickable records them. Fresh DOWN/UP remains intact;
directional repeats and the original hold timer are unchanged. The filter applies
only to the focused surface, allowing editable descendants to retain key repeats. No polling,
additional image work, dependency or preference migration is introduced.

## Verification

A full LauncherScreen test with native Android KeyEvents reproduced the failure
before the fix: releasing the menu-opening hold launched the app once when zero
launches were expected. The same test passes after the fix and verifies that icon,
reorder and hide actions remain available. It then intentionally activates Open
with a fresh press. Additional cases cover Enter and ordinary short Select.

All 65 production unit tests, release lint and optimized preview/test builds pass.
All nine UI instrumentation tests pass on an isolated Android TV API 36 ARM64
emulator. The TV was not switched or modified for this investigation, following
the owner's instruction to use the emulator. Exact Foundation 1.11.4 keyboard
handling was inspected during independent source review, with no blocking findings.
