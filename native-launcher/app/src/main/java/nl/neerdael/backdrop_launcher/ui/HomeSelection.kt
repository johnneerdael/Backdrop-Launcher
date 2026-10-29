package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.AppFocus
import nl.neerdael.backdrop_launcher.core.Scene
import nl.neerdael.backdrop_launcher.core.ResumeItem

/** Focus follows persistent row/app identities and keeps an independent memory for every row. */
class HomeSelection {
    enum class Area { APPS, WATCH_NEXT, SETTINGS }
    var area = Area.APPS
    var app: AppFocus? = null
    var resumeIndex = 0
    var rowId: Long? = null
        private set
    private val rowPackages = mutableMapOf<Long, String>()
    private var resumeIdentity: String? = null

    fun remember(scene: Scene, row: Int, tile: Int) {
        val entry = scene.rows.getOrNull(row) ?: return
        val selected = entry.apps.getOrNull(tile) ?: return
        rowId = entry.id
        rowPackages[entry.id] = selected.packageName
        app = AppFocus(row, tile, selected.packageName)
        area = Area.APPS
    }
    fun focusForRow(scene: Scene, row: Int): AppFocus? {
        val entry = scene.rows.getOrNull(row) ?: return null
        val tile = entry.apps.indexOfFirst { it.packageName == rowPackages[entry.id] }.takeIf { it >= 0 } ?: 0
        return entry.apps.getOrNull(tile)?.let { AppFocus(row, tile, it.packageName) }
    }
    fun reconcile(scene: Scene) {
        val previousArea = area
        val row = scene.rows.indexOfFirst { it.id == rowId }.takeIf { it >= 0 }
        app = row?.let { focusForRow(scene, it) } ?: scene.restoreAppFocus(app)
            ?: scene.rows.indices.firstNotNullOfOrNull { focusForRow(scene, it) }
        app?.let { remember(scene, it.row, it.tile) }
        area = previousArea
        if (scene.watchNext.isEmpty()) resumeIndex = 0 else {
            resumeIndex = scene.watchNext.indexOfFirst { resumeKey(it) == resumeIdentity }
                .takeIf { it >= 0 } ?: resumeIndex.coerceIn(0, scene.watchNext.lastIndex)
            selectResume(scene, resumeIndex)
        }
        if (area == Area.WATCH_NEXT && scene.watchNext.isEmpty()) area = Area.APPS
        if (area == Area.APPS && app == null) area = if (scene.watchNext.isNotEmpty()) Area.WATCH_NEXT else Area.SETTINGS
    }
    fun selectResume(scene: Scene, index: Int) {
        resumeIndex = index.coerceIn(0, scene.watchNext.lastIndex.coerceAtLeast(0))
        scene.watchNext.getOrNull(resumeIndex)?.let { resumeIdentity = resumeKey(it) }
    }
    fun cycle(count: Int, direction: Int, wrap: Boolean = true) {
        resumeIndex = if (count <= 0) 0 else if (wrap) (((resumeIndex.toLong() + direction) % count + count) % count).toInt()
        else (resumeIndex + direction).coerceIn(0, count - 1)
    }
    private fun resumeKey(item: ResumeItem) = if (item.id != 0L) "id:${item.id}" else "${item.packageName}:${item.contentId ?: item.title}"
}
