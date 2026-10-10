package io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv

internal actual fun readTextResource(path: String): String {
    val normalizedPath = path.removePrefix("/")
    val candidates = listOf(
        "resources/$normalizedPath",
        "frontend/src/commonMain/resources/$normalizedPath",
        "src/commonMain/resources/$normalizedPath",
        "../Resources/$normalizedPath",
        "${executableDirectory()}/resources/$normalizedPath",
        "${executableDirectory()}/../Resources/$normalizedPath",
    )
    for (candidate in candidates) {
        readFileOrNull(candidate)?.let { return it.decodeToString() }
    }
    error("Missing resource: $path")
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileOrNull(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val size = ftell(file)
        if (size < 0) return null
        if (fseek(file, 0, SEEK_SET) != 0) return null
        val bytes = ByteArray(size.toInt())
        if (bytes.isEmpty()) return bytes
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }.toLong()
        return if (read == size) bytes else null
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun executableDirectory(): String {
    val executable = getenv("_")?.toKString().orEmpty()
    return executable.substringBeforeLast('/', missingDelimiterValue = ".")
}
