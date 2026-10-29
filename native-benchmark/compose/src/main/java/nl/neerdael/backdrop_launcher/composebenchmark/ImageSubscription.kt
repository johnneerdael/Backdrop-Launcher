package nl.neerdael.backdrop_launcher.composebenchmark

/** One visible image binding; stale deliveries never repaint a reused or hidden node. */
internal class ImageSubscription<Request, Image>(
    private val subscribe: (Request, (Image?) -> Unit) -> (() -> Unit),
    private val display: (Image?) -> Unit,
) {
    private var request: Request? = null
    private var generation = 0L
    private var cancel: (() -> Unit)? = null

    fun update(request: Request?) {
        if (this.request == request) return
        val epoch = ++generation
        cancel?.invoke()
        cancel = null
        this.request = request
        display(null)
        if (request != null) {
            val release = subscribe(request) { image -> if (generation == epoch) display(image) }
            if (generation == epoch) cancel = release else release()
        }
    }
}
