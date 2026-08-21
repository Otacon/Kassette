package nes2.fakes

import nes.ConsoleRegion
import nes2.apu.Apu

class FakeApu : Apu {
    override val samples: ShortArray = ShortArray(0)
    override val sampleCount: Int = 0
    override val irqPending: Boolean = false

    var readValue = 0
    var lastReadAddress: Int? = null
    var lastWriteAddress: Int? = null
    var lastWriteValue: Int? = null

    override fun reset() = Unit

    override fun beginFrame() = Unit

    override fun tick(cpuCycles: Int) = Unit

    override fun read(address: Int): Int {
        lastReadAddress = address
        return readValue
    }

    override fun write(address: Int, value: Int) {
        lastWriteAddress = address
        lastWriteValue = value and 0xFF
    }

    override fun configureTiming(region: ConsoleRegion) = Unit
}
