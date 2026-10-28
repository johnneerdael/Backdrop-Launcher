# Frequently asked questions

## Is Backdrop Launcher a streaming service?

Backdrop Launcher is an Android TV home screen. It opens the apps already installed on your device and displays content those apps publish to Android TV. It does not supply films, television access or streaming subscriptions.

## Do I need Trakt?

No. The app includes ten cinematic backdrops and works with your installed apps without a Trakt login. Connecting Trakt adds backdrop discovery through selected movie/show catalogs and public lists.

## Does Trakt sync my watch history or Continue Watching?

The connection is used for backdrops. Continue Watching comes separately from compatible apps through Android TV’s Watch Next feed. [Read how that works](../home/continue-watching.md).

## Why does my streaming app not appear in Continue Watching?

The app must publish resumable programmes to Android TV and provide a usable link. Check the launcher’s permission, app filter and image filter, then refresh. Allowing a provider in Settings cannot make it publish content it does not supply.

## Can I put an app in more than one row?

Yes. Add it to each row you want. Removing it from one row leaves the others alone. **Hide** affects all Home rows; **Uninstall** is an Android action that removes the app from the device.

## Can I use a picture from a USB drive as an app banner?

The current picker offers the artwork bundled in your build. It does not browse external files. **Clear Custom Banner** returns an app to its native artwork.

## Does the violet website theme change my app accent?

No. Choose your accent under **Settings → Interface → Accent Color**. Your saved preference is independent of the guide’s dark/light theme and brand palette.

## Why are row size choices sometimes visually identical?

They set a requested maximum card height. The available viewport, row titles and app-name labels can limit the final height below that maximum. Smaller or larger numbers do not guarantee a visible difference in every layout.

## What is the difference between Show clock and Show screensaver?

**Show clock** switches Home to the launcher’s clock over its backdrop. **Show screensaver** starts the provider selected in Android’s native screensaver settings. The native Backdrop Launcher Clock uses a separate black-background layout. Both use your chosen clock style and date/time formats.

## Why does Back leave the app?

The configured Home Back action requires Android to recognise Backdrop Launcher as the effective default launcher in a release build. Otherwise Back exits. Some TV firmware restricts Home selection; see [troubleshooting](troubleshooting.md#home-or-back-opens-a-different-launcher).

## Does the launcher need to show a splash screen?

It opens directly to Home without an intentional splash or startup delay. Saved artwork is restored locally and Continue Watching refreshes after Home appears. Actual startup behaviour also depends on the TV and Android.

## What works without an internet connection?

Your installed app rows, saved preferences and included backdrops are available locally. Trakt login, online backdrop discovery, public-list fetching and update checks need a connection. An external streaming app has its own network requirements. Previously saved artwork may be reused; cached content is not a guarantee that an external programme can play offline.

## Where is the source code?

On [GitHub](https://github.com/johnneerdael/Backdrop-Launcher). Backdrop Launcher is distributed under **GNU GPL version 3 or later**. See [Credits and licences](credits.md) for its lineage, service attribution and font notices.

## Why do screenshots show different apps or artwork from my TV?

They are real example captures from the same controls, not a promise of installed apps or available programmes. App selection, provider data, saved colours and backdrop results vary. This guide includes only relevant screens and describes conditional service results as such.
