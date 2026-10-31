package nl.neerdael.backdrop_launcher.ui

internal const val WATCH_CARD_WIDTH = 300f
internal const val WATCH_CARD_HEIGHT = 220f
internal const val WATCH_MOTION_MILLIS = 320

/** Reused by a graphics layer: no per-frame pose allocation or snapshot writes. */
internal class WatchNextPose(var x: Float = 0f, var y: Float = 0f, var scale: Float = 1f, var alpha: Float = 0f)

/** Discrete navigation state changes only at key input and step boundaries. */
internal data class WatchNextMotionState(val keys: List<String>, val settled: Int, val target: Int,
    val requested: Int, val moving: Boolean = false) {
    val pending get() = keys.isNotEmpty() && requested != settled
    val activeIndex get() = if (moving) target else settled

    fun request(delta: Int): WatchNextMotionState = if (keys.isEmpty()) this else
        copy(requested = (requested.toLong() + delta).coerceIn(0, keys.lastIndex.toLong()).toInt())

    fun begin(): WatchNextMotionState = if (moving || !pending) this else
        copy(target = settled + if (requested > settled) 1 else -1, moving = true)

    fun complete() = if (moving) copy(settled = target, moving = false) else this
    fun pause() = copy(settled = activeIndex, target = activeIndex, requested = activeIndex, moving = false)

    fun reconcile(next: List<String>, fallback: Int): WatchNextMotionState {
        if (keys == next) return this
        val id = keys.getOrNull(activeIndex)
        val index = next.indexOf(id).takeIf { it >= 0 } ?: if (next.isEmpty()) 0 else
            (if (keys.isEmpty()) fallback else activeIndex).coerceIn(0, next.lastIndex)
        return initial(next, index)
    }

    fun residentIndices(): IntArray {
        if (keys.isEmpty()) return intArrayOf()
        val values = if (!moving) intArrayOf(settled, settled + 1)
            else if (target > settled) intArrayOf(settled, target, target + 1)
            else intArrayOf(target, settled, settled + 1)
        return values.filter { it in keys.indices }.toIntArray()
    }

    fun writePose(index: Int, fraction: Float, viewportWidth: Float, layout: WatchLayout, out: WatchNextPose) {
        val left = 32f
        val width = layout.cardWidth
        val full = layout.scale
        val previewScale = layout.nextScale
        val previewAlpha = layout.nextAlpha
        val previewX = left + width + layout.gap
        val previewY = layout.cardHeight - WATCH_CARD_HEIGHT * previewScale
        out.x = viewportWidth + width
        out.y = 0f; out.scale = full; out.alpha = 0f
        if (index !in keys.indices) return
        if (!moving) {
            when (index) {
                settled -> { out.x = left; out.alpha = 1f }
                settled + 1 -> { out.x = previewX; out.y = previewY; out.scale = previewScale; out.alpha = previewAlpha }
            }
            return
        }
        val t = fraction.coerceIn(0f, 1f)
        if (target > settled) {
            when (index) {
                settled -> {
                    // Exit before revealing the next preview; keep the whole card full-size.
                    out.x = lerp(left, -width - 24, (t * 2).coerceAtMost(1f))
                    out.alpha = 1f
                }
                target -> {
                    out.x = lerp(previewX, left, t); out.y = lerp(previewY, 0f, t)
                    out.scale = lerp(previewScale, full, t)
                    out.alpha = lerp(previewAlpha, 1f, t)
                }
                target + 1 -> {
                    val reveal = ((t - .5f) * 2).coerceIn(0f, 1f)
                    out.x = lerp(viewportWidth + width / 3, previewX, reveal)
                    out.y = previewY; out.scale = previewScale; out.alpha = previewAlpha * reveal
                }
            }
        } else {
            val exitAt = (viewportWidth - left) / (viewportWidth + width - left)
            when (index) {
                target -> {
                    val enter = ((t - .2f) / .8f).coerceIn(0f, 1f)
                    out.x = lerp(-width - 24, left, enter); out.alpha = 1f
                }
                settled -> {
                    if (t < exitAt) { out.x = lerp(left, viewportWidth + width, t); out.alpha = 1f }
                    else {
                        // Reuse this item's layer as a preview only once its full-size card is offscreen.
                        val reveal = ((t - exitAt) / (1 - exitAt)).coerceIn(0f, 1f)
                        out.x = lerp(viewportWidth + width / 3, previewX, reveal)
                        out.y = previewY; out.scale = previewScale; out.alpha = previewAlpha * reveal
                    }
                }
                settled + 1 -> {
                    out.x = lerp(previewX, viewportWidth + width / 3, (t / .2f).coerceAtMost(1f))
                    out.y = previewY; out.scale = previewScale
                    out.alpha = if (t < .2f) previewAlpha else 0f
                }
            }
        }
    }
    companion object {
        fun initial(keys: List<String>, index: Int): WatchNextMotionState {
            val safe = if (keys.isEmpty()) 0 else index.coerceIn(0, keys.lastIndex)
            return WatchNextMotionState(keys, safe, safe, safe)
        }
        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }
}
