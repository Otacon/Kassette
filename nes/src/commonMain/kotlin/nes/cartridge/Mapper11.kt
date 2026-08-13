package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper11(
    private val prgRom: ByteArray,
    private val chrRom: ByteArray,
) : Mapper {
    private var state = Mapper11State()
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
        val a = address.low16Bits()
        if (a < 0x8000) return
        val v = (value and cpuRead(a)).low8Bits()
        selectedPrgBankBase = ((v and 0x0F) % prgBankCount) * PRG_BANK_SIZE
        selectedChrBankBase = (((v shr 4) and 0x0F) % chrBankCount) * CHR_BANK_SIZE
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
        this.state = state as Mapper11State
    }

    private companion object {
        const val PRG_BANK_SIZE = 32 * 1024
        const val CHR_BANK_SIZE = 8 * 1024
    }
}
