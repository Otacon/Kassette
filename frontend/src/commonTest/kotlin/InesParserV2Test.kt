import kotlinx.coroutines.test.runTest
import nes.ConsoleRegion
import nes.cartridge.*
import kotlin.test.*

class InesParserV2Test {
    private val parser = InesParserV2(utils = InesParserUtils())

    @Test
    fun `four-screen mirroring flag parses as four-screen`() = runTest {
        assertEquals(Mirroring.FOUR_SCREEN, parser.parse(nes2(flags6 = 0x08)).mirroring)
    }

    @Test
    fun `valid NES 2 NROM parses PRG ROM and CHR ROM`() = runTest {
        val cartridge = parser.parse(nes2(prgLsb = 1, chrLsb = 1))

        assertEquals(16 * 1024, cartridge.prgRom.size)
        assertEquals(8 * 1024, cartridge.chr.size)
        assertFalse(cartridge.isChrRam)
    }

    @Test
    fun `iNES 1 header throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(ines())
        }

        assertContains(exception.message.orEmpty(), "Expected NES 2.0")
    }

    @Test
    fun `NES 2 exponent multiplier sizes are decoded`() = runTest {
        val cartridge = parser.parse(
            nes2(
                prgLsb = 14 shl 2,
                chrLsb = 13 shl 2,
                sizeMsb = 0xFF,
                prgSize = 16 * 1024,
                chrSize = 8 * 1024,
            ),
        )

        assertEquals(16 * 1024, cartridge.prgRom.size)
        assertEquals(8 * 1024, cartridge.chr.size)
    }

    @Test
    fun `NES 2 MMC1 parses declared CHR RAM`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 4, chrLsb = 0, flags6 = 0x10, chrRamShift = 7),
        )

        assertEquals(8 * 1024, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper1)
    }

    @Test
    fun `NES 2 UxROM uses declared CHR RAM size`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 4, chrLsb = 0, flags6 = 0x20, chrRamShift = 7),
        )

        assertEquals(8 * 1024, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper2)
    }

    @Test
    fun `NES 2 UxROM submapper 2 parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 4, chrLsb = 0, flags6 = 0x20, submapper = 2, chrRamShift = 7),
        )

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper2)
    }

    @Test
    fun `NES 2 AxROM parses declared CHR RAM`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 4, chrLsb = 0, flags6 = 0x70, chrRamShift = 7),
        )

        assertEquals(8 * 1024, cartridge.chr.size)
        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper7)
    }

    @Test
    fun `NES 2 Color Dreams parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 2, flags6 = 0xB0),
        )

        assertTrue(cartridge.mapper is Mapper11)
    }

    @Test
    fun `NES 2 Mapper 34 CHR RAM parses as BNROM`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 0, flags6 = 0x20, flags7Mapper = 0x20, submapper = 2, chrRamShift = 7),
        )

        assertTrue(cartridge.isChrRam)
        assertTrue(cartridge.mapper is Mapper34)
    }

    @Test
    fun `NES 2 GxROM parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 2, flags6 = 0x20, flags7Mapper = 0x40),
        )

        assertTrue(cartridge.mapper is Mapper66)
    }

    @Test
    fun `NES 2 BF909x parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 4, chrLsb = 0, flags6 = 0x70, flags7Mapper = 0x40, chrRamShift = 7),
        )

        assertTrue(cartridge.mapper is Mapper71)
    }

    @Test
    fun `NES 2 NINA-03 parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 2, flags6 = 0xF0, flags7Mapper = 0x40),
        )

        assertTrue(cartridge.mapper is Mapper79)
    }

    @Test
    fun `NES 2 Jaleco JF-xx parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 2, flags6 = 0x70, flags7Mapper = 0x50),
        )

        assertTrue(cartridge.mapper is Mapper87)
    }

    @Test
    fun `NES 2 Mapper 113 parses`() = runTest {
        val cartridge = parser.parse(
            nes2(prgLsb = 2, chrLsb = 2, flags6 = 0x10, flags7Mapper = 0x70),
        )

        assertTrue(cartridge.mapper is Mapper79)
    }

    @Test
    fun `NES 2 extended unsupported mapper throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(prgLsb = 2, chrLsb = 1, flags6 = 0x40, mapperUpper = 1))
        }

        assertContains(exception.message.orEmpty(), "mapper 260")
    }

    @Test
    fun `NES 2 unsupported submapper throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(submapper = 1))
        }

        assertContains(exception.message.orEmpty(), "submapper 1")
    }

    @Test
    fun `NES 2 missing CHR memory throws ROM format exception`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(chrLsb = 0, chrSize = 0))
        }
    }

    @Test
    fun `NES 2 mixed CHR ROM and RAM throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(chrRamShift = 7))
        }

        assertContains(exception.message.orEmpty(), "both CHR ROM and CHR RAM")
    }

    @Test
    fun `NES 2 timing modes parse region`() = runTest {
        assertEquals(ConsoleRegion.NTSC, parser.parse(nes2(timingMode = 0)).region)
        assertEquals(ConsoleRegion.PAL, parser.parse(nes2(timingMode = 1)).region)
        assertEquals(ConsoleRegion.MULTI_REGION, parser.parse(nes2(timingMode = 2)).region)
        assertEquals(ConsoleRegion.DENDY, parser.parse(nes2(timingMode = 3)).region)
    }

    @Test
    fun `NES 2 multi-region timing can be disambiguated by filename`() = runTest {
        assertEquals(ConsoleRegion.PAL, parser.parse(nes2(timingMode = 2), "Game (Europe).nes").region)
        assertEquals(ConsoleRegion.NTSC, parser.parse(nes2(timingMode = 2), "Game (USA).nes").region)
    }

    @Test
    fun `NES 2 explicit timing ignores filename region marker`() = runTest {
        assertEquals(ConsoleRegion.PAL, parser.parse(nes2(timingMode = 1), "Game (USA).nes").region)
    }

    @Test
    fun `NES 2 nonstandard console type throws ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(consoleType = 1))
        }

        assertContains(exception.message.orEmpty(), "console type")
    }

    @Test
    fun `NES 2 miscellaneous ROMs throw ROM format exception`() = runTest {
        val exception = assertFailsWithSuspend<RomFormatException> {
            parser.parse(nes2(miscRomCount = 1))
        }

        assertContains(exception.message.orEmpty(), "miscellaneous ROM")
    }

    private fun nes2(
        prgLsb: Int = 1,
        chrLsb: Int = 1,
        flags6: Int = 0,
        flags7Mapper: Int = 0,
        mapperUpper: Int = 0,
        submapper: Int = 0,
        sizeMsb: Int = 0,
        chrRamShift: Int = 0,
        timingMode: Int = 0,
        consoleType: Int = 0,
        miscRomCount: Int = 0,
        prgSize: Int = prgLsb * 16 * 1024,
        chrSize: Int = chrLsb * 8 * 1024,
    ): ByteArray {
        val header = ByteArray(16)
        header[0] = 'N'.code.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = 0x1A
        header[4] = prgLsb.toByte()
        header[5] = chrLsb.toByte()
        header[6] = flags6.toByte()
        header[7] = (0x08 or flags7Mapper or consoleType).toByte()
        header[8] = ((submapper shl 4) or mapperUpper).toByte()
        header[9] = sizeMsb.toByte()
        header[11] = chrRamShift.toByte()
        header[12] = timingMode.toByte()
        header[14] = miscRomCount.toByte()
        return header + ByteArray(prgSize) + ByteArray(chrSize)
    }

    private fun ines(): ByteArray {
        val header = ByteArray(16)
        header[0] = 'N'.code.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = 0x1A
        header[4] = 1
        header[5] = 1
        return header + ByteArray(16 * 1024) + ByteArray(8 * 1024)
    }
}
