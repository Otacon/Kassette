package nes.cartridge

import nes.ConsoleRegion

enum class Mirroring { HORIZONTAL, VERTICAL, SINGLE_SCREEN_LOWER, SINGLE_SCREEN_UPPER }

class RomFormatException(message: String) : IllegalArgumentException(message)

class Cartridge(
    val mirroring: Mirroring,
    val prgRom: ByteArray,
    val chr: ByteArray,
    val isChrRam: Boolean,
    val trainerPresent: Boolean,
    val mapper: Mapper,
    val region: ConsoleRegion = ConsoleRegion.NTSC,
    val mapperId: Int = 0,
    val submapperId: Int = 0,
    val prgRamSize: Int = 0,
)
