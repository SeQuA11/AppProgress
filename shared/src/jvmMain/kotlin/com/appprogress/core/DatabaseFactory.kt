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

    fun open(file: File = File(defaultDataDir(), "appprogress.db")): AppDatabase {
        // Если пользователь выбрал восстановление из копии, подставляем её до открытия базы.
        Backups(file.absoluteFile.parentFile, file).applyPendingRestore()
        return AppDatabase(createDriver("jdbc:sqlite:${file.absolutePath}"))
    }

    fun inMemory(): AppDatabase = AppDatabase(createDriver(JdbcSqliteDriver.IN_MEMORY))

    /**
     * Изменения существующих таблиц (новые колонки и т.п.). Каждая миграция — по уникальному имени,
     * а не по номеру версии: модули разрабатываются в разных ветках, и номера бы конфликтовали.
     */
    private val migrations: List<Pair<String, String>> = listOf(
        // Заметки, созданные до появления полнотекстового поиска, попадают в индекс один раз.
        "2026-10-note-fts-fill" to
            "INSERT INTO note_fts(note_id, title, content) SELECT id, replace(replace(title, 'ё', 'е'), 'Ё', 'Е'), " +
            "replace(replace(content, 'ё', 'е'), 'Ё', 'Е') FROM note WHERE id NOT IN (SELECT note_id FROM note_fts)",
    )

    /**
     * Колонки, добавленные в существующие таблицы: (таблица, колонка, тип). В новой базе они уже есть
     * в CREATE TABLE, в старой — добавляются здесь. Проверяем по PRAGMA table_info, поэтому порядок и ветки не важны.
     */
    private val addedColumns: List<Triple<String, String, String>> = listOf(
        Triple("note", "folder_id", "TEXT"),
        Triple("note", "note_date", "TEXT"),
        Triple("note", "is_daily", "INTEGER NOT NULL DEFAULT 0"),
        Triple("note", "encrypted", "INTEGER NOT NULL DEFAULT 0"),
        Triple("note", "tags", "TEXT NOT NULL DEFAULT ''"),
        Triple("card", "repeat_rule", "TEXT NOT NULL DEFAULT 'none'"),
    )

    /** Индексы по добавленным колонкам: создаём после того, как колонки точно есть. */
    private val lateIndexes = listOf(
        "CREATE INDEX IF NOT EXISTS note_folder_id ON note(folder_id)",
        "CREATE INDEX IF NOT EXISTS note_date_idx ON note(note_date)",
    )

    private fun createDriver(url: String): SqlDriver {
        val driver = JdbcSqliteDriver(url)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // Все CREATE в .sq-файлах написаны с IF NOT EXISTS, поэтому схему можно применять при каждом запуске:
        // новые таблицы модулей появятся в уже существующей базе, старые не тронутся.
        AppDatabase.Schema.create(driver)
        addMissingColumns(driver)
        applyMigrations(driver)
        lateIndexes.forEach { driver.execute(null, it, 0) }
        return driver
    }

    private fun addMissingColumns(driver: SqlDriver) {
        addedColumns.groupBy { it.first }.forEach { (table, columns) ->
            val existing = driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.next().value) cursor.getString(1)?.let(names::add)
                QueryResult.Value(names)
            }, 0).value
            columns.filter { it.second !in existing }.forEach { (_, column, type) ->
                driver.execute(null, "ALTER TABLE $table ADD COLUMN $column $type", 0)
            }
        }
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
