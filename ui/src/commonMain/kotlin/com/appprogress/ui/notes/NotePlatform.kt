package com.appprogress.ui.notes

import androidx.compose.ui.graphics.ImageBitmap
import com.appprogress.notes.NotesRepository

/** Сохраняет байты как вложение и возвращает имя файла для ссылки attachment:имя. */
expect fun saveAttachment(bytes: ByteArray, extension: String): String

/** Картинка из буфера обмена в PNG (скриншот, копия из браузера) или null, если там не картинка. */
expect fun clipboardImagePng(): ByteArray?

/** Диалог выбора файлов; выбранные копируются во вложения. Возвращает пары (имя вложения, исходное имя). */
expect fun pickFilesToAttach(): List<Pair<String, String>>

expect suspend fun loadAttachmentImage(name: String): ImageBitmap?

/** Открывает вложение программой по умолчанию (PDF — в просмотрщике PDF и т.д.). */
expect fun openAttachment(name: String)

/** Экспорт всех заметок в выбранную папку. Возвращает текст результата или null, если пользователь отменил. */
expect suspend fun exportNotesToFolder(repo: NotesRepository): String?

/** Импорт из папки или .zip (Obsidian, Notion, любые .md). Возвращает текст результата или null при отмене. */
expect suspend fun importNotesFromDisk(repo: NotesRepository): String?
