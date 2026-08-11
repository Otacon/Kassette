package nes.apu

import kotlin.math.PI
import kotlin.math.exp
import nes.util.low16Bits
import nes.util.low8Bits
import nes.Timing

class NesApu(
    dmcDma: DmcDma,
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
        private val FOUR_STEP_ACTIONS = intArrayOf(QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME, QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME)
        private val FIVE_STEP_ACTIONS = intArrayOf(QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME, QUARTER_FRAME, QUARTER_FRAME or HALF_FRAME, 0)
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

    private val pulse1 = PulseChannel(channelOne = true)
    private val pulse2 = PulseChannel(channelOne = false)
    private val triangle = TriangleChannel()
    private val noise = NoiseChannel()
    private val dmc = DmcChannel(dmcDma)
    private var frameCycle = 0
    private var frameEventIndex = 0
    private var frameMode = 0
    private var frameIrqInhibit = false
    private var frameIrqPending = false
    private var apuCycle = false
    private var samplePhase = 0
    private var highPass90Input = 0.0
    private var highPass90Output = 0.0
    private var highPass440Input = 0.0
    private var highPass440Output = 0.0
    private var lowPass14kOutput = 0.0
    var timing: Timing = Timing.DEFAULT
        set(value) {
            field = value
            noise.periods = value.noisePeriods
            dmc.periods = value.dmcPeriods
        }

    fun reset() {
        pulse1.reset()
        pulse2.reset()
        triangle.reset()
        noise.reset()
        dmc.reset()
        frameCycle = 0
        frameEventIndex = 0
        frameMode = 0
        frameIrqInhibit = false
        frameIrqPending = false
        apuCycle = false
        samplePhase = 0
        sampleCount = 0
        highPass90Input = 0.0
        highPass90Output = 0.0
        highPass440Input = 0.0
        highPass440Output = 0.0
        lowPass14kOutput = 0.0
    }

    fun beginFrame() {
        sampleCount = 0
    }

    fun cpuRead(address: Int): Int {
        if (address.low16Bits() != 0x4015) return 0
        val status = (if (pulse1.lengthCounter > 0) 0x01 else 0) or
                (if (pulse2.lengthCounter > 0) 0x02 else 0) or
                (if (triangle.lengthCounter > 0) 0x04 else 0) or
                (if (noise.lengthCounter > 0) 0x08 else 0) or
                (if (dmc.active()) 0x10 else 0) or
                (if (frameIrqPending) 0x40 else 0) or
                (if (dmc.irqPending()) 0x80 else 0)
        frameIrqPending = false
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
                dmc.setEnabled((v and 0x10) != 0)
                if (!pulse1.enabled) pulse1.lengthCounter = 0
                if (!pulse2.enabled) pulse2.lengthCounter = 0
                if (!triangle.enabled) triangle.lengthCounter = 0
                if (!noise.enabled) noise.lengthCounter = 0
            }

            0x4017 -> {
                frameMode = (v shr 7) and 1
                frameIrqInhibit = (v and 0x40) != 0
                if (frameIrqInhibit) frameIrqPending = false
                frameCycle = 0
                frameEventIndex = 0
                if (frameMode == 1) {
                    quarterFrame()
                    halfFrame()
                }
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
        triangle.stepTimer()
        dmc.stepTimer()
        noise.stepTimer()
        apuCycle = !apuCycle
        if (apuCycle) {
            pulse1.stepTimer()
            pulse2.stepTimer()
        }
        stepFrameCounter()
        samplePhase += SAMPLE_RATE
        if (samplePhase >= timing.cpuHz) {
            samplePhase -= timing.cpuHz
            appendSample(mix())
        }
    }

    private fun stepFrameCounter() {
        frameCycle++
        val events = if (frameMode == 0) timing.apuFourStepEvents else timing.apuFiveStepEvents
        if (frameCycle != events[frameEventIndex]) return
        val actions = if (frameMode == 0) FOUR_STEP_ACTIONS else FIVE_STEP_ACTIONS
        val action = actions[frameEventIndex]
        if ((action and QUARTER_FRAME) != 0) quarterFrame()
        if ((action and HALF_FRAME) != 0) halfFrame()
        if (frameMode == 0 && frameEventIndex == events.lastIndex && !frameIrqInhibit) frameIrqPending = true
        frameEventIndex++
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

    private class PulseChannel(private val channelOne: Boolean) {
        var enabled = false
        var lengthCounter = 0
        private var duty = 0
        private var timer = 0
        private var timerCounter = 0
        private var sequence = 0
        private var envelopeLoop = false
        private var constantVolume = false
        private var volume = 0
        private var envelopeStart = false
        private var envelopeDivider = 0
        private var envelopeDecay = 0
        private var sweepEnabled = false
        private var sweepPeriod = 0
        private var sweepNegate = false
        private var sweepShift = 0
        private var sweepReload = false
        private var sweepDivider = 0

        fun reset() {
            enabled = false; lengthCounter = 0; duty = 0; timer = 0; timerCounter = 0; sequence = 0
            envelopeLoop = false; constantVolume = false; volume = 0; envelopeStart = false; envelopeDivider =
                0; envelopeDecay = 0
            sweepEnabled = false; sweepPeriod = 0; sweepNegate = false; sweepShift = 0; sweepReload =
                false; sweepDivider = 0
        }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    duty = (value shr 6) and 3; envelopeLoop = (value and 0x20) != 0; constantVolume =
                        (value and 0x10) != 0; volume = value and 0x0F
                }

                1 -> {
                    sweepEnabled = (value and 0x80) != 0; sweepPeriod = ((value shr 4) and 7) + 1; sweepNegate =
                        (value and 0x08) != 0; sweepShift = value and 7; sweepReload = true
                }

                2 -> timer = (timer and 0x700) or value
                3 -> {
                    timer = (timer and 0x0FF) or ((value and 7) shl 8); if (enabled) lengthCounter =
                        LENGTH_TABLE[(value shr 3) and 31]; sequence = 0; envelopeStart = true
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
            if (!envelopeLoop && lengthCounter > 0) lengthCounter--
        }

        fun clockSweep() {
            if (sweepDivider == 0) {
                if (sweepEnabled && sweepShift > 0 && timer >= 8) {
                    val target = sweepTarget()
                    if (target in 0..0x7FF) timer = target
                }
                sweepDivider = sweepPeriod
            } else sweepDivider--
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

    private class TriangleChannel {
        var enabled = false
        var lengthCounter = 0
        private var control = false
        private var reloadValue = 0
        private var reloadFlag = false
        private var linearCounter = 0
        private var timer = 0
        private var timerCounter = 0
        private var sequence = 0
        private var outputLevel = 0

        fun reset() {
            enabled = false; lengthCounter = 0; control = false; reloadValue = 0; reloadFlag = false; linearCounter =
                0; timer = 0; timerCounter = 0; sequence = 0; outputLevel = 0
        }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    control = (value and 0x80) != 0; reloadValue = value and 0x7F
                }

                2 -> timer = (timer and 0x700) or value
                3 -> {
                    timer = (timer and 0x0FF) or ((value and 7) shl 8); if (enabled) lengthCounter =
                        LENGTH_TABLE[(value shr 3) and 31]; reloadFlag = true
                }
            }
        }

        fun stepTimer() {
            if (timerCounter <= 0) {
                timerCounter = timer
                if (lengthCounter > 0 && linearCounter > 0 && timer > 1) {
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
            if (!control && lengthCounter > 0) lengthCounter--
        }

        fun output(): Int = outputLevel
    }

    private class NoiseChannel {
        var periods: IntArray = Timing.DEFAULT.noisePeriods
        var enabled = false
        var lengthCounter = 0
        private var envelopeLoop = false
        private var constantVolume = false
        private var volume = 0
        private var envelopeStart = false
        private var envelopeDivider = 0
        private var envelopeDecay = 0
        private var mode = false
        private var timer = 0
        private var timerCounter = 0
        private var shift = 1

        fun reset() {
            enabled = false; lengthCounter = 0; envelopeLoop = false; constantVolume = false; volume =
                0; envelopeStart = false; envelopeDivider = 0; envelopeDecay = 0; mode = false; timer =
                0; timerCounter = 0; shift = 1
        }

        fun write(register: Int, value: Int) {
            when (register) {
                0 -> {
                    envelopeLoop = (value and 0x20) != 0; constantVolume = (value and 0x10) != 0; volume =
                        value and 0x0F
                }

                2 -> {
                    mode = (value and 0x80) != 0; timer = periods[value and 0x0F]
                }

                3 -> {
                    if (enabled) lengthCounter = LENGTH_TABLE[(value shr 3) and 31]; envelopeStart = true
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
            if (!envelopeLoop && lengthCounter > 0) lengthCounter--
        }

        fun output(): Int = if (enabled && lengthCounter > 0 && (shift and 1) == 0) {
            if (constantVolume) volume else envelopeDecay
        } else 0
    }

    private class DmcChannel(
        private val dmcDma: DmcDma,
    ) {
        var periods: IntArray = Timing.DEFAULT.dmcPeriods
        private var enabled = false
        private var irqEnabled = false
        private var irqRequested = false
        private var loop = false
        private var period = periods[0]
        private var timerCounter = period - 1
        private var outputLevel = 0
        private var sampleAddress = 0xC000
        private var sampleLength = 1
        private var currentAddress = 0xC000
        private var bytesRemaining = 0
        private var shiftRegister = 0
        private var bitsRemaining = 8
        private var sampleBuffer = 0
        private var sampleBufferFull = false
        private var silence = true

        fun reset() {
            enabled = false
            irqEnabled = false
            irqRequested = false
            loop = false
            period = periods[0]
            timerCounter = period - 1
            outputLevel = 0
            sampleAddress = 0xC000
            sampleLength = 1
            currentAddress = sampleAddress
            bytesRemaining = 0
            shiftRegister = 0
            bitsRemaining = 8
            sampleBuffer = 0
            sampleBufferFull = false
            silence = true
        }

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

        fun setEnabled(value: Boolean) {
            enabled = value
            if (!enabled) {
                bytesRemaining = 0
            } else if (bytesRemaining == 0) {
                restartSample()
                fetchSampleIfNeeded()
            }
        }

        fun active(): Boolean {
            return bytesRemaining > 0
        }

        fun irqPending(): Boolean = irqRequested

        fun clearIrq() {
            irqRequested = false
        }

        fun stepTimer() {
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
            if (sampleBufferFull || bytesRemaining == 0) return
            sampleBuffer = dmcDma.read(currentAddress).low8Bits()
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
