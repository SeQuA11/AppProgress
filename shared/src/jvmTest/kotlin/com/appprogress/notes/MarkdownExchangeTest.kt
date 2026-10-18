package com.appprogress.notes

import com.appprogress.core.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownExchangeTest {
    private val repo = NotesRepository(DatabaseFactory.inMemory(), Dispatchers.IO)
    private val tmp = Files.createTempDirectory("mdx").toFile().apply { deleteOnExit() }
    private val store = AttachmentStore(File(tmp, "attachments"))
    private val exchange = MarkdownExchange(repo, store)

    @Test
    fun exportWritesFoldersAttachmentsAndObsidianLinks() = runBlocking {
        val work = repo.createFolder("Работа")
        val target = repo.create("Идеи", "текст")
        val pic = store.save(byteArrayOf(1, 2, 3), "png")
        repo.create("План: Q4", "См. [идеи](app://note/$target) и ![схема](attachment:$pic)\nЗадача [Отчёт](app://card/123)", folderId = work)
        repo.setEncryptedContent(repo.create("Секрет", "x"), NoteCrypto.encrypt("x", "1"), true)

        val out = File(tmp, "export")
        val r = exchange.exportAll(out)
        assertEquals(2, r.notes)
        assertEquals(1, r.skipped, "зашифрованные не выгружаются")
        val plan = File(out, "Работа/План_ Q4.md").readText()
        assertTrue("[[Идеи]]" in plan, plan)
        assertTrue("![схема](../attachments/$pic)" in plan, plan)
        assertTrue("Задача Отчёт" in plan)
        assertTrue(File(out, "attachments/$pic").exists())
    }

    @Test
    fun importsObsidianVaultWithImagesAndFolders() = runBlocking {
        val vault = File(tmp, "vault").apply { mkdirs() }
        File(vault, "Проекты").mkdirs()
        File(vault, "media").mkdirs()
        File(vault, "media/фото.png").writeBytes(byteArrayOf(9, 9))
        File(vault, "Проекты/Ремонт.md").writeText("# Ремонт кухни\nСмотри ![[фото.png]] и [[Бюджет|бюджет]]")
        File(vault, "Бюджет.md").writeText("Итого 100")

        val r = exchange.import(vault)
        assertEquals(2, r.notes)
        assertEquals(1, r.attachments)
        val notes = repo.observeAll().first()
        val renovation = notes.single { it.title == "Ремонт кухни" }
        assertTrue(Regex("""!\[]\(attachment:фото-[0-9a-f-]{8}\.png\)""").containsMatchIn(renovation.content), renovation.content)
        assertTrue("[[Бюджет|бюджет]]" in renovation.content)
        assertEquals("Проекты", repo.observeFolders().first().single { it.id == renovation.folderId }.name)
    }

    @Test
    fun importsNotionZipStrippingIds() = runBlocking {
        val zip = File(tmp, "notion.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            put("Работа 0123456789abcdef0123456789abcdef.md", "# Работа\nСм. [Встречи](Встречи%20fedcba9876543210fedcba9876543210.md)")
            put("Встречи fedcba9876543210fedcba9876543210.md", "Список встреч")
        }
        val r = exchange.import(zip)
        assertEquals(2, r.notes)
        val titles = repo.observeAll().first().map { it.title }.toSet()
        assertEquals(setOf("Работа", "Встречи"), titles)
        assertTrue("[[Встречи]]" in repo.observeAll().first().single { it.title == "Работа" }.content)
    }

    @Test
    fun zipSlipIsRejected() {
        val zip = File(tmp, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { z -> z.putNextEntry(ZipEntry("../../evil.md")); z.write("x".toByteArray()); z.closeEntry() }
        val error = runCatching { runBlocking { exchange.import(zip) } }.exceptionOrNull()
        assertTrue(error?.message?.contains("Подозрительный путь") == true)
    }
}
