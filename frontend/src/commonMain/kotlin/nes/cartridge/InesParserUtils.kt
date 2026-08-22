package nes.cartridge

import co.touchlab.kermit.Logger
import nes.ConsoleRegion
import nes.util.toUnsignedInt

class InesParserUtils {
    private val log = Logger.withTag("InesParserUtils")

    fun validateHeader(bytes: ByteArray) {
        if (bytes.size < HEADER_SIZE) {
            log.e { "Truncated ROM: missing iNES header" }
            throw RomFormatException("Truncated ROM: missing iNES header")
        }
        if (bytes[0] != 'N'.code.toByte() ||
            bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'S'.code.toByte() ||
            bytes[3] != 0x1A.toByte()
        ) {
            log.e { "Invalid iNES header: expected NES<EOF> magic bytes" }
            throw RomFormatException("Invalid iNES header: expected NES<EOF> magic bytes")
        }
    }

    fun isNes2(bytes: ByteArray): Boolean = (bytes[7].toUnsignedInt() and 0x0C) == 0x08

    fun romBytesForHash(bytes: ByteArray): ByteArray {
        val trainer = (bytes[6].toUnsignedInt() and 0x04) != 0
        val offset = HEADER_SIZE + if (trainer) TRAINER_SIZE else 0
        if (bytes.size < offset) {
            throw RomFormatException("Truncated ROM: missing trainer")
        }
        return bytes.copyOfRange(offset, bytes.size)
    }

    fun regionFromFilename(name: String): ConsoleRegion? {
        val normalized = name.uppercase()
        return when {
            normalized.contains("(E)") ||
                    normalized.contains("[E]") ||
                    normalized.contains("(EUROPE)") ||
                    normalized.contains("[EUROPE]") ||
                    normalized.contains("(PAL)") ||
                    normalized.contains("[PAL]") -> ConsoleRegion.PAL
            normalized.contains("(U)") ||
                    normalized.contains("[U]") ||
                    normalized.contains("(USA)") ||
                    normalized.contains("[USA]") ||
                    normalized.contains("(J)") ||
                    normalized.contains("[J]") ||
                    normalized.contains("(JAPAN)") ||
                    normalized.contains("[JAPAN]") -> ConsoleRegion.NTSC
            else -> null
        }
    }

    fun createCartridge(
        bytes: ByteArray,
        flags6: Int,
        mapper: Int,
        submapper: Int,
        prgRomSize: Long,
        chrRomSize: Long,
        chrRamSize: Int,
        prgRamSize: Int,
        region: ConsoleRegion,
    ): Cartridge {
        val trainer = (flags6 and 0x04) != 0
        var offset = HEADER_SIZE + if (trainer) TRAINER_SIZE else 0
        val prgSize = prgRomSize.toInt()
        val chrSize = chrRomSize.toInt()
        val required = offset + prgSize + chrSize
        if (bytes.size < required) {
            log.e { "Truncated ROM: expected at least $required bytes, found ${bytes.size}" }
            throw RomFormatException("Truncated ROM: expected at least $required bytes, found ${bytes.size}")
        }

        val prg = bytes.copyOfRange(offset, offset + prgSize)
        log.d { "PRG ROM: ${prg.size / 1024} KiB" }
        offset += prgSize
        val isChrRam = chrSize == 0
        val chr = if (isChrRam) {
            log.d { "CHR: ${chrRamSize / 1024} KiB RAM" }
            ByteArray(chrRamSize)
        } else {
            log.d { "CHR: ${chrSize / 1024} KiB ROM" }
            bytes.copyOfRange(offset, offset + chrSize)
        }
        val mirroring = if ((flags6 and 0x01) != 0) {
            log.d { "Mirroring: Vertical" }
            Mirroring.VERTICAL
        } else {
            log.d { "Mirroring: Horizontal" }
            Mirroring.HORIZONTAL
        }
        return Cartridge(
            mirroring = mirroring,
            prgRom = prg,
            chr = chr,
            isChrRam = isChrRam,
            trainerPresent = trainer,
            region = region,
            mapperId = mapper,
            submapperId = submapper,
            prgRamSize = prgRamSize,
            mapper = createMapper(mapper, submapper, prg, chr, isChrRam, prgRamSize),
        )
    }

