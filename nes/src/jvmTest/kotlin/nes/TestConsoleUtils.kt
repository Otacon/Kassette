package nes

import nes.console.NesConsole
import nes.console.NesConsoleOptions
import nes.cpu.ConsoleRegion
import nes.mapper.GameSystem
import nes.mapper.MirroringType
import nes.mapper.NesRomInfo
import nes.mapper.RomData
import nes.mapper.createMapper
import nes.ppu.DefaultNesPpu

internal fun createConsole(resourceName: String): NesConsole {
    val bytes = loadResource(resourceName)
    val rom = parseINes(
        name = resourceName,
        bytes = bytes,
    )

    val mapper = createMapper(rom)
    val ppu = DefaultNesPpu()

    return NesConsole(
        mapper = mapper,
        ppu = ppu,
        options = NesConsoleOptions(
            region = ConsoleRegion.Ntsc,
            randomizeCpuPpuAlignment = false,
            initializeRam = { it.fill(0) },
        ),
    ).also {
        it.powerOn()
    }
}

internal data class BlarggResult(
    val status: Int,
    val output: String,
    val frames: Int,
)

internal fun runBlarggTest(
    console: NesConsole,
    maxFrames: Int,
): BlarggResult {
    repeat(maxFrames) { frame ->

        console.runFrame()

        // Blargg marks $6000+ as valid with:
        // $6001 = DE
        // $6002 = B0
        // $6003 = 61
        if (!console.hasBlarggSignature()) {
            return@repeat
        }

        when (val status = console.peekCpu(0x6000)) {
            0x80 -> Unit
            0x81 -> {
                repeat(7) { console.runFrame() }
                console.reset()
            }

            else -> {
                val output = console.readBlarggOutput()

                if (status != 0) {
                    error(
                        """
                            Blargg instr_test-v5 failed "with status $status after ${frame + 1} frames.
                            
                            $output
                        """.trimIndent()
                    )
                }

                return BlarggResult(
                    status = status,
                    output = output,
                    frames = frame + 1,
                )
            }
        }
    }

    error("Blargg instr_test-v5 did not finish after $maxFrames frames.\n ${console.readBlarggOutput()}")
}

private fun NesConsole.peekCpu(address: Int): Int = memoryManager.debugRead(address) and 0xFF

private fun NesConsole.hasBlarggSignature(): Boolean =
    peekCpu(0x6001) == 0xDE &&
            peekCpu(0x6002) == 0xB0 &&
            peekCpu(0x6003) == 0x61

private fun NesConsole.readBlarggOutput(): String = buildString {
    var address = 0x6004

    while (address <= 0x7FFF) {
        val value = peekCpu(address++)

        if (value == 0) {
            break
        }

        append(value.toChar())
    }
}

private fun loadResource(path: String): ByteArray =
    checkNotNull(NesInstructionRomTest::class.java.getResourceAsStream(path)) { "Resource not found: $path" }
        .use { it.readBytes() }

/**
 * Small iNES 1.0 parser sufficient for Blargg's NROM test ROMs.
 *
 * I would eventually move a real iNES parser into the nes module,
 * but duplicating the frontend parser is unnecessary for this test.
 */
private fun parseINes(
    name: String,
    bytes: ByteArray,
): RomData {
    require(bytes.size >= 16) { "ROM is smaller than an iNES header" }

    require(
        bytes[0].u8() == 'N'.code &&
                bytes[1].u8() == 'E'.code &&
                bytes[2].u8() == 'S'.code &&
                bytes[3].u8() == 0x1A
    ) {
        "Invalid iNES ROM: $name"
    }

    val prgBanks = bytes[4].u8()
    val chrBanks = bytes[5].u8()

    val flags6 = bytes[6].u8()
    val flags7 = bytes[7].u8()

    val hasBattery = flags6 and 0x02 != 0
    val hasTrainer = flags6 and 0x04 != 0

    val mapperId = (flags6 ushr 4) or (flags7 and 0xF0)

    val mirroring = when {
        flags6 and 0x08 != 0 -> MirroringType.FourScreens
        flags6 and 0x01 != 0 -> MirroringType.Vertical
        else -> MirroringType.Horizontal
    }

    var offset = 16

    val trainer = if (hasTrainer) {
        bytes.copyOfRange(offset, offset + 512).also { offset += 512 }
    } else {
        ByteArray(0)
    }

    val prgSize = prgBanks * 16 * 1024
    val chrSize = chrBanks * 8 * 1024

    require(offset + prgSize + chrSize <= bytes.size) { "Invalid iNES sizes in $name" }

    val prgRom = bytes.copyOfRange(offset, offset + prgSize)
    offset += prgSize
    val chrRom = bytes.copyOfRange(offset, offset + chrSize)

    return RomData(
        info = NesRomInfo(
            romName = name,
            filename = name,
            mapperID = mapperId,
            system = GameSystem.NesNtsc,
            hasBattery = hasBattery,
            hasTrainer = hasTrainer,
            mirroring = mirroring,
        ),
        prgRom = prgRom,
        chrRom = chrRom,
        trainerData = trainer,
    )
}

private fun Byte.u8(): Int = toInt() and 0xFF