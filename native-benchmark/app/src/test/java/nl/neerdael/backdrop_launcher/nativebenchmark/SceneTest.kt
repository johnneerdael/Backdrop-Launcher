package nl.neerdael.backdrop_launcher.nativebenchmark

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class SceneTest {
    @Test fun refusesUnsupportedVersionInsteadOfShowingADifferentScene() {
        assertThrows(IllegalArgumentException::class.java) { Scene.parse("""{"version":2}""") }
    }

    @Test fun preservesRowOrderAndArtworkAndClampsProgress() {
        val scene = Scene.parse("""{
            "version":1,"name":"comparison","backdrop":"backdrops/test.jpg",
            "rows":[{"name":"First","apps":[{"package":"example.app","label":"Example","artwork":"appicons/test.png"}]},
                    {"name":"Second","apps":[]}],
            "watchNext":[{"title":"Programme","image":"poster.jpg","progress":110}]
        }""")
        assertEquals(listOf("First", "Second"), scene.rows.map { it.name })
        assertEquals("appicons/test.png", scene.rows.first().apps.first().artwork)
        assertEquals(100, scene.watchNext.single().progress)
    }

    @Test fun restrictsSceneArtworkToLocalRelativePaths() {
        for (path in listOf("../outside.jpg", "/data/private.jpg", "https://host/image", "a/../../b")) {
            assertThrows(IllegalArgumentException::class.java) {
                Scene.parse("""{"version":1,"name":"bad","backdrop":"$path"}""")
            }
        }
    }

    @Test fun imageDecodePreservesAspectAndNeverUpscales() {
        assertEquals(DecodeSize(1920, 1080, 2), decodeSize(3840, 2160, 1920, 1080))
        assertEquals(DecodeSize(480, 270, 1), decodeSize(480, 270, 1920, 1080))
        assertEquals(DecodeSize(1920, 720, 1), decodeSize(2560, 960, 1920, 1080))
        assertThrows(IllegalArgumentException::class.java) { decodeSize(0, 100, 100, 100) }
    }

    @Test fun fullscreenResumeUsesSelectedArtworkAndFallsBackForImagelessItems() {
        val scene = Scene.parse("""{"version":1,"name":"hero","backdrop":"wallpaper.jpg","bigPicture":true,
            "watchNext":[{"title":"First","image":"first.jpg"},{"title":"Second"}]}""")
        assertEquals("first.jpg", scene.backdropFor(0))
        assertEquals("wallpaper.jpg", scene.backdropFor(1))
        assertEquals("wallpaper.jpg", scene.copy(bigPicture = false).backdropFor(0))
    }

    @Test fun rejectsOversizedEncodedAndDecodedArtwork() {
        assertThrows(IOException::class.java) {
            copyArtwork(ByteArrayInputStream(ByteArray(50)), ByteArrayOutputStream(), maxBytes = 40)
        }
        val output = ByteArrayOutputStream()
        copyArtwork(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, maxBytes = 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { decodeSize(100000, 100000, 1920, 1080) }
        assertThrows(IOException::class.java) {
            copyArtwork(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), cancelled = { true })
        }
    }

    @Test fun focusKeepsTheSameAppInItsOriginalRowAndFollowsMovedApps() {
        val scene = Scene.parse("""{"version":1,"name":"focus","rows":[
            {"name":"First","apps":[{"package":"same.app","label":"Same"}]},
            {"name":"Second","apps":[{"package":"other.app","label":"Other"},{"package":"same.app","label":"Same"}]}]}""")
        val saved = AppFocus(1, 1, "same.app")
        assertEquals(saved, scene.restoreAppFocus(saved))
        assertEquals(AppFocus(1, 0, "other.app"), scene.restoreAppFocus(AppFocus(0, 0, "other.app")))
        assertNull(scene.restoreAppFocus(AppFocus(0, 0, "removed.app")))
    }
}
