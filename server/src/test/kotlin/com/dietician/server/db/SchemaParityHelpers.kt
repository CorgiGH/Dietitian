package com.dietician.server.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.DriverManager

/**
 * Helpers for [SchemaParityTest] (council BREAK #4).
 *
 * Compares the Postgres schema produced by Flyway migrations (`server/src/main/resources/db/migration`)
 * against the SQLite schema produced by SQLDelight (the `.sq` files under `shared/src/commonMain/sqldelight`),
 * modulo an allow-list of legitimate client-vs-server splits + type aliases.
 *
 * The check is intentionally LATE — at JVM test time — because both schemas are built from
 * source-of-truth files at gradle time; comparing them in CI catches the class of bug where a
 * developer edits one side and forgets the other (council BREAK #4: sync-cursor-corruption class).
 */

data class ColumnDef(val name: String, val typeNormalized: String, val nullable: Boolean)
data class TableSchema(val name: String, val columns: List<ColumnDef>)

data class AllowList(
    val typeAliases: Map<String, String>,
    val skipped: Set<SkippedCol>,
    val clientOnly: Set<String>,
    val serverOnly: Set<String>,
)
data class SkippedCol(val table: String, val column: String)

/**
 * Introspect Postgres via `information_schema.columns` (public schema only).
 * `data_type` is upper-cased for symmetric comparison with the SQLite side.
 */
fun dumpPgSchema(jdbcUrl: String, user: String, password: String): Map<String, TableSchema> {
    val out = mutableMapOf<String, TableSchema>()
    DriverManager.getConnection(jdbcUrl, user, password).use { conn ->
        val rs = conn.createStatement().executeQuery(
            """
            SELECT table_name, column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
            ORDER BY table_name, ordinal_position
            """.trimIndent()
        )
        val grouped = mutableMapOf<String, MutableList<ColumnDef>>()
        while (rs.next()) {
            val t = rs.getString("table_name")
            grouped.getOrPut(t) { mutableListOf() } += ColumnDef(
                name = rs.getString("column_name"),
                typeNormalized = rs.getString("data_type").uppercase(),
                nullable = rs.getString("is_nullable") == "YES",
            )
        }
        grouped.forEach { (t, cols) -> out[t] = TableSchema(t, cols) }
    }
    return out
}

/**
 * Instantiate the SQLDelight-generated schema against an in-memory SQLite JDBC driver, then
 * introspect via `sqlite_master` + `PRAGMA table_info` to mirror the Postgres dump shape.
 *
 * Notes:
 *  - `nullable` in `PRAGMA table_info` is column 3 = `notnull` flag (0 = nullable, !=0 = NOT NULL),
 *    so we invert it.
 *  - SQLDelight 2.x `QueryResult.Value` is the success branch returned to the mapping lambda.
 */
fun dumpSqldelightSchema(): Map<String, TableSchema> {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        DieticianDatabase.Schema.create(driver)

        val tables = driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) {
                    names += cursor.getString(0)!!
                }
                QueryResult.Value(names)
            },
            parameters = 0,
        ).value

        return tables.associateWith { t ->
            val cols = driver.executeQuery(
                identifier = null,
                // PRAGMA params are not bindable; safe because `t` came from sqlite_master.
                sql = "PRAGMA table_info($t)",
                mapper = { cursor ->
                    val cols = mutableListOf<ColumnDef>()
                    while (cursor.next().value) {
                        cols += ColumnDef(
                            name = cursor.getString(1)!!,
                            typeNormalized = (cursor.getString(2) ?: "").uppercase(),
                            // column 3 = `notnull`; 0 means nullable.
                            nullable = (cursor.getLong(3) ?: 0L) == 0L,
                        )
                    }
                    QueryResult.Value(cols)
                },
                parameters = 0,
            ).value
            TableSchema(t, cols)
        }
    } finally {
        driver.close()
    }
}

fun loadAllowList(): AllowList {
    val text = (
        Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()
        ).getResourceAsStream("schema-parity/allow-list.json")
        ?.bufferedReader()
        ?.readText()
        ?: error("schema-parity/allow-list.json not found on test classpath")
    val root = Json.parseToJsonElement(text).jsonObject
    return AllowList(
        typeAliases = root["type_aliases"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content },
        skipped = root["skipped_columns"]!!.jsonArray.map {
            val o = it.jsonObject
            SkippedCol(o["table"]!!.jsonPrimitive.content, o["column"]!!.jsonPrimitive.content)
        }.toSet(),
        clientOnly = root["client_only_tables"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
        serverOnly = root["server_only_tables"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
    )
}

fun compareSchemas(
    pg: Map<String, TableSchema>,
    sl: Map<String, TableSchema>,
    allow: AllowList,
): List<String> {
    val violations = mutableListOf<String>()
    // Flyway also creates `flyway_schema_history`; never a parity concern.
    val flywayInternal = setOf("flyway_schema_history")
    val sharedTables = (pg.keys + sl.keys) - allow.clientOnly - allow.serverOnly - flywayInternal
    for (t in sharedTables.sorted()) {
        val pgT = pg[t]
        val slT = sl[t]
        if (pgT == null) { violations += "Table $t in SQLDelight but missing from Postgres"; continue }
        if (slT == null) { violations += "Table $t in Postgres but missing from SQLDelight"; continue }
        val pgCols = pgT.columns.associateBy { it.name }
        val slCols = slT.columns.associateBy { it.name }
        val allCols = (pgCols.keys + slCols.keys).sorted()
        for (c in allCols) {
            if (SkippedCol(t, c) in allow.skipped) continue
            val p = pgCols[c]
            val s = slCols[c]
            if (p == null) { violations += "$t.$c in SQLDelight but missing from Postgres"; continue }
            if (s == null) { violations += "$t.$c in Postgres but missing from SQLDelight"; continue }
            val pNorm = allow.typeAliases.entries.fold(p.typeNormalized) { acc, (k, v) ->
                if (acc.contains(k)) v else acc
            }
            val sNorm = s.typeNormalized
            if (!typesEquivalent(pNorm, sNorm)) {
                violations += "$t.$c type diff: pg=${p.typeNormalized}->$pNorm vs sqlite=$sNorm"
            }
        }
    }
    return violations
}

private fun typesEquivalent(a: String, b: String): Boolean {
    val canon = { s: String -> s.replace(" PRIMARY KEY AUTOINCREMENT", "").trim() }
    return canon(a) == canon(b)
}
