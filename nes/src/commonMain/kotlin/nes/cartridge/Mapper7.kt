package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper7(
    private val prgRom: ByteArray,
    private val chrRam: ByteArray,
    private val hasBusConflicts: Boolean = false,
) : Mapper {
    private var state = Mapper7State(chrRam)
    private val bankCount = prgRom.size / PRG_BANK_SIZE
    private var selectedBankBase: Int get() = state.selectedBankBase; set(value) { state.selectedBankBase = value }
    private var mirroring: Mirroring get() = state.mirroring; set(value) { state.mirroring = value }

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a < 0x8000) return 0
        return prgRom[selectedBankBase + (a and 0x7FFF)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        if (a < 0x8000) return
        val v = (if (hasBusConflicts) value and cpuRead(a) else value).low8Bits()
        selectedBankBase = ((v and 0x0F) % bankCount) * PRG_BANK_SIZE
        mirroring = if ((v and 0x10) == 0) Mirroring.SINGLE_SCREEN_LOWER else Mirroring.SINGLE_SCREEN_UPPER
    }

    override fun ppuRead(address: Int): Int {
        return state.chrRam[address and 0x1FFF].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) {
        state.chrRam[address and 0x1FFF] = value.toByte()
    }

    override fun reset() {
        selectedBankBase = 0
        mirroring = Mirroring.SINGLE_SCREEN_LOWER
    }

    override fun mirroring(): Mirroring = mirroring

    override fun captureState(): MapperState = state.copy(chrRam = state.chrRam.copyOf())

    override fun restoreState(state: MapperState) {
        this.state = state as Mapper7State
    }

    private companion object {
        const val PRG_BANK_SIZE = 32 * 1024
    }
}
