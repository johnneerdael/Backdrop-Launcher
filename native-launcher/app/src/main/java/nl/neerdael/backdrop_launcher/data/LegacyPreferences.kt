package nl.neerdael.backdrop_launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.math.BigInteger

/** Read/write the existing Flutter store without re-encoding unrelated values (including tokens).
 * Native-only keys must start with `native.` and live in a separate preference file.
 * Encodings follow shared_preferences_android 2.4.7's LegacySharedPreferencesPlugin.
 */
class LegacyPreferences(context: Context) : PreferenceAccess {
    private val legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val native = context.getSharedPreferences("BackdropNativePreferences", Context.MODE_PRIVATE)

    private fun store(key: String) = if (key.startsWith("native.")) native else legacy
    private fun diskKey(key: String) = if (key.startsWith("native.")) key else "flutter.$key"
    private fun raw(key: String): Any? = store(key).all[diskKey(key)]
    override fun bool(key: String, default: Boolean) = raw(key) as? Boolean ?: default
    override fun string(key: String, default: String) = raw(key) as? String ?: default
    override fun long(key: String, default: Long): Long = when (val value = raw(key)) {
        is Number -> value.toLong()
        is String -> if (value.startsWith(BIG_INTEGER))
            runCatching {
                val integer = BigInteger(value.removePrefix(BIG_INTEGER), 36)
                require(integer >= BigInteger.valueOf(Long.MIN_VALUE) && integer <= BigInteger.valueOf(Long.MAX_VALUE))
                integer.toLong()
            }.getOrDefault(default)
        else default
        else -> default
    }
    override fun int(key: String, default: Int): Int {
        val value = long(key, default.toLong())
        return if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else default
    }
    override fun double(key: String, default: Double): Double = when (val value = raw(key)) {
        is Number -> value.toDouble()
        is String -> if (value.startsWith(DOUBLE)) value.removePrefix(DOUBLE).toDoubleOrNull() ?: default else default
        else -> default
    }
    override fun strings(key: String, default: List<String>): List<String> {
        val value = raw(key)
        return runCatching {
            when {
                value is Set<*> -> value.map { it as String }
                value is String && value.startsWith(JSON_LIST) -> {
                    val array = JSONArray(value.removePrefix(JSON_LIST))
                    List(array.length()) { index -> array.get(index) as String }
                }
                value is String && value.startsWith(LIST) -> {
                    val bytes = Base64.decode(value.removePrefix(LIST), Base64.DEFAULT)
                    require(bytes.size <= 1024 * 1024) { "Preference list too large" }
                    StringListInput(ByteArrayInputStream(bytes)).use { input ->
                        (input.readObject() as List<*>).map { it as String }
                    }
                }
                else -> default
            }
        }.getOrDefault(default)
    }
    private fun edit(key: String, change: SharedPreferences.Editor.(String) -> Unit) {
        val editor = store(key).edit()
        editor.change(diskKey(key))
        check(editor.commit()) { "Could not persist launcher preference" }
    }
    override fun putBool(key: String, value: Boolean) = edit(key) { putBoolean(it, value); Unit }
    override fun putString(key: String, value: String) {
        require(!value.startsWith(LIST) && !value.startsWith(DOUBLE) && !value.startsWith(BIG_INTEGER)) {
            "String conflicts with Flutter preference encoding"
        }
        edit(key) { putString(it, value); Unit }
    }
    override fun putInt(key: String, value: Int) = putLong(key, value.toLong())
    override fun putLong(key: String, value: Long) = edit(key) { putLong(it, value); Unit }
    override fun putDouble(key: String, value: Double) = edit(key) { putString(it, DOUBLE + value); Unit }
    override fun putStrings(key: String, value: List<String>) = edit(key) {
        putString(it, JSON_LIST + JSONArray(value).toString()); Unit
    }
    override fun remove(key: String) = edit(key) { remove(it); Unit }
    override fun listen(changed: (String) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("flutter.") || key.startsWith("native.")))
                changed(key.removePrefix("flutter."))
        }
        legacy.registerOnSharedPreferenceChangeListener(listener)
        native.registerOnSharedPreferenceChangeListener(listener)
        return {
            legacy.unregisterOnSharedPreferenceChangeListener(listener)
            native.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private class StringListInput(input: ByteArrayInputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            require(desc.name in setOf("java.util.Arrays\$ArrayList", "java.util.ArrayList", "java.lang.String", "[Ljava.lang.String;")) {
                "Unsupported legacy preference list class"
            }
            return super.resolveClass(desc)
        }
    }
    companion object {
        internal const val LIST = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
        internal const val JSON_LIST = "$LIST!"
        internal const val DOUBLE = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
        internal const val BIG_INTEGER = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"
    }
}
