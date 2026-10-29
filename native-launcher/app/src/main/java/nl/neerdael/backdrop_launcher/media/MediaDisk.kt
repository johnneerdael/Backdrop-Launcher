package nl.neerdael.backdrop_launcher.media

import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Uses the Flutter LocalMediaCache layout and SHA-256 keys to retain startup artwork. */
internal class MediaDisk(val root: File, private val validImage: (ByteArray) -> Boolean = ::validMediaImage) {
    fun file(namespace: String, key: String): File = File(root, "media_launcher/$namespace/${hash(key)}.bin")
    fun read(namespace: String, key: String, limit: Int = 1024 * 1024): ByteArray? =
        runCatching { file(namespace, key).takeIf { it.isFile && it.length() in 1..limit.toLong() }?.readBytes() }.getOrNull()
    fun readJson(namespace: String, key: String): JSONObject? =
        runCatching { read(namespace, key)?.let { JSONObject(it.toString(Charsets.UTF_8)) } }.getOrNull()
    fun writeJson(namespace: String, key: String, json: JSONObject) { write(file(namespace, key), json.toString().toByteArray()) }
    fun writeImage(namespace: String, key: String, bytes: ByteArray): String? {
        if (!validImage(bytes)) return null
        val target = file(namespace, key)
        if (!write(target, bytes)) return null
        return relative(target)
    }
    fun cachedImage(namespace: String, key: String, limit: Int): String? {
        val bytes = read(namespace, key, limit) ?: return null
        if (!validImage(bytes)) { file(namespace, key).delete(); return null }
        return relative(file(namespace, key))
    }
    fun isImage(file: File, limit: Int): Boolean = runCatching {
        file.isFile && file.length() in 1..limit.toLong() && validImage(file.readBytes())
    }.getOrDefault(false)
    fun prune(namespace: String, retainedKeys: Set<String>) {
        runCatching {
            val retained = retainedKeys.map { "${hash(it)}.bin" }.toSet()
            file(namespace, "").parentFile?.listFiles()?.filter { it.name.endsWith(".bin") && it.name !in retained }?.forEach { it.delete() }
        }
    }
    fun relative(file: File): String = "cache:${file.relativeTo(root).invariantSeparatorsPath}"
    fun write(target: File, bytes: ByteArray): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile("media_", ".part", target.parentFile)
        try {
            temporary.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            check(temporary.renameTo(target)) { "Unable to persist media cache" }
        } finally { temporary.delete() }
        true
    }.getOrDefault(false)
    companion object {
        fun hash(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal fun validMediaImage(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..65536 || bounds.outHeight !in 1..65536 ||
        bounds.outWidth.toLong() * bounds.outHeight > 268435456) return false
    var sample = 1
    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
    decoded.recycle()
    return true
}
