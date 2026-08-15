package nes.apu

import kotlin.math.PI
import kotlin.math.exp
import nes.util.low16Bits
import nes.util.low8Bits
import nes.Timing

class NesApu(
    private val dmcDma: DmcDma,
) {
    companion object {
        private const val SAMPLE_RATE = 44_100
        private val LENGTH_TABLE = intArrayOf(
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30,
        )
        private val DUTY_TABLE = arrayOf(
            intArrayOf(0, 1, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0, 0, 0, 0, 0),
            intArrayOf(0, 1, 1, 1, 1, 0, 0, 0),
            intArrayOf(1, 0, 0, 1, 1, 1, 1, 1),
        )
        private val TRIANGLE_TABLE = intArrayOf(
            15, 14, 13, 12, 11, 10, 9, 8,
            7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15
        )
        private val FOUR_STEP_ACTIONS = intArrayOf(
            QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME, QUARTER_FRAME, 0,
            QUARTER_FRAME or HALF_FRAME, 0,
        )
        private val FIVE_STEP_ACTIONS = intArrayOf(
            QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME, QUARTER_FRAME, 0,
            QUARTER_FRAME or HALF_FRAME, 0,
        )
        private val PULSE_MIX = DoubleArray(31) { sum ->
            if (sum == 0) 0.0 else 95.88 / ((8128.0 / sum) + 100.0)
        }
        private val TND_MIX = DoubleArray(16 * 16 * 128).also { table ->
            var triangle = 0
            while (triangle < 16) {
                var noise = 0
                while (noise < 16) {
                    var dmc = 0
                    while (dmc < 128) {
                        val input = triangle / 8227.0 + noise / 12241.0 + dmc / 22638.0
                        table[tndIndex(triangle, noise, dmc)] = if (input == 0.0) 0.0 else 159.79 / (1.0 / input + 100.0)
                        dmc++
                    }
                    noise++
                }
                triangle++
            }
        }
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MAX_FRAME_SAMPLES = 2048
        private const val QUARTER_FRAME = 1
        private const val HALF_FRAME = 2
        private val HIGH_PASS_90_HZ = exp(-2.0 * PI * 90.0 / SAMPLE_RATE)
        private val HIGH_PASS_440_HZ = exp(-2.0 * PI * 440.0 / SAMPLE_RATE)
        private val LOW_PASS_14_KHZ = 1.0 - exp(-2.0 * PI * 14_000.0 / SAMPLE_RATE)

        private fun tndIndex(triangle: Int, noise: Int, dmc: Int): Int {
            return ((triangle shl 4) or noise) * 128 + dmc
        }
    }

    val samples = ShortArray(MAX_FRAME_SAMPLES)
    var sampleCount = 0
        private set

    private var state = ApuState()
    private val pulse1 = PulseChannel(channelOne = true) { state.pulse1 }
    private val pulse2 = PulseChannel(channelOne = false) { state.pulse2 }
    private val triangle = TriangleChannel { state.triangle }
    private val noise = NoiseChannel { state.noise }
    private val dmc = DmcChannel(dmcDma) { state.dmc }
    private var frameCycle: Int get() = state.frameCycle; set(value) { state.frameCycle = value }
    private var frameEventIndex: Int get() = state.frameEventIndex; set(value) { state.frameEventIndex = value }
    private var frameMode: Int get() = state.frameMode; set(value) { state.frameMode = value }
    private var frameIrqInhibit: Boolean get() = state.frameIrqInhibit; set(value) { state.frameIrqInhibit = value }
    private var frameIrqPending: Boolean get() = state.frameIrqPending; set(value) { state.frameIrqPending = value }
    private var apuCycle: Boolean get() = state.apuCycle; set(value) { state.apuCycle = value }
    private var samplePhase: Int get() = state.samplePhase; set(value) { state.samplePhase = value }
    private var pendingFrameCounterValue: Int get() = state.pendingFrameCounterValue; set(value) { state.pendingFrameCounterValue = value }
    private var frameCounterWriteDelay: Int get() = state.frameCounterWriteDelay; set(value) { state.frameCounterWriteDelay = value }
    private var blockFrameCounterTicks: Int get() = state.blockFrameCounterTicks; set(value) { state.blockFrameCounterTicks = value }
    private var frameIrqFlag: Boolean get() = state.frameIrqFlag; set(value) { state.frameIrqFlag = value }
    private var frameIrqClearDelay: Int get() = state.frameIrqClearDelay; set(value) { state.frameIrqClearDelay = value }
    private var highPass90Input: Double get() = state.filters[0]; set(value) { state.filters[0] = value }
    private var highPass90Output: Double get() = state.filters[1]; set(value) { state.filters[1] = value }
    private var highPass440Input: Double get() = state.filters[2]; set(value) { state.filters[2] = value }
    private var highPass440Output: Double get() = state.filters[3]; set(value) { state.filters[3] = value }
    private var lowPass14kOutput: Double get() = state.filters[4]; set(value) { state.filters[4] = value }
    var timing: Timing = Timing.DEFAULT
        set(value) {
            field = value
            noise.periods = value.noisePeriods
            dmc.periods = value.dmcPeriods
        }

    fun reset(softReset: Boolean = false) {
        val retainedFrameMode = state.frameMode
        val retainedSampleAddress = state.dmc.values[3]
        val retainedSampleLength = state.dmc.values[4]
        val retainedTriangleLength = state.triangle.lengthCounter
        val retainedTriangleLengthHalt = state.triangle.flags[2]
        val retainedTrianglePendingLengthHalt = state.triangle.flags[3]
        val retainedTriangleLengthReload = state.triangle.values[6]
        val retainedTrianglePreviousLength = state.triangle.values[7]
        state = ApuState()
        if (softReset) {
            state.frameMode = retainedFrameMode
            state.dmc.values[3] = retainedSampleAddress
            state.dmc.values[4] = retainedSampleLength
            state.triangle.lengthCounter = retainedTriangleLength
            state.triangle.flags[2] = retainedTriangleLengthHalt
            state.triangle.flags[3] = retainedTrianglePendingLengthHalt
            state.triangle.values[6] = retainedTriangleLengthReload
            state.triangle.values[7] = retainedTrianglePreviousLength
        }
        state.noise.values[3] = timing.noisePeriods[0]
        state.noise.values[4] = timing.noisePeriods[0] - 1
        state.dmc.values[0] = timing.dmcPeriods[0]
        state.dmc.values[1] = timing.dmcPeriods[0] - 1
        state.pendingFrameCounterValue = if (state.frameMode == 1) 0x80 else 0
        state.frameCounterWriteDelay = 3
        sampleCount = 0
        dmcDma.reset()
    }

    fun beginFrame() {
        sampleCount = 0
    }

    fun captureState(): ApuState = state.copy(
        pulse1 = state.pulse1.copy(values = state.pulse1.values.copyOf(), flags = state.pulse1.flags.copyOf()),
        pulse2 = state.pulse2.copy(values = state.pulse2.values.copyOf(), flags = state.pulse2.flags.copyOf()),
        triangle = state.triangle.copy(values = state.triangle.values.copyOf(), flags = state.triangle.flags.copyOf()),
        noise = state.noise.copy(values = state.noise.values.copyOf(), flags = state.noise.flags.copyOf()),
        dmc = state.dmc.copy(values = state.dmc.values.copyOf(), flags = state.dmc.flags.copyOf()),
        filters = state.filters.copyOf(),
        dmcDma = dmcDma.captureState(),
    )

    fun restoreState(state: ApuState) {
        this.state = state.copy(
            pulse1 = state.pulse1.copy(values = state.pulse1.values.copyOf(), flags = state.pulse1.flags.copyOf()),
            pulse2 = state.pulse2.copy(values = state.pulse2.values.copyOf(), flags = state.pulse2.flags.copyOf()),
            triangle = state.triangle.copy(values = state.triangle.values.copyOf(), flags = state.triangle.flags.copyOf()),
            noise = state.noise.copy(values = state.noise.values.copyOf(), flags = state.noise.flags.copyOf()),
            dmc = state.dmc.copy(values = state.dmc.values.copyOf(), flags = state.dmc.flags.copyOf()),
            filters = state.filters.copyOf(),
            dmcDma = state.dmcDma.copy(),
        )
        if (this.state.noise.values[3] <= 0) {
            this.state.noise.values[3] = timing.noisePeriods[0]
            this.state.noise.values[4] = timing.noisePeriods[0] - 1
        }
        if (this.state.noise.values[5] == 0) this.state.noise.values[5] = 1
        if (this.state.dmc.values[0] <= 0) {
            this.state.dmc.values[0] = timing.dmcPeriods[0]
            this.state.dmc.values[1] = timing.dmcPeriods[0] - 1
        }
        dmcDma.restoreState(this.state.dmcDma)
        normalizeFrameEventIndex()
        sampleCount = 0
    }

    fun cpuRead(address: Int, openBus: Int = 0): Int {
        if (address.low16Bits() != 0x4015) return openBus.low8Bits()
        val status = (if (pulse1.lengthCounter > 0) 0x01 else 0) or
                (if (pulse2.lengthCounter > 0) 0x02 else 0) or
                (if (triangle.lengthCounter > 0) 0x04 else 0) or
                (if (noise.lengthCounter > 0) 0x08 else 0) or
                (if (dmc.active()) 0x10 else 0) or (openBus and 0x20) or
                (if (frameIrqFlag) 0x40 else 0) or
                (if (dmc.irqPending()) 0x80 else 0)
        frameIrqPending = false
        if (frameIrqFlag && frameIrqClearDelay == 0) frameIrqClearDelay = if (apuCycle) 1 else 2
        return status
    }

    fun cpuWrite(address: Int, value: Int) {
        val v = value.low8Bits()
        when (address.low16Bits()) {
            in 0x4000..0x4003 -> pulse1.write((address and 3), v)
            in 0x4004..0x4007 -> pulse2.write((address and 3), v)
            in 0x4008..0x400B -> triangle.write((address and 3), v)
            in 0x400C..0x400F -> noise.write((address and 3), v)
            0x4010, 0x4011, 0x4012, 0x4013 -> dmc.write(address and 3, v)
            0x4015 -> {
                pulse1.enabled = (v and 0x01) != 0
                pulse2.enabled = (v and 0x02) != 0
                triangle.enabled = (v and 0x04) != 0
                noise.enabled = (v and 0x08) != 0
                dmc.clearIrq()
                dmc.setEnabled((v and 0x10) != 0, apuCycle)
                if (!pulse1.enabled) pulse1.lengthCounter = 0
                if (!pulse2.enabled) pulse2.lengthCounter = 0
                if (!triangle.enabled) triangle.lengthCounter = 0
                if (!noise.enabled) noise.lengthCounter = 0
            }

            0x4017 -> {
                frameIrqInhibit = (v and 0x40) != 0
                if (frameIrqInhibit) {
                    frameIrqPending = false
                    frameIrqFlag = false
                    frameIrqClearDelay = 0
                }
                pendingFrameCounterValue = v
                frameCounterWriteDelay = if (apuCycle) 3 else 4
            }
        }
    }

    fun step(cpuCycles: Int) {
        var i = 0
        while (i < cpuCycles) {
            step()
            i++
        }
    }

    fun step() {
        stepFrameCounter()
        pulse1.commitLengthState()
        pulse2.commitLengthState()
        triangle.commitLengthState()
        noise.commitLengthState()
        triangle.stepTimer()
        dmc.stepTimer()
        noise.stepTimer()
        apuCycle = !apuCycle
        if (apuCycle) {
            pulse1.stepTimer()
            pulse2.stepTimer()
        }
        dmc.processDelays()
        samplePhase += SAMPLE_RATE
        if (samplePhase >= timing.cpuHz) {
            samplePhase -= timing.cpuHz
            appendSample(mix())
        }
    }

    private fun stepFrameCounter() {
        if (frameIrqClearDelay > 0 && --frameIrqClearDelay == 0) frameIrqFlag = false
        frameCycle++
        val events = if (frameMode == 0) timing.apuFourStepEvents else timing.apuFiveStepEvents
        if (frameEventIndex < events.size && frameCycle == events[frameEventIndex]) {
            val actions = if (frameMode == 0) FOUR_STEP_ACTIONS else FIVE_STEP_ACTIONS
            val action = actions[frameEventIndex]
            if (blockFrameCounterTicks == 0) {
                if ((action and QUARTER_FRAME) != 0) quarterFrame()
                if ((action and HALF_FRAME) != 0) halfFrame()
                if (action != 0) blockFrameCounterTicks = 2
            }
            if (frameMode == 0 && frameEventIndex >= 3) {
                frameIrqFlag = true
                frameIrqClearDelay = 0
                if (!frameIrqInhibit) {
                    frameIrqPending = true
                } else if (frameEventIndex == events.lastIndex) {
                    frameIrqFlag = false
                }
            }
            frameEventIndex++
            if (frameEventIndex == events.size) {
                frameCycle = 0
                frameEventIndex = 0
            }
        }

        if (frameCounterWriteDelay > 0 && --frameCounterWriteDelay == 0) {
            frameMode = (pendingFrameCounterValue shr 7) and 1
            pendingFrameCounterValue = -1
            frameCycle = 0
            frameEventIndex = 0
            if (frameMode == 1 && blockFrameCounterTicks == 0) {
                quarterFrame()
                halfFrame()
                blockFrameCounterTicks = 2
            }
        }
        if (blockFrameCounterTicks > 0) blockFrameCounterTicks--
    }

    private fun normalizeFrameEventIndex() {
        val events = if (frameMode == 0) timing.apuFourStepEvents else timing.apuFiveStepEvents
        frameEventIndex = 0
        while (frameEventIndex < events.size && events[frameEventIndex] <= frameCycle) frameEventIndex++
        if (frameEventIndex == events.size) {
            frameCycle = 0
            frameEventIndex = 0
        }
    }

    private fun quarterFrame() {
        pulse1.clockEnvelope(); pulse2.clockEnvelope(); triangle.clockLinearCounter(); noise.clockEnvelope()
    }

    private fun halfFrame() {
        pulse1.clockLength(); pulse2.clockLength(); triangle.clockLength(); noise.clockLength()
        pulse1.clockSweep(); pulse2.clockSweep()
    }

    private fun mix(): Double {
        val p1 = pulse1.output()
        val p2 = pulse2.output()
        val t = triangle.output()
        val n = noise.output()
        val d = dmc.output()
        val mixed = PULSE_MIX[p1 + p2] + TND_MIX[tndIndex(t, n, d)]
        highPass90Output = HIGH_PASS_90_HZ * (highPass90Output + mixed - highPass90Input)
        highPass90Input = mixed
        highPass440Output = HIGH_PASS_440_HZ * (highPass440Output + highPass90Output - highPass440Input)
        highPass440Input = highPass90Output
        lowPass14kOutput += LOW_PASS_14_KHZ * (highPass440Output - lowPass14kOutput)
        return lowPass14kOutput
    }

    fun irqPending(): Boolean = frameIrqPending || dmc.irqPending()

    private fun appendSample(value: Double) {
        if (sampleCount >= samples.size) return
        val clamped = when {
            value > 1.0 -> 1.0
            value < -1.0 -> -1.0
            else -> value
        }
        samples[sampleCount++] = (clamped * Short.MAX_VALUE).toInt().toShort()
    }

    private class PulseChannel(private val channelOne: Boolean, private val stateProvider: () -> PulseState) {
        private val state: PulseState get() = stateProvider()
        var enabled: Boolean get() = state.enabled; set(value) { state.enabled = value }
        var lengthCounter: Int get() = state.lengthCounter; set(value) { state.lengthCounter = value }
        private var duty: Int get() = state.values[0]; set(value) { state.values[0] = value }
        private var timer: Int get() = state.values[1]; set(value) { state.values[1] = value }
        private var timerCounter: Int get() = state.values[2]; set(value) { state.values[2] = value }
        private var sequence: Int get() = state.values[3]; set(value) { state.values[3] = value }
        private var volume: Int get() = state.values[4]; set(value) { state.values[4] = value }
        private var envelopeDivider: Int get() = state.values[5]; set(value) { state.values[5] = value }
        private var envelopeDecay: Int get() = state.values[6]; set(value) { state.values[6] = value }
        private var sweepPeriod: Int get() = state.values[7]; set(value) { state.values[7] = value }
        private var sweepShift: Int get() = state.values[8]; set(value) { state.values[8] = value }
        private var sweepDivider: Int get() = state.values[9]; set(value) { state.values[9] = value }
        private var pendingLengthReload: Int get() = state.values[10]; set(value) { state.values[10] = value }
        private var previousLength: Int get() = state.values[11]; set(value) { state.values[11] = value }
        private var envelopeLoop: Boolean get() = state.flags[0]; set(value) { state.flags[0] = value }
        private var constantVolume: Boolean get() = state.flags[1]; set(value) { state.flags[1] = value }
        private var envelopeStart: Boolean get() = state.flags[2]; set(value) { state.flags[2] = value }
        private var sweepEnabled: Boolean get() = state.flags[3]; set(value) { state.flags[3] = value }
        private var sweepNegate: Boolean get() = state.flags[4]; set(value) { state.flags[4] = value }
        private var sweepReload: Boolean get() = state.flags[5]; set(value) { state.flags[5] = value }
        private var lengthHalt: Boolean get() = state.flags[6]; set(value) { state.flags[6] = value }
        private var pendingLengthHalt: Boolean get() = state.flags[7]; set(value) { state.flags[7] = value }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    duty = (value shr 6) and 3
                    envelopeLoop = (value and 0x20) != 0
                    pendingLengthHalt = envelopeLoop
                    constantVolume = (value and 0x10) != 0
                    volume = value and 0x0F
                }

                1 -> {
                    sweepEnabled = (value and 0x80) != 0; sweepPeriod = ((value shr 4) and 7) + 1; sweepNegate =
                        (value and 0x08) != 0; sweepShift = value and 7; sweepReload = true
                }

                2 -> timer = (timer and 0x700) or value
                3 -> {
                    timer = (timer and 0x0FF) or ((value and 7) shl 8)
                    if (enabled) {
                        pendingLengthReload = LENGTH_TABLE[(value shr 3) and 31]
                        previousLength = lengthCounter
                    }
                    sequence = 0
                    envelopeStart = true
                }
            }
        }

        fun stepTimer() {
            if (timerCounter <= 0) {
                timerCounter = timer
                sequence = (sequence + 1) and 7
            } else timerCounter--
        }

        fun clockEnvelope() {
            if (envelopeStart) {
                envelopeStart = false; envelopeDecay = 15; envelopeDivider = volume
            } else if (envelopeDivider == 0) {
                envelopeDivider = volume
                if (envelopeDecay > 0) envelopeDecay-- else if (envelopeLoop) envelopeDecay = 15
            } else envelopeDivider--
        }

        fun clockLength() {
            if (!lengthHalt && lengthCounter > 0) lengthCounter--
        }

        fun commitLengthState() {
            if (pendingLengthReload > 0) {
                if (lengthCounter == previousLength) lengthCounter = pendingLengthReload
                pendingLengthReload = 0
            }
            lengthHalt = pendingLengthHalt
        }

        fun clockSweep() {
            if (sweepDivider > 0) sweepDivider--
            if (sweepDivider == 0 && !sweepReload) {
                if (sweepEnabled && sweepShift > 0 && timer >= 8) {
                    val target = sweepTarget()
                    if (target in 0..0x7FF) timer = target
                }
                sweepDivider = sweepPeriod
            }
            if (sweepReload) {
                sweepReload = false; sweepDivider = sweepPeriod
            }
        }

        fun output(): Int {
            if (!enabled || lengthCounter == 0 || timer < 8 || sweepTarget() > 0x7FF || DUTY_TABLE[duty][sequence] == 0) return 0
            return if (constantVolume) volume else envelopeDecay
        }

        private fun sweepTarget(): Int {
            val delta = timer shr sweepShift
            return if (sweepNegate) timer - delta - if (channelOne) 1 else 0 else timer + delta
        }
    }

    private class TriangleChannel(private val stateProvider: () -> TriangleState) {
        private val state: TriangleState get() = stateProvider()
        var enabled: Boolean get() = state.enabled; set(value) { state.enabled = value }
        var lengthCounter: Int get() = state.lengthCounter; set(value) { state.lengthCounter = value }
        private var reloadValue: Int get() = state.values[0]; set(value) { state.values[0] = value }
        private var linearCounter: Int get() = state.values[1]; set(value) { state.values[1] = value }
        private var timer: Int get() = state.values[2]; set(value) { state.values[2] = value }
        private var timerCounter: Int get() = state.values[3]; set(value) { state.values[3] = value }
        private var sequence: Int get() = state.values[4]; set(value) { state.values[4] = value }
        private var outputLevel: Int get() = state.values[5]; set(value) { state.values[5] = value }
        private var pendingLengthReload: Int get() = state.values[6]; set(value) { state.values[6] = value }
        private var previousLength: Int get() = state.values[7]; set(value) { state.values[7] = value }
        private var control: Boolean get() = state.flags[0]; set(value) { state.flags[0] = value }
        private var reloadFlag: Boolean get() = state.flags[1]; set(value) { state.flags[1] = value }
        private var lengthHalt: Boolean get() = state.flags[2]; set(value) { state.flags[2] = value }
        private var pendingLengthHalt: Boolean get() = state.flags[3]; set(value) { state.flags[3] = value }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    control = (value and 0x80) != 0
                    pendingLengthHalt = control
                    reloadValue = value and 0x7F
                }

                2 -> timer = (timer and 0x700) or value
                3 -> {
                    timer = (timer and 0x0FF) or ((value and 7) shl 8)
                    if (enabled) {
                        pendingLengthReload = LENGTH_TABLE[(value shr 3) and 31]
                        previousLength = lengthCounter
                    }
                    reloadFlag = true
                }
            }
        }

        fun stepTimer() {
            if (timerCounter <= 0) {
                timerCounter = timer
                if (lengthCounter > 0 && linearCounter > 0) {
                    sequence = (sequence + 1) and 31
                    outputLevel = TRIANGLE_TABLE[sequence]
                }
            } else timerCounter--
        }

        fun clockLinearCounter() {
            if (reloadFlag) linearCounter = reloadValue else if (linearCounter > 0) linearCounter--
            if (!control) reloadFlag = false
        }

        fun clockLength() {
            if (!lengthHalt && lengthCounter > 0) lengthCounter--
        }

        fun commitLengthState() {
            if (pendingLengthReload > 0) {
                if (lengthCounter == previousLength) lengthCounter = pendingLengthReload
                pendingLengthReload = 0
            }
            lengthHalt = pendingLengthHalt
        }

        fun output(): Int = outputLevel
    }

    private class NoiseChannel(private val stateProvider: () -> NoiseState) {
        private val state: NoiseState get() = stateProvider()
        var periods: IntArray = Timing.DEFAULT.noisePeriods
        var enabled: Boolean get() = state.enabled; set(value) { state.enabled = value }
        var lengthCounter: Int get() = state.lengthCounter; set(value) { state.lengthCounter = value }
        private var volume: Int get() = state.values[0]; set(value) { state.values[0] = value }
        private var envelopeDivider: Int get() = state.values[1]; set(value) { state.values[1] = value }
        private var envelopeDecay: Int get() = state.values[2]; set(value) { state.values[2] = value }
        private var timer: Int get() = state.values[3]; set(value) { state.values[3] = value }
        private var timerCounter: Int get() = state.values[4]; set(value) { state.values[4] = value }
        private var shift: Int get() = state.values[5]; set(value) { state.values[5] = value }
        private var pendingLengthReload: Int get() = state.values[6]; set(value) { state.values[6] = value }
        private var previousLength: Int get() = state.values[7]; set(value) { state.values[7] = value }
        private var envelopeLoop: Boolean get() = state.flags[0]; set(value) { state.flags[0] = value }
        private var constantVolume: Boolean get() = state.flags[1]; set(value) { state.flags[1] = value }
        private var envelopeStart: Boolean get() = state.flags[2]; set(value) { state.flags[2] = value }
        private var mode: Boolean get() = state.flags[3]; set(value) { state.flags[3] = value }
        private var lengthHalt: Boolean get() = state.flags[4]; set(value) { state.flags[4] = value }
        private var pendingLengthHalt: Boolean get() = state.flags[5]; set(value) { state.flags[5] = value }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    envelopeLoop = (value and 0x20) != 0
                    pendingLengthHalt = envelopeLoop
                    constantVolume = (value and 0x10) != 0
                    volume = value and 0x0F
                }

                2 -> {
                    mode = (value and 0x80) != 0; timer = periods[value and 0x0F]
                }

                3 -> {
                    if (enabled) {
                        pendingLengthReload = LENGTH_TABLE[(value shr 3) and 31]
                        previousLength = lengthCounter
                    }
                    envelopeStart = true
                }
            }
        }

        fun stepTimer() {
            if (timerCounter <= 0) {
                timerCounter = timer - 1
                val tap = if (mode) 6 else 1
                val feedback = (shift xor (shift shr tap)) and 1
                shift = (shift shr 1) or (feedback shl 14)
            } else timerCounter--
        }

        fun clockEnvelope() {
            if (envelopeStart) {
                envelopeStart = false; envelopeDecay = 15; envelopeDivider = volume
            } else if (envelopeDivider == 0) {
                envelopeDivider = volume
                if (envelopeDecay > 0) envelopeDecay-- else if (envelopeLoop) envelopeDecay = 15
            } else envelopeDivider--
        }

        fun clockLength() {
            if (!lengthHalt && lengthCounter > 0) lengthCounter--
        }

        fun commitLengthState() {
            if (pendingLengthReload > 0) {
                if (lengthCounter == previousLength) lengthCounter = pendingLengthReload
                pendingLengthReload = 0
            }
            lengthHalt = pendingLengthHalt
        }

        fun output(): Int = if (enabled && lengthCounter > 0 && (shift and 1) == 0) {
            if (constantVolume) volume else envelopeDecay
        } else 0
    }

    private class DmcChannel(
        private val dmcDma: DmcDma,
        private val stateProvider: () -> DmcState,
    ) {
        private val state: DmcState get() = stateProvider()
        var periods: IntArray = Timing.DEFAULT.dmcPeriods
        private var period: Int get() = state.values[0]; set(value) { state.values[0] = value }
        private var timerCounter: Int get() = if (state.values[1] < 0) period - 1 else state.values[1]; set(value) { state.values[1] = value }
        private var outputLevel: Int get() = state.values[2]; set(value) { state.values[2] = value }
        private var sampleAddress: Int get() = state.values[3]; set(value) { state.values[3] = value }
        private var sampleLength: Int get() = state.values[4]; set(value) { state.values[4] = value }
        private var currentAddress: Int get() = state.values[5]; set(value) { state.values[5] = value }
        private var bytesRemaining: Int get() = state.values[6]; set(value) { state.values[6] = value }
        private var shiftRegister: Int get() = state.values[7]; set(value) { state.values[7] = value }
        private var bitsRemaining: Int get() = state.values[8]; set(value) { state.values[8] = value }
        private var sampleBuffer: Int get() = state.values[9]; set(value) { state.values[9] = value }
        private var transferStartDelay: Int get() = state.values[10]; set(value) { state.values[10] = value }
        private var disableDelay: Int get() = state.values[11]; set(value) { state.values[11] = value }
        private var channelEnabled: Boolean get() = state.flags[0]; set(value) { state.flags[0] = value }
        private var irqEnabled: Boolean get() = state.flags[1]; set(value) { state.flags[1] = value }
        private var irqRequested: Boolean get() = state.flags[2]; set(value) { state.flags[2] = value }
        private var loop: Boolean get() = state.flags[3]; set(value) { state.flags[3] = value }
        private var sampleBufferFull: Boolean get() = state.flags[4]; set(value) { state.flags[4] = value }
        private var silence: Boolean get() = !state.flags[5]; set(value) { state.flags[5] = !value }
        private var sampleFetchPending: Boolean get() = state.flags[6]; set(value) { state.flags[6] = value }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    irqEnabled = (value and 0x80) != 0
                    if (!irqEnabled) irqRequested = false
                    loop = (value and 0x40) != 0
                    period = periods[value and 0x0F]
                }

                1 -> outputLevel = value and 0x7F
                2 -> sampleAddress = 0xC000 + (value shl 6)
                3 -> sampleLength = (value shl 4) + 1
            }
        }

        fun setEnabled(value: Boolean, apuCycle: Boolean) {
            channelEnabled = value
            val delay = if (apuCycle) 2 else 3
            if (!value) {
                if (disableDelay == 0) disableDelay = delay
            } else if (bytesRemaining == 0) {
                restartSample()
                transferStartDelay = delay
            }
        }

        fun processDelays() {
            if (disableDelay > 0 && --disableDelay == 0) {
                bytesRemaining = 0
                transferStartDelay = 0
                if (sampleFetchPending && dmcDma.cancelBeforeHalt()) sampleFetchPending = false
            }
            if (transferStartDelay > 0 && --transferStartDelay == 0) fetchSampleIfNeeded()
        }

        fun active(): Boolean {
            return bytesRemaining > 0
        }

        fun irqPending(): Boolean = irqRequested

        fun clearIrq() {
            irqRequested = false
        }

        fun stepTimer() {
            finishSampleFetch()
            if (timerCounter <= 0) {
                timerCounter = period - 1
                clockOutputUnit()
            } else {
                timerCounter--
            }
        }

        fun output(): Int {
            return outputLevel
        }

        private fun fetchSampleIfNeeded() {
            if (sampleBufferFull || sampleFetchPending || bytesRemaining == 0) return
            sampleFetchPending = dmcDma.request(currentAddress)
        }

        private fun finishSampleFetch() {
            if (!sampleFetchPending) return
            val value = dmcDma.takeResult()
            if (value < 0) return
            sampleFetchPending = false
            sampleBuffer = value.low8Bits()
            sampleBufferFull = true
            currentAddress++
            if (currentAddress > 0xFFFF) currentAddress = 0x8000
            bytesRemaining--
            if (bytesRemaining == 0) {
                if (loop) restartSample() else if (irqEnabled) irqRequested = true
            }
        }

        private fun clockOutputUnit() {
            if (!silence) {
                if ((shiftRegister and 1) == 1) {
                    if (outputLevel <= 125) outputLevel += 2
                } else if (outputLevel >= 2) {
                    outputLevel -= 2
                }
            }
            shiftRegister = shiftRegister shr 1
            bitsRemaining--
            if (bitsRemaining <= 0) {
                bitsRemaining = 8
                if (sampleBufferFull) {
                    silence = false
                    shiftRegister = sampleBuffer
                    sampleBufferFull = false
                    fetchSampleIfNeeded()
                } else {
                    silence = true
                }
            }
        }

        private fun restartSample() {
            currentAddress = sampleAddress
            bytesRemaining = sampleLength
        }
    }
}
