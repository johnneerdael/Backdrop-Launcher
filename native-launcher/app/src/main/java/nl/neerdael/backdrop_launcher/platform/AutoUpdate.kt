package nl.neerdael.backdrop_launcher.platform

import nl.neerdael.backdrop_launcher.core.AUTO_UPDATE_KEY
import nl.neerdael.backdrop_launcher.core.PendingUpdate
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import nl.neerdael.backdrop_launcher.core.UpdateAccess

/** Keeps a verified update ready, and offers each version once: a rejected one waits in Settings. */
internal class AutoUpdate(private val prefs: PreferenceAccess, private val updates: UpdateAccess,
    private val now: () -> Long = System::currentTimeMillis) {
    /** Blocking. The update to install now, if there is one that has not been offered yet. */
    fun next(canInstall: Boolean = true): PendingUpdate? {
        if (!updates.enabled || !prefs.bool(AUTO_UPDATE_KEY, true)) return null
        // A wait longer than the interval means the clock moved back since it was set.
        if (prefs.long(NEXT_CHECK) - now() !in 1..CHECK_MILLIS) fetch()
        // Android refuses without the install permission, which is not the user turning this update down.
        return runCatching { updates.pending() }.getOrNull()?.takeIf { it.info.code != prefs.long(OFFERED) || (canInstall && prefs.bool(BLOCKED)) }
    }

    fun offered(update: PendingUpdate, canInstall: Boolean = true) {
        prefs.putLong(OFFERED, update.info.code)
        prefs.putBool(BLOCKED, !canInstall)
    }

    private fun fetch() {
        val wait = try {
            updates.check()?.takeIf { it.code != updates.pending()?.info?.code }?.let(updates::download)
            CHECK_MILLIS
        } catch (_: Exception) { RETRY_MILLIS }
        prefs.putLong(NEXT_CHECK, now() + wait)
    }

    companion object {
        const val CHECK_MILLIS = 6 * 60 * 60 * 1000L
        const val RETRY_MILLIS = 30 * 60 * 1000L
        private const val NEXT_CHECK = "native.update_next_check"
        private const val OFFERED = "native.update_offered_code"
        private const val BLOCKED = "native.update_offer_blocked"
    }
}
