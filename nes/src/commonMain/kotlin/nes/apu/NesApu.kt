/*
 * This file is part of Kassette.
 *
 * This Kotlin implementation is ported and adapted from MesenCE
 * (https://github.com/nesdev-org/MesenCE). MesenCE is licensed under
 * the GNU General Public License version 3.
 *
 * This modified Kotlin port is distributed under the GNU General Public
 * License version 3. See the repository LICENSE file for details.
 */

package nes.apu

import nes.console.NesConsole
import nes.console.NesConsoleApu
import nes.console.NesConstants
import nes.cpu.ConsoleRegion
import nes.cpu.IRQSource
import nes.memory.INesMemoryHandler
import nes.memory.MemoryOperation
import nes.memory.MemoryRanges

class NesApu : NesConsoleApu, INesMemoryHandler {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MAX_FRAME_SAMPLES = 4096
        private const val MIXER_CYCLE_LENGTH = 120_000
        private const val CHANNEL_COUNT = 5
        private const val BLIP_BUFFER_EXTRA = 18

        private fun channelIndex(channel: ApuAudioChannel): Int = when (channel) {
            ApuAudioChannel.Square1 -> 0
            ApuAudioChannel.Square2 -> 1
            ApuAudioChannel.Triangle -> 2
            ApuAudioChannel.Noise -> 3
            ApuAudioChannel.Dmc -> 4
        }
    }

    val samples = ShortArray(MAX_FRAME_SAMPLES)
    var sampleCount = 0
        private set

    private lateinit var console: NesConsole
    private var region: ConsoleRegion = ConsoleRegion.Ntsc
    private var apuEnabled = true
    private var needToRun = false
    private var previousCycle = 0
    private var currentCycle = 0
    private var apuDisabledStamp = 0L
    private val channelDeltas = IntArray(CHANNEL_COUNT * MIXER_CYCLE_LENGTH)
    private val currentOutput = IntArray(CHANNEL_COUNT)
    private val blipBuffer = BlipBuffer(MAX_FRAME_SAMPLES)
    private var previousMixedOutput = 0

    private val square1 = SquareChannel(isChannel1 = true, apu = this)
    private val square2 = SquareChannel(isChannel1 = false, apu = this)
    private val triangle = TriangleChannel(apu = this)
    private val noise = NoiseChannel(apu = this)
    private val dmc = DeltaModulationChannel(apu = this)
    private val frameCounter = ApuFrameCounter(apu = this)

    override fun initConsole(console: NesConsole) {
        this.console = console
        setRegion(console.region)
        reset(false)
    }

    override fun getMemoryRanges(ranges: MemoryRanges) {
        ranges.addHandler(MemoryOperation.Write, 0x4000, 0x4013)
        ranges.addHandler(MemoryOperation.Write, 0x4015)
        ranges.addHandler(MemoryOperation.Write, 0x4017)
        ranges.addHandler(MemoryOperation.Read, 0x4015)
        ranges.addHandler(MemoryOperation.Read, 0x4018, 0x401A)
    }

    override fun readRam(addr: Int): Int {
        run()
        return when (addr and 0xFFFF) {
            0x4015 -> {
                val status = status(peek = false) or (console.memoryManager.getInternalOpenBus() and 0x20)
                console.cpu.clearIrqSource(IRQSource.FrameCounter)
                status and 0xFF
            }
            0x4018 -> if (console.options.apu.cpuTestMode) square1.output or (square2.output shl 4) else console.getOpenBus()
            0x4019 -> if (console.options.apu.cpuTestMode) triangle.output or (noise.output shl 4) else console.getOpenBus()
            0x401A -> if (console.options.apu.cpuTestMode) dmc.output else console.getOpenBus()
            else -> console.getOpenBus()
        }
    }

    override fun peekRam(addr: Int): Int {
        run()
        return status(peek = true)
    }

    override fun writeRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        val v = value and 0xFF
        when {
            address >= 0x4000 && address <= 0x4003 -> square1.writeRam(address, v)
            address >= 0x4004 && address <= 0x4007 -> square2.writeRam(address, v)
            address >= 0x4008 && address <= 0x400B -> triangle.writeRam(address, v)
            address >= 0x400C && address <= 0x400F -> noise.writeRam(address, v)
            address >= 0x4010 && address <= 0x4013 -> dmc.writeRam(address, v)
            address == 0x4015 -> {
                run()
                console.cpu.clearIrqSource(IRQSource.Dmc)
                square1.setEnabled((v and 0x01) != 0)
                square2.setEnabled((v and 0x02) != 0)
                triangle.setEnabled((v and 0x04) != 0)
                noise.setEnabled((v and 0x08) != 0)
                dmc.setEnabled((v and 0x10) != 0)
            }
            address == 0x4017 -> frameCounter.writeRam(v)
        }
    }

    override fun reset(softReset: Boolean) {
        apuEnabled = true
        currentCycle = 0
        previousCycle = 0
        needToRun = false
        sampleCount = 0
        previousMixedOutput = 0
        currentOutput.fill(0)
        channelDeltas.fill(0)
        blipBuffer.clear()
        square1.reset(softReset)
        square2.reset(softReset)
        triangle.reset(softReset)
        noise.reset(softReset)
        dmc.reset(softReset)
        frameCounter.reset(softReset)
    }

    override fun setRegion(region: ConsoleRegion) {
        run()
        this.region = if (region == ConsoleRegion.Dendy) ConsoleRegion.Ntsc else region
        blipBuffer.setRates(clockRate(), DEFAULT_SAMPLE_RATE)
        frameCounter.setRegion(this.region)
        noise.setRegion(this.region)
        dmc.setRegion(this.region)
    }

    override fun processCpuClock() {
        if (apuEnabled) exec()
    }

    override fun endFrame() {
        run()
        square1.endFrame()
        square2.endFrame()
        triangle.endFrame()
        noise.endFrame()
        dmc.endFrame()
        renderMixerFrame(currentCycle)
        currentCycle = 0
        previousCycle = 0
    }

    override fun beginFrame() {
        sampleCount = 0
    }

    override fun setApuStatus(enabled: Boolean) {
        if (apuEnabled == enabled) return
        if (!enabled) {
            apuDisabledStamp = console.cpu.getCycleCount()
            apuEnabled = false
        } else {
            val gap = console.cpu.getCycleCount() - apuDisabledStamp
            if ((gap and 1L) != 0L) exec()
            apuEnabled = true
        }
    }

    override fun getDmcReadAddress(): Int = dmc.getDmcReadAddress()

    override fun setDmcReadBuffer(value: Int) = dmc.setDmcReadBuffer(value)

    fun captureSnapshot(): ApuState = ApuState(
        Square1 = square1.getState(),
        Square2 = square2.getState(),
        Triangle = triangle.getState(),
        Noise = noise.getState(),
        Dmc = dmc.getState(),
        FrameCounter = frameCounter.getState(),
    )

    fun captureInternalSnapshot(): NesApuSnapshot = NesApuSnapshot(
        ApuEnabled = apuEnabled,
        NeedToRun = needToRun,
        PreviousCycle = previousCycle,
        CurrentCycle = currentCycle,
        ApuDisabledStamp = apuDisabledStamp,
        SampleCount = sampleCount,
        Samples = samples.copyOf(),
        ChannelDeltas = channelDeltas.copyOf(),
        CurrentOutput = currentOutput.copyOf(),
        PreviousMixedOutput = previousMixedOutput,
        BlipBuffer = blipBuffer.captureSnapshot(),
        Square1 = square1.captureSnapshot(),
        Square2 = square2.captureSnapshot(),
        Triangle = triangle.captureSnapshot(),
        Noise = noise.captureSnapshot(),
        Dmc = dmc.captureSnapshot(),
        FrameCounter = frameCounter.captureSnapshot(),
    )

    fun restoreSnapshot(snapshot: NesApuSnapshot) {
        apuEnabled = snapshot.ApuEnabled
        needToRun = snapshot.NeedToRun
        previousCycle = snapshot.PreviousCycle
        currentCycle = snapshot.CurrentCycle
        apuDisabledStamp = snapshot.ApuDisabledStamp
        sampleCount = snapshot.SampleCount.coerceIn(0, samples.size)
        snapshot.Samples.copyInto(samples, endIndex = minOf(snapshot.Samples.size, samples.size))
        square1.restoreSnapshot(snapshot.Square1)
        square2.restoreSnapshot(snapshot.Square2)
        triangle.restoreSnapshot(snapshot.Triangle)
        noise.restoreSnapshot(snapshot.Noise)
        dmc.restoreSnapshot(snapshot.Dmc)
        frameCounter.restoreSnapshot(snapshot.FrameCounter)
        if (snapshot.CurrentOutput.size >= CHANNEL_COUNT) {
            snapshot.CurrentOutput.copyInto(currentOutput, endIndex = CHANNEL_COUNT)
        } else {
            currentOutput[0] = square1.output
            currentOutput[1] = square2.output
            currentOutput[2] = triangle.output
            currentOutput[3] = noise.output
            currentOutput[4] = dmc.output
        }
        channelDeltas.fill(0)
        snapshot.ChannelDeltas.copyInto(channelDeltas, endIndex = minOf(snapshot.ChannelDeltas.size, channelDeltas.size))
        blipBuffer.restoreSnapshot(snapshot.BlipBuffer)
        previousMixedOutput = snapshot.PreviousMixedOutput
    }

    internal fun setNeedToRun() {
        needToRun = true
    }

    internal fun run() {
        var cyclesToRun = currentCycle - previousCycle
        while (cyclesToRun > 0) {
            previousCycle += frameCounter.run(cyclesToRun).also { cyclesToRun -= it }
            square1.reloadLengthCounter()
            square2.reloadLengthCounter()
            noise.reloadLengthCounter()
            triangle.reloadLengthCounter()
            square1.run(previousCycle)
            square2.run(previousCycle)
            noise.run(previousCycle)
            triangle.run(previousCycle)
            dmc.run(previousCycle)
        }
    }

    internal fun frameCounterTick(type: FrameType) {
        square1.tickEnvelope()
        square2.tickEnvelope()
        triangle.tickLinearCounter()
        noise.tickEnvelope()
        if (type == FrameType.HalfFrame) {
            square1.tickLengthCounter()
            square2.tickLengthCounter()
            triangle.tickLengthCounter()
            noise.tickLengthCounter()
            square1.tickSweep()
            square2.tickSweep()
        }
    }

    internal fun cpuCycleCount(): Long = console.cpu.getCycleCount()
    internal fun masterClock(): Long = console.getMasterClock()
    internal fun clockRate(): Int = NesConstants.getClockRate(region)
    internal fun startDmcTransfer() = console.cpu.startDmcTransfer()
    internal fun stopDmcTransfer() = console.cpu.stopDmcTransfer()
    internal fun setIrq(source: IRQSource) = console.cpu.setIrqSource(source)
    internal fun clearIrq(source: IRQSource) = console.cpu.clearIrqSource(source)
    internal fun hasIrq(source: IRQSource): Boolean = console.cpu.hasIrqSource(source)
    internal fun setNextFrameOverclockStatus(disabled: Boolean) = console.setNextFrameOverclockStatus(disabled)
    internal fun enableDmcSampleDuplicationGlitch(): Boolean = console.options.apu.enableDmcSampleDuplicationGlitch
    internal fun reduceDmcPopping(): Boolean = console.options.apu.reduceDmcPopping
    internal fun reverseDpcmBitOrder(): Boolean = console.options.apu.reverseDpcmBitOrder
    internal fun swapDutyCycles(): Boolean = console.options.apu.swapDutyCycles
    internal fun silenceTriangleHighFrequency(): Boolean = console.options.apu.silenceTriangleHighFrequency
    internal fun disableNoiseModeFlag(): Boolean = console.options.apu.disableNoiseModeFlag

    internal fun addDelta(channel: ApuAudioChannel, time: Int, delta: Int) {
        if (delta == 0 || time < 0 || time >= MIXER_CYCLE_LENGTH) return
        val index = channelIndex(channel) * MIXER_CYCLE_LENGTH + time
        channelDeltas[index] += delta
    }

    private fun exec() {
        currentCycle++
        if (currentCycle >= MIXER_CYCLE_LENGTH - 1) {
            dmc.processClock()
            endFrame()
        } else if (needToRun(currentCycle)) run()
    }

    private fun renderMixerFrame(time: Int) {
        val end = if (time < MIXER_CYCLE_LENGTH) time else MIXER_CYCLE_LENGTH - 1
        var cycle = 0
        while (cycle <= end) {
            var changed = false
            var channel = 0
            while (channel < CHANNEL_COUNT) {
                val index = channel * MIXER_CYCLE_LENGTH + cycle
                val delta = channelDeltas[index]
                if (delta != 0) {
                    currentOutput[channel] += delta
                    channelDeltas[index] = 0
                    changed = true
                }
                channel++
            }

            if (changed) {
                val mixedOutput = getOutputVolume() * 4
                blipBuffer.addDelta(cycle, mixedOutput - previousMixedOutput)
                previousMixedOutput = mixedOutput
            }
            cycle++
        }
        blipBuffer.endFrame(time)
        if (sampleCount < samples.size) {
            sampleCount += blipBuffer.readSamples(samples, sampleCount, samples.size - sampleCount)
        }
    }

    private fun getOutputVolume(): Int {
        val squareOutput = currentOutput[0] + currentOutput[1]
        val tndOutput = currentOutput[4] + 2.7516713261 * currentOutput[2] + 1.8493587125 * currentOutput[3]
        val squareVolume = if (squareOutput == 0) 0.0 else (95.88 * 5000.0) / (8128.0 / squareOutput + 100.0)
        val tndVolume = if (tndOutput == 0.0) 0.0 else (159.79 * 5000.0) / (22638.0 / tndOutput + 100.0)
        return (squareVolume + tndVolume).toInt()
    }

    private fun needToRun(cycle: Int): Boolean {
        if (dmc.needToRun() || needToRun) {
            needToRun = false
            return true
        }
        val cyclesToRun = cycle - previousCycle
        return frameCounter.needToRun(cyclesToRun) || dmc.irqPending(cyclesToRun)
    }

    private fun status(peek: Boolean): Int {
        var status = 0
        if (square1.getStatus()) status = status or 0x01
        if (square2.getStatus()) status = status or 0x02
        if (triangle.getStatus()) status = status or 0x04
        if (noise.getStatus()) status = status or 0x08
        if (dmc.getStatus()) status = status or 0x10
        if (if (peek) frameCounter.peekIrqFlag() else frameCounter.getIrqFlag()) status = status or 0x40
        if (hasIrq(IRQSource.Dmc)) status = status or 0x80
        return status and 0xFF
    }

    private class BlipBuffer(private val size: Int) {
        private companion object {
            const val TIME_BITS = 20
            const val TIME_UNIT = 1L shl TIME_BITS
            const val BASS_SHIFT = 9
            const val HALF_WIDTH = 8
            const val PHASE_BITS = 5
            const val PHASE_COUNT = 1 shl PHASE_BITS
            const val DELTA_BITS = 15
            const val DELTA_UNIT = 1 shl DELTA_BITS
            const val FRAC_BITS = TIME_BITS

            val BL_STEP = intArrayOf(
                43, -115, 350, -488, 1136, -914, 5861, 21022,
                44, -118, 348, -473, 1076, -799, 5274, 21001,
                45, -121, 344, -454, 1011, -677, 4706, 20936,
                46, -122, 336, -431, 942, -549, 4156, 20829,
                47, -123, 327, -404, 868, -418, 3629, 20679,
                47, -122, 316, -375, 792, -285, 3124, 20488,
                47, -120, 303, -344, 714, -151, 2644, 20256,
                46, -117, 289, -310, 634, -17, 2188, 19985,
                46, -114, 273, -275, 553, 117, 1758, 19675,
                44, -108, 255, -237, 471, 247, 1356, 19327,
                43, -103, 237, -199, 390, 373, 981, 18944,
                42, -98, 218, -160, 310, 495, 633, 18527,
                40, -91, 198, -121, 231, 611, 314, 18078,
                38, -84, 178, -81, 153, 722, 22, 17599,
                36, -76, 157, -43, 80, 824, -241, 17092,
                34, -68, 135, -3, 8, 919, -476, 16558,
                32, -61, 115, 34, -60, 1006, -683, 16001,
                29, -52, 94, 70, -123, 1083, -862, 15422,
                27, -44, 73, 106, -184, 1152, -1015, 14824,
                25, -36, 53, 139, -239, 1211, -1142, 14210,
                22, -27, 34, 170, -290, 1261, -1244, 13582,
                20, -20, 16, 199, -335, 1301, -1322, 12942,
                18, -12, -3, 226, -375, 1331, -1376, 12293,
                15, -4, -19, 250, -410, 1351, -1408, 11638,
                13, 3, -35, 272, -439, 1361, -1419, 10979,
                11, 9, -49, 292, -464, 1362, -1410, 10319,
                9, 16, -63, 309, -483, 1354, -1383, 9660,
                7, 22, -75, 322, -496, 1337, -1339, 9005,
                6, 26, -85, 333, -504, 1312, -1280, 8355,
                4, 31, -94, 341, -507, 1278, -1205, 7713,
                3, 35, -102, 347, -506, 1238, -1119, 7082,
                1, 40, -110, 350, -499, 1190, -1021, 6464,
                0, 43, -115, 350, -488, 1136, -914, 5861,
            )

            fun step(row: Int, column: Int): Int = BL_STEP[row * HALF_WIDTH + column]
        }

        private val buffer = IntArray(size + BLIP_BUFFER_EXTRA)
        private var factor = 1L
        private var offset = 0L
        private var available = 0
        private var integrator = 0

        fun setRates(clockRate: Int, sampleRate: Int) {
            factor = ((TIME_UNIT.toDouble() * sampleRate / clockRate).toLong()).coerceAtLeast(1L)
            if (factor.toDouble() < TIME_UNIT.toDouble() * sampleRate / clockRate) factor++
        }

        fun clear() {
            offset = factor / 2
            available = 0
            integrator = 0
            buffer.fill(0)
        }

        fun addDelta(time: Int, delta: Int) {
            if (delta == 0) return
            val fixed = time.toLong() * factor + offset
            val outIndex = available + (fixed shr FRAC_BITS).toInt()
            if (outIndex + 15 >= buffer.size) return

            val phaseShift = FRAC_BITS - PHASE_BITS
            val phase = ((fixed shr phaseShift) and (PHASE_COUNT - 1).toLong()).toInt()
            val interp = (fixed and (DELTA_UNIT - 1).toLong()).toInt()
            val delta2 = (delta * interp) shr DELTA_BITS
            val delta1 = delta - delta2
            val reversePhase = PHASE_COUNT - phase

            var i = 0
            while (i < HALF_WIDTH) {
                buffer[outIndex + i] += step(phase, i) * delta1 + step(phase + 1, i) * delta2
                i++
            }
            while (i < HALF_WIDTH * 2) {
                val col = HALF_WIDTH * 2 - 1 - i
                buffer[outIndex + i] += step(reversePhase, col) * delta1 + step(reversePhase - 1, col) * delta2
                i++
            }
        }

        fun endFrame(clockDuration: Int) {
            val off = clockDuration.toLong() * factor + offset
            available += (off shr TIME_BITS).toInt()
            offset = off and (TIME_UNIT - 1)
            if (available > size) available = size
        }

        fun readSamples(out: ShortArray, offset: Int, count: Int): Int {
            val readCount = minOf(count, available)
            var sum = integrator
            var i = 0
            while (i < readCount) {
                var sample = sum shr DELTA_BITS
                sum += buffer[i]
                sample = when {
                    sample > Short.MAX_VALUE -> Short.MAX_VALUE.toInt()
                    sample < Short.MIN_VALUE -> Short.MIN_VALUE.toInt()
                    else -> sample
                }
                out[offset + i] = sample.toShort()
                sum -= sample shl (DELTA_BITS - BASS_SHIFT)
                i++
            }
            integrator = sum
            removeSamples(readCount)
            return readCount
        }

        private fun removeSamples(count: Int) {
            if (count == 0) return
            val remain = available + BLIP_BUFFER_EXTRA - count
            buffer.copyInto(buffer, 0, count, count + remain)
            buffer.fill(0, remain, remain + count)
            available -= count
        }

        fun captureSnapshot(): BlipBufferSnapshot = BlipBufferSnapshot(
            Buffer = buffer.copyOf(),
            Factor = factor,
            Offset = offset,
            Available = available,
            Integrator = integrator,
        )

        fun restoreSnapshot(snapshot: BlipBufferSnapshot) {
            snapshot.Buffer.copyInto(buffer, endIndex = minOf(snapshot.Buffer.size, buffer.size))
            if (snapshot.Buffer.size < buffer.size) buffer.fill(0, snapshot.Buffer.size, buffer.size)
            factor = snapshot.Factor.coerceAtLeast(1L)
            offset = snapshot.Offset and (TIME_UNIT - 1)
            available = snapshot.Available.coerceIn(0, size)
            integrator = snapshot.Integrator
        }
    }
}
