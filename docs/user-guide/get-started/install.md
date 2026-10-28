# Install Backdrop Launcher

Backdrop Launcher is an Android TV app distributed as an APK. Download the [latest universal APK](https://github.com/johnneerdael/Backdrop-Launcher/releases/latest/download/backdrop-launcher.apk), or use the project’s [GitHub Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases) for published downloads and release notes. A source-code ZIP is not an installable app.

## Install the APK

1. Download the APK from a published release and transfer it to your TV using your preferred file-transfer tool, or download it directly on the TV.
2. Open the APK with the TV’s file manager or download app.
3. If Android asks, allow that file manager or download app to **install unknown apps**. Android controls this permission; the exact menu depends on the TV.
4. Confirm **Install**, then open **Backdrop Launcher** from your app list.

If no release APK is listed, there is no published installable release yet. See [Updates](../help/updates.md) for how that state is shown in the app. Developers can use the [repository’s build instructions](https://github.com/johnneerdael/Backdrop-Launcher/blob/main/docs/development/building.md).

## Choose your Home app

You can launch Backdrop Launcher from the TV’s existing app list before making it the default Home app.

To make the remote’s **Home** button return to Backdrop Launcher, use Android’s **Default apps → Home app** setting if your firmware provides it. On some TVs Android instead asks which launcher to use when Home is pressed; choose **Backdrop Launcher** and the persistent option, often **Always**.

Firmware can restrict the Home app or prefer its supplied launcher. If Home keeps opening the original screen, see [Home and Back open a different launcher](../help/troubleshooting.md#home-or-back-opens-a-different-launcher). The Back action you select inside Backdrop Launcher takes effect on Home only when Android recognises it as the effective default launcher.

## Moving from an earlier launcher

Backdrop Launcher uses the Android application ID `nl.neerdael.backdrop_launcher`. This is a new installation identity, so an earlier Media Launcher installation keeps its own settings and rows. Recreate your preferred setup in Backdrop Launcher and read the release’s migration notes before removing the older app.

## Next

[Learn the remote controls](remote.md), then [create your first app row](../home/rows.md).
