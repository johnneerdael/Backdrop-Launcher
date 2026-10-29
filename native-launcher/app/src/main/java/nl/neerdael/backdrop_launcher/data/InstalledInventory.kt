package nl.neerdael.backdrop_launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

internal data class InstalledApp(val packageName: String, val label: String, val version: String, val androidApp: Boolean)

internal interface InstalledInventory {
    fun enumerate(): List<InstalledApp>
    /** Return true for packages that exist but temporarily have no enabled launch activity. */
    fun exists(packageName: String): Boolean
}

@Suppress("DEPRECATION")
internal class AndroidInstalledInventory(private val context: Context) : InstalledInventory {
    private val pm = context.packageManager
    override fun enumerate(): List<InstalledApp> {
        val found = linkedMapOf<String, InstalledApp>()
        for ((category, android) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to false, Intent.CATEGORY_LAUNCHER to true)) {
            for (resolved in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val info = resolved.activityInfo ?: continue
                if (info.packageName == context.packageName || found.containsKey(info.packageName)) continue
                val version = try { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }
                    catch (_: PackageManager.NameNotFoundException) { "" }
                found[info.packageName] = InstalledApp(info.packageName, info.loadLabel(pm).toString(), version, android)
            }
        }
        if (found.keys.none { it == "com.android.tv.settings" || it == "com.android.settings" }) {
            val info = Intent(Settings.ACTION_SETTINGS).resolveActivityInfo(pm, 0)
            if (info != null && info.packageName != context.packageName) {
                val version = try { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }
                    catch (_: PackageManager.NameNotFoundException) { "" }
                found[info.packageName] = InstalledApp(info.packageName, info.loadLabel(pm).toString(), version, false)
            }
        }
        return found.values.toList()
    }
    override fun exists(packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}
