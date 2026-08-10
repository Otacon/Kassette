package nes.cartridge

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UnzipRomJvmTest {
    @Test
    fun `returns single NES ROM`() = runTest {
        val romBytes = byteArrayOf(1, 2, 3)
        val result = unzipRom(RomData("game.zip", zipOf("folder/game.nes" to romBytes)))

        val success = assertIs<UnzipRomResult.Success>(result)
        assertEquals("game.nes", success.romData.name)
        assertContentEquals(romBytes, success.romData.bytes)
    }

    @Test
    fun `returns NotFound when archive has no NES ROMs`() = runTest {
        val result = unzipRom(RomData("game.zip", zipOf("readme.txt" to byteArrayOf(1))))

        assertEquals(UnzipRomResult.NotFound, result)
    }

    @Test
    fun `returns MultipleRoms when archive has multiple NES ROMs`() = runTest {
        val result = unzipRom(
            RomData(
                "games.zip",
                zipOf(
                    "one.nes" to byteArrayOf(1),
                    "two.nes" to byteArrayOf(2),
                ),
            )
        )

        assertEquals(UnzipRomResult.MultipleRoms, result)
    }

    @Test
    fun `returns UnknownError when archive cannot be unzipped`() = runTest {
        val result = unzipRom(RomData("game.zip", byteArrayOf(1, 2, 3)))

        assertEquals(UnzipRomResult.UnknownError, result)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
