package com.appprogress.core

import java.io.File
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class BackupInfo(val name: String, val file: File, val sizeBytes: Long, val createdAt: Long)

/**
 * Резервные копии базы: %APPDATA%\AppProgress\backups\appprogress-2026-10-18_0900.db.
 * Копия делается командой SQLite «VACUUM INTO» — это целостный снимок, даже пока приложение работает с базой.
 * Восстановление — при следующем запуске: файл-маркер restore.pending говорит, какую копию подставить.
 */
class Backups(
    private val dataDir: File = DatabaseFactory.defaultDataDir(),
    private val dbFile: File = File(dataDir, "appprogress.db"),
) {
    val dir = File(dataDir, "backups")
    private val marker = File(dataDir, "restore.pending")
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    fun list(): List<BackupInfo> = dir.listFiles { f -> f.isFile && f.name.endsWith(".db") }.orEmpty()
        .map { BackupInfo(it.name, it, it.length(), it.lastModified()) }
        .sortedByDescending { it.createdAt }

    fun create(prefix: String = "appprogress"): BackupInfo {
        dir.mkdirs()
        val target = File(dir, "$prefix-${LocalDateTime.now().format(stamp)}.db")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { c ->
            c.createStatement().use { it.execute("VACUUM INTO '${target.absolutePath.replace("'", "''")}'") }
        }
        return BackupInfo(target.name, target, target.length(), target.lastModified())
    }

    /** Оставляем [keep] последних обычных копий. */
    fun prune(keep: Int = KEEP) {
        list().filter { it.name.startsWith("appprogress-") }.drop(keep).forEach { it.file.delete() }
    }

    /** Ежедневная копия: если последней больше 20 часов (или копий нет). Возвращает созданную копию или null. */
    fun dailyIfNeeded(now: Long = System.currentTimeMillis()): BackupInfo? {
        if (!dbFile.exists()) return null
        val last = list().firstOrNull { it.name.startsWith("appprogress-") }?.createdAt
        if (last != null && now - last < 20 * 3600_000L) return null
        return create().also { prune() }
    }

    fun scheduleRestore(backup: BackupInfo) = marker.writeText(backup.file.absolutePath)

    fun cancelRestore() { marker.delete() }

    fun pendingRestore(): File? = marker.takeIf { it.exists() }?.readText()?.trim()?.let(::File)?.takeIf { it.exists() }

    /**
     * Вызывается до открытия базы. Текущая база сначала сохраняется как «before-restore-…», чтобы откат был обратимым.
     */
    fun applyPendingRestore(): Boolean {
        val source = pendingRestore() ?: run { marker.delete(); return false }
        if (dbFile.exists()) {
            dir.mkdirs()
            dbFile.copyTo(File(dir, "before-restore-${LocalDateTime.now().format(stamp)}.db"), overwrite = true)
        }
        source.copyTo(dbFile, overwrite = true)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        marker.delete()
        return true
    }

    companion object {
        const val KEEP = 14
    }
}
