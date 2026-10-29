package nl.neerdael.backdrop_launcher.platform

import nl.neerdael.backdrop_launcher.core.UpdateInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeUpdaterTest {
    private val base = "https://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.6"
    private val update = UpdateInfo("1.3.6", 5000, "$base/backdrop-launcher.apk", "Changes")

    @Test fun rejectsUnownedOrAmbiguousInitialAssetUrls() {
        for (url in listOf("http://github.com/johnneerdael/Backdrop-Launcher/releases/download/v1.3.6/backdrop-launcher.apk",
            "$base/backdrop-launcher.apk?redirect=evil", "$base/backdrop-launcher.apk#fragment", "$base/backdrop-launcher.apk/../evil",
            "$base/backdrop-launcher-arm64-v8a.apk", "$base/backdrop-launcher.apk".replace("johnneerdael", "someoneelse"),
            "$base/backdrop-launcher.apk".replace("github.com", "github.com.evil.test"),
            "$base/backdrop-launcher.apk".replace("github.com", "user@github.com"))) {
            assertThrows(IllegalArgumentException::class.java) { NativeUpdater.requireAssetUrl(url, "v1.3.6", NativeUpdater.APK_NAME) }
        }
        NativeUpdater.requireAssetUrl(update.url, "v1.3.6", NativeUpdater.APK_NAME)
    }

    @Test fun redirectAllowlistDoesNotAcceptSuffixOrCleartextHosts() {
        for (host in listOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            NativeUpdater.requireRedirectUrl("https://$host/release.apk?token=signed")
        for (url in listOf("http://github.com/a", "https://evil.test/a", "https://github.com.evil.test/a",
            "https://user@github.com/a", "https://github.com:8443/a", "file:///tmp/apk"))
            assertThrows(IllegalArgumentException::class.java) { NativeUpdater.requireRedirectUrl(url) }
    }

    @Test fun checkUsesManifestCodeAndUniversalAliasAndReadableNotes() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        val updater = NativeUpdater(true, { 4999 }, cache, network, { goodArchive() })
        val result = updater.check()!!
        assertEquals(5000L, result.code)
        assertEquals(update.url, result.url)
        assertEquals("- Faster startup", result.notes)
        assertEquals(listOf(NativeUpdater.RELEASE_API, "$base/release-manifest.json"), network.reads)
    }

    @Test fun currentCodeDraftPrereleaseAndMissingManifestAreNotUpdates() = withDirectory { cache ->
        assertNull(NativeUpdater(true, { 5000 }, cache, FakeUpdateNetwork(release(), manifest(cache)), { goodArchive() }).check())
        for (field in listOf("draft", "prerelease")) {
            val network = FakeUpdateNetwork(release().put(field, true), manifest(cache))
            assertNull(NativeUpdater(true, { 1 }, cache, network, { goodArchive() }).check())
            assertEquals(1, network.reads.size)
        }
        val noManifest = release().put("assets", JSONArray().put(asset("backdrop-launcher.apk")))
        assertNull(NativeUpdater(true, { 1 }, cache, FakeUpdateNetwork(noManifest, manifest(cache)), { goodArchive() }).check())
    }

    @Test fun disabledUpdaterDoesNotFetchOrDownload() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        val updater = NativeUpdater(false, { 1 }, cache, network, { goodArchive() })
        assertNull(updater.check())
        assertTrue(network.reads.isEmpty())
        assertThrows(IllegalStateException::class.java) { updater.download(update) }
        assertEquals(0, network.downloads)
    }

    @Test fun rejectsWrongManifestIdentityVersionCodeCertificateAndChecksum() = withDirectory { cache ->
        val changes: List<Pair<String, Any>> = listOf("repository" to "attacker/fork", "version" to "1.3.7",
            "schema" to 2, "android_version_code" to true, "android_version_code" to 0,
            "signing_certificate_sha256" to "bad")
        for ((key, value) in changes) {
            val updater = NativeUpdater(true, { 1 }, cache, FakeUpdateNetwork(release(), manifest(cache).put(key, value)), { goodArchive() })
            assertThrows(IllegalArgumentException::class.java) { updater.check() }
        }
        val bad = manifest(cache).put("apks", JSONObject().put(NativeUpdater.APK_NAME, JSONObject().put("sha256", "bad").put("size_bytes", 3)))
        assertThrows(IllegalArgumentException::class.java) { NativeUpdater(true, { 1 }, cache, FakeUpdateNetwork(release(), bad), { goodArchive() }).check() }
    }

    @Test fun archiveGuardRequiresExactPackageVersionCodeAndSinglePinnedSigner() {
        NativeUpdater.verifyArchive(goodArchive(), update, 4999)
        for (archive in listOf(null, goodArchive().copy(packageName = "attacker.app"), goodArchive().copy(version = "1.3.7"),
            goodArchive().copy(code = 4999), goodArchive().copy(certificates = emptySet()),
            goodArchive().copy(certificates = setOf("wrong")), goodArchive().copy(certificates = setOf(NativeUpdater.CERTIFICATE, "extra")))) {
            assertThrows(IllegalArgumentException::class.java) { NativeUpdater.verifyArchive(archive, update, 1) }
        }
        assertThrows(IllegalArgumentException::class.java) { NativeUpdater.verifyArchive(goodArchive(), update, 5000) }
    }

    @Test fun verifiedDownloadPublishesOnlyFinalFileAfterChecks() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        var inspectedPart = false
        val updater = NativeUpdater(true, { 4999 }, cache, network, {
            inspectedPart = it.extension == "part"
            goodArchive()
        })
        val info = updater.check()!!
        val downloaded = updater.download(info)
        assertTrue(inspectedPart)
        assertEquals("backdrop-launcher-5000.apk", downloaded.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), downloaded.readBytes())
        assertEquals(listOf(downloaded.name), cache.listFiles()!!.map { it.name })
    }

    @Test fun checksumFailureOrArchiveFailureNeverLeavesInstallableFile() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        val updater = NativeUpdater(true, { 4999 }, cache, network, { goodArchive() })
        val info = updater.check()!!
        network.payload = byteArrayOf(9, 9, 9)
        assertThrows(IllegalArgumentException::class.java) { updater.download(info) }
        assertTrue(cache.listFiles()!!.isEmpty())
        network.payload = byteArrayOf(1, 2, 3)
        val invalid = NativeUpdater(true, { 4999 }, cache, network, { goodArchive().copy(certificates = setOf("bad")) })
        invalid.check()
        assertThrows(IllegalArgumentException::class.java) { invalid.download(info) }
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun maliciousDownloadUrlAndVersionMismatchFailBeforeNetwork() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        val updater = NativeUpdater(true, { 4999 }, cache, network, { goodArchive() })
        assertThrows(IllegalArgumentException::class.java) { updater.download(update.copy(url = update.url.replace("github.com", "evil.test"))) }
        assertThrows(IllegalArgumentException::class.java) { updater.download(update.copy(version = "1.3.7")) }
        assertThrows(IllegalArgumentException::class.java) { updater.download(update.copy(code = 4999)) }
        assertEquals(0, network.downloads)
    }

    @Test fun downloadWithoutVerifiedManifestFailsBeforeNetworkOrCreatingInstaller() = withDirectory { cache ->
        val network = FakeUpdateNetwork(release(), manifest(cache))
        val updater = NativeUpdater(true, { 4999 }, cache, network, { goodArchive() })
        assertThrows(IllegalStateException::class.java) { updater.download(update) }
        assertEquals(0, network.downloads)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun checkingAnotherReleaseInvalidatesOldDownloadMetadata() = withDirectory { cache ->
        val release = release()
        val manifest = manifest(cache)
        val network = FakeUpdateNetwork(release, manifest)
        val updater = NativeUpdater(true, { 4999 }, cache, network, { goodArchive() })
        val old = updater.check()!!
        val newBase = base.replace("v1.3.6", "v1.3.7")
        release.put("tag_name", "v1.3.7").put("assets", JSONArray()
            .put(JSONObject().put("name", NativeUpdater.APK_NAME).put("browser_download_url", "$newBase/${NativeUpdater.APK_NAME}"))
            .put(JSONObject().put("name", "release-manifest.json").put("browser_download_url", "$newBase/release-manifest.json")))
        manifest.put("version", "1.3.7").put("android_version_code", 5001)
        assertEquals(5001L, updater.check()!!.code)
        assertThrows(IllegalStateException::class.java) { updater.download(old) }
        assertEquals(0, network.downloads)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    private fun goodArchive() = UpdateArchive(NativeUpdater.PACKAGE, "1.3.6", 5000, setOf(NativeUpdater.CERTIFICATE))
    private fun asset(name: String) = JSONObject().put("name", name).put("browser_download_url", "$base/$name")
    private fun release() = JSONObject().put("tag_name", "v1.3.6").put("draft", false).put("prerelease", false)
        .put("body", "![Brand](https://example.com/banner.png)\n## What's new\n- **Faster startup**\n## Downloads\n[APK](https://example.com)\n## Source and verification\nProof")
        .put("assets", JSONArray().put(asset("backdrop-launcher-1.3.6-arm64-v8a.apk")).put(asset("backdrop-launcher.apk")).put(asset("release-manifest.json")))
    private fun manifest(cache: File): JSONObject {
        val sample = File(cache, "sample")
        sample.writeBytes(byteArrayOf(1, 2, 3))
        val checksum = NativeUpdater.sha256(sample)
        sample.delete()
        return JSONObject().put("schema", 1).put("repository", NativeUpdater.REPOSITORY).put("version", "1.3.6")
            .put("android_version_code", 5000).put("signing_certificate_sha256", NativeUpdater.CERTIFICATE)
            .put("apks", JSONObject().put(NativeUpdater.APK_NAME, JSONObject().put("sha256", checksum).put("size_bytes", 3)))
    }
    private fun withDirectory(block: (File) -> Unit) {
        val cache = Files.createTempDirectory("native-updater-test").toFile()
        try { block(cache) } finally { cache.deleteRecursively() }
    }
}

internal class FakeUpdateNetwork(private val release: JSONObject, private val manifest: JSONObject) : UpdateNetwork {
    val reads = mutableListOf<String>()
    var downloads = 0
    var payload = byteArrayOf(1, 2, 3)
    override fun json(url: String): JSONObject { reads.add(url); return if (url == NativeUpdater.RELEASE_API) release else manifest }
    override fun download(url: String, target: File) { downloads++; target.writeBytes(payload) }
}
