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

## Original-window routing

The packaged-APK check exposed a second routing case: Android could still send
repeat DOWN and UP to the original Home window while the app dialog took focus.
Those events activated the background Settings toolbar and replaced the app menu
with general Settings. Temporary diagnostic logging confirmed APP_MENU followed
by a repeat still reaching MainActivity and TOOLBAR_CLICK on release; the probes
were removed before committing.

Home now consumes input while a settings modal exists, and background focus
callbacks cannot overwrite the selected app during the transition. Dialog input
continues through its separate window. A new original-window instrumentation case
failed before this guard and passes afterward, including app-focus restoration.
All ten emulator UI tests, 65 unit tests, release lint and optimized builds pass.
The actual optimized APK also retained all app actions after a shell-injected
1500ms D-pad Center hold and release. The physical TV remained untouched.

## Continue Watching enabled

Explicit resume fixtures now cover compact and hero layouts with continuation
keys sent to either the Home window or the app dialog. Playback calls are counted
separately from app launches, and every hold must leave both at zero. Intentional
Open and ordinary short Select must still launch only the selected app.

The compact original-window case reproduces the reported autoplay with the Home
implementation from v1.4.1: expected zero playback calls, observed one. The current
1.4.3 implementation passes all four resume-enabled cases. All 14 emulator UI
checks pass; these use a fake playback action and do not start third-party media.
The follow-up changed tests and documentation only; no new production fix was
required. Installation version and exact public APK hash were checked separately
when the owner subsequently authorized the TV update.

## Focus ownership and real Back

The playback checks did not cover the complete Back round trip. In 1.4.3,
`canFocus` was set false on the app card as soon as its menu opened. That removed
the origin from Home's focus tree and allowed Continue Watching to become the
background focus target. New compact/hero tests failed before Back because that
button was already focused.

The origin card now remains focusable within its active row; the separate dialog
window owns modal focus. Confirm-key UP (normal or Android-cancelled) is allowed
to clear the original control's pressed state. Every Home action is guarded while
a modal exists, while other background input remains blocked. This avoids the
stuck first Select observed when focusability was restored but releases were still
consumed.

The full 19-case emulator suite passes, including actual global Back, compact/hero
and both routing paths, the first short Select after dismissal, and exact
non-first-icon package identity. All 65 unit tests and release lint pass. The
optimized APK was also exercised with a real emulator TV-provider resume fixture:
1500ms hold→app actions→Back returned to the same app icon while Continue Watching
remained visible; the next short Select launched that app normally. No physical
TV gesture replay or third-party playback was used for this validation.
