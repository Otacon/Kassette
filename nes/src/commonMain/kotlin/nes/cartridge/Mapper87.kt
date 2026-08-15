package nes.cartridge

import nes.util.low16Bits
import nes.util.toUnsignedInt

class Mapper87(
    private val prgRom: ByteArray,
    private val chrRom: ByteArray,
) : Mapper {
    private var state = Mapper87State()
    private val chrBankCount = chrRom.size / CHR_BANK_SIZE
    private val prgMask = if (prgRom.size == 16 * 1024) 0x3FFF else 0x7FFF
    private var selectedChrBankBase: Int get() = state.selectedChrBankBase; set(value) { state.selectedChrBankBase = value }

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        return prgRom[a and prgMask].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if (a !in 0x6000..0x7FFF) return
        val bank = ((value and 0x01) shl 1) or ((value and 0x02) shr 1)
        selectedChrBankBase = (bank % chrBankCount) * CHR_BANK_SIZE
    }

    override fun ppuRead(address: Int): Int = chrRom[selectedChrBankBase + (address and 0x1FFF)].toUnsignedInt()

    override fun ppuWrite(address: Int, value: Int) = Unit

    override fun reset() {
        selectedChrBankBase = 0
    }

    override fun captureState(): MapperState = state.copy()

    override fun restoreState(state: MapperState) {
        state as Mapper87State
        this.state = state.copy()
    }

    private companion object {
        const val CHR_BANK_SIZE = 8 * 1024
    }
}
