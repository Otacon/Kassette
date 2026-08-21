package nes2.apu

import nes.ConsoleRegion

interface Apu {
    val samples: ShortArray
    val sampleCount: Int
    val irqPending: Boolean

    fun reset()
    fun beginFrame()
    fun tick(cpuCycles: Int)
    fun read(address: Int): Int
    fun write(address: Int, value: Int)
    fun configureTiming(region: ConsoleRegion)
}

class ApuNes(
    private val state: ApuState = ApuState(),
) : Apu {

    override val samples: ShortArray = ShortArray(MAX_FRAME_SAMPLES)

    override var sampleCount: Int = 0
        private set

    override val irqPending: Boolean
        get() = state.frameIrqPending || state.dmc.irqPending

    override fun reset() {
        state.reset()
    }

    override fun beginFrame() {

    }

    override fun tick(cpuCycles: Int) {

    }

    override fun read(address: Int): Int = 0

    override fun write(address: Int, value: Int) = Unit
    override fun configureTiming(region: ConsoleRegion) {

    }

    private companion object {
        const val MAX_FRAME_SAMPLES = 2048
    }
}
