package nes2

import nes.ConsoleRegion
import nes.cartridge.Cartridge
import nes2.cartridge.CartridgePort
import nes2.cpu.Cpu
import nes2.ppu.Ppu

interface NesMachine {
    fun insertCartridge(cartridge: Cartridge)
    fun reset()
    fun runUntilFrame()
}

class NesMachineImpl(
    private val cpu: Cpu,
    private val ppu: Ppu,
    private val oamDma: OamDma,
    private val cartridge: CartridgePort,
) : NesMachine {

    private var cpuCycles: Long = 0
    private var ppuCycleRemainder = 0
    private var ppuCyclesPerCpuNumerator = 3
    private var ppuCyclesPerCpuDenominator = 1

    override fun insertCartridge(cartridge: Cartridge) {
        this.cartridge.insert(cartridge)
        applyRegion(cartridge.region)
    }

    override fun reset() {
        ppu.reset()
        oamDma.reset()
        val resetCycles = cpu.reset()
        cpuCycles = resetCycles.toLong()
        ppuCycleRemainder = 0
        tickPpu(resetCycles)
    }

    override fun runUntilFrame() {
        val currentFrame = ppu.frame

        while (ppu.frame == currentFrame) {
            step()
        }
    }

    private fun step() {
        if (oamDma.isActive) {
            oamDma.transfer()

            val dmaCycles = if (cpuCycles and 1L == 0L) {
                513
            } else {
                514
            }

            cpuCycles += dmaCycles
            tickPpu(dmaCycles)
            return
        }

        cpu.setIrqLine(cartridge.irqPending())

        val cycles = cpu.step()
        cpuCycles += cycles
        tickPpu(cycles)
    }

    private fun tickPpu(cpuCycles: Int) {
        val totalPpuCycles = ppuCycleRemainder + cpuCycles * ppuCyclesPerCpuNumerator
        val ppuTicks = totalPpuCycles / ppuCyclesPerCpuDenominator
        ppuCycleRemainder = totalPpuCycles % ppuCyclesPerCpuDenominator

        var tick = 0
        while (tick < ppuTicks) {
            ppu.tick()
            tick++
        }
    }

    private fun applyRegion(region: ConsoleRegion) {
        when (region) {
            ConsoleRegion.PAL -> {
                ppuCyclesPerCpuNumerator = 16
                ppuCyclesPerCpuDenominator = 5
                ppu.configureTiming(
                    scanlinesPerFrame = 312,
                    nmiScanline = 241,
                    skipsOddFrameDot = false,
                )
            }

            ConsoleRegion.DENDY -> {
                ppuCyclesPerCpuNumerator = 3
                ppuCyclesPerCpuDenominator = 1
                ppu.configureTiming(
                    scanlinesPerFrame = 312,
                    nmiScanline = 291,
                    skipsOddFrameDot = false,
                )
            }

            ConsoleRegion.NTSC,
            ConsoleRegion.MULTI_REGION -> {
                ppuCyclesPerCpuNumerator = 3
                ppuCyclesPerCpuDenominator = 1
                ppu.configureTiming(
                    scanlinesPerFrame = 262,
                    nmiScanline = 241,
                    skipsOddFrameDot = true,
                )
            }
        }
    }
}
