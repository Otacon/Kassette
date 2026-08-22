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
        const val MAX_FRAME_SAMPLES = 2048
        private const val MIXER_CYCLE_LENGTH = 120_000
        private const val CHANNEL_COUNT = 5

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
    private var samplePhase = 0
    private val channelDeltas = IntArray(CHANNEL_COUNT * MIXER_CYCLE_LENGTH)
    private val currentOutput = IntArray(CHANNEL_COUNT)

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

    override fun peekRam(addr: Int): Int = status(peek = true)

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
        samplePhase = 0
        sampleCount = 0
        currentOutput.fill(0)
        channelDeltas.fill(0)
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
        samplePhase %= clockRate()
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

    fun beginFrame() {
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
        square1.restoreSnapshot(snapshot.Square1)
        square2.restoreSnapshot(snapshot.Square2)
        triangle.restoreSnapshot(snapshot.Triangle)
        noise.restoreSnapshot(snapshot.Noise)
        dmc.restoreSnapshot(snapshot.Dmc)
        frameCounter.restoreSnapshot(snapshot.FrameCounter)
        currentOutput[0] = square1.output
        currentOutput[1] = square2.output
        currentOutput[2] = triangle.output
        currentOutput[3] = noise.output
        currentOutput[4] = dmc.output
        channelDeltas.fill(0)
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

    private fun appendSample(value: Int) {
        if (sampleCount >= samples.size) return
        val clamped = when {
            value > Short.MAX_VALUE -> Short.MAX_VALUE.toInt()
            value < Short.MIN_VALUE -> Short.MIN_VALUE.toInt()
            else -> value
        }
        samples[sampleCount++] = clamped.toShort()
    }

    private fun renderMixerFrame(time: Int) {
        val end = if (time < MIXER_CYCLE_LENGTH) time else MIXER_CYCLE_LENGTH - 1
        var cycle = 0
        while (cycle <= end) {
            var channel = 0
            while (channel < CHANNEL_COUNT) {
                val index = channel * MIXER_CYCLE_LENGTH + cycle
                val delta = channelDeltas[index]
                if (delta != 0) {
                    currentOutput[channel] += delta
                    channelDeltas[index] = 0
                }
                channel++
            }
            samplePhase += DEFAULT_SAMPLE_RATE
            if (samplePhase >= clockRate()) {
                samplePhase -= clockRate()
                appendSample(getOutputVolume() * 4)
            }
            cycle++
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
}
