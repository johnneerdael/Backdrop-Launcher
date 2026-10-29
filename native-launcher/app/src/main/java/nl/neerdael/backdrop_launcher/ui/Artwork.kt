package nl.neerdael.backdrop_launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.onVisibilityChangedNode
import androidx.compose.ui.layout.times
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Image readiness invalidates drawing, without writing measured sizes back into composition. */
@Composable
internal fun Artwork(
    path: String?, loader: ComposeArtwork, active: Boolean, revision: Int,
    modifier: Modifier, contentScale: ContentScale, retain: Boolean = true,
    decodeSize: IntSize? = null, viewport: LayoutBoundsHolder? = null,
) {
    Box(modifier.then(ArtworkElement(path, loader, active, revision, contentScale, retain, decodeSize, viewport)))
}

private data class ArtworkElement(
    val path: String?, val loader: ComposeArtwork, val active: Boolean, val revision: Int,
    val contentScale: ContentScale, val retain: Boolean, val decodeSize: IntSize?,
    val viewport: LayoutBoundsHolder?,
) : ModifierNodeElement<ArtworkNode>() {
    override fun create() = ArtworkNode(this)
    override fun update(node: ArtworkNode) = node.update(this)
    override fun InspectorInfo.inspectableProperties() {
        name = "visibleArtwork"
        properties["path"] = path
    }
}

private data class ArtworkRequest(
    val loader: ComposeArtwork, val path: String, val width: Int, val height: Int,
    val retain: Boolean, val revision: Int,
)

private class ArtworkNode(private var config: ArtworkElement) :
    DelegatingNode(), DrawModifierNode, LayoutAwareModifierNode {
    private var measured = IntSize.Zero
    private var visible = false
    private var painter: BitmapPainter? = null
    private val binding = ImageSubscription<ArtworkRequest, Bitmap>(
        subscribe = { request, deliver -> request.loader.observe(request.path, request.width, request.height, request.retain, deliver) },
        display = { bitmap ->
            painter = bitmap?.let { BitmapPainter(it.asImageBitmap()) }
            if (isAttached) invalidateDraw()
        },
    )
    private var visibility = delegate(visibilityNode())

    private fun visibilityNode() = onVisibilityChangedNode(
        minFractionVisible = 0f, viewportBounds = config.viewport,
    ) {
        visible = it
        refresh()
    }

    fun update(next: ArtworkElement) {
        val changedViewport = config.viewport !== next.viewport
        config = next
        if (changedViewport) {
            undelegate(visibility)
            visibility = delegate(visibilityNode())
        }
        refresh()
    }

    override fun onRemeasured(size: IntSize) {
        measured = size
        refresh()
    }

    private fun refresh() {
        val target = config.decodeSize ?: measured
        val path = config.path
        val request = if (isAttached && config.active && visible && path != null && target.width > 0 && target.height > 0)
            ArtworkRequest(config.loader, path, target.width, target.height, config.retain, config.revision) else null
        binding.update(request)
    }

    override fun onDetach() { visible = false; binding.update(null) }
    override fun onReset() { visible = false; binding.update(null) }

    override fun ContentDrawScope.draw() {
        drawContent()
        val image = painter ?: return
        if (size.width <= 0f || size.height <= 0f) return
        // Match Image's BitmapPainter, center alignment and Crop/Fit math; clip only the bitmap rectangle.
        val scaled = image.intrinsicSize * config.contentScale.computeScaleFactor(image.intrinsicSize, size)
        val offset = Alignment.Center.align(
            IntSize(scaled.width.roundToInt(), scaled.height.roundToInt()),
            IntSize(size.width.roundToInt(), size.height.roundToInt()), layoutDirection,
        )
        clipRect {
            translate(offset.x.toFloat(), offset.y.toFloat()) {
                with(image) { draw(scaled) }
            }
        }
    }
}
