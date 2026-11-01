package nl.neerdael.backdrop_launcher.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** Android installs without asking where it allows an app to update itself, and asks otherwise. */
internal class UpdateInstaller(private val context: Context) {
    val resultAction = "${context.packageName}.UPDATE_RESULT"

    /** Blocking. The outcome arrives as a resultAction broadcast. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                // The installer fills in the outcome, so this one has to stay mutable.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(context, id, Intent(resultAction).setPackage(context.packageName), flags).intentSender)
            }
        } catch (failure: Exception) {
            installer.abandonSession(id)
            throw failure
        }
    }
}
