package com.appprogress.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.appprogress.core.DatabaseFactory
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.assertTrue

/**
 * Рисует экраны без открытия окна и сохраняет PNG в desktopApp/build/screenshots.
 * Каждый модуль кладёт рядом свой *ScreenshotTest и вызывает [shoot] со своими демо-данными.
 */
object Screenshots {
    private val outDir = File("build/screenshots").apply { mkdirs() }

    fun demoContainer(seed: suspend (AppContainer) -> Unit = {}): AppContainer {
        val container = AppContainer(
            db = DatabaseFactory.inMemory(),
            io = Dispatchers.IO,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            dataLocation = "C:\\Users\\demo\\AppData\\Roaming\\AppProgress",
            version = APP_VERSION,
        )
        runBlocking { seed(container) }
        return container
    }

    fun shoot(
        name: String,
        section: Section,
        theme: ThemeMode = ThemeMode.Light,
        width: Int = 1280,
        height: Int = 800,
        seed: suspend (AppContainer) -> Unit = {},
    ) {
        val container = demoContainer(seed)
        val scene = ImageComposeScene(width, height, Density(1f)) { App(container, section, theme) }
        try {
            // Несколько кадров с паузами: данные из базы приходят асинхронно.
            var time = 0L
            repeat(8) {
                scene.render(time)
                Thread.sleep(120)
                time += 100_000_000L
            }
            save(scene, name, time)
        } finally {
            scene.close()
        }
    }

    /** Сохраняет текущий кадр сцены в PNG (для тестов, которые сами двигают мышь). */
    fun save(scene: ImageComposeScene, name: String, nanoTime: Long = System.nanoTime()) {
        val png = scene.render(nanoTime).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(outDir, "$name.png").writeBytes(png)
        assertTrue(png.size > 10_000, "скриншот $name подозрительно пустой")
    }
}
