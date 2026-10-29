package nl.neerdael.backdrop_launcher.core

import java.io.File

fun localArtworkStamp(root: File, paths: List<String>): String {
    val directory = root.canonicalFile
    return paths.distinct().sorted().joinToString("|") { path ->
        val file = File(directory, path).canonicalFile
        require(file.path.startsWith(directory.path + File.separator)) { "Artwork outside scene directory" }
        "$path:${file.lastModified()}:${file.length()}"
    }
}

/** Accept a load only if no newer source change arrived while it was running. */
class HomeRefreshState {
    var version = 0L
        private set
    private var accepted = -1L
    val dirty: Boolean get() = accepted != version
    fun invalidate() { version++ }
    fun accept(request: Long): Boolean {
        if (request != version) return false
        accepted = request
        return true
    }
}

/** Main-thread request bookkeeping; late completions cannot consume a replacement batch. */
class ImageRequests<T> {
    class Batch<T>(val key: String, val targets: MutableList<T>)
    private val pending = mutableMapOf<String, Batch<T>>()
    fun join(key: String, target: T): Pair<Batch<T>, Boolean> {
        pending[key]?.let { it.targets.add(target); return it to false }
        val batch = Batch(key, mutableListOf(target))
        pending[key] = batch
        return batch to true
    }
    fun complete(batch: Batch<T>): List<T> {
        if (pending[batch.key] !== batch) return emptyList()
        pending.remove(batch.key)
        return batch.targets
    }
    fun remove(target: T) { pending.values.forEach { it.targets.remove(target) } }
    fun clear() { pending.clear() }
}
