# Updates

Open **Settings → About Backdrop Launcher** to see the installed version and build number. Compare it with the release notes on the [project’s Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases).

## Check inside the launcher

If your build includes it, select **Settings → Check for updates**. The launcher contacts the project’s GitHub release feed.

| Result | Meaning and next step |
| --- | --- |
| **No release available yet** | The request succeeded, but there is no stable published release with a usable APK. Check the Releases page later. This message does not claim your app is up to date. |
| **No updates available** | A published usable release was checked and no newer version was found. Review any displayed release notes. |
| **Update available** | Review the version and release notes, then choose **Download** if you want it. |
| **Ready to install** | Choose **Install** to open Android’s installer, then follow Android’s prompts. |
| **Update check failed. Please try again.** | The request or response failed. Check connectivity and retry. |

If Android blocks installation, the launcher may offer a shortcut to allow **Install unknown apps** for Backdrop Launcher. Enable the permission in Android and retry the installation. Device settings and installer labels vary by firmware.

The download/install steps are available only when a real release supplies a compatible APK. The guide’s screenshots do not simulate an available update.

## Update manually

Download the [latest universal APK](https://github.com/johnneerdael/Backdrop-Launcher/releases/latest/download/backdrop-launcher.apk), or choose an APK from the [Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases). The universal asset is named `backdrop-launcher.apk`; releases may also include versioned and architecture-specific APKs.

Open it on the TV and follow the same process as [installation](../get-started/install.md). An Android update with the same application identity and signing key normally keeps settings and rows. An APK installed as a separate app has separate data; read the release’s migration notes before removing an older app.

If the latest-download link is unavailable, check the Releases page: the first branded release may still be pending, or an asset may not have been published yet.

## Why is Check for updates missing?

The updater is enabled at build time and can be omitted from some distributions. Use that distribution’s update mechanism or install an official release APK manually.
