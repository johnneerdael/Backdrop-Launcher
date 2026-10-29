package nl.neerdael.backdrop_launcher.ui

/** Main-thread backpressure: retry visible work when a bounded executor frees a slot, without polling. */
internal class VisibleWorkQueue<T>(private val start: (T) -> Boolean) {
    private val waiting = linkedSetOf<T>()
    fun offer(work: T) { if (!start(work)) waiting.add(work) }
    fun cancel(work: T) { waiting.remove(work) }
    fun clear() { waiting.clear() }
    fun drain() {
        val iterator = waiting.iterator()
        while (iterator.hasNext()) {
            if (!start(iterator.next())) return
            iterator.remove()
        }
    }
}
