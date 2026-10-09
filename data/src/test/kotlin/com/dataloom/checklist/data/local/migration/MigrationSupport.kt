package com.dataloom.checklist.data.local.migration

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/** Read-only helpers shared by the migration tests (CL-320). */

private val ignoredTables = setOf("android_metadata", "room_master_table")

/** Application tables (and the FTS table itself), without SQLite internals and FTS shadow tables. */
fun SupportSQLiteDatabase.userTables(): List<String> = strings(
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
        "AND name NOT LIKE 'item_search_fts_%' ORDER BY name",
).filter { it !in ignoredTables }

fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
}

fun SupportSQLiteDatabase.count(table: String): Long = query("SELECT COUNT(*) FROM `$table`").use {
    it.moveToFirst()
    it.getLong(0)
}

fun SupportSQLiteDatabase.columnNames(table: String): List<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildList { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }

/** Every row of the given columns in a stable order; BLOBs become hex text so they compare by value. */
fun SupportSQLiteDatabase.rows(table: String, columns: List<String>): List<List<String?>> {
    val list = columns.joinToString { "`$it`" }
    val order = columns.indices.joinToString { (it + 1).toString() }
    return query("SELECT $list FROM `$table` ORDER BY $order").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(columns.indices.map { cursor.display(it) })
        }
    }
}

private fun Cursor.display(index: Int): String? = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> null
    Cursor.FIELD_TYPE_BLOB -> getBlob(index).joinToString("") { "%02x".format(it) }
    else -> getString(index)
}

/** The whole content of the database, table by table, for before/after comparison. */
fun SupportSQLiteDatabase.snapshot(): Map<String, Pair<List<String>, List<List<String?>>>> =
    userTables().associateWith { table ->
        val columns = columnNames(table)
        columns to rows(table, columns)
    }

/** `PRAGMA integrity_check` result lines; a healthy database returns just "ok". */
fun SupportSQLiteDatabase.integrityCheck(): List<String> = strings("PRAGMA integrity_check")

/** Rows of `PRAGMA foreign_key_check` as "table -> parent (rowid)"; empty when every reference resolves. */
fun SupportSQLiteDatabase.foreignKeyViolations(): List<String> = query("PRAGMA foreign_key_check").use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            add("${cursor.getString(0)} rowid=${cursor.getLong(1)} -> ${cursor.getString(2)}")
        }
    }
}

/** Structure of the application tables: columns, foreign keys and indices. Triggers are checked separately. */
fun SupportSQLiteDatabase.schemaDescription(): List<String> = buildList {
    userTables().forEach { table ->
        query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                add(
                    "$table column ${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} " +
                        "default=${c.getString(4)} pk=${c.getInt(5)}",
                )
            }
        }
        query("PRAGMA foreign_key_list(`$table`)").use { c ->
            while (c.moveToNext()) {
                add(
                    "$table fk ${c.getString(3)} -> ${c.getString(2)}.${c.getString(4)} " +
                        "update=${c.getString(5)} delete=${c.getString(6)}",
                )
            }
        }
        strings(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table' " +
                "AND sql IS NOT NULL ORDER BY name",
        )
            .forEach { index ->
                val unique = query("PRAGMA index_list(`$table`)").use { c ->
                    var found = 0
                    while (c.moveToNext()) if (c.getString(1) == index) found = c.getInt(2)
                    found
                }
                val columns = query("PRAGMA index_info(`$index`)").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(2)) }
                }
                add("$table index $index unique=$unique columns=$columns")
            }
    }
}
