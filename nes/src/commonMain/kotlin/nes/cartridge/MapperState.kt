package nes.cartridge

sealed interface MapperState

data class Mapper0State(var chr: ByteArray = ByteArray(0)) : MapperState
data class Mapper1State(var chr: ByteArray = ByteArray(0), var prgRam: ByteArray = ByteArray(8 * 1024), var registers: IntArray = intArrayOf(0x10, 0x0C, 0, 0, 0)) : MapperState
data class Mapper2State(var chrRam: ByteArray = ByteArray(8 * 1024), var selectedBankBase: Int = 0) : MapperState
data class Mapper3State(var selectedChrBankBase: Int = 0) : MapperState
data class Mapper4State(
    var chr: ByteArray = ByteArray(0),
    var prgRam: ByteArray = ByteArray(0),
    var registers: IntArray = IntArray(8),
    var selectedRegister: Int = 0,
    var prgMode: Boolean = false,
    var chrMode: Boolean = false,
    var irqLatch: Int = 0,
    var irqCounter: Int = 0,
    var irqReload: Boolean = false,
    var irqEnabled: Boolean = false,
    var irqRequested: Boolean = false,
    var mirroring: Mirroring? = null,
    var prgRamEnabled: Boolean = false,
    var prgRamWriteProtected: Boolean = false,
) : MapperState
data class Mapper7State(var chrRam: ByteArray = ByteArray(8 * 1024), var selectedBankBase: Int = 0, var mirroring: Mirroring = Mirroring.SINGLE_SCREEN_LOWER) : MapperState
data class Mapper11State(var selectedPrgBankBase: Int = 0, var selectedChrBankBase: Int = 0) : MapperState
data class Mapper34State(var chr: ByteArray = ByteArray(0), var prgRam: ByteArray = ByteArray(0), var selectedPrgBankBase: Int = 0, var chrBank0Base: Int = 0, var chrBank1Base: Int = 4 * 1024) : MapperState
data class Mapper66State(var selectedPrgBankBase: Int = 0, var selectedChrBankBase: Int = 0) : MapperState
data class Mapper71State(var chrRam: ByteArray = ByteArray(8 * 1024), var selectedPrgBankBase: Int = 0, var firehawkMode: Boolean = false, var mirroring: Mirroring? = null) : MapperState
data class Mapper79State(var selectedPrgBankBase: Int = 0, var selectedChrBankBase: Int = 0, var mirroring: Mirroring? = null) : MapperState
data class Mapper87State(var selectedChrBankBase: Int = 0) : MapperState
