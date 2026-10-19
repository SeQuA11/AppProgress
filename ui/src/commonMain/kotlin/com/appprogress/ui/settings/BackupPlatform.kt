package com.appprogress.ui.settings

/** Резервная копия базы для показа в настройках. */
data class BackupRow(val name: String, val sizeBytes: Long, val createdAt: Long, val automatic: Boolean)

expect fun listBackups(): List<BackupRow>

/** Создаёт копию сейчас. Возвращает текст ошибки или null. */
expect fun createBackupNow(): String?

/** Восстановление при следующем запуске приложения. */
expect fun scheduleRestore(name: String)

expect fun pendingRestoreName(): String?

expect fun cancelRestore()

expect fun openBackupsFolder()
