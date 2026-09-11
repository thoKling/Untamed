package dev.untamed.launcher.ui.settings

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * The system file chooser, wrapped so a missing or unhappy windowing toolkit
 * cannot take the launcher down with it.
 *
 * Swing is already running underneath Compose Desktop, so this costs nothing
 * extra and gives the platform's own dialog.
 */
object FilePickers {

    fun chooseDirectory(title: String, startingAt: String?): String? =
        choose(title, startingAt, JFileChooser.DIRECTORIES_ONLY)

    fun chooseFile(title: String, startingAt: String?): String? =
        choose(title, startingAt, JFileChooser.FILES_ONLY)

    private fun choose(title: String, startingAt: String?, mode: Int): String? = runCatching {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = mode
            isMultiSelectionEnabled = false
            startingAt?.let { path ->
                val file = File(path)
                currentDirectory = if (file.isDirectory) file else file.parentFile
                if (mode == JFileChooser.FILES_ONLY && file.isFile) selectedFile = file
            }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }.getOrNull()
}
