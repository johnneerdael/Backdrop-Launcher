package nl.neerdael.backdrop_launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import nl.neerdael.backdrop_launcher.core.LauncherApp
import nl.neerdael.backdrop_launcher.core.LauncherRow
import nl.neerdael.backdrop_launcher.core.LibraryAccess
import nl.neerdael.backdrop_launcher.core.LibrarySnapshot
import nl.neerdael.backdrop_launcher.core.PreferenceAccess
import org.json.JSONObject
import java.io.File

/** In-place reader/writer for Drift schema12. All entry points block; dispatch off the UI thread.
 * A consistent, complete pre-native snapshot is retained outside the live DB before any write.
 * Unknown/newer versions or malformed schemas fail closed; opening never resets user data.
 */
class LegacyLibrary internal constructor(
    private val context: Context,
    private val prefs: PreferenceAccess,
    private val inventory: InstalledInventory,
    private val databaseFile: File,
    private val artworkAssets: () -> Set<String>,
    private val now: () -> Long,
) : LibraryAccess {
    constructor(context: Context, prefs: PreferenceAccess) : this(
        context.applicationContext, prefs, AndroidInstalledInventory(context.applicationContext),
        File(context.applicationInfo.dataDir, "app_flutter/db.sqlite"),
        { context.assets.list("appicons").orEmpty().filter { it.matches(Regex("(?i).+\\.(png|jpe?g|webp)")) }.map { "appicons/$it" }.toSet() },
        System::currentTimeMillis,
    )

    private var database: SQLiteDatabase? = null
    private var ready = false
    private var enumerated: Map<String, InstalledApp> = emptyMap()
    private val availableArtwork by lazy(artworkAssets)
    private val classifications = mutableMapOf<String, Boolean>()
    private var classificationsLoaded = false
    private val db: SQLiteDatabase get() = database ?: open().also { database = it }

    private fun open(): SQLiteDatabase {
        val existing = databaseFile.exists() && databaseFile.length() > 0
        if (existing) {
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { source ->
                require(source.version in 1..12) { "Unsupported launcher database schema; original data was preserved" }
                require(columns(source, "apps").containsAll(setOf("package_name", "name", "version")) &&
                    columns(source, "categories").containsAll(setOf("id", "name", "order")) &&
                    columns(source, "apps_categories").containsAll(setOf("category_id", "app_package_name", "order"))) {
                    "Invalid launcher database schema; original data was preserved"
                }
            }
        }
        databaseFile.parentFile?.mkdirs()
        val opened = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            if (existing) require(opened.version in 1..12) { "Launcher database changed during opening; original data was preserved" }
            // Android's legacy SQLite API needs a writable connection to hold the exclusive
            // snapshot transaction. No source SQL writes or migrations precede this backup.
            if (existing) backup(opened)
            if (!existing) transaction(opened) { createTables(opened); opened.version = 12 }
            else if (opened.version < 12) migrate(opened)
            require(columns(opened, "apps").containsAll(setOf("hidden", "last_launched_at")) &&
                columns(opened, "categories").containsAll(setOf("hidden", "sort", "source", "row_height"))) {
                "Invalid schema12 launcher database; original data was preserved"
            }
            opened.setForeignKeyConstraintsEnabled(true)
            return opened
        } catch (failure: Throwable) { opened.close(); throw failure }
    }

    private fun columns(source: SQLiteDatabase, table: String): Set<String> =
        source.rawQuery("PRAGMA table_info(${quote(table)})", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    /** Copy through a SQLite transaction rather than copying db/wal/shm files independently.
     * The source write lock pins one snapshot, and backup commit + integrity_check precede any
     * migration. Include extra legacy tables/indexes/triggers so recovery is lossless.
     */
    private fun backup(source: SQLiteDatabase) {
        val destination = File(databaseFile.parentFile, "native-backup/schema${source.version}-before-native.sqlite")
        if (destination.exists()) {
            SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { saved ->
                require(saved.version in 1..12 && integrity(saved)) { "Existing launcher backup is invalid" }
            }
            return
        }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.partial")
        temporary.delete()
        try {
            source.beginTransaction()
            try {
                SQLiteDatabase.openDatabase(temporary.path, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { target ->
                    target.beginTransaction()
                    try {
                        val definitions = source.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END", null).use { cursor ->
                            buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
                        }
                        for ((type, _, sql) in definitions) if (type == "table") target.execSQL(sql)
                        for ((type, name, _) in definitions) if (type == "table") {
                            source.rawQuery("SELECT * FROM ${quote(name)}", null).use { rows ->
                                while (rows.moveToNext()) {
                                    val values = ContentValues()
                                    for (index in 0 until rows.columnCount) when (rows.getType(index)) {
                                        Cursor.FIELD_TYPE_NULL -> values.putNull(quote(rows.getColumnName(index)))
                                        Cursor.FIELD_TYPE_INTEGER -> values.put(quote(rows.getColumnName(index)), rows.getLong(index))
                                        Cursor.FIELD_TYPE_FLOAT -> values.put(quote(rows.getColumnName(index)), rows.getDouble(index))
                                        Cursor.FIELD_TYPE_BLOB -> values.put(quote(rows.getColumnName(index)), rows.getBlob(index))
                                        else -> values.put(quote(rows.getColumnName(index)), rows.getString(index))
                                    }
                                    target.insertOrThrow(quote(name), null, values)
                                }
                            }
                        }
                        // Preserve AUTOINCREMENT high-water marks, including deleted IDs.
                        if (source.rawQuery("SELECT name FROM sqlite_master WHERE name='sqlite_sequence'", null).use { it.moveToFirst() }) {
                            source.rawQuery("SELECT name,seq FROM sqlite_sequence", null).use { rows ->
                                while (rows.moveToNext()) {
                                    target.execSQL("DELETE FROM sqlite_sequence WHERE name=?", arrayOf(rows.getString(0)))
                                    target.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)", arrayOf(rows.getString(0), rows.getLong(1)))
                                }
                            }
                        }
                        for ((type, _, sql) in definitions) if (type != "table") target.execSQL(sql)
                        target.version = source.version
                        target.setTransactionSuccessful()
                    } finally { target.endTransaction() }
                    check(integrity(target)) { "Launcher backup verification failed" }
                }
            } finally { source.endTransaction() }
            check(temporary.renameTo(destination)) { "Could not retain launcher backup" }
        } catch (failure: Throwable) { temporary.delete(); throw failure }
    }
    private fun integrity(source: SQLiteDatabase) = source.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }

    private fun createTables(source: SQLiteDatabase) {
        source.execSQL("CREATE TABLE apps (package_name TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, last_launched_at INTEGER)")
        source.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0, source INTEGER NOT NULL DEFAULT 0, row_height INTEGER NOT NULL DEFAULT 110, \"order\" INTEGER NOT NULL)")
        source.execSQL("CREATE TABLE apps_categories (category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE, app_package_name TEXT NOT NULL REFERENCES apps(package_name) ON DELETE CASCADE, \"order\" INTEGER NOT NULL, PRIMARY KEY(category_id,app_package_name))")
    }
    private fun migrate(source: SQLiteDatabase) = transaction(source) {
        val appColumns = columns(source, "apps")
        if ("hidden" !in appColumns) source.execSQL("ALTER TABLE apps ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        if ("last_launched_at" !in appColumns) source.execSQL("ALTER TABLE apps ADD COLUMN last_launched_at INTEGER")
        // Rebuild only the pre-row table, with FK enforcement still off, to retain memberships.
        if (source.version < 11) {
            val old = columns(source, "categories")
            val sequence = source.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='categories'", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            source.execSQL("CREATE TABLE categories_native_migration (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,hidden INTEGER NOT NULL DEFAULT 0,sort INTEGER NOT NULL DEFAULT 0,source INTEGER NOT NULL DEFAULT 0,row_height INTEGER NOT NULL DEFAULT 110,\"order\" INTEGER NOT NULL)")
            val sort = if ("sort" in old) "sort" else "0"
            val height = if ("row_height" in old) "row_height" else "110"
            source.execSQL("INSERT INTO categories_native_migration(id,name,sort,row_height,\"order\") SELECT id,name,$sort,$height,\"order\" FROM categories")
            source.execSQL("DROP TABLE categories")
            source.execSQL("ALTER TABLE categories_native_migration RENAME TO categories")
            source.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='categories'", arrayOf(sequence))
            source.execSQL("DROP TABLE IF EXISTS launcher_spacers")
        } else if ("hidden" !in columns(source, "categories")) source.execSQL("ALTER TABLE categories ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        // Obsolete app image columns are harmless and retained rather than risking app identities.
        source.version = 12
    }
    private inline fun <T> transaction(source: SQLiteDatabase = db, block: () -> T): T {
        source.beginTransaction()
        try { val result = block(); source.setTransactionSuccessful(); return result }
        finally { source.endTransaction() }
    }

    private fun initialize(refresh: Boolean) {
        db
        if (!classificationsLoaded) {
            val saved = prefs.string(CLASSIFICATIONS)
            if (saved.isNotEmpty()) runCatching {
                val json = JSONObject(saved)
                json.keys().forEach { key -> if (json.get(key) is Boolean) classifications[key] = json.getBoolean(key) }
            }
            classificationsLoaded = true
        }
        if (!ready || refresh) {
            refreshInventory()
            ready = true
        }
    }

    private fun refreshInventory() {
        // Enumerate completely before touching persisted data; an enumeration failure is inert.
        enumerated = inventory.enumerate().associateBy { it.packageName }
        val known = readApps().associateBy { it.packageName }
        val removed = known.keys.filter { it !in enumerated && !inventory.exists(it) }
        val newlyInstalled = enumerated.keys - known.keys
        transaction {
            for (app in enumerated.values) {
                val values = ContentValues().apply { put("name", app.label); put("version", app.version) }
                if (db.update("apps", values, "package_name=?", arrayOf(app.packageName)) == 0) {
                    values.put("package_name", app.packageName)
                    db.insertOrThrow("apps", null, values)
                }
            }
            for (packageName in removed) db.delete("apps", "package_name=?", arrayOf(packageName))
            migrateRows()
            val rows = readRows()
            for (app in enumerated.values) {
                val previous = classifications[app.packageName]
                val oldSource = if (previous == true) 2 else 1
                val targetSource = if (app.androidApp) 2 else 1
                val previousRows = if (previous != null && previous != app.androidApp)
                    rows.filter { it.source == oldSource && membership(it.id).containsKey(app.packageName) } else emptyList()
                val target = rows.firstOrNull { it.source == targetSource }
                if (target != null && (app.packageName in newlyInstalled || previousRows.isNotEmpty())) {
                    appendMember(target.id, app.packageName)
                    previousRows.forEach { db.delete("apps_categories", "category_id=? AND app_package_name=?", arrayOf(it.id.toString(), app.packageName)) }
                }
                classifications[app.packageName] = app.androidApp
            }
        }
        classifications.keys.retainAll(readApps().map { it.packageName }.toSet())
        prefs.putString(CLASSIFICATIONS, JSONObject(classifications.toMap()).toString())
        // Marker follows the transaction. Source-marked rows make interrupted retries safe.
        if (prefs.int(ROWS_VERSION) < 1) prefs.putInt(ROWS_VERSION, 1)
    }

    private fun migrateRows() {
        if (prefs.int(ROWS_VERSION) >= 1) return
        val rows = readRows().toMutableList()
        fun default(row: LauncherRow) = row.source == 0 && row.sort == 0 && row.height == 110
        val favorites = rows.filter { row -> row.name == "Favorites" && default(row) &&
            membership(row.id).keys.let { it.isEmpty() || it == setOf("app.wuplay.androidtv") } }
        val visible = readApps().filter { !it.hidden }.map { it.packageName }.toMutableSet()
        if (favorites.any { "app.wuplay.androidtv" in membership(it.id) }) visible.remove("app.wuplay.androidtv")
        val allApps = rows.filter { it.name == "All Apps" && default(it) &&
            (visible.isNotEmpty() || membership(it.id).isNotEmpty()) && membership(it.id).keys.containsAll(visible) }
        for (row in favorites + allApps) { db.delete("categories", "id=?", arrayOf(row.id.toString())); rows.remove(row) }
        val builtins = (1..2).map { source ->
            rows.firstOrNull { it.source == source } ?: run {
                val id = insertRow(if (source == 1) "TV Apps" else "Android Apps", source, source - 1)
                for (app in readApps().filter { it.androidApp == (source == 2) }) appendMember(id, app.packageName)
                LauncherRow(id, if (source == 1) "TV Apps" else "Android Apps", false, 0, source, 110, source - 1, emptyList())
            }
        }
        writeRowOrder(builtins + rows.filter { row -> builtins.none { it.id == row.id } })
    }
    private fun readApps(): List<LauncherApp> = db.rawQuery("SELECT package_name,name,version,hidden,last_launched_at FROM apps", null).use { c ->
        buildList { while (c.moveToNext()) {
            val packageName = c.getString(0)
            val artwork = prefs.string("app_artwork_$packageName").takeIf { it in availableArtwork }
            add(LauncherApp(packageName, c.getString(1), c.getString(2), enumerated[packageName]?.androidApp ?: classifications[packageName] ?: false,
                c.getInt(3) != 0, if (c.isNull(4)) null else c.getLong(4) * 1000L, artwork))
        } }
    }
    private fun readRows(): List<LauncherRow> = db.rawQuery("SELECT id,name,hidden,sort,source,row_height,\"order\" FROM categories ORDER BY \"order\",id", null).use { c ->
        buildList { while (c.moveToNext()) add(LauncherRow(c.getLong(0), c.getString(1), c.getInt(2) != 0, c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), emptyList())) }
    }
    private fun membership(id: Long): Map<String, Int> = db.rawQuery("SELECT app_package_name,\"order\" FROM apps_categories WHERE category_id=? ORDER BY \"order\",app_package_name", arrayOf(id.toString())).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
    }
    override fun snapshot(refreshInstalled: Boolean): LibrarySnapshot = synchronized(this) {
        initialize(refreshInstalled)
        val apps = readApps().sortedBy { it.label }
        val byPackage = apps.associateBy { it.packageName }
        val rows = readRows().map { row ->
            val members = membership(row.id)
            val entries = members.keys.mapNotNull { byPackage[it] }.filter { !it.hidden }
            val sorted = when (row.sort) {
                1 -> entries.sortedBy { it.label }
                2 -> entries.sortedByDescending { it.lastLaunchedAt ?: 0L }
                else -> entries.sortedBy { members[it.packageName] ?: 0 }
            }
            row.copy(apps = sorted)
        }
        LibrarySnapshot(apps, rows)
    }
    private fun insertRow(name: String, source: Int, order: Int): Long = db.insertOrThrow("categories", null, ContentValues().apply {
        put("name", name); put("source", source); put("\"order\"", order)
    })
    private fun appendMember(id: Long, packageName: String) {
        if (packageName in membership(id)) return
        val next = (membership(id).values.maxOrNull() ?: -1) + 1
        db.insertOrThrow("apps_categories", null, ContentValues().apply { put("category_id", id); put("app_package_name", packageName); put("\"order\"", next) })
    }
    private fun writeRowOrder(rows: List<LauncherRow>) = rows.forEachIndexed { index, row ->
        db.update("categories", ContentValues().apply { put("\"order\"", index) }, "id=?", arrayOf(row.id.toString()))
    }
    override fun createRow(name: String): Long = synchronized(this) {
        initialize(false); require(name.isNotBlank()) { "Row name is required" }
        insertRow(name.trim(), 0, (readRows().maxOfOrNull { it.order } ?: -1) + 1)
    }
    override fun renameRow(id: Long, name: String) = synchronized(this) {
        initialize(false); require(name.isNotBlank()) { "Row name is required" }
        db.update("categories", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString())); Unit
    }
    override fun updateRow(id: Long, hidden: Boolean, sort: Int, height: Int) = synchronized(this) {
        initialize(false); require(sort in 0..2 && height in 50..500) { "Invalid row options" }
        db.update("categories", ContentValues().apply { put("hidden", if (hidden) 1 else 0); put("sort", sort); put("row_height", height) }, "id=?", arrayOf(id.toString())); Unit
    }
    override fun moveRow(id: Long, delta: Int) = synchronized(this) {
        initialize(false)
        val rows = readRows().toMutableList()
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) { val destination = (index.toLong() + delta).coerceIn(0L, rows.lastIndex.toLong()).toInt(); rows.add(destination, rows.removeAt(index)); transaction { writeRowOrder(rows) } }
    }
    override fun deleteRow(id: Long) = synchronized(this) {
        initialize(false); transaction { db.delete("categories", "id=?", arrayOf(id.toString())); writeRowOrder(readRows()) }; Unit
    }
    override fun rowPackages(id: Long): List<String> = synchronized(this) {
        initialize(false)
        membership(id).keys.toList()
    }
    override fun setRowApps(id: Long, packages: List<String>) = synchronized(this) {
        initialize(false)
        if (readRows().none { it.id == id }) return@synchronized
        val known = readApps().map { it.packageName }.toSet()
        val requested = packages.filter { it in known }.toSet()
        // Keep existing manual order; append new choices alphabetically as the Flutter selector did.
        val previous = membership(id).keys
        val ordered = previous.filter { it in requested } + readApps().filter { it.packageName in requested && it.packageName !in previous }.sortedBy { it.label }.map { it.packageName }
        transaction {
            db.delete("apps_categories", "category_id=?", arrayOf(id.toString()))
            ordered.forEachIndexed { index, packageName -> db.insertOrThrow("apps_categories", null, ContentValues().apply { put("category_id", id); put("app_package_name", packageName); put("\"order\"", index) }) }
        }; Unit
    }
    override fun moveApp(rowId: Long, packageName: String, delta: Int) = synchronized(this) {
        initialize(false)
        val members = membership(rowId).keys.toMutableList()
        val index = members.indexOf(packageName)
        if (index >= 0) {
            val destination = (index.toLong() + delta).coerceIn(0L, members.lastIndex.toLong()).toInt()
            members.add(destination, members.removeAt(index))
            transaction { members.forEachIndexed { order, member -> db.update("apps_categories", ContentValues().apply { put("\"order\"", order) }, "category_id=? AND app_package_name=?", arrayOf(rowId.toString(), member)) } }
        }; Unit
    }
    override fun setHidden(packageName: String, hidden: Boolean) = synchronized(this) {
        initialize(false); db.update("apps", ContentValues().apply { put("hidden", if (hidden) 1 else 0) }, "package_name=?", arrayOf(packageName)); Unit
    }
    override fun setArtwork(packageName: String, asset: String?) = synchronized(this) {
        initialize(false); require(asset == null || asset in availableArtwork) { "Choose packaged app artwork" }
        if (asset == null) prefs.remove("app_artwork_$packageName") else prefs.putString("app_artwork_$packageName", asset)
    }
    override fun recordLaunch(packageName: String) = synchronized(this) {
        initialize(false); db.update("apps", ContentValues().apply { put("last_launched_at", now() / 1000L) }, "package_name=?", arrayOf(packageName)); Unit
    }
    override fun close() = synchronized(this) { database?.close(); database = null; ready = false }
    companion object {
        private const val ROWS_VERSION = "application_rows_layout_version"
        private const val CLASSIFICATIONS = "application_rows_native_classifications"
        private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""
    }
}
