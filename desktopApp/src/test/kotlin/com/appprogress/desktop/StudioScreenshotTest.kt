package com.appprogress.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.appprogress.studio.ChartSpec
import com.appprogress.studio.ChartType
import com.appprogress.studio.DiagramData
import com.appprogress.studio.DocKind
import com.appprogress.studio.StudioRepository
import com.appprogress.studio.TableData
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.studio.StudioScreen
import com.appprogress.ui.studio.renderDiagramPng
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class StudioScreenshotTest {
    private fun seed(chartType: ChartType = ChartType.Bar): suspend (AppContainer) -> Unit = { c ->
        val repo = StudioRepository(c.db, c.io)
        repo.createDiagram("Карта мыслей", DiagramData.mindMapTemplate())
        repo.createDiagram("Блок-схема", DiagramData.flowchartTemplate())
        val table = repo.createTable("Бюджет", TableData.budgetSample())
        repo.createChart("Бюджет по месяцам", ChartSpec(tableId = table, type = chartType))
    }

    /** Рисует экран студии на нужной вкладке (вкладку в App выбирает пользователь, поэтому рисуем экран напрямую). */
    private fun shootStudio(name: String, kind: DocKind, chartType: ChartType = ChartType.Bar) {
        val container = Screenshots.demoContainer(seed(chartType))
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            AppTheme(ThemeMode.Light) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StudioScreen(container, kind)
                }
            }
        }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            Screenshots.save(scene, name)
        } finally {
            scene.close()
        }
    }

    @Test fun table() = Screenshots.shoot("studio-table", Section.Studio, seed = seed())
    @Test fun barChart() = shootStudio("studio-chart-bar", DocKind.Chart)
    @Test fun pieChart() = shootStudio("studio-chart-pie", DocKind.Chart, ChartType.Pie)
    @Test fun diagram() = shootStudio("studio-diagram", DocKind.Diagram)
    @Test fun diagramDark() = Screenshots.shoot("studio-table-dark", Section.Studio, ThemeMode.Dark, seed = seed())

    @Test
    fun exportDiagramToPng() {
        val png = renderDiagramPng(DiagramData.flowchartTemplate())
        File("build/screenshots/studio-diagram-export.png").writeBytes(png)
        assertTrue(png.size > 5_000)
    }
}
