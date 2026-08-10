package frontend

import nes.cartridge.RomData
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import kotlin.io.path.readBytes

actual class FileChooser(
    private val parent: Frame
) {
    actual suspend fun pickRom(): RomData? {
        val dialog = FileDialog(parent, "Open NES ROM", FileDialog.LOAD)
        return try {
            dialog.filenameFilter = java.io.FilenameFilter { _, name ->
                name.endsWith(".nes", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)
            }
            dialog.isVisible = true
            val file = dialog.file ?: return null
            val path = Path.of(dialog.directory, file)
            RomData(path.fileName.toString(), path.readBytes())
        } finally {
            dialog.dispose()
        }
    }
}
