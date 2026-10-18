package com.appprogress.notes

import com.appprogress.core.DatabaseFactory
import com.appprogress.core.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** Вложения заметок — обычные файлы в папке данных: %APPDATA%\AppProgress\attachments. */
class AttachmentStore(val dir: File = File(DatabaseFactory.defaultDataDir(), "attachments")) {
    init { dir.mkdirs() }

    fun file(name: String) = File(dir, File(name).name) // File(name).name — защита от «../» в имени

    fun save(bytes: ByteArray, extension: String): String {
        val name = "${newId().take(8)}.${extension.trimStart('.').lowercase().ifBlank { "bin" }}"
        file(name).writeBytes(bytes)
        return name
    }

    /** Копирует файл во вложения, сохраняя понятное имя: «отчёт-1a2b3c4d.pdf». */
    fun import(source: File): String {
        val base = source.nameWithoutExtension.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").take(40).trim('-').ifBlank { "file" }
        val name = "$base-${newId().take(8)}.${source.extension.lowercase().ifBlank { "bin" }}"
        source.copyTo(file(name))
        return name
    }
}

data class ExchangeResult(val notes: Int, val folders: Int = 0, val attachments: Int = 0, val skipped: Int = 0)

/**
 * Обмен заметками с другими программами через обычные Markdown-файлы.
 * Экспорт: папки → каталоги, заметка → «Название.md», вложения → «attachments/».
 * Импорт: каталог или .zip из Obsidian или Notion; картинки копируются во вложения.
 */
class MarkdownExchange(private val repo: NotesRepository, private val store: AttachmentStore = AttachmentStore()) {

    suspend fun exportAll(target: File): ExchangeResult = withContext(Dispatchers.IO) {
        val notes = repo.observeAll().first()
        val folders = repo.observeFolders().first().associateBy { it.id }
        fun folderPath(id: String?): List<String> {
            val path = mutableListOf<String>()
            var cur = id?.let(folders::get)
            while (cur != null && path.size < 20) { path.add(0, safeName(cur.name)); cur = cur.parentId?.let(folders::get) }
            return path
        }
        val titles = notes.associate { it.id to it.displayTitle }
        val attachmentsDir = File(target, "attachments")
        var attachments = 0
        var skipped = 0
        val used = mutableSetOf<String>()
        notes.forEach { note ->
            if (note.encrypted) { skipped++; return@forEach }
            val dirParts = folderPath(note.folderId)
            val dir = File(target, dirParts.joinToString(File.separator)).apply { mkdirs() }
            var name = safeName(note.displayTitle)
            var n = 2
            while (!used.add("${dir.path}/$name".lowercase())) name = "${safeName(note.displayTitle)} ($n)".also { n++ }
            val up = "../".repeat(dirParts.size)
            val text = note.content
                .replace(NoteMarkup.IMAGE) { m -> copyOut(m.groupValues[2], attachmentsDir)?.let { attachments++; "![${m.groupValues[1]}](${up}attachments/$it)" } ?: m.value }
                .replace(NoteMarkup.FILE) { m -> copyOut(m.groupValues[2], attachmentsDir)?.let { attachments++; "[${m.groupValues[1]}](${up}attachments/$it)" } ?: m.value }
                // Ссылку на заметку превращаем в [[Название]] — так её понимает Obsidian; на задачи и письма — просто текст.
                .replace(NoteMarkup.APP_LINK) { m -> if (m.groupValues[2] == "note") "[[${titles[m.groupValues[3]] ?: m.groupValues[1]}]]" else m.groupValues[1] }
            File(dir, "$name.md").writeText((if (note.title.isNotBlank()) "# ${note.title}\n\n" else "") + text)
        }
        ExchangeResult(notes.size - skipped, folders.size, attachments, skipped)
    }

    private fun copyOut(name: String, dir: File): String? {
        val src = store.file(name)
        if (!src.exists()) return null
        dir.mkdirs()
        src.copyTo(File(dir, src.name), overwrite = true)
        return src.name
    }

