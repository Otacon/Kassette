package nes.cartridge

import nes.util.low16Bits
import nes.util.toUnsignedInt

class Mapper2(
    private val prgRom: ByteArray,
    private val chrRam: ByteArray,
    private val hasBusConflicts: Boolean = false,
) : Mapper {
    private var state = Mapper2State(chrRam)
    private val bankCount = prgRom.size / PRG_BANK_SIZE
    private var selectedBankBase: Int get() = state.selectedBankBase; set(value) { state.selectedBankBase = value }
    private val fixedBankBase = (bankCount - 1) * PRG_BANK_SIZE

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        val bankBase = if (a < 0xC000) selectedBankBase else fixedBankBase
        val index = bankBase + (a and 0x3FFF)
        return prgRom[index].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if (a >= 0x8000) {
            val v = if (hasBusConflicts) value and cpuRead(a) else value
            selectedBankBase = ((v and 0x0F) % bankCount) * PRG_BANK_SIZE
        }
    }

    override fun ppuRead(address: Int): Int {
        return state.chrRam[address and 0x1FFF].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) {
        state.chrRam[address and 0x1FFF] = value.toByte()
    }

    override fun reset() {
        selectedBankBase = 0
    }

    override fun captureState(): MapperState = state.copy(chrRam = state.chrRam.copyOf())

    override fun restoreState(state: MapperState) {
        state as Mapper2State
        this.state = state.copy(chrRam = state.chrRam.copyOf())
    }

    companion object {
        private const val PRG_BANK_SIZE = 16 * 1024
    }
}
