package nes.cartridge

import co.touchlab.kermit.Logger
import io.Nes20Db
import io.Nes20DbEntry
import io.sha1Hex

class InesParserComposite(
    private val inesParserV1: InesParserV1,
    private val inesParserV2: InesParserV2,
    private val nes20Db: Nes20Db,
    private val utils: InesParserUtils,
) : InesParser {
    private val log = Logger.withTag("InesParserComposite")

    override suspend fun parse(romData: RomData): Cartridge {
        val bytes = romData.bytes
        utils.validateHeader(bytes)
        val sha1 = sha1Hex(utils.romBytesForHash(bytes))
        val result = sha1?.let { nes20Db.findBySha1(it) }
        if (result != null) {
            log.d { "Found $sha1 entry on nes20DB: $result" }
        } else {
            log.d { "No results found for ${sha1?.uppercase()}" }
        }
        val cartridge = if (utils.isNes2(bytes)) {
            log.d { "ROM format: iNES 2.0" }
            inesParserV2.parse(romData)
        } else {
            log.d { "ROM format: iNES 1.0" }
            inesParserV1.parse(romData)
        }
        return result?.let { cartridge.withNes20DbMetadata(it) } ?: cartridge
    }

    private fun Cartridge.withNes20DbMetadata(entry: Nes20DbEntry): Cartridge {
        utils.validateMapperSizes(
            mapper = entry.mapper,
            submapper = entry.submapper,
            prgSize = prgRom.size.toLong(),
            chrRomSize = if (isChrRam) 0 else chr.size.toLong(),
            chrRamSize = if (isChrRam) chr.size else 0,
            prgRamSize = entry.prgRamSize,
        )
        return Cartridge(
            mirroring = entry.mirroring,
            prgRom = prgRom,
            chr = chr,
            isChrRam = isChrRam,
            trainerPresent = trainerPresent,
            mapper = utils.createMapper(entry.mapper, entry.submapper, prgRom, chr, isChrRam, entry.prgRamSize),
            region = entry.region,
        )
    }
}
