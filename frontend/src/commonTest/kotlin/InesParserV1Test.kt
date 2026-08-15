import kotlinx.coroutines.test.runTest
import nes.ConsoleRegion
import nes.cartridge.*
import nes.util.toUnsignedInt
import kotlin.test.*

class InesParserV1Test {
    private val parser = InesParserV1(utils = InesParserUtils())

    @Test
    fun `valid NROM-128 parses PRG ROM and CHR ROM`() = runTest {
        val cartridge = parser.parse(ines(1, 1))

        assertEquals(16 * 1024, cartridge.prgRom.size)
        assertFalse(cartridge.isChrRam)
    }

    @Test
    fun `valid NROM-256 parses PRG ROM`() = runTest {
        val cartridge = parser.parse(ines(2, 1))

        assertEquals(32 * 1024, cartridge.prgRom.size)
    }

    @Test
    fun `valid MMC1 parses PRG ROM and CHR ROM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 2, flags6 = 0x10))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(16 * 1024, cartridge.chr.size)
        assertFalse(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper1)
    }

    @Test
    fun `valid UxROM parses PRG ROM and CHR RAM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 0, flags6 = 0x20))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(8192, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper2)
    }

    @Test
    fun `valid CNROM parses PRG ROM and CHR ROM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 2, chrBanks = 4, flags6 = 0x30))

        assertEquals(32 * 1024, cartridge.prgRom.size)
        assertEquals(32 * 1024, cartridge.chr.size)
        assertFalse(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper3)
    }

    @Test
    fun `valid MMC3 parses PRG ROM and CHR ROM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 2, flags6 = 0x40))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(16 * 1024, cartridge.chr.size)
        assertFalse(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper4)
    }

    @Test
    fun `valid MMC3 with CHR RAM parses PRG ROM and CHR RAM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 0, flags6 = 0x40))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(8192, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper4)
    }

    @Test
    fun `valid AxROM parses PRG ROM and CHR RAM`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 0, flags6 = 0x70))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(8192, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper7)
    }

    @Test
    fun `parser skips trainer bytes before PRG ROM`() = runTest {
        val cartridge = parser.parse(ines(1, 1, trainer = true, prgFill = 0x42))

        assertTrue(cartridge.trainerPresent)
        assertEquals(0x42, cartridge.prgRom[0].toUnsignedInt())
    }

    @Test
    fun `horizontal mirroring flag parses as horizontal`() = runTest {
        assertEquals(Mirroring.HORIZONTAL, parser.parse(ines(flags6 = 0)).mirroring)
    }

    @Test
    fun `vertical mirroring flag parses as vertical`() = runTest {
        assertEquals(Mirroring.VERTICAL, parser.parse(ines(flags6 = 1)).mirroring)
    }

    @Test
    fun `four-screen mirroring flag parses as four-screen`() = runTest {
        assertEquals(Mirroring.FOUR_SCREEN, parser.parse(ines(flags6 = 0x08)).mirroring)
    }

    @Test
    fun `PAL TV system flag parses PAL region`() = runTest {
        val rom = ines().also { it[9] = 1 }

        assertEquals(ConsoleRegion.PAL, parser.parse(rom).region)
    }

    @Test
    fun `dual-compatible TV system flag parses multi-region`() = runTest {
        val rom = ines().also { it[10] = 1 }

        assertEquals(ConsoleRegion.MULTI_REGION, parser.parse(rom).region)
    }

    @Test
    fun `extended PAL TV system flag parses PAL region`() = runTest {
        val rom = ines().also { it[10] = 2 }

        assertEquals(ConsoleRegion.PAL, parser.parse(rom).region)
    }

    @Test
    fun `PAL TV system flag takes precedence over extended dual-compatible flag`() = runTest {
        val rom = ines().also {
            it[9] = 1
            it[10] = 1
        }

        assertEquals(ConsoleRegion.PAL, parser.parse(rom).region)
    }

    @Test
    fun `PAL filename marker overrides missing iNES region metadata`() = runTest {
        assertEquals(ConsoleRegion.PAL, parser.parse(ines(), "Game (Europe).nes").region)
        assertEquals(ConsoleRegion.PAL, parser.parse(ines(), "Game [E].nes").region)
        assertEquals(ConsoleRegion.PAL, parser.parse(ines(), "Game (PAL).nes").region)
    }

    @Test
    fun `USA and Japan filename markers parse as NTSC`() = runTest {
        assertEquals(ConsoleRegion.NTSC, parser.parse(ines().also { it[9] = 1 }, "Game (USA).nes").region)
        assertEquals(ConsoleRegion.NTSC, parser.parse(ines().also { it[9] = 1 }, "Game (Japan).nes").region)
    }

    @Test
    fun `zero CHR banks creates CHR RAM`() = runTest {
        val cartridge = parser.parse(ines(1, 0))

        assertTrue(cartridge.isChrRam)
        assertEquals(8192, cartridge.chr.size)
    }

    @Test
    fun `invalid magic throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ByteArray(16))
        }
    }

    @Test
    fun `NES 2 header throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2())
        }

        assertContains(exception.message.orEmpty(), "Expected iNES 1.0")
    }

    @Test
    fun `truncated data throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines().copyOf(20))
        }
    }

    @Test
    fun `unsupported mapper throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(flags6 = 0x50))
        }

        assertContains(exception.message.orEmpty(), "mapper 5")
    }

    @Test
    fun `UxROM with CHR ROM throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 4, chrBanks = 1, flags6 = 0x20))
        }
    }

    @Test
    fun `UxROM with one PRG bank throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 1, chrBanks = 0, flags6 = 0x20))
        }
    }

    @Test
    fun `CNROM with CHR RAM throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 2, chrBanks = 0, flags6 = 0x30))
        }
    }

    @Test
    fun `CNROM with invalid PRG size throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 3, chrBanks = 1, flags6 = 0x30))
        }
    }

    @Test
    fun `MMC3 with one PRG bank throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 1, chrBanks = 1, flags6 = 0x40))
        }
    }

    @Test
    fun `AxROM with CHR ROM throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines(prgBanks = 4, chrBanks = 1, flags6 = 0x70))
        }
    }

    private fun nes2(): ByteArray {
        val header = ByteArray(16)
        header[0] = 'N'.code.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = 0x1A
        header[4] = 1
        header[5] = 1
        header[7] = 0x08
        return header + ByteArray(16 * 1024) + ByteArray(8 * 1024)
    }
}
