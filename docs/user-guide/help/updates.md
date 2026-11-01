# Updates

Open **Settings → About Backdrop Launcher** to see the installed version and build number. Compare it with the release notes on the [project’s Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases).

## Automatic updates

Backdrop keeps itself up to date. Automatic updates are on unless you turn them off under **Settings → System → Install updates automatically**, and builds without the updater do not show this switch.

Every six hours, Backdrop asks the project's GitHub release feed whether there is a new stable release. It downloads a new release in the background and checks it before use: the version, the published checksum and the signing certificate must all match. A download that fails these checks is thrown away.

The update installs while Home is on screen, never while you are watching something. Installing restarts Backdrop, so the screen returns to your default Home. If Backdrop is your default Home, it opens again by itself.

### The first update

Android must allow Backdrop to install apps once. The first time an update is ready, Android may say your TV isn't allowed to install unknown apps from this source:

1. Select **Settings** in that message.
2. Turn on **Backdrop Launcher**.
3. Press Back until you return to Backdrop.

The waiting update then installs by itself. You can also turn this on ahead of time in Android's **Install unknown apps** settings.

With that allowed, updates on Android 14 and later install without asking. Android 12 and 13 can ask for the first update, and older versions ask for every update.

### If you turn an update down

Choose **Cancel** on Android's prompt and the update is kept on your TV. Backdrop will not ask about that version again. A newer release is offered as usual and replaces the kept one.

To install a kept update later, open **Settings** and select **Install update {version}**. The same action appears on the Updates page.

If you selected Cancel on the message about installing unknown apps, you do not need to do anything else in Backdrop: turn on the permission in Android and the kept update installs the next time Home opens.

## Check inside the launcher

To check right away, select **Settings → Check for updates**. This works with automatic updates on or off.

| Result | Meaning and next step |
| --- | --- |
| **No newer published release is available** | The check succeeded and your version is current, or no usable release has been published. |
| **Latest release** | A newer release is available. Review the release notes, then choose **Download {version}**. |
| **Install update {version}** | The update is downloaded and checked. Select it to install. |
| An error message | The check or download failed. Check your connection and try again. |

If a check fails, Backdrop tries again automatically after about half an hour.

## Update manually

Download the [latest universal APK](https://github.com/johnneerdael/Backdrop-Launcher/releases/latest/download/backdrop-launcher.apk), or choose an APK from the [Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases). The universal asset is named `backdrop-launcher.apk`; releases may also include versioned and architecture-specific APKs.

Open it on the TV and follow the same process as [installation](../get-started/install.md). An Android update with the same application identity and signing key normally keeps settings and rows. An APK installed as a separate app has separate data; read the release’s migration notes before removing an older app.

If the latest-download link is unavailable, check the Releases page: the first branded release may still be pending, or an asset may not have been published yet.

## Why is Check for updates missing?

The updater is enabled at build time and can be omitted from some distributions. Use that distribution’s update mechanism or install an official release APK manually.
