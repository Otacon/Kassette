package nes.cartridge

import nes.util.toUnsignedInt

class Mapper0(
    private val prgRom: ByteArray,
    private val chr: ByteArray,
    private val isChrRam: Boolean,
) : Mapper {
    private var state = Mapper0State(if (isChrRam) chr else ByteArray(0))
    private val prgMask = if (prgRom.size == 0x4000) 0x3FFF else 0x7FFF
    private val activeChr: ByteArray get() = if (isChrRam) state.chr else chr

    override fun cpuRead(address: Int): Int {
        if (address < 0x8000) return 0
        return prgRom[address and prgMask].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) = Unit

    override fun ppuRead(address: Int): Int = activeChr[address and 0x1FFF].toUnsignedInt()

    override fun ppuWrite(address: Int, value: Int) {
        if (isChrRam) state.chr[address and 0x1FFF] = value.toByte()
    }

    override fun captureState(): MapperState = state.copy(chr = state.chr.copyOf())

    override fun restoreState(state: MapperState) {
        this.state = state as Mapper0State
    }

}
