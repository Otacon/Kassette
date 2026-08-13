package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper79(
    private val prgRom: ByteArray,
    private val chrRom: ByteArray,
    private val multicartMode: Boolean = false,
) : Mapper {
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private val chrBankCount = chrRom.size / CHR_BANK_SIZE
    private var selectedPrgBankBase = 0
    private var selectedChrBankBase = 0
    private var mirroring: Mirroring? = null

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        return prgRom[selectedPrgBankBase + (a and 0x7FFF)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if ((a and 0xE100) != 0x4100) return
        val v = value.low8Bits()
        if (multicartMode) {
            selectedPrgBankBase = (((v shr 3) and 0x07) % prgBankCount) * PRG_BANK_SIZE
            selectedChrBankBase = (((v and 0x07) or ((v shr 3) and 0x08)) % chrBankCount) * CHR_BANK_SIZE
            mirroring = if ((v and 0x80) != 0) Mirroring.VERTICAL else Mirroring.HORIZONTAL
        } else {
            selectedPrgBankBase = (((v shr 3) and 0x01) % prgBankCount) * PRG_BANK_SIZE
            selectedChrBankBase = ((v and 0x07) % chrBankCount) * CHR_BANK_SIZE
        }
    }

    override fun ppuRead(address: Int): Int = chrRom[selectedChrBankBase + (address and 0x1FFF)].toUnsignedInt()

    override fun ppuWrite(address: Int, value: Int) = Unit

    override fun reset() {
        selectedPrgBankBase = 0
        selectedChrBankBase = 0
        mirroring = null
    }

    override fun mirroring(): Mirroring? = mirroring

    override fun captureState(): MapperState = Mapper79State(selectedPrgBankBase, selectedChrBankBase, mirroring)

    override fun restoreState(state: MapperState) {
        state as Mapper79State
        selectedPrgBankBase = state.selectedPrgBankBase
        selectedChrBankBase = state.selectedChrBankBase
        mirroring = state.mirroring
    }

    private companion object {
        const val PRG_BANK_SIZE = 32 * 1024
        const val CHR_BANK_SIZE = 8 * 1024
    }
}
