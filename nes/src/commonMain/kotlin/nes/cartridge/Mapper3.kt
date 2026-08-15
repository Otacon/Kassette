package nes.cartridge

import nes.util.low16Bits
import nes.util.toUnsignedInt

class Mapper3(
    private val prgRom: ByteArray,
    private val chrRom: ByteArray,
    private val hasBusConflicts: Boolean = false,
) : Mapper {
    private var state = Mapper3State()
    private val chrBankCount = chrRom.size / CHR_BANK_SIZE
    private val prgMask = if (prgRom.size == PRG_BANK_SIZE) 0x3FFF else 0x7FFF
    private var selectedChrBankBase: Int get() = state.selectedChrBankBase; set(value) { state.selectedChrBankBase = value }

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        return prgRom[a and prgMask].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if (a >= 0x8000) {
            val v = if (hasBusConflicts) value and cpuRead(a) else value
            selectedChrBankBase = (v % chrBankCount) * CHR_BANK_SIZE
        }
    }

    override fun ppuRead(address: Int): Int {
        val index = selectedChrBankBase + (address and 0x1FFF)
        return chrRom[index].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) = Unit

    override fun reset() {
        selectedChrBankBase = 0
    }

    override fun captureState(): MapperState = state.copy()

    override fun restoreState(state: MapperState) {
        state as Mapper3State
        this.state = state.copy()
    }

    companion object {
        private const val PRG_BANK_SIZE = 16 * 1024
        private const val CHR_BANK_SIZE = 8 * 1024
    }
}
