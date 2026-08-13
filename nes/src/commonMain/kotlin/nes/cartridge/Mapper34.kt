package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper34(
    private val prgRom: ByteArray,
    private val chr: ByteArray,
    private val isChrRam: Boolean,
    prgRamSize: Int,
    forceNina: Boolean = false,
) : Mapper {
    private val nina = forceNina || !isChrRam
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private val chrBankCount = chr.size / if (nina) NINA_CHR_BANK_SIZE else BNROM_CHR_BANK_SIZE
    private val prgRam = ByteArray(prgRamSize)
    private var selectedPrgBankBase = 0
    private var chrBank0Base = 0
    private var chrBank1Base = NINA_CHR_BANK_SIZE

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a in 0x6000..0x7FFF) return if (prgRam.isEmpty()) 0 else prgRam[(a - 0x6000) % prgRam.size].toUnsignedInt()
        if (a < 0x8000) return 0
        return prgRom[selectedPrgBankBase + (a and 0x7FFF)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        val v = if (a >= 0x8000) (value and cpuRead(a)).low8Bits() else value.low8Bits()
        if (a in 0x6000..0x7FFF && prgRam.isNotEmpty()) {
            prgRam[(a - 0x6000) % prgRam.size] = v.toByte()
        }
        if (nina) {
            when (a) {
                0x7FFD -> selectedPrgBankBase = (v % prgBankCount) * PRG_BANK_SIZE
                0x7FFE -> chrBank0Base = (v % chrBankCount) * NINA_CHR_BANK_SIZE
                0x7FFF -> chrBank1Base = (v % chrBankCount) * NINA_CHR_BANK_SIZE
            }
        } else if (a >= 0x8000) {
            selectedPrgBankBase = (v % prgBankCount) * PRG_BANK_SIZE
        }
    }

    override fun ppuRead(address: Int): Int {
        val a = address and 0x1FFF
        if (!nina) return chr[a].toUnsignedInt()
        val base = if (a < 0x1000) chrBank0Base else chrBank1Base
        return chr[base + (a and 0x0FFF)].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) {
        if (isChrRam) chr[address and 0x1FFF] = value.toByte()
    }

    override fun reset() {
        selectedPrgBankBase = 0
        chrBank0Base = 0
        chrBank1Base = if (chr.size > NINA_CHR_BANK_SIZE) NINA_CHR_BANK_SIZE else 0
    }

    override fun captureState(): MapperState = Mapper34State(
        chr = if (isChrRam) chr.copyOf() else ByteArray(0),
        prgRam = prgRam.copyOf(),
        selectedPrgBankBase = selectedPrgBankBase,
        chrBank0Base = chrBank0Base,
        chrBank1Base = chrBank1Base,
    )

    override fun restoreState(state: MapperState) {
        state as Mapper34State
        if (isChrRam) state.chr.copyInto(chr)
        state.prgRam.copyInto(prgRam)
        selectedPrgBankBase = state.selectedPrgBankBase
        chrBank0Base = state.chrBank0Base
        chrBank1Base = state.chrBank1Base
    }

    private companion object {
        const val PRG_BANK_SIZE = 32 * 1024
        const val BNROM_CHR_BANK_SIZE = 8 * 1024
        const val NINA_CHR_BANK_SIZE = 4 * 1024
    }
}
