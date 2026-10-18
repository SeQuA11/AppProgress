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

    private fun createDriver(url: String): SqlDriver {
        val driver = JdbcSqliteDriver(url)
        migrate(driver)
        return driver
    }

    /**
     * Версию схемы храним в PRAGMA user_version самого файла базы.
     * 0 — база новая: создаём таблицы; меньше текущей — применяем миграции по порядку.
     */
    private fun migrate(driver: SqlDriver) {
        val schema = AppDatabase.Schema
        val current = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0).value
        when {
            current == 0L -> schema.create(driver)
            current < schema.version -> schema.migrate(driver, current, schema.version)
            else -> return
        }
        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
    }
}
