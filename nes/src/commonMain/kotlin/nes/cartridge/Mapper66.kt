package nes.cartridge

import nes.util.low16Bits
import nes.util.toUnsignedInt

class Mapper66(
    private val prgRom: ByteArray,
    private val chrRom: ByteArray,
) : Mapper {
    private var state = Mapper66State()
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private val chrBankCount = chrRom.size / CHR_BANK_SIZE
    private var selectedPrgBankBase: Int get() = state.selectedPrgBankBase; set(value) { state.selectedPrgBankBase = value }
    private var selectedChrBankBase: Int get() = state.selectedChrBankBase; set(value) { state.selectedChrBankBase = value }

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        return prgRom[selectedPrgBankBase + (a and 0x7FFF)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        if (address.low16Bits() < 0x8000) return
        selectedPrgBankBase = (((value shr 4) and 0x03) % prgBankCount) * PRG_BANK_SIZE
        selectedChrBankBase = ((value and 0x03) % chrBankCount) * CHR_BANK_SIZE
    }

    override fun ppuRead(address: Int): Int {
        return chrRom[selectedChrBankBase + (address and 0x1FFF)].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) = Unit

    override fun reset() {
        selectedPrgBankBase = 0
        selectedChrBankBase = 0
    }

    override fun captureState(): MapperState = state.copy()

    override fun restoreState(state: MapperState) {
        this.state = state as Mapper66State
    }

    private companion object {
        const val PRG_BANK_SIZE = 32 * 1024
        const val CHR_BANK_SIZE = 8 * 1024
    }
}
