package com.appprogress.core

import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SettingsRepository(private val db: AppDatabase, private val io: CoroutineDispatcher) {
    suspend fun get(key: String): String? = withContext(io) { db.settingQueries.get(key).executeAsOneOrNull() }
    suspend fun put(key: String, value: String) = withContext(io) { db.settingQueries.put(key, value) }
}
