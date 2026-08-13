package io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

actual fun platformSavestateStore(): SavestateStore = JvmSavestateStore()

private class JvmSavestateStore : SavestateStore {
    private val directory: Path = Path.of(System.getProperty("user.home"), ".kassette", "savestates")

    override fun saveState(sha: String, number: Int, data: ByteArray) {
        Files.createDirectories(directory)
        path(sha, number).writeBytes(data)
    }

    override fun loadState(sha: String, number: Int): ByteArray? {
        val path = path(sha, number)
        return if (path.exists()) path.readBytes() else null
    }

    override fun hasState(sha: String, number: Int): Boolean = path(sha, number).exists()

    private fun path(sha: String, number: Int): Path = directory.resolve("$sha-$number.savestate")
}