    fun validateMapperSizes(mapper: Int, submapper: Int, prgSize: Long, chrRomSize: Long, chrRamSize: Int, prgRamSize: Int) {
        if (!supportsSubmapper(mapper, submapper)) {
            throw RomFormatException("Unsupported submapper $submapper for Mapper $mapper")
        }
        val chrSize = if (chrRomSize != 0L) chrRomSize else chrRamSize.toLong()
        when (mapper) {
            0 -> {
                if (prgSize != PRG_BANK_SIZE.toLong() && prgSize != 2L * PRG_BANK_SIZE) invalidSize("PRG ROM", mapper, prgSize)
                if (chrSize != CHR_BANK_SIZE.toLong()) invalidSize(if (chrRomSize == 0L) "CHR RAM" else "CHR ROM", mapper, chrSize)
            }
            1 -> {
                val prgBanks = prgSize / PRG_BANK_SIZE
                if (prgSize !in (2L * PRG_BANK_SIZE)..(16L * PRG_BANK_SIZE) || prgSize % PRG_BANK_SIZE != 0L || !prgBanks.isPowerOfTwo()) {
                    invalidSize("PRG ROM", mapper, prgSize)
                }
                if (chrRomSize == 0L) {
                    if (chrRamSize != CHR_BANK_SIZE) throw RomFormatException("Invalid CHR memory for Mapper 1: MMC1 requires 8 KiB CHR RAM")
                } else if (chrRomSize !in CHR_BANK_SIZE.toLong()..(16L * CHR_BANK_SIZE) || chrRomSize % MMC1_CHR_BANK_SIZE != 0L) {
                    invalidSize("CHR ROM", mapper, chrRomSize)
                }
            }
            2 -> {
                if (prgSize !in (2L * PRG_BANK_SIZE)..(16L * PRG_BANK_SIZE) || prgSize % PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize != 0L || chrRamSize != CHR_BANK_SIZE) throw RomFormatException("Invalid CHR memory for Mapper 2: UxROM requires 8 KiB CHR RAM")
            }
            3 -> {
                if (prgSize != PRG_BANK_SIZE.toLong() && prgSize != 2L * PRG_BANK_SIZE) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(4L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            4 -> {
                if (prgSize !in (2L * PRG_BANK_SIZE)..(32L * PRG_BANK_SIZE) || prgSize % MMC3_PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrSize !in CHR_BANK_SIZE.toLong()..(32L * CHR_BANK_SIZE) || chrSize % MMC3_CHR_BANK_SIZE != 0L) invalidSize(if (chrRomSize == 0L) "CHR RAM" else "CHR ROM", mapper, chrSize)
                if (prgRamSize != 0 && prgRamSize != 8 * 1024) invalidSize("PRG RAM", mapper, prgRamSize.toLong())
            }
            7 -> {
                if (prgSize !in AXROM_PRG_BANK_SIZE.toLong()..(16L * AXROM_PRG_BANK_SIZE) ||
                    prgSize % AXROM_PRG_BANK_SIZE != 0L
                ) {
                    invalidSize("PRG ROM", mapper, prgSize)
                }
                if (chrRomSize != 0L || chrRamSize != CHR_BANK_SIZE) {
                    throw RomFormatException("Invalid CHR memory for Mapper 7: AxROM requires 8 KiB CHR RAM")
                }
            }
            11 -> {
                if (prgSize !in COLOR_DREAMS_PRG_BANK_SIZE.toLong()..(16L * COLOR_DREAMS_PRG_BANK_SIZE) ||
                    prgSize % COLOR_DREAMS_PRG_BANK_SIZE != 0L
                ) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(16L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            34 -> {
                val usesNina = submapper == 1 || (submapper == 0 && chrRomSize != 0L)
                if (prgSize !in BNROM_PRG_BANK_SIZE.toLong()..(256L * BNROM_PRG_BANK_SIZE) || prgSize % BNROM_PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (usesNina) {
                    if (chrRomSize !in MMC1_CHR_BANK_SIZE.toLong()..(32L * CHR_BANK_SIZE) || chrRomSize % MMC1_CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
                } else if (chrRomSize != 0L || chrRamSize != CHR_BANK_SIZE) {
                    throw RomFormatException("Invalid CHR memory for Mapper 34: BNROM requires 8 KiB CHR RAM")
                }
            }
            66 -> {
                if (prgSize !in GXROM_PRG_BANK_SIZE.toLong()..(4L * GXROM_PRG_BANK_SIZE) || prgSize % GXROM_PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(4L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            71 -> {
                if (prgSize !in (2L * PRG_BANK_SIZE)..(16L * PRG_BANK_SIZE) || prgSize % PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize != 0L || chrRamSize != CHR_BANK_SIZE) throw RomFormatException("Invalid CHR memory for Mapper 71: BF909x requires 8 KiB CHR RAM")
            }
            79 -> {
                if (prgSize !in NINA_PRG_BANK_SIZE.toLong()..(2L * NINA_PRG_BANK_SIZE) || prgSize % NINA_PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(8L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            87 -> {
                if (prgSize != PRG_BANK_SIZE.toLong() && prgSize != 2L * PRG_BANK_SIZE) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(4L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            113 -> {
                if (prgSize !in NINA_PRG_BANK_SIZE.toLong()..(8L * NINA_PRG_BANK_SIZE) || prgSize % NINA_PRG_BANK_SIZE != 0L) invalidSize("PRG ROM", mapper, prgSize)
                if (chrRomSize !in CHR_BANK_SIZE.toLong()..(16L * CHR_BANK_SIZE) || chrRomSize % CHR_BANK_SIZE != 0L) invalidSize("CHR ROM", mapper, chrRomSize)
            }
            else -> {
                log.e { "Unsupported mapper $mapper; only Mapper 0, 1, 2, 3, 4, 7, 11, 34, 66, 71, 79, 87, and 113 are supported" }
                throw RomFormatException("Unsupported mapper $mapper; only Mapper 0, 1, 2, 3, 4, 7, 11, 34, 66, 71, 79, 87, and 113 are supported")
            }
        }
    }

    fun createMapper(mapper: Int, submapper: Int, prg: ByteArray, chr: ByteArray, isChrRam: Boolean, prgRamSize: Int): Mapper = when (mapper) {
        0 -> Mapper0(prgRom = prg, chr = chr, isChrRam = isChrRam)
        1 -> Mapper1(prgRom = prg, chr = chr, isChrRam = isChrRam)
        2 -> Mapper2(prgRom = prg, chrRam = chr, hasBusConflicts = submapper == 2)
        3 -> Mapper3(prgRom = prg, chrRom = chr, hasBusConflicts = submapper == 2)
        4 -> Mapper4(prgRom = prg, chr = chr, isChrRam = isChrRam, prgRamSize = prgRamSize)
        7 -> Mapper7(prgRom = prg, chrRam = chr, hasBusConflicts = submapper == 2)
        11 -> Mapper11(prgRom = prg, chrRom = chr)
        34 -> Mapper34(prgRom = prg, chr = chr, isChrRam = isChrRam, prgRamSize = prgRamSize, forceNina = submapper == 1)
        66 -> Mapper66(prgRom = prg, chrRom = chr)
        71 -> Mapper71(prgRom = prg, chrRam = chr, bf9097Mode = submapper == 1)
        79 -> Mapper79(prgRom = prg, chrRom = chr)
        87 -> Mapper87(prgRom = prg, chrRom = chr)
        113 -> Mapper79(prgRom = prg, chrRom = chr, multicartMode = true)
        else -> error("Unsupported mapper $mapper")
    }

    private fun Long.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0L

    private fun supportsSubmapper(mapper: Int, submapper: Int): Boolean = when (mapper) {
        2, 3, 7 -> submapper == 0 || submapper == 2
        34 -> submapper in 0..2
        71 -> submapper == 0 || submapper == 1
        else -> submapper == 0
    }

    private fun invalidSize(memory: String, mapper: Int, size: Long): Nothing {
        val message = "Invalid $memory size for Mapper $mapper: ${size / 1024} KiB"
        log.e { message }
        throw RomFormatException(message)
    }

    companion object {
        const val HEADER_SIZE = 16
        const val TRAINER_SIZE = 512
        const val PRG_BANK_SIZE = 16 * 1024
        const val CHR_BANK_SIZE = 8 * 1024
        private const val MMC1_CHR_BANK_SIZE = 4 * 1024
        private const val MMC3_PRG_BANK_SIZE = 8 * 1024
        private const val MMC3_CHR_BANK_SIZE = 1024
        private const val AXROM_PRG_BANK_SIZE = 32 * 1024
        private const val COLOR_DREAMS_PRG_BANK_SIZE = 32 * 1024
        private const val BNROM_PRG_BANK_SIZE = 32 * 1024
        private const val GXROM_PRG_BANK_SIZE = 32 * 1024
        private const val NINA_PRG_BANK_SIZE = 32 * 1024
    }
}
