package nes.cartridge

import nes.util.low16Bits
import nes.util.low8Bits
import nes.util.toUnsignedInt

class Mapper4(
    private val prgRom: ByteArray,
    private val chr: ByteArray,
    private val isChrRam: Boolean,
    prgRamSize: Int,
) : Mapper {
    private val prgBankCount = prgRom.size / PRG_BANK_SIZE
    private val chrBankCount = chr.size / CHR_BANK_SIZE
    private val prgRam = ByteArray(prgRamSize)
    private val registers = IntArray(8)
    private val prgBankOffsets = IntArray(4)
    private val chrBankOffsets = IntArray(8)

    private var selectedRegister = 0
    private var prgMode = false
    private var chrMode = false
    private var irqLatch = 0
    private var irqCounter = 0
    private var irqReload = false
    private var irqEnabled = false
    private var irqRequested = false
    private var mirroring: Mirroring? = null
    private var prgRamEnabled = prgRam.isNotEmpty()
    private var prgRamWriteProtected = false
    private var a12LowCycle = NO_A12_LOW_CYCLE

    init {
        rebuildBankOffsets()
    }

    override fun cpuRead(address: Int): Int {
        return cpuRead(address, 0)
    }

    override fun cpuRead(address: Int, openBus: Int): Int {
        val a = address.low16Bits()
        if (a in 0x6000..0x7FFF) {
            if (prgRam.isEmpty() || !prgRamEnabled) return openBus.low8Bits()
            return prgRam[(a - 0x6000) % prgRam.size].toUnsignedInt()
        }
        if (a < 0x8000) return 0
        val page = (a - 0x8000) shr 13
        val index = prgBankOffsets[page] + (a and 0x1FFF)
        return prgRom[index].toUnsignedInt()
    }

    override fun cpuWrite(address: Int, value: Int) {
        val a = address.low16Bits()
        val v = value.low8Bits()
        when (a) {
            in 0x6000..0x7FFF -> if (prgRam.isNotEmpty() && prgRamEnabled && !prgRamWriteProtected) {
                prgRam[(a - 0x6000) % prgRam.size] = v.toByte()
            }
            in 0x8000..0x9FFF -> if ((a and 1) == 0) {
                selectedRegister = v and 7
                prgMode = (v and 0x40) != 0
                chrMode = (v and 0x80) != 0
                rebuildBankOffsets()
            } else {
                registers[selectedRegister] = v
                rebuildBankOffsets()
            }
            in 0xA000..0xBFFF -> if ((a and 1) == 0) {
                mirroring = if ((v and 1) == 0) Mirroring.VERTICAL else Mirroring.HORIZONTAL
            } else {
                prgRamEnabled = (v and 0x80) != 0
                prgRamWriteProtected = (v and 0x40) != 0
            }
            in 0xC000..0xDFFF -> if ((a and 1) == 0) {
                irqLatch = v
            } else {
                irqReload = true
            }
            in 0xE000..0xFFFF -> if ((a and 1) == 0) {
                irqEnabled = false
                irqRequested = false
            } else {
                irqEnabled = true
            }
        }
    }

    override fun ppuRead(address: Int): Int {
        return chr[mapChrAddress(address)].toUnsignedInt()
    }

    override fun ppuWrite(address: Int, value: Int) {
        if (isChrRam) {
            chr[mapChrAddress(address)] = value.toByte()
        }
    }

    override fun ppuAddressChanged(address: Int, cpuCycle: Long) {
        if ((address and 0x1000) == 0) {
            if (a12LowCycle == NO_A12_LOW_CYCLE) a12LowCycle = cpuCycle
        } else {
            if (a12LowCycle != NO_A12_LOW_CYCLE && cpuCycle - a12LowCycle >= A12_LOW_FILTER_CYCLES) {
                clockIrqCounter()
            }
            a12LowCycle = NO_A12_LOW_CYCLE
        }
    }

    override fun reset() {
        registers.fill(0)
        selectedRegister = 0
        prgMode = false
        chrMode = false
        irqLatch = 0
        irqCounter = 0
        irqReload = false
        irqEnabled = false
        irqRequested = false
        mirroring = null
        prgRamEnabled = prgRam.isNotEmpty()
        prgRamWriteProtected = false
        a12LowCycle = NO_A12_LOW_CYCLE
        rebuildBankOffsets()
    }

    override fun clockScanline() {
        clockIrqCounter()
    }

    private fun clockIrqCounter() {
        if (irqCounter == 0 || irqReload) {
            irqCounter = irqLatch
            irqReload = false
        } else {
            irqCounter--
        }
        if (irqCounter == 0 && irqEnabled) {
            irqRequested = true
        }
    }

    override fun irqPending(): Boolean {
        return irqRequested
    }

    override fun mirroring(): Mirroring? {
        return mirroring
    }

    override fun captureState(): MapperState = Mapper4State(
        chr = if (isChrRam) chr.copyOf() else ByteArray(0),
        prgRam = prgRam.copyOf(),
        registers = registers.copyOf(),
        selectedRegister = selectedRegister,
        prgMode = prgMode,
        chrMode = chrMode,
        irqLatch = irqLatch,
        irqCounter = irqCounter,
        irqReload = irqReload,
        irqEnabled = irqEnabled,
        irqRequested = irqRequested,
        mirroring = mirroring,
        prgRamEnabled = prgRamEnabled,
        prgRamWriteProtected = prgRamWriteProtected,
        a12LowCycle = a12LowCycle,
    )

    override fun restoreState(state: MapperState) {
        state as Mapper4State
        if (isChrRam) state.chr.copyInto(chr)
        state.prgRam.copyInto(prgRam)
        state.registers.copyInto(registers)
        selectedRegister = state.selectedRegister
        prgMode = state.prgMode
        chrMode = state.chrMode
        irqLatch = state.irqLatch
        irqCounter = state.irqCounter
        irqReload = state.irqReload
        irqEnabled = state.irqEnabled
        irqRequested = state.irqRequested
        mirroring = state.mirroring
        prgRamEnabled = state.prgRamEnabled
        prgRamWriteProtected = state.prgRamWriteProtected
        a12LowCycle = state.a12LowCycle
        rebuildBankOffsets()
    }

    private fun mapChrAddress(address: Int): Int {
        val a = address and 0x1FFF
        return chrBankOffsets[a shr 10] + (a and 0x03FF)
    }

    private fun rebuildBankOffsets() {
        val secondLastPrg = prgBankCount - 2
        if (prgMode) {
            setPrgBank(0, secondLastPrg)
            setPrgBank(2, registers[6])
        } else {
            setPrgBank(0, registers[6])
            setPrgBank(2, secondLastPrg)
        }
        setPrgBank(1, registers[7])
        setPrgBank(3, prgBankCount - 1)

        val firstPair = registers[0] and 0xFE
        val secondPair = registers[1] and 0xFE
        if (chrMode) {
            setChrBank(0, registers[2])
            setChrBank(1, registers[3])
            setChrBank(2, registers[4])
            setChrBank(3, registers[5])
            setChrBank(4, firstPair)
            setChrBank(5, firstPair + 1)
            setChrBank(6, secondPair)
            setChrBank(7, secondPair + 1)
        } else {
            setChrBank(0, firstPair)
            setChrBank(1, firstPair + 1)
            setChrBank(2, secondPair)
            setChrBank(3, secondPair + 1)
            setChrBank(4, registers[2])
            setChrBank(5, registers[3])
            setChrBank(6, registers[4])
            setChrBank(7, registers[5])
        }
    }

    private fun setPrgBank(page: Int, bank: Int) {
        prgBankOffsets[page] = (bank % prgBankCount) * PRG_BANK_SIZE
    }

    private fun setChrBank(page: Int, bank: Int) {
        chrBankOffsets[page] = (bank % chrBankCount) * CHR_BANK_SIZE
    }

    companion object {
        private const val PRG_BANK_SIZE = 8 * 1024
        private const val CHR_BANK_SIZE = 1024
        private const val NO_A12_LOW_CYCLE = -1L
        private const val A12_LOW_FILTER_CYCLES = 3
    }
}
