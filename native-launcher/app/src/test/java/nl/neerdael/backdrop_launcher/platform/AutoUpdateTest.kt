package nl.neerdael.backdrop_launcher.platform

import nl.neerdael.backdrop_launcher.core.AUTO_UPDATE_KEY
import nl.neerdael.backdrop_launcher.core.PendingUpdate
import nl.neerdael.backdrop_launcher.core.UpdateAccess
import nl.neerdael.backdrop_launcher.core.UpdateInfo
import nl.neerdael.backdrop_launcher.media.MediaPrefs
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class AutoUpdateTest {
    private val prefs = MediaPrefs()
    private var clock = 1_000_000_000L
    private fun info(code: Long) = UpdateInfo("1.0.$code", code, "https://example.test/$code", "Notes")

    private inner class Updates(override var enabled: Boolean = true) : UpdateAccess {
        var latest: UpdateInfo? = info(5)
        var ready: PendingUpdate? = null
        var failure: Exception? = null
        var checks = 0
        val downloads = mutableListOf<Long>()
        override fun check(): UpdateInfo? { checks++; failure?.let { throw it }; return latest }
        override fun download(update: UpdateInfo): File {
            downloads += update.code
            return File("update-${update.code}.apk").also { ready = PendingUpdate(update, it) }
        }
        override fun pending() = ready
    }
    private fun auto(updates: UpdateAccess) = AutoUpdate(prefs, updates) { clock }

    @Test fun onByDefaultItDownloadsANewReleaseAndOffersIt() {
        val updates = Updates()
        assertEquals(5L, auto(updates).next()?.info?.code)
        assertEquals(listOf(5L), updates.downloads)
    }

    @Test fun turnedOffItLeavesGitHubAlone() {
        prefs.putBool(AUTO_UPDATE_KEY, false)
        val updates = Updates()
        assertNull(auto(updates).next())
        assertEquals(0, updates.checks)
    }

    @Test fun turnedOffItDoesNotOfferAnUpdateAlreadyDownloaded() {
        val updates = Updates().apply { ready = PendingUpdate(info(5), File("a.apk")) }
        prefs.putBool(AUTO_UPDATE_KEY, false)
        assertNull(auto(updates).next())
    }

    @Test fun buildsWithoutTheUpdaterNeverCheck() {
        val updates = Updates(enabled = false)
        assertNull(auto(updates).next())
        assertEquals(0, updates.checks)
    }

    @Test fun nothingNewMeansNothingToOffer() {
        val updates = Updates().apply { latest = null }
        assertNull(auto(updates).next())
        assertTrue(updates.downloads.isEmpty())
    }

    @Test fun gitHubIsAskedOnlyOncePerInterval() {
        val updates = Updates().apply { latest = null }
        val auto = auto(updates)
        auto.next()
        clock += AutoUpdate.CHECK_MILLIS - 1
        auto.next()
        assertEquals(1, updates.checks)
        clock += 1
        auto.next()
        assertEquals(2, updates.checks)
    }

    @Test fun aClockThatJumpedBackCannotStopChecks() {
        val updates = Updates().apply { latest = null }
        val auto = auto(updates)
        auto.next()
        clock -= 30L * 24 * 60 * 60 * 1000
        auto.next()
        assertEquals(2, updates.checks)
    }

    @Test fun aFailedCheckIsRetriedSoonerAndKeepsTheDownload() {
        val updates = Updates().apply { ready = PendingUpdate(info(4), File("a.apk")); failure = IOException("offline") }
        val auto = auto(updates)
        assertEquals(4L, auto.next()?.info?.code)
        clock += AutoUpdate.RETRY_MILLIS - 1
        auto.next()
        assertEquals(1, updates.checks)
        clock += 1
        auto.next()
        assertEquals(2, updates.checks)
    }

    @Test fun aDownloadThatFailsIsRetriedLater() {
        val updates = object : UpdateAccess {
            override val enabled = true
            var attempts = 0
            override fun check() = info(5)
            override fun download(update: UpdateInfo): File { attempts++; throw IOException("Downloaded update differs") }
        }
        val auto = auto(updates)
        assertNull(auto.next())
        auto.next()
        assertEquals(1, updates.attempts)
        clock += AutoUpdate.RETRY_MILLIS
        auto.next()
        assertEquals(2, updates.attempts)
    }

    @Test fun aVersionAlreadyDownloadedIsNotDownloadedAgain() {
        val updates = Updates().apply { ready = PendingUpdate(info(5), File("a.apk")) }
        assertEquals(5L, auto(updates).next()?.info?.code)
        assertTrue(updates.downloads.isEmpty())
    }

    @Test fun aRejectedUpdateStaysDownloadedButIsNotOfferedAgain() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next()!!)
        assertNull(auto.next())
        assertEquals(5L, updates.pending()?.info?.code)
        clock += AutoUpdate.CHECK_MILLIS
        assertNull(AutoUpdate(prefs, updates) { clock }.next())
        assertEquals(listOf(5L), updates.downloads)
    }

    @Test fun anUpdateBlockedByTheInstallPermissionWaitsQuietly() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next(canInstall = false)!!, canInstall = false)
        assertNull(auto.next(canInstall = false))
    }

    @Test fun anUpdateBlockedByTheInstallPermissionInstallsOnceItIsAllowed() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next(canInstall = false)!!, canInstall = false)
        assertEquals(5L, auto.next(canInstall = true)?.info?.code)
    }

    @Test fun allowingInstallsDoesNotReviveARealRejection() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next(canInstall = true)!!, canInstall = true)
        assertNull(auto.next(canInstall = true))
    }

    @Test fun aRejectionAfterAllowingInstallsIsFinal() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next(canInstall = false)!!, canInstall = false)
        auto.offered(auto.next(canInstall = true)!!, canInstall = true)
        assertNull(auto.next(canInstall = true))
    }

    @Test fun aNewerReleaseIsOfferedAfterARejection() {
        val updates = Updates()
        val auto = auto(updates)
        auto.offered(auto.next()!!)
        updates.latest = info(6)
        clock += AutoUpdate.CHECK_MILLIS
        assertEquals(6L, auto.next()?.info?.code)
    }
}
