package com.appprogress.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.appprogress.db.AppDatabase
import java.io.File

object DatabaseFactory {

    /** Папка данных: %APPDATA%\AppProgress на Windows, ~/.local/share/AppProgress на Linux. */
    fun defaultDataDir(): File {
        val os = System.getProperty("os.name").lowercase()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let(::File)
            else -> System.getenv("XDG_DATA_HOME")?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
        } ?: File(System.getProperty("user.home"))
        return File(base, "AppProgress").apply { mkdirs() }
    }

    fun open(file: File = File(defaultDataDir(), "appprogress.db")): AppDatabase =
        AppDatabase(createDriver("jdbc:sqlite:${file.absolutePath}"))

    fun inMemory(): AppDatabase = AppDatabase(createDriver(JdbcSqliteDriver.IN_MEMORY))

    /**
     * Изменения существующих таблиц (новые колонки и т.п.). Каждая миграция — по уникальному имени,
     * а не по номеру версии: модули разрабатываются в разных ветках, и номера бы конфликтовали.
     */
    private val migrations: List<Pair<String, String>> = listOf(
        // "2026-11-01-note-color" to "ALTER TABLE note ADD COLUMN color TEXT",
    )

    private fun createDriver(url: String): SqlDriver {
        val driver = JdbcSqliteDriver(url)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // Все CREATE в .sq-файлах написаны с IF NOT EXISTS, поэтому схему можно применять при каждом запуске:
        // новые таблицы модулей появятся в уже существующей базе, старые не тронутся.
        AppDatabase.Schema.create(driver)
        applyMigrations(driver)
        return driver
    }

    private fun applyMigrations(driver: SqlDriver) {
        for ((name, sql) in migrations) {
            val applied = driver.executeQuery(
                null, "SELECT 1 FROM applied_migration WHERE name = ?",
                { QueryResult.Value(it.next().value) }, 1,
            ) { bindString(0, name) }.value
            if (applied) continue
            driver.execute(null, sql, 0)
            driver.execute(null, "INSERT INTO applied_migration(name, applied_at) VALUES (?, ?)", 2) {
                bindString(0, name)
                bindLong(1, nowMillis())
            }
        }
    }
}
