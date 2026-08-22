import io.Nes20Db
import io.Nes20DbEntry
import kotlinx.coroutines.test.runTest
import nes.ConsoleRegion
import nes.cartridge.*
import kotlin.test.*

class InesParserCompositeTest {

    private val utils = InesParserUtils()
    private val parser = InesParserComposite(
        nes20Db = EmptyNes20Db,
        inesParserV1 = InesParserV1(utils),
        inesParserV2 = InesParserV2(utils),
        utils = InesParserUtils()
    )


    @Test
    fun `routes iNES 1 ROMs to V1 parser`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 0, flags6 = 0x20))

        assertEquals(64 * 1024, cartridge.prgRom.size)
        assertEquals(2, cartridge.mapperId)
        assertEquals(0, cartridge.submapperId)
    }

    @Test
    fun `routes NES 2 ROMs to V2 parser`() = runTest {
        val cartridge = parser.parse(nes20(prgLsb = 1, chrLsb = 1))

        assertEquals(16 * 1024, cartridge.prgRom.size)
        assertFalse(cartridge.isChrRam)
    }

    @Test
    fun `keeps ROM metadata when Nes20Db has no match`() = runTest {
        val cartridge = parser.parse(ines(prgBanks = 1, chrBanks = 1, flags6 = 0))

        assertEquals(ConsoleRegion.NTSC, cartridge.region)
        assertEquals(Mirroring.HORIZONTAL, cartridge.mirroring)
        assertEquals(0, cartridge.mapperId)
    }

    @Test
    fun `overrides ROM metadata from Nes20Db match`() = runTest {
        val dbEntry = Nes20DbEntry(
            sha1 = "0000000000000000000000000000000000000000",
            region = ConsoleRegion.PAL,
            mapper = 3,
            submapper = 0,
            mirroring = Mirroring.VERTICAL,
            prgRamSize = 0,
        )
        val parser = InesParserComposite(
            nes20Db = SingleEntryNes20Db(dbEntry),
            inesParserV1 = InesParserV1(utils),
            inesParserV2 = InesParserV2(utils),
            utils = utils,
        )

        val cartridge = parser.parse(ines(prgBanks = 1, chrBanks = 1, flags6 = 0))

        assertEquals(ConsoleRegion.PAL, cartridge.region)
        assertEquals(Mirroring.VERTICAL, cartridge.mirroring)
        assertEquals(3, cartridge.mapperId)
        assertEquals(0, cartridge.submapperId)
    }

    @Test
    fun `Nes20Db Mapper 2 submapper 2 override parses`() = runTest {
        val dbEntry = Nes20DbEntry(
            sha1 = "0000000000000000000000000000000000000000",
            region = ConsoleRegion.NTSC,
            mapper = 2,
            submapper = 2,
            mirroring = Mirroring.VERTICAL,
            prgRamSize = 0,
        )
        val parser = InesParserComposite(
            nes20Db = SingleEntryNes20Db(dbEntry),
            inesParserV1 = InesParserV1(utils),
            inesParserV2 = InesParserV2(utils),
            utils = utils,
        )

        val cartridge = parser.parse(ines(prgBanks = 4, chrBanks = 0, flags6 = 0x20))

        assertEquals(Mirroring.VERTICAL, cartridge.mirroring)
        assertEquals(2, cartridge.mapperId)
        assertEquals(2, cartridge.submapperId)
    }

    @Test
    fun `invalid magic throws before routing`() = runTest {
        assertFailsWithSuspend<RomFormatException> {
            parser.parse(ByteArray(16))
        }
    }

    private fun nes20(
        prgLsb: Int = 1,
        chrLsb: Int = 1,
        flags6: Int = 0,
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
        header[7] = (0x08 or consoleType).toByte()
        header[8] = ((submapper shl 4) or mapperUpper).toByte()
        header[9] = sizeMsb.toByte()
        header[11] = chrRamShift.toByte()
        header[12] = timingMode.toByte()
        header[14] = miscRomCount.toByte()
        return header + ByteArray(prgSize) + ByteArray(chrSize)
    }

    private object EmptyNes20Db : Nes20Db {
        override fun findBySha1(sha1: String): Nes20DbEntry? = null
    }

    private class SingleEntryNes20Db(
        private val entry: Nes20DbEntry,
    ) : Nes20Db {
        override fun findBySha1(sha1: String): Nes20DbEntry = entry
    }
}
