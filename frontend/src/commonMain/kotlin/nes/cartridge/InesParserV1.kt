package nes.cartridge

import co.touchlab.kermit.Logger
import nes.ConsoleRegion
import nes.util.toUnsignedInt

class InesParserV1(
    private val utils: InesParserUtils,
) : InesParser {
    private val log = Logger.withTag("InesParserV1")

    override suspend fun parse(romData: RomData): InesParseResult = try {
        InesParseResult.Success(parseCartridge(romData))
    } catch (_: RomFormatException) {
        InesParseResult.InvalidRom
    } catch (_: Throwable) {
        InesParseResult.UnknownError
    }

    private fun parseCartridge(romData: RomData): Cartridge {
        val bytes = romData.bytes
        utils.validateHeader(bytes)
        if (utils.isNes2(bytes)) {
            throw RomFormatException("Expected iNES 1.0 ROM, found NES 2.0 header")
        }
        val prgBanks = bytes[4].toUnsignedInt()
        val chrBanks = bytes[5].toUnsignedInt()
        val flags6 = bytes[6].toUnsignedInt()
        val flags7 = bytes[7].toUnsignedInt()
        val prgRamSize = decodePrgRamSize(bytes)
        val region = utils.regionFromFilename(romData.name) ?: decodeRegion(bytes)
        log.d { "Region: $region" }
        val mapper = (flags6 shr 4) or (flags7 and 0xF0)
        val prgRomSize = prgBanks.toLong() * InesParserUtils.PRG_BANK_SIZE
        val chrRomSize = chrBanks.toLong() * InesParserUtils.CHR_BANK_SIZE
        val chrRamSize = if (chrRomSize == 0L) InesParserUtils.CHR_BANK_SIZE else 0
        log.d { "Mapper: $mapper" }

        utils.validateMapperSizes(
            mapper = mapper,
            submapper = 0,
            prgSize = prgRomSize,
            chrRomSize = chrRomSize,
            chrRamSize = chrRamSize,
            prgRamSize = prgRamSize,
        )
        return utils.createCartridge(
            bytes = bytes,
            flags6 = flags6,
            mapper = mapper,
            submapper = 0,
            prgRomSize = prgRomSize,
            chrRomSize = chrRomSize,
            chrRamSize = chrRamSize,
            prgRamSize = prgRamSize,
            region = region,
        )
    }

    private fun decodePrgRamSize(bytes: ByteArray): Int {
        val banks = bytes[8].toUnsignedInt()
        return (if (banks == 0) 1 else banks) * 8 * 1024
    }

    private fun decodeRegion(bytes: ByteArray): ConsoleRegion {
        require(bytes.size >= 16) { "Invalid NES header" }
        val byte9Region = bytes[9].toUnsignedInt() and 0x01
        val byte10Region = bytes[10].toUnsignedInt() and 0x03

        if (byte9Region == 1) return ConsoleRegion.PAL
        return when (byte10Region) {
            2 -> ConsoleRegion.PAL
            1, 3 -> ConsoleRegion.MULTI_REGION
            else -> ConsoleRegion.NTSC
        }
    }

}
