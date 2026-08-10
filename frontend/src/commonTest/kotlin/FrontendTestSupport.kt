import nes.cartridge.InesParser
import nes.cartridge.InesParseResult
import nes.cartridge.RomData
import nes.cartridge.RomFormatException
import kotlin.test.fail

fun ines(prgBanks: Int = 1, chrBanks: Int = 1, flags6: Int = 0, trainer: Boolean = false, prgFill: Int = 0): ByteArray {
    val header = ByteArray(16)
    header[0] = 'N'.code.toByte()
    header[1] = 'E'.code.toByte()
    header[2] = 'S'.code.toByte()
    header[3] = 0x1A
    header[4] = prgBanks.toByte()
    header[5] = chrBanks.toByte()
    header[6] = (flags6 or if (trainer) 4 else 0).toByte()
    val trainerBytes = if (trainer) ByteArray(512) { 0x55 } else ByteArray(0)
    val prg = ByteArray(prgBanks * 16 * 1024) { prgFill.toByte() }
    val chr = ByteArray(chrBanks * 8 * 1024)
    return header + trainerBytes + prg + chr
}

suspend fun InesParser.parse(bytes: ByteArray) = parse(RomData("test.nes", bytes)).cartridgeOrThrow()

suspend fun InesParser.parse(bytes: ByteArray, name: String) = parse(RomData(name, bytes)).cartridgeOrThrow()

fun InesParseResult.cartridgeOrThrow() = when (this) {
    is InesParseResult.Success -> cartridge
    InesParseResult.InvalidRom -> throw RomFormatException("Invalid ROM")
    InesParseResult.UnknownError -> throw RomFormatException("Unable to parse ROM")
}

suspend inline fun <reified T : Throwable> assertFailsWithSuspend(noinline block: suspend () -> Unit): T {
    try {
        block()
    } catch (throwable: Throwable) {
        if (throwable is T) return throwable
        throw throwable
    }
    fail("Expected an exception of ${T::class}")
}
