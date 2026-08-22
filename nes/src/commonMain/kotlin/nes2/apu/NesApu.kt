package nes2.apu

import nes2.console.NesConsole
import nes2.console.NesConsoleApu
import nes2.console.NesConstants
import nes2.cpu.ConsoleRegion
import nes2.cpu.IRQSource
import nes2.memory.INesMemoryHandler
import nes2.memory.MemoryOperation
import nes2.memory.MemoryRanges
import kotlin.math.PI
import kotlin.math.exp

class NesApu : NesConsoleApu, INesMemoryHandler {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MAX_FRAME_SAMPLES = 2048

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
        private const val HIGH_PASS_90_HZ_CUTOFF = 90.0
        private const val HIGH_PASS_440_HZ_CUTOFF = 440.0
        private const val LOW_PASS_14_KHZ_CUTOFF = 14_000.0

        private fun tndIndex(triangle: Int, noise: Int, dmc: Int): Int = ((triangle shl 4) or noise) * 128 + dmc
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
    private var highPass90Input = 0.0
    private var highPass90Output = 0.0
    private var highPass440Input = 0.0
    private var highPass440Output = 0.0
    private var lowPass14kOutput = 0.0
    private var highPass90 = 0.0
    private var highPass440 = 0.0
    private var lowPass14k = 0.0

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
            0x4018 -> square1.output or (square2.output shl 4)
            0x4019 -> triangle.output or (noise.output shl 4)
            0x401A -> dmc.output
            else -> console.getOpenBus()
        }
    }

    override fun peekRam(addr: Int): Int = status(peek = true)

    override fun writeRam(addr: Int, value: Int) {
        val address = addr and 0xFFFF
        val v = value and 0xFF
        when (address) {
            in 0x4000..0x4003 -> square1.writeRam(address, v)
            in 0x4004..0x4007 -> square2.writeRam(address, v)
            in 0x4008..0x400B -> triangle.writeRam(address, v)
            in 0x400C..0x400F -> noise.writeRam(address, v)
            in 0x4010..0x4013 -> dmc.writeRam(address, v)
            0x4015 -> {
                run()
                console.cpu.clearIrqSource(IRQSource.Dmc)
                square1.setEnabled((v and 0x01) != 0)
                square2.setEnabled((v and 0x02) != 0)
                triangle.setEnabled((v and 0x04) != 0)
                noise.setEnabled((v and 0x08) != 0)
                dmc.setEnabled((v and 0x10) != 0)
            }
            0x4017 -> frameCounter.writeRam(v)
        }
    }

    override fun reset(softReset: Boolean) {
        apuEnabled = true
        currentCycle = 0
        previousCycle = 0
        needToRun = false
        samplePhase = 0
        sampleCount = 0
        highPass90Input = 0.0
        highPass90Output = 0.0
        highPass440Input = 0.0
        highPass440Output = 0.0
        lowPass14kOutput = 0.0
        square1.reset(softReset)
        square2.reset(softReset)
        triangle.reset(softReset)
        noise.reset(softReset)
        dmc.reset(softReset)
        frameCounter.reset(softReset)
    }

    override fun setRegion(region: ConsoleRegion) {
        this.region = if (region == ConsoleRegion.Dendy) ConsoleRegion.Ntsc else region
        highPass90 = exp(-2.0 * PI * HIGH_PASS_90_HZ_CUTOFF / DEFAULT_SAMPLE_RATE)
        highPass440 = exp(-2.0 * PI * HIGH_PASS_440_HZ_CUTOFF / DEFAULT_SAMPLE_RATE)
        lowPass14k = 1.0 - exp(-2.0 * PI * LOW_PASS_14_KHZ_CUTOFF / DEFAULT_SAMPLE_RATE)
        samplePhase %= clockRate()
        frameCounter.setRegion(this.region)
        noise.setRegion(this.region)
        dmc.setRegion(this.region)
    }

    override fun processCpuClock() {
        if (apuEnabled) exec()
        samplePhase += DEFAULT_SAMPLE_RATE
        if (samplePhase >= clockRate()) {
            samplePhase -= clockRate()
            run()
            appendSample(mix())
        }
    }

    override fun endFrame() {
        run()
        square1.endFrame()
        square2.endFrame()
        triangle.endFrame()
        noise.endFrame()
        dmc.endFrame()
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

    private fun exec() {
        currentCycle++
        if (needToRun(currentCycle)) run()
    }

    private fun mix(): Double {
        val mixed = PULSE_MIX[square1.output + square2.output] + TND_MIX[tndIndex(triangle.output, noise.output, dmc.output)]
        highPass90Output = highPass90 * (highPass90Output + mixed - highPass90Input)
        highPass90Input = mixed
        highPass440Output = highPass440 * (highPass440Output + highPass90Output - highPass440Input)
        highPass440Input = highPass90Output
        lowPass14kOutput += lowPass14k * (highPass440Output - lowPass14kOutput)
        return lowPass14kOutput
    }

    private fun appendSample(value: Double) {
        if (sampleCount >= samples.size) return
        val clamped = when {
            value > 1.0 -> 1.0
            value < -1.0 -> -1.0
            else -> value
        }
        samples[sampleCount++] = (clamped * Short.MAX_VALUE).toInt().toShort()
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
