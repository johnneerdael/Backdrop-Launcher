# Continue Watching motion restoration

Restore the compact carousel transition specified by the owner and raise the
next preview from 10% to 20% opacity. A rightward navigation keeps the outgoing
card full-size as it translates off the left edge. The one-third-size preview
moves left/up, grows and becomes opaque over 320ms, then a new preview enters.
Left navigation retains the earlier full-size translation behavior. Compact
boundaries do not wrap; rapid requests queue adjacent steps and preserve active
item identity across feed replacement. Hero layout stays unchanged.

Use one stationary remote-focus/activation anchor so navigation does not move
focus between animated cards. Retain the recent app-menu focus safeguards.
Render at most three resident card layers and at most two visible cards; reuse
the audited bounded artwork loader at a fixed poster decode size. Read animation
progress only in graphics-layer lambdas, reuse pose objects and stop motion while
Home is hidden or a modal is open. No new dependency or fullscreen bitmap copy.

Validate geometry, opacity, queuing, feed changes and pause semantics in unit
checks; assert intermediate frame bounds and remote/playback/focus behavior in
emulator UI tests. Inspect short optimized captures and keep resource evidence
separate from timing claims. Publish through the existing source/signing gates
and update the TV in place after verification.
