package nes2

import nes2.cpu.Cpu
import nes2.ppu.Ppu

interface NesMachine {
    fun runUntilFrame()
}

class NesMachineImpl(
    private val cpu: Cpu,
    private val ppu: Ppu,
) : NesMachine {

    override fun runUntilFrame() {
        val currentFrame = ppu.frame

        while (ppu.frame == currentFrame) {
            step()
        }
    }

    private fun step() {
        val cpuCycles = cpu.step()
        var currentCycle = 0
        while (currentCycle < cpuCycles * PPU_TICKS_PER_CPU_CYCLE) {
            ppu.tick()
            currentCycle++
        }
    }

    private companion object {
        const val PPU_TICKS_PER_CPU_CYCLE = 3
    }
}