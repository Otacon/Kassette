package nes2

import nes2.cpu.Cpu
import nes2.ppu.Ppu

interface NesMachine {
    fun reset()
    fun runUntilFrame()
}

class NesMachineImpl(
    private val cpu: Cpu,
    private val ppu: Ppu,
    private val oamDma: OamDma,
) : NesMachine {

    private var cpuCycles: Long = 0

    override fun reset() {
        cpuCycles = 0
        cpu.reset()
        ppu.reset()
        oamDma.reset()
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

        val cycles = cpu.step()
        cpuCycles += cycles
        tickPpu(cycles)
    }

    private fun tickPpu(cpuCycles: Int) {
        var tick = 0
        while (tick < cpuCycles * PPU_TICKS_PER_CPU_CYCLE) {
            ppu.tick()
            tick++
        }
    }

    private companion object {
        const val PPU_TICKS_PER_CPU_CYCLE = 3
    }
}