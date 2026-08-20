package nes2.fakes

import nes2.cpu.Cpu

class FakeCpu : Cpu {

    var cycles = 0

    var steps = 0
        private set

    var resets = 0
        private set

    var irqLine = false
        private set

    var nmiRequests = 0
        private set

    var resetCycles = 0

    override fun reset(): Int {
        resets++
        return resetCycles
    }

    override fun setIrqLine(active: Boolean) {
        irqLine = active
    }

    override fun requestNmi() {
        nmiRequests++
    }

    override fun step(): Int {
        steps++
        return cycles
    }
}
