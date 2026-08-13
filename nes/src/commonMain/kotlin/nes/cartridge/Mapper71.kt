package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper71(
    private val prgRom: ByteArray,
    private val chrRam: ByteArray,
    bf9097Mode: Boolean = false,
) : Mapper {
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private var selectedPrgBankBase = 0
    private val fixedPrgBankBase = (prgBankCount - 1) * PRG_BANK_SIZE
    private var firehawkMode = bf9097Mode
    private var mirroring: Mirroring? = null

    override fun cpuRead(address: Int): Int = cpuRead(address, 0)

    override fun cpuRead(address: Int, openBus: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return openBus.low8Bits()
        val bankBase = if (a < 0xC000) selectedPrgBankBase else fixedPrgBankBase
        return prgRom[bankBase + (a and 0x3FFF)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if (a < 0x8000) return
        val v = value.low8Bits()
        if (a == 0x9000) firehawkMode = true
        if (a >= 0xC000 || !firehawkMode) {
            selectedPrgBankBase = (v % prgBankCount) * PRG_BANK_SIZE
        } else {
            mirroring = if ((v and 0x10) != 0) Mirroring.SINGLE_SCREEN_LOWER else Mirroring.SINGLE_SCREEN_UPPER
        }
    }

    override fun ppuRead(address: Int): Int = chrRam[address and 0x1FFF].toUnsignedInt()

    override fun ppuWrite(address: Int, value: Int) {
        chrRam[address and 0x1FFF] = value.toByte()
    }

    override fun mirroring(): Mirroring? = if (firehawkMode) mirroring else null

    override fun captureState(): MapperState = Mapper71State(chrRam.copyOf(), selectedPrgBankBase, firehawkMode, mirroring)

    override fun restoreState(state: MapperState) {
        state as Mapper71State
        state.chrRam.copyInto(chrRam)
        selectedPrgBankBase = state.selectedPrgBankBase
        firehawkMode = state.firehawkMode
        mirroring = state.mirroring
    }

    private companion object {
        const val PRG_BANK_SIZE = 16 * 1024
    }
}
