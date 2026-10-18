package com.appprogress.ui.studio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.appprogress.studio.DiagramData
import org.jetbrains.skia.EncodedImageFormat
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private const val MARGIN = 40f

/** Рисует диаграмму без окна (ImageComposeScene) и сохраняет PNG в файл, выбранный в стандартном диалоге. */
actual fun exportDiagramPng(diagram: DiagramData, title: String): String? {
    val bounds = diagram.bounds() ?: return null
    val png = renderDiagramPng(diagram, bounds)

    val dialog = FileDialog(null as Frame?, "Сохранить диаграмму", FileDialog.SAVE).apply {
        file = title.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".png"
        isVisible = true
    }
    val name = dialog.file ?: return null
    val target = File(dialog.directory, if (name.endsWith(".png", ignoreCase = true)) name else "$name.png")
    target.writeBytes(png)
    return target.absolutePath
}

fun renderDiagramPng(diagram: DiagramData, bounds: FloatArray = diagram.bounds() ?: floatArrayOf(0f, 0f, 100f, 100f)): ByteArray {
    val (minX, minY, maxX, maxY) = bounds.toList()
    val width = (maxX - minX + MARGIN * 2).toInt().coerceAtLeast(1)
    val height = (maxY - minY + MARGIN * 2).toInt().coerceAtLeast(1)
    val scene = ImageComposeScene(width, height, Density(1f)) {
        MaterialTheme(colorScheme = lightColorScheme()) {
            DiagramView(
                diagram = diagram,
                scale = 1f,
                pan = Offset(MARGIN - minX, MARGIN - minY),
                showGrid = false,
                modifier = Modifier.fillMaxSize().background(Color.White),
            )
        }
    }
    try {
        return scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
    } finally {
        scene.close()
    }
}
