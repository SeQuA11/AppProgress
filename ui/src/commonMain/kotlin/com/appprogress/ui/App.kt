package com.appprogress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.appprogress.ui.common.ComingSoonScreen
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.Navigator
import com.appprogress.ui.settings.SettingsScreen
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private const val THEME_KEY = "ui.theme"

@Composable
fun App(
    container: AppContainer,
    initialSection: Section = Section.Notes,
    initialTheme: ThemeMode = ThemeMode.System,
) {
    var section by remember { mutableStateOf(initialSection) }
    var theme by remember { mutableStateOf(initialTheme) }
    val setTheme: (ThemeMode) -> Unit = { mode ->
        theme = mode
        container.scope.launch { container.settings.put(THEME_KEY, mode.name) }
    }
    LaunchedEffect(Unit) {
        container.settings.get(THEME_KEY)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?.let { theme = it }
    }

    val navigator = remember { Navigator { section = it } }

    AppTheme(theme) {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            // Surface задаёт цвет текста по умолчанию (onBackground) — без него текст в тёмной теме был бы чёрным.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(section, version = container.version, onSelect = { section = it })
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxSize()) {
                        val content = section.content
                        when {
                            section == Section.Settings -> SettingsScreen(theme, setTheme, container)
                            content != null -> content(container)
                            else -> ComingSoonScreen(section)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Section, version: String, onSelect: (Section) -> Unit) {
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "AppProgress",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 16.dp),
        )
        Section.entries.filter { it != Section.Settings }.forEach { s ->
            SidebarItem(s, selected = s == current, onClick = { onSelect(s) })
        }
        Spacer(Modifier.weight(1f))
        SidebarItem(Section.Settings, selected = current == Section.Settings, onClick = { onSelect(Section.Settings) })
        Text(
            "Версия $version",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun SidebarItem(section: Section, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        Icon(section.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            section.title,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (!section.ready) {
            Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                Text(
                    "скоро",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
