package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper1(
    private val prgRom: ByteArray,
    private val chr: ByteArray,
    private val isChrRam: Boolean,
) : Mapper {
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private val chrBankCount = chr.size / CHR_BANK_SIZE
    private val prgRam = ByteArray(PRG_RAM_SIZE)

    private var shiftRegister = 0x10
    private var control = 0x0C
    private var chrBank0 = 0
    private var chrBank1 = 0
    private var prgBank = 0

    override fun cpuRead(address: Int): Int {
        val a = address.low16Bits()
        if (a in 0x6000..0x7FFF) {
            return if (prgRamEnabled()) prgRam[a and 0x1FFF].toUnsignedInt() else 0
        }
        if (a < 0x8000) return 0
        return prgRom[mapPrgAddress(a)].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        val v = value.low8Bits()
        if (a in 0x6000..0x7FFF) {
            if (prgRamEnabled()) prgRam[a and 0x1FFF] = v.toByte()
            return
        }
        if (a < 0x8000) return

        if ((v and 0x80) != 0) {
            shiftRegister = 0x10
            control = control or 0x0C
            return
        }

        val complete = (shiftRegister and 1) != 0
        shiftRegister = (shiftRegister shr 1) or ((v and 1) shl 4)
        if (complete) {
            when (a) {
                in 0x8000..0x9FFF -> control = shiftRegister
                in 0xA000..0xBFFF -> chrBank0 = shiftRegister
                in 0xC000..0xDFFF -> chrBank1 = shiftRegister
                else -> prgBank = shiftRegister
            }
            shiftRegister = 0x10
        }
    }

    override fun ppuRead(address: Int): Int {
        return chr[mapChrAddress(address)].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) {
        if (isChrRam) chr[mapChrAddress(address)] = value.toByte()
    }

    override fun reset() {
        shiftRegister = 0x10
        control = 0x0C
        chrBank0 = 0
        chrBank1 = 0
        prgBank = 0
    }

    override fun mirroring(): Mirroring = when (control and 0x03) {
        0 -> Mirroring.SINGLE_SCREEN_LOWER
        1 -> Mirroring.SINGLE_SCREEN_UPPER
        2 -> Mirroring.VERTICAL
        else -> Mirroring.HORIZONTAL
    }

    override fun captureState(): MapperState = Mapper1State(
        chr = if (isChrRam) chr.copyOf() else ByteArray(0),
        prgRam = prgRam.copyOf(),
        registers = intArrayOf(shiftRegister, control, chrBank0, chrBank1, prgBank),
    )

    override fun restoreState(state: MapperState) {
        state as Mapper1State
        if (isChrRam) state.chr.copyInto(chr)
        state.prgRam.copyInto(prgRam)
        shiftRegister = state.registers[0]
        control = state.registers[1]
        chrBank0 = state.registers[2]
        chrBank1 = state.registers[3]
        prgBank = state.registers[4]
    }

    private fun mapPrgAddress(address: Int): Int {
        val offset = address and 0x3FFF
        val bank = when ((control shr 2) and 0x03) {
            0, 1 -> ((prgBank and 0x0E) % prgBankCount) + if (address >= 0xC000) 1 else 0
            2 -> if (address < 0xC000) 0 else (prgBank and 0x0F) % prgBankCount
            else -> if (address < 0xC000) (prgBank and 0x0F) % prgBankCount else prgBankCount - 1
        }
        return bank * PRG_BANK_SIZE + offset
    }

    private fun mapChrAddress(address: Int): Int {
        val a = address and 0x1FFF
        val bank = if ((control and 0x10) == 0) {
            ((chrBank0 and 0x1E) % chrBankCount) + (a shr 12)
        } else if (a < 0x1000) {
            chrBank0 % chrBankCount
        } else {
            chrBank1 % chrBankCount
        }
        return bank * CHR_BANK_SIZE + (a and 0x0FFF)
    }

    private fun prgRamEnabled(): Boolean = (prgBank and 0x10) == 0

    private companion object {
        const val PRG_BANK_SIZE = 16 * 1024
        const val CHR_BANK_SIZE = 4 * 1024
        const val PRG_RAM_SIZE = 8 * 1024
    }
}
