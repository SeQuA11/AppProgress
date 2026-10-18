package com.appprogress.ui.notes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.appprogress.notes.AttachmentStore
import com.appprogress.notes.MarkdownExchange
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val store by lazy { AttachmentStore() }

actual fun saveAttachment(bytes: ByteArray, extension: String): String = store.save(bytes, extension)

actual fun clipboardImagePng(): ByteArray? = runCatching {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
    val image = clipboard.getData(DataFlavor.imageFlavor) as java.awt.Image
    val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
    buffered.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    ByteArrayOutputStream().also { ImageIO.write(buffered, "png", it) }.toByteArray()
}.getOrNull()

actual fun pickFilesToAttach(): List<Pair<String, String>> {
    val dialog = FileDialog(null as Frame?, "Прикрепить файлы", FileDialog.LOAD).apply {
        isMultipleMode = true
        isVisible = true
    }
    return dialog.files.orEmpty().map { store.import(it) to it.name }
}

actual suspend fun loadAttachmentImage(name: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching { Image.makeFromEncoded(store.file(name).readBytes()).toComposeImageBitmap() }.getOrNull()
}

actual fun openAttachment(name: String) {
    val file = store.file(name)
    if (file.exists() && Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().open(file) }
}

actual suspend fun exportNotesToFolder(repo: NotesRepository): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Куда сохранить заметки"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val target = File(chooser.selectedFile, "AppProgress-заметки")
    val r = MarkdownExchange(repo).exportAll(target)
    return buildString {
        append("Сохранено заметок: ${r.notes}, вложений: ${r.attachments} → ${target.absolutePath}")
        if (r.skipped > 0) append(". Зашифрованные (${r.skipped}) не выгружались")
    }
}

actual suspend fun importNotesFromDisk(repo: NotesRepository): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Папка с .md-файлами или .zip из Notion"
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileFilter = FileNameExtensionFilter("Папка или архив .zip", "zip")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    return runCatching {
        val r = MarkdownExchange(repo).import(chooser.selectedFile)
        "Импортировано заметок: ${r.notes}, папок: ${r.folders}, вложений: ${r.attachments}"
    }.getOrElse { "Не удалось импортировать: ${it.message}" }
}