    suspend fun import(source: File): ExchangeResult {
        val root = if (source.isFile && source.extension.equals("zip", ignoreCase = true)) unzip(source) else source
        require(root.isDirectory) { "Нужна папка с .md-файлами или .zip-архив" }
        val allFiles = root.walkTopDown().filter { it.isFile }.toList()
        val byName = allFiles.groupBy { it.name.lowercase() }
        val rootPath = root.canonicalPath
        val folderIds = mutableMapOf<String, String>()
        var folders = 0
        var notes = 0
        var attachments = 0

        // Каталоги внутри импортируемой папки становятся папками заметок; сама папка — верхний уровень.
        suspend fun folderFor(dir: File): String? {
            val path = dir.canonicalPath
            if (path == rootPath || !path.startsWith(rootPath)) return null
            folderIds[path]?.let { return it }
            val parent = folderFor(dir.parentFile)
            return repo.createFolder(cleanTitle(dir.name), parent).also { folderIds[path] = it; folders++ }
        }

        fun attach(ref: String, from: File): String? {
            val decoded = runCatching { URLDecoder.decode(ref, Charsets.UTF_8) }.getOrDefault(ref).substringBefore('#').substringBefore('?')
            val direct = File(from.parentFile, decoded).takeIf { it.isFile }
            val found = direct ?: byName[File(decoded).name.lowercase()]?.firstOrNull() ?: return null
            attachments++
            return store.import(found)
        }

        for (file in allFiles.filter { it.extension.equals("md", ignoreCase = true) }) {
            val folder = folderFor(file.parentFile)
            var text = withContext(Dispatchers.IO) { file.readText() }
            // Obsidian: ![[картинка.png]]
            text = text.replace(Regex("""!\[\[([^\]|]+)(?:\|[^\]]*)?]]""")) { m ->
                attach(m.groupValues[1], file)?.let { "![](attachment:$it)" } ?: m.value
            }
            // Обычный Markdown: ![подпись](путь) и [подпись](путь к файлу рядом)
            text = text.replace(Regex("""!\[([^\]]*)]\((?!https?://)([^)]+)\)""")) { m ->
                attach(m.groupValues[2].trim().trim('<', '>'), file)?.let { "![${m.groupValues[1]}](attachment:$it)" } ?: m.value
            }
            text = text.replace(Regex("""(?<!!)\[([^\]]+)]\((?!https?://|app://|attachment:)([^)]+\.(?:pdf|docx?|xlsx?|pptx?|txt|zip|png|jpe?g|gif))\)""", RegexOption.IGNORE_CASE)) { m ->
                attach(m.groupValues[2].trim(), file)?.let { "[${m.groupValues[1]}](attachment:$it)" } ?: m.value
            }
            // Ссылки Notion на другие страницы экспорта (Страница%20abc123.md) → [[Страница]]
            text = text.replace(Regex("""(?<!!)\[([^\]]+)]\((?!https?://)([^)]+\.md)\)""")) { m ->
                "[[${cleanTitle(File(URLDecoder.decode(m.groupValues[2], Charsets.UTF_8)).nameWithoutExtension)}]]"
            }
            var title = cleanTitle(file.nameWithoutExtension)
            // Если файл начинается с «# Заголовок», совпадающего с названием, — не дублируем его в тексте.
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            if (firstLine.startsWith("# ")) {
                title = cleanTitle(firstLine.removePrefix("# ").trim()).ifBlank { title }
                text = text.lines().drop(1).joinToString("\n").trimStart('\n')
            }
            repo.create(title, text, folderId = folder)
            notes++
        }
        if (root != source) root.deleteRecursively()
        return ExchangeResult(notes, folders, attachments)
    }

    private fun unzip(zip: File): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "appprogress-import-${newId().take(8)}").apply { mkdirs() }
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                val out = File(dir, entry.name)
                // Защита от «zip slip»: файл архива не должен выйти за пределы папки распаковки.
                require(out.canonicalPath.startsWith(dir.canonicalPath)) { "Подозрительный путь в архиве: ${entry.name}" }
                if (entry.isDirectory) out.mkdirs() else { out.parentFile.mkdirs(); out.outputStream().use { input.copyTo(it) } }
            }
        }
        return dir
    }

    companion object {
        /** Notion добавляет к именам 32-значный id: «Планы 1a2b…ef» → «Планы». */
        fun cleanTitle(name: String) = name.replace(Regex("""\s+[0-9a-f]{32}$"""), "").trim()

        fun safeName(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80).ifBlank { "Без названия" }
    }
}
