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
        sampleCount = 0
    }

    override fun beginFrame() {
        sampleCount = 0
    }

    override fun tick(cpuCycles: Int) {

    }

    override fun read(address: Int): Int {
        return when (address and 0xFFFF) {
            APU_STATUS -> readStatus()
            else -> 0
        }
    }

    override fun write(address: Int, value: Int) {
        when (address and 0xFFFF) {
            APU_STATUS -> writeStatus(value and 0xFF)
        }
    }

    override fun configureTiming(region: ConsoleRegion) {

    }

    private fun readStatus(): Int {
        val status = PULSE_1_ENABLED.statusBit(state.pulse1.lengthCounter > 0) or
                PULSE_2_ENABLED.statusBit(state.pulse2.lengthCounter > 0) or
                TRIANGLE_ENABLED.statusBit(state.triangle.lengthCounter > 0) or
                NOISE_ENABLED.statusBit(state.noise.lengthCounter > 0) or
                DMC_ENABLED.statusBit(state.dmc.bytesRemaining > 0) or
                FRAME_IRQ_PENDING.statusBit(state.frameIrqPending) or
                DMC_IRQ_PENDING.statusBit(state.dmc.irqPending)

        state.frameIrqPending = false
        return status
    }

    private fun writeStatus(value: Int) {
        state.pulse1.enabled = PULSE_1_ENABLED.isEnabled(value)
        state.pulse2.enabled = PULSE_2_ENABLED.isEnabled(value)
        state.triangle.enabled = TRIANGLE_ENABLED.isEnabled(value)
        state.noise.enabled = NOISE_ENABLED.isEnabled(value)
        state.dmc.enabled = DMC_ENABLED.isEnabled(value)

        if (!state.pulse1.enabled) state.pulse1.lengthCounter = 0
        if (!state.pulse2.enabled) state.pulse2.lengthCounter = 0
        if (!state.triangle.enabled) state.triangle.lengthCounter = 0
        if (!state.noise.enabled) state.noise.lengthCounter = 0
        if (!state.dmc.enabled) state.dmc.bytesRemaining = 0

        state.dmc.irqPending = false
    }

    private fun Int.statusBit(enabled: Boolean): Int = if (enabled) this else 0

    private fun Int.isEnabled(value: Int): Boolean = (value and this) != 0

    private companion object {
        const val APU_STATUS = 0x4015
        const val PULSE_1_ENABLED = 0x01
        const val PULSE_2_ENABLED = 0x02
        const val TRIANGLE_ENABLED = 0x04
        const val NOISE_ENABLED = 0x08
        const val DMC_ENABLED = 0x10
        const val FRAME_IRQ_PENDING = 0x40
        const val DMC_IRQ_PENDING = 0x80
        const val MAX_FRAME_SAMPLES = 2048
    }
}
