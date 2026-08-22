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
    fun configureTiming(apuFourStepEvents: IntArray, apuFiveStepEvents: IntArray)
}

class ApuNes(
    private val state: ApuState = ApuState(),
) : Apu {

    private var fourStepEvents = ConsoleRegion.NTSC.timing.apuFourStepEvents
    private var fiveStepEvents = ConsoleRegion.NTSC.timing.apuFiveStepEvents

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
        var cycle = 0
        while (cycle < cpuCycles) {
            tickFrameCounter()
            cycle++
        }
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
            APU_FRAME_COUNTER -> writeFrameCounter(value and 0xFF)
        }
    }

    override fun configureTiming(apuFourStepEvents: IntArray, apuFiveStepEvents: IntArray) {
        fourStepEvents = apuFourStepEvents
        fiveStepEvents = apuFiveStepEvents
    }

    private fun tickFrameCounter() {
        state.frameCycle++

        val events = if (state.frameMode == FOUR_STEP_MODE) fourStepEvents else fiveStepEvents
        if (state.frameCycle != events[state.frameStep]) return

        clockFrameStep()

        if (state.frameMode == FOUR_STEP_MODE && state.frameStep == events.lastIndex && !state.frameIrqInhibit) {
            state.frameIrqPending = true
        }

        state.frameStep++
        if (state.frameStep == events.size) {
            state.frameCycle = 0
            state.frameStep = 0
        }
    }

    private fun clockFrameStep() {
        when (state.frameStep) {
            0, 2 -> clockQuarterFrame()
            1, 3 -> {
                clockQuarterFrame()
                clockHalfFrame()
            }
        }
    }

    private fun clockQuarterFrame() = Unit

    private fun clockHalfFrame() = Unit

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

    private fun writeFrameCounter(value: Int) {
        state.frameMode = if ((value and FRAME_COUNTER_MODE) != 0) FIVE_STEP_MODE else FOUR_STEP_MODE
        state.frameIrqInhibit = (value and FRAME_COUNTER_IRQ_INHIBIT) != 0
        if (state.frameIrqInhibit) state.frameIrqPending = false
        state.frameCycle = 0
        state.frameStep = 0

        if (state.frameMode == FIVE_STEP_MODE) {
            clockQuarterFrame()
            clockHalfFrame()
        }
    }

    private fun Int.statusBit(enabled: Boolean): Int = if (enabled) this else 0

    private fun Int.isEnabled(value: Int): Boolean = (value and this) != 0

    private companion object {
        const val APU_STATUS = 0x4015
        const val APU_FRAME_COUNTER = 0x4017
        const val PULSE_1_ENABLED = 0x01
        const val PULSE_2_ENABLED = 0x02
        const val TRIANGLE_ENABLED = 0x04
        const val NOISE_ENABLED = 0x08
        const val DMC_ENABLED = 0x10
        const val FRAME_IRQ_PENDING = 0x40
        const val DMC_IRQ_PENDING = 0x80
        const val FRAME_COUNTER_IRQ_INHIBIT = 0x40
        const val FRAME_COUNTER_MODE = 0x80
        const val FOUR_STEP_MODE = 0
        const val FIVE_STEP_MODE = 1
        const val MAX_FRAME_SAMPLES = 2048
    }
}
