package nl.neerdael.backdrop_launcher.data

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyPreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: LegacyPreferences
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("FlutterSharedPreferences", 0).edit().clear().commit()
        context.getSharedPreferences("BackdropNativePreferences", 0).edit().clear().commit()
        prefs = LegacyPreferences(context)
    }
    @Test fun `reads exact Flutter scalars JSON token strings and both list encodings`() {
        val legacy = context.getSharedPreferences("FlutterSharedPreferences", 0)
        val stream = ByteArrayOutputStream()
        ObjectOutputStream(stream).use { it.writeObject(arrayListOf("tv.one", "tv.two")) }
        val token = "{\"access_token\":\"secret-token\",\"refresh_token\":\"refresh\",\"created_at\":1720000000}"
        legacy.edit().putBoolean("flutter.show_clock", false)
            .putLong("flutter.backdrop_interval_minutes", 30L)
            .putString("flutter.focus_border_thickness", LegacyPreferences.DOUBLE + "2.75")
            .putString("flutter.trakt_token", token)
            .putString("flutter.old_list", LegacyPreferences.LIST + Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT))
            .putString("flutter.new_list", LegacyPreferences.JSON_LIST + "[\"list:one\",\"list:two\"]")
            .putString("flutter.big", LegacyPreferences.BIG_INTEGER + "zik0zj")
            .commit()
        val before = legacy.all.toMap()
        assertFalse(prefs.bool("show_clock", true))
        assertEquals(30, prefs.int("backdrop_interval_minutes"))
        assertEquals(2.75, prefs.double("focus_border_thickness"), 0.0)
        assertEquals(token, prefs.string("trakt_token"))
        assertEquals(listOf("tv.one", "tv.two"), prefs.strings("old_list"))
        assertEquals(listOf("list:one", "list:two"), prefs.strings("new_list"))
        assertEquals(2147483647L, prefs.long("big"))
        assertEquals(before, legacy.all) // Reading never rewrites tokens or legacy encodings.
    }
    @Test fun `writes remain Flutter compatible while native additions stay separate`() {
        prefs.putBool("show_clock", true)
        prefs.putInt("watch_next_max_items", 8)
        prefs.putDouble("app_icon_background_opacity", 0.25)
        prefs.putStrings("watch_next_excluded_packages", listOf("tv.one", "quote\"slash\\"))
        prefs.putString("native.schema_note", "one")
        val disk = context.getSharedPreferences("FlutterSharedPreferences", 0)
        assertEquals(8L, disk.all["flutter.watch_next_max_items"])
        assertEquals(LegacyPreferences.DOUBLE + "0.25", disk.all["flutter.app_icon_background_opacity"])
        assertTrue((disk.all["flutter.watch_next_excluded_packages"] as String).startsWith(LegacyPreferences.JSON_LIST))
        assertEquals(listOf("tv.one", "quote\"slash\\"), prefs.strings("watch_next_excluded_packages"))
        assertFalse(disk.contains("flutter.native.schema_note"))
        assertEquals("one", context.getSharedPreferences("BackdropNativePreferences", 0).getString("native.schema_note", null))
    }
    @Test fun `malformed or unsafe lists fall back without touching stored bytes`() {
        val disk = context.getSharedPreferences("FlutterSharedPreferences", 0)
        val stream = ByteArrayOutputStream()
        ObjectOutputStream(stream).use { it.writeObject(hashMapOf("not" to "a list")) }
        val encoded = LegacyPreferences.LIST + Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        disk.edit().putString("flutter.bad", encoded).putString("flutter.invalid", LegacyPreferences.JSON_LIST + "[1]").commit()
        assertEquals(listOf("fallback"), prefs.strings("bad", listOf("fallback")))
        assertEquals(emptyList<String>(), prefs.strings("invalid"))
        assertEquals(encoded, disk.getString("flutter.bad", null))
    }
    @Test fun `legacy big integers retain Long bounds and reject overflow without truncation`() {
        val disk = context.getSharedPreferences("FlutterSharedPreferences", 0)
        val minimum = BigInteger.valueOf(Long.MIN_VALUE)
        val maximum = BigInteger.valueOf(Long.MAX_VALUE)
        for ((name, value) in mapOf("min" to minimum, "max" to maximum,
            "underflow" to minimum.subtract(BigInteger.ONE), "overflow" to maximum.add(BigInteger.ONE))) {
            disk.edit().putString("flutter.$name", LegacyPreferences.BIG_INTEGER + value.toString(36)).commit()
        }
        assertEquals(Long.MIN_VALUE, prefs.long("min", 7L))
        assertEquals(Long.MAX_VALUE, prefs.long("max", 7L))
        assertEquals(7L, prefs.long("underflow", 7L))
        assertEquals(7L, prefs.long("overflow", 7L))
    }
}
