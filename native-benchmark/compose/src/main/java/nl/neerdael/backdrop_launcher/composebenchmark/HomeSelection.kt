package nl.neerdael.backdrop_launcher.composebenchmark

import nl.neerdael.backdrop_launcher.nativebenchmark.AppFocus
import nl.neerdael.backdrop_launcher.nativebenchmark.Scene

/** Lightweight selection survives hidden Home without retaining image references. */
class HomeSelection {
    enum class Area { APPS, WATCH_NEXT, SETTINGS }
    var area = Area.APPS
    var app: AppFocus? = null
    var resumeIndex = 0
    fun reconcile(scene: Scene) {
        app = scene.restoreAppFocus(app) ?: scene.rows.withIndex().firstOrNull { it.value.apps.isNotEmpty() }
            ?.let { AppFocus(it.index, 0, it.value.apps.first().packageName) }
        resumeIndex = if (scene.watchNext.isEmpty()) 0 else resumeIndex.coerceIn(0, scene.watchNext.lastIndex)
        if (area == Area.WATCH_NEXT && scene.watchNext.isEmpty()) area = Area.APPS
        if (area == Area.APPS && app == null) area = if (scene.watchNext.isNotEmpty()) Area.WATCH_NEXT else Area.SETTINGS
    }
    fun cycle(count: Int, direction: Int) {
        resumeIndex = if (count <= 0) 0 else Math.floorMod(resumeIndex + direction, count)
    }
}
