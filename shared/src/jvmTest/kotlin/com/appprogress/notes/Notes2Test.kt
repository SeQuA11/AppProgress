package com.appprogress.notes

import com.appprogress.core.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Notes2Test {
    private var time = 1_000_000L
    private val repo = NotesRepository(DatabaseFactory.inMemory(), Dispatchers.IO) { time }

    @Test
    fun fullTextSearchHandlesCyrillicCaseYoAndPrefixes() = runBlocking {
        val a = repo.create("Отпуск в Краснодаре", "Купить билеты и ёлочные игрушки")
        repo.create("Работа", "Отчёт за квартал")
        assertEquals(listOf(a), repo.search("отп кра"))
        assertEquals(listOf(a), repo.search("ЕЛОЧНЫЕ"), "ё = е, регистр не важен")
        assertTrue(repo.search("\"; DROP TABLE note; --").isEmpty(), "спецсимволы не ломают запрос")
    }

    @Test
    fun searchFollowsEdits() = runBlocking {
        val id = repo.create("Черновик", "старый текст")
        repo.updateText(id, "Черновик", "новый текст про кофе")
        assertEquals(listOf(id), repo.search("кофе"))
        assertTrue(repo.search("старый").isEmpty())
    }

    @Test
    fun tagsAreExtractedAndStored() = runBlocking {
        repo.create("Покупки", "Список #дом #покупки, см. https://site.ru/#anchor\n# Заголовок не тег\n`#код` тоже нет")
        assertEquals(listOf("дом", "покупки"), repo.observeSummaries().first().single().tags)
    }

    @Test
    fun versionsAreSnapshottedAtMostEveryTenMinutes() = runBlocking {
        val id = repo.create("План", "v1")
        repo.updateText(id, "План", "v2")
        time += 60_000
        repo.updateText(id, "План", "v3")
        assertEquals(listOf("v1"), repo.versions(id).map { it.content }, "вторая правка в течение 10 минут — без новой версии")
        time += 10 * 60_000
        repo.updateText(id, "План", "v4")
        assertEquals(listOf("v3", "v1"), repo.versions(id).map { it.content })

        repo.restoreVersion(id, repo.versions(id).last())
        assertEquals("v1", repo.get(id)!!.content)
        assertEquals("v4", repo.versions(id).first().content, "текущий текст сохранился перед откатом")
    }

    @Test
    fun trashRestoreAndPurge() = runBlocking {
        val keep = repo.create("Вернуть")
        val old = repo.create("Старое")
        repo.delete(keep)
        repo.delete(old)
        assertEquals(2, repo.observeTrash().first().size)
        repo.restore(keep)
        assertEquals(listOf(keep), repo.observeSummaries().first().map { it.id })

        time += 31L * 24 * 3600_000
        repo.purgeTrash()
        assertTrue(repo.observeTrash().first().isEmpty())
        assertNull(repo.get(old))
    }

    @Test
    fun deletingFolderMovesNotesUp() = runBlocking {
        val work = repo.createFolder("Работа")
        val project = repo.createFolder("Проект", work)
        val note = repo.create("Задачи проекта", folderId = project)
        repo.deleteFolder(NoteFolder(project, "Проект", work))
        assertEquals(work, repo.get(note)!!.folderId)
        assertEquals(work, repo.ensureFolder("Работа"), "ensureFolder находит существующую папку")
    }

    @Test
    fun backlinksByTitleAndById() = runBlocking {
        val target = repo.create("Идеи", "")
        val a = repo.create("Встреча", "Обсудили [[Идеи]] и решили")
        val b = repo.create("Итоги", "См. [заметку](app://note/$target)")
        repo.create("Другое", "без ссылок")
        assertEquals(setOf(a, b), repo.backlinks(repo.get(target)!!).toSet())
        assertEquals(target, repo.openOrCreateByTitle("идеи"))
        val created = repo.openOrCreateByTitle("Новая тема")
        assertEquals("Новая тема", repo.get(created)!!.title)
    }

    @Test
    fun dailyNoteIsCreatedOncePerDay() = runBlocking {
        val day = LocalDate(2026, 10, 18)
        val first = repo.openOrCreateDaily(day) { "# План дня" }
        val again = repo.openOrCreateDaily(day) { error("не должно вызываться") }
        assertEquals(first, again)
        val note = repo.get(first)!!
        assertTrue(note.isDaily && note.date == day)
    }

    @Test
    fun encryptionRoundTripAndWrongPassword() {
        val secret = NoteCrypto.encrypt("пароль от wi-fi: 12345", "кот")
        assertTrue(secret.startsWith("enc1:") && "12345" !in secret)
        assertEquals("пароль от wi-fi: 12345", NoteCrypto.decrypt(secret, "кот"))
        assertNull(NoteCrypto.decrypt(secret, "собака"))
    }

    @Test
    fun encryptedNoteHidesContentFromTagsAndPreview() = runBlocking {
        val id = repo.create("Секрет", "#личное текст")
        repo.setEncryptedContent(id, NoteCrypto.encrypt("#личное текст", "1"), encrypted = true)
        val s = repo.observeSummaries().first().single()
        assertTrue(s.tags.isEmpty())
        assertEquals("Зашифрованная заметка", s.previewLine)
    }

    @Test
    fun checkboxToggleAndPlainPreview() {
        val text = "Дела\n- [ ] Купить хлеб\n- [x] Позвонить"
        assertEquals("Дела\n- [x] Купить хлеб\n- [x] Позвонить", NoteMarkup.toggleCheckbox(text, 1))
        assertEquals("Дела\n- [ ] Купить хлеб\n- [ ] Позвонить", NoteMarkup.toggleCheckbox(text, 2))
        assertEquals("Отчёт и Идеи", NoteMarkup.plain("## [Отчёт](app://card/1) и [[Идеи]]"))
    }

    /** База от первой версии приложения: при открытии получает новые колонки, индекс поиска и не теряет заметки. */
    @Test
    fun upgradesDatabaseFromFirstVersion() = runBlocking {
        val file = File.createTempFile("old", ".db").apply { delete(); deleteOnExit() }
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.execute(
                    "CREATE TABLE note (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL DEFAULT '', content TEXT NOT NULL DEFAULT '', " +
                        "pinned INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)",
                )
                st.execute("INSERT INTO note VALUES ('old1', 'Старая заметка', 'Про #рыбалку и снасти', 0, 1, 1, NULL)")
            }
        }
        val upgraded = NotesRepository(DatabaseFactory.open(file), Dispatchers.IO)
        upgraded.maintenance()
        assertEquals(listOf("old1"), upgraded.search("снасти"))
        assertEquals(listOf("рыбалку"), upgraded.observeSummaries().first().single().tags)
        // Повторное открытие не дублирует строки в индексе.
        assertEquals(listOf("old1"), NotesRepository(DatabaseFactory.open(file), Dispatchers.IO).search("снасти"))
    }
}
