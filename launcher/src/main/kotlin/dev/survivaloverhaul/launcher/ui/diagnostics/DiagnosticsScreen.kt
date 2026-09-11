package dev.survivaloverhaul.launcher.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import dev.survivaloverhaul.launcher.infrastructure.logging.LogLevel
import dev.survivaloverhaul.launcher.infrastructure.logging.LogLine
import dev.survivaloverhaul.launcher.ui.LauncherStore
import dev.survivaloverhaul.launcher.ui.components.Panel
import dev.survivaloverhaul.launcher.ui.components.QuietButton
import dev.survivaloverhaul.launcher.ui.components.SectionLabel
import dev.survivaloverhaul.launcher.ui.theme.Palette
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * The support screen: what the launcher has been doing, and one button that
 * turns it into something a user can paste somewhere.
 */
@Composable
fun DiagnosticsScreen(
    store: LauncherStore,
    log: LauncherLog,
    modifier: Modifier = Modifier,
) {
    val lines by log.lines.collectAsState()
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    // Follow the tail, the way a log viewer is expected to.
    LaunchedEffect(lines.size) { vertical.scrollTo(vertical.maxValue) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Diagnostics")
                Spacer(Modifier.weight(1f))
                QuietButton(
                    label = "Copy diagnostics",
                    onClick = {
                        copyToClipboard(store.diagnostics())
                        store.note("Diagnostics copied to the clipboard")
                    },
                )
                QuietButton(label = "Open log folder", onClick = { openLogFolder(log, store) })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Log file: " + log.currentFile,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
            )
            store.notice?.let { notice ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Ember,
                )
            }
        }

        Panel(Modifier.fillMaxWidth().weight(1f)) {
            SectionLabel("This session")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.NightDeep, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Column(Modifier.verticalScroll(vertical).horizontalScroll(horizontal)) {
                    lines.forEach { line -> LogRow(line) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    Text(
        text = line.format(),
        color = line.level.tone(),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        softWrap = false,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

private fun LogLevel.tone(): Color = when (this) {
    LogLevel.DEBUG -> Palette.TextFaint
    LogLevel.INFO -> Palette.TextSecondary
    LogLevel.WARN -> Palette.Pending
    LogLevel.ERROR -> Palette.Missing
}

private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

private fun openLogFolder(log: LauncherLog, store: LauncherStore) {
    val folder = log.currentFile.parent?.toFile()
    val opened = runCatching {
        if (folder != null && Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(folder)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (!opened) {
        store.note("Could not open the folder. It is at " + log.currentFile.parent)
    }
}
