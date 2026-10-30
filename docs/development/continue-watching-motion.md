# Compact Continue Watching motion

The Compose port rendered the selected poster and next preview at fixed positions,
so changing the index replaced them immediately. `WatchNextCarousel` now keeps a
stationary remote-focus target and animates the surrounding poster layers over
320ms with the earlier cubic easing. The next preview uses 20% opacity.

Right navigation translates the outgoing card left at full size. The existing
preview moves left/up, grows from one-third size and fades to full opacity. A
following preview enters after the outgoing card has left. Left navigation uses
full-size translation. Adjacent steps queue without restarting an in-flight
transition; feed replacement preserves item identity. Hiding Home or opening a
menu settles the current step and discards subsequent queued motion.

Animation progress is observed only by graphics layers. Poster decode dimensions
remain fixed while growing, using the existing bounded artwork cache and loader.
There are at most three resident cards and two visible cards; no additional
fullscreen artwork is introduced. Emulator graphics accounting is not comparable
to the physical TV, so this change makes no measured CPU or graphics-memory claim.

Validation uses eight navigation/geometry unit checks and four Compose UI checks
covering intermediate frame bounds, focus, activation, rapid reversal, feed
replacement and pause. Short optimized emulator captures with real JPEG posters
also show the full-size outgoing card and growing incoming preview.
