package nl.neerdael.backdrop_launcher.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LegacyLibraryTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var prefs: LegacyPreferences
    private var library: LegacyLibrary? = null
    private val apps = mutableListOf(InstalledApp("tv.one", "One", "2", false), InstalledApp("tv.two", "Two", "2", false))
    private val installed = mutableSetOf("tv.one", "tv.two")
    private val inventory = object : InstalledInventory {
        override fun enumerate() = apps.toList()
        override fun exists(packageName: String) = packageName in installed
    }
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        directory = File(context.cacheDir, "legacy-${System.nanoTime()}").apply { mkdirs() }
        file = File(directory, "db.sqlite")
        context.getSharedPreferences("FlutterSharedPreferences", 0).edit().clear().commit()
        prefs = LegacyPreferences(context)
    }
    @After fun tearDown() { library?.close(); directory.deleteRecursively() }
    private fun open(): LegacyLibrary = LegacyLibrary(context, prefs, inventory, file, { setOf("appicons/one.png") }, { 1800000000123L }).also { library = it }
    private fun schema(version: Int = 12, seed: SQLiteDatabase.() -> Unit = {}) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE apps(package_name TEXT PRIMARY KEY,name TEXT NOT NULL,version TEXT NOT NULL,hidden INTEGER NOT NULL DEFAULT 0,last_launched_at INTEGER)")
            val rowColumns = if (version >= 11) "source INTEGER NOT NULL DEFAULT 0" else "type INTEGER NOT NULL DEFAULT 1,columns_count INTEGER NOT NULL DEFAULT 6"
            val hidden = if (version >= 12) ",hidden INTEGER NOT NULL DEFAULT 0" else ""
            db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,sort INTEGER NOT NULL DEFAULT 0,$rowColumns,row_height INTEGER NOT NULL DEFAULT 110,\"order\" INTEGER NOT NULL$hidden)")
            db.execSQL("CREATE TABLE apps_categories(category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,app_package_name TEXT NOT NULL REFERENCES apps(package_name) ON DELETE CASCADE,\"order\" INTEGER NOT NULL,PRIMARY KEY(category_id,app_package_name))")
            db.version = version
            db.seed()
        }
    }
    private fun SQLiteDatabase.seedApps() {
        execSQL("INSERT INTO apps VALUES('tv.one','Old One','1',0,1720000000)")
        execSQL("INSERT INTO apps VALUES('tv.two','Old Two','1',1,NULL)")
    }
    private fun SQLiteDatabase.row(id: Int, name: String, source: Int = 0, sort: Int = 0, height: Int = 110) {
        execSQL("INSERT INTO categories(id,name,source,sort,row_height,\"order\") VALUES(?,?,?,?,?,?)", arrayOf<Any>(id, name, source, sort, height, id))
    }
    private fun SQLiteDatabase.member(id: Int, packageName: String, order: Int = 0) {
        execSQL("INSERT INTO apps_categories VALUES(?,?,?)", arrayOf<Any>(id, packageName, order))
    }
    @Test fun `schema12 refresh preserves identities memberships hidden fields seconds and settings`() {
        schema {
            seedApps(); row(41, "My row", sort = 2, height = 145); row(42, "TV Apps", source = 1); row(43, "Android Apps", source = 2)
            member(41, "tv.one", 9); member(41, "tv.two", 4); member(42, "tv.one", 6)
        }
        prefs.putInt("application_rows_layout_version", 1)
        prefs.putString("app_artwork_tv.one", "appicons/one.png")
        prefs.putString("trakt_token", "{\"access_token\":\"retain\"}")
        val state = open().snapshot()
        assertEquals(listOf(41L, 42L, 43L), state.rows.map { it.id })
        assertEquals(1720000000000L, state.apps.single { it.packageName == "tv.one" }.lastLaunchedAt)
        assertEquals("2", state.apps.single { it.packageName == "tv.one" }.version)
        assertTrue(state.apps.single { it.packageName == "tv.two" }.hidden)
        assertEquals(145, state.rows.first().height)
        assertEquals(2, state.rows.first().sort)
        assertEquals("appicons/one.png", state.apps.single { it.packageName == "tv.one" }.artwork)
        assertEquals(listOf("tv.one"), state.rows.first().apps.map { it.packageName })
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(12, db.version)
            db.rawQuery("SELECT \"order\" FROM apps_categories WHERE category_id=41 AND app_package_name='tv.two'", null).use { assertTrue(it.moveToFirst()); assertEquals(4, it.getInt(0)) }
        }
        assertEquals("{\"access_token\":\"retain\"}", prefs.string("trakt_token"))
        val backup = File(directory, "native-backup/schema12-before-native.sqlite")
        assertTrue(backup.exists())
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT version FROM apps WHERE package_name='tv.one'", null).use { assertTrue(it.moveToFirst()); assertEquals("1", it.getString(0)) }
        }
    }
    @Test fun `unknown newer schema stays untouched and does not refresh preferences`() {
        schema(13) { seedApps(); row(77, "Do not touch"); member(77, "tv.one") }
        val before = file.readBytes()
        try { open().snapshot(); fail("Should reject future schema") } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, file.readBytes())
        assertFalse(File(directory, "native-backup").exists())
        assertEquals("", prefs.string("application_rows_native_classifications"))
    }
    @Test fun `legacy row migration is conservative and retains custom ids and cross kind choices`() {
        schema {
            seedApps(); row(10, "Favorites"); member(10, "tv.one") // Customized favorite must survive.
            row(20, "All Apps", sort = 1); member(20, "tv.one") // Customized sort must survive.
            row(30, "Personal"); member(30, "tv.one")
        }
        val state = open().snapshot()
        assertEquals(listOf(1, 2, 0, 0, 0), state.rows.map { it.source })
        assertEquals(listOf(10L, 20L, 30L), state.rows.drop(2).map { it.id })
        assertTrue(state.rows.first().apps.any { it.packageName == "tv.one" })
        val androidRow = state.rows[1]
        library!!.setRowApps(androidRow.id, listOf("tv.one"))
        library!!.setRowApps(state.rows.first().id, emptyList())
        assertEquals(listOf("tv.one"), library!!.snapshot(true).rows[1].apps.map { it.packageName })
        assertTrue(library!!.snapshot().rows.first().apps.isEmpty())
    }
    @Test fun `schema10 migrates in place without cascading memberships`() {
        schema(10) {
            seedApps()
            execSQL("INSERT INTO categories(id,name,sort,row_height,\"order\") VALUES(81,'Keep me',1,150,3)")
            member(81, "tv.one", 7)
        }
        val row = open().snapshot().rows.single { it.id == 81L }
        assertEquals("Keep me", row.name)
        assertEquals(1, row.sort)
        assertEquals(150, row.height)
        assertEquals(listOf("tv.one"), row.apps.map { it.packageName })
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(12, it.version) }
        assertTrue(File(directory, "native-backup/schema10-before-native.sqlite").exists())
    }
    @Test fun `backup includes committed WAL data and retains autoincrement high water mark`() {
        schema {
            seedApps(); row(81, "Keep me"); member(81, "tv.one", 7)
            row(999, "Deleted"); execSQL("DELETE FROM categories WHERE id=999")
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { writer ->
            assertTrue(writer.enableWriteAheadLogging())
            writer.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            writer.execSQL("UPDATE apps SET name='Committed in WAL' WHERE package_name='tv.one'")
            assertTrue(File(file.path + "-wal").length() > 0)
            open().snapshot()
            val backup = File(directory, "native-backup/schema12-before-native.sqlite")
            SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { saved ->
                saved.rawQuery("SELECT name FROM apps WHERE package_name='tv.one'", null).use { assertTrue(it.moveToFirst()); assertEquals("Committed in WAL", it.getString(0)) }
                saved.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='categories'", null).use { assertTrue(it.moveToFirst()); assertEquals(999L, it.getLong(0)) }
            }
        }
    }
    @Test fun `backup failure blocks migrations and inventory writes`() {
        schema(10) { seedApps() }
        File(directory, "native-backup").writeText("A file prevents creation of the backup directory")
        try { open().snapshot(); fail("Backup must precede migration") } catch (_: android.database.sqlite.SQLiteException) { }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
            assertEquals(10, source.version)
            source.rawQuery("SELECT version FROM apps WHERE package_name='tv.one'", null).use { assertTrue(it.moveToFirst()); assertEquals("1", it.getString(0)) }
        }
        assertEquals(0, prefs.int("application_rows_layout_version"))
    }
    @Test fun `only generated legacy defaults are replaced with TV and Android rows`() {
        schema {
            seedApps(); row(10, "Favorites"); row(20, "All Apps"); member(20, "tv.one")
            row(30, "Keep custom"); member(30, "tv.one")
        }
        val state = open().snapshot()
        assertEquals(listOf("TV Apps", "Android Apps", "Keep custom"), state.rows.map { it.name })
        assertEquals(30L, state.rows.last().id)
        assertEquals(listOf("tv.one"), state.rows.last().apps.map { it.packageName })
        assertTrue(state.apps.single { it.packageName == "tv.two" }.hidden)
        library!!.setHidden("tv.two", false)
        assertEquals(listOf("tv.one", "tv.two"), library!!.snapshot().rows.first().apps.map { it.packageName })
    }
    @Test fun `row CRUD order hiding artwork and launch data survive reopening`() {
        val access = open()
        val initial = access.snapshot()
        val first = access.createRow("First")
        val second = access.createRow("Second")
        access.setHidden("tv.two", false)
        access.setRowApps(first, listOf("tv.one", "tv.two"))
        access.moveApp(first, "tv.two", -1)
        access.renameRow(first, "Renamed")
        access.updateRow(first, true, 0, 145)
        access.moveRow(second, -1)
        access.setArtwork("tv.one", "appicons/one.png")
        try { access.setArtwork("tv.one", "../../private/token"); fail("Reject filesystem artwork") } catch (_: IllegalArgumentException) { }
        access.recordLaunch("tv.two")
        access.close()
        val state = open().snapshot()
        val row = state.rows.single { it.id == first }
        assertEquals("Renamed", row.name)
        assertTrue(row.hidden)
        assertEquals(145, row.height)
        assertEquals(listOf("tv.two", "tv.one"), row.apps.map { it.packageName })
        assertEquals(1800000000000L, state.apps.single { it.packageName == "tv.two" }.lastLaunchedAt)
        assertEquals(listOf(initial.rows[0].id, initial.rows[1].id, second, first), state.rows.map { it.id })
        library!!.deleteRow(first)
        assertEquals(2, library!!.snapshot().apps.size)
        assertEquals(2, library!!.snapshot().rows.first().apps.size)
    }
    @Test fun `temporarily disabled package retains memberships while removed package cascades`() {
        val access = open()
        val first = access.snapshot().rows.first().id
        apps.removeAll { it.packageName == "tv.two" }
        assertEquals(2, access.snapshot(true).apps.size)
        installed.remove("tv.two")
        assertEquals(listOf("tv.one"), access.snapshot(true).apps.map { it.packageName })
        assertEquals(listOf("tv.one"), access.snapshot().rows.single { it.id == first }.apps.map { it.packageName })
    }
    @Test fun `row packages retain hidden memberships in manual order across sorting and restart`() {
        schema {
            seedApps(); row(81, "Alphabetical", sort = 1)
            member(81, "tv.one", 9); member(81, "tv.two", 4)
        }
        val access = open()
        assertEquals(listOf("tv.two", "tv.one"), access.rowPackages(81))
        assertEquals(listOf("tv.one"), access.snapshot().rows.single { it.id == 81L }.apps.map { it.packageName })
        // Saving an unchanged membership selection must retain the globally hidden choice.
        access.setRowApps(81, access.rowPackages(81))
        access.close()
        val reopened = open()
        assertEquals(listOf("tv.two", "tv.one"), reopened.rowPackages(81))
        assertEquals(emptyList<String>(), reopened.rowPackages(Long.MAX_VALUE))
    }
    @Test fun `classification change moves only default membership and preserves custom row`() {
        val access = open()
        access.snapshot()
        val row = access.createRow("My selection")
        access.setRowApps(row, listOf("tv.one"))
        apps[0] = apps[0].copy(androidApp = true)
        val state = access.snapshot(true)
        assertFalse(state.rows.single { it.source == 1 }.apps.any { it.packageName == "tv.one" })
        assertTrue(state.rows.single { it.source == 2 }.apps.any { it.packageName == "tv.one" })
        assertEquals(listOf("tv.one"), state.rows.single { it.id == row }.apps.map { it.packageName })
    }
}
