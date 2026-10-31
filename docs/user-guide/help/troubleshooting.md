# Troubleshooting

Start with the section that matches what you see. Launcher settings are separate from Android’s system settings and from the apps you launch.

## An app or row is missing

1. Open **Settings → Applications**. Hidden and empty rows remain here.
2. Edit the row with **Right** or hold Select. Enable **Show on home screen** and check at least one app.
3. Open **Installed apps**, find the app and select **Show** if it is hidden.
4. Return Home. Only enabled rows with at least one visible app are shown.

An app can be hidden globally while remaining checked in a row. Deleting a row and uninstalling an app are separate actions. [Manage apps](../home/apps.md) explains how to recover a hidden app.

## I cannot move a card or row

For a card, set its row’s **App order** to **Manual**, then hold Select on the card and choose **Reorder**. Use **Left/Right**, then Select or Back to finish. Up/Down does not transfer an app to another row.

For a row, open **Applications**, focus the row and press Select briefly. Use **Up/Down** and Select to finish. Right or a long Select edits the row instead.

If changing **App size** stops making a difference, the row has run out of height. Row titles, app-name labels, Continue Watching and the screen height all limit how tall app cards can get.

## Continue Watching is missing

1. Enable **Settings → Interface → Watch Next → Show Watch Next Section**.
2. Open a compatible TV app, start a programme and leave something resumable. The provider must publish items to Android TV’s Watch Next feed.
3. Return to Watch Next settings and select **Refresh content**.
4. Check **Filter apps**: the provider’s switch must be on. Temporarily turn **Hide programs without image** off if it may be excluding your items.
5. If **Watch Next permission required** appears, use **Grant permission**, complete Android’s prompt, then **Check again**.

No eligible programmes means no Home area, even when the feature is enabled. Trakt login does not populate it.

Some firmware does not expose a working TV-listings permission prompt. This requires device-specific setup; the [repository’s compatibility notes](https://github.com/johnneerdael/Backdrop-Launcher/blob/main/docs/wuplay-compatibility.md) contain the technical background. Include your TV model and Android version when asking for help.

??? info "Advanced: grant TV-listings access with ADB"
    If ADB is already configured and authorised for your TV, a release installation uses this command:

    ```sh
    adb shell pm grant nl.neerdael.backdrop_launcher android.permission.READ_TV_LISTINGS
    ```

    Return to the launcher and use **Check again** or **Refresh content**. A debug build uses `nl.neerdael.backdrop_launcher.debug`. This grants feed access; a compatible app must still publish programmes.

If a programme opens its app but does not resume, check the provider app itself. Backdrop Launcher follows the supplied link; it cannot guarantee every app’s playback behaviour.

## Backdrop or Trakt problems

| What you see | What to try |
| --- | --- |
| **Trakt is not configured in this build** | Use included artwork or an APK configured for Trakt. This is a build setting, not an account password problem. |
| **Unable to connect to Trakt. Check your connection and try again.** | Check Android’s connection, then start Connect Trakt again. |
| **Waiting for Trakt. Check your connection.** | Leave the dialog open while checking connectivity and completing the approval on your phone. |
| **Connection was declined.** | Start a new request if you want to connect and approve it in Trakt. |
| **This code expired** or **This code is no longer valid** | Start Connect Trakt again and use the newly displayed code. |
| **Select at least one catalog or list** | Open Catalogs & lists and enable a source. |
| **Select a catalog or list with movies or shows** | Choose a source containing supported media. |
| **No backdrops found in the selected catalogs** | Try another catalog or list; not every title has usable images. |
| **Trakt backdrops are unavailable. Showing included backdrops.** | Included artwork is the fallback. Check connectivity and try again later. |
| **Unable to fetch lists. Check your Trakt connection.** | Check the connection, then retry Fetch popular Trakt lists. |

If changing the backdrop seems to have no effect, turn off Watch Next’s **Immersive mode**. Otherwise its programme image can be the visible background.

To use included artwork temporarily, turn **Use Trakt backdrops** off. **Disconnect Trakt** acts immediately and is unnecessary for this switch.

## Home or Back opens a different launcher

Choose Backdrop Launcher as Android’s **default Home app** if the TV allows it. A saved **Back button action** inside Backdrop Launcher does not make it Android’s default.

Some firmware restricts Home selection or gives its supplied launcher priority. You can still open Backdrop Launcher from the installed app list. Record the TV model and firmware when reporting this behaviour; a launcher setting cannot override every vendor’s Home policy.

## Clock and screensaver problems

**Show clock** uses the launcher’s clock view; **Show screensaver** asks Android to start the provider selected in Android’s screensaver settings. Make sure the provider you want is selected, then try Android’s **Start now** or preview.

If the clock style changed but the wrong view appears, recheck **System → Screensaver Clock Style** and the native provider. A shared style does not mean the launcher clock and native screensaver have the same background.

If Back leaves the app, confirm the [default Home app](#home-or-back-opens-a-different-launcher). If Android’s settings fail while selecting an older launcher’s clock provider, update to the current release and select **Backdrop Launcher Clock** again.

## A custom image is wrong or will not apply

Use **Clear Custom Banner** to restore the app’s default image, then reopen **Set Custom Banner** and choose the intended picture. The picker uses bundled images without text labels. Check the image itself before selecting it.

If you see **Failed to set banner** or **Failed to clear banner**, retry once. Include the error text and launcher version in a report if it continues. **No bundled app artwork** means that build does not contain selectable images.

## Text or focus is hard to see

Increase **Appearance → App icon background** or **Focus border thickness**, and keep **Miscellaneous → Show focus borders** on. A bright photographic scene can need a different card background than a dark one. Turning borders off also disables their animation.

The status bar reappears when focused even if automatic hiding is on. Date and Time are independently controlled under **Interface → Status bar**.

## An update will not download or install

Check the [Releases page](https://github.com/johnneerdael/Backdrop-Launcher/releases) for a published APK. **No release available yet** means a successful check found none; a failed request has a different message.

For installer problems, allow the app opening the APK to install unknown apps. Android also requires an update to have the expected identity and signing key. Use the intended release APK and consult its notes before uninstalling anything, because uninstalling removes that app’s data.

## Report a problem

Use the project’s [issue tracker](https://github.com/johnneerdael/Backdrop-Launcher/issues) and include:

- Version and build from **About Backdrop Launcher**.
- TV model, Android version and firmware if known.
- The steps, expected result and actual result.
- Relevant provider app/version for a Continue Watching problem.
- A screenshot if it explains the issue, with personal information removed.

Leave out Trakt device-login codes, QR codes and account tokens. A screenshot of a connected settings page is usually more useful than a pending login screen.
