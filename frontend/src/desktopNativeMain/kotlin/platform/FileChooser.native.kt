package platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import nes.cartridge.RomData
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
actual class FileChooser {
    actual suspend fun pickRom(): RomData? {
        val panel = NSOpenPanel.openPanel()
        panel.title = "Open NES ROM"
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false

        if (panel.runModal() != NSModalResponseOK) return null
        val path = panel.URL?.path ?: return null
        require(path.endsWith(".nes", ignoreCase = true)) { "Native PoC only accepts .nes ROM files" }
        return RomData(path.substringAfterLast('/'), readBytes(path))
    }

    private fun readBytes(path: String): ByteArray {
        val file = fopen(path, "rb") ?: error("Unable to open ROM: $path")
        try {
            check(fseek(file, 0, SEEK_END) == 0) { "Unable to seek ROM: $path" }
            val size = ftell(file)
            check(size >= 0) { "Unable to size ROM: $path" }
            check(fseek(file, 0, SEEK_SET) == 0) { "Unable to rewind ROM: $path" }
            val bytes = ByteArray(size.toInt())
            if (bytes.isEmpty()) return bytes
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }.toLong()
            check(read == size) { "Unable to read ROM: $path" }
            return bytes
        } finally {
            fclose(file)
        }
    }
}
