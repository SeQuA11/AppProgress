package com.appprogress.ui.settings

import com.appprogress.core.Backups
import java.awt.Desktop

private val backups by lazy { Backups() }

actual fun listBackups(): List<BackupRow> =
    backups.list().map { BackupRow(it.name, it.sizeBytes, it.createdAt, automatic = it.name.startsWith("appprogress-")) }

actual fun createBackupNow(): String? = runCatching { backups.create(); backups.prune() }.exceptionOrNull()?.message

actual fun scheduleRestore(name: String) {
    backups.list().firstOrNull { it.name == name }?.let(backups::scheduleRestore)
}

actual fun pendingRestoreName(): String? = backups.pendingRestore()?.name

actual fun cancelRestore() = backups.cancelRestore()

actual fun openBackupsFolder() {
    backups.dir.mkdirs()
    if (Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().open(backups.dir) }
}
