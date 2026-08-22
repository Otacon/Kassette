package nes.cartridge

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual suspend fun unzipRom(zipData: RomData): UnzipRomResult = try {
    if (zipData.bytes.size < 4 || zipData.bytes[0] != 'P'.code.toByte() || zipData.bytes[1] != 'K'.code.toByte()) {
        return UnzipRomResult.UnknownError
    }
    val nesRoms = buildList {
        ZipInputStream(ByteArrayInputStream(zipData.bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".nes", ignoreCase = true)) {
                    add(RomData(entry.name.substringAfterLast('/'), zip.readBytes()))
                }
                zip.closeEntry()
            }
        }
    }
    when (nesRoms.size) {
        0 -> UnzipRomResult.NotFound
        1 -> UnzipRomResult.Success(nesRoms.single())
        else -> UnzipRomResult.MultipleRoms
    }
} catch (_: Throwable) {
    UnzipRomResult.UnknownError
}
