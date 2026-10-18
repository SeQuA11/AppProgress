package com.appprogress.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseFactoryTest {
    @Test
    fun reopeningExistingDatabaseKeepsData() = runBlocking {
        val file = File.createTempFile("appprogress", ".db").apply { delete(); deleteOnExit() }
        SettingsRepository(DatabaseFactory.open(file), Dispatchers.IO).put("k", "v")

        // Повторное открытие снова применяет схему (IF NOT EXISTS) и не теряет данные.
        val reopened = SettingsRepository(DatabaseFactory.open(file), Dispatchers.IO)
        assertEquals("v", reopened.get("k"))
    }
}
